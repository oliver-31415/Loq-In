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

// Pure day-window maths for the Insights charts. Free of Android types so it can be unit tested.
object InsightsWindow {

    // Every N-th day is labelled on the 30-day view; the 7-day view labels every day.
    const val LABEL_EVERY_DAYS = 5
    private const val SHORT_WINDOW_DAYS = 7

    /**
     * Whether the day at [index] (0 = oldest) of a [count]-day window gets a label.
     * On longer windows the labels are anchored to today so the latest day is always labelled.
     */
    fun shouldLabel(index: Int, count: Int): Boolean {
        if (index !in 0 until count) return false
        if (count <= SHORT_WINDOW_DAYS) return true
        return (count - 1 - index) % LABEL_EVERY_DAYS == 0
    }

    /**
     * YYYYMMDD values for the [count] days ending on the day of [today], oldest first.
     * The calendar passed in is not modified.
     */
    fun ymdsEndingOn(today: Calendar, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val cal = today.clone() as Calendar
        val out = ArrayList<Int>(count)
        repeat(count) {
            out += ymdOf(cal)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return out.reversed()
    }

    fun ymdOf(cal: Calendar): Int {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return (y * 10000) + (m * 100) + d
    }
}
