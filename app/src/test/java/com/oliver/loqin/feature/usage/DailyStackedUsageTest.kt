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
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyStackedUsageTest {

    private val none: (String) -> Boolean = { false }

    @Test
    fun `keeps top apps and folds the rest into other`() {
        val days = listOf(
            mapOf("a" to 100L, "b" to 50L, "c" to 40L, "d" to 30L, "e" to 20L, "f" to 10L),
        )
        val result = DailyStackedUsage.build(days, previous = emptyList(), topN = 2, hide = none)

        assertEquals(listOf("a", "b", DailyStackedUsage.OTHER_KEY), result.legendKeys)
        val day = result.days.single()
        assertEquals(250L, day.totalMs)
        assertEquals(
            listOf(
                DailyStackedUsage.Segment("a", 100L),
                DailyStackedUsage.Segment("b", 50L),
                DailyStackedUsage.Segment(DailyStackedUsage.OTHER_KEY, 100L),
            ),
            day.segments,
        )
    }

    @Test
    fun `no other segment when every app fits in top n`() {
        val days = listOf(mapOf("a" to 10L, "b" to 5L))
        val result = DailyStackedUsage.build(days, previous = emptyList(), topN = 4, hide = none)

        assertEquals(listOf("a", "b"), result.legendKeys)
        assertEquals(2, result.days.single().segments.size)
    }

    @Test
    fun `hidden packages are dropped from totals and segments`() {
        val days = listOf(mapOf("a" to 100L, "launcher" to 900L))
        val result = DailyStackedUsage.build(days, previous = emptyList(), topN = 4) { it == "launcher" }

        assertEquals(listOf("a"), result.legendKeys)
        assertEquals(100L, result.days.single().totalMs)
    }

    @Test
    fun `ranking uses totals across all days`() {
        val days = listOf(
            mapOf("a" to 60L, "b" to 10L),
            mapOf("a" to 0L, "b" to 80L),
        )
        val result = DailyStackedUsage.build(days, previous = emptyList(), topN = 1, hide = none)

        assertEquals(listOf("b", DailyStackedUsage.OTHER_KEY), result.legendKeys)
        assertEquals(
            listOf(DailyStackedUsage.Segment("b", 10L), DailyStackedUsage.Segment(DailyStackedUsage.OTHER_KEY, 60L)),
            result.days[0].segments,
        )
        assertEquals(
            listOf(DailyStackedUsage.Segment("b", 80L), DailyStackedUsage.Segment(DailyStackedUsage.OTHER_KEY, 0L)),
            result.days[1].segments,
        )
    }

    @Test
    fun `previous totals pass through and clamp negatives`() {
        val result = DailyStackedUsage.build(
            days = listOf(mapOf("a" to 1L)),
            previous = listOf(30L, -5L),
            hide = none,
        )
        assertEquals(listOf(30L, 0L), result.previousMs)
    }

    @Test
    fun `empty input gives empty result`() {
        val result = DailyStackedUsage.build(emptyList(), previous = emptyList(), hide = none)
        assertTrue(result.days.isEmpty())
        assertTrue(result.legendKeys.isEmpty())
    }
}
