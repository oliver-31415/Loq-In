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

package com.oliver.loqin.platform.receiver.system

import com.oliver.loqin.BuildConfig
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.oliver.loqin.blocking.BlockingRuntime
import com.oliver.loqin.data.prefs.AutostartStore
import com.oliver.loqin.data.prefs.SchedulePlanner
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.platform.receiver.bluetooth.BluetoothTriggerMonitor
import com.oliver.loqin.platform.receiver.location.LocationTriggerMonitor
import com.oliver.loqin.platform.receiver.schedule.ScheduleReceiver
import com.oliver.loqin.platform.receiver.wifi.WifiTriggerMonitor
import com.oliver.loqin.util.ProtectionStatusNotifier

// Receives system boot events and restores LoqIn runtime state.
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val ctx = context.applicationContext

        // Restore trigger monitors.
        // Wi-Fi/Bluetooth defer and coalesce their FGS sync until this receiver callback has returned, so Android can create the service immediately.
        runCatching { WifiTriggerMonitor.ensureStarted(ctx) }
        runCatching { BluetoothTriggerMonitor.ensureStarted(ctx) }
        runCatching { LocationTriggerMonitor.ensureStarted(ctx) }

        // Ensure prefs/runtime initialized
        SwitchModeStore.ensureInit(ctx)

        val enabled = SwitchModeStore.isEnabled(ctx)
        val autostart = AutostartStore.isEnabled(ctx)
        val hasA11y = BlockingRuntime.isAccessibilityActive(ctx)

        if (BuildConfig.DEBUG) Log.d(TAG, "BOOT_COMPLETED received -> enabled=$enabled autostart=$autostart accessibility=$hasA11y")

        // Restore time schedule alarms
        runCatching { SchedulePlanner.updateNextAlarm(ctx) }
        runCatching { SchedulePlanner.notifyNextChanged(ctx) }

        // Immediate watchdog re-eval after boot: if we are currently inside an active
        // schedule window, re-assert the desired state now (instead of waiting for the next boundary).
        runCatching {
            ctx.sendBroadcast(
                Intent(ctx, ScheduleReceiver::class.java).apply {
                    this.action = ScheduleReceiver.ACTION_TICK
                    putExtra("time_reason", "boot_completed")
                    putExtra("alarm_reason", "boot_watchdog")
                }
            )
        }

        // Restore the appropriate protection runtime after boot
        if (!enabled || !autostart) {
            return
        }

        runCatching {
            // Full Accessibility will remain system-managed.
            // If Android Advanced Protection makes it unavailable, ensureRunning() can reconcile the limited Usage Access fallback.
            BlockingRuntime.ensureRunning(ctx)
        }.onFailure {
            Log.w(TAG, "Blocking runtime start blocked after boot: ${it.message}")
        }

        // If LoqIn is enabled but Accessibility is OFF after boot, show a persistent warning notification so the user can fix it.
        runCatching { ProtectionStatusNotifier.refresh(ctx) }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
