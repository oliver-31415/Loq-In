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
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * Single drag-through color spectrum: the full hue rainbow runs horizontally,
 * while each column fades white (top) → pure hue (middle) → black (bottom).
 * One pad, every hue and lightness; the hex field covers exact matches.
 */
class SpectrumPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** 0..1, horizontal position → hue (0°..360°). */
    var spectrumX: Float = 0.58f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** 0..1, vertical position (0 = white top, 0.5 = pure hue, 1 = black). */
    var spectrumY: Float = 0.25f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var onColorPicked: ((x: Float, y: Float) -> Unit)? = null

    private var rainbowShader: Shader? = null
    private var shadeShader: Shader? = null
    private val rainbowPaint = Paint()
    private val shadePaint = Paint()
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val thumbFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun currentColor(): Int {
        val pure = Color.HSVToColor(floatArrayOf(spectrumX * 360f, 1f, 1f))
        return if (spectrumY <= 0.5f) {
            ColorUtils.blendARGB(Color.WHITE, pure, spectrumY * 2f)
        } else {
            ColorUtils.blendARGB(pure, Color.BLACK, (spectrumY - 0.5f) * 2f)
        }
    }

    /** Approximate inverse: place the thumb near the given color (hex-exact via the hex field). */
    fun setColor(color: Int) {
        val hsv = FloatArray(3).also { Color.colorToHSV(color, it) }
        spectrumX = hsv[0] / 360f
        // saturation drives the white→hue half, darkness drives the hue→black half
        spectrumY = if (hsv[2] >= 0.5f) 0.5f * hsv[1] else 0.5f + 0.5f * (1f - hsv[2])
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (rainbowShader == null) {
            rainbowShader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                intArrayOf(
                    0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(),
                    0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt(),
                ),
                null, Shader.TileMode.CLAMP,
            )
            rainbowPaint.shader = rainbowShader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), rainbowPaint)
            shadeShader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.WHITE, 0x00FFFFFF, Color.BLACK),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            shadePaint.shader = shadeShader
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), rainbowPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shadePaint)

        val cx = spectrumX * width
        val cy = spectrumY * height
        val radius = resources.displayMetrics.density * 12f
        thumbFillPaint.color = currentColor()
        canvas.drawCircle(cx, cy, radius, thumbFillPaint)
        thumbPaint.strokeWidth = resources.displayMetrics.density * 3f
        thumbPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius, thumbPaint)
        thumbPaint.color = 0x66000000
        canvas.drawCircle(cx, cy, radius + thumbPaint.strokeWidth * 0.75f, thumbPaint)
    }

    private fun updateFromTouch(x: Float, y: Float) {
        spectrumX = (x / width).coerceIn(0f, 1f)
        spectrumY = (y / height).coerceIn(0f, 1f)
        updateMagnifier()
        onColorPicked?.invoke(spectrumX, spectrumY)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                showMagnifier()
                updateFromTouch(event.x, event.y)
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                updateFromTouch(event.x, event.y)
                return true
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL,
            -> {
                hideMagnifier()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // --- Magnifier: compact swatch pin that rides the thumb with a pointed connector ---

    private var magnifierPopup: android.widget.PopupWindow? = null
    private val thumbScreen = IntArray(2)

    private fun buildMagnifierView(): View {
        val d = resources.displayMetrics.density
        val getter = { currentColor() }
        val circle = 48 * d
        val stem = 10 * d
        return object : View(context) {
            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat()
                val cy = circle / 2f
                val r = circle / 2f - 2.5f * d
                thumbFillPaint.color = getter()
                canvas.drawCircle(w / 2f, cy, r, thumbFillPaint)
                thumbPaint.strokeWidth = 2.5f * d
                thumbPaint.color = Color.WHITE
                canvas.drawCircle(w / 2f, cy, r, thumbPaint)
                // pointed connector down to the thumb
                val path = android.graphics.Path().apply {
                    moveTo(w / 2f - 4 * d, cy + r - 0.5f * d)
                    lineTo(w / 2f + 4 * d, cy + r - 0.5f * d)
                    lineTo(w / 2f, cy + r + stem)
                    close()
                }
                thumbFillPaint.color = Color.WHITE
                canvas.drawPath(path, thumbFillPaint)
            }
        }.apply {
            elevation = 10 * d
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, (circle + 1).toInt())
                }
            }
        }
    }

    private fun magnifierSize(): IntArray {
        val d = resources.displayMetrics.density
        val w = (48 * d).toInt()
        return intArrayOf(w, w + (10 * d).toInt())
    }

    private fun showMagnifier() {
        if (magnifierPopup != null) return
        val (w, h) = magnifierSize()
        val popup = android.widget.PopupWindow(buildMagnifierView(), w, h).apply {
            isTouchable = false
            isFocusable = false
            isOutsideTouchable = false
            elevation = 10 * resources.displayMetrics.density
        }
        magnifierPopup = popup
        popup.showAtLocation(this, android.view.Gravity.NO_GRAVITY, 0, 0)
        popup.update(0, 0, w, h)
    }

    private fun updateMagnifier() {
        val popup = magnifierPopup ?: return
        popup.contentView.invalidate()
        val (w, h) = magnifierSize()
        // PopupWindow coords are relative to this view's WINDOW, not the screen
        getLocationInWindow(thumbScreen)
        val windowW = resources.displayMetrics.widthPixels
        val cx = thumbScreen[0] + spectrumX * width
        val thumbY = thumbScreen[1] + spectrumY * height
        val x = (cx - w / 2f).coerceIn(0f, windowW - w.toFloat())
        val y = (thumbY - h - 5 * resources.displayMetrics.density).coerceAtLeast(0f)
        popup.update(x.toInt(), y.toInt(), w, h)
    }

    private fun hideMagnifier() {
        magnifierPopup?.dismiss()
        magnifierPopup = null
    }

    override fun onDetachedFromWindow() {
        hideMagnifier()
        super.onDetachedFromWindow()
    }
}
