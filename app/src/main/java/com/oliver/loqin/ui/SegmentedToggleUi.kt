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
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.AbsoluteCornerSize
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel

// Keeps two-option segmented controls visually consistent across LoqIn with modern capsule design.
object SegmentedToggleUi {
    fun apply(
        context: Context,
        buttons: Iterable<MaterialButton>,
        selectedId: Int,
    ) {
        val accent = AccentColor.getAccentColorInt(context)
        val onSurfaceColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnSurface,
            Color.WHITE
        )
        val surfaceColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurface,
            ContextCompat.getColor(context, R.color.foqos_surface)
        )
        val isDarkTheme = !MaterialColors.isColorLight(surfaceColor)

        // Style the parent track if it is a MaterialButtonToggleGroup
        val parent = (buttons.firstOrNull()?.parent as? ViewGroup)
        if (parent is MaterialButtonToggleGroup) {
            // Expressive segmented control: a tonal pill track, no outline, with the
            // selected segment as a filled pill in the accent container colour.
            val trackBg = GradientDrawable().apply {
                cornerRadius = dp(context, 100).toFloat()
                setColor(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorSurfaceContainerHigh,
                        ColorUtils.setAlphaComponent(onSurfaceColor, if (isDarkTheme) 0x14 else 0x0A),
                    )
                )
            }
            parent.background = trackBg
            val p = dp(context, 4)
            parent.setPadding(p, p, p, p)

            // MaterialButtonGroup owns its children's corner shapes and rewrites them on
            // the first onMeasure/onLayout via updateChildShapes(). Give the group the
            // shape + inner corner size so the first measure is already a full pill.
            val pill = RelativeCornerSize(0.5f)
            parent.shapeAppearance = ShapeAppearanceModel.builder()
                .setAllCornerSizes(pill)
                .build()
            parent.innerCornerSize = pill
        }

        val buttonCornerRadius = dp(context, 100)
        val buttonHeight = dp(context, 44)

        buttons.forEach { button ->
            val selected = button.id == selectedId
            button.minWidth = 0
            button.minimumWidth = 0
            button.minHeight = buttonHeight
            button.minimumHeight = buttonHeight
            button.insetTop = 0
            button.insetBottom = 0
            button.isAllCaps = false
            button.cornerRadius = buttonCornerRadius
            // Corner geometry is owned by the MaterialButtonToggleGroup (see above);
            // setting it per-button here would be overwritten on the next measure.
            button.isActivated = selected

            // Ensure clean icons for block/allow mode buttons if applicable
            when (button.id) {
                R.id.btnBlockSelectedMode,
                R.id.btnInAppModeBlock,
                R.id.btnWebsiteModeBlock -> {
                    if (button.icon == null) {
                        button.icon = ContextCompat.getDrawable(context, R.drawable.app_blocking_white_24)
                        button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                        button.iconPadding = dp(context, 8)
                        button.iconSize = dp(context, 18)
                    }
                }
                R.id.btnAllowSelectedMode,
                R.id.btnInAppModeAllow,
                R.id.btnWebsiteModeAllow -> {
                    if (button.icon == null) {
                        button.icon = ContextCompat.getDrawable(context, R.drawable.check_circle_24)
                        button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                        button.iconPadding = dp(context, 8)
                        button.iconSize = dp(context, 18)
                    }
                }
            }

            if (selected) {
                // Concrete accent container: ?attr/colorPrimaryContainer doesn't follow a
                // custom accent (see AccentColor.getAccentContainerColorInt).
                val selectedBg = ColorUtils.compositeColors(
                    AccentColor.getAccentContainerColorInt(context),
                    surfaceColor,
                )
                val onSelected = onSurfaceColor
                button.backgroundTintList = ColorStateList.valueOf(selectedBg)
                button.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
                button.strokeWidth = 0
                button.setTextColor(onSelected)
                button.iconTint = ColorStateList.valueOf(accent)
                button.setTypeface(button.typeface, Typeface.BOLD)
                button.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x25))
                button.alpha = 1f
            } else {
                button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                button.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
                button.strokeWidth = 0
                val unselectedText = MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    ColorUtils.setAlphaComponent(onSurfaceColor, 0x88),
                )
                button.setTextColor(unselectedText)
                button.iconTint = ColorStateList.valueOf(unselectedText)
                button.setTypeface(Typeface.create(button.typeface, Typeface.NORMAL), Typeface.NORMAL)
                button.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurfaceColor, 0x1A))
                button.alpha = 1f
            }
            button.jumpDrawablesToCurrentState()
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
