/*
 * Switchly
 * Copyright (C) 2025-2026 Saltyy
 * Copyright (C) 2026 Switchly Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package at.saltyy.switchly.blocking

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import at.saltyy.switchly.R
import at.saltyy.switchly.data.prefs.AppLogStore
import at.saltyy.switchly.data.prefs.BlockAttemptStore
import at.saltyy.switchly.data.prefs.BlockCountStore
import at.saltyy.switchly.data.prefs.EmergencyBypassStore
import at.saltyy.switchly.data.prefs.IgnoredUsageAppsStore
import at.saltyy.switchly.data.prefs.ProfileRuleModeStore
import at.saltyy.switchly.data.prefs.ProfileStore
import at.saltyy.switchly.data.prefs.SwitchModeStore
import at.saltyy.switchly.data.prefs.TempAllowStore
import at.saltyy.switchly.feature.blocker.BlockerActivity
import at.saltyy.switchly.ui.MainActivity
import at.saltyy.switchly.util.AppBlockSafety
import at.saltyy.switchly.util.ProtectionStatusNotifier

/**
 * Best-effort basic app blocker used only when Android Advanced Protection prevents the Accessibility runtime from being available but Usage Access still works.
 * Limitations are deliberate: this service enforces whole-app Block selected/Allow selected rules only.
 * Website rules, in-app rules, Accessibility navigation actions and usage/open limits remain part of the full Accessibility runtime.
 */
class UsageAccessFallbackBlockingService : Service() {

    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler
    private lateinit var resolver: UsageEventsForegroundResolver
    private lateinit var powerManager: PowerManager
    private var keyguardManager: KeyguardManager? = null

    private var lastEligibilityCheckAt = 0L
    private var lastScanWallMs = 0L
    private var lastLaunchEventKey = ""
    private var lastLaunchElapsed = 0L
    private var pendingVisibleEventKey = ""
    private var pendingVisiblePackage = ""
    private var countedVisibleEventKey = ""

    private val poll = object : Runnable {
        override fun run() {
            if (!isRuntimeStillEligible()) {
                stopSelf()
                return
            }

            UsageAccessFallbackBlocking.markRunning(this@UsageAccessFallbackBlockingService)
            runCatching { scanAndEnforce() }
                .onFailure { error ->
                    AppLogStore.append(
                        this@UsageAccessFallbackBlockingService,
                        "Blocking",
                        "limited_usage_fallback scan failed reason=${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                    )
                }

            worker.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        resolver = UsageEventsForegroundResolver(this)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(KeyguardManager::class.java)

        val promoted = runCatching { createChannelAndPromote() }
            .onFailure { error ->
                AppLogStore.append(
                    this,
                    "Blocking",
                    "limited_usage_fallback foreground failed reason=${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                )
            }
            .isSuccess
        if (!promoted) {
            UsageAccessFallbackBlocking.markStopped(this)
            stopSelf()
            return
        }

        workerThread = HandlerThread("SwitchlyLimitedUsageFallback").apply { start() }
        worker = Handler(workerThread.looper)
        lastScanWallMs = System.currentTimeMillis() - INITIAL_LOOKBACK_MS
        UsageAccessFallbackBlocking.markRunning(this)
        runCatching { ProtectionStatusNotifier.refresh(this) }

        AppLogStore.append(
            this,
            "Blocking",
            "limited_usage_fallback created mode=basic_app_only"
        )
        worker.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Eligibility checks touch Usage Access / system services, so keep them on the worker thread.
        if (!::worker.isInitialized) {
            return START_NOT_STICKY
        }
        worker.removeCallbacks(poll)
        worker.post(poll)
        return START_STICKY
    }

    override fun onDestroy() {
        if (::worker.isInitialized) {
            worker.removeCallbacksAndMessages(null)
        }
        if (::workerThread.isInitialized) {
            workerThread.quitSafely()
        }
        UsageAccessFallbackBlocking.markStopped(this)
        runCatching { ProtectionStatusNotifier.refresh(this) }
        AppLogStore.append(this, "Blocking", "limited_usage_fallback destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isRuntimeStillEligible(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEligibilityCheckAt < ELIGIBILITY_RECHECK_MS) {
            return true
        }
        lastEligibilityCheckAt = now
        return UsageAccessFallbackBlocking.shouldRun(this)
    }

    private fun scanAndEnforce() {
        confirmPendingBlockVisible()

        val now = System.currentTimeMillis()
        val from = (lastScanWallMs - EVENT_OVERLAP_MS).coerceAtLeast(now - MAX_EVENT_LOOKBACK_MS)
        val events = resolver.queryForegroundEvents(from, now)
        lastScanWallMs = now

        val latest = events.maxByOrNull { it.second } ?: return
        val pkg = latest.first
        val eventTs = latest.second
        val eventKey = "$eventTs|$pkg"

        BlockingRuntime.markUsageTopResolution(this, pkg, "advanced_protection_usage_fallback")
        BlockingRuntime.markForegroundPackage(this, pkg, "advanced_protection_usage_fallback")

        if (!shouldBlockBasicApp(pkg)) {
            BlockingRuntime.markBlockingCheck(
                this,
                pkg,
                "limited_fallback_allow",
                "eventTs=$eventTs mode=basic_app_only"
            )
            return
        }

        if (BlockerActivity.isRecentlyFocusedFor(pkg, BLOCKER_VISIBLE_TTL_MS)) {
            if (pendingVisibleEventKey == eventKey) {
                confirmPendingBlockVisible()
            }
            return
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val mayRetrySameEvent = eventKey == lastLaunchEventKey &&
            nowElapsed - lastLaunchElapsed >= LAUNCH_RETRY_MS
        if (eventKey == lastLaunchEventKey && !mayRetrySameEvent) {
            return
        }

        val firstLaunchForEvent = eventKey != lastLaunchEventKey
        lastLaunchEventKey = eventKey
        lastLaunchElapsed = nowElapsed

        if (firstLaunchForEvent) {
            BlockAttemptStore.incrementToday(this, pkg)
        }

        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

        BlockingRuntime.markBlockingCheck(
            this,
            pkg,
            "limited_fallback_block",
            "eventTs=$eventTs mode=basic_app_only retry=${!firstLaunchForEvent}"
        )
        BlockingRuntime.markBlockerLaunchRequested(
            this,
            pkg,
            "source=advanced_protection_usage_fallback label=$label retry=${!firstLaunchForEvent}"
        )

        val launched = runCatching { BlockerActivity.show(this, pkg, label) }
        if (launched.isSuccess) {
            pendingVisibleEventKey = eventKey
            pendingVisiblePackage = pkg
        } else {
            val error = launched.exceptionOrNull()
            AppLogStore.append(
                this,
                "Blocking",
                "limited_usage_fallback launch failed pkg=$pkg reason=${error?.javaClass?.simpleName ?: "unknown"}: ${error?.message.orEmpty()}"
            )
        }
    }

    private fun confirmPendingBlockVisible() {
        val pkg = pendingVisiblePackage
        val eventKey = pendingVisibleEventKey
        if (pkg.isBlank() || eventKey.isBlank() || eventKey == countedVisibleEventKey) {
            return
        }
        if (!BlockerActivity.isRecentlyFocusedFor(pkg, BLOCKER_VISIBLE_TTL_MS)) {
            return
        }

        countedVisibleEventKey = eventKey
        BlockCountStore.incrementToday(this, pkg)
        BlockingRuntime.markBlockShown(
            this,
            pkg,
            "source=advanced_protection_usage_fallback event=$eventKey"
        )
    }

    private fun shouldBlockBasicApp(pkg: String): Boolean {
        if (pkg.isBlank() || AppBlockSafety.isAlwaysExcluded(this, pkg)) {
            return false
        }
        if (IgnoredUsageAppsStore.isExcludedFromProtection(this, pkg)) {
            return false
        }
        if (!SwitchModeStore.isEnabled(this)) {
            return false
        }
        if (!powerManager.isInteractive || keyguardManager?.isKeyguardLocked == true) {
            return false
        }
        if (EmergencyBypassStore.isActive(this) || TempAllowStore.isAllowed(this, pkg)) {
            return false
        }

        val profile = ProfileStore.getCurrent(this) ?: return false
        return if (ProfileRuleModeStore.isAllowMode(this, profile)) {
            val launchable = ProfileStore.getLaunchablePackages(this)
            val allowed = ProfileStore.getAllowedForProfile(this, profile)
            pkg in launchable && pkg !in allowed && !AppBlockSafety.isAllowModeEssential(this, pkg)
        } else {
            pkg in ProfileStore.getBlockedForProfile(this, profile)
        }
    }

    private fun createChannelAndPromote() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.advanced_protection_fallback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.advanced_protection_fallback_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.lock_24)
            .setContentTitle(getString(R.string.advanced_protection_fallback_notification_title))
            .setContentText(getString(R.string.advanced_protection_fallback_notification_text))
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "switchly_advanced_protection_fallback"
        private const val NOTIFICATION_ID = 9012
        private const val POLL_INTERVAL_MS = 900L
        private const val ELIGIBILITY_RECHECK_MS = 5_000L
        private const val INITIAL_LOOKBACK_MS = 8_000L
        private const val MAX_EVENT_LOOKBACK_MS = 10_000L
        private const val EVENT_OVERLAP_MS = 1_500L
        private const val LAUNCH_RETRY_MS = 2_500L
        private const val BLOCKER_VISIBLE_TTL_MS = 3_000L
    }
}
