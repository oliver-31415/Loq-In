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
 * What to do when an app with limits is unselected in the picker:
 * ask every time (until remembered), always remove the limits, or always keep them.
 */
object PickerLimitActionStore {

    enum class Action(val id: String) {
        ASK("ask"),
        REMOVE_LIMITS("remove_limits"),
        KEEP_LIMITS("keep_limits");

        companion object {
            fun fromId(id: String?): Action = entries.firstOrNull { it.id == id } ?: ASK
        }
    }

    private const val PREFS = "loqin_prefs"
    private const val KEY = "picker_unselect_with_limits_action"

    fun get(context: Context): Action =
        Action.fromId(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun set(context: Context, action: Action) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, action.id) }
    }
}
