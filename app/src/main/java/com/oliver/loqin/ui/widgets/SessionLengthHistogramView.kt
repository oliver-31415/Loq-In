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
 * Histogram of session lengths for one app, one accent bar per bucket with the session count
 * above it. Buckets are laid out by [com.oliver.loqin.feature.usage.SessionLengthBuckets]; the
 * caller supplies the bucket labels.
 *
 * Accent and on-surface greys only. Empty buckets still get a slot so the x-axis stays fixed.
 */
class SessionLengthHistogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var counts: List<Int> = emptyList()
    private var labels: List<String> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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
        contentDescription = context.getString(R.string.charts_sessions_a11y)
    }

    /** [counts] has one entry per bucket, [labels] has the matching bucket names. */
    fun setData(counts: List<Int>, labels: List<String>) {
        this.counts = counts.map { it.coerceAtLeast(0) }
        this.labels = labels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = counts.size
        val w = width.toFloat()
        val h = height.toFloat()
        if (n == 0 || w <= 0f || h <= 0f) return
        if (counts.all { it == 0 }) return

        val density = resources.displayMetrics.density
        val accent = AccentColor.getAccentColorInt(context)
        val onSurface = resolveOnSurface()
        labelPaint.color = withAlpha(onSurface, 160)
        countPaint.color = accent
        baselinePaint.color = withAlpha(onSurface, 38)
        barPaint.color = accent

        val labelArea = 22f * density
        val countArea = 20f * density
        val chartTop = countArea
        val chartBottom = h - labelArea
        if (chartBottom <= chartTop + 8f) return

        canvas.drawLine(0f, chartBottom, w, chartBottom, baselinePaint)

        val slot = w / n
        val barW = (slot * 0.56f).coerceIn(6f * density, 48f * density)
        val radius = min(8f * density, barW / 2f)
        val maxCount = max(1, counts.maxOrNull() ?: 1).toFloat()
        val rect = RectF()

        for (i in 0 until n) {
            val count = counts[i]
            val cx = slot * (i + 0.5f)
            if (count > 0) {
                val frac = (count / maxCount).coerceIn(0f, 1f)
                val barTop = chartBottom - (chartBottom - chartTop) * frac
                rect.set(cx - barW / 2f, barTop, cx + barW / 2f, chartBottom)
                canvas.drawRoundRect(rect, radius, radius, barPaint)
                canvas.drawText(count.toString(), cx, barTop - 5f * density, countPaint)
            }
            labels.getOrNull(i)?.let { canvas.drawText(it, cx, h - 6f * density, labelPaint) }
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
