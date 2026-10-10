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
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.oliver.loqin.R
import com.oliver.loqin.feature.usage.DailyStackedUsage
import com.oliver.loqin.theme.AccentColor
import kotlin.math.max
import kotlin.math.min

/**
 * One bar per day, stacked by the top apps plus an "Other" segment.
 *
 * Colours are a single accent hue in four lightness steps for the top apps and a neutral grey
 * for "Other", so segments differ by lightness rather than by hue. A faint outline shows the
 * previous period at the same position, and an optional dashed line marks the daily goal.
 *
 * Labels per day are supplied by the caller; an empty string leaves that day unlabelled.
 * The legend is not drawn here; use [colorForKey] to colour matching swatches.
 */
class StackedDailyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var result: DailyStackedUsage.Result =
        DailyStackedUsage.Result(days = emptyList(), previousMs = emptyList(), legendKeys = emptyList())
    private var labels: List<String> = emptyList()
    private var goalMs: Long = 0L

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.25f * density
    }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(3f * density, 4f * density), 0f)
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
    }

    private val clipPath = Path()
    private val rect = RectF()

    /**
     * @param result shaped segments per day, see [DailyStackedUsage.build]
     * @param labels one label per day; empty strings are skipped
     * @param goalMs daily goal in ms, or 0 for no goal line
     */
    fun setData(result: DailyStackedUsage.Result, labels: List<String>, goalMs: Long) {
        this.result = result
        this.labels = labels
        this.goalMs = goalMs.coerceAtLeast(0L)
        requestLayout()
        invalidate()
    }

    /** Colour used for [key] in the bars. Unknown keys fall back to the grey used for Other. */
    fun colorForKey(key: String): Int {
        val onSurface = resolveOnSurface()
        if (key == DailyStackedUsage.OTHER_KEY) return withAlpha(onSurface, OTHER_ALPHA)
        val index = result.legendKeys.indexOf(key)
        if (index < 0) return withAlpha(onSurface, OTHER_ALPHA)
        return seriesColor(index, AccentColor.getAccentColorInt(context), onSurface)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Height comes from XML; width fills.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val accent = AccentColor.getAccentColorInt(context)
        val onSurface = resolveOnSurface()
        labelPaint.color = withAlpha(onSurface, LABEL_ALPHA)
        outlinePaint.color = withAlpha(onSurface, PREVIOUS_OUTLINE_ALPHA)
        goalPaint.color = withAlpha(onSurface, GOAL_ALPHA)
        baselinePaint.color = withAlpha(onSurface, BASELINE_ALPHA)

        val labelBand = LABEL_BAND_DP * density
        val topPad = TOP_PAD_DP * density
        val chartTop = topPad
        val chartBottom = (h - labelBand).coerceAtLeast(chartTop + 8f * density)
        val chartHeight = chartBottom - chartTop

        canvas.drawLine(0f, chartBottom, w, chartBottom, baselinePaint)

        val days = result.days
        if (days.isEmpty()) return

        val yMaxRaw = max(
            days.maxOfOrNull { it.totalMs } ?: 0L,
            max(result.previousMs.maxOrNull() ?: 0L, goalMs),
        )
        val yMax = if (yMaxRaw <= 0L) 1f else yMaxRaw.toFloat() * 1.1f

        fun yFor(ms: Long): Float = chartBottom - (ms.coerceAtLeast(0L).toFloat() / yMax) * chartHeight

        val slot = w / days.size
        val barWidth = min(slot * 0.62f, MAX_BAR_DP * density)
        val radius = 3f * density
        outlinePaint.strokeWidth = 1.25f * density

        days.forEachIndexed { i, day ->
            val cx = slot * (i + 0.5f)
            val left = cx - barWidth / 2f
            val right = cx + barWidth / 2f

            // Previous period outline, behind the current bar.
            val prev = result.previousMs.getOrNull(i) ?: 0L
            if (prev > 0L) {
                val prevTop = yFor(prev)
                rect.set(left, prevTop, right, chartBottom)
                canvas.drawRoundRect(rect, radius, radius, outlinePaint)
            }

            if (day.totalMs > 0L) {
                val barTop = yFor(day.totalMs)
                clipPath.reset()
                rect.set(left, barTop, right, chartBottom)
                clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clipPath)
                var bottom = chartBottom
                for (segment in day.segments) {
                    if (segment.ms <= 0L) continue
                    val segHeight = segment.ms.toFloat() / yMax * chartHeight
                    val segTop = bottom - segHeight
                    barPaint.color = colorForKeyInternal(segment.key, accent, onSurface)
                    canvas.drawRect(left, segTop, right, bottom, barPaint)
                    bottom = segTop
                }
                canvas.restore()
            }

            labels.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { label ->
                canvas.drawText(label, cx, h - (LABEL_BASELINE_DP * density), labelPaint)
            }
        }

        if (goalMs > 0L) {
            val gy = yFor(goalMs)
            canvas.drawLine(0f, gy, w, gy, goalPaint)
        }
    }

    private fun colorForKeyInternal(key: String, accent: Int, onSurface: Int): Int {
        if (key == DailyStackedUsage.OTHER_KEY) return withAlpha(onSurface, OTHER_ALPHA)
        val index = result.legendKeys.indexOf(key).let { if (it < 0) 0 else it }
        return seriesColor(index, accent, onSurface)
    }

    /**
     * Opaque, clearly separated series: the accent, a dark and a light shade of it, then a
     * neutral grey. Differ in lightness rather than hue, so they stay apart for colour-blind
     * users; "Other" is a fainter grey still.
     */
    private fun seriesColor(index: Int, accent: Int, onSurface: Int): Int = when (index) {
        0 -> accent
        1 -> ColorUtils.blendARGB(accent, Color.BLACK, 0.45f)
        2 -> ColorUtils.blendARGB(accent, Color.WHITE, 0.55f)
        else -> withAlpha(onSurface, NEUTRAL_SERIES_ALPHA)
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
        const val NEUTRAL_SERIES_ALPHA = 150
        const val OTHER_ALPHA = 64
        const val LABEL_ALPHA = 160
        const val PREVIOUS_OUTLINE_ALPHA = 89 // about 35 percent
        const val GOAL_ALPHA = 170
        const val BASELINE_ALPHA = 40
        const val TOP_PAD_DP = 8f
        const val LABEL_BAND_DP = 22f
        const val LABEL_BASELINE_DP = 6f
        const val MAX_BAR_DP = 26f
    }
}
