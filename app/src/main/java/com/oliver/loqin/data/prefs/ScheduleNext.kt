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

import android.content.Context
import android.text.format.DateUtils
import com.oliver.loqin.R
import com.oliver.loqin.util.TimeFormatPrefs
import java.util.Calendar

/**
 * The next moment a schedule starts or ends, looked up from the saved schedules. Shared by the
 * Next schedule widget and the schedules page so both name the same time.
 */
object ScheduleNext {

    data class Boundary(
        val timeMillis: Long,
        /** True when the schedule's window ends at [timeMillis] rather than starts. */
        val isEnd: Boolean,
        val schedule: ScheduleStore.Schedule,
    )

    /** Next start or end among enabled schedules that pass [include], or null if none is coming. */
    fun find(context: Context, include: (ScheduleStore.Schedule) -> Boolean = { true }): Boundary? {
        val schedules = ScheduleStore.getAll(context)
            .filter { it.enabled && include(it) }
            .filterNot { schedule ->
                // Wi-Fi / Bluetooth schedules that run all day have no clock boundary to show.
                val isConnectionOnly = !schedule.wifiSsid.isNullOrBlank() ||
                    !schedule.btDeviceName.isNullOrBlank() || !schedule.btDeviceAddress.isNullOrBlank()
                isConnectionOnly && schedule.startMinutes == 0 && schedule.endMinutes >= 1439
            }
        if (schedules.isEmpty()) return null

        val nowMs = System.currentTimeMillis()
        var best: Boundary? = null
        fun consider(candidate: Boundary?) {
            if (candidate == null || candidate.timeMillis <= nowMs) return
            if (best == null || candidate.timeMillis < best!!.timeMillis) best = candidate
        }

        schedules.forEach { schedule ->
            val (range, dayMatches) = when (schedule.type) {
                ScheduleStore.Type.WEEKLY -> (-1 until 14) to { day: Calendar ->
                    val bit = ScheduleStore.Days.fromCalendarDay(day.get(Calendar.DAY_OF_WEEK))
                    schedule.daysMask and bit != 0
                }
                ScheduleStore.Type.ONE_TIME -> {
                    if (schedule.startDate <= 0 || schedule.endDate <= 0) return@forEach
                    (-1 until 30) to { day: Calendar ->
                        val ymd = day.get(Calendar.YEAR) * 10000 + (day.get(Calendar.MONTH) + 1) * 100 +
                            day.get(Calendar.DAY_OF_MONTH)
                        ymd in schedule.startDate..schedule.endDate
                    }
                }
            }
            for (offset in range) {
                val day = Calendar.getInstance().apply {
                    timeInMillis = nowMs
                    add(Calendar.DAY_OF_YEAR, offset)
                }
                if (!dayMatches(day)) continue
                consider(boundary(schedule, day, isEnd = false))
                consider(boundary(schedule, day, isEnd = true))
            }
        }
        return best
    }

    /** "Starts today at 9:31 PM", "Ends tomorrow at 7:00 AM", "Starts Mon at 9:00 AM". */
    fun describe(context: Context, boundary: Boundary): String {
        val cal = Calendar.getInstance().apply { timeInMillis = boundary.timeMillis }
        val time = TimeFormatPrefs.formatMinutesOfDay(
            context,
            cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE),
        )
        val today = Calendar.getInstance()
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val whenText = when {
            sameDay(cal, today) -> context.getString(R.string.schedule_next_today_fmt, time)
            sameDay(cal, tomorrow) -> context.getString(R.string.schedule_next_tomorrow_fmt, time)
            else -> context.getString(
                R.string.schedule_next_day_fmt,
                DateUtils.formatDateTime(context, boundary.timeMillis, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY),
                time,
            )
        }
        return context.getString(
            if (boundary.isEnd) R.string.schedule_next_ends_fmt else R.string.schedule_next_starts_fmt,
            whenText,
        )
    }

    private fun boundary(schedule: ScheduleStore.Schedule, day: Calendar, isEnd: Boolean): Boundary? {
        if (isEnd && !isRangeAction(schedule.action)) return null
        val timeMillis = if (isEnd) {
            // Next morning for overnight windows, next midnight for all-day ones.
            ScheduleWindow.endOfWindowStartingOn(schedule, day).timeInMillis
        } else {
            (day.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, schedule.startMinutes / 60)
                set(Calendar.MINUTE, schedule.startMinutes % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        return Boundary(timeMillis, isEnd, schedule)
    }

    private fun isRangeAction(action: ScheduleStore.Action): Boolean = when (action) {
        ScheduleStore.Action.ENABLE_AND_DISABLE,
        ScheduleStore.Action.DISABLE_AND_ENABLE -> true
        else -> false
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
