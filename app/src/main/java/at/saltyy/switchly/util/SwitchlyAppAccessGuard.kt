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

package at.saltyy.switchly.util

import android.app.Activity
import android.content.Context
import at.saltyy.switchly.R
import at.saltyy.switchly.data.prefs.EmergencyBypassStore
import at.saltyy.switchly.data.prefs.SwitchModeStore

object SwitchlyAppAccessGuard {

    fun isLocked(context: Context): Boolean {
        return EditingLockGuard.isLocked(context)
    }

    /**
     * Recovery access is intentionally narrow: an active Emergency Unlock or Temporary Disable may be used to repair control settings that could otherwise leave the user locked out.
     * Temporary Enable is not a recovery state and must keep the normal editing lock.
     */
    fun isControlSettingsRecoveryActive(context: Context): Boolean {
        return EmergencyBypassStore.isActive(context) ||
            SwitchModeStore.getTemporaryRemainingMillis(context) > 0L
    }

    fun isControlSettingsLocked(context: Context): Boolean {
        return isLocked(context) && !isControlSettingsRecoveryActive(context)
    }

    fun blockControlSettingsIfLocked(activity: Activity): Boolean {
        if (!isControlSettingsLocked(activity)) {
            return false
        }
        return EditingLockGuard.blockWithDialog(
            activity = activity,
            messageRes = R.string.settings_restricted_action_unavailable,
        )
    }

    fun blockIfLocked(activity: Activity): Boolean {
        return EditingLockGuard.blockWithDialog(
            activity = activity,
            messageRes = R.string.settings_restricted_action_unavailable,
        )
    }
}
