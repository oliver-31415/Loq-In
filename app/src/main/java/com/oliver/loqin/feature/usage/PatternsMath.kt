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

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Pure calculations behind the Patterns screen. Kept free of Android types so the
 * bucketing rules can be unit tested on the JVM.
 */
object PatternsMath {
    const val WEEKDAY_COUNT = 7
    const val HOUR_COUNT = 24
    const val MIN_DAYS_COLLECTED = 3
    const val PICKUP_CUTOFF_HOUR = 4
    const val PICKUP_AXIS_MINUTES = 20 * 60

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3_600_000L

    /** A span of time in epoch milliseconds, such as one app session or one protected interval. */
    data class Span(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    }

    enum class PeakGroup { WEEKDAYS, WEEKENDS, EVERY_DAY }

    /** A one or two hour block. [endHour] is exclusive, so 22 to 23 means 22:00 to 23:00. */
    data class PeakBlock(val group: PeakGroup, val startHour: Int, val endHour: Int)

    private data class HourPeak(val start: Int, val end: Int, val value: Double)

    /** Monday-first index: Monday is 0 and Sunday is 6. */
    fun weekdayIndex(calendar: Calendar): Int = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7

    fun startOfDay(timeMs: Long, zone: TimeZone): Long {
        val cal = calendar(timeMs, zone)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Local start-of-day values for the last [count] days, oldest first, ending with the day of [nowMs]. */
    fun lastDayStarts(nowMs: Long, count: Int, zone: TimeZone): List<Long> {
        val cal = calendar(startOfDay(nowMs, zone), zone)
        val days = ArrayList<Long>(count)
        repeat(count) {
            days.add(0, cal.timeInMillis)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return days
    }

    /** Number of distinct local days on which any span starts. */
    fun daysCollected(spans: List<Span>, zone: TimeZone): Int =
        spans.asSequence()
            .filter { it.durationMs > 0L }
            .map { startOfDay(it.startMs, zone) }
            .distinct()
            .count()

    /** Total overlap of [spans] with [startMs, endMs), in minutes. */
    fun overlapMinutes(spans: List<Span>, startMs: Long, endMs: Long): Double {
        if (endMs <= startMs) return 0.0
        var total = 0L
        for (span in spans) {
            val overlap = minOf(span.endMs, endMs) - maxOf(span.startMs, startMs)
            if (overlap > 0L) total += overlap
        }
        return total / MINUTE_MS.toDouble()
    }

    /**
     * Average minutes of use for each weekday-hour cell. Spans are clipped to the window and split
     * on every local hour boundary, so a session crossing 22:00 or midnight is counted in each cell
     * it touches. Each cell is divided by how many days of that weekday fall between the later of
     * [windowStartMs] and [coverageStartMs] and [windowEndMs], so days before data existed do not dilute it.
     * Result is indexed [weekday Monday-first][hour 0..23].
     */
    fun heatmapAverages(
        spans: List<Span>,
        windowStartMs: Long,
        windowEndMs: Long,
        coverageStartMs: Long,
        zone: TimeZone,
    ): Array<DoubleArray> {
        val grid = Array(WEEKDAY_COUNT) { DoubleArray(HOUR_COUNT) }
        if (windowEndMs <= windowStartMs) return grid

        val occurrences = IntArray(WEEKDAY_COUNT)
        val dayCursor = calendar(startOfDay(maxOf(windowStartMs, coverageStartMs), zone), zone)
        while (dayCursor.timeInMillis < windowEndMs) {
            occurrences[weekdayIndex(dayCursor)]++
            dayCursor.add(Calendar.DAY_OF_YEAR, 1)
        }

        for (span in spans) {
            val end = minOf(span.endMs, windowEndMs)
            var cursor = maxOf(span.startMs, windowStartMs)
            while (cursor < end) {
                val c = calendar(cursor, zone)
                val intoHourMs = c.get(Calendar.MINUTE) * MINUTE_MS +
                    c.get(Calendar.SECOND) * 1_000L + c.get(Calendar.MILLISECOND)
                val next = minOf(end, cursor + (HOUR_MS - intoHourMs))
                grid[weekdayIndex(c)][c.get(Calendar.HOUR_OF_DAY)] += (next - cursor) / MINUTE_MS.toDouble()
                cursor = next
            }
        }

        for (weekday in 0 until WEEKDAY_COUNT) {
            val days = occurrences[weekday]
            if (days > 0) {
                for (hour in 0 until HOUR_COUNT) grid[weekday][hour] /= days
            }
        }
        return grid
    }

    /**
     * Finds the busiest hour block. Weekdays and weekends are compared separately; when their blocks
     * overlap, or only one side has any use, the block is reported for every day instead.
     * Returns null when there is no use at all.
     */
    fun peakBlock(grid: Array<DoubleArray>): PeakBlock? {
        val all = peakHours(hourAverages(grid, 0..6)) ?: return null
        val weekdays = peakHours(hourAverages(grid, 0..4))
        val weekends = peakHours(hourAverages(grid, 5..6))
        if (weekdays != null && weekends == null) {
            return PeakBlock(PeakGroup.WEEKDAYS, weekdays.start, weekdays.end)
        }
        if (weekdays == null && weekends != null) {
            return PeakBlock(PeakGroup.WEEKENDS, weekends.start, weekends.end)
        }
        if (weekdays == null || weekends == null ||
            (weekdays.start < weekends.end && weekends.start < weekdays.end)
        ) {
            return PeakBlock(PeakGroup.EVERY_DAY, all.start, all.end)
        }
        return if (weekdays.value >= weekends.value) {
            PeakBlock(PeakGroup.WEEKDAYS, weekdays.start, weekdays.end)
        } else {
            PeakBlock(PeakGroup.WEEKENDS, weekends.start, weekends.end)
        }
    }

    /**
     * Earliest session of each local day, as minutes after that day's 04:00 cutoff. A session that
     * starts before 04:00 belongs to the previous day. Keyed by the attributed day's local start.
     */
    fun firstPickupMinutesByDay(spans: List<Span>, zone: TimeZone): Map<Long, Int> {
        val result = HashMap<Long, Int>()
        for (span in spans) {
            if (span.durationMs <= 0L) continue
            val shifted = span.startMs - PICKUP_CUTOFF_HOUR * HOUR_MS
            val dayStart = startOfDay(shifted, zone)
            val cutoff = calendar(dayStart, zone).apply {
                set(Calendar.HOUR_OF_DAY, PICKUP_CUTOFF_HOUR)
            }.timeInMillis
            val offset = ((span.startMs - cutoff) / MINUTE_MS).toInt().coerceAtLeast(0)
            val existing = result[dayStart]
            if (existing == null || offset < existing) result[dayStart] = offset
        }
        return result
    }

    /** Average of the pickups recorded for [dayStarts], or null when none of those days has one. */
    fun averagePickupMinutes(pickups: Map<Long, Int>, dayStarts: List<Long>): Double? {
        val values = dayStarts.mapNotNull { pickups[it] }
        return if (values.isEmpty()) null else values.average()
    }

    /** Converts minutes after the 04:00 cutoff into a 24 hour clock label such as 07:42. */
    fun clockLabel(minutesAfterCutoff: Double): String {
        val total = (PICKUP_CUTOFF_HOUR * 60 + minutesAfterCutoff.roundToInt()) % (24 * 60)
        return String.format(java.util.Locale.ROOT, "%02d:%02d", total / 60, total % 60)
    }

    fun hourLabel(hour: Int): String = String.format(java.util.Locale.ROOT, "%02d:00", hour)

    private fun calendar(timeMs: Long, zone: TimeZone): Calendar =
        Calendar.getInstance(zone).apply { timeInMillis = timeMs }

    private fun hourAverages(grid: Array<DoubleArray>, weekdays: IntRange): DoubleArray {
        val count = weekdays.count().toDouble()
        return DoubleArray(HOUR_COUNT) { hour -> weekdays.sumOf { grid[it][hour] } / count }
    }

    /**
     * Peak hour plus its busier neighbour when that neighbour is at least half as busy as the peak.
     * Blocks are capped at two hours.
     */
    private fun peakHours(values: DoubleArray): HourPeak? {
        var peak = 0
        for (hour in 1 until HOUR_COUNT) {
            if (values[hour] > values[peak]) peak = hour
        }
        val peakValue = values[peak]
        if (peakValue <= 0.0) return null

        val left = peak - 1
        val right = peak + 1
        val leftValue = if (left >= 0) values[left] else -1.0
        val rightValue = if (right < HOUR_COUNT) values[right] else -1.0
        val neighbour = if (leftValue >= rightValue) left else right
        val neighbourValue = maxOf(leftValue, rightValue)

        var start = peak
        var end = peak + 1
        if (neighbour in 0 until HOUR_COUNT && neighbourValue >= peakValue * 0.5) {
            if (neighbour < peak) start = neighbour else end = neighbour + 1
        }
        return HourPeak(start, end, peakValue)
    }
}
