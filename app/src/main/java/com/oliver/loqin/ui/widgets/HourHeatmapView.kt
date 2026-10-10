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
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.oliver.loqin.feature.usage.PatternsMath
import com.oliver.loqin.theme.AccentColor

/**
 * Weekday by hour grid. Rows are Monday to Sunday, columns are hours 0 to 23. Cell shade follows
 * the accent colour at an intensity relative to the busiest cell. Empty cells show a faint track.
 */
class HourHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val leftLabelWidth = 18f * density
    private val topLabelHeight = 16f * density
    private val cellHeight = 18f * density
    private val gap = 2f * density
    private val corner = 3f * density
    private val markRadius = 2.5f * density

    private var grid: Array<DoubleArray> =
        Array(PatternsMath.WEEKDAY_COUNT) { DoubleArray(PatternsMath.HOUR_COUNT) }
    private var dayInitials: List<String> = emptyList()
    private var marks: Set<Int> = emptySet()

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * scaledDensity
    }
    private val rect = RectF()

    /**
     * [grid] is indexed [weekday Monday-first][hour], as produced by PatternsMath.heatmapAverages.
     * [dayInitials] holds one short label per weekday row.
     */
    fun setData(grid: Array<DoubleArray>, dayInitials: List<String>) {
        this.grid = grid
        this.dayInitials = dayInitials
        invalidate()
    }

    /** Cells to mark with a dot, as weekday * 24 + hour. Used for archived block events. */
    fun setBlockMarks(cells: Set<Int>) {
        marks = cells
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wanted = topLabelHeight +
            PatternsMath.WEEKDAY_COUNT * cellHeight +
            (PatternsMath.WEEKDAY_COUNT - 1) * gap
        setMeasuredDimension(
            resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec),
            resolveSize(wanted.toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val hours = PatternsMath.HOUR_COUNT
        val onSurface = patternsOnSurfaceColor(context)
        val accent = AccentColor.getAccentColorInt(context)
        val cellWidth = (width - leftLabelWidth - gap * (hours - 1)) / hours

        var peak = 0.0
        for (row in grid) for (v in row) if (v > peak) peak = v

        trackPaint.color = ColorUtils.setAlphaComponent(onSurface, 24)
        cellPaint.color = accent
        markPaint.color = ColorUtils.setAlphaComponent(onSurface, 220)

        for (day in 0 until PatternsMath.WEEKDAY_COUNT) {
            val top = topLabelHeight + day * (cellHeight + gap)
            for (hour in 0 until hours) {
                val left = leftLabelWidth + hour * (cellWidth + gap)
                rect.set(left, top, left + cellWidth, top + cellHeight)
                canvas.drawRoundRect(rect, corner, corner, trackPaint)

                val value = grid.getOrNull(day)?.getOrNull(hour) ?: 0.0
                if (value > 0.0 && peak > 0.0) {
                    val intensity = (value / peak).coerceIn(0.0, 1.0)
                    cellPaint.alpha = (70 + 185 * intensity).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(rect, corner, corner, cellPaint)
                }

                if (marks.contains(day * hours + hour)) {
                    canvas.drawCircle(rect.centerX(), rect.centerY(), markRadius, markPaint)
                }
            }
        }

        labelPaint.color = ColorUtils.setAlphaComponent(onSurface, 160)

        labelPaint.textAlign = Paint.Align.CENTER
        for (hour in listOf(0, 6, 12, 18)) {
            val cx = leftLabelWidth + hour * (cellWidth + gap) + cellWidth / 2f
            canvas.drawText(hour.toString(), cx, topLabelHeight - 4f * density, labelPaint)
        }

        val fm = labelPaint.fontMetrics
        for (day in 0 until PatternsMath.WEEKDAY_COUNT) {
            val cy = topLabelHeight + day * (cellHeight + gap) + cellHeight / 2f
            val baseline = cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(dayInitials.getOrElse(day) { "" }, leftLabelWidth / 2f, baseline, labelPaint)
        }
    }
}
