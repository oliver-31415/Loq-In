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

package com.oliver.loqin.feature.schedule

import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.ScheduleStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePreviewFormatterTest {

    private fun schedule(
        action: ScheduleStore.Action = ScheduleStore.Action.ENABLE,
        type: ScheduleStore.Type = ScheduleStore.Type.WEEKLY,
        wifiSsid: String? = null,
        btDeviceName: String? = null,
        locationLabel: String? = null,
        locationTrigger: ScheduleStore.LocationTrigger? = null,
        locationLat: Double? = if (locationTrigger != null) 48.2082 else null,
        locationLng: Double? = if (locationTrigger != null) 16.3738 else null,
        startMinutes: Int = 9 * 60,
        endMinutes: Int = 17 * 60,
        note: String = "",
        profile: String = "Default",
    ) = ScheduleStore.Schedule(
        id = 1,
        enabled = true,
        profile = profile,
        title = "Test",
        note = note,
        type = type,
        daysMask = 0,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        startDate = 0,
        endDate = 0,
        wifiSsid = wifiSsid,
        btDeviceName = btDeviceName,
        locationLabel = locationLabel,
        locationLat = locationLat,
        locationLng = locationLng,
        locationTrigger = locationTrigger,
        action = action,
    )

    @Test
    fun `every action maps to a label`() {
        val labels = ScheduleStore.Action.entries.map(SchedulePreviewFormatter::actionLabelRes)
        assertTrue(labels.none { it == R.string.schedules_preview_weekly })
        assertEquals(ScheduleStore.Action.entries.size, labels.toSet().size)
    }

    @Test
    fun `disconnect actions are covered`() {
        assertEquals(
            R.string.schedules_action_disconnect_enable,
            SchedulePreviewFormatter.actionLabelRes(ScheduleStore.Action.DISCONNECT_ENABLE),
        )
        assertEquals(
            R.string.schedules_action_disconnect_disable,
            SchedulePreviewFormatter.actionLabelRes(ScheduleStore.Action.DISCONNECT_DISABLE),
        )
    }

    @Test
    fun `weekly schedule shows profile action when time and note`() {
        val lines = SchedulePreviewFormatter.lines(schedule(note = "bedtime"))
        assertEquals(5, lines.size)
        assertEquals(SchedulePreviewFormatter.ValueKind.TEXT, lines[0].kind)
        assertEquals("Default", lines[0].text)
        assertEquals(SchedulePreviewFormatter.ValueKind.ACTION, lines[1].kind)
        assertEquals(R.string.schedules_action_enable, lines[1].detailRes)
        assertEquals(SchedulePreviewFormatter.ValueKind.WEEKLY, lines[2].kind)
        assertEquals(SchedulePreviewFormatter.ValueKind.TEXT, lines[3].kind)
        assertEquals("09:00-17:00", lines[3].text)
        assertEquals("bedtime", lines[4].text)
    }

    @Test
    fun `single-point schedule shows one time`() {
        val lines = SchedulePreviewFormatter.lines(schedule(startMinutes = 8 * 60 + 5, endMinutes = 8 * 60 + 5))
        assertEquals("08:05", lines[3].text)
    }

    @Test
    fun `wifi bluetooth and location triggers are distinguished`() {
        assertEquals(
            SchedulePreviewFormatter.ValueKind.WIFI,
            SchedulePreviewFormatter.lines(schedule(wifiSsid = "HomeNet"))[2].kind,
        )
        assertEquals(
            "HomeNet",
            SchedulePreviewFormatter.lines(schedule(wifiSsid = "HomeNet"))[2].text,
        )
        assertEquals(
            SchedulePreviewFormatter.ValueKind.BLUETOOTH,
            SchedulePreviewFormatter.lines(schedule(btDeviceName = "Car"))[2].kind,
        )
        val location = SchedulePreviewFormatter.lines(
            schedule(locationLabel = "Office", locationTrigger = ScheduleStore.LocationTrigger.ENTER_EXIT),
        )[2]
        assertEquals(SchedulePreviewFormatter.ValueKind.LOCATION, location.kind)
        assertEquals("Office", location.text)
        assertEquals(R.string.schedules_preview_location_enter_exit, location.detailRes)
    }

    @Test
    fun `location trigger variants map to labels`() {
        assertEquals(
            R.string.schedules_preview_location_enter,
            SchedulePreviewFormatter.lines(
                schedule(locationLabel = "Office", locationTrigger = ScheduleStore.LocationTrigger.ENTER),
            )[2].detailRes,
        )
        assertEquals(
            R.string.schedules_preview_location_exit,
            SchedulePreviewFormatter.lines(
                schedule(locationLabel = "Office", locationTrigger = ScheduleStore.LocationTrigger.EXIT),
            )[2].detailRes,
        )
    }

    @Test
    fun `one-time schedule is labelled once`() {
        val lines = SchedulePreviewFormatter.lines(schedule(type = ScheduleStore.Type.ONE_TIME))
        assertEquals(SchedulePreviewFormatter.ValueKind.ONE_TIME, lines[2].kind)
    }

    @Test
    fun `blank note is omitted and blank profile falls back to a dash`() {
        val lines = SchedulePreviewFormatter.lines(schedule(profile = "", note = ""))
        assertEquals(4, lines.size)
        assertEquals("-", lines[0].text)
    }
}
