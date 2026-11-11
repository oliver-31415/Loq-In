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

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.PageUi

/**
 * One Permissions row in the shared list-row anatomy (docs/EXPRESSIVE_DESIGN.md, "List row +
 * status line"), added to a ListGroupLayout: accent icon, title, summary and a status line whose
 * words, tone and leading icon say whether the permission is ready or needs attention, then the
 * "why" info button and a chevron.
 */
internal class PermissionRow private constructor(
    val row: LinearLayout,
    private val statusView: TextView,
) {
    private var clickEnabled = true

    /**
     * [ok] is ready (positive tone, check icon); [error] is broken (error tone and icon); [neutral]
     * is information only, such as "Not available on this device". Anything else needs setup
     * (warning tone and icon).
     */
    fun setState(status: CharSequence?, ok: Boolean, error: Boolean = false, neutral: Boolean = false) {
        statusView.text = status
        val (tone, iconRes) = when {
            ok -> PageUi.Status.POSITIVE to R.drawable.check_circle_24
            error -> PageUi.Status.ERROR to R.drawable.error_24
            neutral -> PageUi.Status.NEUTRAL to null
            else -> PageUi.Status.WARNING to R.drawable.error_24
        }
        PageUi.setRowStatus(statusView, tone)
        val icon = iconRes?.let { ContextCompat.getDrawable(statusView.context, it)?.mutate() }
        icon?.let {
            val size = statusView.resources.getDimensionPixelSize(R.dimen.permission_status_icon_size)
            it.setBounds(0, 0, size, size)
        }
        TextViewCompat.setCompoundDrawablesRelative(statusView, icon, null, null, null)
        TextViewCompat.setCompoundDrawableTintList(statusView, statusView.textColors)
    }

    /** Locked while protection is armed: full contrast with a lock, taps do nothing. */
    fun setLocked(locked: Boolean) {
        clickEnabled = !locked
        row.isClickable = true
        row.isFocusable = true
        PageUi.setRowLocked(row, locked, row.context.getString(R.string.settings_row_locked_state))
    }

    companion object {
        fun build(
            context: Context,
            @DrawableRes iconRes: Int,
            title: CharSequence,
            summary: CharSequence,
            infoContentDescription: CharSequence,
            onInfoClick: () -> Unit,
            onClick: () -> Unit,
        ): PermissionRow {
            val res = context.resources
            fun px(id: Int) = res.getDimensionPixelSize(id)
            val iconSize = px(R.dimen.row_icon_size)

            val row = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = px(R.dimen.row_min_height)
                setPaddingRelative(
                    px(R.dimen.row_padding_start),
                    px(R.dimen.row_padding_vertical),
                    px(R.dimen.row_padding_end),
                    px(R.dimen.row_padding_vertical),
                )
                isClickable = true
                isFocusable = true
            }

            row.addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                setImageResource(iconRes)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(PageUi.readableAccent(context)))
                importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
            })

            val texts = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = px(R.dimen.row_icon_gap)
                }
                orientation = LinearLayout.VERTICAL
            }
            fun line(text: CharSequence?, appearance: Int, topGap: Boolean) = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { if (topGap) topMargin = px(R.dimen.permission_row_line_gap) }
                TextViewCompat.setTextAppearance(this, appearance)
                this.text = text
            }
            texts.addView(line(title, R.style.TextAppearance_LoqIn_RowTitle, topGap = false))
            texts.addView(line(summary, R.style.TextAppearance_LoqIn_RowSummary, topGap = true))
            val status = line(null, R.style.TextAppearance_LoqIn_RowStatus, topGap = true).apply {
                compoundDrawablePadding = px(R.dimen.permission_status_icon_gap)
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
            }
            texts.addView(status)
            row.addView(texts)

            // Same info button as Feature access: 48dp target, smaller glyph, secondary to the row.
            row.addView(ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(px(R.dimen.touch_target), px(R.dimen.touch_target)).apply {
                    marginStart = px(R.dimen.permission_info_gap)
                }
                context.withStyledAttributes(attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)) {
                    background = getDrawable(0)
                }
                val pad = px(R.dimen.permission_info_padding)
                setPadding(pad, pad, pad, pad)
                setImageResource(R.drawable.info_24)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(AccentColor.getAccentColorInt(context)))
                contentDescription = infoContentDescription
                setOnClickListener { onInfoClick() }
            })

            // Last child: PageUi.setRowLocked swaps it for a lock while the row is locked.
            row.addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginStart = px(R.dimen.permission_info_gap)
                }
                setImageResource(R.drawable.keyboard_arrow_right_24)
                ImageViewCompat.setImageTintList(
                    this,
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.foqos_on_surface_variant)),
                )
                importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
            })

            val holder = PermissionRow(row, status)
            row.setOnClickListener { if (holder.clickEnabled) onClick() }
            return holder
        }
    }
}
