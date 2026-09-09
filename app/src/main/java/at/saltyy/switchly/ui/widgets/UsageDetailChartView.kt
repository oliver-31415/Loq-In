/*
 * Switchly
 * Copyright (C) 2025-2026 Saltyy
 * Copyright (C) 2026 Switchly Contributors
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

package at.saltyy.switchly.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import at.saltyy.switchly.R
import at.saltyy.switchly.theme.AccentColor
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * One chart to replace WeeklyBarChartView + TimeSeriesLineChartView on the
 * app-usage detail page. Bars for short series (<= 14 buckets), smooth area
 * curve for long ones — always rounded, always live accent, labels that say
 * what the bucket actually is (peak + total in the summary line above).
 *
 * No XML styling needed: paints resolve the accent + on-surface at draw time.
 */
class UsageDetailChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnBucketSelectedListener {
        fun onSelected(index: Int, valueMs: Long)
    }

    private var values: List<Long> = emptyList()
    private var labels: List<String> = emptyList()
    private var listener: OnBucketSelectedListener? = null
    private var selectedIndex: Int = -1

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val touchSlop: Int = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    fun setData(values: List<Long>, labels: List<String>) {
        this.values = values
        this.labels = labels
        this.selectedIndex = -1
        requestLayout()
        invalidate()
    }

    fun setOnBucketSelectedListener(l: OnBucketSelectedListener?) {
        listener = l
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Height comes from XML (190dp); width fills.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val accent = AccentColor.getAccentColorInt(context)
        val onSurface = resolveOnSurface()
        val faint = Color.argb(38, Color.red(onSurface), Color.green(onSurface), Color.blue(onSurface))

        labelPaint.color = Color.argb(160, Color.red(onSurface), Color.green(onSurface), Color.blue(onSurface))
        valuePaint.color = accent
        gridPaint.color = faint
        gridPaint.strokeWidth = 1f

        val density = resources.displayMetrics.density
        // Generous label band so x-labels never touch the curve or bars.
        val labelArea = 30f * density
        val topPad = (if (values.any { it > 0L }) 34f else 10f) * density
        val chartTop = topPad
        val chartBottom = (h - labelArea).coerceAtLeast(chartTop + 8f)

        // Gridlines: 3 faint horizontals, no harsh white rules.
        for (i in 0..2) {
            val y = chartTop + (chartBottom - chartTop) * i / 2f
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        if (values.isEmpty() || values.all { it <= 0L }) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                "—",
                w / 2f, (chartTop + chartBottom) / 2f, labelPaint
            )
            drawLabels(canvas, w, h - 8f * density)
            return
        }

        if (values.size <= 14) drawBars(canvas, w, chartTop, chartBottom, accent, faint)
        else drawArea(canvas, w, chartTop, chartBottom, accent)

        drawLabels(canvas, w, h - 8f * density)

        // Peak value callout above the tallest bucket, kept inside bounds.
        val maxIdx = values.indices.maxByOrNull { values[it] } ?: 0
        if (values[maxIdx] > 0L) {
            val x = bucketCenterX(maxIdx, w)
            valuePaint.textSize = 12f * density
            canvas.drawText(fmtDuration(values[maxIdx]), x.coerceIn(48f * density, w - 48f * density), chartTop - 8f * density, valuePaint)
        }
    }

    private fun bucketCenterX(i: Int, w: Float): Float {
        val n = values.size.coerceAtLeast(1)
        return w * (i + 0.5f) / n
    }

    private fun drawBars(canvas: Canvas, w: Float, top: Float, bottom: Float, accent: Int, track: Int) {
        val n = values.size
        val maxV = max(1L, values.maxOrNull() ?: 1L).toFloat()
        val gap = min(14f, w * 0.02f)
        val barW = ((w - gap * (n + 1)) / n).coerceAtLeast(4f)
        val radius = min(9f, barW / 2f)

        barPaint.color = accent
        trackPaint.color = track
        val rect = RectF()

        for (i in 0 until n) {
            val left = gap + i * (barW + gap)
            val frac = (values[i].toFloat() / maxV).coerceIn(0f, 1f)
            val barTop = bottom - ((bottom - top) * frac)
            rect.set(left, top, left + barW, bottom)
            canvas.drawRoundRect(rect, radius, radius, trackPaint)
            if (frac > 0f) {
                rect.set(left, barTop, left + barW, bottom)
                canvas.drawRoundRect(rect, radius, radius, barPaint)
                if (i == selectedIndex) {
                    barPaint.alpha = 90
                    canvas.drawRoundRect(rect, radius, radius, barPaint)
                    barPaint.alpha = 255
                }
            }
        }
    }

    private fun drawArea(canvas: Canvas, w: Float, top: Float, bottom: Float, accent: Int) {
        val n = values.size
        val maxV = max(1L, values.maxOrNull() ?: 1L).toFloat()
        fun x(i: Int) = w * i / (n - 1).coerceAtLeast(1).toFloat()
        fun y(v: Long) = bottom - ((bottom - top) * (v.toFloat() / maxV).coerceIn(0f, 1f))

        linePaint.color = accent
        linePaint.strokeWidth = 3f
        fillPaint.color = accent
        fillPaint.alpha = 52

        // Smooth curve through Catmull-Rom -> bezier.
        val line = Path()
        val fill = Path()
        if (n == 1) {
            line.moveTo(0f, y(values[0])); line.lineTo(w, y(values[0]))
            fill.moveTo(0f, y(values[0])); fill.lineTo(w, y(values[0]))
            fill.lineTo(w, bottom); fill.lineTo(0f, bottom); fill.close()
        } else {
            val pts = (0 until n).map { i -> x(i) to y(values[i]) }
            line.moveTo(pts[0].first, pts[0].second)
            fill.moveTo(pts[0].first, pts[0].second)
            for (i in 0 until n - 1) {
                val p0 = pts[max(0, i - 1)]
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts[min(n - 1, i + 2)]
                // Clamp control points to the floor: Catmull-Rom overshoots
                // below zero on near-flat stretches, which reads as broken.
                val c1x = p1.first + (p2.first - p0.first) / 6f
                val c1y = (p1.second + (p2.second - p0.second) / 6f).coerceAtMost(bottom)
                val c2x = p2.first - (p3.first - p1.first) / 6f
                val c2y = (p2.second - (p3.second - p1.second) / 6f).coerceAtMost(bottom)
                line.cubicTo(c1x, c1y, c2x, c2y, p2.first, p2.second)
                fill.cubicTo(c1x, c1y, c2x, c2y, p2.first, p2.second)
            }
            fill.lineTo(w, bottom); fill.lineTo(0f, bottom); fill.close()
        }
        canvas.drawPath(fill, fillPaint)
        canvas.drawPath(line, linePaint)

        // Selected dot.
        if (selectedIndex in 0 until n) {
            val sx = x(selectedIndex)
            val sy = y(values[selectedIndex])
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            }
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = accent
            }
            canvas.drawCircle(sx, sy, 9f, dotPaint)
            canvas.drawCircle(sx, sy, 9f, ringPaint)
        }
    }

    private fun drawLabels(canvas: Canvas, w: Float, y: Float) {
        if (labels.isEmpty()) return
        // Max ~7 labels to stay readable: sample evenly.
        val step = ((labels.size + 6) / 7).coerceAtLeast(1)
        var i = 0
        while (i < labels.size) {
            val x = if (values.size <= 14) bucketCenterX(i, w)
            else w * i / (values.size - 1).coerceAtLeast(1).toFloat()
            canvas.drawText(labels[i], x.coerceIn(20f, w - 20f), y, labelPaint)
            i += step
        }
    }

    private fun fmtDuration(ms: Long): String {
        if (ms <= 0L) return "0m"
        val m = ms / 60000L
        if (m < 60L) return "${m}m"
        return String.format(Locale.getDefault(), "%dh %02dm", m / 60, m % 60)
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

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                return true
            }
            android.view.MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (dx * dx + dy * dy > touchSlop * touchSlop) {
                    return super.onTouchEvent(event)
                }
                val idx = nearestBucket(event.x, event.y)
                if (idx != null && listener != null) {
                    selectedIndex = idx
                    invalidate()
                    listener?.onSelected(idx, values[idx])
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun nearestBucket(x: Float, y: Float): Int? {
        if (values.isEmpty()) return null
        val n = values.size
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (i in 0 until n) {
            val cx = if (n <= 14) bucketCenterX(i, width.toFloat())
            else width.toFloat() * i / (n - 1).coerceAtLeast(1).toFloat()
            val d = kotlin.math.abs(cx - x)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return if (bestDist < width.toFloat() / n + 24f) best else null
    }
}
