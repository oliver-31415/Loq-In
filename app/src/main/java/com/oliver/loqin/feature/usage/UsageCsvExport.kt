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

package com.oliver.loqin.feature.usage

/** Pure CSV formatting for the usage export. No Android types, so it stays unit-testable. */
object UsageCsvExport {
    const val DAYS = 90
    const val HEADER = "date,package,app_name,screen_time_minutes,opens,blocks"

    /** Quotes a field containing a comma, quote or line break, doubling inner quotes (RFC 4180). */
    fun escape(field: String): String {
        val needsQuotes = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    // A row is written only when at least one measure is non-zero.
    fun hasData(screenTimeMs: Long, opens: Int, blocks: Int): Boolean =
        screenTimeMs > 0L || opens > 0 || blocks > 0

    // Whole minutes, truncated, so a 40-second session is recorded as 0 rather than rounded up.
    fun row(
        date: String,
        packageName: String,
        appName: String,
        screenTimeMs: Long,
        opens: Int,
        blocks: Int
    ): String = listOf(
        date,
        packageName,
        appName,
        (screenTimeMs / 60_000L).toString(),
        opens.toString(),
        blocks.toString()
    ).joinToString(",") { escape(it) }
}
