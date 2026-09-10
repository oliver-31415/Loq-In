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

package com.oliver.loqin.feature.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * 2D saturation × brightness pad for the custom accent picker. The fill shows
 * every shade of the current hue; dragging the thumb picks a precise color.
 */
class ColorSvPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var hue: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 360f)
            shadersDirty = true
            invalidate()
        }

    var saturation: Float = 0.5f
    var brightness: Float = 0.5f

    var onColorPicked: ((saturation: Float, brightness: Float) -> Unit)? = null

    private var shadersDirty = true
    private var blackShader: Shader? = null

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val thumbFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun rebuildShaders(w: Int, h: Int) {
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        blackShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP,
        )
        // base: white→hue horizontally, then fade to black vertically on top
        basePaint.shader = ComposeShader(
            LinearGradient(
                0f, 0f, w.toFloat(), 0f,
                Color.WHITE, hueColor, Shader.TileMode.CLAMP,
            ),
            blackShader!!,
            PorterDuff.Mode.MULTIPLY,
        )
        shadersDirty = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (shadersDirty) rebuildShaders(width, height)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), basePaint)

        val cx = saturation * width
        val cy = (1f - brightness) * height
        val current = Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        val radius = max(10f, resources.displayMetrics.density * 12f)
        thumbFillPaint.color = current
        canvas.drawCircle(cx, cy, radius, thumbFillPaint)
        thumbPaint.strokeWidth = resources.displayMetrics.density * 3f
        thumbPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius, thumbPaint)
        thumbPaint.color = 0x66000000
        canvas.drawCircle(cx, cy, radius + thumbPaint.strokeWidth * 0.75f, thumbPaint)
    }

    private fun updateFromTouch(x: Float, y: Float) {
        saturation = (x / width).coerceIn(0f, 1f)
        brightness = (1f - y / height).coerceIn(0f, 1f)
        invalidate()
        onColorPicked?.invoke(saturation, brightness)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE,
            android.view.MotionEvent.ACTION_UP,
            -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x, event.y)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

/**
 * Horizontal hue bar (0–360°) with a draggable thumb. Companion to [ColorSvPadView].
 */
class ColorHueBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var hue: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 360f)
            invalidate()
        }

    var onHuePicked: ((hue: Float) -> Unit)? = null

    private var barShader: Shader? = null
    private val barPaint = Paint()
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val thumbFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (barShader == null) {
            barShader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                intArrayOf(
                    0xFFFF0000.toInt(), 0xFFFF00FF.toInt(), 0xFF0000FF.toInt(),
                    0xFF00FFFF.toInt(), 0xFF00FF00.toInt(), 0xFFFFFF00.toInt(), 0xFFFF0000.toInt(),
                ),
                null, Shader.TileMode.CLAMP,
            )
            barPaint.shader = barShader
        }
        val barHeight = height * 0.62f
        val top = (height - barHeight) / 2f
        val r = barHeight / 2f
        canvas.drawRoundRect(0f, top, width.toFloat(), top + barHeight, r, r, barPaint)

        val cx = (hue / 360f) * width
        val cy = height / 2f
        val radius = barHeight / 2f * 0.9f
        thumbFillPaint.color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        canvas.drawCircle(cx, cy, radius, thumbFillPaint)
        thumbPaint.strokeWidth = resources.displayMetrics.density * 3f
        thumbPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius, thumbPaint)
    }

    private fun updateFromTouch(x: Float) {
        hue = ((x / width) * 360f).coerceIn(0f, 360f)
        invalidate()
        onHuePicked?.invoke(hue)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE,
            android.view.MotionEvent.ACTION_UP,
            -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
