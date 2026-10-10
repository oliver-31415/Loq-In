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

/**
 * After the user turns protection on from Home in a mode where the manual button can't turn it
 * off again (QR, barcode, NFC, schedule), they get a short window to undo a mistaken enable
 * without the unlock method — the "first minute" escape hatch other strict blockers offer.
 */
object EnableUndoWindow {
    const val WINDOW_MS = 60_000L

    private const val PREFS = "loqin_prefs"
    private const val KEY_UNTIL = "enable_undo_until_ms"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun start(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit { putLong(KEY_UNTIL, now + WINDOW_MS) }
    }

    fun clear(context: Context) {
        prefs(context).edit { remove(KEY_UNTIL) }
    }

    fun remainingMs(context: Context, now: Long = System.currentTimeMillis()): Long {
        val until = prefs(context).getLong(KEY_UNTIL, 0L)
        val remaining = until - now
        // A clock moved backwards must not stretch the window beyond its length.
        return if (remaining in 1..WINDOW_MS) remaining else 0L
    }
}
