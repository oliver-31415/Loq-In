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

import com.oliver.loqin.feature.usage.WeeklySummary.Totals
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklySummaryTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    /** Milliseconds for a UTC wall-clock time. Month is 1-based. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis
    }

    private val goodTotals = Totals(screenMs = 1_000L, previousScreenMs = 2_000L, blocks = 0, focusMs = 0L)

    @Test
    fun lastWeekIsSevenDaysEndingYesterday() {
        // Monday 2026-10-12 10:00 UTC
        val now = at(2026, 10, 12, 10)
        val expected = setOf(20261011, 20261010, 20261009, 20261008, 20261007, 20261006, 20261005)
        assertEquals(expected, WeeklySummary.ymdsForDaysAgo(now, utc, 1..7))
    }

    @Test
    fun previousWeekIsTheSevenDaysBeforeThat() {
        val now = at(2026, 10, 12, 10)
        val expected = setOf(20261004, 20261003, 20261002, 20261001, 20260930, 20260929, 20260928)
        assertEquals(expected, WeeklySummary.ymdsForDaysAgo(now, utc, 8..14))
    }

    @Test
    fun ymdsCrossMonthAndYearBoundaries() {
        // Thursday 2027-01-07 10:00 UTC
        val now = at(2027, 1, 7, 10)
        val expected = setOf(20270106, 20270105, 20270104, 20270103, 20270102, 20270101, 20261231)
        assertEquals(expected, WeeklySummary.ymdsForDaysAgo(now, utc, 1..7))
    }

    @Test
    fun noSummaryBeforeSevenDaysOfUsage() {
        assertFalse(WeeklySummary.shouldSend(daysWithUsage = 6, totals = goodTotals))
        assertTrue(WeeklySummary.shouldSend(daysWithUsage = 7, totals = goodTotals))
    }

    @Test
    fun noSummaryWhenNothingToReport() {
        val empty = Totals(screenMs = 0L, previousScreenMs = 500L, blocks = 0, focusMs = 0L)
        assertFalse(WeeklySummary.shouldSend(daysWithUsage = 30, totals = empty))
    }

    @Test
    fun blocksAloneAreEnoughToReport() {
        val onlyBlocks = Totals(screenMs = 0L, previousScreenMs = 0L, blocks = 3, focusMs = 0L)
        assertTrue(WeeklySummary.shouldSend(daysWithUsage = 7, totals = onlyBlocks))
    }

    @Test
    fun focusAloneIsEnoughToReport() {
        val onlyFocus = Totals(screenMs = 0L, previousScreenMs = 0L, blocks = 0, focusMs = 60_000L)
        assertTrue(WeeklySummary.shouldSend(daysWithUsage = 7, totals = onlyFocus))
    }

    @Test
    fun changePercentPositiveMeansLessThanBefore() {
        assertEquals(70, WeeklySummary.changePercent(current = 300L, previous = 1_000L))
    }

    @Test
    fun changePercentNegativeMeansMoreThanBefore() {
        assertEquals(-20, WeeklySummary.changePercent(current = 1_200L, previous = 1_000L))
    }

    @Test
    fun changePercentZeroMeansSame() {
        assertEquals(0, WeeklySummary.changePercent(current = 1_000L, previous = 1_000L))
    }

    @Test
    fun changePercentRoundsToNearestWholePercent() {
        // 18.4% less rounds to 18
        assertEquals(18, WeeklySummary.changePercent(current = 8_160L, previous = 10_000L))
    }

    @Test
    fun changePercentIsNullWithoutBaseline() {
        assertNull(WeeklySummary.changePercent(current = 1_000L, previous = 0L))
    }

    @Test
    fun delayFromWednesdayIsToNextMondayAtNine() {
        // Wednesday 2026-10-07 12:00 UTC -> Monday 2026-10-12 09:00 UTC
        val now = at(2026, 10, 7, 12)
        val expected = (4L * 24 + 21) * 3_600_000L
        assertEquals(expected, WeeklySummary.delayUntilNextSend(now, utc))
    }

    @Test
    fun mondayBeforeNineIsSameDay() {
        // Monday 2026-10-12 08:00 UTC
        val now = at(2026, 10, 12, 8)
        assertEquals(3_600_000L, WeeklySummary.delayUntilNextSend(now, utc))
    }

    @Test
    fun mondayAtExactlyNineWaitsAWeek() {
        val now = at(2026, 10, 12, 9)
        assertEquals(7L * 24 * 3_600_000L, WeeklySummary.delayUntilNextSend(now, utc))
    }

    @Test
    fun mondayAfterNineWaitsUntilNextWeek() {
        // Monday 2026-10-12 10:00 UTC -> Monday 2026-10-19 09:00 UTC
        val now = at(2026, 10, 12, 10)
        assertEquals((6L * 24 + 23) * 3_600_000L, WeeklySummary.delayUntilNextSend(now, utc))
    }
}
