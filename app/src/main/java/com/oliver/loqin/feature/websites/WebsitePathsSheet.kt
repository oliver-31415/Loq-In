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

package com.oliver.loqin.feature.websites

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier

/**
 * Bottom-sheet overlay listing a host's path rules (and its whole-site rule) with per-rule
 * switches. Replaces the old in-grid expansion panel so the tile grid stays clean.
 */
object WebsitePathsSheet {

    interface Listener {
        /** Root (whole-site) rule switch: adds the host rule when it does not exist yet. */
        fun onRootRuleToggle(host: String, enabled: Boolean)
        /** Root row tap: open the root rule editor (or the add dialog when there is no root rule). */
        fun onRootEdit(host: String)
        fun onPathToggle(rule: String, enabled: Boolean)
        fun onPathEdit(rule: String)
        fun onAddPath(host: String)
        fun isReadOnly(): Boolean
    }

    fun show(
        activity: AppCompatActivity,
        host: String,
        hostRule: String?,
        hostEnabled: Boolean,
        hostPending: Boolean,
        hostStateLabel: String,
        rows: List<WebsitePathRow>,
        listener: Listener,
    ) {
        val sheet = BottomSheetDialog(activity)
        sheet.setContentView(
            buildContent(
                activity = activity,
                host = host,
                hostRule = hostRule,
                hostEnabled = hostEnabled,
                hostPending = hostPending,
                hostStateLabel = hostStateLabel,
                rows = rows,
                listener = listener,
                onClose = { sheet.dismiss() },
                onChanged = {
                    sheet.dismiss()
                },
            )
        )
        sheet.show()
    }

    private fun buildContent(
        activity: AppCompatActivity,
        host: String,
        hostRule: String?,
        hostEnabled: Boolean,
        hostPending: Boolean,
        hostStateLabel: String,
        rows: List<WebsitePathRow>,
        listener: Listener,
        onClose: () -> Unit,
        onChanged: () -> Unit,
    ): LinearLayout {
        val onSurface = ContextCompat.getColor(activity, R.color.foqos_on_surface)
        val onSurfaceSoft = ContextCompat.getColor(activity, R.color.foqos_on_surface_variant)
        val accent = AccentColor.getAccentColorInt(activity)

        fun dp(value: Float): Int =
            (value * activity.resources.displayMetrics.density + 0.5f).toFloat().toInt()

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16f)
            setPadding(pad, dp(8f), pad, dp(22f))
            setBackgroundColor(ContextCompat.getColor(activity, R.color.foqos_surface))
        }

        list.addView(View(activity).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2f).toFloat()
                setColor(ColorUtils.setAlphaComponent(onSurfaceSoft, 0x61))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36f), dp(4f)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        })

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12f), 0, dp(4f))
        }
        header.addView(TextView(activity).apply {
            text = activity.getString(R.string.website_paths_sheet_title)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(onSurface)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(activity).apply {
            text = host
            textSize = 13f
            setTextColor(onSurfaceSoft)
        })
        header.addView(FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(activity, R.color.foqos_surface))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClose() }
            addView(TextView(activity).apply {
                text = "\u2715"
                textSize = 14f
                setTextColor(onSurface)
                layoutParams = FrameLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.CENTER }
            })
            layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f)).apply {
                marginStart = dp(8f)
            }
        })
        list.addView(header)

        fun stateRow(
            title: String,
            labelOn: String,
            labelOff: String,
            enabled: Boolean,
            pending: Boolean,
            onToggle: (Boolean) -> Unit,
            onClick: (() -> Unit)?,
        ): View {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10f), dp(8f), dp(6f), dp(8f))
                isClickable = onClick != null
                isFocusable = onClick != null
                background = if (onClick != null) {
                    val tv = android.util.TypedValue()
                    activity.theme.resolveAttribute(
                        android.R.attr.selectableItemBackground, tv, true
                    )
                    ContextCompat.getDrawable(activity, tv.resourceId)
                } else {
                    null
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            val texts = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvTitle = TextView(activity).apply {
                text = title
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(onSurface)
            }
            val tvMeta = TextView(activity).apply {
                text = when {
                    pending -> activity.getString(R.string.website_rule_pending)
                    !enabled -> labelOff
                    else -> labelOn
                }
                textSize = 11.5f
                setTextColor(onSurfaceSoft)
            }
            texts.addView(tvTitle)
            texts.addView(tvMeta)
            row.addView(texts)

            val sw = SwitchCompat(activity).apply {
                CustomAccentApplier.tintSwitch(this)
                isChecked = enabled
                isEnabled = !pending && !listener.isReadOnly()
                alpha = if (isEnabled) 1f else 0.5f
                setOnCheckedChangeListener { _, isChecked ->
                    if (!listener.isReadOnly()) {
                        onToggle(isChecked)
                        tvMeta.text = if (isChecked) labelOn else labelOff
                    }
                }
            }
            row.addView(sw)

            val contentAlpha = when {
                pending -> 0.62f
                enabled -> 1f
                else -> 0.52f
            }
            tvTitle.alpha = contentAlpha
            tvMeta.alpha = if (enabled) 0.70f else 0.56f
            onClick?.let { row.setOnClickListener { it() } }
            return row
        }

        // The root (whole-site) row is always listed, even when it has no rule yet, so the
        // root is visible alongside its subpages and can be added from here.
        list.addView(stateRow(
            title = hostRule ?: host,
            labelOn = hostStateLabel,
            labelOff = if (hostRule != null) {
                activity.getString(R.string.website_rule_disabled)
            } else {
                activity.getString(R.string.website_root_not_added)
            },
            enabled = hostRule != null && hostEnabled,
            pending = hostPending,
            onToggle = { checked -> listener.onRootRuleToggle(host, checked) },
            onClick = {
                listener.onRootEdit(host)
                onChanged()
            },
        ))

        rows.forEach { row ->
            list.addView(stateRow(
                title = row.rule,
                labelOn = WebsiteRuleTileAdapter.pathStateLabel(row.copy(enabled = true), activity),
                labelOff = activity.getString(R.string.website_rule_disabled),
                enabled = row.enabled,
                pending = row.pending,
                onToggle = { checked ->
                    listener.onPathToggle(row.rule, checked)
                },
                onClick = {
                    listener.onPathEdit(row.rule)
                    onChanged()
                },
            ))
        }

        if (hostRule == null && rows.isEmpty()) {
            list.addView(TextView(activity).apply {
                text = activity.getString(R.string.website_tile_paths_only)
                textSize = 13f
                setTextColor(onSurfaceSoft)
                setPadding(dp(10f), dp(12f), dp(10f), dp(12f))
            })
        }

        // Inline list action so it sits naturally with the rule rows above it.
        val addRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(12f), dp(14f), dp(10f))
            isClickable = true
            isFocusable = true
            val ripple = android.util.TypedValue()
            if (activity.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, ripple, true
                )
            ) {
                setBackgroundResource(ripple.resourceId)
            }
            addView(ImageView(activity).apply {
                setImageResource(R.drawable.add_24)
                setColorFilter(accent)
                layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f))
            })
            addView(TextView(activity).apply {
                text = activity.getString(R.string.website_add_path)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(accent)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(12f) }
            })
            isEnabled = !listener.isReadOnly()
            alpha = if (isEnabled) 1f else 0.45f
            setOnClickListener {
                listener.onAddPath(host)
                onChanged()
            }
        }
        list.addView(addRow)

        return list
    }
}
