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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class InsightsWindowTest {

    @Test
    fun sevenDayWindowLabelsEveryDay() {
        for (i in 0 until 7) assertTrue(InsightsWindow.shouldLabel(i, 7))
    }

    @Test
    fun thirtyDayWindowLabelsTodayAndEveryFifthDayBefore() {
        val labelled = (0 until 30).filter { InsightsWindow.shouldLabel(it, 30) }
        assertEquals(listOf(4, 9, 14, 19, 24, 29), labelled)
        assertTrue(InsightsWindow.shouldLabel(29, 30))
        assertFalse(InsightsWindow.shouldLabel(28, 30))
    }

    @Test
    fun outOfRangeIndexIsNotLabelled() {
        assertFalse(InsightsWindow.shouldLabel(-1, 7))
        assertFalse(InsightsWindow.shouldLabel(7, 7))
    }

    @Test
    fun ymdsEndOnTodayAndCrossMonthBoundary() {
        val today = Calendar.getInstance().apply { clear(); set(2026, Calendar.MARCH, 2) }
        val ymds = InsightsWindow.ymdsEndingOn(today, 4)
        assertEquals(listOf(20260227, 20260228, 20260301, 20260302), ymds)
        // Input calendar must not be mutated.
        assertEquals(2, today.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun ymdsHandlesZeroCount() {
        assertTrue(InsightsWindow.ymdsEndingOn(Calendar.getInstance(), 0).isEmpty())
    }
}
