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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.oliver.loqin.R
import com.oliver.loqin.feature.theme.CustomAccentPickerDialog
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.util.LocaleHelper
import com.oliver.loqin.util.TimeFormatPrefs

/**
 * Appearance.
 *
 * Choice settings use the same segmented bar as the insights range selector
 * (a single track with the active option highlighted). Theme colour is an
 * inline selector: a live preview of the accent above a grid of swatches.
 * Picking a preset animates the preview, saves the accent and recreates the
 * screen. Custom opens the colour picker dialog.
 */
class AppearanceActivity : AppCompatActivity() {

    private lateinit var toggleThemeMode: MaterialButtonToggleGroup
    private lateinit var toggleTimeFormat: MaterialButtonToggleGroup
    private lateinit var toggleLanguage: MaterialButtonToggleGroup

    private lateinit var tvThemeModeSummary: TextView
    private lateinit var tvTimeFormatSummary: TextView
    private lateinit var tvThemeColorSummary: TextView
    private lateinit var tvLanguageSummary: TextView

    private lateinit var accentPreviewPill: TextView
    private lateinit var accentPreviewSwitch: MaterialSwitch
    private lateinit var accentPreviewProgress: LinearProgressIndicator
    private lateinit var gridAccentSwatches: GridLayout

    /** Swatches keyed by accent value, including [CUSTOM]. */
    private val swatchViews = mutableMapOf<String, AccentSwatchView>()

    /** Accent the grid marks as selected. Updated on tap, before the save. */
    private var selectedAccent: String = "default"

    /** Colour the preview is currently showing. Animations start from here. */
    private var previewColor: Int = 0
    private var previewAnimator: ValueAnimator? = null

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
        toolbar.setBackgroundColor(AccentColor.getToolbarBackgroundColor(this))
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.title = getString(R.string.settings_theme_title)

        toggleThemeMode = findViewById(R.id.toggleThemeMode)
        toggleTimeFormat = findViewById(R.id.toggleTimeFormat)
        toggleLanguage = findViewById(R.id.toggleLanguage)

        tvThemeModeSummary = findViewById(R.id.tvThemeModeSummary)
        tvTimeFormatSummary = findViewById(R.id.tvTimeFormatSummary)
        tvThemeColorSummary = findViewById(R.id.tvThemeColorSummary)
        tvLanguageSummary = findViewById(R.id.tvLanguageSummary)

        accentPreviewPill = findViewById(R.id.accentPreviewPill)
        accentPreviewSwitch = findViewById(R.id.accentPreviewSwitch)
        accentPreviewProgress = findViewById(R.id.accentPreviewProgress)
        gridAccentSwatches = findViewById(R.id.gridAccentSwatches)

        setupThemeMode()
        setupTimeFormat()
        setupLanguage()
        setupAccentSelector()
        updateAllSummaries()
    }

    override fun onResume() {
        super.onResume()
        updateAllSummaries()
    }

    override fun onDestroy() {
        previewAnimator?.cancel()
        super.onDestroy()
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
    // Theme colour selector
    // ---------------------------------------------------------------------

    /**
     * Builds the swatch grid (presets in settings order, then Custom) and
     * shows the current accent in the preview. Preset names and values come
     * from the `pref_accent_*` arrays.
     */
    private fun setupAccentSelector() {
        val entries = resources.getStringArray(R.array.pref_accent_entries)
        val values = resources.getStringArray(R.array.pref_accent_values)

        selectedAccent = currentAccent()
        previewColor = accentColorForValue(selectedAccent)
        applyPreviewColor(previewColor)

        val items = values.mapIndexed { index, value -> value to entries.getOrElse(index) { value } } +
            (CUSTOM to getString(R.string.pref_accent_custom_title))

        items.forEachIndexed { index, (value, name) ->
            val isCustom = value == CUSTOM
            val swatch = AccentSwatchView(this).apply {
                bind(
                    name = name,
                    color = accentColorForValue(value),
                    custom = isCustom,
                    selected = value == selectedAccent,
                )
                setOnClickListener { onSwatchTapped(value) }
            }
            gridAccentSwatches.addView(
                swatch,
                GridLayout.LayoutParams(
                    GridLayout.spec(index / GRID_COLUMNS),
                    GridLayout.spec(index % GRID_COLUMNS, GridLayout.CENTER, 1f),
                ),
            )
            swatchViews[value] = swatch
        }
    }

    private fun onSwatchTapped(value: String) {
        if (value == CUSTOM) {
            showCustomColorPicker()
            return
        }
        if (value == selectedAccent) return

        val previous = swatchViews[selectedAccent]
        selectedAccent = value
        previous?.setChosen(false, SELECTION_ANIMATION_MS)
        swatchViews[value]?.setChosen(true, SELECTION_ANIMATION_MS)
        prefs.edit { putString(KEY_ACCENT, value) }
        updateThemeColorSummary()
        animatePreview(to = accentColorForValue(value))
    }

    /**
     * Animates the preview to [to], then recreates the screen so everything
     * picks up the saved accent. A cancelled animation (another tap arrived
     * first) leaves the recreate to the newer one.
     */
    private fun animatePreview(to: Int) {
        previewAnimator?.cancel()
        previewAnimator = ValueAnimator.ofArgb(previewColor, to).apply {
            duration = SELECTION_ANIMATION_MS
            addUpdateListener { animator ->
                previewColor = animator.animatedValue as Int
                applyPreviewColor(previewColor)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    recreate()
                }
            })
            start()
        }
    }

    private fun applyPreviewColor(color: Int) {
        accentPreviewPill.background = GradientDrawable().apply {
            cornerRadius = 100f * resources.displayMetrics.density
            setColor(color)
        }
        accentPreviewPill.setTextColor(
            if (AccentColor.prefersDarkText(color)) Color.BLACK else Color.WHITE
        )
        CustomAccentApplier.tintSwitch(accentPreviewSwitch, color)
        accentPreviewProgress.setIndicatorColor(color)
    }

    private fun showCustomColorPicker() {
        CustomAccentPickerDialog.show(this) {
            updateThemeColorSummary()
            recreate()
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
            CUSTOM -> {
                // Stored as #AARRGGBB; show the #RRGGBB the user typed
                val hex = prefs.getString(KEY_ACCENT_CUSTOM, "").orEmpty()
                    .let { if (it.length == 9) "#" + it.substring(3) else it }
                if (hex.isNotBlank()) getString(R.string.pref_theme_color_custom_fmt, hex)
                else getString(R.string.pref_accent_custom_title)
            }
            else -> {
                val entries = resources.getStringArray(R.array.pref_accent_entries)
                val values = resources.getStringArray(R.array.pref_accent_values)
                entries.getOrNull(values.indexOf(current)) ?: entries.firstOrNull().orEmpty()
            }
        }
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
    // Colours
    // ---------------------------------------------------------------------

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
            CUSTOM -> runCatching {
                (prefs.getString(KEY_ACCENT_CUSTOM, "#6BA6E8") ?: "#6BA6E8").toColorInt()
            }.getOrDefault(ContextCompat.getColor(this, R.color.accent_default_blue))
            else -> ContextCompat.getColor(this, R.color.accent_default_blue)
        }
    }

    private companion object {
        const val KEY_THEME_MODE = "pref_theme_mode"
        const val KEY_ACCENT = "pref_accent"
        const val KEY_ACCENT_CUSTOM = "pref_accent_custom"
        const val KEY_LANGUAGE = "pref_language"
        const val CUSTOM = "custom"
        const val GRID_COLUMNS = 5
        const val SELECTION_ANIMATION_MS = 220L
    }
}
