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

import java.util.Calendar
import kotlin.math.roundToInt

// Pure day and trend maths for the per-app opens and blocks card. Free of Android types so it can be unit tested.
object OpensBlocks {

    // A trend needs at least two days in the current period, otherwise "down 100%" is meaningless.
    const val MIN_DAYS_FOR_TREND = 2

    /** Local day numbers (yyyymmdd) from the day of [startMs] through the day of [endMs], inclusive. */
    fun dayYmds(startMs: Long, endMs: Long): List<Int> {
        if (endMs < startMs) {
            return emptyList()
        }
        val cursor = calendarForMs(startMs)
        val last = calendarForMs(endMs)
        val out = ArrayList<Int>()
        while (!cursor.after(last)) {
            out += ymd(cursor)
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }

    /** The same number of local days as [days], immediately before them, oldest first. */
    fun previousYmds(days: List<Int>): List<Int> {
        val first = days.firstOrNull() ?: return emptyList()
        val cursor = calendarForYmd(first)
        val out = ArrayList<Int>(days.size)
        repeat(days.size) {
            cursor.add(Calendar.DAY_OF_YEAR, -1)
            out += ymd(cursor)
        }
        return out.reversed()
    }

    /**
     * Signed percent change from [previous] to [current], rounded to a whole number.
     * Returns null when there is no previous data to compare against.
     */
    fun changePercent(current: Int, previous: Int): Int? {
        if (previous <= 0) {
            return null
        }
        return ((current - previous).toDouble() * 100.0 / previous.toDouble()).roundToInt()
    }

    /** Like [changePercent], but only when the current period has enough days to be a trend. */
    fun trendPercent(currentDays: Int, current: Int, previous: Int): Int? {
        if (currentDays < MIN_DAYS_FOR_TREND) {
            return null
        }
        return changePercent(current, previous)
    }

    private fun calendarForMs(ms: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun calendarForYmd(ymd: Int): Calendar = Calendar.getInstance().apply {
        set(ymd / 10_000, (ymd / 100 % 100) - 1, ymd % 100, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun ymd(c: Calendar): Int =
        c.get(Calendar.YEAR) * 10_000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}
