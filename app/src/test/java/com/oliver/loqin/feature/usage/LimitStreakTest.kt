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

import com.oliver.loqin.feature.usage.LimitStreak.DayRecord
import com.oliver.loqin.feature.usage.LimitStreak.DayStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LimitStreakTest {

    private val counted = DayRecord(protectionMs = 3_600_000L, limitHits = 0)
    private val miss = DayRecord(protectionMs = 3_600_000L, limitHits = 1)
    private val neutral = DayRecord(protectionMs = 0L, limitHits = 0)

    @Test
    fun emptyHistoryHasNoStreak() {
        val result = LimitStreak.evaluate(emptyList())

        assertEquals(0, result.current)
        assertEquals(0, result.best)
        assertEquals(emptyList<DayStatus>(), result.recentStatuses)
    }

    @Test
    fun neutralDaysNeitherExtendNorBreakTheStreak() {
        val result = LimitStreak.evaluate(listOf(counted, counted, neutral, counted))

        assertEquals(3, result.current)
        assertEquals(3, result.best)
        assertEquals(DayStatus.NO_PROTECTION, result.recentStatuses[2])
    }

    @Test
    fun neutralOnlyHistoryStaysAtZero() {
        val result = LimitStreak.evaluate(listOf(neutral, neutral))

        assertEquals(0, result.current)
        assertEquals(0, result.best)
    }

    @Test
    fun withinLimitsDaysAccumulate() {
        val result = LimitStreak.evaluate(listOf(counted, counted, counted))

        assertEquals(3, result.current)
        assertEquals(3, result.best)
        assertEquals(
            listOf(DayStatus.WITHIN_LIMITS, DayStatus.WITHIN_LIMITS, DayStatus.WITHIN_LIMITS),
            result.recentStatuses,
        )
    }

    @Test
    fun singleMissIsForgivenWithoutAddingToTheStreak() {
        val result = LimitStreak.evaluate(listOf(counted, counted, miss, counted))

        assertEquals(3, result.current)
        assertEquals(3, result.best)
        assertEquals(DayStatus.GRACE, result.recentStatuses[2])
    }

    @Test
    fun secondMissWithinSevenDaysEndsTheStreak() {
        val result = LimitStreak.evaluate(listOf(counted, counted, miss, counted, miss))

        assertEquals(0, result.current)
        assertEquals(3, result.best)
        assertEquals(DayStatus.LIMIT_REACHED, result.recentStatuses[4])
    }

    @Test
    fun secondMissSixDaysAfterTheFirstStillEndsTheStreak() {
        val history = listOf(counted, miss, counted, counted, counted, counted, counted, miss)

        val result = LimitStreak.evaluate(history)

        assertEquals(0, result.current)
        assertEquals(6, result.best)
    }

    @Test
    fun missesSevenDaysApartAreBothForgiven() {
        val history = listOf(
            counted, miss, counted, counted, counted, counted, counted, counted, miss, counted,
        )

        val result = LimitStreak.evaluate(history)

        assertEquals(8, result.current)
        assertEquals(8, result.best)
        assertEquals(
            listOf(
                DayStatus.WITHIN_LIMITS,
                DayStatus.WITHIN_LIMITS,
                DayStatus.WITHIN_LIMITS,
                DayStatus.WITHIN_LIMITS,
                DayStatus.WITHIN_LIMITS,
                DayStatus.GRACE,
                DayStatus.WITHIN_LIMITS,
            ),
            result.recentStatuses,
        )
    }

    @Test
    fun todayWithoutProtectionYetIsNeutral() {
        val result = LimitStreak.evaluate(listOf(counted, counted, neutral))

        assertEquals(2, result.current)
    }

    @Test
    fun todayWithHitsCountsAsMissAndCanBreakTheStreak() {
        val inGrace = LimitStreak.evaluate(listOf(counted, counted, counted, miss))
        assertEquals(3, inGrace.current)

        val broken = LimitStreak.evaluate(listOf(counted, miss, counted, counted, miss))
        assertEquals(0, broken.current)
    }

    @Test
    fun bestSurvivesALaterBreak() {
        val result = LimitStreak.evaluate(listOf(counted, counted, counted, miss, miss, counted))

        assertEquals(1, result.current)
        assertEquals(3, result.best)
    }

    @Test
    fun recentStatusesCoverTheLastSevenDaysOldestFirst() {
        val history = listOf(
            miss, counted, neutral, counted, miss, counted, counted, neutral, counted,
        )

        val result = LimitStreak.evaluate(history)

        assertEquals(
            listOf(
                DayStatus.NO_PROTECTION,
                DayStatus.WITHIN_LIMITS,
                DayStatus.LIMIT_REACHED,
                DayStatus.WITHIN_LIMITS,
                DayStatus.WITHIN_LIMITS,
                DayStatus.NO_PROTECTION,
                DayStatus.WITHIN_LIMITS,
            ),
            result.recentStatuses,
        )
    }
}
