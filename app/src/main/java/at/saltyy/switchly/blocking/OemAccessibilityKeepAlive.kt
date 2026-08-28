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
import android.os.Build
import androidx.core.content.ContextCompat
import at.saltyy.switchly.data.prefs.AppLogStore
import at.saltyy.switchly.data.prefs.SwitchModeStore
import at.saltyy.switchly.util.PermissionUtils

/**
 * Targeted process-resilience helper for vivo/iQOO builds that may kill the app process when the Recent Apps task is removed even though the Accessibility toggle still looks enabled.
 * This does not and cannot grant/re-enable Accessibility.
 * Its only job is to keep the process at foreground-service importance while Switchly protection is active on the affected OEM family.
 */
object OemAccessibilityKeepAlive {

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
            SwitchlyAccessibilityService::class.java,
        )
        if (!accessibilityEnabled) {
            return
        }

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

    fun stop(context: Context) {
        val ctx = context.applicationContext
        val serviceIntent = Intent(ctx, OemAccessibilityKeepAliveService::class.java)
        runCatching {
            ctx.stopService(serviceIntent)
        }
    }
}
