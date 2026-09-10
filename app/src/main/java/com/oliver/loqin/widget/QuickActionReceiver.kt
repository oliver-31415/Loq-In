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

package com.oliver.loqin.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.oliver.loqin.R
import com.oliver.loqin.blocking.BlockingRuntime
import com.oliver.loqin.data.prefs.AppLogStore
import com.oliver.loqin.data.prefs.AutomationModeStore
import com.oliver.loqin.data.prefs.EmergencyBypassStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.feature.settings.PermissionsActivity

class QuickActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FOCUS_NOW -> handleFocusNow(context)
            ACTION_PAUSE_LOQIN_5 -> handlePauseLoqIn(context, 5)
            ACTION_PAUSE_LOQIN_15 -> handlePauseLoqIn(context, 15)
            ACTION_PAUSE_LOQIN_30 -> handlePauseLoqIn(context, 30)
            ACTION_PAUSE_LOQIN_60 -> handlePauseLoqIn(context, 60)
            ACTION_PAUSE_LOQIN_END -> handlePauseLoqInEnd(context)
        }
    }

    companion object {
        const val ACTION_FOCUS_NOW = "com.oliver.loqin.action.FOCUS_NOW"
        const val ACTION_PAUSE_LOQIN_5 = "com.oliver.loqin.action.PAUSE_LOQIN_5"
        const val ACTION_PAUSE_LOQIN_15 = "com.oliver.loqin.action.PAUSE_LOQIN_15"
        const val ACTION_PAUSE_LOQIN_30 = "com.oliver.loqin.action.PAUSE_LOQIN_30"
        const val ACTION_PAUSE_LOQIN_60 = "com.oliver.loqin.action.PAUSE_LOQIN_60"
        const val ACTION_PAUSE_LOQIN_END = "com.oliver.loqin.action.PAUSE_LOQIN_END"

        fun refreshWidgets(context: Context) {
            PauseBlockerWidgetProvider.refreshAll(context)
            FocusNowWidgetProvider.refreshAll(context)
            ScannerWidgetProvider.refreshAll(context)
        }

        fun handleFocusNow(context: Context): Boolean {
            if (!ensureProtectionReady(context, "enable")) {
                refreshWidgets(context)
                return false
            }

            val canEnable = AutomationModeStore.isButtonEnableAllowed(context)
            if (!canEnable) {
                AppLogStore.append(context, "Widget", "action_result action=enable result=blocked reason=control_mode")
                Toast.makeText(context, context.getString(R.string.mode_blocked_tile_action), Toast.LENGTH_SHORT).show()
                refreshWidgets(context)
                return false
            }

            val tempDisableRemaining = SwitchModeStore.getTemporaryRemainingMillis(context)
            val tempEnableRemaining = SwitchModeStore.getTemporaryEnableRemainingMillis(context)
            val currentlyEnabled = SwitchModeStore.isEnabled(context)
            if (currentlyEnabled && tempDisableRemaining <= 0L && tempEnableRemaining <= 0L) {
                AppLogStore.append(context, "Widget", "action_result action=enable result=noop reason=already_enabled")
                Toast.makeText(context, context.getString(R.string.widget_focus_already_active), Toast.LENGTH_SHORT).show()
                refreshWidgets(context)
                return false
            }

            SwitchModeStore.setEnabled(context, true)
            AppLogStore.append(context, "Profiles", "Manual toggle action=enable profile=${ProfileStore.getCurrent(context)}")
            BlockingRuntime.ensureRunning(context)
            AppLogStore.append(context, "Widget", "action_result action=enable result=changed reason=applied")
            Toast.makeText(context, context.getString(R.string.widget_focus_now_applied), Toast.LENGTH_SHORT).show()
            refreshWidgets(context)
            return true
        }

        fun handlePauseLoqIn(context: Context, minutes: Int): Boolean {
            if (!ensureProtectionReady(context, "temp_disable")) {
                refreshWidgets(context)
                return false
            }

            val baseEnabled = SwitchModeStore.isBaseEnabled(context)
            val tempDisableRemaining = SwitchModeStore.getTemporaryRemainingMillis(context)
            val tempEnableActive = SwitchModeStore.hasActiveTemporaryEnable(context)
            if (!baseEnabled && !tempEnableActive && tempDisableRemaining <= 0L) {
                AppLogStore.append(context, "Widget", "action_result action=temp_disable result=noop reason=already_disabled")
                Toast.makeText(context, context.getString(R.string.widget_pause_already_disabled), Toast.LENGTH_SHORT).show()
                refreshWidgets(context)
                return false
            }

            if (!AutomationModeStore.isTileAllowed(context)) {
                AppLogStore.append(context, "Widget", "action_result action=temp_disable result=blocked reason=control_mode")
                Toast.makeText(context, context.getString(R.string.mode_blocked_tile_action), Toast.LENGTH_SHORT).show()
                refreshWidgets(context)
                return false
            }

            val requireNfc = SwitchModeStore.isNfcRequiredForDisable(context)
            val emergencyActive = EmergencyBypassStore.isActive(context)
            if (requireNfc && !emergencyActive) {
                AppLogStore.append(context, "Widget", "action_result action=temp_disable result=blocked reason=nfc_required")
                Toast.makeText(context, context.getString(R.string.toast_disable_requires_nfc), Toast.LENGTH_SHORT).show()
                refreshWidgets(context)
                return false
            }

            val applied = SwitchModeStore.setTemporarilyDisabled(context, minutes * 60_000L)
            if (!applied) {
                refreshWidgets(context)
                return false
            }
            AppLogStore.append(context, "Profiles", "Manual toggle action=temp_disable profile=${ProfileStore.getCurrent(context)} duration=${minutes * 60_000L}ms")
            BlockingRuntime.ensureRunning(context)
            AppLogStore.append(context, "Widget", "action_result action=temp_disable result=changed reason=applied durationMin=$minutes")
            Toast.makeText(
                context,
                context.resources.getQuantityString(R.plurals.widget_pause_applied_fmt, minutes, minutes),
                Toast.LENGTH_SHORT
            ).show()
            refreshWidgets(context)
            return true
        }

        // Ends an active break early. Ending a break restores protection, so unlike
        // starting one it needs no control-mode / NFC gate.
        fun handlePauseLoqInEnd(context: Context): Boolean {
            val remaining = SwitchModeStore.getTemporaryRemainingMillis(context)
            if (remaining <= 0L) {
                AppLogStore.append(context, "Widget", "action_result action=end_break result=noop reason=no_active_break")
                refreshWidgets(context)
                return false
            }

            SwitchModeStore.cancelTemporaryDisable(context)
            AppLogStore.append(context, "Profiles", "Manual toggle action=temp_disable_end profile=${ProfileStore.getCurrent(context)}")
            AppLogStore.append(context, "Widget", "action_result action=end_break result=changed reason=applied")
            Toast.makeText(context, context.getString(R.string.widget_breaks_ended_toast), Toast.LENGTH_SHORT).show()
            refreshWidgets(context)
            return true
        }

        private fun ensureProtectionReady(context: Context, action: String): Boolean {
            if (BlockingRuntime.isAccessibilityActive(context)) {
                return true
            }

            AppLogStore.append(context, "Widget", "action_result action=$action result=blocked reason=permission_missing")
            Toast.makeText(context, context.getString(R.string.widget_action_requires_permissions), Toast.LENGTH_SHORT).show()
            runCatching {
                context.startActivity(
                    Intent(context, PermissionsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
            return false
        }
    }
}
