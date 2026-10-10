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
import kotlin.math.max
import kotlin.math.min

/**
 * Per-day outcome of the pause screen for one app. Each day is one stacked bar: "stepped away"
 * in accent at the base, "opened" in neutral grey on top. Bar height is the number of pause
 * outcomes that day, so both the volume and the split are visible.
 *
 * Accent and neutral greys only. A small legend sits above the bars.
 */
class PauseSplitBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var leftCounts: List<Int> = emptyList()
    private var continuedCounts: List<Int> = emptyList()
    private var labels: List<String> = emptyList()

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val greyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }

    init {
        contentDescription = context.getString(R.string.charts_pause_a11y)
    }

    /**
     * [leftCounts] is "stepped away" per day, [continuedCounts] is "opened" per day. Both must be
     * the same length as [labels]. Negatives count as zero.
     */
    fun setData(leftCounts: List<Int>, continuedCounts: List<Int>, labels: List<String>) {
        this.leftCounts = leftCounts.map { it.coerceAtLeast(0) }
        this.continuedCounts = continuedCounts.map { it.coerceAtLeast(0) }
        this.labels = labels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = leftCounts.size
        val w = width.toFloat()
        val h = height.toFloat()
        if (n == 0 || w <= 0f || h <= 0f) return
        val totals = List(n) { leftCounts[it] + continuedCounts.getOrElse(it) { 0 } }
        if (totals.all { it == 0 }) return

        val density = resources.displayMetrics.density
        val accent = AccentColor.getAccentColorInt(context)
        val onSurface = resolveOnSurface()
        val neutral = withAlpha(onSurface, 150)
        val faint = withAlpha(onSurface, 38)
        accentPaint.color = accent
        greyPaint.color = neutral
        baselinePaint.color = faint
        labelPaint.color = withAlpha(onSurface, 160)
        legendPaint.color = withAlpha(onSurface, 200)

        val legendHeight = 22f * density
        val labelArea = 22f * density
        val chartTop = legendHeight + 6f * density
        val chartBottom = h - labelArea
        if (chartBottom <= chartTop + 8f) return

        drawLegend(canvas, density, legendHeight)
        canvas.drawLine(0f, chartBottom, w, chartBottom, baselinePaint)

        val slot = w / n
        val barW = (slot * 0.5f).coerceIn(4f * density, 28f * density)
        val radius = min(6f * density, barW / 2f)
        val maxTotal = max(1, totals.maxOrNull() ?: 1).toFloat()
        val rect = RectF()

        for (i in 0 until n) {
            val total = totals[i]
            if (total <= 0) continue
            val cx = slot * (i + 0.5f)
            val chartHeight = chartBottom - chartTop
            val totalHeight = chartHeight * (total / maxTotal)
            val leftHeight = chartHeight * (leftCounts[i] / maxTotal)
            val left = cx - barW / 2f
            val right = cx + barW / 2f
            // Whole bar in grey first, then the accent base on top of it, so the rounded top
            // corners follow the total height.
            rect.set(left, chartBottom - totalHeight, right, chartBottom)
            canvas.drawRoundRect(rect, radius, radius, greyPaint)
            if (leftCounts[i] > 0) {
                rect.set(left, chartBottom - leftHeight, right, chartBottom)
                canvas.drawRoundRect(rect, radius, radius, accentPaint)
            }
        }

        // Thin out day labels so a 30-day range stays readable.
        val step = ((n + 7) / 8).coerceAtLeast(1)
        var i = 0
        while (i < labels.size && i < n) {
            canvas.drawText(labels[i], slot * (i + 0.5f), h - 6f * density, labelPaint)
            i += step
        }
    }

    private fun drawLegend(canvas: Canvas, density: Float, height: Float) {
        val swatch = 9f * density
        val y = height / 2f
        var x = 0f
        canvas.drawRoundRect(RectF(x, y - swatch / 2f, x + swatch, y + swatch / 2f), 2f * density, 2f * density, accentPaint)
        x += swatch + 6f * density
        val leftText = context.getString(R.string.charts_pause_legend_left)
        canvas.drawText(leftText, x, y + legendPaint.textSize / 3f, legendPaint)
        x += legendPaint.measureText(leftText) + 16f * density
        canvas.drawRoundRect(RectF(x, y - swatch / 2f, x + swatch, y + swatch / 2f), 2f * density, 2f * density, greyPaint)
        x += swatch + 6f * density
        canvas.drawText(context.getString(R.string.charts_pause_legend_opened), x, y + legendPaint.textSize / 3f, legendPaint)
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
}
