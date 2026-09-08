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

package at.saltyy.switchly.ui

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders the organic Foqos-style hero blob art.
 * Used as a live animated Drawable inside MainActivity, and as a static high-res
 * snapshot renderer for home-screen AppWidgets.
 */
object HeroArtRenderer {

    /**
     * Renders a static snapshot of the hero artwork onto a Bitmap.
     * Ideal for RemoteViews / AppWidgets where live animators cannot run.
     */
    fun renderBitmap(
        widthPx: Int,
        heightPx: Int,
        accent: Int,
        radiusPx: Float,
        phaseOffset: Float = 0.5f,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = HeroArtDrawable(accent, radiusPx, staticPhase = phaseOffset)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }
}

/**
 * Organic, multi-layer metaball / blob shader art derived from the active accent.
 */
class HeroArtDrawable(
    private val accent: Int,
    private val radiusPx: Float,
    private val staticPhase: Float? = null,
) : Drawable() {

    private class Blob(
        val fx: Float, val fy: Float,
        val fr: Float,
        val seed: Float,
        val driftX: Float = 0.025f,
        val driftY: Float = 0.02f,
    )

    private class Pair(
        val a: Blob,
        val b: Blob,
        val phase: Float,
        val converge: Float,
    )

    private class Layer(val shaderTop: Int, val shaderBottom: Int, val pair: Pair?, val single: Blob?)

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val layerPath = Path()
    private val tempPath = Path()
    private val shaders = arrayOfNulls<Shader>(4)

    private fun vary(lighten: Float, satMul: Float, alpha: Int, maxV: Float = 1f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(accent, hsv)
        hsv[1] = (hsv[1] * satMul).coerceIn(0.3f, 1f)
        hsv[2] = (hsv[2] + lighten).coerceIn(0f, maxV)
        val c = Color.HSVToColor(hsv)
        return Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))
    }

    private val midPair = Pair(
        Blob(0.98f, 0.92f, 0.52f, seed = 2.1f),
        Blob(0.62f, 1.32f, 0.38f, seed = 4.3f),
        phase = 0.0f, converge = 0.42f,
    )
    private val darkSingle = Blob(1.05f, 0.08f, 0.52f, seed = 2.1f)
    private val lightPair = Pair(
        Blob(-0.06f, 0.85f, 0.55f, seed = 0.6f),
        Blob(0.28f, 1.38f, 0.46f, seed = 5.2f),
        phase = PI.toFloat(), converge = 0.46f,
    )
    private val layers = listOf(
        Layer(vary(0.16f, 0.95f, 0xFF, 0.80f), vary(-0.12f, 1.10f, 0xFF), midPair, null),
        Layer(vary(-0.02f, 1.05f, 0xFF), vary(-0.34f, 1.15f, 0xFF), null, darkSingle),
        Layer(vary(-0.02f, 0.95f, 0xFF), vary(0.32f, 0.62f, 0xFF, 0.78f), lightPair, null),
    )

    private var phase = staticPhase ?: 0f
    private var animator: ValueAnimator? = null
    private var builtBounds = false

    override fun onBoundsChange(bounds: Rect) {
        rebuildShaders(bounds)
    }

    private fun rebuildShaders(b: Rect) {
        basePaint.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            vary(0.08f, 1.0f, 0xFF, 0.80f), vary(-0.24f, 1.10f, 0xFF),
            Shader.TileMode.CLAMP,
        )
        val w = b.width().toFloat().coerceAtLeast(1f)
        val h = b.height().toFloat().coerceAtLeast(1f)
        layers.forEachIndexed { i, layer ->
            val pair = layer.pair
            val single = layer.single
            val blob = pair?.a ?: single
            if (blob != null) {
                val cy = b.top + blob.fy * h
                val r = blob.fr * min(w, h)
                shaders[i] = LinearGradient(
                    0f, cy - r * 1.1f, 0f, cy + r * 0.7f,
                    layer.shaderTop, layer.shaderBottom,
                    Shader.TileMode.CLAMP,
                )
            }
        }
        builtBounds = true
    }

    private fun addBlob(path: Path, cx: Float, cy: Float, r: Float, seed: Float, t: Float) {
        val steps = 72
        var first = true
        for (k in 0..steps) {
            val theta = (k % steps) * (PI * 2 / steps).toFloat()
            val wobble = 1f +
                0.06f * sin(2f * theta + seed + t) +
                0.04f * sin(3f * theta - seed * 1.7f - t * 2f)
            val x = cx + r * wobble * cos(theta)
            val y = cy + r * wobble * sin(theta)
            if (first) { path.moveTo(x, y); first = false } else { path.lineTo(x, y) }
        }
        path.close()
    }

    private fun addBridge(path: Path, ax: Float, ay: Float, ar: Float, bx: Float, by: Float, br: Float) {
        val dx = bx - ax
        val dy = by - ay
        val d = sqrt(dx * dx + dy * dy)
        if (d < 1f) return
        val maxD = (ar + br) * 1.15f
        if (d >= maxD) return
        val t = 1f - d / maxD
        val s = t * t * (3f - 2f * t)
        val ux = dx / d
        val uy = dy / d
        val px = -uy
        val py = ux
        val w1 = ar * 0.62f * s
        val w2 = br * 0.62f * s
        val m1x = ax + px * w1; val m1y = ay + py * w1
        val m2x = bx + px * w2; val m2y = by + py * w2
        val m3x = bx - px * w2; val m3y = by - py * w2
        val m4x = ax - px * w1; val m4y = ay - py * w1
        val cx = (ax + bx) / 2f
        val cy = (ay + by) / 2f
        val nx = px * (w1 + w2) * 0.5f * 0.9f
        val ny = py * (w1 + w2) * 0.5f * 0.9f
        path.moveTo(m1x, m1y)
        path.quadTo(cx + nx, cy + ny, m2x, m2y)
        path.lineTo(m3x, m3y)
        path.quadTo(cx - nx, cy - ny, m4x, m4y)
        path.close()
    }

    private fun centerOf(
        b: Rect, w: Float, h: Float, minDim: Float, t: Float,
        fx: Float, fy: Float, seed: Float, driftX: Float, driftY: Float,
        dirX: Float, dirY: Float, converge: Float, phase: Float, roleSign: Float,
    ): FloatArray {
        val merge = converge * minDim * (0.5f - 0.5f * cos(t + phase)) * roleSign
        return floatArrayOf(
            b.left + fx * w + sin(t + seed) * minDim * driftX + dirX * merge,
            b.top + fy * h + cos(t * 2f + seed) * minDim * driftY + dirY * merge,
        )
    }

    private fun drawPairLayer(canvas: Canvas, b: Rect, layer: Layer, shaderIdx: Int, t: Float) {
        val pair = layer.pair ?: return
        val w = b.width().toFloat().coerceAtLeast(1f)
        val h = b.height().toFloat().coerceAtLeast(1f)
        val minDim = min(w, h)
        val dxh = pair.b.fx * w - pair.a.fx * w
        val dyh = pair.b.fy * h - pair.a.fy * h
        val dh = sqrt(dxh * dxh + dyh * dyh).coerceAtLeast(1f)
        val dirX = dxh / dh
        val dirY = dyh / dh
        val ca = centerOf(b, w, h, minDim, t, pair.a.fx, pair.a.fy, pair.a.seed, pair.a.driftX, pair.a.driftY, dirX, dirY, pair.converge, pair.phase, +0.5f)
        val cb = centerOf(b, w, h, minDim, t, pair.b.fx, pair.b.fy, pair.b.seed, pair.b.driftX, pair.b.driftY, dirX, dirY, pair.converge, pair.phase, -0.5f)
        paint.shader = shaders[shaderIdx]
        layerPath.reset()
        addBlob(layerPath, ca[0], ca[1], pair.a.fr * minDim, pair.a.seed, t)
        addBlob(layerPath, cb[0], cb[1], pair.b.fr * minDim, pair.b.seed, t)
        tempPath.reset()
        addBridge(tempPath, ca[0], ca[1], pair.a.fr * minDim, cb[0], cb[1], pair.b.fr * minDim)
        layerPath.addPath(tempPath)
        canvas.drawPath(layerPath, paint)
    }

    private fun drawSingleLayer(canvas: Canvas, b: Rect, layer: Layer, shaderIdx: Int, t: Float) {
        val single = layer.single ?: return
        val w = b.width().toFloat().coerceAtLeast(1f)
        val h = b.height().toFloat().coerceAtLeast(1f)
        val minDim = min(w, h)
        val c = centerOf(b, w, h, minDim, t, single.fx, single.fy, single.seed, single.driftX, single.driftY, 0f, 0f, 0f, 0f, 0f)
        paint.shader = shaders[shaderIdx]
        layerPath.reset()
        addBlob(layerPath, c[0], c[1], single.fr * minDim, single.seed, t)
        canvas.drawPath(layerPath, paint)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() == 0 || b.height() == 0) return
        if (!builtBounds) rebuildShaders(b)
        if (staticPhase == null && animator == null) {
            animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
                duration = 26000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidateSelf()
                }
                start()
            }
        }
        clipPath.reset()
        clipPath.addRoundRect(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            radiusPx, radiusPx, Path.Direction.CW,
        )
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRoundRect(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            radiusPx, radiusPx, basePaint,
        )
        val t = phase
        layers.forEachIndexed { i, layer ->
            if (layer.pair != null) drawPairLayer(canvas, b, layer, i, t)
            else drawSingleLayer(canvas, b, layer, i, t)
        }
        canvas.restore()
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        if (staticPhase == null) {
            if (visible) animator?.resume() else animator?.pause()
        }
        return super.setVisible(visible, restart)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = -1
    override fun getIntrinsicHeight(): Int = -1
}
