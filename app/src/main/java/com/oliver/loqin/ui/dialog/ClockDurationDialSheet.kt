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

package com.oliver.loqin.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.widgets.ClockDurationDialView
import java.text.DateFormat
import java.util.Date

/**
 * Reusable clock-dial duration sheet, the same picker used by the dashboard
 * "Take a break" / "Temporarily enable" flow. Returns the chosen whole-minute
 * duration (1..[maxMinutes]) through [onPicked].
 */
object ClockDurationDialSheet {

    fun show(
        activity: Activity,
        title: CharSequence,
        subtitle: CharSequence? = null,
        maxMinutes: Int,
        minMinutes: Int = 1,
        initialMinutes: Int = 25,
        applyLabel: (Int) -> CharSequence,
        onPicked: (Int) -> Unit,
        onDismissed: (() -> Unit)? = null,
    ) {
        val accent = AccentColor.getAccentColorInt(activity)
        val tint = ColorStateList.valueOf(accent)

        val sheet = BottomSheetDialog(activity)
        val parent = activity.findViewById<ViewGroup>(android.R.id.content)
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.bottom_sheet_temp_clock_dial, parent, false)
        sheet.setContentView(view)
        prepareExpandedSheet(activity, sheet)

        val clockDialView = view.findViewById<ClockDurationDialView>(R.id.clockDialView)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvSubtitle)
        val tvDialDuration = view.findViewById<TextView>(R.id.tvDialDuration)
        val tvDialUnit = view.findViewById<TextView>(R.id.tvDialUnit)
        val tvDialEndTime = view.findViewById<TextView>(R.id.tvDialEndTime)
        val btnApplyDuration = view.findViewById<MaterialButton>(R.id.btnApplyDuration)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        view.findViewById<View>(R.id.roundelBg)?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AccentColor.getAccentContainerColorInt(activity))
        }
        // Set drawables in code: app:srcCompat is ignored when inflated by a non-AppCompat Activity.
        view.findViewById<ImageView>(R.id.ivIcon)?.apply {
            setImageResource(R.drawable.timer_24)
            imageTintList = tint
        }
        view.findViewById<ImageView>(R.id.ivClose)?.apply {
            setImageResource(R.drawable.close_24)
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.foqos_on_surface)
            )
        }

        tvTitle.text = title
        if (subtitle.isNullOrBlank()) {
            tvSubtitle.visibility = View.GONE
        } else {
            tvSubtitle.text = subtitle
        }

        val cappedMax = maxMinutes.coerceAtLeast(0)
        clockDialView.accentColor = accent
        clockDialView.minMinutes = 0
        clockDialView.maxMinutes = cappedMax
        val initial = if (cappedMax == 0) {
            0
        } else {
            initialMinutes.coerceIn(minMinutes.coerceAtLeast(1), cappedMax)
        }
        clockDialView.setDurationMinutes(initial, animate = false)

        val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.5) Color.BLACK else Color.WHITE
        btnApplyDuration.backgroundTintList = ColorStateList.valueOf(accent)
        btnApplyDuration.setTextColor(onAccent)

        fun updateDisplay(minutes: Int) {
            if (minutes <= 0) {
                tvDialDuration.text = "0"
                tvDialUnit.text = activity.getString(R.string.minutes).uppercase()
                tvDialEndTime.text = activity.getString(R.string.tile_temp_subtitle_choose)
                btnApplyDuration.isEnabled = false
                btnApplyDuration.alpha = 0.45f
                btnApplyDuration.text = activity.getString(R.string.tile_temp_subtitle_choose)
                return
            }

            btnApplyDuration.isEnabled = true
            btnApplyDuration.alpha = 1f

            if (minutes < 60) {
                tvDialDuration.text = "$minutes"
                tvDialUnit.text = activity.getString(R.string.minutes).uppercase()
            } else {
                val h = minutes / 60
                val m = minutes % 60
                tvDialDuration.text = if (m == 0) "${h}h" else "${h}h ${m}m"
                tvDialUnit.text = activity.getString(R.string.tile_temp_title_plain).uppercase()
            }

            val endTimeMillis = System.currentTimeMillis() + minutes * 60_000L
            tvDialEndTime.text = activity.getString(
                R.string.dashboard_temp_ends_at,
                timeFormat.format(Date(endTimeMillis))
            )
            btnApplyDuration.text = applyLabel(minutes)
        }

        updateDisplay(initial)
        clockDialView.onDurationChanged = { mins -> updateDisplay(mins) }

        var picked = false
        btnApplyDuration.setOnClickListener {
            val mins = clockDialView.durationMinutes
            if (mins <= 0) return@setOnClickListener
            picked = true
            sheet.dismiss()
            onPicked(mins)
        }
        btnClose.setOnClickListener { sheet.dismiss() }

        sheet.setOnDismissListener {
            if (!picked) onDismissed?.invoke()
        }
        sheet.show()
    }

    private fun prepareExpandedSheet(activity: Activity, dialog: BottomSheetDialog) {
        dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { bs ->
            val topRadius = 24 * activity.resources.displayMetrics.density + 0.5f
            bs.background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    topRadius, topRadius,
                    topRadius, topRadius,
                    0f, 0f,
                    0f, 0f
                )
                setColor(ContextCompat.getColor(activity, R.color.foqos_surface))
            }
            BottomSheetBehavior.from(bs).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }
}
