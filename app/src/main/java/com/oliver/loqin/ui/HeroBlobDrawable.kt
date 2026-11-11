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
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Home hero while protection runs: a reactive, Material 3 Expressive blob.
 *
 * A soft body of [POINTS] springs drawn as one smooth closed curve, in an irregular, slowly
 * changing outline (low harmonics plus the Expressive shape lobes). It does not wander at random:
 * it is carried by a slow current, an eddy inside its home area in the right part of the card
 * with a gentle meander, and it stretches along the way it is flowing.
 *
 * Two droplets pinch off its trailing edge, ride the current on their own and are drawn back:
 * the neck thins and snaps as one leaves, and snaps on again when it comes close, pulling it in
 * with a ripple.
 *
 * It reacts to the card: pressing pulls the blob towards the finger and bulges that side. On
 * release it glides to a stop where it is; if that is outside its home area, the current carries
 * it back by the shortest way. [nudge] (Home scrolling) gives it inertia, and it blooms in with a
 * spring when it first appears ([bloomFrom]).
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
    private val gooPath = Path()
    private val circlePath = Path()
    private val oval = RectF()

    // --- Shapes (M3 Expressive shape library, as lobe count + depth) ----------------------------

    private val shapeLobes = intArrayOf(9, 4, 8, 6, 12, 5)
    private val shapeDepth = floatArrayOf(0.055f, 0.11f, 0.07f, 0.09f, 0.04f, 0.10f)
    private var shapeFrom = 0
    private var shapeTo = 0
    private var morph = 1f
    private var morphV = 0f
    private var nextShapeAt = 6f
    private var rotation = 0f
    // Phases of the low harmonics that keep the outline from looking like a circle.
    private var wobble2 = 0.4f
    private var wobble3 = 1.9f

    // --- Physics (positions are fractions of the card; offsets are fractions of the radius) -----

    private val cosT = FloatArray(POINTS) { cos(2 * PI * it / POINTS).toFloat() }
    private val sinT = FloatArray(POINTS) { sin(2 * PI * it / POINTS).toFloat() }
    private val offset = FloatArray(POINTS)
    private val offsetV = FloatArray(POINTS)
    private val px = FloatArray(POINTS)
    private val py = FloatArray(POINTS)
    private val gx = FloatArray(POINTS)
    private val gy = FloatArray(POINTS)
    private val flowOut = FloatArray(2)

    private var cx = 0.74f
    private var cy = 0.46f
    private var vx = 0f
    private var vy = 0f
    // Smoothed direction of travel (in pixels), for the stretch and for where droplets leave.
    private var headX = 0f
    private var headY = 1f

    private var ghostX = cx + 0.06f
    private var ghostY = cy + 0.10f
    private var ghostVx = 0f
    private var ghostVy = 0f

    // Droplets: ABSORBED -> SHEDDING -> FREE -> MERGING -> ABSORBED. Two free droplets can also
    // combine: one JOINS the other and is HELD inside it until that one rejoins the body.
    private val dropState = intArrayOf(ABSORBED, ABSORBED)
    private val dropBase = floatArrayOf(0.22f, 0.15f)
    private val dropSize = dropBase.copyOf()
    private val dropSizeTarget = dropBase.copyOf()
    private val dropAngle = FloatArray(2)
    private val dropExt = FloatArray(2)
    private val dropExtV = FloatArray(2)
    private val dropTarget = IntArray(2) { -1 }
    private val surfaceOut = FloatArray(4)
    private val dropX = floatArrayOf(cx, cx)
    private val dropY = floatArrayOf(cy, cy)
    private val dropVx = FloatArray(2)
    private val dropVy = FloatArray(2)
    private val dropSince = FloatArray(2)
    private val dropShedAt = floatArrayOf(2.0f, 7.5f)

    private var bloom = if (stillFrame) 1f else 0f
    private var bloomV = 0f
    private var bloomX = 0.3f
    private var bloomY = 0.85f

    private var exiting = false
    private var finished = false
    private var warmedUp = false

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

    /**
     * Protection stopped: shrink back into the bloom origin and fade out, revealing whatever is
     * drawn underneath (Home layers the idle card below), then stop for good.
     */
    fun collapse() {
        if (exiting) return
        exiting = true
        if (!animating()) {
            finished = true
            invalidateSelf()
            return
        }
        wake()
    }

    /** Gives the blob a push, e.g. from Home scrolling ([dyPx] of scroll since the last call). */
    fun nudge(dyPx: Float) {
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        vy += (-dyPx / h * 3f).coerceIn(-0.6f, 0.6f)
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
        if (!animating() || !isVisible || finished) return
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
        var e = (abs(vx) + abs(vy)) * 3f + abs(bloomV) + abs(morphV) * 0.2f
        for (v in offsetV) e += abs(v) / POINTS
        for (state in dropState) if (state == SHEDDING || state == MERGING || state == JOINING) e += 0.5f
        return if (pressed) e + 1f else e
    }

    // --- Simulation -----------------------------------------------------------------------------

    private fun random(): Float {
        seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
        return (seed ushr 8) / 16777216f
    }

    private fun release() {
        // Let go: the body jiggles, free droplets scatter and a held one squeezes out.
        for (i in 0 until POINTS) offsetV[i] += 1.4f
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val r = blobRadius(b)
        for (s in dropState.indices) {
            when (dropState[s]) {
                ABSORBED -> dropShedAt[s] = min(dropShedAt[s], time + 0.15f + 0.35f * s)
                SHEDDING -> dropExtV[s] += r * 1.2f
                FREE -> {
                    val ex = (dropX[s] - cx) * b.width()
                    val ey = (dropY[s] - cy) * b.height()
                    val d = sqrt(ex * ex + ey * ey).coerceAtLeast(1f)
                    dropVx[s] += ex / d * r * 1.2f / b.width()
                    dropVy[s] += ey / d * r * 1.2f / b.height()
                }
            }
        }
    }

    /**
     * The current at a point, in card fractions per second: an eddy around ([zx], [zy]) inside an
     * ellipse of radii ([rx], [ry]), a pull back in along the shortest way from outside it, and a
     * slow meander that differs from place to place (so droplets drift apart and back).
     */
    private fun flow(x: Float, y: Float, zx: Float, zy: Float, rx: Float, ry: Float, out: FloatArray) {
        val qx = (x - zx) / rx
        val qy = (y - zy) / ry
        val q = sqrt(qx * qx + qy * qy)
        val spin = 0.22f + 0.07f * sin(time * 0.05f)
        var ux = -qy * spin * rx
        var uy = qx * spin * ry
        if (q > 0.9f) {
            val back = ((q - 0.9f) * 1.6f).coerceAtMost(1.4f)
            ux -= qx / q * back * rx
            uy -= qy / q * back * ry
        }
        ux += 0.018f * sin(time * 0.21f + y * 7f)
        uy += 0.016f * cos(time * 0.17f + x * 6f)
        out[0] = ux
        out[1] = uy
    }

    // An impulse on the side of the body facing [theta] (positive bulges out, negative pulls in).
    private fun kick(theta: Float, amount: Float) {
        val c = cos(theta)
        val s = sin(theta)
        for (i in 0 until POINTS) {
            val d = cosT[i] * c + sinT[i] * s
            if (d > 0f) {
                val d2 = d * d
                offsetV[i] += amount * d2 * d2
            }
        }
    }

    private fun step(dt: Float) {
        if (dt <= 0f) return
        time += dt
        val b = bounds
        val w = b.width().toFloat().coerceAtLeast(1f)
        val h = b.height().toFloat().coerceAtLeast(1f)
        val r = blobRadius(b).coerceAtLeast(1f)

        // Carried by the current; held by a spring to the finger while pressed. After a drag it
        // glides to a stop and only drifts back if it was left outside its home.
        if (pressed) {
            val k = 38f
            val c = 2f * sqrt(k) * 0.6f
            vx += (k * (hotX.coerceIn(0.15f, 0.9f) - cx) - c * vx) * dt
            vy += (k * (hotY.coerceIn(0.15f, 0.85f) - cy) - c * vy) * dt
        } else {
            flow(cx, cy, HOME_X, HOME_Y, HOME_RX, HOME_RY, flowOut)
            vx += (flowOut[0] - vx) * 1.4f * dt
            vy += (flowOut[1] - vy) * 1.4f * dt
        }
        cx = (cx + vx * dt).coerceIn(0.05f, 0.95f)
        cy = (cy + vy * dt).coerceIn(0.05f, 0.95f)

        val speedPx = sqrt(vx * w * vx * w + vy * h * vy * h)
        if (speedPx > 0.5f) {
            val f = min(1f, dt * 1.5f)
            headX += (vx * w / speedPx - headX) * f
            headY += (vy * h / speedPx - headY) * f
            val hl = sqrt(headX * headX + headY * headY).coerceAtLeast(1e-3f)
            headX /= hl
            headY /= hl
        }

        // The trailing shadow blob follows lazily.
        val gk = 3.2f
        val gc = 2f * sqrt(gk) * 0.85f
        ghostVx += (gk * (cx + 0.07f - ghostX) - gc * ghostVx) * dt
        ghostVy += (gk * (cy + 0.11f - ghostY) - gc * ghostVy) * dt
        ghostX += ghostVx * dt
        ghostY += ghostVy * dt

        // Slow shape morph with a little overshoot, like Expressive shape morphs.
        if (time >= nextShapeAt) {
            shapeFrom = shapeTo
            shapeTo = (shapeTo + 1 + (random() * (shapeLobes.size - 1)).toInt()) % shapeLobes.size
            morph = 0f
            morphV = 0f
            nextShapeAt = time + 7f + random() * 4f
        }
        morphV += (9f * (1f - morph) - 2f * sqrt(9f) * 0.55f * morphV) * dt
        morph += morphV * dt
        rotation += dt * 0.08f
        wobble2 += dt * 0.09f
        wobble3 -= dt * 0.06f

        // Bloom in on a spring with overshoot; collapse out without one.
        val bloomTarget = if (exiting) 0f else 1f
        val bloomDamping = if (exiting) 0.95f else 0.42f
        bloomV += (20f * (bloomTarget - bloom) - 2f * sqrt(20f) * bloomDamping * bloomV) * dt
        bloom += bloomV * dt
        if (exiting && bloom < 0.01f) {
            bloom = 0f
            finished = true
            invalidateSelf()
            stop()
            return
        }

        // Body: each point springs to the shape, stretched along the flow and bulging to a touch.
        val speed = speedPx / r
        val stretch = 0.06f + min(speed * 0.3f, 0.14f)
        val hx = (hotX - cx) * w
        val hy = (hotY - cy) * h
        val hd = sqrt(hx * hx + hy * hy).coerceAtLeast(1f)
        for (i in 0 until POINTS) {
            val along = cosT[i] * headX + sinT[i] * headY
            var target = stretch * (along * along - 0.5f)
            if (pressed) {
                val toward = max(0f, (cosT[i] * hx + sinT[i] * hy) / hd)
                target += 0.2f * toward * toward * toward
            }
            target += 0.010f * sin(time * 1.6f + i * 0.63f)
            offsetV[i] += (130f * (target - offset[i]) - 8f * offsetV[i]) * dt
            offset[i] += offsetV[i] * dt
        }

        if (b.width() > 0 && b.height() > 0) stepDroplets(dt, w, h, r)
    }

    // How far a droplet's centre gets past the outline before its neck snaps; a free droplet
    // reconnects at about the same distance.
    private fun neckReach(sr: Float, r: Float): Float = sr * 1.2f + r * 0.25f

    private fun stepDroplets(dt: Float, w: Float, h: Float, r: Float) {
        for (s in dropState.indices) {
            dropSize[s] += (dropSizeTarget[s] - dropSize[s]) * min(1f, dt * 6f)
            val sr = r * dropSize[s]
            when (dropState[s]) {
                HELD -> {
                    val t = dropTarget[s]
                    dropX[s] = dropX[t]
                    dropY[s] = dropY[t]
                    continue
                }
                ABSORBED -> {
                    dropX[s] = cx
                    dropY[s] = cy
                    dropVx[s] = vx
                    dropVy[s] = vy
                    if (!pressed && !exiting && bloom > 0.9f && time >= dropShedAt[s]) {
                        // Pinch off the trailing side, as if the current tugged it loose, leaning
                        // towards the open card so the droplet stays in view.
                        val openX = (0.45f - cx) * w
                        val openY = (0.5f - cy) * h
                        val openL = sqrt(openX * openX + openY * openY).coerceAtLeast(1f)
                        val theta = atan2(-headY + 1.4f * openY / openL, -headX + 1.4f * openX / openL) +
                            (random() - 0.5f) * 1.0f
                        dropAngle[s] = theta
                        dropExt[s] = -sr * 0.5f
                        dropExtV[s] = r * 0.9f
                        dropState[s] = SHEDDING
                        dropSince[s] = time
                        kick(theta, 2.2f)
                        placeOnNormal(s, w, h, r)
                    }
                    continue
                }
                SHEDDING -> {
                    // Pushed straight out along the outline's normal; the neck's pull slows it,
                    // and stretched far enough it snaps.
                    dropExtV[s] -= dropExt[s].coerceAtLeast(0f) * 0.6f * dt
                    dropExt[s] += dropExtV[s] * dt
                    placeOnNormal(s, w, h, r)
                    if (dropExt[s] > neckReach(sr, r)) {
                        dropState[s] = FREE
                        dropSince[s] = time
                        kick(dropAngle[s], -1.6f)
                    } else if (dropExtV[s] < 0f && dropExt[s] < -sr * 0.5f) {
                        absorb(s, dropAngle[s])
                    }
                    continue
                }
            }

            val ex = (dropX[s] - cx) * w
            val ey = (dropY[s] - cy) * h
            val d = sqrt(ex * ex + ey * ey).coerceAtLeast(1f)
            val ux = ex / d
            val uy = ey / d
            val theta = atan2(ey, ex)
            val surface = bodyRadiusAt(theta, r)
            val since = time - dropSince[s]
            var ax = 0f
            var ay = 0f
            var drag = 2f
            when (dropState[s]) {
                FREE -> {
                    // Another droplet out at the same time? Then they seek each other first and
                    // the body waits (for a while) before drawing either back.
                    var partner = false
                    for (o in dropState.indices) {
                        if (o == s) continue
                        if (dropState[o] == FREE || (dropState[o] == JOINING && dropTarget[o] == s)) partner = true
                        if (dropState[o] != FREE) continue
                        val ox = (dropX[o] - dropX[s]) * w
                        val oy = (dropY[o] - dropY[s]) * h
                        val od = sqrt(ox * ox + oy * oy).coerceAtLeast(1f)
                        val osr = r * dropSize[o]
                        ax += ox / od * r * 0.6f
                        ay += oy / od * r * 0.6f
                        // Close enough: the smaller joins the larger.
                        val smaller = dropSize[s] < dropSize[o] || (dropSize[s] == dropSize[o] && s > o)
                        if (smaller && since > 0.6f && time - dropSince[o] > 0.6f && od < sr + osr + r * 0.15f) {
                            dropState[s] = JOINING
                            dropTarget[s] = o
                            dropSince[s] = time
                        }
                    }
                    val waiting = partner && since < 9f
                    if (!waiting) {
                        // Floats free for a while, then is drawn back more and more.
                        val pull = min(r * (0.03f + 0.05f * (since - 4f).coerceAtLeast(0f)), r * 0.6f)
                        ax -= ux * pull
                        ay -= uy * pull
                    }
                    // Reconnects at about the distance it snapped off, then is drawn in. Touching
                    // the body always joins, so a droplet never slides over it.
                    val touching = d - surface < sr * 1.6f
                    val reconnect = !waiting && since > 4.5f && d - surface < neckReach(sr, r)
                    if (dropState[s] == FREE && (touching || reconnect)) {
                        dropState[s] = MERGING
                        dropSince[s] = time
                        kick(theta, 1.2f)
                    }
                }
                MERGING -> {
                    // Drawn in along the neck, riding along with the body.
                    drag = 0f
                    ax -= ex * 6f + (dropVx[s] - vx) * w * 4f
                    ay -= ey * 6f + (dropVy[s] - vy) * h * 4f
                    if (d < surface * 0.55f) {
                        absorb(s, theta)
                        continue
                    }
                }
                JOINING -> {
                    val t = dropTarget[s]
                    if (t < 0 || dropState[t] != FREE) {
                        dropState[s] = FREE
                        dropSince[s] = time
                    } else {
                        drag = 0f
                        val tx = (dropX[s] - dropX[t]) * w
                        val ty = (dropY[s] - dropY[t]) * h
                        ax -= tx * 10f + (dropVx[s] - dropVx[t]) * w * 5f
                        ay -= ty * 10f + (dropVy[s] - dropVy[t]) * h * 5f
                        if (sqrt(tx * tx + ty * ty) < r * dropSize[t] * 0.5f) {
                            // Combined: one bigger droplet with the same amount of goo.
                            dropState[s] = HELD
                            dropSizeTarget[t] = sqrt(dropSizeTarget[t] * dropSizeTarget[t] + dropSize[s] * dropSize[s])
                            dropVx[t] = (dropVx[t] + dropVx[s]) * 0.5f
                            dropVy[t] = (dropVy[t] + dropVy[s]) * 0.5f
                            continue
                        }
                    }
                }
            }
            if (drag > 0f) {
                flow(dropX[s], dropY[s], DROP_X, DROP_Y, DROP_RX, DROP_RY, flowOut)
                dropVx[s] += (flowOut[0] - dropVx[s]) * drag * dt
                dropVy[s] += (flowOut[1] - dropVy[s]) * drag * dt
            }
            dropVx[s] += ax / w * dt
            dropVy[s] += ay / h * dt
            dropX[s] = (dropX[s] + dropVx[s] * dt).coerceIn(0.02f, 0.98f)
            dropY[s] = (dropY[s] + dropVy[s] * dt).coerceIn(0.02f, 0.98f)
        }
    }

    // Puts a shedding droplet on the outline's normal at its angle, [dropExt] beyond the surface.
    private fun placeOnNormal(s: Int, w: Float, h: Float, r: Float) {
        surfaceAt(dropAngle[s], r, surfaceOut)
        val ox = surfaceOut[0] + surfaceOut[2] * dropExt[s]
        val oy = surfaceOut[1] + surfaceOut[3] * dropExt[s]
        dropX[s] = cx + ox / w
        dropY[s] = cy + oy / h
        dropVx[s] = vx + surfaceOut[2] * dropExtV[s] / w
        dropVy[s] = vy + surfaceOut[3] * dropExtV[s] / h
    }

    private fun absorb(s: Int, theta: Float) {
        dropState[s] = ABSORBED
        dropShedAt[s] = time + 5f + random() * 4f
        val grown = dropSize[s] / dropBase[s]
        dropSize[s] = dropBase[s]
        dropSizeTarget[s] = dropBase[s]
        kick(theta, 1.0f * grown)
        for (i in 0 until POINTS) offsetV[i] += 0.4f * grown
        // Droplets it had swallowed come back into the body with it.
        for (o in dropState.indices) {
            if (dropState[o] == HELD && dropTarget[o] == s) {
                dropState[o] = ABSORBED
                dropTarget[o] = -1
                dropShedAt[o] = time + 2f + random() * 3f
            }
        }
    }

    private fun blobRadius(b: Rect): Float = min(b.width(), b.height()) * 0.34f

    private fun restRadius(theta: Float): Float {
        val a = shapeDepth[shapeFrom] * cos(shapeLobes[shapeFrom] * (theta - rotation))
        val z = shapeDepth[shapeTo] * cos(shapeLobes[shapeTo] * (theta - rotation))
        val lobes = (a + (z - a) * morph) * 0.75f
        val irregular = 0.11f * cos(2f * (theta - wobble2)) + 0.06f * cos(3f * (theta - wobble3))
        return 1f + lobes + irregular
    }

    // --- Drawing --------------------------------------------------------------------------------

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() == 0 || b.height() == 0) return
        if (blobShader == null) onBoundsChange(b)
        if (stillFrame && !warmedUp) {
            // The widget's still frame: let it settle into a natural pose first.
            warmedUp = true
            repeat(270) { step(1f / 30f) }
        }
        if (finished && exiting) return
        if (animating() && isVisible && animator?.isStarted != true) wake()
        // While collapsing, the whole art fades with the blob.
        val alpha = if (exiting) (bloom.coerceIn(0f, 1f) * 255).toInt() else 255
        basePaint.alpha = alpha
        ghostPaint.alpha = alpha
        blobPaint.alpha = alpha

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
            val w = b.width()
            val h = b.height()
            val bx = b.left + (bloomX + (cx - bloomX) * grow) * w
            val by = b.top + (bloomY + (cy - bloomY) * grow) * h
            val ghx = b.left + (bloomX + (ghostX - bloomX) * grow) * w
            val ghy = b.top + (bloomY + (ghostY - bloomY) * grow) * h

            for (i in 0 until POINTS) {
                val theta = (2 * PI * i / POINTS).toFloat()
                val gr = r * 1.22f * (
                    1f + 0.05f * cos(7 * (theta + rotation * 0.7f)) +
                        0.08f * cos(2f * (theta - wobble2 - 0.8f))
                    )
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
            // The body, attached droplets and their necks are merged into one shape and drawn
            // once, so the joins have no anti-aliased seams. Free droplets are drawn on their own.
            gooPath.set(blobPath)
            for (s in dropState.indices) {
                if (dropState[s] == ABSORBED || dropState[s] == HELD) continue
                val sr = r * dropSize[s]
                val sx = b.left + (bloomX + (dropX[s] - bloomX) * grow) * w
                val sy = b.top + (bloomY + (dropY[s] - bloomY) * grow) * h
                when (dropState[s]) {
                    SHEDDING, MERGING -> {
                        // The neck grows out of the outline along its normal: from a circle that
                        // touches the outline there from inside, sized to the local curve.
                        val angle = if (dropState[s] == SHEDDING) dropAngle[s] else atan2(sy - by, sx - bx)
                        surfaceAt(angle, r, surfaceOut)
                        var rho = bodyRadiusAt(angle, r)
                        for (k in 1..3) {
                            rho = min(rho, bodyRadiusAt(angle + 0.25f * k, r))
                            rho = min(rho, bodyRadiusAt(angle - 0.25f * k, r))
                        }
                        val anchorX = bx + surfaceOut[0]
                        val anchorY = by + surfaceOut[1]
                        val vcx = anchorX - surfaceOut[2] * rho
                        val vcy = anchorY - surfaceOut[3] * rho
                        val reach = neckReach(sr, r)
                        val out = if (dropState[s] == SHEDDING) {
                            dropExt[s]
                        } else {
                            sqrt((sx - anchorX) * (sx - anchorX) + (sy - anchorY) * (sy - anchorY))
                        }
                        val f = ((out + sr * 0.5f) / (reach + sr * 0.5f)).coerceIn(0f, 1f)
                        bridgePath.reset()
                        addGooBridge(
                            bridgePath, vcx, vcy, rho * 0.97f, sx, sy, sr, 0.6f - 0.3f * f,
                            body = true, bodyX = bx, bodyY = by, bodyR = r,
                        )
                        merge(canvas, bridgePath)
                        circlePath.reset()
                        circlePath.addCircle(sx, sy, sr, Path.Direction.CW)
                        merge(canvas, circlePath)
                    }
                    JOINING -> {
                        val t = dropTarget[s]
                        val tr = r * dropSize[t]
                        val tx = b.left + (bloomX + (dropX[t] - bloomX) * grow) * w
                        val ty = b.top + (bloomY + (dropY[t] - bloomY) * grow) * h
                        val dd = sqrt((sx - tx) * (sx - tx) + (sy - ty) * (sy - ty))
                        val near = max(sr, tr) * 0.6f
                        val far = sr + tr + r * 0.15f
                        val f = ((dd - near) / (far - near)).coerceIn(0f, 1f)
                        bridgePath.reset()
                        addGooBridge(bridgePath, tx, ty, tr, sx, sy, sr, 0.6f - 0.3f * f)
                        merge(canvas, bridgePath)
                        circlePath.reset()
                        circlePath.addCircle(sx, sy, sr, Path.Direction.CW)
                        merge(canvas, circlePath)
                        circlePath.reset()
                        circlePath.addCircle(tx, ty, tr, Path.Direction.CW)
                        merge(canvas, circlePath)
                    }
                    else -> {
                        // Free: stretched a little along the way it is moving (kept round while
                        // another droplet is joining it, so the neck meets it cleanly).
                        // (A droplet another one is joining is part of the merged shape instead.)
                        if (dropState.indices.any { dropState[it] == JOINING && dropTarget[it] == s }) continue
                        val dvx = dropVx[s] * w
                        val dvy = dropVy[s] * h
                        val dv = sqrt(dvx * dvx + dvy * dvy)
                        val st = 1f + min(dv / (r * 4f), 0.06f)
                        canvas.save()
                        canvas.translate(sx, sy)
                        if (dv > 1f) canvas.rotate(Math.toDegrees(atan2(dvy, dvx).toDouble()).toFloat())
                        oval.set(-sr * st, -sr / st, sr * st, sr / st)
                        canvas.drawOval(oval, blobPaint)
                        canvas.restore()
                    }
                }
            }
            canvas.drawPath(gooPath, blobPaint)
        }
        canvas.restore()
    }

    // Adds [part] to the merged shape; if the union fails, draws it on its own instead.
    private fun merge(canvas: Canvas, part: Path) {
        if (!gooPath.op(part, Path.Op.UNION)) canvas.drawPath(part, blobPaint)
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

    // Outline radius of the body (centre-relative, in px) in direction [theta].
    private fun bodyRadiusAt(theta: Float, r: Float): Float {
        var f = theta / (2f * PI.toFloat()) * POINTS
        f -= kotlin.math.floor(f / POINTS) * POINTS
        val i = f.toInt() % POINTS
        val j = (i + 1) % POINTS
        val t = f - kotlin.math.floor(f)
        return r * (restRadius(theta) + offset[i] + (offset[j] - offset[i]) * t)
    }

    /**
     * The outline point in direction [theta] (offset from the centre, px) and its outward
     * normal: out = [x, y, nx, ny].
     */
    private fun surfaceAt(theta: Float, r: Float, out: FloatArray) {
        val rho = bodyRadiusAt(theta, r)
        val e = 0.06f
        val dRho = (bodyRadiusAt(theta + e, r) - bodyRadiusAt(theta - e, r)) / (2f * e)
        val c = cos(theta)
        val sn = sin(theta)
        val tx = dRho * c - rho * sn
        val ty = dRho * sn + rho * c
        var nx = ty
        var ny = -tx
        val nl = sqrt(nx * nx + ny * ny).coerceAtLeast(1e-3f)
        nx /= nl
        ny /= nl
        if (nx * c + ny * sn < 0f) {
            nx = -nx
            ny = -ny
        }
        out[0] = rho * c
        out[1] = rho * sn
        out[2] = nx
        out[3] = ny
    }

    /**
     * A metaball neck between the body (centre [ax],[ay], radius [ar]) and a droplet: it leaves
     * each outline tangentially and curves inward, like liquid pulled into a thread. [v] is how
     * far round each outline it starts (0.6 = a thick join, towards 0 = a thin thread).
     */
    private fun addGooBridge(
        path: Path,
        ax: Float,
        ay: Float,
        ar: Float,
        bx: Float,
        by: Float,
        br: Float,
        v: Float,
        body: Boolean = false,
        bodyX: Float = 0f,
        bodyY: Float = 0f,
        bodyR: Float = 0f,
    ) {
        val dx = bx - ax
        val dy = by - ay
        val d = sqrt(dx * dx + dy * dy)
        if (d <= abs(ar - br) || d < 1f) return
        val u1: Float
        val u2: Float
        if (d < ar + br) {
            u1 = kotlin.math.acos(((ar * ar + d * d - br * br) / (2f * ar * d)).coerceIn(-1f, 1f))
            u2 = kotlin.math.acos(((br * br + d * d - ar * ar) / (2f * br * d)).coerceIn(-1f, 1f))
        } else {
            u1 = 0f
            u2 = 0f
        }
        val centre = atan2(dy, dx)
        val maxSpread = kotlin.math.acos(((ar - br) / d).coerceIn(-1f, 1f))
        val pi = PI.toFloat()
        val a1 = centre + u1 + (maxSpread - u1) * v
        val a2 = centre - u1 - (maxSpread - u1) * v
        val a3 = centre + pi - u2 - (pi - u2 - maxSpread) * v
        val a4 = centre - pi + u2 + (pi - u2 - maxSpread) * v
        var p1x = ax + cos(a1) * ar
        var p1y = ay + sin(a1) * ar
        var p2x = ax + cos(a2) * ar
        var p2y = ay + sin(a2) * ar
        if (body) {
            // Start exactly on the body's real outline (just inside it), not on the helper circle.
            val t1 = atan2(p1y - bodyY, p1x - bodyX)
            val t2 = atan2(p2y - bodyY, p2x - bodyX)
            val e1 = bodyRadiusAt(t1, bodyR) * 0.995f
            val e2 = bodyRadiusAt(t2, bodyR) * 0.995f
            p1x = bodyX + cos(t1) * e1
            p1y = bodyY + sin(t1) * e1
            p2x = bodyX + cos(t2) * e2
            p2y = bodyY + sin(t2) * e2
        }
        val p3x = bx + cos(a3) * br
        val p3y = by + sin(a3) * br
        val p4x = bx + cos(a4) * br
        val p4y = by + sin(a4) * br
        // Handle length: shorter as the neck stretches, so it narrows in the middle. Shortened
        // further if the two sides would cross (a bow-tie instead of a thread).
        val p13 = sqrt((p3x - p1x) * (p3x - p1x) + (p3y - p1y) * (p3y - p1y))
        var handle = min(v * 2.4f, p13 / (ar + br)) * min(1f, d * 2f / (ar + br))
        val nx = -dy / d
        val ny = dx / d
        val side = if ((p1x - ax) * nx + (p1y - ay) * ny >= 0f) 1f else -1f
        var h1 = 0f
        var h2 = 0f
        var open = false
        for (attempt in 0 until 5) {
            h1 = ar * handle
            h2 = br * handle
            val c1x = p1x + cos(a1 - pi / 2) * h1
            val c1y = p1y + sin(a1 - pi / 2) * h1
            val c3x = p3x + cos(a3 + pi / 2) * h2
            val c3y = p3y + sin(a3 + pi / 2) * h2
            val midX = (p1x + 3f * c1x + 3f * c3x + p3x) / 8f
            val midY = (p1y + 3f * c1y + 3f * c3y + p3y) / 8f
            if (((midX - ax) * nx + (midY - ay) * ny) * side >= br * 0.08f) {
                open = true
                break
            }
            handle *= 0.6f
        }
        if (!open) return
        path.moveTo(p1x, p1y)
        path.cubicTo(
            p1x + cos(a1 - pi / 2) * h1, p1y + sin(a1 - pi / 2) * h1,
            p3x + cos(a3 + pi / 2) * h2, p3y + sin(a3 + pi / 2) * h2,
            p3x, p3y,
        )
        // Close through the centres: those segments always stay inside the droplet and the body
        // (the outline is star-shaped around its centre), so no straight edge shows.
        path.lineTo(bx, by)
        path.lineTo(p4x, p4y)
        path.cubicTo(
            p4x + cos(a4 - pi / 2) * h2, p4y + sin(a4 - pi / 2) * h2,
            p2x + cos(a2 + pi / 2) * h1, p2y + sin(a2 + pi / 2) * h1,
            p2x, p2y,
        )
        if (body) path.lineTo(bodyX, bodyY) else path.lineTo(ax, ay)
        path.close()
    }

    private companion object {
        const val POINTS = 40
        const val CALM_ENERGY = 0.35f

        const val ABSORBED = 0
        const val SHEDDING = 1
        const val FREE = 2
        const val MERGING = 3
        const val JOINING = 4
        const val HELD = 5

        // The blob's home: an ellipse in the right part of the card, away from the text. The
        // droplets ride the current in a wider one.
        const val HOME_X = 0.74f
        const val HOME_Y = 0.48f
        const val HOME_RX = 0.17f
        const val HOME_RY = 0.26f
        const val DROP_X = 0.62f
        const val DROP_Y = 0.50f
        const val DROP_RX = 0.24f
        const val DROP_RY = 0.36f
    }
}
