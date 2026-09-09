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

    var minMinutes: Int = 0
    var maxMinutes: Int = 180
        set(value) {
            field = value.coerceAtLeast(0)
            if (durationMinutes > field) {
                setDurationMinutes(field, animate = false)
            }
        }

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
    private var isClampedAtZero: Boolean = false
    private var isClampedAtMax: Boolean = false
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = dp(250f).toInt()
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val w = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(desiredSize, widthSize)
            else -> desiredSize
        }
        val h = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(desiredSize, heightSize)
            else -> desiredSize
        }
        val size = min(w, h)
        setMeasuredDimension(size, size)
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

        if (sweepAngle >= 360f) {
            canvas.drawCircle(centerX, centerY, dialRadius, activeArcPaint)
        } else if (sweepAngle > 0f) {
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
                    isClampedAtZero = false
                    isClampedAtMax = false
                    previousAngle = -1f
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onDurationChangeFinished?.invoke(durationMinutes)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateAngleFromTouch(x: Float, y: Float, isInitial: Boolean) {
        if (maxMinutes <= 0) {
            if (durationMinutes != 0) {
                durationMinutes = 0
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onDurationChanged?.invoke(0)
                invalidate()
            }
            isClampedAtZero = true
            isClampedAtMax = true
            previousAngle = 0f
            return
        }

        val dx = x - centerX
        val dy = y - centerY

        // Angle from 12 o'clock clockwise: 0° .. 360°
        var touchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        if (touchAngle < 0f) touchAngle += 360f

        val isFinalHour = (baseHourMinutes + 60 >= maxMinutes)
        val remMinutesInHour = (maxMinutes - baseHourMinutes).coerceIn(0, 60)
        val maxAngleThisHour = if (remMinutesInHour == 60) 360f else (remMinutesInHour / 60f) * 360f

        if (isInitial) {
            isClampedAtZero = false
            isClampedAtMax = false
            if (durationMinutes == 0 && touchAngle > 300f) {
                // Tapped slightly to the left of 12 o'clock while at 0: keep at 0
                isClampedAtZero = true
                previousAngle = 0f
                return
            }

            val rawMinute = (touchAngle / 360f) * 60f
            var snapped = (round(rawMinute / 5.0) * 5).toInt()
            if (snapped == 60) {
                snapped = 0
                baseHourMinutes = (baseHourMinutes + 60).coerceAtMost(maxMinutes)
            }
            val total = (baseHourMinutes + snapped).coerceIn(minMinutes, maxMinutes)
            if (total == 0) {
                isClampedAtZero = true
            }
            if (total >= maxMinutes) {
                isClampedAtMax = true
            }
            if (total != durationMinutes) {
                durationMinutes = total
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onDurationChanged?.invoke(durationMinutes)
                invalidate()
            }
            previousAngle = if (total >= maxMinutes && isFinalHour) maxAngleThisHour else touchAngle
            return
        }

        // Active dragging state: Zero clamping
        if (isClampedAtZero) {
            if (touchAngle > 180f) {
                // Finger still in counter-clockwise half; cannot run backwards past 0
                previousAngle = 0f
                return
            } else {
                // Finger moved clockwise back into [0°, 180°]
                isClampedAtZero = false
                previousAngle = 0f
            }
        }

        // Active dragging state: Max clamping
        if (isClampedAtMax) {
            if (isFinalHour && remMinutesInHour < 60) {
                // In final partial hour: clamp if still clockwise of maxAngleThisHour
                val diff = touchAngle - maxAngleThisHour
                if (diff in 0f..180f || diff < -180f) {
                    previousAngle = maxAngleThisHour
                    return
                } else {
                    isClampedAtMax = false
                    previousAngle = maxAngleThisHour
                }
            } else {
                if (touchAngle < 180f) {
                    previousAngle = 360f
                    return
                } else {
                    isClampedAtMax = false
                    previousAngle = 360f
                }
            }
        }

        if (previousAngle >= 0f) {
            val delta = touchAngle - previousAngle
            // Clockwise crossing: e.g. 350° -> 10°
            if (delta < -220f) {
                if (baseHourMinutes + 60 <= maxMinutes) {
                    baseHourMinutes += 60
                } else {
                    isClampedAtMax = true
                    if (durationMinutes != maxMinutes) {
                        durationMinutes = maxMinutes
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onDurationChanged?.invoke(durationMinutes)
                        invalidate()
                    }
                    previousAngle = 360f
                    return
                }
            } else if (delta > 220f) {
                // Counter-clockwise crossing: e.g. 10° -> 350°
                if (baseHourMinutes >= 60) {
                    baseHourMinutes -= 60
                } else {
                    // Cannot run backwards past 0!
                    isClampedAtZero = true
                    baseHourMinutes = 0
                    if (durationMinutes != 0) {
                        durationMinutes = 0
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onDurationChanged?.invoke(durationMinutes)
                        invalidate()
                    }
                    previousAngle = 0f
                    return
                }
            }
        }

        // Check if moving clockwise past maxAngle in final hour
        if (isFinalHour && remMinutesInHour < 60 && touchAngle >= maxAngleThisHour && previousAngle <= maxAngleThisHour) {
            isClampedAtMax = true
            if (durationMinutes != maxMinutes) {
                durationMinutes = maxMinutes
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onDurationChanged?.invoke(durationMinutes)
                invalidate()
            }
            previousAngle = maxAngleThisHour
            return
        }

        val rawMinuteInHour = (touchAngle / 360f) * 60f
        val snappedMinute = (round(rawMinuteInHour / 5.0) * 5).toInt()
        val total = if (snappedMinute == 60) {
            (baseHourMinutes + 60).coerceAtMost(maxMinutes)
        } else {
            (baseHourMinutes + snappedMinute).coerceIn(minMinutes, maxMinutes)
        }

        if (total == 0) {
            isClampedAtZero = true
        }
        if (total >= maxMinutes) {
            isClampedAtMax = true
        }

        if (total != durationMinutes) {
            durationMinutes = total
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onDurationChanged?.invoke(durationMinutes)
            invalidate()
        }

        previousAngle = if (total >= maxMinutes && isFinalHour) maxAngleThisHour else touchAngle
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
