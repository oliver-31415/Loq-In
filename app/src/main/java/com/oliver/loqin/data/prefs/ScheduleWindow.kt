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

package com.oliver.loqin.data.prefs

import java.util.Calendar

/**
 * When a schedule's time window covers a moment. Shared by the receiver, the planner and the UI so
 * they agree on two edge cases:
 * - The part of an overnight window after midnight (22:00–06:00 at 01:00) belongs to the day it
 *   started on: a Friday-only window still runs into Saturday morning, and Monday morning is not
 *   covered unless Sunday is selected.
 * - An all-day window (00:00–23:59) runs to midnight, so all-day, 24/7 and date-range schedules
 *   don't switch off for the last minute of each day.
 */
object ScheduleWindow {

    private const val LAST_MINUTE = 24 * 60 - 1

    fun isAllDay(s: ScheduleStore.Schedule): Boolean = s.startMinutes == 0 && s.endMinutes >= LAST_MINUTE

    fun isOvernight(s: ScheduleStore.Schedule): Boolean = !isAllDay(s) && s.endMinutes < s.startMinutes

    /** Whether the schedule's days or dates include the calendar day of [day]. */
    fun appliesOn(s: ScheduleStore.Schedule, day: Calendar): Boolean = when (s.type) {
        ScheduleStore.Type.WEEKLY ->
            s.daysMask and ScheduleStore.Days.fromCalendarDay(day.get(Calendar.DAY_OF_WEEK)) != 0

        ScheduleStore.Type.ONE_TIME ->
            s.startDate > 0 && s.endDate > 0 && ymd(day) in s.startDate..s.endDate
    }

    /** Whether the schedule's day rule and time window both cover [now]. */
    fun covers(s: ScheduleStore.Schedule, now: Calendar): Boolean {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return when {
            isAllDay(s) -> appliesOn(s, now)
            s.endMinutes == s.startMinutes -> false
            s.endMinutes > s.startMinutes -> minute in s.startMinutes until s.endMinutes && appliesOn(s, now)
            minute >= s.startMinutes -> appliesOn(s, now)
            minute < s.endMinutes -> appliesOn(s, dayBefore(now))
            else -> false
        }
    }

    /**
     * The window's end for a window starting on [day]: the next midnight for all-day windows and
     * the next morning for overnight ones.
     */
    fun endOfWindowStartingOn(s: ScheduleStore.Schedule, day: Calendar): Calendar {
        val end = (day.clone() as Calendar).apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (isAllDay(s)) {
            end.set(Calendar.HOUR_OF_DAY, 0)
            end.set(Calendar.MINUTE, 0)
            end.add(Calendar.DAY_OF_YEAR, 1)
            return end
        }
        end.set(Calendar.HOUR_OF_DAY, s.endMinutes / 60)
        end.set(Calendar.MINUTE, s.endMinutes % 60)
        if (isOvernight(s)) end.add(Calendar.DAY_OF_YEAR, 1)
        return end
    }

    fun ymd(day: Calendar): Int =
        day.get(Calendar.YEAR) * 10000 + (day.get(Calendar.MONTH) + 1) * 100 + day.get(Calendar.DAY_OF_MONTH)

    private fun dayBefore(day: Calendar): Calendar =
        (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
}
