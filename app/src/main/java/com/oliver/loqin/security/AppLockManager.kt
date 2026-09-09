/*
 * Loq In
 * Copyright (C) 2026 Loq In Contributors
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

package com.oliver.loqin.security

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.oliver.loqin.data.prefs.AppLogStore
import com.oliver.loqin.feature.barcode.BarcodeScanActivity
import com.oliver.loqin.feature.blocker.BlockerActivity
import com.oliver.loqin.feature.entry.ScanLauncherActivity
import com.oliver.loqin.feature.qr.QrScanActivity
import com.oliver.loqin.feature.scan.UnifiedScanActivity
import com.oliver.loqin.feature.settings.AppLockActivity
import com.oliver.loqin.nfc.NfcEntryActivity
import com.oliver.loqin.util.ActivityTransitionCompat
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

object AppLockManager {
    private var startedActivities: Int = 0
    private val sessionUnlocked = AtomicBoolean(false)
    private val promptShowing = AtomicBoolean(false)

    @Volatile
    private var lastPromptActivityRef: WeakReference<Activity>? = null

    fun register(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                startedActivities += 1
            }

            override fun onActivityResumed(activity: Activity) {
                maybeRequestUnlock(activity)
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    sessionUnlocked.set(false)
                    promptShowing.set(false)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (lastPromptActivityRef?.get() === activity) {
                    lastPromptActivityRef = null
                }
            }
        })
    }

    fun maybeRequestUnlock(activity: Activity): Boolean {
        lastPromptActivityRef = WeakReference(activity)
        if (!shouldProtect(activity)) {
            return false
        }
        if (!AppLockStore.isEnabled(activity)) {
            return false
        }
        if (sessionUnlocked.get()) {
            return false
        }
        if (!promptShowing.compareAndSet(false, true)) {
            return true
        }

        AppLogStore.append(activity, "AppLock", "Lock triggered package=${activity.javaClass.simpleName}")
        activity.startActivity(
            Intent(activity, AppLockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
        ActivityTransitionCompat.finishWithoutAnimation(activity)
        return true
    }

    fun markUnlocked() {
        lastPromptActivityRef?.get()?.let { AppLogStore.append(it, "AppLock", "Unlock success method=pin_or_biometric") }
        sessionUnlocked.set(true)
        promptShowing.set(false)
    }

    fun clearPromptFlag() {
        promptShowing.set(false)
    }

    private fun shouldProtect(activity: Activity): Boolean {
        return when (activity) {
            is AppLockActivity,
            is NfcEntryActivity,
            is BlockerActivity -> false

            // Direct scanner shortcuts must stay usable as physical unlock controls even when the rest of LoqIn is PIN-protected.
            is QrScanActivity -> !activity.intent.getBooleanExtra(QrScanActivity.EXTRA_ALLOW_DIRECT_OPEN, false)
            is BarcodeScanActivity -> !activity.intent.getBooleanExtra(BarcodeScanActivity.EXTRA_ALLOW_DIRECT_OPEN, false)
            is UnifiedScanActivity -> !activity.intent.getBooleanExtra(UnifiedScanActivity.EXTRA_ALLOW_DIRECT_OPEN, false)
            is ScanLauncherActivity -> activity.intent?.action !in DIRECT_SCANNER_ACTIONS
            else -> true
        }
    }

    private val DIRECT_SCANNER_ACTIONS = setOf(
        ScanLauncherActivity.ACTION_OPEN_QR_SCAN,
        ScanLauncherActivity.ACTION_OPEN_BARCODE_SCAN,
    )
}
