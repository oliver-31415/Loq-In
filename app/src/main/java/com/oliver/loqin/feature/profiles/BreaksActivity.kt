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
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.EmergencyPinStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.TempPauseStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.RuleSummary
import com.oliver.loqin.ui.RuleTile
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.EmergencyPinDialog
import com.oliver.loqin.ui.dialog.LoqInInfoRow
import com.oliver.loqin.ui.dialog.showLoqInInfoDialog
import com.oliver.loqin.ui.dialog.showLoqInNumberInput
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.ui.widgets.TileGridLayout
import com.oliver.loqin.util.EditingLockGuard

private const val MAX_COUNT = 100
private const val MAX_MINUTES = 1440
private const val DEFAULT_COUNT = 2
private const val DEFAULT_PER_PAUSE_MINUTES = 15
private const val DEFAULT_PER_DAY_MINUTES = 30

/** Per-profile temporary-pause limits: one tile per cap, plus today's usage. */
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

    private lateinit var summary: View
    private lateinit var gridLimits: TileGridLayout
    private lateinit var gridToday: TileGridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_breaks)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        summary = findViewById(R.id.breaksSummary)
        gridLimits = findViewById(R.id.gridLimits)
        gridToday = findViewById(R.id.gridToday)
        findViewById<View>(R.id.headerLimits).findViewById<TextView>(R.id.tvHeader).text =
            getString(R.string.breaks_page_limits_header)
        findViewById<View>(R.id.headerToday).findViewById<TextView>(R.id.tvHeader).text =
            getString(R.string.breaks_page_today_header)

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
        if (::gridLimits.isInitialized) render()
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

        val unlimited = caps.maxCountPerDay <= 0 && caps.maxMinutesPerPause <= 0 && caps.maxMinutesPerDay <= 0
        RuleSummary.bind(
            summary,
            R.drawable.hourglass_24,
            if (unlimited) getString(R.string.breaks_page_summary_unlimited) else TempPauseDialogs.capsSummaryText(this, profile),
            getString(R.string.temp_pause_used_fmt, usedCount, usedMinutes)
        )

        gridLimits.removeAllViews()
        capTile(Cap.COUNT, caps.maxCountPerDay, locked)
        capTile(Cap.PER_PAUSE, caps.maxMinutesPerPause, locked)
        capTile(Cap.PER_DAY, caps.maxMinutesPerDay, locked)

        // Today's numbers are already in the summary card; this section only offers the reset.
        val usedToday = usedCount > 0 || usedMinutes > 0
        gridToday.removeAllViews()
        findViewById<View>(R.id.headerToday).visibility = if (usedToday) View.VISIBLE else View.GONE
        gridToday.visibility = if (usedToday) View.VISIBLE else View.GONE
        if (usedToday) resetTile(locked)
    }

    private fun newTile(grid: TileGridLayout): RuleTile =
        RuleTile.inflate(grid).also { grid.addView(it.root) }

    // The tile label already says "per day" / "per break", so the value is just the amount.
    private fun Cap.valueText(value: Int): String = when (this) {
        Cap.COUNT -> value.toString()
        Cap.PER_PAUSE, Cap.PER_DAY -> getString(R.string.breaks_page_minutes_fmt, value)
    }

    private fun capTile(cap: Cap, value: Int, locked: Boolean) {
        val on = value > 0
        val label = getString(cap.labelRes)
        val tile = newTile(gridLimits)
        tile.setSymbol(cap.iconRes)
        // Off reads as a quiet "No limit" line; a set limit gets the big number.
        if (on) tile.setText(label, value = cap.valueText(value))
        else tile.setText(label, sub = getString(R.string.breaks_page_no_limit))
        tile.setState(on, showCheck = false)
        tile.setToggle(on, contentDescription = label) {
            if (locked) {
                tile.root.showWarnPill(R.string.edit_locked_manage_temp_pauses)
            } else {
                writeCap(cap, if (on) 0 else cap.defaultValue)
            }
        }
        tile.root.setOnClickListener {
            if (locked) {
                it.showWarnPill(R.string.edit_locked_manage_temp_pauses)
            } else {
                pickCap(cap, value)
            }
        }
        tile.setDimmed(locked)
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

    private fun resetTile(locked: Boolean) {
        val tile = newTile(gridToday)
        tile.setSymbol(R.drawable.pin_24)
        tile.setText(getString(R.string.temp_pause_reset_today), sub = getString(R.string.breaks_page_pin_sub))
        tile.setState(false, showCheck = false)
        tile.setToggle(null)
        tile.setDimmed(locked)
        tile.root.setOnClickListener { resetToday(tile.root) }
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
