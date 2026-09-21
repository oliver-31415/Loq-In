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

package com.oliver.loqin.feature.settings

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.LoqInDialogOption
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.ui.dialog.showLoqInOptionDialog
import com.oliver.loqin.ui.dialog.styleLoqInDialogButtons
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.util.PendingChange
import com.oliver.loqin.util.PendingChangeType
import com.oliver.loqin.util.ProtectionChangeGate
import com.oliver.loqin.util.ProtectionChangePolicy
import com.oliver.loqin.util.ProtectionFeedback
import com.oliver.loqin.ui.dialog.showLoqInInfoDialog
import com.oliver.loqin.ui.dialog.LoqInInfoRow
import com.oliver.loqin.util.EditingLockGuard
import com.oliver.loqin.util.InAppRuleLabels

/**
 * Settings page for the protection change gate: configure the delay for weakening changes and
 * review, discard, or apply the changes currently waiting in the queue.
 */
class ProtectionChangesActivity : AppCompatActivity() {

    private lateinit var tvChangeDelayValue: TextView
    private lateinit var tvChangeDelaySummary: TextView
    private lateinit var tvPendingEmpty: TextView
    private lateinit var tvPendingSummary: TextView
    private lateinit var pendingFooter: View
    private lateinit var pendingContainer: LinearLayout
    private lateinit var pendingActionsRow: View
    private lateinit var btnApplyAllPending: MaterialButton
    private lateinit var btnDiscardAllPending: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_protection_changes)
        CustomAccentApplier.applyIfNeeded(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))

        tvChangeDelayValue = findViewById(R.id.tvChangeDelayValue)
        tvChangeDelaySummary = findViewById(R.id.tvChangeDelaySummary)
        findViewById<View>(R.id.btnProtectionChangesInfo).setOnClickListener {
            showLoqInInfoDialog(
                title = getString(R.string.protection_changes_info_title),
                rows = listOf(
                    LoqInInfoRow(
                        label = getString(R.string.protection_changes_info_title),
                        value = getString(R.string.protection_changes_info_body),
                    ),
                ),
            )
        }
        tvPendingEmpty = findViewById(R.id.tvPendingEmpty)
        tvPendingSummary = findViewById(R.id.tvPendingSummary)
        pendingFooter = findViewById(R.id.pendingFooter)
        pendingContainer = findViewById(R.id.pendingChangesContainer)
        pendingActionsRow = findViewById(R.id.pendingActionsRow)
        btnApplyAllPending = findViewById(R.id.btnApplyAllPending)
        btnDiscardAllPending = findViewById(R.id.btnDiscardAllPending)

        findViewById<View>(R.id.rowChangeDelay).setOnClickListener { showDelayDialog() }
        btnDiscardAllPending.setOnClickListener { confirmDiscardAll() }
        btnApplyAllPending.setOnClickListener { confirmApplyAll() }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val delay = ProtectionChangeGate.getDelayMinutes(this)
        val delayLabel = delayLabel(delay)
        tvChangeDelayValue.text = delayLabel
        tvChangeDelaySummary.text = if (delay <= 0) {
            getString(R.string.protection_change_delay_summary_off)
        } else {
            getString(R.string.protection_change_delay_summary_on, delayLabel)
        }

        val pending = ProtectionChangeGate.pendingChanges(this)
        val hasPending = pending.isNotEmpty()
        tvPendingEmpty.visibility = if (hasPending) View.GONE else View.VISIBLE
        pendingFooter.visibility = if (hasPending) View.VISIBLE else View.GONE
        pendingActionsRow.visibility = if (hasPending) View.VISIBLE else View.GONE
        btnApplyAllPending.visibility =
            if (hasPending && ProtectionChangeGate.canApplyPendingNow(this)) View.VISIBLE else View.GONE

        pendingContainer.removeAllViews()
        pending.forEachIndexed { index, change ->
            if (index > 0) {
                pendingContainer.addView(
                    MaterialDivider(this).apply {
                        dividerInsetStart = dp(54)
                        dividerInsetEnd = 0
                        setDividerColor(dividerColor())
                    }
                )
            }
            pendingContainer.addView(buildPendingRow(change))
        }
    }

    private fun buildPendingRow(change: PendingChange): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            isClickable = true
            isFocusable = true
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundResource(selectableItemBackground())
            setOnClickListener { showPendingChangeActionsDialog(change) }
        }
        row.addView(ImageView(this).apply {
            val icon = leadingIcon(change)
            if (icon != null) {
                setImageDrawable(icon)
            } else {
                setImageResource(R.drawable.schedule_24)
                imageTintList = android.content.res.ColorStateList.valueOf(accentColor())
            }
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        })
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            params.marginStart = dp(14)
            layoutParams = params
        }
        texts.addView(TextView(this).apply {
            text = pendingChangeLabel(change)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        texts.addView(TextView(this).apply {
            text = dueLabel(change.executeAtMs)
            textSize = 13f
            setTextColor(secondaryTextColor())
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            params.topMargin = dp(2)
            layoutParams = params
        })
        row.addView(texts)
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.keyboard_arrow_right_24)
            alpha = 0.7f
            imageTintList = android.content.res.ColorStateList.valueOf(secondaryTextColor())
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
        })
        return row
    }

    private fun showDelayDialog() {
        val current = ProtectionChangeGate.getDelayMinutes(this)
        val values = delaySliderValues(current)
        val locked = EditingLockGuard.isLocked(this)
        val currentIndex = values.indexOf(current).coerceAtLeast(0)

        val valueLabel = TextView(this).apply {
            text = delayLabel(current)
            textSize = 22f
            gravity = android.view.Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(accentColor())
        }
        val slider = com.google.android.material.slider.Slider(this).apply {
            valueFrom = if (locked) currentIndex.toFloat() else 0f
            valueTo = (values.size - 1).toFloat()
            stepSize = 1f
            value = currentIndex.toFloat().coerceIn(valueFrom, valueTo)
            labelBehavior = com.google.android.material.slider.LabelFormatter.LABEL_GONE
            addOnChangeListener { _, value, _ ->
                valueLabel.text = delayLabel(values[value.toInt().coerceIn(0, values.size - 1)])
            }
        }
        val hint = TextView(this).apply {
            text = getString(
                if (locked) {
                    R.string.protection_change_delay_locked
                } else {
                    R.string.protection_change_delay_slider_hint
                }
            )
            textSize = 13f
            setTextColor(secondaryTextColor())
            gravity = android.view.Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            params.topMargin = dp(8)
            layoutParams = params
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(4))
            addView(valueLabel)
            addView(
                slider,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
            addView(hint)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.protection_change_delay_title)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.styleLoqInDialogButtons()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val index = slider.value.toInt().coerceIn(0, values.size - 1)
                dialog.dismiss()
                applyDelay(values[index])
            }
        }
        dialog.show()
    }

    /**
     * Snap points covering the whole range from Off to 7 days without typing a number.
     * A non-preset current value (legacy custom delay) is inserted so it stays selectable.
     */
    private fun delaySliderValues(current: Int): List<Int> {
        val base = listOf(
            0, 1, 2, 5, 10, 15, 30,
            60, 120, 180, 360, 720,
            1_440, 2_880, 4_320, 7_200, 10_080,
        )
        return if (current in base) base else (base + current).sorted()
    }

    private fun applyDelay(minutes: Int) {
        if (ProtectionChangeGate.setDelayMinutes(this, minutes)) {
            refreshUi()
        } else {
            ProtectionFeedback.showInfo(
                this,
                R.string.protection_change_delay_title,
                R.string.protection_change_delay_locked,
            )
        }
    }

    private fun showPendingChangeActionsDialog(change: PendingChange) {
        val canApplyNow = ProtectionChangeGate.canApplyPendingNow(this)
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(
                if (canApplyNow) {
                    R.string.protection_pending_apply_one_title
                } else {
                    R.string.protection_pending_cancel_one_title
                }
            )
            .setMessage(pendingChangeLabel(change))
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.protection_pending_cancel_one_action) { _, _ ->
                if (ProtectionChangeGate.cancelPending(this, change.id)) {
                    refreshUi()
                    ProtectionFeedback.showInfo(
                        this,
                        R.string.protection_pending_changes_title,
                        R.string.protection_pending_cancel_one_done,
                    )
                }
            }
        if (canApplyNow) {
            builder.setPositiveButton(R.string.protection_pending_apply_one_action) { _, _ ->
                val applied = ProtectionChangeGate.applyPendingNow(this, change.id)
                refreshUi()
                ProtectionFeedback.showInfo(
                    this,
                    R.string.protection_pending_changes_title,
                    if (applied) {
                        R.string.protection_pending_apply_one_done
                    } else {
                        R.string.protection_pending_apply_failed
                    },
                )
            }
        }
        builder.showAccented()
    }

    private fun confirmApplyAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.protection_pending_apply_all_title)
            .setMessage(R.string.protection_pending_apply_all_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.protection_pending_apply_all_action) { _, _ ->
                val applied = ProtectionChangeGate.applyAllPendingNow(this)
                refreshUi()
                ProtectionFeedback.showInfo(
                    this,
                    R.string.protection_pending_changes_title,
                    if (applied > 0) {
                        R.string.protection_pending_apply_all_done
                    } else {
                        R.string.protection_pending_apply_failed
                    },
                )
            }
            .showAccented()
    }

    private fun confirmDiscardAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.protection_pending_cancel_title)
            .setMessage(R.string.protection_pending_cancel_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.protection_pending_cancel_action) { _, _ ->
                ProtectionChangeGate.cancelAllPending(this)
                refreshUi()
                ProtectionFeedback.showInfo(
                    this,
                    R.string.protection_pending_changes_title,
                    R.string.protection_pending_cancelled,
                )
            }
            .showAccented()
    }

    private fun pendingChangeLabel(change: PendingChange): String {
        val profile = change.data.optString("profile").ifBlank { "-" }
        return when (change.type) {
            PendingChangeType.APP_SELECTION -> {
                val removals = change.data.optJSONArray("removePackages")
                if (removals != null) {
                    getString(R.string.protection_pending_item_unblock_apps, appNames(removals))
                } else {
                    getString(
                        R.string.protection_pending_item_allow_apps,
                        appNames(change.data.optJSONArray("addPackages")),
                    )
                }
            }

            PendingChangeType.IN_APP_SELECTION -> {
                val baseKey = change.data.optString("baseKey")
                val surface = InAppRuleLabels.label(this, baseKey) ?: baseKey.ifBlank { profile }
                getString(R.string.protection_pending_item_in_app, surface)
            }

            PendingChangeType.WEBSITE_REMOVE,
            PendingChangeType.WEBSITE_ENABLED ->
                getString(
                    R.string.protection_pending_item_website,
                    change.data.optString("rule").ifBlank { profile },
                )

            PendingChangeType.APP_LIMITS -> {
                val packageName = change.data.optString("packageName")
                getString(R.string.protection_pending_item_app_limits, appLabel(packageName))
            }

            PendingChangeType.AUTO_BLOCK_NEW_APPS ->
                getString(R.string.protection_pending_item_auto_block, profile)

            PendingChangeType.CLEAR_APP_DATA ->
                getString(
                    R.string.protection_pending_item_clear_data,
                    appNames(change.data.optJSONArray("packages")),
                )

            else -> getString(R.string.protection_pending_item_unknown)
        }
    }

    private fun leadingIcon(change: PendingChange): android.graphics.drawable.Drawable? = when (change.type) {
        PendingChangeType.APP_SELECTION -> appIcon(
            firstPackage(change.data.optJSONArray("removePackages"))
                ?: firstPackage(change.data.optJSONArray("addPackages")),
        )

        PendingChangeType.IN_APP_SELECTION,
        PendingChangeType.APP_LIMITS -> appIcon(change.data.optString("packageName"))

        PendingChangeType.CLEAR_APP_DATA -> appIcon(firstPackage(change.data.optJSONArray("packages")))

        PendingChangeType.WEBSITE_REMOVE,
        PendingChangeType.WEBSITE_ENABLED -> androidx.core.content.ContextCompat
            .getDrawable(this, R.drawable.language_24)
            ?.mutate()
            ?.apply { setTint(accentColor()) }

        else -> null
    }

    private fun appIcon(packageName: String?): android.graphics.drawable.Drawable? {
        if (packageName.isNullOrBlank()) return null
        return runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    private fun firstPackage(packages: org.json.JSONArray?): String? {
        if (packages == null || packages.length() == 0) return null
        return packages.optString(0).takeIf { it.isNotBlank() }
    }

    private fun appNames(packages: org.json.JSONArray?): String {
        val labels = ArrayList<String>()
        val count = packages?.length() ?: 0
        for (index in 0 until count) {
            val packageName = packages?.optString(index).orEmpty()
            if (packageName.isNotBlank()) labels += appLabel(packageName)
        }
        return when {
            labels.isEmpty() -> getString(R.string.protection_pending_item_unknown)
            labels.size <= 3 -> labels.joinToString(", ")
            else -> resources.getQuantityString(
                R.plurals.protection_pending_apps_count,
                labels.size,
                labels.size,
            )
        }
    }

    private fun appLabel(packageName: String): String = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun dueLabel(executeAtMs: Long): String = DateUtils.getRelativeTimeSpanString(
        executeAtMs,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

    private fun delayLabel(minutes: Int): String = when (minutes) {
        0 -> getString(R.string.protection_change_delay_off)
        15 -> getString(R.string.protection_change_delay_15m)
        60 -> getString(R.string.protection_change_delay_1h)
        360 -> getString(R.string.protection_change_delay_6h)
        1_440 -> getString(R.string.protection_change_delay_24h)

        else -> when {
            minutes % 1_440 == 0 -> {
                val days = minutes / 1_440
                resources.getQuantityString(R.plurals.protection_change_delay_days_value, days, days)
            }

            minutes % 60 == 0 -> {
                val hours = minutes / 60
                resources.getQuantityString(R.plurals.protection_change_delay_hours_value, hours, hours)
            }

            else -> resources.getQuantityString(
                R.plurals.protection_change_delay_minutes_value,
                minutes,
                minutes,
            )
        }
    }

    private fun secondaryTextColor(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        return if (typedValue.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun accentColor(): Int = com.oliver.loqin.theme.AccentColor.getAccentColorInt(this)

    private fun dividerColor(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        return if (typedValue.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun selectableItemBackground(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
        return typedValue.resourceId
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
