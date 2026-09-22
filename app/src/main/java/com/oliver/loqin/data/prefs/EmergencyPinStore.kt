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

package com.oliver.loqin.data.prefs

import android.content.Context
import androidx.core.content.edit

// Persists and retrieves emergency pin state.
object EmergencyPinStore {
    private const val PREFS = "loqin_prefs"
    private const val KEY_EMERGENCY_PIN = "pref_emergency_pin"

    private val legacyKeys = listOf(
        "emergency_pin",
        "pref_emergency_unlock_pin",
        "emergency_unlock_pin"
    )

    private const val MIN_PIN_LENGTH = 4
    private const val MAX_PIN_LENGTH = 8

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getPin(ctx: Context): String? {
        val sp = prefs(ctx)
        val candidates = buildList {
            add(sp.getString(KEY_EMERGENCY_PIN, null))
            legacyKeys.forEach { key -> add(sp.getString(key, null)) }
        }

        val resolved = candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
        if (!resolved.isNullOrBlank() && sp.getString(KEY_EMERGENCY_PIN, null) != resolved) {
            sp.edit { putString(KEY_EMERGENCY_PIN, resolved) }
        }
        return resolved
    }

    fun hasPin(ctx: Context): Boolean = !getPin(ctx).isNullOrBlank()

    fun setPin(ctx: Context, pin: String) {
        val clean = pin.trim()
        if (clean.isBlank()) {
            return
        }
        prefs(ctx).edit(commit = true) {
            putString(KEY_EMERGENCY_PIN, clean)
            legacyKeys.forEach { key -> remove(key) }
        }
    }

    fun removePin(ctx: Context) {
        clearAllKnownPinValues(ctx)
    }

    fun matchesPin(ctx: Context, enteredPin: String): Boolean {
        val expected = getPin(ctx).orEmpty()
        return expected.isNotBlank() && expected == enteredPin.trim()
    }

    /**
     * Recovery is intentionally only available when protection is genuinely off.
     * A temporary disable/Emergency Unlock window must never become a PIN-reset bypass.
     */
    fun canResetWithoutCurrentPin(ctx: Context): Boolean {
        return !SwitchModeStore.isBaseEnabled(ctx) &&
            !SwitchModeStore.hasActiveTemporaryOverride(ctx) &&
            !EmergencyBypassStore.isActive(ctx) &&
            !EmergencyBypassStore.isPaused(ctx)
    }

    fun resetPinWhenFullyDisabled(ctx: Context, newPin: String): Boolean {
        val clean = newPin.trim()
        if (clean.length !in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
            return false
        }
        if (!canResetWithoutCurrentPin(ctx)) {
            return false
        }

        clearAllKnownPinValues(ctx)
        setPin(ctx, clean)
        return true
    }

    private fun clearAllKnownPinValues(ctx: Context) {
        prefs(ctx).edit(commit = true) {
            remove(KEY_EMERGENCY_PIN)
            legacyKeys.forEach { key -> remove(key) }
        }
    }
}
