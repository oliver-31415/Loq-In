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
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import com.oliver.loqin.R
import com.oliver.loqin.feature.theme.CustomAccentPickerDialog
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.LoqInDialogOption
import com.oliver.loqin.ui.dialog.showLoqInOptionDialog
import com.oliver.loqin.util.LocaleHelper
import com.oliver.loqin.util.TimeFormatPrefs
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors

/**
 * Appearance.
 *
 * Choice settings use the same segmented bar as the insights range selector
 * (a single track with the active option highlighted). Theme colour opens the
 * existing colour overlay from a row that previews the current accent.
 */
class AppearanceActivity : AppCompatActivity() {

    private lateinit var toggleThemeMode: MaterialButtonToggleGroup
    private lateinit var toggleTimeFormat: MaterialButtonToggleGroup
    private lateinit var toggleLanguage: MaterialButtonToggleGroup

    private lateinit var tvThemeModeSummary: TextView
    private lateinit var tvTimeFormatSummary: TextView
    private lateinit var tvThemeColorSummary: TextView
    private lateinit var tvLanguageSummary: TextView

    private lateinit var accentPreviewDot: View

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private val themeModeValues = listOf("system", "light", "dark")
    private val themeModeButtonIds = listOf(R.id.btnThemeSystem, R.id.btnThemeLight, R.id.btnThemeDark)

    private val timeFormatValues = listOf("system", "24h", "12h")
    private val timeFormatButtonIds = listOf(R.id.btnTimeAutomatic, R.id.btnTime24, R.id.btnTime12)

    private val languageValues = listOf("system", "en", "de")
    private val languageButtonIds =
        listOf(R.id.btnLanguageSystem, R.id.btnLanguageEnglish, R.id.btnLanguageGerman)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appearance)
        CustomAccentApplier.applyIfNeeded(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.title = getString(R.string.settings_theme_title)

        toggleThemeMode = findViewById(R.id.toggleThemeMode)
        toggleTimeFormat = findViewById(R.id.toggleTimeFormat)
        toggleLanguage = findViewById(R.id.toggleLanguage)

        tvThemeModeSummary = findViewById(R.id.tvThemeModeSummary)
        tvTimeFormatSummary = findViewById(R.id.tvTimeFormatSummary)
        tvThemeColorSummary = findViewById(R.id.tvThemeColorSummary)
        tvLanguageSummary = findViewById(R.id.tvLanguageSummary)

        accentPreviewDot = findViewById(R.id.accentPreviewDot)

        findViewById<View>(R.id.rowThemeColor).setOnClickListener { showThemeColorDialog() }

        setupThemeMode()
        setupTimeFormat()
        setupLanguage()
        updateAllSummaries()
    }

    override fun onResume() {
        super.onResume()
        updateAllSummaries()
    }

    // ---------------------------------------------------------------------
    // Segmented settings
    // ---------------------------------------------------------------------

    private fun setupThemeMode() {
        applySegmented(toggleThemeMode, themeModeButtonIds, currentThemeMode())
        toggleThemeMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = valueFor(themeModeButtonIds, themeModeValues, checkedId) ?: return@addOnButtonCheckedListener
            if (value == currentThemeMode()) return@addOnButtonCheckedListener
            prefs.edit { putString(KEY_THEME_MODE, value) }
            AppCompatDelegate.setDefaultNightMode(
                when (value) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
            recreate()
        }
    }

    private fun setupTimeFormat() {
        applySegmented(toggleTimeFormat, timeFormatButtonIds, TimeFormatPrefs.getMode(this))
        toggleTimeFormat.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = valueFor(timeFormatButtonIds, timeFormatValues, checkedId) ?: return@addOnButtonCheckedListener
            TimeFormatPrefs.setMode(this, value)
            applySegmented(toggleTimeFormat, timeFormatButtonIds, value)
            updateTimeFormatSummary()
        }
    }

    private fun setupLanguage() {
        applySegmented(toggleLanguage, languageButtonIds, currentLanguage())
        toggleLanguage.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = valueFor(languageButtonIds, languageValues, checkedId) ?: return@addOnButtonCheckedListener
            if (value == currentLanguage()) return@addOnButtonCheckedListener
            prefs.edit { putString(KEY_LANGUAGE, value) }
            LocaleHelper.setLanguage(application, value)
            recreate()
        }
    }

    private fun applySegmented(toggle: MaterialButtonToggleGroup, buttonIds: List<Int>, value: String) {
        val index = valuesIndex(buttonIds, value)
        val selectedId = buttonIds.getOrElse(index) { buttonIds.first() }
        toggle.check(selectedId)
        val buttons = buttonIds.mapNotNull { toggle.findViewById<MaterialButton>(it) }
        SegmentedToggleUi.apply(this, buttons, selectedId)
    }

    private fun valuesIndex(buttonIds: List<Int>, value: String): Int {
        val values = when (buttonIds) {
            themeModeButtonIds -> themeModeValues
            timeFormatButtonIds -> timeFormatValues
            languageButtonIds -> languageValues
            else -> emptyList()
        }
        return values.indexOf(value)
    }

    private fun valueFor(buttonIds: List<Int>, values: List<String>, checkedId: Int): String? {
        val index = buttonIds.indexOf(checkedId)
        return values.getOrNull(index)
    }

    // ---------------------------------------------------------------------
    // Colour overlay
    // ---------------------------------------------------------------------

    private fun showThemeColorDialog() {
        val current = currentAccent()

        val allEntries = resources.getStringArray(R.array.pref_accent_entries)
        val allValues = resources.getStringArray(R.array.pref_accent_values)

        val entries = allEntries + getString(R.string.pref_accent_custom)
        val values = allValues + "custom"

        val checked = values.indexOf(current).let { idx -> if (idx >= 0) idx else 0 }

        showSingleSelectCheckboxDialog(
            title = getString(R.string.pref_theme_color_title),
            dialogSubtitle = getString(R.string.pref_theme_color_summary),
            entries = entries,
            checkedIndex = checked,
            iconDrawables = values.map { colorPreviewDrawable(accentColorForValue(it)) as Drawable? }.toTypedArray(),
            instantApply = true,
        ) { which, dialog ->
            val selected = values[which]
            if (selected == "custom") {
                dialog.dismiss()
                showCustomColorPicker()
            } else {
                prefs.edit { putString(KEY_ACCENT, selected) }
                updateThemeColorSummary()
                dialog.dismiss()
                recreate()
            }
        }
    }

    private fun showCustomColorPicker() {
        CustomAccentPickerDialog.show(this) {
            updateThemeColorSummary()
            recreate()
        }
    }

    private fun showSingleSelectCheckboxDialog(
        title: String,
        dialogSubtitle: String? = null,
        entries: Array<String>,
        checkedIndex: Int,
        summaries: Array<String>? = null,
        iconRes: Array<Int?>? = null,
        iconDrawables: Array<Drawable?>? = null,
        instantApply: Boolean = false,
        onSelected: (index: Int, dialog: AlertDialog) -> Unit,
    ) {
        lateinit var dialog: AlertDialog
        dialog = showLoqInOptionDialog(
            title = title,
            subtitle = dialogSubtitle,
            options = entries.mapIndexed { index, label ->
                LoqInDialogOption(
                    title = label,
                    summary = summaries?.getOrNull(index),
                    iconRes = iconRes?.getOrNull(index),
                    iconDrawable = iconDrawables?.getOrNull(index),
                    selected = index == checkedIndex
                )
            },
            confirmSelection = !instantApply
        ) { which ->
            onSelected(which, dialog)
        }
    }

    // ---------------------------------------------------------------------
    // Values and summaries
    // ---------------------------------------------------------------------

    private fun currentThemeMode(): String = prefs.getString(KEY_THEME_MODE, "system") ?: "system"

    private fun currentAccent(): String = prefs.getString(KEY_ACCENT, "default") ?: "default"

    private fun currentLanguage(): String = prefs.getString(KEY_LANGUAGE, "system") ?: "system"

    private fun updateAllSummaries() {
        updateThemeModeSummary()
        updateTimeFormatSummary()
        updateThemeColorSummary()
        updateLanguageSummary()
    }

    private fun updateThemeModeSummary() {
        tvThemeModeSummary.text = getString(
            when (currentThemeMode()) {
                "light" -> R.string.pref_theme_mode_light_summary
                "dark" -> R.string.pref_theme_mode_dark_summary
                else -> R.string.pref_theme_mode_system_summary
            }
        )
    }

    private fun updateTimeFormatSummary() {
        tvTimeFormatSummary.text = getString(
            when (TimeFormatPrefs.getMode(this)) {
                "12h" -> R.string.pref_time_format_12h_summary
                "24h" -> R.string.pref_time_format_24h_summary
                else -> R.string.pref_time_format_system_summary
            }
        )
    }

    private fun updateThemeColorSummary() {
        val current = currentAccent()
        tvThemeColorSummary.text = when (current) {
            "custom" -> {
                val hex = prefs.getString(KEY_ACCENT_CUSTOM, "").orEmpty()
                if (hex.isNotBlank()) getString(R.string.pref_theme_color_custom_fmt, hex)
                else getString(R.string.pref_accent_custom_title)
            }
            else -> {
                val entries = resources.getStringArray(R.array.pref_accent_entries)
                val values = resources.getStringArray(R.array.pref_accent_values)
                entries.getOrNull(values.indexOf(current)) ?: entries.firstOrNull().orEmpty()
            }
        }
        accentPreviewDot.background = colorPreviewDrawable(accentColorForValue(current))
    }

    private fun updateLanguageSummary() {
        tvLanguageSummary.text = getString(
            when (currentLanguage()) {
                "en" -> R.string.pref_language_en_summary
                "de" -> R.string.pref_language_de_summary
                else -> R.string.pref_language_system_summary
            }
        )
    }

    // ---------------------------------------------------------------------
    // Drawables and colours
    // ---------------------------------------------------------------------

    private fun colorPreviewDrawable(color: Int): Drawable {
        val outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, 0x33000000)
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), outline)
            setSize(dp(24), dp(24))
        }
    }

    private fun accentColorForValue(value: String): Int {
        return when (value) {
            "blue" -> ContextCompat.getColor(this, R.color.accent_blue)
            "orange" -> ContextCompat.getColor(this, R.color.accent_orange)
            "purple" -> ContextCompat.getColor(this, R.color.accent_purple)
            "pink" -> ContextCompat.getColor(this, R.color.accent_pink)
            "teal" -> ContextCompat.getColor(this, R.color.accent_teal)
            "red" -> ContextCompat.getColor(this, R.color.accent_red)
            "amber" -> ContextCompat.getColor(this, R.color.accent_amber)
            "gray" -> ContextCompat.getColor(this, R.color.accent_gray)
            "custom" -> runCatching {
                (prefs.getString(KEY_ACCENT_CUSTOM, "#6BA6E8") ?: "#6BA6E8").toColorInt()
            }.getOrDefault(ContextCompat.getColor(this, R.color.accent_default_blue))
            else -> ContextCompat.getColor(this, R.color.accent_default_blue)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val KEY_THEME_MODE = "pref_theme_mode"
        const val KEY_ACCENT = "pref_accent"
        const val KEY_ACCENT_CUSTOM = "pref_accent_custom"
        const val KEY_LANGUAGE = "pref_language"
    }
}
