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

class UsageComparisonTest {

    private val minute = 60_000L

    @Test
    fun noPreviousDataHidesComparison() {
        assertNull(UsageComparison.summaryChange(currentMs = 30 * minute, previousMs = 0L))
        assertNull(UsageComparison.summaryChange(currentMs = 30 * minute, previousMs = -5L))
    }

    @Test
    fun percentShownWhenBothSidesReachBaseline() {
        assertEquals(
            UsageComparison.Change.Percent(-50),
            UsageComparison.summaryChange(currentMs = 60 * minute, previousMs = 120 * minute)
        )
        assertEquals(
            UsageComparison.Change.Percent(50),
            UsageComparison.summaryChange(currentMs = 15 * minute, previousMs = 10 * minute)
        )
    }

    @Test
    fun zeroCurrentFallsBackToMinutesBecauseCurrentIsBelowBaseline() {
        assertEquals(
            UsageComparison.Change.Minutes(-10),
            UsageComparison.summaryChange(currentMs = 0L, previousMs = 10 * minute)
        )
    }

    @Test
    fun smallPreviousFallsBackToAbsoluteMinutes() {
        assertEquals(
            UsageComparison.Change.Minutes(15),
            UsageComparison.summaryChange(currentMs = 20 * minute, previousMs = 5 * minute)
        )
    }

    @Test
    fun smallCurrentFallsBackToAbsoluteMinutes() {
        assertEquals(
            UsageComparison.Change.Minutes(-9),
            UsageComparison.summaryChange(currentMs = 3 * minute, previousMs = 12 * minute)
        )
    }

    @Test
    fun exactBaselineStillUsesPercent() {
        assertEquals(
            UsageComparison.Change.Percent(100),
            UsageComparison.summaryChange(
                currentMs = 20 * minute,
                previousMs = UsageComparison.PERCENT_MIN_BASELINE_MS
            )
        )
    }

    @Test
    fun equalOrNegligibleDifferenceIsSame() {
        assertEquals(
            UsageComparison.Change.Same,
            UsageComparison.summaryChange(currentMs = 30 * minute, previousMs = 30 * minute)
        )
        // 20 s on 30 min is about 1.1%, which rounds to a real percent rather than Same.
        assertEquals(
            UsageComparison.Change.Percent(1),
            UsageComparison.summaryChange(currentMs = 30 * minute + 20_000L, previousMs = 30 * minute)
        )
        assertEquals(
            UsageComparison.Change.Same,
            UsageComparison.summaryChange(currentMs = 1_000 * minute + 200L * 1000L, previousMs = 1_000 * minute)
        )
    }

    @Test
    fun rowDeltaOmittedWhenBothZero() {
        assertNull(UsageComparison.rowDeltaMinutes(currentMs = 0L, previousMs = 0L))
        assertNull(UsageComparison.rowDeltaMinutes(currentMs = -1L, previousMs = 0L))
    }

    @Test
    fun rowDeltaIsSignedMinutes() {
        assertEquals(-5, UsageComparison.rowDeltaMinutes(currentMs = 0L, previousMs = 5 * minute))
        assertEquals(12, UsageComparison.rowDeltaMinutes(currentMs = 12 * minute, previousMs = 0L))
        assertEquals(-12, UsageComparison.rowDeltaMinutes(currentMs = 8 * minute, previousMs = 20 * minute))
    }

    @Test
    fun rowDeltaRoundsToNearestMinute() {
        assertEquals(2, UsageComparison.rowDeltaMinutes(currentMs = 90_000L, previousMs = 0L))
        assertEquals(0, UsageComparison.rowDeltaMinutes(currentMs = 1_000L, previousMs = 0L))
    }
}
