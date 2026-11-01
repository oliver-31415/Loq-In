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

package com.oliver.loqin.feature.profiles

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.EmergencyPinStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.TempPauseStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.SheetRows
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.EmergencyPinDialog
import com.oliver.loqin.ui.dialog.LoqInInfoRow
import com.oliver.loqin.ui.dialog.showLoqInInfoDialog
import com.oliver.loqin.ui.dialog.showLoqInNumberInput
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.ui.widgets.ListGroupLayout
import com.oliver.loqin.util.EditingLockGuard

private const val MAX_COUNT = 100
private const val MAX_MINUTES = 1440
private const val DEFAULT_COUNT = 2
private const val DEFAULT_PER_PAUSE_MINUTES = 15
private const val DEFAULT_PER_DAY_MINUTES = 30

/**
 * Per-profile break limits. A "Today" card shows what's left, with a meter per daily limit;
 * below it each limit is a settings row: the switch turns it on or off, the row edits the amount.
 */
class BreaksActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PROFILE_NAME = "extra_profile_name"
    }

    private enum class Cap(
        @StringRes val labelRes: Int,
        @DrawableRes val iconRes: Int,
        @StringRes val hintRes: Int,
        @StringRes val errorRes: Int,
        @StringRes val infoRes: Int,
        val max: Int,
        val defaultValue: Int,
    ) {
        COUNT(
            R.string.temp_pause_section_count, R.drawable.calendar_month_24, R.string.temp_pause_section_count,
            R.string.temp_pause_error_count_fmt, R.string.temp_pause_section_count_subtitle, MAX_COUNT, DEFAULT_COUNT
        ),
        PER_PAUSE(
            R.string.temp_pause_section_length, R.drawable.timer_24, R.string.minutes_hint,
            R.string.temp_pause_error_minutes_fmt, R.string.temp_pause_section_length_subtitle, MAX_MINUTES, DEFAULT_PER_PAUSE_MINUTES
        ),
        PER_DAY(
            R.string.temp_pause_section_total, R.drawable.schedule_24, R.string.minutes_hint,
            R.string.temp_pause_error_minutes_fmt, R.string.temp_pause_section_total_subtitle, MAX_MINUTES, DEFAULT_PER_DAY_MINUTES
        ),
    }

    private lateinit var todayTitle: TextView
    private lateinit var meterCount: View
    private lateinit var meterMinutes: View
    private lateinit var btnReset: MaterialButton
    private lateinit var lockNote: View
    private lateinit var groupLimits: ListGroupLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_breaks)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        todayTitle = findViewById(R.id.todayTitle)
        meterCount = findViewById(R.id.meterCount)
        meterMinutes = findViewById(R.id.meterMinutes)
        btnReset = findViewById(R.id.btnResetToday)
        lockNote = findViewById(R.id.lockNote)
        groupLimits = findViewById(R.id.groupLimits)
        findViewById<View>(R.id.headerLimits).findViewById<TextView>(R.id.tvHeader).text =
            getString(R.string.breaks_page_limits_header)
        meterCount.findViewById<TextView>(R.id.meterLabel).setText(R.string.breaks_meter_count)
        meterMinutes.findViewById<TextView>(R.id.meterLabel).setText(R.string.breaks_meter_minutes)
        btnReset.setOnClickListener { resetToday(it) }

        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        toolbar.setBackgroundColor(AccentColor.getToolbarBackgroundColor(this))
        toolbar.subtitle = getString(R.string.app_rules_profile_subtitle, currentProfile())
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        render()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, R.id.action_breaks_info, Menu.NONE, R.string.profile_sheet_pauses)
            .setIcon(R.drawable.info_24)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == R.id.action_breaks_info) {
            showLoqInInfoDialog(
                title = getString(R.string.profile_sheet_pauses),
                rows = Cap.entries.map { cap ->
                    LoqInInfoRow(label = getString(cap.labelRes), value = getString(cap.infoRes))
                },
            )
            true
        } else {
            super.onOptionsItemSelected(item)
        }

    override fun onResume() {
        super.onResume()
        if (::groupLimits.isInitialized) render()
    }

    private fun currentProfile(): String =
        intent.getStringExtra(EXTRA_PROFILE_NAME)?.trim()?.takeIf { it.isNotBlank() }
            ?: ProfileStore.getCurrent(this) ?: "default"

    private fun render() {
        CustomAccentApplier.applyIfNeeded(this)
        val profile = currentProfile()
        val locked = EditingLockGuard.isLocked(this)
        val caps = TempPauseStore.getCaps(this, profile)
        val usedCount = TempPauseStore.usedCountToday(this, profile)
        val usedMinutes = TempPauseStore.usedMinutesToday(this, profile)

        renderToday(profile, caps, usedCount, usedMinutes, locked)

        lockNote.isVisible = locked
        groupLimits.removeAllViews()
        limitRow(Cap.COUNT, caps.maxCountPerDay, locked)
        limitRow(Cap.PER_PAUSE, caps.maxMinutesPerPause, locked)
        limitRow(Cap.PER_DAY, caps.maxMinutesPerDay, locked)
        groupLimits.refreshSegments()
    }

    private fun renderToday(profile: String, caps: TempPauseStore.Caps, usedCount: Int, usedMinutes: Int, locked: Boolean) {
        val nextMax = TempPauseStore.maxAllowedDurationMinutes(this, profile)
        todayTitle.text = when {
            !TempPauseStore.hasCaps(this, profile) -> getString(R.string.breaks_page_summary_unlimited)
            nextMax <= 0 -> getString(R.string.breaks_today_none_left)
            caps.maxCountPerDay > 0 -> {
                val left = TempPauseStore.remainingPauses(this, profile)
                resources.getQuantityString(R.plurals.breaks_today_left, caps.maxCountPerDay, left, caps.maxCountPerDay)
            }
            nextMax != Int.MAX_VALUE -> getString(R.string.breaks_today_next_fmt, nextMax)
            else -> getString(R.string.breaks_page_summary_unlimited)
        }

        bindMeter(meterCount, usedCount, caps.maxCountPerDay, R.string.breaks_meter_count_fmt, null)
        bindMeter(
            meterMinutes, usedMinutes, caps.maxMinutesPerDay,
            R.string.breaks_meter_minutes_fmt, R.string.breaks_page_minutes_fmt,
        )

        btnReset.isVisible = usedCount > 0 || usedMinutes > 0
        btnReset.alpha = if (locked) 0.45f else 1f
    }

    /**
     * "used / limit" with a bar when the limit is on; with no limit just the amount used, no bar.
     * [plainRes] formats the bare amount (null = the number alone).
     */
    private fun bindMeter(meter: View, used: Int, limit: Int, @StringRes cappedRes: Int, @StringRes plainRes: Int?) {
        val value = meter.findViewById<TextView>(R.id.meterValue)
        val bar = meter.findViewById<LinearProgressIndicator>(R.id.meterBar)
        if (limit > 0) {
            value.text = getString(cappedRes, used, limit)
            val accent = AccentColor.getAccentColorInt(this)
            val full = used >= limit
            val color = if (full) ContextCompat.getColor(this, R.color.status_warning) else accent
            bar.setIndicatorColor(color)
            // Opaque tint: a see-through track darkens where its rounded end overlaps itself.
            bar.trackColor = ColorUtils.compositeColors(
                ColorUtils.setAlphaComponent(color, 0x33),
                ContextCompat.getColor(this, R.color.foqos_surface),
            )
            bar.max = limit
            bar.setProgressCompat(used.coerceAtMost(limit), false)
            bar.isVisible = true
        } else {
            value.text = plainRes?.let { getString(it, used) } ?: used.toString()
            bar.isVisible = false
        }
    }

    private fun Cap.valueText(value: Int): String = when (this) {
        Cap.COUNT -> resources.getQuantityString(R.plurals.breaks_limit_count, value, value)
        Cap.PER_PAUSE -> getString(R.string.breaks_limit_up_to_fmt, value)
        Cap.PER_DAY -> getString(R.string.breaks_page_minutes_fmt, value)
    }

    private fun limitRow(cap: Cap, value: Int, locked: Boolean) {
        val on = value > 0
        val accent = AccentColor.getAccentColorInt(this)
        val label = getString(cap.labelRes)
        val row = SheetRows.row(
            this, groupLimits, cap.iconRes, label,
            if (on) cap.valueText(value) else getString(R.string.breaks_page_no_limit),
            accent,
        )
        if (on) row.summary.setTextColor(accent)
        val switch = MaterialSwitch(this).apply {
            isChecked = on
            thumbIconDrawable = ContextCompat.getDrawable(this@BreaksActivity, R.drawable.loqin_switch_thumb_icon)
            CustomAccentApplier.tintSwitch(this)
            contentDescription = label
        }
        switch.setOnCheckedChangeListener { button, checked ->
            if (locked) {
                button.isChecked = !checked
                row.root.showWarnPill(R.string.edit_locked_manage_temp_pauses)
            } else {
                writeCap(cap, if (checked) cap.defaultValue else 0)
            }
        }
        row.trailing.addView(switch)
        row.root.setOnClickListener {
            if (locked) it.showWarnPill(R.string.edit_locked_manage_temp_pauses) else pickCap(cap, value)
        }
        // Fade the content, not the segment, so the group keeps its shape while read-only.
        if (locked) {
            switch.isEnabled = false
            row.icon.alpha = 0.45f
            row.title.alpha = 0.55f
            row.summary.alpha = 0.55f
        }
        groupLimits.addView(row.root)
    }

    private fun pickCap(cap: Cap, current: Int) {
        showLoqInNumberInput(
            title = getString(cap.labelRes),
            hint = getString(cap.hintRes),
            confirmText = getString(R.string.save),
            range = 1..cap.max,
            invalidMessage = getString(cap.errorRes, cap.max),
            initialValue = if (current > 0) current else cap.defaultValue,
        ) { writeCap(cap, it) }
    }

    /** Saves one cap and keeps the other two as they are. */
    private fun writeCap(cap: Cap, value: Int) {
        if (EditingLockGuard.isLocked(this)) {
            findViewById<View>(R.id.root).showWarnPill(R.string.edit_locked_manage_temp_pauses)
            return
        }
        val profile = currentProfile()
        val caps = TempPauseStore.getCaps(this, profile)
        TempPauseStore.setCaps(
            this,
            profile,
            TempPauseStore.Caps(
                maxCountPerDay = if (cap == Cap.COUNT) value else caps.maxCountPerDay,
                maxMinutesPerPause = if (cap == Cap.PER_PAUSE) value else caps.maxMinutesPerPause,
                maxMinutesPerDay = if (cap == Cap.PER_DAY) value else caps.maxMinutesPerDay,
            )
        )
        render()
    }

    private fun resetToday(anchor: View) {
        if (EditingLockGuard.isLocked(this)) {
            anchor.showWarnPill(R.string.edit_locked_manage_temp_pauses)
            return
        }

        fun doReset() {
            TempPauseStore.resetToday(this, currentProfile())
            anchor.showWarnPill(R.string.temp_pause_reset_success)
            render()
        }

        if (EmergencyPinStore.hasPin(this)) {
            EmergencyPinDialog.showEnterPin(this, onSuccess = { doReset() })
        } else {
            EmergencyPinDialog.showSetPin(this) { doReset() }
        }
    }
}
