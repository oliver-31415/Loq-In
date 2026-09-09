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

package com.oliver.loqin.blocking

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.oliver.loqin.data.prefs.AppLogStore
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.util.PermissionUtils

/**
 * Targeted process-resilience helper for vivo/iQOO builds that may kill the app process when the Recent Apps task is removed even though the Accessibility toggle still looks enabled.
 * This does not and cannot grant/re-enable Accessibility.
 * Its only job is to keep the process at foreground-service importance while LoqIn protection is active on the affected OEM family.
 */
object OemAccessibilityKeepAlive {

    private const val PREFS_NAME = "oem_accessibility_health"
    private const val KEY_SEEN_ACCESSIBILITY_HEALTHY = "seen_accessibility_healthy"
    private const val KEY_LAST_HEALTHY_MS = "last_accessibility_healthy_ms"
    private const val KEY_LAST_SETTINGS_OFF_LOG_MS = "last_settings_off_log_ms"
    private const val SETTINGS_OFF_LOG_THROTTLE_MS = 60 * 60 * 1_000L

    fun isAffectedDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim().lowercase()
        val brand = Build.BRAND.orEmpty().trim().lowercase()
        return manufacturer.contains("vivo") ||
            brand.contains("vivo") ||
            manufacturer.contains("iqoo") ||
            brand.contains("iqoo")
    }

    fun sync(context: Context) {
        val ctx = context.applicationContext
        if (!isAffectedDevice()) {
            stop(ctx)
            return
        }

        if (!SwitchModeStore.isEnabled(ctx)) {
            stop(ctx)
            return
        }

        // Only start the guard after Android has actually enabled/bound Accessibility once.
        // If the OEM later breaks the binding, keep an already-running guard alive instead of stopping it just because the Accessibility state temporarily becomes unhealthy.
        val accessibilityEnabled = PermissionUtils.isAccessibilityServiceEnabled(
            ctx,
            LoqInAccessibilityService::class.java,
        )
        if (!accessibilityEnabled) {
            recordLikelySettingsDisable(ctx)
            return
        }

        recordAccessibilityHealthy(ctx)

        val intent = Intent(ctx, OemAccessibilityKeepAliveService::class.java)
        runCatching {
            ContextCompat.startForegroundService(ctx, intent)
        }.onFailure { error ->
            AppLogStore.append(
                ctx,
                "Accessibility",
                "OEM keep-alive start skipped manufacturer=${Build.MANUFACTURER} reason=${error.javaClass.simpleName}: ${error.message.orEmpty()}",
            )
        }
    }

    fun recordAccessibilityHealthy(context: Context) {
        if (!isAffectedDevice()) return
        val now = System.currentTimeMillis()
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_SEEN_ACCESSIBILITY_HEALTHY, true)
                putLong(KEY_LAST_HEALTHY_MS, now)
            }
    }

    fun hasSeenAccessibilityHealthy(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SEEN_ACCESSIBILITY_HEALTHY, false)

    fun lastAccessibilityHealthyMs(context: Context): Long =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_HEALTHY_MS, 0L)

    fun isLikelyAccessibilityDisabledByOem(context: Context): Boolean {
        val ctx = context.applicationContext
        if (!isAffectedDevice() || !SwitchModeStore.isEnabled(ctx) || !hasSeenAccessibilityHealthy(ctx)) {
            return false
        }
        return !PermissionUtils.isAccessibilityServiceEnabled(
            ctx,
            LoqInAccessibilityService::class.java,
        )
    }

    private fun recordLikelySettingsDisable(context: Context) {
        val ctx = context.applicationContext
        if (!SwitchModeStore.isEnabled(ctx) || !hasSeenAccessibilityHealthy(ctx)) return

        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastLog = prefs.getLong(KEY_LAST_SETTINGS_OFF_LOG_MS, 0L)
        if (now - lastLog < SETTINGS_OFF_LOG_THROTTLE_MS) return

        AppLogStore.append(
            ctx,
            "Accessibility",
            "OEM accessibility setting appears disabled after previous healthy state manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} lastHealthyMs=${lastAccessibilityHealthyMs(ctx)}",
        )
        prefs.edit { putLong(KEY_LAST_SETTINGS_OFF_LOG_MS, now) }
    }

    fun stop(context: Context) {
        val ctx = context.applicationContext
        val serviceIntent = Intent(ctx, OemAccessibilityKeepAliveService::class.java)
        runCatching {
            ctx.stopService(serviceIntent)
        }
    }
}
