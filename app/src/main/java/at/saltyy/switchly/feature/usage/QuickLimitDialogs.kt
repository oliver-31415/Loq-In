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

package at.saltyy.switchly.feature.usage

import android.content.res.ColorStateList
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import at.saltyy.switchly.R
import at.saltyy.switchly.blocking.BlockingRuntime
import at.saltyy.switchly.data.prefs.AttemptLimitStore
import at.saltyy.switchly.data.prefs.DomainBlockStore
import at.saltyy.switchly.data.prefs.DomainLimitStore
import at.saltyy.switchly.data.prefs.LimitReachedStore
import at.saltyy.switchly.data.prefs.OpenCountStore
import at.saltyy.switchly.data.prefs.ProfileRuleModeStore
import at.saltyy.switchly.data.prefs.ProfileStore
import at.saltyy.switchly.data.prefs.SessionLimitStore
import at.saltyy.switchly.data.prefs.UsageLimitStore
import at.saltyy.switchly.data.prefs.UsageLimitResetStore
import at.saltyy.switchly.data.prefs.UsageStore
import at.saltyy.switchly.theme.AccentColor
import at.saltyy.switchly.theme.CustomAccentApplier
import at.saltyy.switchly.ui.SwitchlyDropdownAdapter
import at.saltyy.switchly.ui.showWarnPill
import at.saltyy.switchly.ui.dialog.Dialogs
import at.saltyy.switchly.ui.dialog.showAccented
import at.saltyy.switchly.ui.dialog.applySwitchlyDialogWidth
import at.saltyy.switchly.util.AppBlockSafety
import at.saltyy.switchly.util.EditingLockGuard
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

/**
 * Quick-edit dialogs for app/website limits.
 * Goal: one consistent entry point (icon) to set either a time limit or an open-attempt limit.
 */
object QuickLimitDialogs {


    private const val MODE_TIME = 0
    private const val MODE_ATTEMPTS = 1
    private const val MODE_ALWAYS_BLOCK = 2
    private const val MAX_TIME_MINUTES = 24 * 60
    private const val MAX_ATTEMPTS = 200
    private const val STEP_TIME_MINUTES = 15
    private const val STEP_VISIT_MINUTES = 5
    private const val DEFAULT_TIME_MINUTES = 60
    private const val DEFAULT_OPENS = 5
    private const val DEFAULT_VISIT_MINUTES = 15

    fun showForApp(
        activity: AppCompatActivity,
        pkg: String,
        label: String,
        startOnAttempts: Boolean? = null,
        onChanged: (() -> Unit)? = null
    ) {
        val safety = AppBlockSafety.resolve(activity, pkg)
        when (safety.level) {
            AppBlockSafety.Level.PROTECTED -> {
                AlertDialog.Builder(activity)
                    .setTitle(safety.warningTitle ?: activity.getString(R.string.app_picker_protected_warning_title))
                    .setMessage(safety.warningMessage ?: activity.getString(R.string.app_picker_protected_warning_message))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.app_picker_block_protected_confirm) { _, _ ->
                        showForAppInternal(activity, pkg, label, startOnAttempts, onChanged)
                    }
                    .showAccented()
            }
            AppBlockSafety.Level.SOFT_WARNING -> {
                AlertDialog.Builder(activity)
                    .setTitle(safety.warningTitle ?: activity.getString(R.string.app_picker_protected_caution_title))
                    .setMessage(safety.warningMessage ?: safety.hint ?: activity.getString(R.string.app_picker_protected_generic_hint))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.continue_label) { _, _ ->
                        showForAppInternal(activity, pkg, label, startOnAttempts, onChanged)
                    }
                    .showAccented()
            }
            else -> showForAppInternal(activity, pkg, label, startOnAttempts, onChanged)
        }
    }

    private fun showForAppInternal(
        activity: AppCompatActivity,
        pkg: String,
        label: String,
        startOnAttempts: Boolean? = null,
        onChanged: (() -> Unit)? = null
    ) {
        if (EditingLockGuard.isLocked(activity)) {
            activity.findViewById<View>(android.R.id.content)
                .showWarnPill(R.string.toast_disable_switchly_to_edit_app_limits)
            return
        }

        val profile = ProfileStore.getCurrent(activity)
        if (profile.isNullOrBlank()) {
            activity.findViewById<View>(android.R.id.content).showWarnPill(R.string.no_profile_selected)
            return
        }

        showAppLimitEditor(
            activity = activity,
            profile = profile,
            pkg = pkg,
            label = label,
            focusAttempts = startOnAttempts == true,
            onChanged = onChanged,
        )
    }

    /**
     * App limits intentionally use one editor instead of hopping through separate Time / Attempts /
     * Session dialogs. This keeps combinations such as “4 opens/day + 8 min per visit” visible and editable as one atomic configuration.
     */
    private fun showAppLimitEditor(
        activity: AppCompatActivity,
        profile: String,
        pkg: String,
        label: String,
        focusAttempts: Boolean,
        onChanged: (() -> Unit)?,
    ) {
        val v = LayoutInflater.from(activity).inflate(R.layout.dialog_app_limits, FrameLayout(activity), false)
        val ivIcon = v.findViewById<android.widget.ImageView>(R.id.ivAppLimitIcon)
        val tvTitle = v.findViewById<TextView>(R.id.tvAppLimitTitle)
        val tvSubtitle = v.findViewById<TextView>(R.id.tvAppLimitSubtitle)
        val tvSentence = v.findViewById<TextView>(R.id.tvAppLimitSentence)
        val swTime = v.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swAppLimitTime)
        val swOpens = v.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swAppLimitOpens)
        val swVisit = v.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swAppLimitVisit)
        val tilTime = v.findViewById<TextInputLayout>(R.id.tilAppLimitTime)
        val etTime = v.findViewById<TextInputEditText>(R.id.etAppLimitTime)
        val tilAttempts = v.findViewById<TextInputLayout>(R.id.tilAppLimitAttempts)
        val etAttempts = v.findViewById<TextInputEditText>(R.id.etAppLimitAttempts)
        val tilPerVisit = v.findViewById<TextInputLayout>(R.id.tilAppLimitPerVisit)
        val etPerVisit = v.findViewById<TextInputEditText>(R.id.etAppLimitPerVisit)
        val tvVisitWarning = v.findViewById<TextView>(R.id.tvVisitWarning)
        val btnClear = v.findViewById<MaterialButton>(R.id.btnAppLimitClear)
        val btnCancel = v.findViewById<MaterialButton>(R.id.btnAppLimitCancel)
        val btnSave = v.findViewById<MaterialButton>(R.id.btnAppLimitSave)

        val currentTime = UsageLimitStore.getLimitMinutes(activity, profile, pkg)
        val currentAttempts = AttemptLimitStore.getLimitAttempts(activity, profile, pkg)
        val currentPerVisit = SessionLimitStore.getLimitMinutes(activity, profile, pkg)

        tvTitle.text = label
        tvSubtitle.text = activity.getString(R.string.profile_active_fmt, profile)
        runCatching {
            ivIcon.setImageDrawable(activity.packageManager.getApplicationIcon(pkg))
        }.onFailure {
            ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        fun fmtInt(value: Int): String =
            if (value > 0) String.format(Locale.getDefault(), "%d", value) else ""

        etTime.setText(fmtInt(currentTime))
        etAttempts.setText(fmtInt(currentAttempts))
        etPerVisit.setText(fmtInt(currentPerVisit))
        swTime.isChecked = currentTime > 0
        swOpens.isChecked = currentAttempts > 0
        swVisit.isChecked = currentPerVisit > 0

        val accent = AccentColor.getAccentColorInt(activity)
        val accentList = ColorStateList.valueOf(accent)
        listOf(tilTime, tilAttempts, tilPerVisit).forEach { til ->
            til.boxStrokeColor = accent
            til.hintTextColor = accentList
            til.defaultHintTextColor = accentList
        }

        btnCancel.setTextColor(accent)
        btnCancel.isAllCaps = false
        btnCancel.backgroundTintList = null
        runCatching { btnCancel.setBackgroundColor(android.graphics.Color.TRANSPARENT) }

        val error = android.graphics.Color.rgb(186, 26, 26)
        btnClear.setTextColor(error)
        btnClear.isAllCaps = false
        btnClear.backgroundTintList = null
        runCatching { btnClear.setBackgroundColor(android.graphics.Color.TRANSPARENT) }
        btnClear.visibility =
            if (currentTime > 0 || currentAttempts > 0 || currentPerVisit > 0) View.VISIBLE
            else View.GONE

        val onAccent = if (androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.5) {
            android.graphics.Color.BLACK
        } else {
            android.graphics.Color.WHITE
        }
        btnSave.setTextColor(onAccent)
        btnSave.isAllCaps = false
        btnSave.backgroundTintList = AccentColor.getActiveColor(activity)

        fun rawInt(field: TextInputEditText): Int =
            field.text?.toString()?.trim()?.toIntOrNull() ?: 0

        fun effectiveValues(): Triple<Int, Int, Int> {
            val time = if (swTime.isChecked) rawInt(etTime) else 0
            val opens = if (swOpens.isChecked) rawInt(etAttempts) else 0
            val visit = if (swVisit.isChecked) rawInt(etPerVisit) else 0
            return Triple(time, opens, visit)
        }

        fun refreshSentence() {
            val (time, opens, visit) = effectiveValues()
            tvSentence.text = when {
                time <= 0 && opens <= 0 && visit <= 0 ->
                    activity.getString(R.string.app_limit_sentence_none)
                time > 0 -> buildString {
                    append(activity.getString(R.string.app_limit_sentence_time_fmt, time))
                    if (opens > 0) append(activity.getString(R.string.app_limit_sentence_split_fmt, opens))
                    if (visit > 0) append(activity.getString(R.string.app_limit_sentence_visit_fmt, visit))
                }
                opens > 0 -> buildString {
                    append(activity.getString(R.string.app_limit_sentence_opens_only_fmt, opens))
                    if (visit > 0) append(activity.getString(R.string.app_limit_sentence_visit_fmt, visit))
                }
                else -> activity.getString(R.string.app_limit_sentence_visit_only_fmt, visit)
            }
            if (time > 0 && visit > time) {
                tvVisitWarning.text = activity.getString(
                    R.string.app_limit_visit_exceeds_daily_fmt, visit, time
                )
                tvVisitWarning.visibility = View.VISIBLE
            } else {
                tvVisitWarning.visibility = View.GONE
            }
        }

        fun parseField(
            enabled: Boolean,
            field: TextInputEditText,
            layout: TextInputLayout,
            max: Int,
            rangeError: String
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
                layout.error = rangeError
                return null
            }
            layout.error = null
            return value
        }

        fun validateAll(focusInvalid: Boolean): Triple<Int, Int, Int>? {
            val time = parseField(
                swTime.isChecked, etTime, tilTime, MAX_TIME_MINUTES,
                activity.getString(R.string.app_limit_error_range_minutes_fmt, MAX_TIME_MINUTES)
            )
            val visit = parseField(
                swVisit.isChecked, etPerVisit, tilPerVisit, MAX_TIME_MINUTES,
                activity.getString(R.string.app_limit_error_range_minutes_fmt, MAX_TIME_MINUTES)
            )
            val opens = parseField(
                swOpens.isChecked, etAttempts, tilAttempts, MAX_ATTEMPTS,
                activity.getString(R.string.app_limit_error_range_attempts_fmt, MAX_ATTEMPTS)
            )
            if (time == null || visit == null || opens == null) {
                if (focusInvalid) {
                    when {
                        time == null -> etTime.requestFocus()
                        visit == null -> etPerVisit.requestFocus()
                        else -> etAttempts.requestFocus()
                    }
                }
                return null
            }
            return Triple(time, visit, opens)
        }

        fun refreshSaveState() {
            val valid = validateAll(focusInvalid = false) != null
            btnSave.isEnabled = valid
            btnSave.alpha = if (valid) 1f else 0.5f
        }

        fun refreshAll() {
            refreshSentence()
            refreshSaveState()
        }

        // Clearing a field switches its limit off instead of leaving a stale
        // number behind an off toggle (or an error behind an on toggle).
        etTime.addTextChangedListener {
            if (swTime.isChecked && etTime.text?.toString()?.trim().isNullOrEmpty()) {
                swTime.isChecked = false
            } else {
                refreshAll()
            }
        }
        etAttempts.addTextChangedListener {
            if (swOpens.isChecked && etAttempts.text?.toString()?.trim().isNullOrEmpty()) {
                swOpens.isChecked = false
            } else {
                refreshAll()
            }
        }
        etPerVisit.addTextChangedListener {
            if (swVisit.isChecked && etPerVisit.text?.toString()?.trim().isNullOrEmpty()) {
                swVisit.isChecked = false
            } else {
                refreshAll()
            }
        }

        fun setTimeValue(value: Int) {
            etTime.setText(fmtInt(value))
            etTime.setSelection(etTime.text?.length ?: 0)
        }

        swTime.setOnCheckedChangeListener { _, checked ->
            if (checked && etTime.text?.toString()?.trim().isNullOrEmpty()) {
                setTimeValue(DEFAULT_TIME_MINUTES)
            }
            if (!checked) tilTime.error = null
            refreshAll()
        }
        swOpens.setOnCheckedChangeListener { _, checked ->
            if (checked && etAttempts.text?.toString()?.trim().isNullOrEmpty()) {
                etAttempts.setText(fmtInt(DEFAULT_OPENS))
                etAttempts.setSelection(etAttempts.text?.length ?: 0)
            }
            if (!checked) tilAttempts.error = null
            refreshAll()
        }
        swVisit.setOnCheckedChangeListener { _, checked ->
            if (checked && etPerVisit.text?.toString()?.trim().isNullOrEmpty()) {
                etPerVisit.setText(fmtInt(DEFAULT_VISIT_MINUTES))
                etPerVisit.setSelection(etPerVisit.text?.length ?: 0)
            }
            if (!checked) tilPerVisit.error = null
            refreshAll()
        }

        fun stepTime(delta: Int) {
            if (!swTime.isChecked && delta <= 0) return
            if (!swTime.isChecked) {
                setTimeValue(delta.coerceIn(1, MAX_TIME_MINUTES))
                swTime.isChecked = true
                return
            }
            val next = rawInt(etTime) + delta
            if (next < 1) {
                etTime.setText("")
                swTime.isChecked = false
                return
            }
            setTimeValue(next.coerceAtMost(MAX_TIME_MINUTES))
        }

        fun stepOpens(delta: Int) {
            if (!swOpens.isChecked && delta <= 0) return
            if (!swOpens.isChecked) {
                etAttempts.setText(fmtInt(delta.coerceIn(1, MAX_ATTEMPTS)))
                swOpens.isChecked = true
                return
            }
            val next = rawInt(etAttempts) + delta
            if (next < 1) {
                etAttempts.setText("")
                swOpens.isChecked = false
                return
            }
            etAttempts.setText(fmtInt(next.coerceAtMost(MAX_ATTEMPTS)))
        }

        fun stepVisit(delta: Int) {
            if (!swVisit.isChecked && delta <= 0) return
            if (!swVisit.isChecked) {
                etPerVisit.setText(fmtInt(delta.coerceIn(1, MAX_TIME_MINUTES)))
                swVisit.isChecked = true
                return
            }
            val next = rawInt(etPerVisit) + delta
            if (next < 1) {
                etPerVisit.setText("")
                swVisit.isChecked = false
                return
            }
            etPerVisit.setText(fmtInt(next.coerceAtMost(MAX_TIME_MINUTES)))
        }

        v.findViewById<MaterialButton>(R.id.btnTimeMinus).setOnClickListener { stepTime(-STEP_TIME_MINUTES) }
        v.findViewById<MaterialButton>(R.id.btnTimePlus).setOnClickListener { stepTime(STEP_TIME_MINUTES) }
        v.findViewById<MaterialButton>(R.id.btnOpensMinus).setOnClickListener { stepOpens(-1) }
        v.findViewById<MaterialButton>(R.id.btnOpensPlus).setOnClickListener { stepOpens(1) }
        v.findViewById<MaterialButton>(R.id.btnVisitMinus).setOnClickListener { stepVisit(-STEP_VISIT_MINUTES) }
        v.findViewById<MaterialButton>(R.id.btnVisitPlus).setOnClickListener { stepVisit(STEP_VISIT_MINUTES) }

        val pillValues = mapOf(
            R.id.pillTime15 to 15,
            R.id.pillTime30 to 30,
            R.id.pillTime60 to 60,
            R.id.pillTime120 to 120,
        )
        pillValues.forEach { (id, minutes) ->
            v.findViewById<MaterialButton>(id).setOnClickListener {
                setTimeValue(minutes)
                if (!swTime.isChecked) swTime.isChecked = true
            }
        }

        refreshAll()

        fun applyValues(timeMinutes: Int, attempts: Int, perVisitMinutes: Int) {
            UsageLimitStore.setLimitMinutes(activity, profile, pkg, timeMinutes)
            SessionLimitStore.setLimitMinutes(activity, profile, pkg, perVisitMinutes)
            AttemptLimitStore.setLimitAttempts(activity, profile, pkg, attempts)

            LimitReachedStore.clearToday(activity, pkg)
            // No reset-mode choice in the UI: keep whatever cadence is stored
            // (per-day default), only cleaning up when the time limit is removed.
            if (timeMinutes <= 0) {
                UsageLimitResetStore.clearMode(activity, profile, pkg)
                UsageStore.setUsageMsToday(activity, pkg, 0L)
            }
            if (attempts == 0) {
                OpenCountStore.setToday(activity, profile, pkg, 0)
            }
            if (timeMinutes > 0 || attempts > 0 || perVisitMinutes > 0) {
                ensureManaged(activity, profile, pkg)
            }

            BlockingRuntime.ensureRunning(activity)
            onChanged?.invoke()
        }

        val dlg = Dialogs.builder(activity)
            .setTitle(activity.getString(R.string.edit_limits))
            .setView(v)
            .create()

        btnClear.setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle(R.string.app_limit_remove_confirm_title)
                .setMessage(activity.getString(R.string.app_limit_remove_confirm_message, label))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.app_limit_remove) { _, _ ->
                    applyValues(0, 0, 0)
                    dlg.dismiss()
                }
                .showAccented()
        }
        btnCancel.setOnClickListener { dlg.dismiss() }
        btnSave.setOnClickListener {
            val validated = validateAll(focusInvalid = true) ?: return@setOnClickListener
            val (time, visit, opens) = validated
            applyValues(time, opens, visit)
            dlg.dismiss()
        }

        dlg.applySwitchlyDialogWidth(0.94f)
        dlg.setOnShowListener {
            runCatching { CustomAccentApplier.applyToDialog(dlg) }
            val focus = if (focusAttempts) etAttempts else etTime
            focus.post {
                focus.requestFocus()
                focus.setSelection(focus.text?.length ?: 0)
            }
        }
        dlg.show()
    }

    fun showForWebsite(activity: AppCompatActivity, domain: String, label: String, onChanged: (() -> Unit)? = null) {
        if (EditingLockGuard.isLocked(activity)) {
            activity.findViewById<View>(android.R.id.content)
                .showWarnPill(R.string.toast_disable_switchly_to_edit_websites)
            return
        }

        // Websites support: time limit OR always-block rule.
        val normalized = DomainBlockStore.normalize(domain) ?: domain
        val isAllowMode = ProfileStore.getCurrent(activity)?.let { ProfileRuleModeStore.isAllowMode(activity, it) } == true
        val isAlways = DomainBlockStore.getDomains(activity).contains(normalized)
        val current = DomainLimitStore.getLimitMinutes(activity, normalized)

        showCompactLimitDialog(
            activity = activity,
            title = activity.getString(R.string.edit_limits),
            subtitle = label,
            supportedModes = intArrayOf(MODE_TIME, MODE_ALWAYS_BLOCK),
            initialMode = if (isAlways) MODE_ALWAYS_BLOCK else MODE_TIME,
            initialValueProvider = { current }
        ) { mode, value, _ ->
            when (mode) {
                MODE_ALWAYS_BLOCK -> {
                    if (value > 0) {
                        DomainLimitStore.clear(activity, normalized)
                        DomainBlockStore.addDomain(activity, normalized)
                    } else {
                        // Clear always-block
                        DomainBlockStore.removeDomain(activity, normalized)
                    }
                }

                else -> {
                    val m = value.coerceAtLeast(0)
                    if (m <= 0) {
                        DomainLimitStore.clear(activity, normalized)
                        DomainBlockStore.removeDomain(activity, normalized)
                    } else {
                        if (isAllowMode) {
                            DomainBlockStore.addDomain(activity, normalized)
                        } else {
                            DomainBlockStore.removeDomain(activity, normalized)
                        }
                        DomainLimitStore.setLimitMinutes(activity, normalized, m)
                    }
                }
            }

            BlockingRuntime.ensureRunning(activity)
            onChanged?.invoke()
        }
    }
    private fun ensureManaged(activity: AppCompatActivity, profile: String, pkg: String) {
        if (AppBlockSafety.isAlwaysExcluded(activity, pkg)) {
            return
        }
        val selected = ProfileStore.getSelectedForProfileMode(activity, profile).toMutableSet()
        if (!selected.contains(pkg)) {
            selected.add(pkg)
            ProfileStore.setSelectedForProfileMode(activity, profile, selected)
        }
    }


    private fun showCompactLimitDialog(
        activity: AppCompatActivity,
        title: String,
        subtitle: CharSequence? = null,
        supportedModes: IntArray,
        initialMode: Int,
        initialValueProvider: (mode: Int) -> Int,
        showTimeResetMode: Boolean = false,
        initialResetMode: String = UsageLimitResetStore.MODE_DAY,
        onApply: (mode: Int, value: Int, resetMode: String?) -> Unit
    ) {
        val v = LayoutInflater.from(activity).inflate(R.layout.dialog_quick_limit_compact, FrameLayout(activity), false)
        val tilType = v.findViewById<TextInputLayout>(R.id.tilType)
        val tvSubtitle = v.findViewById<TextView>(R.id.tvLimitSubtitle)
        val tvModeSummary = v.findViewById<TextView>(R.id.tvLimitModeSummary)
        val etType = v.findViewById<MaterialAutoCompleteTextView>(R.id.etType)
        val tilValue = v.findViewById<TextInputLayout>(R.id.tilValue)
        val etValue = v.findViewById<TextInputEditText>(R.id.etValue)
        val tilResetMode = v.findViewById<TextInputLayout>(R.id.tilResetMode)
        val etResetMode = v.findViewById<MaterialAutoCompleteTextView>(R.id.etResetMode)

        // Ensure the dialog matches the currently selected accent (including custom colors).
        val accent = AccentColor.getAccentColorInt(activity)
        val accentList = ColorStateList.valueOf(accent)
        fun accentInputs() {
            // Outlined box stroke + hints + dropdown icon
            tilValue.boxStrokeColor = accent
            tilValue.hintTextColor = accentList
            tilValue.defaultHintTextColor = accentList

            tilType.boxStrokeColor = accent
            tilType.hintTextColor = accentList
            tilType.defaultHintTextColor = accentList
            tilType.setEndIconTintList(accentList)

            tilResetMode.boxStrokeColor = accent
            tilResetMode.hintTextColor = accentList
            tilResetMode.defaultHintTextColor = accentList
            tilResetMode.setEndIconTintList(accentList)
        }
        accentInputs()
        tvSubtitle.text = subtitle?.toString() ?: ""
        tvSubtitle.visibility = if (subtitle.isNullOrBlank()) View.GONE else View.VISIBLE

        val modeLabels = supportedModes.map { mode ->
            when (mode) {
                MODE_ATTEMPTS -> activity.getString(R.string.limit_attempts_action)
                MODE_ALWAYS_BLOCK -> activity.getString(R.string.rule_block_always)
                else -> activity.getString(R.string.limit_time_action)
            }
        }

        val resetModes = listOf(UsageLimitResetStore.MODE_DAY, UsageLimitResetStore.MODE_SESSION)
        val resetLabels = listOf(
            activity.getString(R.string.limit_reset_per_day),
            activity.getString(R.string.limit_reset_per_session)
        )
        val activeResetMode = arrayOf(
            when (initialResetMode) {
                UsageLimitResetStore.MODE_SESSION -> UsageLimitResetStore.MODE_SESSION
                else -> UsageLimitResetStore.MODE_DAY
            }
        )
        etResetMode.setAdapter(SwitchlyDropdownAdapter(activity, resetLabels))
        etResetMode.setText(resetLabels[resetModes.indexOf(activeResetMode[0]).coerceAtLeast(0)], false)
        etResetMode.setOnItemClickListener { _, _, position, _ ->
            activeResetMode[0] = resetModes.getOrNull(position) ?: UsageLimitResetStore.MODE_DAY
        }

        etType.setAdapter(SwitchlyDropdownAdapter(activity, modeLabels))
        if (supportedModes.size <= 1) tilType.visibility = View.GONE

        fun applyMode(mode: Int, keepTypedValue: Boolean) {
            val typed = etValue.text?.toString()?.trim().orEmpty()
            val fromStore = initialValueProvider(mode).let { if (it == 0) "" else it.toString() }
            val nextText = if (keepTypedValue && typed.isNotBlank()) typed else fromStore

            if (mode == MODE_ALWAYS_BLOCK) {
                tilValue.visibility = View.GONE
                tilResetMode.visibility = View.GONE
                etValue.setText("")
                tvModeSummary.setText(R.string.limit_always_block_action_summary)
                return
            }

            tilValue.visibility = View.VISIBLE
            tilResetMode.visibility = if (showTimeResetMode && mode == MODE_TIME) View.VISIBLE else View.GONE
            tvModeSummary.setText(
                when (mode) {
                    MODE_ATTEMPTS -> R.string.limit_attempts_action_summary
                    else -> R.string.limit_time_action_summary
                }
            )
            tilValue.hint = when (mode) {
                MODE_ATTEMPTS -> activity.getString(R.string.opens_hint)
                else -> activity.getString(R.string.minutes_hint_day)
            }
            etValue.inputType = InputType.TYPE_CLASS_NUMBER
            etValue.setText(nextText)
            etValue.setSelection(etValue.text?.length ?: 0)
        }

        val initialIdx = supportedModes.indexOf(initialMode).takeIf { it >= 0 } ?: 0
        val activeMode = intArrayOf(supportedModes[initialIdx])
        etType.setText(modeLabels[initialIdx], false)
        applyMode(activeMode[0], keepTypedValue = false)

        etType.setOnItemClickListener { _, _, position, _ ->
            activeMode[0] = supportedModes[position]
            applyMode(activeMode[0], keepTypedValue = true)
        }

        // Use custom in-view MaterialButtons so dialogs match Switchly's button styling.
        val dlg = Dialogs.builder(activity)
            .setTitle(title)
            .setView(v)
            .create()

        val btnClear = v.findViewById<MaterialButton>(R.id.btnClear)
        val btnCancel = v.findViewById<MaterialButton>(R.id.btnCancel)
        val btnOk = v.findViewById<MaterialButton>(R.id.btnOk)

        // Match the global dialog button design:
        // - OK = filled accent
        // - Cancel/Clear = text-only accent
        val onAccent = if (androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.5) android.graphics.Color.BLACK else android.graphics.Color.WHITE

        btnCancel.setTextColor(accent)
        btnCancel.isAllCaps = false
        btnCancel.backgroundTintList = null
        runCatching { btnCancel.setBackgroundColor(android.graphics.Color.TRANSPARENT) }

        val error = android.graphics.Color.rgb(186, 26, 26)
        btnClear.setTextColor(error)
        btnClear.isAllCaps = false
        btnClear.backgroundTintList = null
        runCatching { btnClear.setBackgroundColor(android.graphics.Color.TRANSPARENT) }

        btnOk.setTextColor(onAccent)
        btnOk.isAllCaps = false
        // Prefer active accent tint for proper state handling.
        btnOk.backgroundTintList = AccentColor.getActiveColor(activity)

        btnClear.setOnClickListener {
            onApply(activeMode[0], 0, activeResetMode[0])
            dlg.dismiss()
        }
        btnCancel.setOnClickListener { dlg.dismiss() }
        btnOk.setOnClickListener {
            if (activeMode[0] == MODE_ALWAYS_BLOCK) {
                // Pass a non-zero sentinel to mean "enable always block".
                onApply(activeMode[0], 1, activeResetMode[0])
                dlg.dismiss()
                return@setOnClickListener
            }

            val raw = etValue.text?.toString()?.trim().orEmpty()
            val n = raw.toIntOrNull() ?: 0
            val max = when (activeMode[0]) {
                MODE_ATTEMPTS -> 200
                else -> 24 * 60
            }
            if (n < 0 || n > max) {
                etValue.showWarnPill(R.string.invalid_value)
                return@setOnClickListener
            }
            onApply(activeMode[0], n, activeResetMode[0])
            dlg.dismiss()
        }

        dlg.applySwitchlyDialogWidth(0.94f)
        dlg.setOnShowListener {
            // Retint in CUSTOM accent mode so the dialog matches the rest of the app.
            runCatching { CustomAccentApplier.applyToDialog(dlg) }
        }
        dlg.show()
    }
}
