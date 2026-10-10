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
 * A row of vertical split bars, one per day.
 *
 * Each day's bar is divided into a "left" part (the user stepped away, drawn in the accent colour)
 * and a "continued" part (grey). The split is proportional to the day's pause count. Days with
 * no pauses are drawn as an empty track so the row still shows all seven days.
 */
class SplitBarRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Day(val left: Int, val continued: Int) {
        val total: Int get() = left + continued
    }

    private var days: List<Day> = emptyList()
    private var labels: List<String> = emptyList()

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val continuedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    private val rect = RectF()

    /** One entry per day, oldest first; [labels] is aligned by index. */
    fun setData(days: List<Day>, labels: List<String>) {
        this.days = days
        this.labels = labels
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || days.isEmpty()) return

        val accent = AccentColor.getAccentColorInt(context)
        val onSurface = resolveOnSurface()
        trackPaint.color = withAlpha(onSurface, TRACK_ALPHA)
        leftPaint.color = accent
        continuedPaint.color = withAlpha(onSurface, CONTINUED_ALPHA)
        labelPaint.color = withAlpha(onSurface, LABEL_ALPHA)

        val labelBand = LABEL_BAND_DP * density
        val barTop = 2f * density
        val barBottom = h - labelBand
        val barHeight = barBottom - barTop
        if (barHeight <= 0f) return

        val slot = w / days.size
        val barWidth = minOf(slot * 0.5f, MAX_BAR_DP * density)
        val radius = barWidth / 2f

        days.forEachIndexed { i, day ->
            val cx = slot * (i + 0.5f)
            val left = cx - barWidth / 2f
            val right = cx + barWidth / 2f
            rect.set(left, barTop, right, barBottom)
            if (day.total <= 0) {
                canvas.drawRoundRect(rect, radius, radius, trackPaint)
            } else {
                val leftFraction = day.left.toFloat() / day.total
                val splitY = barBottom - barHeight * (1f - leftFraction)
                // Continued sits on top, stepped-away at the bottom.
                if (day.continued > 0) {
                    rect.set(left, barTop, right, barBottom)
                    canvas.drawRoundRect(rect, radius, radius, continuedPaint)
                }
                if (day.left > 0) {
                    rect.set(left, splitY, right, barBottom)
                    canvas.drawRoundRect(rect, radius, radius, leftPaint)
                }
            }
            labels.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { label ->
                canvas.drawText(label, cx, h - 4f * density, labelPaint)
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

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
        const val TRACK_ALPHA = 38
        const val CONTINUED_ALPHA = 110
        const val LABEL_ALPHA = 160
        const val LABEL_BAND_DP = 20f
        const val MAX_BAR_DP = 22f
    }
}
