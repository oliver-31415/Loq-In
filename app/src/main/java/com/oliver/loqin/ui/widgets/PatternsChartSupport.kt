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

package com.oliver.loqin.ui.widgets

import android.content.Context
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.oliver.loqin.R

/** Theme on-surface colour used for chart text and tracks, falling back to the brand colour. */
internal fun patternsOnSurfaceColor(context: Context): Int {
    val tv = TypedValue()
    val resolved = context.theme.resolveAttribute(
        com.google.android.material.R.attr.colorOnSurface, tv, true
    )
    return if (resolved && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
        tv.data
    } else {
        ContextCompat.getColor(context, R.color.foqos_on_surface)
    }
}
