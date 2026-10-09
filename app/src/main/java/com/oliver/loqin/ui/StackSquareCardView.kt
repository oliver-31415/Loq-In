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

package com.oliver.loqin.ui

import android.content.Context
import android.util.AttributeSet

/**
 * Square grid tile (website rules) for hosts that group more than one rule. The stacked
 * "paper edges" read as stray outlines on the tile and are not drawn; the page count and
 * layers badge on the tile content already communicate the grouping.
 */
class StackSquareCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SquareCardView(context, attrs, defStyleAttr) {

    var stackDepth: Int = 0
        set(value) {
            field = value.coerceIn(0, MAX_DEPTH)
            invalidate()
        }

    companion object {
        const val MAX_DEPTH = 3
    }
}
