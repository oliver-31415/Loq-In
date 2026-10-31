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
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * Lays its children out in a fixed number of equal columns, row by row, for tile grids built in
 * code (in-app rules, breaks) that sit inside a scrolling page instead of a RecyclerView. Child
 * margins are respected, so grid_rule_tile's 4dp margin gives the same gaps as the app grid.
 */
class TileGridLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    var columns: Int = 3
        set(value) {
            field = value.coerceAtLeast(1)
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val cell = (width - paddingLeft - paddingRight) / columns
        var height = paddingTop + paddingBottom
        var rowHeight = 0
        var col = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as MarginLayoutParams
            val childWidth = (cell - lp.leftMargin - lp.rightMargin).coerceAtLeast(0)
            child.measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            rowHeight = maxOf(rowHeight, child.measuredHeight + lp.topMargin + lp.bottomMargin)
            col++
            if (col == columns) {
                height += rowHeight
                rowHeight = 0
                col = 0
            }
        }
        height += rowHeight
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cell = (width - paddingLeft - paddingRight) / columns
        var top = paddingTop
        var rowHeight = 0
        var col = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as MarginLayoutParams
            val left = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                width - paddingRight - (col + 1) * cell + lp.leftMargin
            } else {
                paddingLeft + col * cell + lp.leftMargin
            }
            child.layout(left, top + lp.topMargin, left + child.measuredWidth, top + lp.topMargin + child.measuredHeight)
            rowHeight = maxOf(rowHeight, child.measuredHeight + lp.topMargin + lp.bottomMargin)
            col++
            if (col == columns) {
                top += rowHeight
                rowHeight = 0
                col = 0
            }
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)
    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)
    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams
}
