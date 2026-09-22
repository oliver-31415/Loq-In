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

import android.content.res.ColorStateList
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.addTextChangedListener
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.EmergencyPinStore
import com.oliver.loqin.data.prefs.TempPauseStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.dialog.Dialogs
import com.oliver.loqin.ui.dialog.EmergencyPinDialog
import com.oliver.loqin.ui.dialog.applyLoqInDialogWidth
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.util.EditingLockGuard
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Shared per-profile temporary-pause caps editor. */
object TempPauseDialogs {

    private const val MAX_COUNT = 100
    private const val MAX_MINUTES = 1440
    private const val DEFAULT_COUNT = 2
    private const val DEFAULT_PER_PAUSE_MINUTES = 15
    private const val DEFAULT_PER_DAY_MINUTES = 30
    private const val STEP_COUNT = 1
    private const val STEP_PER_PAUSE_MINUTES = 5
    private const val STEP_PER_DAY_MINUTES = 15

    fun summaryText(activity: AppCompatActivity, profile: String): String {
        val caps = TempPauseStore.getCaps(activity, profile)
        val used = activity.getString(
            R.string.temp_pause_used_fmt,
            TempPauseStore.usedCountToday(activity, profile),
            TempPauseStore.usedMinutesToday(activity, profile)
        )
        if (caps.maxCountPerDay <= 0 && caps.maxMinutesPerPause <= 0 && caps.maxMinutesPerDay <= 0) {
            return activity.getString(R.string.temp_pause_summary_unlimited) + "\n" + used
        }
        val parts = mutableListOf<String>()
        if (caps.maxCountPerDay > 0) {
            parts += activity.getString(R.string.temp_pause_part_count_fmt, caps.maxCountPerDay)
        }
        if (caps.maxMinutesPerPause > 0) {
            parts += activity.getString(R.string.temp_pause_part_max_fmt, caps.maxMinutesPerPause)
        }
        if (caps.maxMinutesPerDay > 0) {
            parts += activity.getString(R.string.temp_pause_part_total_fmt, caps.maxMinutesPerDay)
        }
        return parts.joinToString(" · ") + "\n" + used
    }

    fun show(activity: AppCompatActivity, profile: String, onSaved: (() -> Unit)? = null) {
        val content = activity.layoutInflater.inflate(R.layout.dialog_temp_pause, FrameLayout(activity), false)
        val swCount = content.findViewById<SwitchCompat>(R.id.swTempPauseCount)
        val swPerPause = content.findViewById<SwitchCompat>(R.id.swTempPausePerPause)
        val swPerDay = content.findViewById<SwitchCompat>(R.id.swTempPausePerDay)
        val rowCount = content.findViewById<View>(R.id.rowTempPauseCountControls)
        val rowPerPause = content.findViewById<View>(R.id.rowTempPausePerPauseControls)
        val rowPerDay = content.findViewById<View>(R.id.rowTempPausePerDayControls)
        val tilCount = content.findViewById<TextInputLayout>(R.id.tilTempPauseCount)
        val etCount = content.findViewById<TextInputEditText>(R.id.etTempPauseCount)
        val tilPerPause = content.findViewById<TextInputLayout>(R.id.tilTempPausePerPause)
        val etPerPause = content.findViewById<TextInputEditText>(R.id.etTempPausePerPause)
        val tilPerDay = content.findViewById<TextInputLayout>(R.id.tilTempPausePerDay)
        val etPerDay = content.findViewById<TextInputEditText>(R.id.etTempPausePerDay)
        val tvSentence = content.findViewById<TextView>(R.id.tvTempPauseSentence)
        val tvUsage = content.findViewById<TextView>(R.id.tvTempPauseUsage)
        val btnClear = content.findViewById<MaterialButton>(R.id.btnTempPauseClear)
        val btnCancel = content.findViewById<MaterialButton>(R.id.btnTempPauseCancel)
        val btnSave = content.findViewById<MaterialButton>(R.id.btnTempPauseSave)

        val caps = TempPauseStore.getCaps(activity, profile)
        etCount.setText(if (caps.maxCountPerDay > 0) caps.maxCountPerDay.toString() else "")
        etPerPause.setText(if (caps.maxMinutesPerPause > 0) caps.maxMinutesPerPause.toString() else "")
        etPerDay.setText(if (caps.maxMinutesPerDay > 0) caps.maxMinutesPerDay.toString() else "")
        swCount.isChecked = caps.maxCountPerDay > 0
        swPerPause.isChecked = caps.maxMinutesPerPause > 0
        swPerDay.isChecked = caps.maxMinutesPerDay > 0

        fun refreshUsage() {
            val usedCount = TempPauseStore.usedCountToday(activity, profile)
            val usedMinutes = TempPauseStore.usedMinutesToday(activity, profile)
            tvUsage.text = activity.getString(R.string.temp_pause_used_fmt, usedCount, usedMinutes)
            btnClear.visibility = if (usedCount > 0 || usedMinutes > 0) View.VISIBLE else View.GONE
            btnClear.alpha = if (EditingLockGuard.isLocked(activity)) 0.65f else 1f
        }
        refreshUsage()

        val accent = AccentColor.getAccentColorInt(activity)
        val accentList = ColorStateList.valueOf(accent)
        listOf(tilCount, tilPerPause, tilPerDay).forEach { til ->
            til.boxStrokeColor = accent
            til.hintTextColor = accentList
            til.defaultHintTextColor = accentList
        }
        listOf(swCount, swPerPause, swPerDay).forEach { CustomAccentApplier.tintSwitch(it) }
        runCatching {
            val surfaceVariant = ContextCompat.getColor(activity, R.color.foqos_surface_variant)
            content.findViewById<MaterialCardView>(R.id.cardTempPauseSummary)
                .setCardBackgroundColor(
                    ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x1F), surfaceVariant)
                )
        }

        // Disabled sections collapse to their header row so the whole dialog fits without
        // scrolling; enabling a switch reveals its controls.
        fun setSectionEnabled(container: View, enabled: Boolean) {
            container.visibility = if (enabled) View.VISIBLE else View.GONE
            fun apply(view: View) {
                view.isEnabled = enabled
                if (view is android.view.ViewGroup) {
                    for (index in 0 until view.childCount) {
                        apply(view.getChildAt(index))
                    }
                }
            }
            apply(container)
        }

        fun fmtInt(value: Int): String = if (value > 0) value.toString() else ""

        fun rawInt(field: TextInputEditText): Int =
            field.text?.toString()?.trim()?.toIntOrNull() ?: 0

        fun effectiveValues(): Triple<Int, Int, Int> {
            val count = if (swCount.isChecked) rawInt(etCount) else 0
            val perPause = if (swPerPause.isChecked) rawInt(etPerPause) else 0
            val perDay = if (swPerDay.isChecked) rawInt(etPerDay) else 0
            return Triple(count, perPause, perDay)
        }

        fun refreshSentence() {
            val (count, perPause, perDay) = effectiveValues()
            val parts = mutableListOf<String>()
            if (count > 0) parts += activity.getString(R.string.temp_pause_part_count_fmt, count)
            if (perPause > 0) parts += activity.getString(R.string.temp_pause_part_max_fmt, perPause)
            if (perDay > 0) parts += activity.getString(R.string.temp_pause_part_total_fmt, perDay)
            tvSentence.text = if (parts.isEmpty()) {
                activity.getString(R.string.temp_pause_summary_unlimited)
            } else {
                parts.joinToString(" · ")
            }
        }

        fun parseField(
            enabled: Boolean,
            field: TextInputEditText,
            layout: TextInputLayout,
            max: Int,
            errorFmtRes: Int,
        ): Int? {
            if (!enabled) {
                layout.error = null
                return 0
            }
            val raw = field.text?.toString()?.trim().orEmpty()
            if (raw.isBlank()) {
                layout.error = activity.getString(R.string.app_limit_error_required)
                return null
            }
            val value = raw.toIntOrNull()
            if (value == null) {
                layout.error = activity.getString(R.string.app_limit_error_not_number)
                return null
            }
            if (value !in 1..max) {
                layout.error = activity.getString(errorFmtRes, max)
                return null
            }
            layout.error = null
            return value
        }

        fun validateAll(focusInvalid: Boolean): Triple<Int, Int, Int>? {
            val count = parseField(swCount.isChecked, etCount, tilCount, MAX_COUNT, R.string.temp_pause_error_count_fmt)
            val perPause = parseField(swPerPause.isChecked, etPerPause, tilPerPause, MAX_MINUTES, R.string.temp_pause_error_minutes_fmt)
            val perDay = parseField(swPerDay.isChecked, etPerDay, tilPerDay, MAX_MINUTES, R.string.temp_pause_error_minutes_fmt)
            if (count == null || perPause == null || perDay == null) {
                if (focusInvalid) {
                    when {
                        count == null -> etCount.requestFocus()
                        perPause == null -> etPerPause.requestFocus()
                        else -> etPerDay.requestFocus()
                    }
                }
                return null
            }
            return Triple(count, perPause, perDay)
        }

        val locked = EditingLockGuard.isLocked(activity)

        fun refreshSaveState() {
            val valid = validateAll(focusInvalid = false) != null
            btnSave.isEnabled = valid
            btnSave.alpha = when {
                !valid -> 0.5f
                locked -> 0.65f
                else -> 1f
            }
        }

        fun refreshAll() {
            refreshSentence()
            refreshSaveState()
        }

        etCount.addTextChangedListener { tilCount.error = null; refreshAll() }
        etPerPause.addTextChangedListener { tilPerPause.error = null; refreshAll() }
        etPerDay.addTextChangedListener { tilPerDay.error = null; refreshAll() }

        swCount.setOnCheckedChangeListener { _, checked ->
            if (checked && etCount.text?.toString()?.trim().isNullOrEmpty()) {
                etCount.setText(fmtInt(DEFAULT_COUNT))
                etCount.setSelection(etCount.text?.length ?: 0)
            }
            if (!checked) tilCount.error = null
            setSectionEnabled(rowCount, checked)
            refreshAll()
        }
        swPerPause.setOnCheckedChangeListener { _, checked ->
            if (checked && etPerPause.text?.toString()?.trim().isNullOrEmpty()) {
                etPerPause.setText(fmtInt(DEFAULT_PER_PAUSE_MINUTES))
                etPerPause.setSelection(etPerPause.text?.length ?: 0)
            }
            if (!checked) tilPerPause.error = null
            setSectionEnabled(rowPerPause, checked)
            refreshAll()
        }
        swPerDay.setOnCheckedChangeListener { _, checked ->
            if (checked && etPerDay.text?.toString()?.trim().isNullOrEmpty()) {
                etPerDay.setText(fmtInt(DEFAULT_PER_DAY_MINUTES))
                etPerDay.setSelection(etPerDay.text?.length ?: 0)
            }
            if (!checked) tilPerDay.error = null
            setSectionEnabled(rowPerDay, checked)
            refreshAll()
        }

        fun step(
            field: TextInputEditText,
            layout: TextInputLayout,
            switch: SwitchCompat,
            delta: Int,
            max: Int,
            defaultOnEnable: Int,
        ) {
            if (!switch.isChecked) {
                if (delta <= 0) return
                field.setText(fmtInt(defaultOnEnable.coerceAtLeast(1)))
                field.setSelection(field.text?.length ?: 0)
                switch.isChecked = true
                return
            }
            val next = rawInt(field) + delta
            if (next < 1) {
                field.setText("")
                switch.isChecked = false
                return
            }
            field.setText(fmtInt(next.coerceAtMost(max)))
            field.setSelection(field.text?.length ?: 0)
            layout.error = null
        }

        content.findViewById<MaterialButton>(R.id.btnTempPauseCountMinus).setOnClickListener {
            step(etCount, tilCount, swCount, -STEP_COUNT, MAX_COUNT, DEFAULT_COUNT)
        }
        content.findViewById<MaterialButton>(R.id.btnTempPauseCountPlus).setOnClickListener {
            step(etCount, tilCount, swCount, STEP_COUNT, MAX_COUNT, DEFAULT_COUNT)
        }
        content.findViewById<MaterialButton>(R.id.btnTempPausePerPauseMinus).setOnClickListener {
            step(etPerPause, tilPerPause, swPerPause, -STEP_PER_PAUSE_MINUTES, MAX_MINUTES, DEFAULT_PER_PAUSE_MINUTES)
        }
        content.findViewById<MaterialButton>(R.id.btnTempPausePerPausePlus).setOnClickListener {
            step(etPerPause, tilPerPause, swPerPause, STEP_PER_PAUSE_MINUTES, MAX_MINUTES, DEFAULT_PER_PAUSE_MINUTES)
        }
        content.findViewById<MaterialButton>(R.id.btnTempPausePerDayMinus).setOnClickListener {
            step(etPerDay, tilPerDay, swPerDay, -STEP_PER_DAY_MINUTES, MAX_MINUTES, DEFAULT_PER_DAY_MINUTES)
        }
        content.findViewById<MaterialButton>(R.id.btnTempPausePerDayPlus).setOnClickListener {
            step(etPerDay, tilPerDay, swPerDay, STEP_PER_DAY_MINUTES, MAX_MINUTES, DEFAULT_PER_DAY_MINUTES)
        }

        setSectionEnabled(rowCount, swCount.isChecked)
        setSectionEnabled(rowPerPause, swPerPause.isChecked)
        setSectionEnabled(rowPerDay, swPerDay.isChecked)
        refreshAll()

        // Buttons follow the global dialog design: filled accent save, text cancel, red clear.
        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.5) {
            android.graphics.Color.BLACK
        } else {
            android.graphics.Color.WHITE
        }
        val errorColor = android.graphics.Color.rgb(186, 26, 26)

        btnCancel.setTextColor(accent)
        btnCancel.isAllCaps = false
        btnCancel.backgroundTintList = null
        runCatching { btnCancel.setBackgroundColor(android.graphics.Color.TRANSPARENT) }

        btnClear.setTextColor(errorColor)
        btnClear.isAllCaps = false
        btnClear.backgroundTintList = null
        runCatching { btnClear.setBackgroundColor(android.graphics.Color.TRANSPARENT) }

        btnSave.setTextColor(onAccent)
        btnSave.isAllCaps = false
        btnSave.backgroundTintList = AccentColor.getActiveColor(activity)

        val dialog = Dialogs.builder(activity)
            .setView(content)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            if (EditingLockGuard.isLocked(activity)) {
                content.showWarnPill(R.string.edit_locked_manage_temp_pauses)
                return@setOnClickListener
            }
            val validated = validateAll(focusInvalid = true) ?: return@setOnClickListener
            val (count, perPause, perDay) = validated
            TempPauseStore.setCaps(activity, profile, TempPauseStore.Caps(count, perPause, perDay))
            onSaved?.invoke()
            dialog.dismiss()
        }
        btnClear.setOnClickListener {
            if (EditingLockGuard.isLocked(activity)) {
                content.showWarnPill(R.string.edit_locked_manage_temp_pauses)
                return@setOnClickListener
            }

            fun doReset() {
                TempPauseStore.resetToday(activity, profile)
                refreshUsage()
                content.showWarnPill(R.string.temp_pause_reset_success)
                onSaved?.invoke()
            }

            if (EmergencyPinStore.hasPin(activity)) {
                EmergencyPinDialog.showEnterPin(activity, onSuccess = { doReset() })
            } else {
                EmergencyPinDialog.showSetPin(activity) {
                    doReset()
                }
            }
        }

        dialog.applyLoqInDialogWidth(0.94f)
        dialog.setOnShowListener {
            runCatching { CustomAccentApplier.applyToDialog(dialog) }
        }
        dialog.show()
    }
}
