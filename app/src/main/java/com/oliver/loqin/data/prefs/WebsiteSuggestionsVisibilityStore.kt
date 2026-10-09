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
import androidx.preference.PreferenceManager

/** Visibility of the website suggestion section on the website rules screen. */
object WebsiteSuggestionsVisibilityStore {
    const val KEY_SHOW_WEBSITE_SUGGESTIONS = "pref_show_website_suggestions"

    fun isVisible(ctx: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(KEY_SHOW_WEBSITE_SUGGESTIONS, true)
    }

    fun setVisible(ctx: Context, visible: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit(commit = true) {
            putBoolean(KEY_SHOW_WEBSITE_SUGGESTIONS, visible)
        }
    }
}
