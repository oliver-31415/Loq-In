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

/**
 * Buckets an app's session lengths for the session histogram on the app detail page.
 * Buckets are `<1 min`, `1–5`, `5–15`, `15–30` and `30+` minutes; a bucket's lower bound is inclusive.
 */
object SessionLengthBuckets {
    const val BUCKET_COUNT = 5

    private const val MINUTE_MS = 60_000L

    /** Exclusive upper bounds in minutes for every bucket except the last, which is open-ended. */
    private val upperBoundsMinutes = longArrayOf(1L, 5L, 15L, 30L)

    /** Index of the bucket that [durationMs] falls into. Negative durations count as zero. */
    fun indexFor(durationMs: Long): Int {
        val duration = durationMs.coerceAtLeast(0L)
        for (index in upperBoundsMinutes.indices) {
            if (duration < upperBoundsMinutes[index] * MINUTE_MS) return index
        }
        return BUCKET_COUNT - 1
    }

    /** Session counts per bucket, always [BUCKET_COUNT] entries long, even when [durationsMs] is empty. */
    fun countsFor(durationsMs: List<Long>): List<Int> {
        val counts = IntArray(BUCKET_COUNT)
        for (duration in durationsMs) {
            counts[indexFor(duration)]++
        }
        return counts.toList()
    }
}
