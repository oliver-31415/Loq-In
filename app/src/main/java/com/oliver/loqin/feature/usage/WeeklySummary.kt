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
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Pure rules for the Monday weekly summary notification.
 *
 * "Last week" is the seven calendar days ending yesterday, relative to the moment the summary is sent.
 * The week before it is the seven days ending the day before that.
 */
object WeeklySummary {

    /** Stored screen-time days required before any summary is sent. */
    const val MIN_DAYS_WITH_USAGE = 7

    /** Local hour on Monday when the summary is sent. */
    const val SEND_HOUR = 9

    /** Totals for last week, with screen time for the week before for the comparison. */
    data class Totals(
        val screenMs: Long,
        val previousScreenMs: Long,
        val blocks: Int,
        val focusMs: Long,
    )

    /** yyyymmdd values for every day whose distance from [nowMs] in whole days lies in [daysAgo]. */
    fun ymdsForDaysAgo(nowMs: Long, zone: TimeZone, daysAgo: IntRange): Set<Int> {
        val out = linkedSetOf<Int>()
        for (offset in daysAgo) {
            val cal = Calendar.getInstance(zone).apply {
                timeInMillis = nowMs
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            out += ymdOf(cal)
        }
        return out
    }

    /** Whether there is enough stored history and something to report. */
    fun shouldSend(daysWithUsage: Int, totals: Totals): Boolean {
        if (daysWithUsage < MIN_DAYS_WITH_USAGE) {
            return false
        }
        return totals.screenMs > 0L || totals.blocks > 0 || totals.focusMs > 0L
    }

    /**
     * Percent by which [current] is below [previous].
     * Positive means less than before, negative means more, zero means the same.
     * Returns null when there is no baseline to compare against.
     */
    fun changePercent(current: Long, previous: Long): Int? {
        if (previous <= 0L) {
            return null
        }
        return ((previous - current).toDouble() * 100.0 / previous.toDouble()).roundToInt()
    }

    /**
     * Milliseconds from [nowMs] until the next Monday at [SEND_HOUR]:00 in [zone].
     * A Monday slot that is still ahead of [nowMs] counts as the next send.
     */
    fun delayUntilNextSend(nowMs: Long, zone: TimeZone): Long {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, SEND_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || cal.timeInMillis <= nowMs) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis - nowMs
    }

    private fun ymdOf(cal: Calendar): Int {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return (year * 10000) + (month * 100) + day
    }
}
