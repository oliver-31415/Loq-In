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
 * One-line takeaway under the session-length histogram. Returns null when the shape of the
 * distribution is not clear enough to say anything useful, which is the common case.
 */
object SessionHint {

    enum class Hint { MOSTLY_SHORT, OFTEN_LONG }

    /** Short-session hint needs at least this many sessions, so a couple of quick opens never trigger it. */
    const val MIN_SESSIONS_FOR_SHORT = 10

    private const val SHORT_SHARE_PERCENT = 70L
    private const val LONG_SHARE_PERCENT = 40L

    /**
     * [counts] is laid out as [SessionLengthBuckets]. Most sessions under 5 minutes (with at least
     * [MIN_SESSIONS_FOR_SHORT] sessions) gives [Hint.MOSTLY_SHORT]. At least 40% of sessions at 15
     * minutes or longer gives [Hint.OFTEN_LONG]. Otherwise null.
     */
    fun forBuckets(counts: List<Int>): Hint? {
        require(counts.size == SessionLengthBuckets.BUCKET_COUNT) { "Expected ${SessionLengthBuckets.BUCKET_COUNT} buckets" }
        val safe = counts.map { it.coerceAtLeast(0).toLong() }
        val total = safe.sum()
        if (total <= 0L) return null
        val short = safe[0] + safe[1]
        val long = safe[3] + safe[4]
        if (total >= MIN_SESSIONS_FOR_SHORT && short * 100L >= total * SHORT_SHARE_PERCENT) {
            return Hint.MOSTLY_SHORT
        }
        if (long * 100L >= total * LONG_SHARE_PERCENT) {
            return Hint.OFTEN_LONG
        }
        return null
    }
}
