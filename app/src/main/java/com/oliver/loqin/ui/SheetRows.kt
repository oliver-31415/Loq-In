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

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.oliver.loqin.R
import com.oliver.loqin.ui.widgets.ListGroupLayout

/**
 * Building blocks for sheets built in code, matching the profile sheet (sheet_profile_edit):
 * the same header, grouped list segments and rows, so every Home sheet looks alike.
 */
object SheetRows {

    class Row(
        val root: LinearLayout,
        val icon: ImageView,
        val title: TextView,
        val summary: TextView,
        val trailing: LinearLayout,
    ) {
        /**
         * Marks the row as the chosen one: accent title and a trailing check, as in Android's
         * own single-choice lists. The check sits after any other trailing controls.
         */
        fun setSelected(selected: Boolean, accent: Int) {
            if (selected) {
                title.setTextColor(accent)
            } else {
                TextViewCompat.setTextAppearance(title, R.style.TextAppearance_LoqIn_RowTitle)
            }
            val existing = trailing.findViewWithTag<View>(CHECK_TAG)
            if (selected && existing == null) {
                val ctx = root.context
                trailing.addView(ImageView(ctx).apply {
                    tag = CHECK_TAG
                    setImageResource(R.drawable.check_circle_24)
                    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(accent))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 24f), dp(ctx, 24f)).apply {
                        marginStart = dp(ctx, 8f)
                    }
                })
            } else if (!selected && existing != null) {
                trailing.removeView(existing)
            }
            root.isSelected = selected
        }
    }

    private const val CHECK_TAG = "sheet_row_check"

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    /** Handle, title and close button; returns the header view. */
    fun header(context: Context, parent: ViewGroup, title: CharSequence, onClose: () -> Unit): View {
        val view = LayoutInflater.from(context).inflate(R.layout.sheet_header, parent, false)
        view.findViewById<TextView>(R.id.sheetTitle).text = title
        view.findViewById<View>(R.id.btnSheetClose).setOnClickListener { onClose() }
        return view
    }

    /** A grouped list; [onSurface] for sheets (tonal segments), false on a page background. */
    fun group(context: Context, parent: ViewGroup, onSurface: Boolean = true): ListGroupLayout =
        LayoutInflater.from(context).inflate(
            if (onSurface) R.layout.list_group_on_surface else R.layout.list_group_on_page,
            parent,
            false,
        ) as ListGroupLayout

    fun row(
        context: Context,
        parent: ViewGroup,
        iconRes: Int,
        title: CharSequence,
        summary: CharSequence? = null,
        accent: Int,
    ): Row {
        val root = LayoutInflater.from(context).inflate(R.layout.sheet_list_row, parent, false) as LinearLayout
        val row = Row(
            root = root,
            icon = root.findViewById(R.id.rowIcon),
            title = root.findViewById(R.id.rowTitle),
            summary = root.findViewById(R.id.rowSummary),
            trailing = root.findViewById(R.id.rowTrailing),
        )
        if (iconRes != 0) {
            row.icon.setImageResource(iconRes)
            ImageViewCompat.setImageTintList(row.icon, ColorStateList.valueOf(accent))
        } else {
            row.icon.visibility = View.GONE
            (row.title.parent as View).let { texts ->
                (texts.layoutParams as LinearLayout.LayoutParams).marginStart = 0
            }
        }
        row.title.text = title
        if (!summary.isNullOrEmpty()) {
            row.summary.text = summary
            row.summary.visibility = View.VISIBLE
        }
        return row
    }

    /** A borderless 48dp pencil button for a row's trailing slot. */
    fun editButton(context: Context, accent: Int, onClick: () -> Unit): View =
        ImageView(context).apply {
            setImageResource(R.drawable.edit_24)
            ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(accent))
            val pad = dp(context, 12f)
            setPadding(pad, pad, pad, pad)
            val tv = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
            setBackgroundResource(tv.resourceId)
            contentDescription = context.getString(R.string.edit)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(context, 48f), dp(context, 48f))
            setOnClickListener { onClick() }
        }
}
