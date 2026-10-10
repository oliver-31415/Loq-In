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

import com.oliver.loqin.feature.usage.PauseWeekSummary.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PauseWeekSummaryTest {

    @Test
    fun noPausesThisWeekGivesNoPercent() {
        val r = PauseWeekSummary.build(0, 0, 0, 0)
        assertNull(r.percent)
        assertNull(r.previousPercent)
        assertNull(r.trend)
    }

    @Test
    fun percentRoundsLeftShare() {
        // 41 of 100 pauses stepped away.
        val r = PauseWeekSummary.build(41, 59, 0, 0)
        assertEquals(41, r.percent)
    }

    @Test
    fun previousWeekBelowThresholdHasNoComparison() {
        val r = PauseWeekSummary.build(2, 2, 1, 1)
        assertEquals(50, r.percent)
        assertNull(r.previousPercent)
        assertNull(r.trend)
    }

    @Test
    fun previousWeekAtThresholdIsCompared() {
        // Previous week: 1 of 3 stepped away = 33%. This week 41%, so up.
        val r = PauseWeekSummary.build(41, 59, 1, 2)
        assertEquals(33, r.previousPercent)
        assertEquals(Trend.UP, r.trend)
    }

    @Test
    fun lowerThisWeekIsDown() {
        val r = PauseWeekSummary.build(3, 7, 3, 7)
        assertEquals(30, r.percent)
        assertEquals(30, r.previousPercent)
        assertNull(r.trend)

        val down = PauseWeekSummary.build(2, 8, 3, 7)
        assertEquals(Trend.DOWN, down.trend)
    }

    @Test
    fun negativeInputsAreTreatedAsZero() {
        val r = PauseWeekSummary.build(-5, 4, -1, 0)
        assertEquals(0, r.percent)
        assertNull(r.previousPercent)
    }
}
