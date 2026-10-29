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

package com.oliver.loqin.ui

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Home hero while protection runs: a reactive, Material 3 Expressive blob.
 *
 * A soft body of [POINTS] springs drawn as one smooth closed curve. It wanders around the card on
 * underdamped springs (so it overshoots and settles), squashes and stretches along its motion,
 * and morphs between Expressive shapes (cookie, clover, sunny, flower, burst). Two droplets orbit
 * it and stick to it gooily when close; a darker trailing blob gives depth.
 *
 * It reacts to the card: pressing pulls the blob towards the finger and bulges that side,
 * releasing jiggles it and scatters the droplets, [nudge] (Home scrolling) gives it inertia, and
 * it blooms in with a spring when it first appears ([bloomFrom]).
 *
 * Runs only while visible, at 60 fps while it moves and 30 fps when calm, and draws a still
 * frame when animations are turned off. No allocations per frame.
 */
class HeroBlobDrawable(
    private val accent: Int,
    private val radiusPx: Float,
    private val stillFrame: Boolean = false,
) : Drawable() {

    // --- Look -----------------------------------------------------------------------------------

    private fun tone(lighten: Float, satMul: Float, maxV: Float = 1f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(accent, hsv)
        hsv[1] = (hsv[1] * satMul).coerceIn(0.25f, 1f)
        hsv[2] = (hsv[2] + lighten).coerceIn(0f, maxV)
        return Color.HSVToColor(hsv)
    }

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tone(-0.20f, 1.12f) }
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Close to the card's own lightness, so the white text on top keeps its contrast; shape,
    // motion and the darker shadow blob carry the effect.
    private val shineColor = tone(0.08f, 0.88f, 0.86f)
    private val blobColor = tone(-0.05f, 1.05f, 0.82f)
    private val shaderMatrix = Matrix()
    private var blobShader: Shader? = null

    private val clipPath = Path()
    private val ghostPath = Path()
    private val blobPath = Path()
    private val bridgePath = Path()

    // --- Shapes (M3 Expressive shape library, as lobe count + depth) ----------------------------

    private val shapeLobes = intArrayOf(9, 4, 8, 6, 12, 5)
    private val shapeDepth = floatArrayOf(0.055f, 0.11f, 0.07f, 0.09f, 0.04f, 0.10f)
    private var shapeFrom = 0
    private var shapeTo = 0
    private var morph = 1f
    private var morphV = 0f
    private var nextShapeAt = 4f
    private var rotation = 0f

    // --- Physics (positions are fractions of the card; offsets are fractions of the radius) -----

    private val cosT = FloatArray(POINTS) { cos(2 * PI * it / POINTS).toFloat() }
    private val sinT = FloatArray(POINTS) { sin(2 * PI * it / POINTS).toFloat() }
    private val offset = FloatArray(POINTS)
    private val offsetV = FloatArray(POINTS)
    private val px = FloatArray(POINTS)
    private val py = FloatArray(POINTS)
    private val gx = FloatArray(POINTS)
    private val gy = FloatArray(POINTS)

    private var cx = 0.74f
    private var cy = 0.46f
    private var vx = 0f
    private var vy = 0f
    private var tx = cx
    private var ty = cy
    private var nextTargetAt = 2.5f

    private var ghostX = cx + 0.06f
    private var ghostY = cy + 0.10f
    private var ghostVx = 0f
    private var ghostVy = 0f

    private val satAngle = floatArrayOf(0.6f, 3.6f)
    private val satSpeed = floatArrayOf(0.55f, -0.38f)
    private val satSize = floatArrayOf(0.20f, 0.13f)
    private val satDist = floatArrayOf(1.30f, 1.45f)
    private val satDistV = floatArrayOf(0f, 0f)

    private var bloom = if (stillFrame) 1f else 0f
    private var bloomV = 0f
    private var bloomX = 0.3f
    private var bloomY = 0.85f

    private var pressed = false
    private var hotX = 0.5f
    private var hotY = 0.5f

    private var time = 0f
    private var seed = 0x2545F491
    private var lastFrameAt = 0L
    private var animator: TimeAnimator? = null

    /** Where the bloom starts, as fractions of the card (the Enable button, usually). */
    fun bloomFrom(fx: Float, fy: Float) {
        bloomX = fx.coerceIn(0f, 1f)
        bloomY = fy.coerceIn(0f, 1f)
    }

    /**
     * A touch anywhere on the card, in the card's coordinates. The card's own buttons and rows
     * take most touches, so Home forwards them here as well (see MainActivity.dispatchTouchEvent).
     */
    fun touch(x: Float, y: Float, down: Boolean) {
        setHotspot(bounds.left + x, bounds.top + y)
        if (down != pressed) {
            pressed = down
            if (!down) release()
        }
        wake()
    }

    /** Gives the blob a push, e.g. from Home scrolling ([dyPx] of scroll since the last call). */
    fun nudge(dyPx: Float) {
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        vy += (-dyPx / h * 6f).coerceIn(-1.2f, 1.2f)
        for (i in 0 until POINTS) offsetV[i] += (dyPx / h) * 2f * sinT[i]
        invalidateSelf()
    }

    // --- Drawable plumbing ----------------------------------------------------------------------

    override fun onBoundsChange(bounds: Rect) {
        basePaint.shader = LinearGradient(
            bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(),
            tone(0.10f, 1.0f, 0.82f), tone(-0.24f, 1.10f),
            Shader.TileMode.CLAMP,
        )
        val r = blobRadius(bounds)
        blobShader = LinearGradient(0f, -r, 0f, r, shineColor, blobColor, Shader.TileMode.CLAMP)
        blobPaint.shader = blobShader
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val nowPressed = state.contains(android.R.attr.state_pressed)
        if (nowPressed == pressed) return false
        pressed = nowPressed
        if (!pressed) release()
        wake()
        return true
    }

    override fun setHotspot(x: Float, y: Float) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        hotX = ((x - b.left) / b.width()).coerceIn(0f, 1f)
        hotY = ((y - b.top) / b.height()).coerceIn(0f, 1f)
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible) wake() else stop()
        return changed
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun animating(): Boolean = !stillFrame && ValueAnimator.areAnimatorsEnabled()

    private fun wake() {
        if (!animating() || !isVisible) return
        if (animator == null) {
            animator = TimeAnimator().apply {
                setTimeListener { _, _, deltaMs ->
                    // Replaced as a background (accent or state change): stop for good.
                    if (callback == null) {
                        stop()
                        return@setTimeListener
                    }
                    step((deltaMs / 1000f).coerceIn(0f, 0.05f))
                    // 60 fps while it moves or is touched, 30 fps once it has calmed down.
                    val now = android.os.SystemClock.uptimeMillis()
                    val interval = if (energy() > CALM_ENERGY) 0L else 33L
                    if (now - lastFrameAt >= interval) {
                        lastFrameAt = now
                        invalidateSelf()
                    }
                }
            }
        }
        if (animator?.isStarted != true) animator?.start()
    }

    private fun stop() {
        animator?.cancel()
    }

    private fun energy(): Float {
        var e = abs(vx) + abs(vy) + abs(bloomV) + abs(morphV) * 0.2f
        for (v in offsetV) e += abs(v) / POINTS
        return if (pressed) e + 1f else e
    }

    // --- Simulation -----------------------------------------------------------------------------

    private fun random(): Float {
        seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
        return (seed ushr 8) / 16777216f
    }

    private fun release() {
        // Let go: the body springs out and the droplets scatter.
        for (i in 0 until POINTS) offsetV[i] += 1.8f
        for (s in satDistV.indices) satDistV[s] += 2.6f
        nextTargetAt = time + 0.6f
    }

    private fun step(dt: Float) {
        if (dt <= 0f) return
        time += dt

        // Wander: a new spot every few seconds, in the right part of the card away from the text.
        if (pressed) {
            tx = hotX.coerceIn(0.15f, 0.9f)
            ty = hotY.coerceIn(0.15f, 0.85f)
        } else if (time >= nextTargetAt) {
            tx = 0.60f + random() * 0.30f
            ty = 0.22f + random() * 0.52f
            nextTargetAt = time + 2.4f + random() * 2.2f
        }
        val k = if (pressed) 38f else 7f
        val c = 2f * sqrt(k) * if (pressed) 0.6f else 0.42f
        vx += (k * (tx - cx) - c * vx) * dt
        vy += (k * (ty - cy) - c * vy) * dt
        cx += vx * dt
        cy += vy * dt

        // The trailing shadow blob follows lazily.
        val gk = 3.2f
        val gc = 2f * sqrt(gk) * 0.85f
        ghostVx += (gk * (cx + 0.07f - ghostX) - gc * ghostVx) * dt
        ghostVy += (gk * (cy + 0.11f - ghostY) - gc * ghostVy) * dt
        ghostX += ghostVx * dt
        ghostY += ghostVy * dt

        // Shape morph on a bouncy spring (overshoots a little, like Expressive shape morphs).
        if (time >= nextShapeAt) {
            shapeFrom = shapeTo
            shapeTo = (shapeTo + 1 + (random() * (shapeLobes.size - 1)).toInt()) % shapeLobes.size
            morph = 0f
            morphV = 0f
            nextShapeAt = time + 4.5f + random() * 2.5f
        }
        morphV += (26f * (1f - morph) - 2f * sqrt(26f) * 0.45f * morphV) * dt
        morph += morphV * dt
        rotation += dt * 0.22f

        // Bloom in on a spring with overshoot.
        bloomV += (20f * (1f - bloom) - 2f * sqrt(20f) * 0.42f * bloomV) * dt
        bloom += bloomV * dt

        // Body: each point springs to the shape, stretched along the motion and bulging to a touch.
        val b = bounds
        val r = blobRadius(b).coerceAtLeast(1f)
        val speedX = vx * b.width() / r
        val speedY = vy * b.height() / r
        val speed = sqrt(speedX * speedX + speedY * speedY)
        val stretch = min(speed * 0.06f, 0.22f)
        val hx = (hotX - cx) * b.width()
        val hy = (hotY - cy) * b.height()
        val hd = sqrt(hx * hx + hy * hy).coerceAtLeast(1f)
        for (i in 0 until POINTS) {
            var target = 0f
            if (speed > 0.01f) {
                val along = (cosT[i] * speedX + sinT[i] * speedY) / speed
                target += stretch * (along * along - 0.5f)
            }
            if (pressed) {
                val toward = max(0f, (cosT[i] * hx + sinT[i] * hy) / hd)
                target += 0.2f * toward * toward * toward
            }
            target += 0.022f * sin(time * 2.1f + i * 0.83f) + 0.014f * sin(time * 3.7f - i * 1.31f)
            offsetV[i] += (130f * (target - offset[i]) - 8f * offsetV[i]) * dt
            offset[i] += offsetV[i] * dt
        }

        // Droplets orbit, drift in and out, and fly out when the blob is let go.
        for (s in satAngle.indices) {
            satAngle[s] += satSpeed[s] * dt
            val rest = (if (pressed) 1.75f else 1.28f + 0.16f * s) + 0.12f * sin(time * 0.9f + s * 2f)
            satDistV[s] += (14f * (rest - satDist[s]) - 2f * sqrt(14f) * 0.35f * satDistV[s]) * dt
            satDist[s] += satDistV[s] * dt
        }
    }

    private fun blobRadius(b: Rect): Float = min(b.width(), b.height()) * 0.34f

    private fun restRadius(theta: Float): Float {
        val a = shapeDepth[shapeFrom] * cos(shapeLobes[shapeFrom] * (theta - rotation))
        val z = shapeDepth[shapeTo] * cos(shapeLobes[shapeTo] * (theta - rotation))
        return 1f + a + (z - a) * morph
    }

    // --- Drawing --------------------------------------------------------------------------------

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() == 0 || b.height() == 0) return
        if (blobShader == null) onBoundsChange(b)
        if (animating() && isVisible && animator?.isStarted != true) wake()

        clipPath.reset()
        clipPath.addRoundRect(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            radiusPx, radiusPx, Path.Direction.CW,
        )
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(b, basePaint)

        val r = blobRadius(b) * bloom.coerceAtLeast(0f)
        if (r > 1f) {
            // While blooming the blob grows out of the bloom origin.
            val grow = bloom.coerceIn(0f, 1f)
            val bx = b.left + (bloomX + (cx - bloomX) * grow) * b.width()
            val by = b.top + (bloomY + (cy - bloomY) * grow) * b.height()
            val ghx = b.left + (bloomX + (ghostX - bloomX) * grow) * b.width()
            val ghy = b.top + (bloomY + (ghostY - bloomY) * grow) * b.height()

            for (i in 0 until POINTS) {
                val theta = (2 * PI * i / POINTS).toFloat()
                val gr = r * 1.22f * (1f + 0.05f * cos(7 * (theta + rotation * 0.7f)))
                gx[i] = ghx + cosT[i] * gr
                gy[i] = ghy + sinT[i] * gr
                val pr = r * (restRadius(theta) + offset[i])
                px[i] = bx + cosT[i] * pr
                py[i] = by + sinT[i] * pr
            }
            smoothClosed(ghostPath, gx, gy)
            canvas.drawPath(ghostPath, ghostPaint)

            shaderMatrix.setTranslate(0f, by)
            blobShader?.setLocalMatrix(shaderMatrix)
            smoothClosed(blobPath, px, py)
            canvas.drawPath(blobPath, blobPaint)
            // Droplets and their necks are drawn separately: in one path, opposite winding
            // directions would cut holes where they overlap the body.
            for (s in satAngle.indices) {
                val sr = r * satSize[s]
                val sx = bx + cos(satAngle[s]) * r * satDist[s]
                val sy = by + sin(satAngle[s]) * r * satDist[s]
                canvas.drawCircle(sx, sy, sr, blobPaint)
                bridgePath.reset()
                addGooBridge(bridgePath, bx, by, r, sx, sy, sr)
                canvas.drawPath(bridgePath, blobPaint)
            }
        }
        canvas.restore()
    }

    // Catmull-Rom through the points, as cubic Béziers: one smooth closed outline.
    private fun smoothClosed(path: Path, xs: FloatArray, ys: FloatArray) {
        path.reset()
        val n = xs.size
        path.moveTo(xs[0], ys[0])
        for (i in 0 until n) {
            val i0 = (i - 1 + n) % n
            val i2 = (i + 1) % n
            val i3 = (i + 2) % n
            path.cubicTo(
                xs[i] + (xs[i2] - xs[i0]) / 6f, ys[i] + (ys[i2] - ys[i0]) / 6f,
                xs[i2] - (xs[i3] - xs[i]) / 6f, ys[i2] - (ys[i3] - ys[i]) / 6f,
                xs[i2], ys[i2],
            )
        }
        path.close()
    }

    // A neck between the blob and a droplet that thins out as they part (a gooey join).
    private fun addGooBridge(path: Path, ax: Float, ay: Float, ar: Float, bx: Float, by: Float, br: Float) {
        val dx = bx - ax
        val dy = by - ay
        val d = sqrt(dx * dx + dy * dy)
        val reach = (ar + br) * 1.12f
        if (d < 1f || d >= reach) return
        val t = 1f - (d - ar * 0.6f).coerceAtLeast(0f) / (reach - ar * 0.6f)
        val s = (t * t * (3f - 2f * t)).coerceIn(0f, 1f)
        if (s <= 0.18f) return  // snap off instead of thinning to a hairline
        val ux = dx / d
        val uy = dy / d
        val nx = -uy
        val ny = ux
        val w1 = ar * 0.42f * s
        val w2 = br * 0.80f * s
        val mx = (ax + bx) / 2f
        val my = (ay + by) / 2f
        val pinch = (w1 + w2) * 0.5f * (0.35f + 0.5f * s)
        path.moveTo(ax + nx * w1, ay + ny * w1)
        path.quadTo(mx + nx * pinch, my + ny * pinch, bx + nx * w2, by + ny * w2)
        path.lineTo(bx - nx * w2, by - ny * w2)
        path.quadTo(mx - nx * pinch, my - ny * pinch, ax - nx * w1, ay - ny * w1)
        path.close()
    }

    private companion object {
        const val POINTS = 40
        const val CALM_ENERGY = 0.35f
    }
}
