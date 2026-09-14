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
import androidx.core.content.edit
import com.oliver.loqin.data.prefs.DiagnosticsTimelineStore

/**
 * Versioned migration gate for 2.3.x settings additions.
 * Feature-specific stores can keep their own migrations, while this gives the app one durable schema marker.
 */
object SettingsSchemaMigration {
    const val CURRENT_SCHEMA_VERSION = 228
    private const val PREFS = "loqin_prefs"
    private const val KEY_SCHEMA_VERSION = "settings_schema_version"

    data class Result(val from: Int, val to: Int, val changed: Boolean)

    @Synchronized
    fun ensureCurrent(context: Context): Result {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.all[KEY_SCHEMA_VERSION]
        val from = readSchemaVersion(raw)

        // Some backup/import paths and older builds may have persisted this numeric marker as a Long.
        // SharedPreferences#getInt throws ClassCastException in that case, which can crash Application.onCreate.
        // Normalize any compatible numeric/string representation back to Int before continuing.
        if (raw != null && raw !is Int) {
            prefs.edit { putInt(KEY_SCHEMA_VERSION, from) }
            DiagnosticsTimelineStore.record(
                context,
                "Migration",
                "Settings schema type normalized",
                "fromType=${raw.javaClass.simpleName} value=$from",
            )
        }

        if (from >= CURRENT_SCHEMA_VERSION) return Result(from, from, false)

        // The top-level schema marker is groundwork for staged 2.3.x additions. Existing per-feature migrations remain authoritative.
        prefs.edit { putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION) }
        DiagnosticsTimelineStore.record(
            context,
            "Migration",
            "Settings schema migrated",
            "from=$from to=$CURRENT_SCHEMA_VERSION",
        )
        return Result(from, CURRENT_SCHEMA_VERSION, true)
    }

    fun currentStoredVersion(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return readSchemaVersion(prefs.all[KEY_SCHEMA_VERSION])
    }

    private fun readSchemaVersion(raw: Any?): Int = when (raw) {
        is Int -> raw
        is Long -> raw.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        is Number -> raw.toInt()
        is String -> raw.toIntOrNull() ?: 0
        else -> 0
    }
}
