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

package com.oliver.loqin.feature.schedule

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * A 24-hour strip for one schedule row: the day as a track, the schedule's window filled in
 * (overnight windows draw both ends), and a tick at the current time.
 */
class ScheduleTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var segments: List<Pair<Int, Int>> = emptyList()
    private var nowMinute: Int = -1

    /** [startMinute]..[endMinute] in minutes of the day; end before start wraps past midnight. */
    fun setWindow(startMinute: Int, endMinute: Int, nowMinute: Int, track: Int, fill: Int, now: Int) {
        segments = when {
            endMinute > startMinute -> listOf(startMinute to endMinute)
            endMinute < startMinute -> listOf(0 to endMinute, startMinute to DAY)
            else -> emptyList()
        }
        this.nowMinute = nowMinute
        trackPaint.color = track
        fillPaint.color = fill
        nowPaint.color = now
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (14 * density).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val barH = 6 * density
        val top = (height - barH) / 2f
        val radius = barH / 2f
        val w = width.toFloat()
        rect.set(0f, top, w, top + barH)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        fun x(minute: Int): Float = (minute / DAY.toFloat() * w).let { if (rtl) w - it else it }
        for ((start, end) in segments) {
            var a = minOf(x(start), x(end))
            var b = maxOf(x(start), x(end))
            // Short windows stay visible as a dot-sized pill rather than a hairline.
            if (b - a < barH) {
                val mid = (a + b) / 2f
                a = (mid - barH / 2f).coerceAtLeast(0f)
                b = (a + barH).coerceAtMost(w)
            }
            rect.set(a, top, b, top + barH)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }
        if (nowMinute in 0 until DAY) {
            val nx = x(nowMinute).coerceIn(density, w - density)
            rect.set(nx - density, 0f, nx + density, height.toFloat())
            canvas.drawRoundRect(rect, density, density, nowPaint)
        }
    }

    private companion object {
        const val DAY = 24 * 60
    }
}
