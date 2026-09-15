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
 * Small local-only event timeline used by Support diagnostics.
 * It intentionally stores short operational summaries rather than user content.
 */
object DiagnosticsTimelineStore {
    private const val PREFS = "loqin_diagnostics_timeline"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 240
    private const val SEP = '\u001E'
    private const val FIELD_SEP = '\u001F'

    data class Entry(
        val timestampMillis: Long,
        val category: String,
        val event: String,
        val details: String,
    )

    @Synchronized
    fun record(context: Context, category: String, event: String, details: String = "") {
        val cleanCategory = clean(category, 40)
        val cleanEvent = clean(event, 180)
        val cleanDetails = clean(details, 360)
        if (cleanCategory.isBlank() || cleanEvent.isBlank()) return

        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_EVENTS, null)
            ?.split(SEP)
            ?.filter { it.isNotBlank() }
            ?.toMutableList()
            ?: mutableListOf()
        val encoded = listOf(
            System.currentTimeMillis().toString(),
            cleanCategory,
            cleanEvent,
            cleanDetails,
        ).joinToString(FIELD_SEP.toString())

        if (current.lastOrNull()?.substringAfter(FIELD_SEP, "") != encoded.substringAfter(FIELD_SEP, "")) {
            current += encoded
        }
        while (current.size > MAX_EVENTS) current.removeAt(0)
        prefs.edit { putString(KEY_EVENTS, current.joinToString(SEP.toString())) }
    }

    fun latest(context: Context, limit: Int = 40): List<Entry> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_EVENTS, null)
            ?.split(SEP)
            ?.asSequence()
            ?.filter { it.isNotBlank() }
            ?.mapNotNull(::decode)
            ?.toList()
            .orEmpty()
            .takeLast(limit.coerceIn(1, MAX_EVENTS))
    }

    @Synchronized
    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
    }

    private fun decode(raw: String): Entry? {
        val parts = raw.split(FIELD_SEP, limit = 4)
        if (parts.size < 3) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        return Entry(timestamp, parts[1], parts[2], parts.getOrElse(3) { "" })
    }

    private fun clean(value: String, max: Int): String = value
        .replace(SEP, ' ')
        .replace(FIELD_SEP, ' ')
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
        .take(max)
}
