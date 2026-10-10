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
import androidx.core.content.edit

/**
 * Optional daily screen-time goal shown on the Insights overview. Display only, never blocks.
 * Stored in "loqin_prefs" as an Int number of minutes; 0 means no goal is set.
 */
object InsightsGoalStore {

    private const val PREFS = "loqin_prefs"
    const val KEY_DAILY_GOAL_MIN = "insights_daily_goal_min"

    private const val MAX_MINUTES = 24 * 60

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Goal in minutes, or 0 when unset. */
    fun getDailyGoalMin(context: Context): Int {
        val sp = prefs(context)
        return try {
            sp.getInt(KEY_DAILY_GOAL_MIN, 0)
        } catch (_: ClassCastException) {
            // Restored backups may have written the value as a Long or String.
            val raw = runCatching { sp.getLong(KEY_DAILY_GOAL_MIN, 0L) }.getOrDefault(0L)
            raw.toInt().coerceIn(0, MAX_MINUTES)
        }.coerceIn(0, MAX_MINUTES)
    }

    /** Sets the goal; values <= 0 clear it. */
    fun setDailyGoalMin(context: Context, minutes: Int) {
        val clamped = minutes.coerceIn(0, MAX_MINUTES)
        prefs(context).edit {
            if (clamped <= 0) remove(KEY_DAILY_GOAL_MIN) else putInt(KEY_DAILY_GOAL_MIN, clamped)
        }
    }

    fun clearDailyGoal(context: Context) = setDailyGoalMin(context, 0)
}
