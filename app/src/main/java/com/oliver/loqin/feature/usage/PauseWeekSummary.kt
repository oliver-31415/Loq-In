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

// Pure maths for the pause results card. Free of Android types so the rules can be unit tested.
object PauseWeekSummary {

    // Mirrors the threshold used for the weekly pause summary in PauseRuleStore.
    const val MIN_PAUSES_FOR_COMPARISON = 3

    enum class Trend { UP, DOWN }

    /**
     * [percent] is the share of this week's pauses where the user stepped away, or null when
     * there were no pauses. [previousPercent] is null unless the previous week had enough pauses
     * to compare against. [trend] is only set when both percentages exist and differ.
     */
    data class Result(
        val percent: Int?,
        val previousPercent: Int?,
        val trend: Trend?,
    )

    fun build(
        thisLeft: Int,
        thisContinued: Int,
        previousLeft: Int,
        previousContinued: Int,
    ): Result {
        val percent = percentLeft(thisLeft, thisContinued)
        val previousTotal = previousLeft.coerceAtLeast(0) + previousContinued.coerceAtLeast(0)
        val previousPercent = if (previousTotal >= MIN_PAUSES_FOR_COMPARISON) {
            percentLeft(previousLeft, previousContinued)
        } else {
            null
        }
        val trend = if (percent != null && previousPercent != null && percent != previousPercent) {
            if (percent > previousPercent) Trend.UP else Trend.DOWN
        } else {
            null
        }
        return Result(percent = percent, previousPercent = previousPercent, trend = trend)
    }

    private fun percentLeft(left: Int, continued: Int): Int? {
        val l = left.coerceAtLeast(0)
        val c = continued.coerceAtLeast(0)
        val total = l + c
        if (total <= 0) return null
        return (l.toDouble() * 100.0 / total.toDouble()).roundToInt()
    }
}
