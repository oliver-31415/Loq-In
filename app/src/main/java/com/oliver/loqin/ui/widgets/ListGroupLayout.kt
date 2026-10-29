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
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.oliver.loqin.R

/**
 * Material 3 Expressive grouped list, as in Android 16 Settings: each visible child row is its own
 * rounded segment, separated by a small gap instead of a divider. The first and last visible rows
 * get the large outer corners, rows in between get small inner corners. Hidden (GONE) rows are
 * skipped, so groups whose rows appear at runtime keep the right shape.
 *
 * Use it in place of a card that stacks rows with dividers: put the rows directly inside, drop the
 * dividers and any row background. Each row keeps its own padding (16dp horizontal). Clickable rows
 * get a ripple clipped to their segment.
 */
class ListGroupLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val outerCorner: Float
    private val innerCorner: Float
    private val gap: Int
    private val segmentColor: Int
    private var appliedSignature: String? = null

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        val a = context.obtainStyledAttributes(attrs, R.styleable.ListGroupLayout, defStyleAttr, 0)
        outerCorner = a.getDimension(R.styleable.ListGroupLayout_listOuterCornerSize, 24f * density)
        innerCorner = a.getDimension(R.styleable.ListGroupLayout_listInnerCornerSize, 6f * density)
        gap = a.getDimensionPixelSize(R.styleable.ListGroupLayout_listSegmentGap, (2f * density + 0.5f).toInt())
        segmentColor = a.getColor(
            R.styleable.ListGroupLayout_listSegmentColor,
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE),
        )
        a.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        applySegmentsIfNeeded()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** Re-applies segment shapes, e.g. after changing a row's clickable state from code. */
    fun refreshSegments() {
        appliedSignature = null
        requestLayout()
    }

    private fun applySegmentsIfNeeded() {
        val visible = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }
        val signature = visible.joinToString(",") { "${System.identityHashCode(it)}:${it.isClickable}" }
        if (signature == appliedSignature) return
        appliedSignature = signature
        visible.forEachIndexed { index, child ->
            val top = if (index == 0) outerCorner else innerCorner
            val bottom = if (index == visible.lastIndex) outerCorner else innerCorner
            val shape = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(top)
                .setTopRightCornerSize(top)
                .setBottomLeftCornerSize(bottom)
                .setBottomRightCornerSize(bottom)
                .build()
            val fill = MaterialShapeDrawable(shape).apply { fillColor = ColorStateList.valueOf(segmentColor) }
            child.background = if (child.isClickable) {
                val ripple = MaterialColors.getColor(child, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
                RippleDrawable(
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(ripple, 0x1F)),
                    fill,
                    MaterialShapeDrawable(shape),
                )
            } else {
                fill
            }
            (child.layoutParams as? MarginLayoutParams)?.let { lp ->
                val wantTop = if (index == 0) 0 else gap
                if (lp.topMargin != wantTop) {
                    lp.topMargin = wantTop
                    child.layoutParams = lp
                }
            }
        }
    }
}
