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

import kotlin.math.roundToInt

// Pure comparison maths for the statistics page. Kept free of Android types so the rules can be unit tested.
object UsageComparison {

    // Percentages on a tiny baseline swing wildly (2 min -> 9 min is +350%), so both sides must reach this before a percent is shown.
    const val PERCENT_MIN_BASELINE_MS = 10L * 60_000L

    private const val MINUTE_MS = 60_000L

    sealed interface Change {
        data object Same : Change
        // Signed: negative means less screen time than the previous period.
        data class Percent(val value: Int) : Change
        data class Minutes(val value: Int) : Change
    }

    /**
     * Compares the total for the current period with the previous equal-length period.
     * Returns null when there is no previous data to compare against.
     */
    fun summaryChange(currentMs: Long, previousMs: Long): Change? {
        if (previousMs <= 0L) {
            return null
        }
        val current = currentMs.coerceAtLeast(0L)
        val diff = current - previousMs
        if (current >= PERCENT_MIN_BASELINE_MS && previousMs >= PERCENT_MIN_BASELINE_MS) {
            val percent = (diff.toDouble() * 100.0 / previousMs.toDouble()).roundToInt()
            return if (percent == 0) Change.Same else Change.Percent(percent)
        }
        val minutes = roundToMinutes(diff)
        return if (minutes == 0) Change.Same else Change.Minutes(minutes)
    }

    /**
     * Signed per-app change in whole minutes for the Week view.
     * Returns null when the app has no time in either period, so the row can omit the delta.
     */
    fun rowDeltaMinutes(currentMs: Long, previousMs: Long): Int? {
        val current = currentMs.coerceAtLeast(0L)
        val previous = previousMs.coerceAtLeast(0L)
        if (current == 0L && previous == 0L) {
            return null
        }
        return roundToMinutes(current - previous)
    }

    private fun roundToMinutes(diffMs: Long): Int {
        return (diffMs.toDouble() / MINUTE_MS.toDouble()).roundToInt()
    }
}
