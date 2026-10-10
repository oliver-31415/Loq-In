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
 * Rules for the "days within limits" streak.
 *
 * A day counts when protection was active and no limit was reached. Protection
 * active with at least one limit hit is a miss. A day without protection is
 * neutral and neither extends nor breaks the streak. One miss inside any rolling
 * seven-day window is forgiven (the day does not add to the streak); a second
 * miss inside that window ends the streak.
 */
object LimitStreak {
    /** Number of days the streak is computed over. */
    const val HISTORY_DAYS = 365

    /** Rolling window in which a single miss is forgiven. */
    const val GRACE_WINDOW_DAYS = 7

    /** Number of day markers shown on the card. */
    const val MARKER_DAYS = 7

    /**
     * One day of input. Callers pass consecutive days, oldest first; the last
     * entry is today (which may still be in progress).
     */
    data class DayRecord(val protectionMs: Long, val limitHits: Int)

    enum class DayStatus {
        WITHIN_LIMITS,
        GRACE,
        LIMIT_REACHED,
        NO_PROTECTION,
    }

    /** [recentStatuses] holds the last [MARKER_DAYS] days, oldest first. */
    data class Result(
        val current: Int,
        val best: Int,
        val recentStatuses: List<DayStatus>,
    )

    fun evaluate(days: List<DayRecord>): Result {
        var streak = 0
        var best = 0
        var lastMissIndex: Int? = null
        val statuses = ArrayList<DayStatus>(days.size)

        days.forEachIndexed { index, day ->
            val status = when {
                day.protectionMs <= 0L -> DayStatus.NO_PROTECTION
                day.limitHits <= 0 -> {
                    streak++
                    DayStatus.WITHIN_LIMITS
                }
                else -> {
                    val previousMiss = lastMissIndex
                    lastMissIndex = index
                    when {
                        previousMiss != null && index - previousMiss < GRACE_WINDOW_DAYS -> {
                            streak = 0
                            DayStatus.LIMIT_REACHED
                        }
                        streak > 0 -> DayStatus.GRACE
                        else -> DayStatus.LIMIT_REACHED
                    }
                }
            }
            best = maxOf(best, streak)
            statuses.add(status)
        }

        return Result(
            current = streak,
            best = best,
            recentStatuses = statuses.takeLast(MARKER_DAYS),
        )
    }
}
