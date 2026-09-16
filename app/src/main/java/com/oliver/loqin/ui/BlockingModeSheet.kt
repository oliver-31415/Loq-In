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
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.AutomationModeStore
import com.oliver.loqin.feature.qr.QrGenerateActivity
import com.oliver.loqin.feature.schedule.SchedulesActivity
import com.oliver.loqin.feature.settings.ManageBarcodesActivity
import com.oliver.loqin.nfc.NfcWriterActivity
import com.oliver.loqin.theme.AccentColor
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
        sheet.setContentView(
            buildContent(
                activity = activity,
                includeHeader = true,
                onClose = { sheet.dismiss() },
                onModeChanged = onModeChanged,
            )
        )
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
        val onSurface = ContextCompat.getColor(activity, R.color.foqos_on_surface)
        val onSurfaceSoft = ContextCompat.getColor(activity, R.color.foqos_on_surface_variant)
        val accent = AccentColor.getAccentColorInt(activity)
        val surfaceVariant = ContextCompat.getColor(activity, R.color.foqos_surface_variant)

        fun dp(value: Float): Int =
            (value * activity.resources.displayMetrics.density + 0.5f).toInt()

        fun rowBg(selected: Boolean): GradientDrawable = GradientDrawable().apply {
            cornerRadius = dp(16f).toFloat()
            setColor(
                if (selected) ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x18), surfaceVariant)
                else surfaceVariant
            )
            setStroke(dp(2f), if (selected) accent else Color.TRANSPARENT)
        }

        fun roundelBg(): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(activity, R.color.foqos_surface))
        }

        fun createEditButton(onClick: () -> Unit, sizeDp: Float = 34f, iconSizeDp: Float = 17f): View {
            return FrameLayout(activity).apply {
                val base = roundelBg()
                val mask = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                background = RippleDrawable(
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x33)),
                    base,
                    mask,
                )
                isClickable = true
                isFocusable = true
                contentDescription = activity.getString(R.string.edit)
                layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply {
                    marginStart = dp(6f)
                }
                addView(ImageView(activity).apply {
                    setImageResource(R.drawable.edit_24)
                    setColorFilter(accent)
                    layoutParams = FrameLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)).apply {
                        gravity = Gravity.CENTER
                    }
                })
                setOnClickListener { onClick() }
            }
        }

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
            if (includeHeader) {
                setBackgroundColor(ContextCompat.getColor(activity, R.color.foqos_surface))
            }
        }

        if (includeHeader) {
            // Grab handle
            list.addView(View(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(2f).toFloat()
                    setColor(ColorUtils.setAlphaComponent(onSurfaceSoft, 0x61))
                }
                layoutParams = LinearLayout.LayoutParams(dp(36f), dp(4f)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            })

            // Title + close
            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12f), 0, dp(4f))
            }
            header.addView(TextView(activity).apply {
                text = activity.getString(R.string.blocking_mode_title)
                textSize = 22f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(onSurface)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            header.addView(FrameLayout(activity).apply {
                background = roundelBg()
                isClickable = true
                isFocusable = true
                setOnClickListener { onClose?.invoke() }
                addView(TextView(activity).apply {
                    text = "\u2715"
                    textSize = 14f
                    setTextColor(onSurface)
                    layoutParams = FrameLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER }
                })
                layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f))
            })
            list.addView(header)
        }

        var current = AutomationModeStore.getMode(activity)

        val modeRowViews = mutableListOf<Pair<AutomationModeStore.Mode, View>>()

        fun modeRow(
            mode: AutomationModeStore.Mode,
            nameRes: Int,
            descRes: Int,
            iconRes: Int,
            supported: Boolean,
            onEdit: (() -> Unit)? = null,
        ): LinearLayout {
            val isSelected = mode == current
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10f), dp(13f), dp(10f), dp(13f))
                background = rowBg(isSelected)
                isClickable = supported
                isFocusable = supported
                alpha = if (supported) 1f else 0.4f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            row.addView(FrameLayout(activity).apply {
                background = roundelBg()
                addView(ImageView(activity).apply {
                    setImageResource(iconRes)
                    setColorFilter(accent)
                    layoutParams = FrameLayout.LayoutParams(dp(19f), dp(19f)).apply {
                        gravity = Gravity.CENTER
                    }
                })
                layoutParams = LinearLayout.LayoutParams(dp(38f), dp(38f))
            })
            val texts = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12f)
                }
            }
            texts.addView(TextView(activity).apply {
                text = activity.getString(nameRes)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(onSurface)
            })
            texts.addView(TextView(activity).apply {
                text = if (supported) activity.getString(descRes)
                else activity.getString(R.string.blocking_mode_unsupported)
                textSize = 12f
                setTextColor(onSurfaceSoft)
            })
            row.addView(texts)

            if (onEdit != null && supported) {
                row.addView(createEditButton(onEdit, sizeDp = 34f, iconSizeDp = 17f))
            }

            modeRowViews += mode to row
            return row
        }

        // ---- Mixed mode: expands an inline channel sub-menu ----
        val mixedRow = modeRow(
            AutomationModeStore.Mode.MIXED,
            R.string.blocking_mode_mixed,
            R.string.blocking_mode_mixed_desc,
            R.drawable.security_24,
            supported = true,
        )
        val mixedSubmenu = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8f) }
        }

        var mixedExpanded = true
        val mixedChannelsArrow = ImageView(activity).apply {
            setImageResource(R.drawable.keyboard_arrow_right_24)
            setColorFilter(onSurfaceSoft)
        }

        fun applyMixedChannelsVisibility() {
            val showChannels = current == AutomationModeStore.Mode.MIXED && mixedExpanded
            mixedSubmenu.isVisible = showChannels
            mixedChannelsArrow.animate()
                .rotation(if (mixedExpanded) 90f else 0f)
                .setDuration(140)
                .start()
        }

        val mixedChannelsHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(8f), dp(10f), dp(4f))
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.attr.selectableItemBackground.resId(activity))
            addView(TextView(activity).apply {
                text = activity.getString(R.string.toggle_section_mixed_channels)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(onSurfaceSoft)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(mixedChannelsArrow)
            setOnClickListener {
                mixedExpanded = !mixedExpanded
                applyMixedChannelsVisibility()
            }
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
            into: LinearLayout = mixedSubmenu,
            onEdit: (() -> Unit)? = null,
        ) {
            val switch = MaterialSwitch(activity).apply {
                isChecked = getter()
                isEnabled = supported
                alpha = if (supported) 1f else 0.4f
                thumbTintList = ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)),
                    intArrayOf(Color.WHITE, Color.WHITE),
                )
                trackTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(-android.R.attr.state_checked),
                        intArrayOf(android.R.attr.state_checked),
                    ),
                    intArrayOf(
                        ColorUtils.setAlphaComponent(onSurfaceSoft, 0x55),
                        ColorUtils.setAlphaComponent(accent, 0x88),
                    ),
                )
            }
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
                alpha = if (supported) 1f else 0.4f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = if (indent) dp(14f) else 0
                }
                setOnClickListener { switch.toggle() }
            }
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
                    row.showWarnPill(R.string.mixed_channels_locked_while_loqin_enabled)
                    return@setOnCheckedChangeListener
                }
                setter(checked)
                onChanged?.invoke(checked)
            }
            row.addView(FrameLayout(activity).apply {
                background = roundelBg()
                addView(ImageView(activity).apply {
                    setImageResource(iconRes)
                    setColorFilter(if (supported) accent else onSurfaceSoft)
                    layoutParams = FrameLayout.LayoutParams(dp(17f), dp(17f)).apply {
                        gravity = Gravity.CENTER
                    }
                })
                layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f))
            })
            val texts = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12f)
                }
            }
            texts.addView(TextView(activity).apply {
                text = activity.getString(titleRes)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(onSurface)
            })
            texts.addView(TextView(activity).apply {
                text = activity.getString(summaryRes)
                textSize = 11.5f
                setTextColor(onSurfaceSoft)
            })
            row.addView(texts)
            if (onEdit != null && supported) {
                row.addView(createEditButton(onEdit, sizeDp = 32f, iconSizeDp = 16f).apply {
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        marginStart = dp(6f)
                        marginEnd = dp(6f)
                    }
                })
            }
            row.addView(switch)
            into.addView(row)
        }

        // Manual controls: when full control is OFF, buttons/tiles may still ENABLE
        // protection — the nested "enable-only" switch mirrors Feature access.
        val enableOnlyRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (AutomationModeStore.isMixedAllowButton(activity)) View.GONE else View.VISIBLE
        }
        channelSwitchRow(
            R.drawable.security_24,
            R.string.pref_mixed_allow_button_title,
            R.string.pref_mixed_allow_button_summary,
            supported = true,
            getter = { AutomationModeStore.isMixedAllowButton(activity) },
            setter = { AutomationModeStore.setMixedAllowButton(activity, it) },
            onChanged = { manualAllowed -> enableOnlyRow.isVisible = !manualAllowed },
        )
        channelSwitchRow(
            R.drawable.lock_24,
            R.string.pref_allow_button_enable_title,
            R.string.pref_allow_button_enable_summary,
            supported = true,
            getter = { AutomationModeStore.isButtonEnableAllowed(activity) },
            setter = { AutomationModeStore.setButtonEnableAllowed(activity, it) },
            indent = true,
            into = enableOnlyRow,
        )
        mixedSubmenu.addView(enableOnlyRow)
        channelSwitchRow(
            R.drawable.schedule_24,
            R.string.pref_mixed_allow_schedule_title,
            R.string.pref_mixed_allow_schedule_summary,
            supported = true,
            getter = { AutomationModeStore.isMixedAllowSchedule(activity) },
            setter = { AutomationModeStore.setMixedAllowSchedule(activity, it) },
            onEdit = ::editSchedule,
        )
        channelSwitchRow(
            R.drawable.nfc_24,
            R.string.pref_mixed_allow_nfc_title,
            R.string.pref_mixed_allow_nfc_summary,
            supported = AutomationModeStore.isNfcSupported(activity),
            getter = { AutomationModeStore.isMixedAllowNfc(activity) },
            setter = { AutomationModeStore.setMixedAllowNfc(activity, it) },
            onEdit = ::editNfc,
        )
        channelSwitchRow(
            R.drawable.qr_code_24,
            R.string.pref_mixed_allow_qr_title,
            R.string.pref_mixed_allow_qr_summary,
            supported = AutomationModeStore.isCameraSupported(activity),
            getter = { AutomationModeStore.isMixedAllowQr(activity) },
            setter = { AutomationModeStore.setMixedAllowQr(activity, it) },
            onEdit = ::editQr,
        )
        channelSwitchRow(
            R.drawable.barcode_24,
            R.string.pref_mixed_allow_barcode_title,
            R.string.pref_mixed_allow_barcode_summary,
            supported = AutomationModeStore.isCameraSupported(activity),
            getter = { AutomationModeStore.isMixedAllowBarcode(activity) },
            setter = { AutomationModeStore.setMixedAllowBarcode(activity, it) },
            onEdit = ::editBarcode,
        )

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
            modeRowViews.forEach { (m, rowView) ->
                rowView.background = rowBg(m == mode)
            }
            applyMixedChannelsVisibility()
        }

        mixedRow.setOnClickListener { selectMode(AutomationModeStore.Mode.MIXED, mixedRow) }
        list.addView(mixedRow)
        list.addView(mixedChannelsHeader)
        list.addView(mixedSubmenu)

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
                onEdit = spec.onEdit,
            )
            row.layoutParams = (row.layoutParams as LinearLayout.LayoutParams).apply {
                topMargin = dp(8f)
            }
            row.setOnClickListener {
                if (!AutomationModeStore.isModeSupported(activity, spec.mode)) {
                    return@setOnClickListener
                }
                selectMode(spec.mode, row)
            }
            list.addView(row)
        }

        applyMixedChannelsVisibility()

        return list
    }

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
