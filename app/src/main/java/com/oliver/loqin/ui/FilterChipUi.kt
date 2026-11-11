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
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor

/**
 * One filter-chip look (All / Social / Video ..., log filters): style LoqIn.FilterChip, and the
 * same selected treatment as a selected segment in [SegmentedToggleUi] (accent container fill,
 * on-surface medium label, no outline); unselected chips are outlined and transparent.
 */
object FilterChipUi {

    /** A chip in style LoqIn.FilterChip. Selection is drawn by [setSelected], not by Material. */
    fun create(context: Context, label: CharSequence): Chip =
        Chip(context, null, R.attr.loqinFilterChipStyle).apply {
            text = label
            id = View.generateViewId()
            isClickable = true
            isFocusable = true
            // UiConsistency recolours checkable chips; this one owns its colours.
            setTag(R.id.tag_filter_chip, true)
        }

    fun setSelected(chip: Chip, selected: Boolean) {
        val ctx = chip.context
        val surface = ContextCompat.getColor(ctx, R.color.foqos_surface)
        val onSurface = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val onSurfaceVariant = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurfaceVariant, onSurface)
        chip.isChecked = selected
        if (selected) {
            val fill = ColorUtils.compositeColors(AccentColor.getAccentContainerColorInt(ctx), surface)
            chip.chipBackgroundColor = ColorStateList.valueOf(fill)
            chip.chipStrokeWidth = 0f
            chip.setTextColor(onSurface)
            chip.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        } else {
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
            chip.chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.foqos_outline))
            chip.chipStrokeWidth = ctx.resources.displayMetrics.density
            chip.setTextColor(onSurfaceVariant)
            chip.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        chip.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, 0x1F))
    }
}
