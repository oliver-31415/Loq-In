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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class OpensBlocksTest {

    private fun atMs(year: Int, month1: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            set(year, month1 - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun dayYmdsCoverEveryLocalDayInclusive() {
        val days = OpensBlocks.dayYmds(atMs(2026, 10, 5, 9), atMs(2026, 10, 11, 20))
        assertEquals(listOf(20261005, 20261006, 20261007, 20261008, 20261009, 20261010, 20261011), days)
    }

    @Test
    fun dayYmdsCrossMonthBoundary() {
        val days = OpensBlocks.dayYmds(atMs(2026, 2, 27, 0), atMs(2026, 3, 2, 23))
        assertEquals(listOf(20260227, 20260228, 20260301, 20260302), days)
    }

    @Test
    fun dayYmdsSingleDayAndInvertedRange() {
        assertEquals(listOf(20261011), OpensBlocks.dayYmds(atMs(2026, 10, 11, 1), atMs(2026, 10, 11, 23)))
        assertEquals(emptyList<Int>(), OpensBlocks.dayYmds(atMs(2026, 10, 11, 9), atMs(2026, 10, 10, 9)))
    }

    @Test
    fun previousYmdsMatchLengthAndPrecedeRange() {
        val week = OpensBlocks.dayYmds(atMs(2026, 10, 5, 9), atMs(2026, 10, 11, 9))
        assertEquals(
            listOf(20260928, 20260929, 20260930, 20261001, 20261002, 20261003, 20261004),
            OpensBlocks.previousYmds(week),
        )
    }

    @Test
    fun previousYmdsCrossYearBoundary() {
        assertEquals(listOf(20251230, 20251231), OpensBlocks.previousYmds(listOf(20260101, 20260102)))
    }

    @Test
    fun previousYmdsEmptyWhenNoDays() {
        assertEquals(emptyList<Int>(), OpensBlocks.previousYmds(emptyList()))
    }

    @Test
    fun changePercentIsSignedAndRounded() {
        assertEquals(-20, OpensBlocks.changePercent(current = 8, previous = 10))
        assertEquals(20, OpensBlocks.changePercent(current = 12, previous = 10))
        assertEquals(-100, OpensBlocks.changePercent(current = 0, previous = 4))
        assertEquals(-33, OpensBlocks.changePercent(current = 2, previous = 3))
        assertEquals(0, OpensBlocks.changePercent(current = 10, previous = 10))
    }

    @Test
    fun changePercentHiddenWithoutPreviousData() {
        assertNull(OpensBlocks.changePercent(current = 5, previous = 0))
        assertNull(OpensBlocks.changePercent(current = 5, previous = -1))
    }

    @Test
    fun trendNeedsAtLeastTwoCurrentDays() {
        assertNull(OpensBlocks.trendPercent(currentDays = 1, current = 3, previous = 6))
        assertEquals(-50, OpensBlocks.trendPercent(currentDays = 2, current = 3, previous = 6))
    }
}
