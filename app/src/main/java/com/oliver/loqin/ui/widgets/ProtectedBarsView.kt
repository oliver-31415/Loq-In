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
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.oliver.loqin.theme.AccentColor

/** One day's protected time out of the time that day could be observed. */
data class ProtectionDay(val protectedMinutes: Double, val totalMinutes: Double)

/**
 * One stacked bar per day on a 24 hour scale. The accent segment is protected time and the faint
 * segment above it is the rest of the observed day. Days with no data show "no data" rotated in
 * their column.
 */
class ProtectedBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val labelHeight = 18f * density
    private val topPad = 4f * density

    private var days: List<ProtectionDay?> = emptyList()
    private var dayLabels: List<String> = emptyList()
    private var noDataLabel: String = ""

    private val protectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val restPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * scaledDensity
    }

    /**
     * [days] holds one entry per day, oldest first. A null entry means no stored segments for that
     * day and shows [noDataLabel] in its column. [dayLabels] are drawn under each column.
     */
    fun setData(days: List<ProtectionDay?>, dayLabels: List<String>, noDataLabel: String) {
        this.days = days
        this.dayLabels = dayLabels
        this.noDataLabel = noDataLabel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = days.size
        if (count == 0) return

        val onSurface = patternsOnSurfaceColor(context)
        val accent = AccentColor.getAccentColorInt(context)
        val plotTop = topPad
        val plotBottom = height - labelHeight
        val plotHeight = plotBottom - plotTop
        val columnWidth = width.toFloat() / count
        val barWidth = columnWidth * 0.6f
        val fullDay = FULL_DAY_MINUTES

        protectedPaint.color = accent
        restPaint.color = ColorUtils.setAlphaComponent(onSurface, 40)
        labelPaint.color = ColorUtils.setAlphaComponent(onSurface, 160)
        val fm = labelPaint.fontMetrics

        for (i in 0 until count) {
            val cx = columnWidth * (i + 0.5f)
            val day = days[i]
            if (day == null || day.totalMinutes <= 0.0) {
                val cy = (plotTop + plotBottom) / 2f
                canvas.save()
                canvas.rotate(-90f, cx, cy)
                labelPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(noDataLabel, cx, cy - (fm.ascent + fm.descent) / 2f, labelPaint)
                canvas.restore()
            } else {
                val protectedFrac = (day.protectedMinutes / fullDay).coerceIn(0.0, 1.0).toFloat()
                val totalFrac = (day.totalMinutes / fullDay).coerceIn(0.0, 1.0).toFloat()
                val protectedTop = plotBottom - plotHeight * protectedFrac
                val totalTop = plotBottom - plotHeight * totalFrac
                val left = cx - barWidth / 2f
                val right = cx + barWidth / 2f
                if (totalTop < protectedTop) {
                    canvas.drawRect(left, totalTop, right, protectedTop, restPaint)
                }
                canvas.drawRect(left, protectedTop, right, plotBottom, protectedPaint)
            }

            dayLabels.getOrNull(i)?.let { label ->
                labelPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(label, cx, height - 4f * density, labelPaint)
            }
        }
    }

    private companion object {
        const val FULL_DAY_MINUTES = 24.0 * 60.0
    }
}
