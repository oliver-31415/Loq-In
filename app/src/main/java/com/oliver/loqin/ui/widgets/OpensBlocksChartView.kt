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
 * Daily opens and blocked attempts for one app on the app detail page.
 * Opens are accent bars rising from the baseline. Blocks sit in a lane above the bars as a
 * neutral dot with the count, shown only on days with at least one block.
 *
 * Accent and neutral greys only, so the two signals stay apart without relying on hue.
 */
class OpensBlocksChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var opens: List<Int> = emptyList()
    private var blocks: List<Int> = emptyList()
    private var labels: List<String> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
    }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    init {
        contentDescription = context.getString(R.string.charts_opens_a11y)
    }

    /** [opens] and [blocks] are per day and must be the same length as [labels]. Negatives count as zero. */
    fun setData(opens: List<Int>, blocks: List<Int>, labels: List<String>) {
        this.opens = opens.map { it.coerceAtLeast(0) }
        this.blocks = blocks.map { it.coerceAtLeast(0) }
        this.labels = labels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = opens.size
        val w = width.toFloat()
        val h = height.toFloat()
        if (n == 0 || w <= 0f || h <= 0f) return
        if (opens.all { it == 0 } && blocks.all { it == 0 }) return

        val density = resources.displayMetrics.density
        val accent = AccentColor.getAccentColorInt(context)
        val onSurface = resolveOnSurface()
        val faint = withAlpha(onSurface, 38)
        val neutral = withAlpha(onSurface, 190)
        labelPaint.color = withAlpha(onSurface, 160)
        countPaint.color = neutral
        baselinePaint.color = faint

        val laneTop = 0f
        val laneHeight = 30f * density
        val labelArea = 22f * density
        val chartTop = laneTop + laneHeight + 4f * density
        val chartBottom = h - labelArea
        if (chartBottom <= chartTop + 8f) return

        canvas.drawLine(0f, chartBottom, w, chartBottom, baselinePaint)

        val slot = w / n
        val barW = (slot * 0.5f).coerceIn(4f * density, 28f * density)
        val radius = min(6f * density, barW / 2f)
        val maxOpen = max(1, opens.maxOrNull() ?: 1).toFloat()
        val markerY = laneTop + laneHeight * 0.7f
        val markerRadius = 4f * density
        val rect = RectF()
        barPaint.color = accent
        markerPaint.color = neutral

        for (i in 0 until n) {
            val cx = slot * (i + 0.5f)
            val open = opens[i]
            if (open > 0) {
                val frac = (open / maxOpen).coerceIn(0f, 1f)
                val barTop = chartBottom - (chartBottom - chartTop) * frac
                rect.set(cx - barW / 2f, barTop, cx + barW / 2f, chartBottom)
                canvas.drawRoundRect(rect, radius, radius, barPaint)
            }
            val block = blocks.getOrElse(i) { 0 }
            if (block > 0) {
                canvas.drawCircle(cx, markerY, markerRadius, markerPaint)
                canvas.drawText(block.toString(), cx, markerY - markerRadius - 3f * density, countPaint)
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
