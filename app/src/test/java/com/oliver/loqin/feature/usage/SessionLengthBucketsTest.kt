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
import org.junit.Test

class SessionLengthBucketsTest {

    private val minute = 60_000L

    @Test
    fun indexForPlacesBoundariesInTheBucketAbove() {
        assertEquals(0, SessionLengthBuckets.indexFor(0L))
        assertEquals(0, SessionLengthBuckets.indexFor(minute - 1))
        assertEquals(1, SessionLengthBuckets.indexFor(minute))
        assertEquals(1, SessionLengthBuckets.indexFor(5 * minute - 1))
        assertEquals(2, SessionLengthBuckets.indexFor(5 * minute))
        assertEquals(2, SessionLengthBuckets.indexFor(15 * minute - 1))
        assertEquals(3, SessionLengthBuckets.indexFor(15 * minute))
        assertEquals(3, SessionLengthBuckets.indexFor(30 * minute - 1))
        assertEquals(4, SessionLengthBuckets.indexFor(30 * minute))
        assertEquals(4, SessionLengthBuckets.indexFor(10 * 60 * minute))
    }

    @Test
    fun negativeDurationsCountAsZero() {
        assertEquals(0, SessionLengthBuckets.indexFor(-5_000L))
    }

    @Test
    fun countsForTalliesEachBucket() {
        val durations = listOf(
            10_000L,
            2 * minute,
            3 * minute,
            7 * minute,
            20 * minute,
            45 * minute,
            45 * minute,
        )
        assertEquals(listOf(1, 2, 1, 1, 2), SessionLengthBuckets.countsFor(durations))
    }

    @Test
    fun countsForAlwaysReturnsFiveBuckets() {
        assertEquals(listOf(0, 0, 0, 0, 0), SessionLengthBuckets.countsFor(emptyList()))
    }
}
