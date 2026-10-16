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

import android.content.Context
import androidx.core.content.edit
import com.oliver.loqin.util.ManagedDevicePolicyHelper

// Persists and retrieves app lock state.
object AppLockStore {
    private const val PREFS = "loqin_prefs"
    private const val KEY_ENABLED = "pref_app_lock_enabled"
    private const val KEY_PIN = "pref_app_lock_pin"
    private const val KEY_BIOMETRIC = "pref_app_lock_biometric"
    private const val KEY_STRICT_PROTECTION = "pref_app_lock_strict_protection"
    private const val KEY_FAILED_ATTEMPTS = "pref_app_lock_failed_attempts"
    private const val KEY_LOCKED_UNTIL = "pref_app_lock_locked_until_ms"

    // After FREE_ATTEMPTS wrong PINs each further one locks entry: 30 s, doubling up to 15 min.
    private const val FREE_ATTEMPTS = 5
    private const val FIRST_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 15 * 60_000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasPin(ctx: Context): Boolean = !prefs(ctx).getString(KEY_PIN, null).isNullOrBlank()

    /** Length of the stored PIN, or 0 when none is set. Used to size the entered digit boxes. */
    fun pinLength(ctx: Context): Int = prefs(ctx).getString(KEY_PIN, null)?.trim()?.length ?: 0

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false) && hasPin(ctx)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit(commit = true) {
            putBoolean(KEY_ENABLED, enabled && hasPin(ctx))
        }
    }

    fun setPin(ctx: Context, pin: String) {
        prefs(ctx).edit(commit = true) {
            putString(KEY_PIN, pin.trim())
        }
    }

    /** Removes the saved PIN and disables App lock (which requires a PIN). */
    fun clearPin(ctx: Context) {
        prefs(ctx).edit(commit = true) {
            remove(KEY_PIN)
            putBoolean(KEY_ENABLED, false)
            putBoolean(KEY_BIOMETRIC, false)
        }
    }

    /**
     * Checks [enteredPin], counting wrong entries. While locked out after repeated wrong PINs it
     * returns false without checking.
     */
    fun matchesPin(ctx: Context, enteredPin: String): Boolean {
        if (lockoutRemainingMs(ctx) > 0L) return false
        val expected = prefs(ctx).getString(KEY_PIN, null)?.trim().orEmpty()
        val matches = expected.isNotBlank() && expected == enteredPin.trim()
        val sp = prefs(ctx)
        if (matches) {
            sp.edit(commit = true) {
                remove(KEY_FAILED_ATTEMPTS)
                remove(KEY_LOCKED_UNTIL)
            }
        } else {
            val failed = sp.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            sp.edit(commit = true) {
                putInt(KEY_FAILED_ATTEMPTS, failed)
                if (failed >= FREE_ATTEMPTS) {
                    val lockout = (FIRST_LOCKOUT_MS shl (failed - FREE_ATTEMPTS).coerceAtMost(5))
                        .coerceAtMost(MAX_LOCKOUT_MS)
                    putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + lockout)
                }
            }
        }
        return matches
    }

    /** Time until another PIN may be tried; capped so a clock set back can't extend it. */
    fun lockoutRemainingMs(ctx: Context): Long {
        val until = prefs(ctx).getLong(KEY_LOCKED_UNTIL, 0L)
        if (until <= 0L) return 0L
        return (until - System.currentTimeMillis()).coerceIn(0L, MAX_LOCKOUT_MS)
    }

    fun isBiometricEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_BIOMETRIC, false)

    fun setBiometricEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit(commit = true) { putBoolean(KEY_BIOMETRIC, enabled) }
    }

    fun isStrictProtectionEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_STRICT_PROTECTION, false)

    fun setStrictProtectionEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit(commit = true) { putBoolean(KEY_STRICT_PROTECTION, enabled) }
        ManagedDevicePolicyHelper.syncSelfUninstallBlock(ctx)
    }
}
