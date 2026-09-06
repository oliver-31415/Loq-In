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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/**
 * A modern circular clock dial view for selecting focus / lock-in duration.
 * Users can spin the dial around the clock face to adjust the time,
 * with clock ticks, quarter markings (15, 30, 45, 60), active accent arc,
 * and a tactile glowing knob.
 */
class ClockDurationDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    var minMinutes: Int = 5
    var maxMinutes: Int = 180

    var durationMinutes: Int = 15
        private set

    var accentColor: Int = Color.parseColor("#4CAF50")
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var onDurationChanged: ((Int) -> Unit)? = null
    var onDurationChangeFinished: ((Int) -> Unit)? = null

    // Touch & state tracking
    private var baseHourMinutes: Int = 0
    private var previousAngle: Float = -1f
    private var isDragging: Boolean = false
    private var animator: ValueAnimator? = null

    // Geometry
    private val arcBounds = RectF()
    private var centerX = 0f
    private var centerY = 0f
    private var dialRadius = 0f

    // Paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val activeArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val baseCircleFullPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val thumbGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        updatePaints()
    }

    private fun updatePaints() {
        val trackWidth = dp(9f)
        val arcWidth = dp(9f)

        trackPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 0x1A)
        trackPaint.strokeWidth = trackWidth

        activeArcPaint.color = accentColor
        activeArcPaint.strokeWidth = arcWidth

        baseCircleFullPaint.color = ColorUtils.setAlphaComponent(accentColor, 0x55)
        baseCircleFullPaint.strokeWidth = arcWidth

        tickPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 0x2E)
        tickPaint.strokeWidth = dp(1.75f)

        majorTickPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 0x66)
        majorTickPaint.strokeWidth = dp(2.5f)

        labelPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 0x88)
        labelPaint.textSize = dp(11f)

        thumbPaint.color = accentColor
        thumbGlowPaint.color = ColorUtils.setAlphaComponent(accentColor, 0x38)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        val padding = dp(26f)
        dialRadius = (min(w, h) / 2f) - padding
        arcBounds.set(
            centerX - dialRadius,
            centerY - dialRadius,
            centerX + dialRadius,
            centerY + dialRadius
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dialRadius <= 0f) return

        // 1. Base circular track
        canvas.drawCircle(centerX, centerY, dialRadius, trackPaint)

        // 2. Draw clock tick marks (12 clock positions at 30° intervals)
        val tickOuter = dialRadius - dp(10f)
        val tickInnerMinor = dialRadius - dp(17f)
        val tickInnerMajor = dialRadius - dp(20f)
        val labelRadius = dialRadius - dp(30f)

        for (i in 0 until 12) {
            val angleDeg = i * 30f
            val rad = Math.toRadians((angleDeg - 90f).toDouble())
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()

            val isQuarter = (i % 3 == 0) // 12, 3, 6, 9
            val innerR = if (isQuarter) tickInnerMajor else tickInnerMinor
            val paint = if (isQuarter) majorTickPaint else tickPaint

            val startX = centerX + innerR * cosA
            val startY = centerY + innerR * sinA
            val endX = centerX + tickOuter * cosA
            val endY = centerY + tickOuter * sinA

            canvas.drawLine(startX, startY, endX, endY, paint)

            // Numbers at quarters: 60, 15, 30, 45
            if (isQuarter) {
                val labelText = when (i) {
                    0 -> "60"
                    3 -> "15"
                    6 -> "30"
                    9 -> "45"
                    else -> ""
                }
                val lx = centerX + labelRadius * cosA
                // Adjust Y baseline for vertical centering
                val fontMetrics = labelPaint.fontMetrics
                val ly = centerY + labelRadius * sinA - (fontMetrics.ascent + fontMetrics.descent) / 2f
                canvas.drawText(labelText, lx, ly, labelPaint)
            }
        }

        // 3. Active Arc
        val minuteInHour = durationMinutes % 60
        val sweepAngle = if (durationMinutes > 0 && minuteInHour == 0) {
            360f
        } else {
            (minuteInHour / 60f) * 360f
        }

        // If >= 60 minutes, draw completed hour circle underneath
        if (durationMinutes >= 60) {
            canvas.drawCircle(centerX, centerY, dialRadius, baseCircleFullPaint)
        }

        if (sweepAngle > 0f) {
            canvas.drawArc(arcBounds, -90f, sweepAngle, false, activeArcPaint)
        }

        // 4. Glowing Thumb Knob
        val thumbAngleDeg = -90f + sweepAngle
        val thumbRad = Math.toRadians(thumbAngleDeg.toDouble())
        val tx = centerX + dialRadius * cos(thumbRad).toFloat()
        val ty = centerY + dialRadius * sin(thumbRad).toFloat()

        // Thumb glow
        canvas.drawCircle(tx, ty, dp(18f), thumbGlowPaint)
        // Main thumb circle
        canvas.drawCircle(tx, ty, dp(12.5f), thumbPaint)
        // Center white core
        canvas.drawCircle(tx, ty, dp(4.5f), thumbCorePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                updateAngleFromTouch(event.x, event.y, isInitial = true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    updateAngleFromTouch(event.x, event.y, isInitial = false)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onDurationChangeFinished?.invoke(durationMinutes)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateAngleFromTouch(x: Float, y: Float, isInitial: Boolean) {
        val dx = x - centerX
        val dy = y - centerY

        // Angle from 12 o'clock clockwise: 0° .. 360°
        var touchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        if (touchAngle < 0f) touchAngle += 360f

        if (!isInitial && previousAngle >= 0f) {
            val delta = touchAngle - previousAngle
            // Check crossing 12 o'clock boundary (0° / 360°)
            if (delta < -220f) {
                // Clockwise crossing: e.g. 350° -> 10°
                if (baseHourMinutes + 60 <= maxMinutes) {
                    baseHourMinutes += 60
                }
            } else if (delta > 220f) {
                // Counter-clockwise crossing: e.g. 10° -> 350°
                if (baseHourMinutes >= 60) {
                    baseHourMinutes -= 60
                }
            }
        }

        // Convert touch angle to 5-minute increments (0..60)
        val rawMinuteInHour = (touchAngle / 360f) * 60f
        var snappedMinute = (round(rawMinuteInHour / 5.0) * 5).toInt()
        if (snappedMinute == 0 && baseHourMinutes == 0) {
            snappedMinute = 5
        }

        val total = (baseHourMinutes + snappedMinute).coerceIn(minMinutes, maxMinutes)

        if (total != durationMinutes) {
            durationMinutes = total
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onDurationChanged?.invoke(durationMinutes)
            invalidate()
        }

        previousAngle = touchAngle
    }

    /**
     * Programmatically set duration, with optional smooth rotation animation.
     */
    fun setDurationMinutes(minutes: Int, animate: Boolean = true) {
        val target = minutes.coerceIn(minMinutes, maxMinutes)
        if (!animate) {
            durationMinutes = target
            baseHourMinutes = (target / 60) * 60
            previousAngle = -1f
            invalidate()
            onDurationChanged?.invoke(target)
            return
        }

        animator?.cancel()
        val startVal = durationMinutes
        animator = ValueAnimator.ofInt(startVal, target).apply {
            duration = 280L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val v = va.animatedValue as Int
                if (v != durationMinutes) {
                    durationMinutes = v
                    baseHourMinutes = (durationMinutes / 60) * 60
                    invalidate()
                    onDurationChanged?.invoke(durationMinutes)
                }
            }
            start()
        }
    }
}
