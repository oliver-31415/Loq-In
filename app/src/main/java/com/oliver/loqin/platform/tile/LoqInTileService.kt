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

package com.oliver.loqin.platform.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.AppLogStore
import com.oliver.loqin.data.prefs.AutomationModeStore
import com.oliver.loqin.data.prefs.EmergencyBypassStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.feature.entry.QuickActionIconFactory

class LoqInTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { toggleAndRefresh() }
        } else {
            toggleAndRefresh()
        }
    }

    private fun toggleAndRefresh() {
        val ctx = this
        val currentlyEnabled = SwitchModeStore.isEnabled(ctx)
        val canChange = if (currentlyEnabled) {
            AutomationModeStore.isTileAllowed(ctx)
        } else {
            AutomationModeStore.isTileAllowed(ctx) || AutomationModeStore.isButtonEnableAllowed(ctx)
        }

        if (!canChange) {
            AppLogStore.append(ctx, "QuickSettings", "action_result action=toggle result=blocked reason=control_mode enabled=$currentlyEnabled")
            val messageRes = if (currentlyEnabled && AutomationModeStore.isButtonEnableAllowed(ctx)) {
                R.string.mode_blocked_button_disable_enable_only
            } else {
                R.string.mode_blocked_tile_action
            }
            Toast.makeText(
                applicationContext,
                getString(messageRes),
                Toast.LENGTH_SHORT
            ).show()
            refreshTile()
            return
        }

        val requireNfc = SwitchModeStore.isNfcRequiredForDisable(ctx)

        // Disable only via NFC, when lock is enabled
        val emergencyActive = EmergencyBypassStore.isActive(ctx)

        // Disable only via NFC, when lock is enabled (unless Emergency Bypass is active)
        if (currentlyEnabled && requireNfc && !emergencyActive) {
            AppLogStore.append(ctx, "QuickSettings", "action_result action=disable result=blocked reason=nfc_required")
            Toast.makeText(
                applicationContext,
                getString(R.string.toast_disable_requires_nfc),
                Toast.LENGTH_SHORT
            ).show()
            refreshTile()
            return
        }

        val target = !currentlyEnabled
        val before = SwitchModeStore.isEnabled(ctx)
        val accepted = SwitchModeStore.setEnabled(ctx, target, allowNfcBypass = false)
        val after = SwitchModeStore.isEnabled(ctx)
        if (accepted && before != after) {
            AppLogStore.append(
                ctx,
                "Profiles",
                "Manual toggle action=${if (target) "enable" else "disable"} profile=${ProfileStore.getCurrent(ctx)}"
            )
            AppLogStore.append(ctx, "QuickSettings", "action_result action=${if (target) "enable" else "disable"} result=changed reason=applied")
        } else if (accepted) {
            AppLogStore.append(
                ctx,
                "QuickSettings",
                "action_result action=${if (target) "enable" else "disable"} result=noop reason=${if (after) "already_enabled" else "already_disabled"}"
            )
            Toast.makeText(
                applicationContext,
                getString(if (after) R.string.widget_focus_already_active else R.string.widget_pause_already_disabled),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            AppLogStore.append(ctx, "QuickSettings", "action_result action=${if (target) "enable" else "disable"} result=blocked reason=state_change_rejected")
        }
        refreshTile()
    }

    private fun refreshTile() {
        val enabled = SwitchModeStore.isEnabled(this)
        qsTile?.apply {
            // State
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

            // Label
            label = if (enabled) {
                getString(R.string.qs_label_on)
            } else {
                getString(R.string.qs_label_off)
            }

            // Use a rendered monochrome bitmap so System UI does not need to resolve app-theme colors.
            icon = QuickActionIconFactory.createTileIcon(
                this@LoqInTileService,
                R.drawable.qs_loqin_24
            )
            updateTile()
        }
    }
}
