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

package at.saltyy.switchly.feature.profiles

import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import at.saltyy.switchly.R
import at.saltyy.switchly.data.prefs.EmergencyPinStore
import at.saltyy.switchly.data.prefs.TempPauseStore
import at.saltyy.switchly.ui.dialog.EmergencyPinDialog
import at.saltyy.switchly.ui.dialog.styleSwitchlyDialogButtons
import at.saltyy.switchly.ui.showWarnPill
import at.saltyy.switchly.util.EditingLockGuard
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Shared per-profile temporary-pause caps editor. */
object TempPauseDialogs {

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
        val tilCount = content.findViewById<TextInputLayout>(R.id.tilTempPauseCount)
        val etCount = content.findViewById<TextInputEditText>(R.id.etTempPauseCount)
        val tilPerPause = content.findViewById<TextInputLayout>(R.id.tilTempPausePerPause)
        val etPerPause = content.findViewById<TextInputEditText>(R.id.etTempPausePerPause)
        val tilPerDay = content.findViewById<TextInputLayout>(R.id.tilTempPausePerDay)
        val etPerDay = content.findViewById<TextInputEditText>(R.id.etTempPausePerDay)
        val tvUsage = content.findViewById<TextView>(R.id.tvTempPauseUsage)

        val caps = TempPauseStore.getCaps(activity, profile)
        etCount.setText(if (caps.maxCountPerDay > 0) caps.maxCountPerDay.toString() else "")
        etPerPause.setText(if (caps.maxMinutesPerPause > 0) caps.maxMinutesPerPause.toString() else "")
        etPerDay.setText(if (caps.maxMinutesPerDay > 0) caps.maxMinutesPerDay.toString() else "")
        tvUsage.text = activity.getString(
            R.string.temp_pause_used_fmt,
            TempPauseStore.usedCountToday(activity, profile),
            TempPauseStore.usedMinutesToday(activity, profile)
        )

        etCount.addTextChangedListener { tilCount.error = null }
        etPerPause.addTextChangedListener { tilPerPause.error = null }
        etPerDay.addTextChangedListener { tilPerDay.error = null }

        fun parseField(
            field: TextInputEditText,
            layout: TextInputLayout,
            max: Int
        ): Int? {
            val raw = field.text?.toString()?.trim().orEmpty()
            if (raw.isBlank()) {
                layout.error = null
                return 0
            }
            val value = raw.toIntOrNull()
            if (value == null) {
                layout.error = activity.getString(R.string.app_limit_error_not_number)
                return null
            }
            if (value !in 0..max) {
                layout.error = activity.getString(R.string.temp_pause_error_range_fmt, max)
                return null
            }
            layout.error = null
            return value
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.temp_pause_title))
            .setView(content)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.temp_pause_reset_today, null)
            .create()

        dialog.setOnShowListener {
            dialog.styleSwitchlyDialogButtons()
            // Caps and usage cannot change while protection is active. Keep the
            // buttons tappable (dimmed) so the tap can warn instead of doing nothing.
            val locked = EditingLockGuard.isLocked(activity)
            val btnNeutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            btnNeutral.alpha = if (locked) 0.45f else 1f

            btnNeutral.setOnClickListener {
                if (EditingLockGuard.isLocked(activity)) {
                    content.showWarnPill(R.string.edit_locked_manage_temp_pauses)
                    return@setOnClickListener
                }

                fun doReset() {
                    TempPauseStore.resetToday(activity, profile)
                    tvUsage.text = activity.getString(R.string.temp_pause_used_fmt, 0, 0)
                    content.showWarnPill(R.string.temp_pause_reset_success)
                    onSaved?.invoke()
                }

                if (EmergencyPinStore.hasPin(activity)) {
                    EmergencyPinDialog.showEnterPin(activity) {
                        doReset()
                    }
                } else {
                    EmergencyPinDialog.showSetPin(activity) {
                        doReset()
                    }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (EditingLockGuard.isLocked(activity)) {
                    content.showWarnPill(R.string.edit_locked_manage_temp_pauses)
                    return@setOnClickListener
                }
                val count = parseField(etCount, tilCount, 100) ?: run {
                    etCount.requestFocus()
                    return@setOnClickListener
                }
                val perPause = parseField(etPerPause, tilPerPause, 1440) ?: run {
                    etPerPause.requestFocus()
                    return@setOnClickListener
                }
                val perDay = parseField(etPerDay, tilPerDay, 1440) ?: run {
                    etPerDay.requestFocus()
                    return@setOnClickListener
                }
                TempPauseStore.setCaps(
                    activity,
                    profile,
                    TempPauseStore.Caps(count, perPause, perDay)
                )
                onSaved?.invoke()
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
