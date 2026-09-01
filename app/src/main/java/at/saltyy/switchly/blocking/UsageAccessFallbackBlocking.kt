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

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import at.saltyy.switchly.data.prefs.AppLogStore
import at.saltyy.switchly.data.prefs.IgnoredUsageAppsStore
import at.saltyy.switchly.data.prefs.ProfileRuleModeStore
import at.saltyy.switchly.data.prefs.ProfileStore
import at.saltyy.switchly.data.prefs.SwitchModeStore
import at.saltyy.switchly.feature.usage.UsageStatsRepo
import at.saltyy.switchly.util.AdvancedProtectionCompat

/**
 * Coordinates Switchly's limited UsageEvents-based app-blocking fallback.
 * This is intentionally narrow: it only runs when Android Advanced Protection is enabled, the Accessibility runtime is unavailable, Switchly protection is enabled and Usage Access is granted.
 * It cannot replace Accessibility for websites, in-app rules or navigation actions.
 */
object UsageAccessFallbackBlocking {

    private const val PREFS = "usage_access_fallback_runtime"
    private const val KEY_RUNNING = "running"
    private const val KEY_LAST_HEARTBEAT_ELAPSED = "last_heartbeat_elapsed"
    private const val HEARTBEAT_STALE_MS = 5_000L

    fun shouldRun(context: Context): Boolean {
        val ctx = context.applicationContext
        if (!SwitchModeStore.isEnabled(ctx)) {
            return false
        }
        if (!AdvancedProtectionCompat.isEnabled(ctx)) {
            return false
        }
        if (BlockingRuntime.isAccessibilityActive(ctx)) {
            return false
        }
        val profile = ProfileStore.getCurrent(ctx) ?: return false
        if (!ProfileRuleModeStore.isAllowMode(ctx, profile)) {
            val hasEffectiveBlockedApp = ProfileStore.getBlockedForProfile(ctx, profile)
                .any { pkg -> !IgnoredUsageAppsStore.isExcludedFromProtection(ctx, pkg) }
            if (!hasEffectiveBlockedApp) {
                return false
            }
        }
        return UsageStatsRepo.hasUsageAccess(ctx)
    }

    fun sync(context: Context) {
        val ctx = context.applicationContext
        val serviceIntent = Intent(ctx, UsageAccessFallbackBlockingService::class.java)
        if (shouldRun(ctx)) {
            runCatching { ContextCompat.startForegroundService(ctx, serviceIntent) }
                .onFailure { error ->
                    AppLogStore.append(
                        ctx,
                        "Blocking",
                        "limited_usage_fallback start failed reason=${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                    )
                }
        } else {
            runCatching {
                ctx.stopService(serviceIntent)
            }
            markStopped(ctx)
        }
    }

    fun isRunning(context: Context): Boolean {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean(KEY_RUNNING, false)) {
            return false
        }
        val last = p.getLong(KEY_LAST_HEARTBEAT_ELAPSED, 0L)
        if (last <= 0L) {
            return false
        }
        val age = SystemClock.elapsedRealtime() - last
        return age in 0..HEARTBEAT_STALE_MS
    }

    internal fun markRunning(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_RUNNING, true)
            putLong(KEY_LAST_HEARTBEAT_ELAPSED, SystemClock.elapsedRealtime())
        }
    }

    internal fun markStopped(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_RUNNING, false)
            remove(KEY_LAST_HEARTBEAT_ELAPSED)
        }
    }
}
