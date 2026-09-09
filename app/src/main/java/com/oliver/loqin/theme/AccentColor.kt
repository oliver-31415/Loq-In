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

package com.oliver.loqin.theme

import android.content.Context
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import com.oliver.loqin.R

object AccentColor {

    private const val PREF_KEY = "pref_accent"
    private const val PREF_CUSTOM = "pref_accent_custom"

    enum class Option(val value: String) {
        GREEN("green"),
        BLUE("blue"),
        ORANGE("orange"),
        PURPLE("purple"),
        PINK("pink"),
        TEAL("teal"),
        RED("red"),
        AMBER("amber"),
        GRAY("gray"),
        CUSTOM("custom")
    }

    fun getOption(context: Context): Option {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return when (prefs.getString(PREF_KEY, "default")) {
            Option.BLUE.value   -> Option.BLUE
            Option.ORANGE.value -> Option.ORANGE
            Option.PURPLE.value -> Option.PURPLE
            Option.PINK.value   -> Option.PINK
            Option.TEAL.value   -> Option.TEAL
            Option.RED.value    -> Option.RED
            Option.AMBER.value  -> Option.AMBER
            Option.GRAY.value   -> Option.GRAY
            Option.CUSTOM.value -> Option.CUSTOM
            else                -> Option.GREEN
        }
    }

    fun getAccentColorInt(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return when (getOption(context)) {
            Option.GREEN  -> ContextCompat.getColor(context, R.color.accent_default_blue)
            Option.BLUE   -> ContextCompat.getColor(context, R.color.accent_blue)
            Option.ORANGE -> ContextCompat.getColor(context, R.color.accent_orange)
            Option.PURPLE -> ContextCompat.getColor(context, R.color.accent_purple)
            Option.PINK   -> ContextCompat.getColor(context, R.color.accent_pink)
            Option.TEAL   -> ContextCompat.getColor(context, R.color.accent_teal)
            Option.RED    -> ContextCompat.getColor(context, R.color.accent_red)
            Option.AMBER  -> ContextCompat.getColor(context, R.color.accent_amber)
            Option.GRAY   -> ContextCompat.getColor(context, R.color.accent_gray)
            Option.CUSTOM -> {
                val hex = prefs.getString(PREF_CUSTOM, "#6BA6E8") ?: "#6BA6E8"
                try {
                    hex.toColorInt()
                } catch (e: IllegalArgumentException) {
                    ContextCompat.getColor(context, R.color.accent_default_blue)
                }
            }
        }
    }

    // Foqos restyle: toolbars are flat surface (no accent header). The accent stays on
    // buttons/controls. All activities that tint their toolbar programmatically get the
    // surface color here, so the whole app flips consistently.
    fun getToolbarColor(context: Context): Int = ContextCompat.getColor(context, R.color.foqos_surface)

    fun getActiveColor(context: Context): ColorStateList = ColorStateList.valueOf(getAccentColorInt(context))

    /**
     * Concrete container wash for icon roundels/badges. Same reason as
     * [getDatePickerTheme]: ?attr/colorPrimaryContainer inside drawables and
     * dialog contexts can resolve to the base green instead of the live
     * accent, so set it explicitly in code.
     */
    fun getAccentContainerColorInt(context: Context): Int = when (getOption(context)) {
        Option.GREEN  -> ContextCompat.getColor(context, R.color.accent_default_blue_container)
        Option.BLUE   -> ContextCompat.getColor(context, R.color.accent_blue_container)
        Option.ORANGE -> ContextCompat.getColor(context, R.color.accent_orange_container)
        Option.PURPLE -> ContextCompat.getColor(context, R.color.accent_purple_container)
        Option.PINK   -> ContextCompat.getColor(context, R.color.accent_pink_container)
        Option.TEAL   -> ContextCompat.getColor(context, R.color.accent_teal_container)
        Option.RED    -> ContextCompat.getColor(context, R.color.accent_red_container)
        Option.AMBER  -> ContextCompat.getColor(context, R.color.accent_amber_container)
        Option.GRAY   -> ContextCompat.getColor(context, R.color.accent_gray_container)
        Option.CUSTOM -> androidx.core.graphics.ColorUtils.setAlphaComponent(getAccentColorInt(context), 0x2E)
    }

    /**
     * Concrete date-picker dialog theme for the current accent. The picker is
     * themed with setTheme(), which resolves against a dialog overlay — not
     * the activity theme — so ?attr references to the live accent cannot
     * resolve there (they crash inflation). One overlay per accent carries
     * concrete colors instead.
     */
    fun getDatePickerTheme(context: Context): Int = when (getOption(context)) {
        Option.BLUE   -> R.style.ThemeOverlay_LoqIn_DatePicker_Blue
        Option.ORANGE -> R.style.ThemeOverlay_LoqIn_DatePicker_Orange
        Option.PURPLE -> R.style.ThemeOverlay_LoqIn_DatePicker_Purple
        Option.PINK   -> R.style.ThemeOverlay_LoqIn_DatePicker_Pink
        Option.TEAL   -> R.style.ThemeOverlay_LoqIn_DatePicker_Teal
        Option.RED    -> R.style.ThemeOverlay_LoqIn_DatePicker_Red
        Option.AMBER  -> R.style.ThemeOverlay_LoqIn_DatePicker_Amber
        Option.GRAY   -> R.style.ThemeOverlay_LoqIn_DatePicker_Gray
        else          -> R.style.ThemeOverlay_LoqIn_DatePicker
    }

    /**
     * Concrete time-picker dialog theme for the current accent.
     */
    fun getTimePickerTheme(context: Context): Int = when (getOption(context)) {
        Option.BLUE   -> R.style.ThemeOverlay_LoqIn_TimePicker_Blue
        Option.ORANGE -> R.style.ThemeOverlay_LoqIn_TimePicker_Orange
        Option.PURPLE -> R.style.ThemeOverlay_LoqIn_TimePicker_Purple
        Option.PINK   -> R.style.ThemeOverlay_LoqIn_TimePicker_Pink
        Option.TEAL   -> R.style.ThemeOverlay_LoqIn_TimePicker_Teal
        Option.RED    -> R.style.ThemeOverlay_LoqIn_TimePicker_Red
        Option.AMBER  -> R.style.ThemeOverlay_LoqIn_TimePicker_Amber
        Option.GRAY   -> R.style.ThemeOverlay_LoqIn_TimePicker_Gray
        else          -> R.style.ThemeOverlay_LoqIn_TimePicker
    }

    /**
     * Concrete Material alert dialog theme for the current accent.
     */
    fun getDialogTheme(context: Context): Int = when (getOption(context)) {
        Option.BLUE   -> R.style.ThemeOverlay_LoqIn_Dialog_Blue
        Option.ORANGE -> R.style.ThemeOverlay_LoqIn_Dialog_Orange
        Option.PURPLE -> R.style.ThemeOverlay_LoqIn_Dialog_Purple
        Option.PINK   -> R.style.ThemeOverlay_LoqIn_Dialog_Pink
        Option.TEAL   -> R.style.ThemeOverlay_LoqIn_Dialog_Teal
        Option.RED    -> R.style.ThemeOverlay_LoqIn_Dialog_Red
        Option.AMBER  -> R.style.ThemeOverlay_LoqIn_Dialog_Amber
        Option.GRAY   -> R.style.ThemeOverlay_LoqIn_Dialog_Gray
        else          -> R.style.ThemeOverlay_LoqIn_Dialog
    }
}
