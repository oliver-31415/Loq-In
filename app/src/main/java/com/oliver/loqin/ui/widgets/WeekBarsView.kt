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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Seven bars for the last seven days of focus time, today last, each labelled with its weekday.
 * Empty days are a thin track so the week still reads as seven slots.
 */
class WeekBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11.5f * resources.displayMetrics.scaledDensity
    }
    private val rect = RectF()
    private var values: LongArray = LongArray(DAYS)
    private var labels: List<String> = weekdayLabels()

    fun setData(lastSevenDaysMs: LongArray, bar: Int, track: Int, label: Int) {
        values = lastSevenDaysMs.copyOf(DAYS)
        barPaint.color = bar
        trackPaint.color = track
        labelPaint.color = label
        labels = weekdayLabels()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (CHART_DP * density + labelPaint.textSize * 1.8f).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val gap = 8 * density
        val slot = (width - gap * (DAYS - 1)) / DAYS
        val chartH = CHART_DP * density
        val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val minBar = 6 * density
        val radius = 8 * density
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (i in 0 until DAYS) {
            val col = if (rtl) DAYS - 1 - i else i
            val left = col * (slot + gap)
            val v = values[i]
            val h = if (v > 0) (chartH * v / max).coerceAtLeast(minBar) else minBar
            rect.set(left, chartH - h, left + slot, chartH)
            canvas.drawRoundRect(rect, radius.coerceAtMost(h / 2), radius.coerceAtMost(h / 2), if (v > 0) barPaint else trackPaint)
            canvas.drawText(labels[i], left + slot / 2, chartH + labelPaint.textSize * 1.5f, labelPaint)
        }
    }

    private fun weekdayLabels(): List<String> {
        val fmt = SimpleDateFormat("EEE", Locale.getDefault())
        val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(DAYS - 1)) }
        return List(DAYS) {
            fmt.format(day.time).also { day.add(Calendar.DAY_OF_YEAR, 1) }
        }
    }

    companion object {
        const val DAYS = 7
        private const val CHART_DP = 64f
    }
}
