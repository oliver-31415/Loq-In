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
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.oliver.loqin.feature.usage.PatternsMath
import com.oliver.loqin.theme.AccentColor

/**
 * One dot per day for the first distracting-app pickup. The vertical axis runs from 04:00 at the
 * top to 24:00 at the bottom. Pickups after the bottom edge are drawn on it. Days without a pickup
 * are left empty. A dashed line marks the average.
 */
class DotStripChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val axisWidth = 40f * density
    private val labelHeight = 18f * density
    private val topPad = 8f * density
    private val dotRadius = 3.5f * density

    private var points: List<Double?> = emptyList()
    private var dayLabels: List<String> = emptyList()
    private var average: Double? = null

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val averagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * scaledDensity
    }

    /**
     * [points] holds one entry per day, oldest first. Each entry is minutes after 04:00, or null
     * when that day had no distracting-app session. [averageMinutes] draws the dashed line.
     */
    fun setData(points: List<Double?>, dayLabels: List<String>, averageMinutes: Double?) {
        this.points = points
        this.dayLabels = dayLabels
        this.average = averageMinutes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val onSurface = patternsOnSurfaceColor(context)
        val accent = AccentColor.getAccentColorInt(context)
        val axisMax = PatternsMath.PICKUP_AXIS_MINUTES.toDouble()
        val plotLeft = axisWidth
        val plotRight = width.toFloat()
        val plotTop = topPad
        val plotBottom = height - labelHeight

        fun yFor(minutes: Double): Float =
            plotTop + (plotBottom - plotTop) * (minutes.coerceIn(0.0, axisMax) / axisMax).toFloat()

        gridPaint.color = ColorUtils.setAlphaComponent(onSurface, 40)
        labelPaint.color = ColorUtils.setAlphaComponent(onSurface, 160)
        val fm = labelPaint.fontMetrics

        labelPaint.textAlign = Paint.Align.RIGHT
        for (minutes in listOf(0.0, 480.0, 960.0, axisMax)) {
            val y = yFor(minutes)
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
            val hour = PatternsMath.PICKUP_CUTOFF_HOUR + (minutes / 60.0).toInt()
            canvas.drawText(
                PatternsMath.hourLabel(hour),
                plotLeft - 6f * density,
                y - (fm.ascent + fm.descent) / 2f,
                labelPaint,
            )
        }

        val count = points.size
        if (count == 0) return
        val columnWidth = (plotRight - plotLeft) / count

        labelPaint.textAlign = Paint.Align.CENTER
        dotPaint.color = accent
        for (i in 0 until count) {
            val cx = plotLeft + columnWidth * (i + 0.5f)
            points[i]?.let { minutes ->
                canvas.drawCircle(cx, yFor(minutes), dotRadius, dotPaint)
            }
            dayLabels.getOrNull(i)?.let { label ->
                canvas.drawText(label, cx, height - 4f * density, labelPaint)
            }
        }

        average?.let { avg ->
            val y = yFor(avg)
            averagePaint.color = ColorUtils.setAlphaComponent(onSurface, 170)
            canvas.drawLine(plotLeft, y, plotRight, y, averagePaint)
        }
    }
}
