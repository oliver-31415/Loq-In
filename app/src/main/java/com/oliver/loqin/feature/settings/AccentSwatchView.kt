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

package com.oliver.loqin.feature.settings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.SweepGradient
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import kotlin.math.roundToInt

/**
 * One swatch in the Appearance theme colour grid.
 *
 * Presets are a filled disc. The Custom swatch is a rainbow disc with a plus
 * icon; once a custom colour is active it becomes a rainbow ring around a disc
 * of that colour. The selected state adds a ring in the swatch colour, set
 * apart from the disc by a small gap, and a check mark on the disc.
 */
class AccentSwatchView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rainbowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(RING_STROKE_DP)
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(GLYPH_STROKE_DP)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glyphPath = Path()

    private var swatchColor: Int = Color.TRANSPARENT
    private var isCustom: Boolean = false
    private var selectionAnimator: ValueAnimator? = null

    /** 0 = not selected, 1 = fully selected. Animated by [setSelected]. */
    private var selectionProgress = 0f
        set(value) {
            field = value
            invalidate()
        }

    init {
        isFocusable = true
        isClickable = true
        val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
        background = attrs.getDrawable(0)
        attrs.recycle()
    }

    /**
     * Sets what the swatch shows. [color] is the preset colour, or the custom
     * colour for the Custom swatch when it is active.
     */
    fun bind(name: CharSequence, color: Int, custom: Boolean, selected: Boolean) {
        contentDescription = name
        swatchColor = color
        isCustom = custom
        selectionAnimator?.cancel()
        selectionProgress = if (selected) 1f else 0f
        updateStateDescription(selected)
    }

    /**
     * Shows or hides the selected state. With [durationMs] above zero the ring
     * and check fade in or out instead of snapping.
     */
    fun setChosen(chosen: Boolean, durationMs: Long = 0L) {
        updateStateDescription(chosen)
        selectionAnimator?.cancel()
        val target = if (chosen) 1f else 0f
        if (durationMs <= 0L) {
            selectionProgress = target
            return
        }
        selectionAnimator = ValueAnimator.ofFloat(selectionProgress, target).apply {
            duration = durationMs
            addUpdateListener { selectionProgress = it.animatedValue as Float }
            start()
        }
    }

    private fun updateStateDescription(selected: Boolean) {
        ViewCompat.setStateDescription(
            this,
            context.getString(if (selected) R.string.pref_theme_color_selected else R.string.pref_theme_color_not_selected),
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = dp(TOUCH_SIZE_DP).roundToInt()
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rainbowPaint.shader = SweepGradient(w / 2f, h / 2f, RAINBOW, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val progress = selectionProgress
        val alpha = (progress * 255f).roundToInt()

        if (progress > 0f) {
            ringPaint.color = ColorUtils.setAlphaComponent(swatchColor, alpha)
            canvas.drawCircle(cx, cy, dp(RING_RADIUS_DP), ringPaint)
        }

        if (isCustom) {
            canvas.drawCircle(cx, cy, dp(DISC_RADIUS_DP), rainbowPaint)
            if (progress > 0f) {
                discPaint.color = ColorUtils.setAlphaComponent(swatchColor, alpha)
                canvas.drawCircle(cx, cy, dp(CUSTOM_INNER_RADIUS_DP), discPaint)
            }
        } else {
            discPaint.color = swatchColor
            canvas.drawCircle(cx, cy, dp(DISC_RADIUS_DP), discPaint)
        }

        when {
            progress > 0f -> drawCheck(canvas, cx, cy, alpha)
            isCustom -> drawPlus(canvas, cx, cy)
        }
    }

    private fun drawCheck(canvas: Canvas, cx: Float, cy: Float, alpha: Int) {
        val s = dp(CHECK_SIZE_DP)
        glyphPaint.color = ColorUtils.setAlphaComponent(glyphColorFor(swatchColor), alpha)
        glyphPath.reset()
        glyphPath.moveTo(cx - s * 0.9f, cy + s * 0.05f)
        glyphPath.lineTo(cx - s * 0.25f, cy + s * 0.7f)
        glyphPath.lineTo(cx + s * 0.95f, cy - s * 0.65f)
        canvas.drawPath(glyphPath, glyphPaint)
    }

    private fun drawPlus(canvas: Canvas, cx: Float, cy: Float) {
        val s = dp(PLUS_SIZE_DP)
        glyphPaint.color = Color.WHITE
        canvas.drawLine(cx - s, cy, cx + s, cy, glyphPaint)
        canvas.drawLine(cx, cy - s, cx, cy + s, glyphPaint)
    }

    /** Dark glyph on light fills (Amber), white on dark fills, matching the accent text rule. */
    private fun glyphColorFor(fill: Int): Int =
        if (AccentColor.prefersDarkText(fill)) DARK_GLYPH else Color.WHITE

    private fun dp(value: Float): Float = value * density

    private companion object {
        const val TOUCH_SIZE_DP = 48f
        const val DISC_RADIUS_DP = 18f
        const val CUSTOM_INNER_RADIUS_DP = 13f
        const val RING_RADIUS_DP = 22.25f
        const val RING_STROKE_DP = 2.5f
        const val GLYPH_STROKE_DP = 2.5f
        const val CHECK_SIZE_DP = 6.5f
        const val PLUS_SIZE_DP = 5.5f
        const val DARK_GLYPH = 0xFF1B1B18.toInt()

        val RAINBOW = intArrayOf(
            0xFFFF0000.toInt(),
            0xFFFFFF00.toInt(),
            0xFF00FF00.toInt(),
            0xFF00FFFF.toInt(),
            0xFF0000FF.toInt(),
            0xFFFF00FF.toInt(),
            0xFFFF0000.toInt(),
        )
    }
}
