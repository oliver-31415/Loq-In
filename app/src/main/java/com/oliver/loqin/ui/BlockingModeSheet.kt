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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.oliver.loqin.R
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.data.prefs.AutomationModeStore
import com.oliver.loqin.data.prefs.ScanCodeStore
import com.oliver.loqin.feature.qr.QrGenerateActivity
import com.oliver.loqin.feature.schedule.SchedulesActivity
import com.oliver.loqin.feature.settings.ManageBarcodesActivity
import com.oliver.loqin.nfc.NfcWriterActivity
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.dialog.padForNavigationBar
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.util.EditingLockGuard
import com.oliver.loqin.util.LoqInAppAccessGuard
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Foqos strategy-picker equivalent: choose how Loq In may be changed.
 *
 * Shared by the Home hero card and the onboarding "Controls" step so both show
 * the same modern sheet. [onModeChanged] lets the host refresh its own summary
 * (e.g. the Home hero badge).
 */
object BlockingModeSheet {

    fun show(
        activity: Activity,
        onModeChanged: (AutomationModeStore.Mode) -> Unit = {},
    ) {
        val sheet = BottomSheetDialog(activity)
        // Taller than most screens: scroll inside the expanded sheet.
        sheet.setContentView(
            androidx.core.widget.NestedScrollView(activity).apply {
                setBackgroundResource(R.drawable.sheet_profile_bg)
                addView(
                    buildContent(
                        activity = activity,
                        includeHeader = true,
                        onClose = { sheet.dismiss() },
                        onModeChanged = onModeChanged,
                    )
                )
            }
        )
        // Tall sheet: open expanded so as many modes as possible are visible without dragging.
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        sheet.padForNavigationBar()
        sheet.show()
    }

    /** Full-page variant (onboarding) with the same rows but no sheet chrome. */
    fun buildPageContent(
        activity: Activity,
        onModeChanged: (AutomationModeStore.Mode) -> Unit = {},
    ): LinearLayout {
        val content = buildContent(
            activity = activity,
            includeHeader = false,
            onClose = null,
            onModeChanged = onModeChanged,
        )
        // The page layout already applies the horizontal page padding; without
        // this the rows would be inset twice and not fill the page width.
        content.setPadding(0, content.paddingTop, 0, content.paddingBottom)
        return content
    }

    private fun buildContent(
        activity: Activity,
        includeHeader: Boolean,
        onClose: (() -> Unit)?,
        onModeChanged: (AutomationModeStore.Mode) -> Unit,
    ): LinearLayout {
        val onSurfaceSoft = ContextCompat.getColor(activity, R.color.foqos_on_surface_variant)
        val accent = AccentColor.getAccentColorInt(activity)

        fun dp(value: Float): Int = SheetRows.dp(activity, value)

        fun isNfcTagWritingLocked(): Boolean =
            EditingLockGuard.isLocked(activity) &&
                !AutomationModeStore.isNfcTagWritingAllowedWhileEnabled(activity)

        fun editNfc() {
            if (isNfcTagWritingLocked()) {
                EditingLockGuard.showLockedDialog(activity, R.string.edit_locked_write_nfc_tags)
            } else {
                activity.startActivity(Intent(activity, NfcWriterActivity::class.java))
            }
        }

        fun editBarcode() {
            if (EditingLockGuard.isLocked(activity)) {
                EditingLockGuard.showLockedDialog(activity, R.string.edit_locked_manage_barcodes)
            } else {
                activity.startActivity(
                    Intent(activity, ManageBarcodesActivity::class.java)
                        .putExtra(ManageBarcodesActivity.EXTRA_FORCE_ALLOW, true)
                )
            }
        }

        fun editQr() {
            if (EditingLockGuard.isLocked(activity)) {
                EditingLockGuard.showLockedDialog(activity, R.string.edit_locked_manage_qr_codes)
            } else {
                activity.startActivity(
                    Intent(activity, QrGenerateActivity::class.java)
                        .putExtra(QrGenerateActivity.EXTRA_FORCE_ALLOW, true)
                )
            }
        }

        fun editSchedule() {
            openRulesDestination(activity, Intent(activity, SchedulesActivity::class.java))
        }

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16f)
            setPadding(pad, if (includeHeader) dp(8f) else dp(4f), pad, dp(22f))
            if (includeHeader) setBackgroundResource(R.drawable.sheet_profile_bg)
        }

        if (includeHeader) {
            list.addView(
                SheetRows.header(activity, list, activity.getString(R.string.blocking_mode_title)) {
                    onClose?.invoke()
                }
            )
        }
        // Sheets get tonal segments; the onboarding page sits on the page background.
        val mixedGroup = SheetRows.group(activity, list, onSurface = includeHeader)
        val singleGroup = SheetRows.group(activity, list, onSurface = includeHeader)

        var current = AutomationModeStore.getMode(activity)


        val modeRows = mutableListOf<Pair<AutomationModeStore.Mode, SheetRows.Row>>()

        fun modeRow(
            mode: AutomationModeStore.Mode,
            nameRes: Int,
            descRes: Int,
            iconRes: Int,
            supported: Boolean,
            parent: LinearLayout,
            onEdit: (() -> Unit)? = null,
        ): SheetRows.Row {
            val row = SheetRows.row(
                activity,
                parent,
                iconRes,
                activity.getString(nameRes),
                if (supported) activity.getString(descRes) else activity.getString(R.string.blocking_mode_unsupported),
                accent,
            )
            row.root.isClickable = supported
            row.root.isFocusable = supported
            row.root.alpha = if (supported) 1f else 0.4f
            if (onEdit != null && supported) {
                row.trailing.addView(SheetRows.editButton(activity, accent, onEdit))
            }
            row.setSelected(mode == current, accent)
            modeRows += mode to row
            return row
        }

        // ---- Mixed mode: expands an inline channel sub-menu ----
        val mixedRow = modeRow(
            AutomationModeStore.Mode.MIXED,
            R.string.blocking_mode_mixed,
            R.string.blocking_mode_mixed_desc,
            R.drawable.security_24,
            supported = true,
            parent = mixedGroup,
        ).root
        // The channels are rows of the Mixed group itself (segments follow visibility).
        val mixedSubmenu = mutableListOf<View>()

        var mixedExpanded = true
        val mixedChannelsArrow = ImageView(activity).apply {
            setImageResource(R.drawable.keyboard_arrow_right_24)
            setColorFilter(onSurfaceSoft)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val mixedChannelsHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48f)
            setPadding(dp(20f), dp(8f), dp(16f), dp(8f))
            isClickable = true
            isFocusable = true
            addView(TextView(activity).apply {
                text = activity.getString(R.string.toggle_section_mixed_channels)
                setTextAppearance(R.style.TextAppearance_LoqIn_RowSummary)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(mixedChannelsArrow)
        }

        fun applyMixedChannelsVisibility() {
            val mixedOn = current == AutomationModeStore.Mode.MIXED
            mixedChannelsHeader.isVisible = mixedOn
            mixedSubmenu.forEach { it.isVisible = mixedOn && mixedExpanded && it.tag != HIDDEN_TAG }
            androidx.core.view.ViewCompat.setStateDescription(
                mixedChannelsHeader,
                activity.getString(if (mixedExpanded) R.string.a11y_state_expanded else R.string.a11y_state_collapsed),
            )
            mixedChannelsArrow.animate()
                .rotation(if (mixedExpanded) 90f else 0f)
                .setDuration(140)
                .start()
        }
        mixedChannelsHeader.setOnClickListener {
            mixedExpanded = !mixedExpanded
            applyMixedChannelsVisibility()
        }

        fun channelSwitchRow(
            iconRes: Int,
            titleRes: Int,
            summaryRes: Int,
            supported: Boolean,
            getter: () -> Boolean,
            setter: (Boolean) -> Unit,
            onChanged: ((Boolean) -> Unit)? = null,
            indent: Boolean = false,
            onEdit: (() -> Unit)? = null,
        ): View {
            val switch = MaterialSwitch(activity).apply {
                isChecked = getter()
                isEnabled = supported
                thumbIconDrawable = ContextCompat.getDrawable(activity, R.drawable.loqin_switch_thumb_icon)
                CustomAccentApplier.tintSwitch(this)
            }
            val row = SheetRows.row(
                activity,
                mixedGroup,
                iconRes,
                activity.getString(titleRes),
                activity.getString(summaryRes),
                accent,
            )
            row.root.alpha = if (supported) 1f else 0.4f
            row.root.isClickable = supported
            if (indent) row.root.setPaddingRelative(dp(44f), row.root.paddingTop, row.root.paddingEnd, row.root.paddingBottom)
            row.root.setOnClickListener { switch.toggle() }
            var suppressSwitchListener = false
            switch.setOnCheckedChangeListener { _, checked ->
                if (suppressSwitchListener) return@setOnCheckedChangeListener
                if (checked && !supported) {
                    suppressSwitchListener = true
                    switch.isChecked = false
                    suppressSwitchListener = false
                    return@setOnCheckedChangeListener
                }
                // Mixed-channel toggles are protection-sensitive too; keep them locked while active.
                if (LoqInAppAccessGuard.isControlSettingsLocked(activity)) {
                    suppressSwitchListener = true
                    switch.isChecked = !checked
                    suppressSwitchListener = false
                    row.root.showWarnPill(R.string.mixed_channels_locked_while_loqin_enabled)
                    return@setOnCheckedChangeListener
                }
                setter(checked)
                onChanged?.invoke(checked)
            }
            if (onEdit != null && supported) {
                row.trailing.addView(SheetRows.editButton(activity, accent, onEdit))
            }
            row.trailing.addView(switch)
            mixedSubmenu += row.root
            return row.root
        }

        mixedGroup.addView(mixedRow)
        mixedGroup.addView(mixedChannelsHeader)

        // Manual controls: when full control is OFF, buttons/tiles may still ENABLE
        // protection — the nested "enable-only" switch mirrors Feature access.
        var enableOnlyRow: View? = null
        mixedGroup.addView(
            channelSwitchRow(
                R.drawable.security_24,
                R.string.pref_mixed_allow_button_title,
                R.string.pref_mixed_allow_button_summary,
                supported = true,
                getter = { AutomationModeStore.isMixedAllowButton(activity) },
                setter = { AutomationModeStore.setMixedAllowButton(activity, it) },
                onChanged = { manualAllowed ->
                    enableOnlyRow?.tag = if (manualAllowed) HIDDEN_TAG else null
                    applyMixedChannelsVisibility()
                },
            )
        )
        enableOnlyRow = channelSwitchRow(
            R.drawable.lock_24,
            R.string.pref_allow_button_enable_title,
            R.string.pref_allow_button_enable_summary,
            supported = true,
            getter = { AutomationModeStore.isButtonEnableAllowed(activity) },
            setter = { AutomationModeStore.setButtonEnableAllowed(activity, it) },
            indent = true,
        ).also {
            if (AutomationModeStore.isMixedAllowButton(activity)) it.tag = HIDDEN_TAG
            mixedGroup.addView(it)
        }
        mixedGroup.addView(channelSwitchRow(
            R.drawable.schedule_24,
            R.string.pref_mixed_allow_schedule_title,
            R.string.pref_mixed_allow_schedule_summary,
            supported = true,
            getter = { AutomationModeStore.isMixedAllowSchedule(activity) },
            setter = { AutomationModeStore.setMixedAllowSchedule(activity, it) },
            onEdit = ::editSchedule,
        ))
        mixedGroup.addView(channelSwitchRow(
            R.drawable.nfc_24,
            R.string.pref_mixed_allow_nfc_title,
            R.string.pref_mixed_allow_nfc_summary,
            supported = AutomationModeStore.isNfcSupported(activity),
            getter = { AutomationModeStore.isMixedAllowNfc(activity) },
            setter = { AutomationModeStore.setMixedAllowNfc(activity, it) },
            onEdit = ::editNfc,
        ))
        mixedGroup.addView(channelSwitchRow(
            R.drawable.qr_code_24,
            R.string.pref_mixed_allow_qr_title,
            R.string.pref_mixed_allow_qr_summary,
            supported = AutomationModeStore.isCameraSupported(activity),
            getter = { AutomationModeStore.isMixedAllowQr(activity) },
            setter = { AutomationModeStore.setMixedAllowQr(activity, it) },
            onEdit = ::editQr,
        ))
        mixedGroup.addView(channelSwitchRow(
            R.drawable.barcode_24,
            R.string.pref_mixed_allow_barcode_title,
            R.string.pref_mixed_allow_barcode_summary,
            supported = AutomationModeStore.isCameraSupported(activity),
            getter = { AutomationModeStore.isMixedAllowBarcode(activity) },
            setter = { AutomationModeStore.setMixedAllowBarcode(activity, it) },
            onEdit = ::editBarcode,
        ))

        // A scan-only mode without a code is a lockout waiting to happen: ask for the code now.
        fun promptScanSetupIfMissing(mode: AutomationModeStore.Mode) {
            val (titleRes, messageRes, actionRes, onSetup) = when {
                mode == AutomationModeStore.Mode.QR && !AutomationModeStore.hasQrDisableCode(activity) ->
                    ScanSetupPrompt(
                        R.string.blocking_mode_qr_setup_title,
                        R.string.blocking_mode_qr_setup_message,
                        R.string.blocking_mode_qr_setup_action,
                        ::editQr,
                    )
                mode == AutomationModeStore.Mode.BARCODE &&
                    !ScanCodeStore.hasEntries(activity, ScanCodeStore.Kind.BARCODE) ->
                    ScanSetupPrompt(
                        R.string.blocking_mode_barcode_setup_title,
                        R.string.blocking_mode_barcode_setup_message,
                        R.string.blocking_mode_barcode_setup_action,
                        ::editBarcode,
                    )
                else -> return
            }
            AlertDialog.Builder(activity)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(actionRes) { _, _ -> onSetup() }
                .setNegativeButton(R.string.not_now, null)
                .showAccented()
        }

        fun selectMode(mode: AutomationModeStore.Mode, anchor: View? = null) {
            if (mode == current) return
            // Changing the control mode is a protection-sensitive edit: it must go
            // through the same lock as Settings so it cannot be swapped while active.
            if (LoqInAppAccessGuard.isControlSettingsLocked(activity)) {
                (anchor ?: list).showWarnPill(R.string.mode_switch_requires_loqin_disabled)
                return
            }
            current = mode
            AutomationModeStore.setMode(activity, mode)
            onModeChanged(mode)
            modeRows.forEach { (m, row) -> row.setSelected(m == mode, accent) }
            applyMixedChannelsVisibility()
            promptScanSetupIfMissing(mode)
        }

        mixedRow.setOnClickListener { selectMode(AutomationModeStore.Mode.MIXED, mixedRow) }
        list.addView(mixedGroup)

        // ---- Single-channel modes: select and close ----
        data class SingleModeSpec(
            val mode: AutomationModeStore.Mode,
            val iconRes: Int,
            val nameRes: Int,
            val descRes: Int,
            val onEdit: (() -> Unit)?,
        )

        val singleModes = listOf(
            SingleModeSpec(
                AutomationModeStore.Mode.NFC,
                R.drawable.nfc_24,
                R.string.blocking_mode_nfc,
                R.string.blocking_mode_nfc_desc,
                ::editNfc,
            ),
            SingleModeSpec(
                AutomationModeStore.Mode.QR,
                R.drawable.qr_code_24,
                R.string.blocking_mode_qr,
                R.string.blocking_mode_qr_desc,
                ::editQr,
            ),
            SingleModeSpec(
                AutomationModeStore.Mode.BARCODE,
                R.drawable.barcode_24,
                R.string.blocking_mode_barcode,
                R.string.blocking_mode_barcode_desc,
                ::editBarcode,
            ),
            SingleModeSpec(
                AutomationModeStore.Mode.SCHEDULE,
                R.drawable.schedule_24,
                R.string.blocking_mode_schedule,
                R.string.blocking_mode_schedule_desc,
                ::editSchedule,
            ),
        )
        singleModes.forEach { spec ->
            val row = modeRow(
                spec.mode,
                spec.nameRes,
                spec.descRes,
                spec.iconRes,
                supported = AutomationModeStore.isModeSupported(activity, spec.mode),
                parent = singleGroup,
                onEdit = spec.onEdit,
            ).root
            row.setOnClickListener {
                if (!AutomationModeStore.isModeSupported(activity, spec.mode)) {
                    return@setOnClickListener
                }
                selectMode(spec.mode, row)
            }
            singleGroup.addView(row)
        }
        list.addView(singleGroup)

        applyMixedChannelsVisibility()

        return list
    }

    private const val HIDDEN_TAG = "hidden_channel"

    private data class ScanSetupPrompt(
        val titleRes: Int,
        val messageRes: Int,
        val actionRes: Int,
        val onSetup: () -> Unit,
    )

    private fun openRulesDestination(activity: Activity, intent: Intent) {
        if (!EditingLockGuard.isLocked(activity) || EditingLockGuard.isSuppressed(activity)) {
            activity.startActivity(intent)
            return
        }

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.loqin_rules_locked_title)
            .setMessage(R.string.rules_restricted_open_message)
        val persistChoice = EditingLockGuard.addDontShowAgain(builder, activity)
        builder.setPositiveButton(R.string.rules_open_restricted) { _, _ ->
                persistChoice()
                activity.startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .showAccented()
    }

    private fun Int.resId(ctx: Context): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(this, tv, true)
        return tv.resourceId
    }
}
