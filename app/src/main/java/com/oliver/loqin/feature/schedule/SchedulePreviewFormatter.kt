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

import android.content.Context
import androidx.annotation.StringRes
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.ScheduleStore
import java.util.Locale

/**
 * Confirmation text for a schedule that is about to be saved.
 *
 * The structure is built without a Context so every action/trigger combination can be unit tested;
 * [describe] resolves the resource labels for display.
 */
object SchedulePreviewFormatter {

    enum class ValueKind {
        TEXT,
        ACTION,
        WIFI,
        BLUETOOTH,
        LOCATION,
        ONE_TIME,
        WEEKLY,
    }

    data class Line(
        @param:StringRes val labelRes: Int,
        val kind: ValueKind,
        val text: String = "",
        @param:StringRes val detailRes: Int? = null,
    )

    fun lines(schedule: ScheduleStore.Schedule): List<Line> {
        val lines = mutableListOf(
            Line(
                labelRes = R.string.schedules_preview_profile,
                kind = ValueKind.TEXT,
                text = schedule.profile.ifBlank { "-" },
            ),
            Line(
                labelRes = R.string.schedules_preview_action,
                kind = ValueKind.ACTION,
                detailRes = actionLabelRes(schedule.action),
            ),
            triggerLine(schedule),
            Line(
                labelRes = R.string.schedules_preview_time,
                kind = ValueKind.TEXT,
                text = timeLabel(schedule),
            ),
        )
        if (schedule.note.isNotBlank()) {
            lines += Line(
                labelRes = R.string.schedules_preview_note,
                kind = ValueKind.TEXT,
                text = schedule.note,
            )
        }
        return lines
    }

    /** Label/value pairs for the app's row-style info dialog. */
    fun infoRows(context: Context, schedule: ScheduleStore.Schedule): List<Pair<String, String>> =
        lines(schedule).map { context.getString(it.labelRes) to value(context, it) }

    fun describe(context: Context, schedule: ScheduleStore.Schedule): String =
        lines(schedule).joinToString("\n") { line ->
            "${context.getString(line.labelRes)}: ${value(context, line)}"
        }

    private fun triggerLine(schedule: ScheduleStore.Schedule): Line = when {
        schedule.wifiSsid != null -> Line(
            labelRes = R.string.schedules_preview_when,
            kind = ValueKind.WIFI,
            text = schedule.wifiSsid.orEmpty(),
        )

        schedule.btDeviceName != null -> Line(
            labelRes = R.string.schedules_preview_when,
            kind = ValueKind.BLUETOOTH,
            text = schedule.btDeviceName.orEmpty(),
        )

        schedule.isLocationSchedule() -> Line(
            labelRes = R.string.schedules_preview_when,
            kind = ValueKind.LOCATION,
            text = schedule.locationLabel?.takeIf { it.isNotBlank() }
                ?: schedule.locationLat?.let { lat ->
                    String.format(Locale.US, "%.4f, %.4f", lat, schedule.locationLng ?: 0.0)
                }.orEmpty(),
            detailRes = locationTriggerRes(schedule.locationTrigger),
        )

        schedule.type == ScheduleStore.Type.ONE_TIME -> Line(
            labelRes = R.string.schedules_preview_when,
            kind = ValueKind.ONE_TIME,
        )

        else -> Line(
            labelRes = R.string.schedules_preview_when,
            kind = ValueKind.WEEKLY,
        )
    }

    @StringRes
    fun actionLabelRes(action: ScheduleStore.Action): Int = when (action) {
        ScheduleStore.Action.ENABLE -> R.string.schedules_action_enable
        ScheduleStore.Action.DISABLE -> R.string.schedules_action_disable
        ScheduleStore.Action.TOGGLE -> R.string.schedules_action_toggle
        ScheduleStore.Action.ENABLE_AND_DISABLE -> R.string.schedules_action_enable_disable
        ScheduleStore.Action.DISABLE_AND_ENABLE -> R.string.schedules_action_disable_enable
        ScheduleStore.Action.DISCONNECT_ENABLE -> R.string.schedules_action_disconnect_enable
        ScheduleStore.Action.DISCONNECT_DISABLE -> R.string.schedules_action_disconnect_disable
    }

    @StringRes
    private fun locationTriggerRes(trigger: ScheduleStore.LocationTrigger?): Int? = when (trigger) {
        ScheduleStore.LocationTrigger.ENTER -> R.string.schedules_preview_location_enter
        ScheduleStore.LocationTrigger.EXIT -> R.string.schedules_preview_location_exit
        ScheduleStore.LocationTrigger.ENTER_EXIT -> R.string.schedules_preview_location_enter_exit
        null -> null
    }

    private fun timeLabel(schedule: ScheduleStore.Schedule): String {
        fun format(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
        return if (schedule.endMinutes != schedule.startMinutes) {
            "${format(schedule.startMinutes)}-${format(schedule.endMinutes)}"
        } else {
            format(schedule.startMinutes)
        }
    }

    private fun value(context: Context, line: Line): String = when (line.kind) {
        ValueKind.ACTION -> context.getString(line.detailRes ?: R.string.schedules_preview_weekly)
        ValueKind.WIFI -> context.getString(R.string.schedules_preview_wifi_fmt, line.text)
        ValueKind.BLUETOOTH -> context.getString(R.string.schedules_preview_bt_fmt, line.text)
        ValueKind.LOCATION -> buildString {
            append(context.getString(R.string.schedules_preview_location_fmt, line.text))
            line.detailRes?.let { append(" · ").append(context.getString(it)) }
        }

        ValueKind.ONE_TIME -> context.getString(R.string.schedules_preview_once)
        ValueKind.WEEKLY -> context.getString(R.string.schedules_preview_weekly)
        ValueKind.TEXT -> line.text
    }
}
