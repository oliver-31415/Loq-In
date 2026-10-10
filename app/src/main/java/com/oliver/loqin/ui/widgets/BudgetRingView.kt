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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor

/**
 * Ring showing today's screen time against a daily goal.
 *
 * The arc fills clockwise from the top in the live accent colour. Once usage passes the goal the
 * ring is simply full; it never changes colour, and the caller states the overage in text.
 * With no goal (0) only the faint track is drawn.
 */
class BudgetRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var usedMs: Long = 0L
    private var goalMs: Long = 0L

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_DP * density
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()

    /** Sets today's usage and the goal in ms. A goal of 0 means no goal. */
    fun setProgress(usedMs: Long, goalMs: Long) {
        this.usedMs = usedMs.coerceAtLeast(0L)
        this.goalMs = goalMs.coerceAtLeast(0L)
        invalidate()
    }

    /** Fraction of the goal used, clamped to 0..1 for drawing. */
    fun fraction(): Float {
        if (goalMs <= 0L) return 0f
        return (usedMs.toFloat() / goalMs.toFloat()).coerceIn(0f, 1f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (SIZE_DP * density).toInt()
        setMeasuredDimension(
            resolveSize(size, widthMeasureSpec),
            resolveSize(size, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = STROKE_DP * density / 2f
        oval.set(inset, inset, width - inset, height - inset)
        if (oval.width() <= 0f || oval.height() <= 0f) return

        val onSurface = resolveOnSurface()
        trackPaint.color = Color.argb(
            TRACK_ALPHA,
            Color.red(onSurface),
            Color.green(onSurface),
            Color.blue(onSurface),
        )
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)

        val fraction = fraction()
        if (fraction <= 0f) return
        arcPaint.color = AccentColor.getAccentColorInt(context)
        val sweep = if (fraction >= 1f) 360f else fraction * 360f
        // Start at 12 o'clock; the round cap is only useful for partial arcs.
        canvas.drawArc(oval, -90f, sweep, false, arcPaint)
    }

    private fun resolveOnSurface(): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true)) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) tv.data
            else ContextCompat.getColor(context, R.color.foqos_on_surface)
        } else {
            ContextCompat.getColor(context, R.color.foqos_on_surface)
        }
    }

    private companion object {
        const val SIZE_DP = 64f
        const val STROKE_DP = 6f
        const val TRACK_ALPHA = 38
    }
}
