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

package com.oliver.loqin.util

import android.content.Context
import com.oliver.loqin.data.prefs.EmergencyBypassStore
import com.oliver.loqin.data.prefs.SwitchModeStore

/**
 * One canonical read-only view of the effective protection state.
 */
object ProtectionStateProvider {
    enum class State {
        DISABLED,
        ENABLED,
        TEMPORARILY_DISABLED,
        TEMPORARILY_ENABLED,
        EMERGENCY_UNLOCK,
        EMERGENCY_UNLOCK_PAUSED,
    }

    data class Snapshot(
        val state: State,
        val baseEnabled: Boolean,
        val effectiveEnabled: Boolean,
        val blockingExpected: Boolean,
        val temporaryDisableRemainingMs: Long,
        val temporaryEnableRemainingMs: Long,
        val emergencyRemainingMinutes: Int,
    )

    fun snapshot(context: Context): Snapshot {
        val base = SwitchModeStore.isBaseEnabled(context)
        val effective = SwitchModeStore.isEnabled(context)
        val tempDisable = SwitchModeStore.getTemporaryRemainingMillis(context)
        val tempEnable = SwitchModeStore.getTemporaryEnableRemainingMillis(context)
        val emergencyActive = EmergencyBypassStore.isActive(context)
        val emergencyPaused = EmergencyBypassStore.isPaused(context)
        val state = when {
            emergencyActive -> State.EMERGENCY_UNLOCK
            emergencyPaused -> State.EMERGENCY_UNLOCK_PAUSED
            tempDisable > 0L -> State.TEMPORARILY_DISABLED
            tempEnable > 0L -> State.TEMPORARILY_ENABLED
            effective -> State.ENABLED
            else -> State.DISABLED
        }
        return Snapshot(
            state = state,
            baseEnabled = base,
            effectiveEnabled = effective,
            blockingExpected = effective && !emergencyActive,
            temporaryDisableRemainingMs = tempDisable,
            temporaryEnableRemainingMs = tempEnable,
            emergencyRemainingMinutes = EmergencyBypassStore.minutesRemaining(context),
        )
    }
}
