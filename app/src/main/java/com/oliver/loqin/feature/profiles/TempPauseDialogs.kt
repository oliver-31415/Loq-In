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

package com.oliver.loqin.feature.profiles

import androidx.appcompat.app.AppCompatActivity
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.TempPauseStore

/** Shared per-profile temporary-pause caps editor. */
object TempPauseDialogs {

    fun summaryText(activity: AppCompatActivity, profile: String): String {
        val used = activity.getString(
            R.string.temp_pause_used_fmt,
            TempPauseStore.usedCountToday(activity, profile),
            TempPauseStore.usedMinutesToday(activity, profile)
        )
        return capsSummaryText(activity, profile) + "\n" + used
    }

    fun capsSummaryText(activity: AppCompatActivity, profile: String): String {
        val caps = TempPauseStore.getCaps(activity, profile)
        if (caps.maxCountPerDay <= 0 && caps.maxMinutesPerPause <= 0 && caps.maxMinutesPerDay <= 0) {
            return activity.getString(R.string.temp_pause_summary_unlimited)
        }
        val parts = mutableListOf<String>()
        if (caps.maxCountPerDay > 0) {
            parts += activity.getString(R.string.temp_pause_part_count_fmt, caps.maxCountPerDay)
        }
        if (caps.maxMinutesPerPause > 0) {
            parts += activity.getString(R.string.temp_pause_part_max_fmt, caps.maxMinutesPerPause)
        }
        if (caps.maxMinutesPerDay > 0) {
            parts += activity.getString(R.string.temp_pause_part_total_fmt, caps.maxMinutesPerDay)
        }
        return parts.joinToString(" · ")
    }
}
