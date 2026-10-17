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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWindowTest {

    private fun schedule(
        start: Int,
        end: Int,
        days: Int = ScheduleStore.Days.FRI,
        type: ScheduleStore.Type = ScheduleStore.Type.WEEKLY,
        startDate: Int = 0,
        endDate: Int = 0,
    ) = ScheduleStore.Schedule(
        id = 1,
        enabled = true,
        profile = "Default",
        title = "",
        note = "",
        type = type,
        daysMask = days,
        startMinutes = start,
        endMinutes = end,
        startDate = startDate,
        endDate = endDate,
        action = ScheduleStore.Action.ENABLE_AND_DISABLE,
    )

    // 2026-10-16 is a Friday.
    private fun at(day: Int, hour: Int, minute: Int = 0): Calendar = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
    }

    @Test
    fun `overnight window runs into the next morning`() {
        val friNight = schedule(22 * 60, 6 * 60)
        assertTrue(ScheduleWindow.covers(friNight, at(16, 23)))
        assertTrue(ScheduleWindow.covers(friNight, at(17, 1))) // Saturday 01:00
        assertFalse(ScheduleWindow.covers(friNight, at(17, 6)))
        assertFalse(ScheduleWindow.covers(friNight, at(16, 1))) // Friday 01:00 belongs to Thursday
    }

    @Test
    fun `day window keeps to its own day`() {
        val friDay = schedule(9 * 60, 17 * 60)
        assertTrue(ScheduleWindow.covers(friDay, at(16, 9)))
        assertFalse(ScheduleWindow.covers(friDay, at(16, 17)))
        assertFalse(ScheduleWindow.covers(friDay, at(17, 10)))
    }

    @Test
    fun `all day window includes the last minute`() {
        val allDay = schedule(0, 24 * 60 - 1)
        assertTrue(ScheduleWindow.covers(allDay, at(16, 23, 59)))
        assertTrue(ScheduleWindow.covers(allDay, at(16, 0, 0)))
        assertFalse(ScheduleWindow.covers(allDay, at(17, 0, 0)))
    }

    @Test
    fun `date range covers whole days`() {
        val range = schedule(0, 24 * 60 - 1, days = 0, type = ScheduleStore.Type.ONE_TIME, startDate = 20261016, endDate = 20261017)
        assertTrue(ScheduleWindow.covers(range, at(16, 23, 59)))
        assertTrue(ScheduleWindow.covers(range, at(17, 12)))
        assertFalse(ScheduleWindow.covers(range, at(18, 0)))
    }

    @Test
    fun `empty window never covers`() {
        assertFalse(ScheduleWindow.covers(schedule(600, 600), at(16, 10)))
    }

    @Test
    fun `window ends on the right day`() {
        val friday = at(16, 0)
        val overnight = ScheduleWindow.endOfWindowStartingOn(schedule(22 * 60, 6 * 60), friday)
        assertEquals(17, overnight.get(Calendar.DAY_OF_MONTH))
        assertEquals(6, overnight.get(Calendar.HOUR_OF_DAY))

        val allDay = ScheduleWindow.endOfWindowStartingOn(schedule(0, 24 * 60 - 1), friday)
        assertEquals(17, allDay.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, allDay.get(Calendar.HOUR_OF_DAY))

        val day = ScheduleWindow.endOfWindowStartingOn(schedule(9 * 60, 17 * 60), friday)
        assertEquals(16, day.get(Calendar.DAY_OF_MONTH))
        assertEquals(17, day.get(Calendar.HOUR_OF_DAY))
    }
}
