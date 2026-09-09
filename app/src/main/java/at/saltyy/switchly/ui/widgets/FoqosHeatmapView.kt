/*
 * Switchly
 * Copyright (C) 2025-2026 Saltyy
 * Copyright (C) 2026 Switchly Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the GNU General Public
 * License, or (at your option) any later version.
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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import at.saltyy.switchly.theme.AccentColor
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Foqos-style 4-week calendar heatmap (FourWeekHeatmapView).
 *
 * A REAL calendar grid: columns are weekdays (Monday-first), rows are weeks —
 * the current week plus the three before it. Day-of-month numbers are drawn
 * ABOVE every cell (like a calendar), the last row shows the rest of the
 * current week as empty future cells, and each filled cell carries its day
 * number inside. Fill intensity encodes blocked time for that day.
 */
class FoqosHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Per-day values in ms, oldest -> today. Length should be DAYS. */
    private var dayValuesMs: LongArray = LongArray(DAYS)

    /** Index of today inside dayValuesMs (DAYS - 1). */
    private var todayIndex: Int = DAYS - 1

    /** Selected cell in GRID coordinates (0..DAYS-1), -1 when cleared. */
    private var selectedCell: Int = -1

    /** Delivered via [onDaySelected]; index into dayValuesMs, -1 when cleared. */
    var onDaySelected: ((Int) -> Unit)? = null

    private val accent: Int by lazy { AccentColor.getAccentColorInt(context) }
    private val textColor: Int by lazy {
        val tv = TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
        tv.data
    }

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cellStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = sp(13f)
        isFakeBoldText = true
    }

    private val cellRect = RectF()

    /** Grid geometry, computed in onSizeChanged. */
    private var labelHeight = 0f
    private var cellSize = 0f
    private var gap = 0f
    private var offsetX = 0f

    /** Real-calendar mapping: cellDates[i] = midnight of the day cell i represents. */
    private val cellDates = LongArray(DAYS)
    private var todayMillis = 0L

    init {
        // Monday-first columns like Foqos.
        setWillNotDraw(false)
        // Initialize geometry BEFORE the first measure pass: onMeasure depends on
        // labelHeight/gap, and onSizeChanged only runs AFTER the first layout —
        // otherwise the first measure underestimates the height and the grid cells
        // get squeezed (shrunken + left-aligned dead space).
        labelHeight = sp(12f)
        gap = dp(3f)
        contentDescription = contentDescription ?: context.getString(
            at.saltyy.switchly.R.string.activity_heatmap_content_desc
        )
    }

    fun setData(valuesMs: LongArray, todayIdx: Int = valuesMs.size - 1) {
        dayValuesMs = if (valuesMs.size == DAYS) valuesMs.clone() else padOrTrim(valuesMs)
        todayIndex = todayIdx.coerceIn(0, dayValuesMs.size - 1)
        selectedCell = -1
        invalidate()
    }

    private fun padOrTrim(values: LongArray): LongArray {
        val out = LongArray(DAYS)
        val n = minOf(values.size, DAYS)
        if (n > 0) System.arraycopy(values, 0, out, DAYS - n, n)
        return out
    }

    fun clearSelection() {
        if (selectedCell != -1) {
            selectedCell = -1
            invalidate()
        }
    }

    /** Compact duration label for inside filled cells ("45m", "2h"). */
    private fun durationLabel(ms: Long): String {
        val totalMin = ms / 60_000L
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> "${h}h"
            m > 0 -> "${m}m"
            else -> "<1m"
        }
    }

    /** Live-update today's value (array tail) without clearing the selection. */
    fun updateTodayValue(ms: Long) {
        if (dayValuesMs.isEmpty()) {
            return
        }
        val idx = dayValuesMs.size - 1
        if (dayValuesMs[idx] == ms) {
            return
        }
        dayValuesMs[idx] = ms
        invalidate()
    }

    /** Monday-first calendar window: current week + the 3 before it. */
    private fun refreshCalendar() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        todayMillis = cal.timeInMillis
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysSinceMonday = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_YEAR, -(daysSinceMonday + (ROWS - 1) * COLS))
        for (i in 0 until DAYS) {
            cellDates[i] = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    /** Day offset between midnight [fromMillis] and today, DST-safe. */
    private fun daysAgo(fromMillis: Long): Int =
        ((todayMillis - fromMillis).toDouble() / DAY_MS).roundToInt()

    /** Blocked ms for grid cell [i]; -1 when the day is future/outside the window. */
    private fun valueFor(i: Int): Long {
        if (cellDates[i] > todayMillis) return -1L
        val back = daysAgo(cellDates[i])
        if (back >= dayValuesMs.size) return -1L
        return dayValuesMs[dayValuesMs.size - 1 - back]
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gap = dp(3f)
        val availW = w - paddingLeft - paddingRight
        val availH = h - paddingTop - paddingBottom
        cellSize = max(
            0f,
            minOf(
                (availW - gap * (COLS - 1)) / COLS,
                (availH - labelHeight * ROWS - gap * (ROWS - 1)) / ROWS
            )
        )
        // Center the grid horizontally when the height constraint shrank the cells.
        val gridW = COLS * cellSize + (COLS - 1) * gap
        offsetX = ((availW - gridW) / 2f).coerceAtLeast(0f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(paddingLeft + paddingRight)
        val cell = (w - paddingLeft - paddingRight - dp(3f) * (COLS - 1)) / COLS
        val h = ROWS * (labelHeight + cell) + (ROWS - 1) * dp(3f) + paddingTop + paddingBottom
        setMeasuredDimension(
            w,
            resolveSize(h.toInt().coerceAtLeast(minHeightHint()), heightMeasureSpec)
        )
    }

    private fun minHeightHint(): Int = dp(120f).toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        refreshCalendar()
        val cal = Calendar.getInstance()

        for (i in 0 until DAYS) {
            val col = i % COLS
            val row = i / COLS

            val left = paddingLeft + offsetX + col * (cellSize + gap)
            val top = paddingTop + row * (labelHeight + cellSize + gap) + labelHeight
            cellRect.set(left, top, left + cellSize, top + cellSize)

            cal.timeInMillis = cellDates[i]
            val dayLabel = cal.get(Calendar.DAY_OF_MONTH).toString()

            // Day-of-month label ABOVE every cell (real-calendar look).
            labelPaint.color = textColor
            canvas.drawText(
                dayLabel,
                left + cellSize / 2f,
                paddingTop + row * (labelHeight + cellSize + gap) + labelHeight - dp(2f),
                labelPaint
            )

            val v = valueFor(i)
            val isFuture = v < 0L && cellDates[i] > todayMillis
            cellPaint.color = colorFor(v)
            val radius = cellSize * 0.26f
            canvas.drawRoundRect(cellRect, radius, radius, cellPaint)

            if (cellDates[i] == todayMillis || (!isFuture && i == selectedCell)) {
                cellStrokePaint.color = accent
                canvas.drawRoundRect(cellRect, radius, radius, cellStrokePaint)
            }

            // Duration label INSIDE cells that have data.
            if (v > 0L) {
                numberPaint.color = onBucketColor(v)
                val x = left + cellSize / 2f
                val y = top + cellSize / 2f - (numberPaint.descent() + numberPaint.ascent()) / 2f
                canvas.drawText(durationLabel(v), x, y, numberPaint)
            }
        }
    }

    private fun colorFor(valueMs: Long): Int {
        val bucket = bucketFor(valueMs)
        if (bucket < 0) {
            // Empty day: subtle text-color tint so it's visible in light AND dark mode.
            return (textColor and 0x00FFFFFF) or 0x1F000000
        }
        return bucketColors(accent)[bucket]
    }

    /** Number color inside a cell: dark text on light buckets, white on dark buckets. */
    private fun onBucketColor(valueMs: Long): Int {
        return if (bucketFor(valueMs) <= 1) Color.argb(0xFF, 0x1B, 0x1B, 0x18) else Color.WHITE
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_UP -> {
                val x = event.x - paddingLeft - offsetX
                val y = event.y - paddingTop
                if (x < 0 || y < 0) return performClick()
                val col = (x / (cellSize + gap)).toInt()
                val pitch = labelHeight + cellSize + gap
                val row = (y / pitch).toInt()
                if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return performClick()
                val idx = row * COLS + col
                if (idx !in 0 until DAYS) return performClick()
                val v = valueFor(idx)
                if (v <= 0L || cellDates[idx] > todayMillis) return performClick()
                // Translate grid cell -> index into the oldest->today data array.
                val arrayIdx = dayValuesMs.size - 1 - daysAgo(cellDates[idx])
                selectedCell = if (selectedCell == idx) -1 else idx
                invalidate()
                onDaySelected?.invoke(if (selectedCell == -1) -1 else arrayIdx)
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity

    companion object {
        const val DAYS = 28
        const val ROWS = 4
        const val COLS = 7
        private const val DAY_MS = 86_400_000L

        /** Intensity bucket for a day value in ms (Foqos legend: <1h, 1-3h, 3-5h, >5h). */
        fun bucketFor(valueMs: Long): Int = when {
            valueMs <= 0L -> -1
            valueMs < 3_600_000L -> 0
            valueMs < 3 * 3_600_000L -> 1
            valueMs < 5 * 3_600_000L -> 2
            else -> 3
        }

        /**
         * The 4 bucket fill colors, light -> dark single-hue ramp derived from the
         * active accent (Foqos uses a light->dark purple ramp on its accent).
         */
        fun bucketColors(accent: Int): IntArray {
            val hsv = FloatArray(3)
            Color.colorToHSV(accent, hsv)
            fun variant(lighten: Float, satMul: Float): Int {
                val v = FloatArray(3)
                v[0] = hsv[0]
                v[1] = (hsv[1] * satMul).coerceIn(0.25f, 1f)
                v[2] = (hsv[2] + lighten).coerceIn(0f, 1f)
                return Color.HSVToColor(v)
            }
            return intArrayOf(
                variant(0.42f, 0.55f),
                variant(0.18f, 0.8f),
                accent,
                variant(-0.22f, 1.05f),
            )
        }

        /** Human-readable bucket labels for legends. */
        fun bucketLabels(): List<String> = listOf("<1h", "1-3h", "3-5h", ">5h")
    }
}