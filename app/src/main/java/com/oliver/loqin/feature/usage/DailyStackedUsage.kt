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
 * Pure shaping of per-day usage into stacked segments for [com.oliver.loqin.ui.widgets.StackedDailyBarChartView].
 *
 * The top [topN] packages (by total visible time across the whole window) keep their own segment,
 * everything else is folded into a single [OTHER_KEY] segment. Hidden packages are dropped entirely.
 */
object DailyStackedUsage {

    const val OTHER_KEY = "__other__"

    data class Segment(val key: String, val ms: Long)

    data class Day(val segments: List<Segment>, val totalMs: Long)

    /**
     * @param days one map of package to ms per day, oldest first
     * @param previous total ms per day for the comparison window, aligned by index (may be shorter)
     * @param legendKeys package names in stack order, followed by [OTHER_KEY] when any time was folded
     */
    data class Result(
        val days: List<Day>,
        val previousMs: List<Long>,
        val legendKeys: List<String>,
    )

    fun build(
        days: List<Map<String, Long>>,
        previous: List<Long>,
        topN: Int = 4,
        hide: (String) -> Boolean,
    ): Result {
        val visible: List<Map<String, Long>> = days.map { day ->
            day.filter { (pkg, ms) -> ms > 0L && !hide(pkg) }
        }

        val totalsByPkg = HashMap<String, Long>()
        for (day in visible) {
            for ((pkg, ms) in day) {
                totalsByPkg[pkg] = (totalsByPkg[pkg] ?: 0L) + ms
            }
        }

        val topKeys = totalsByPkg.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(topN.coerceAtLeast(0))
            .map { it.key }

        val hasOther = visible.any { day -> day.keys.any { it !in topKeys } }

        val outDays = visible.map { day ->
            val segments = ArrayList<Segment>(topKeys.size + 1)
            for (key in topKeys) {
                segments.add(Segment(key, day[key] ?: 0L))
            }
            if (hasOther) {
                val other = day.entries.filter { it.key !in topKeys }.sumOf { it.value }
                segments.add(Segment(OTHER_KEY, other))
            }
            Day(segments = segments, totalMs = day.values.sum())
        }

        return Result(
            days = outDays,
            previousMs = previous.map { it.coerceAtLeast(0L) },
            legendKeys = if (hasOther) topKeys + OTHER_KEY else topKeys,
        )
    }
}
