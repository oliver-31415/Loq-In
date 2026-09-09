/*
 * Switchly
 * Copyright (C) 2025-2026 Saltyy
 * Copyright (C) 2026 Switchly Contributors
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

package at.saltyy.switchly.feature.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import at.saltyy.switchly.R
import at.saltyy.switchly.util.TimeFormatPrefs
import at.saltyy.switchly.theme.AccentColor
import at.saltyy.switchly.theme.CustomAccentApplier
import at.saltyy.switchly.ui.EdgeToEdgeUtils
import at.saltyy.switchly.ui.ThemeUtils
import at.saltyy.switchly.ui.dialog.SwitchlyDialogOption
import at.saltyy.switchly.ui.dialog.showSwitchlyOptionDialog
import at.saltyy.switchly.util.LocaleHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors

class AppearanceActivity : AppCompatActivity() {

    private lateinit var tvThemeModeSummary: TextView
    private lateinit var tvTimeFormatSummary: TextView
    private lateinit var tvThemeColorSummary: TextView
    private lateinit var tvLanguageSummary: TextView

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

        tvThemeModeSummary = findViewById(R.id.tvThemeModeSummary)
        tvTimeFormatSummary = findViewById(R.id.tvTimeFormatSummary)
        tvThemeColorSummary = findViewById(R.id.tvThemeColorSummary)
        tvLanguageSummary = findViewById(R.id.tvLanguageSummary)

        findViewById<View>(R.id.rowThemeMode).setOnClickListener { showThemeModeDialog() }
        findViewById<View>(R.id.rowTimeFormat).setOnClickListener { showTimeFormatDialog() }
        findViewById<View>(R.id.rowThemeColor).setOnClickListener { showThemeColorDialog() }
        findViewById<View>(R.id.rowLanguage).setOnClickListener { showLanguageDialog() }

        updateAllSummaries()
    }

    override fun onResume() {
        super.onResume()
        updateAllSummaries()
    }

    private fun updateAllSummaries() {
        updateThemeModeSummary()
        updateTimeFormatSummary()
        updateThemeColorSummary()
        updateLanguageSummary()
    }

    private fun updateThemeModeSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("pref_theme_mode", "system") ?: "system"
        tvThemeModeSummary.text = when (current) {
            "light" -> getString(R.string.pref_theme_mode_light)
            "dark" -> getString(R.string.pref_theme_mode_dark)
            else -> getString(R.string.pref_theme_mode_system)
        }
    }

    private fun updateTimeFormatSummary() {
        tvTimeFormatSummary.text = when (TimeFormatPrefs.getMode(this)) {
            "12h" -> getString(R.string.pref_time_format_12h)
            "24h" -> getString(R.string.pref_time_format_24h)
            else -> getString(R.string.pref_time_format_system)
        }
    }

    private fun updateThemeColorSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("pref_accent", "default") ?: "default"

        if (current == "custom") {
            val hex = prefs.getString("pref_accent_custom", "").orEmpty()
            tvThemeColorSummary.text = if (hex.isNotBlank()) {
                getString(R.string.pref_theme_color_custom_fmt, hex)
            } else {
                getString(R.string.pref_accent_custom_title)
            }
            return
        }

        val entries = resources.getStringArray(R.array.pref_accent_entries)
        val values = resources.getStringArray(R.array.pref_accent_values)
        val label = values.indexOf(current).let { i -> if (i in entries.indices) entries[i] else entries.firstOrNull() }
            ?: ""
        tvThemeColorSummary.text = label
    }

    private fun updateLanguageSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("pref_language", "system") ?: "system"

        val entries = resources.getStringArray(R.array.pref_language_entries)
        val values = resources.getStringArray(R.array.pref_language_values)

        val label = values.indexOf(current).let { idx ->
            if (idx in entries.indices) entries[idx] else entries.firstOrNull()
        } ?: ""

        tvLanguageSummary.text = label
    }

    private fun showThemeModeDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("pref_theme_mode", "system") ?: "system"

        val entries = arrayOf(
            getString(R.string.pref_theme_mode_system),
            getString(R.string.pref_theme_mode_light),
            getString(R.string.pref_theme_mode_dark)
        )
        val values = arrayOf("system", "light", "dark")
        val checked = values.indexOf(current).coerceAtLeast(0)

        val summaries = arrayOf(
            getString(R.string.pref_theme_mode_system_summary),
            getString(R.string.pref_theme_mode_light_summary),
            getString(R.string.pref_theme_mode_dark_summary)
        )

        showSingleSelectCheckboxDialog(
            title = getString(R.string.pref_theme_mode_title),
            entries = entries,
            checkedIndex = checked,
            summaries = summaries,
            iconRes = arrayOf(
                R.drawable.tune_24,
                R.drawable.light_mode_24,
                R.drawable.dark_mode_24
            ),
        ) { which, dialog ->
            val selected = values[which]
            prefs.edit { putString("pref_theme_mode", selected) }
            updateThemeModeSummary()

            when (selected) {
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }

            dialog.dismiss()
            recreate()
        }
    }

    private fun showTimeFormatDialog() {
        val current = TimeFormatPrefs.getMode(this)
        val entries = arrayOf(
            getString(R.string.pref_time_format_system),
            getString(R.string.pref_time_format_24h),
            getString(R.string.pref_time_format_12h)
        )
        val values = arrayOf("system", "24h", "12h")
        val checked = values.indexOf(current).coerceAtLeast(0)

        val summaries = arrayOf(
            getString(R.string.pref_time_format_system_summary),
            getString(R.string.pref_time_format_24h_summary),
            getString(R.string.pref_time_format_12h_summary)
        )

        showSingleSelectCheckboxDialog(
            title = getString(R.string.pref_time_format_title),
            entries = entries,
            checkedIndex = checked,
            summaries = summaries,
            iconDrawables = arrayOf(
                badgeDrawable("AUTO"),
                badgeDrawable("24"),
                badgeDrawable("12")
            ),
        ) { which, dialog ->
            TimeFormatPrefs.setMode(this, values[which])
            updateTimeFormatSummary()
            dialog.dismiss()
        }
    }

    private fun showThemeColorDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("pref_accent", "default") ?: "default"

        val allEntries = resources.getStringArray(R.array.pref_accent_entries)
        val allValues = resources.getStringArray(R.array.pref_accent_values)

        val entries = allEntries + getString(R.string.pref_accent_custom)
        val values = allValues + "custom"

        val checked = values.indexOf(current).let { idx -> if (idx >= 0) idx else 0 }

        val summaries = values.map { value ->
            when (value) {
                "default" -> getString(R.string.pref_accent_default_summary)
                "blue" -> getString(R.string.pref_accent_blue_summary)
                "orange" -> getString(R.string.pref_accent_orange_summary)
                "purple" -> getString(R.string.pref_accent_purple_summary)
                "pink" -> getString(R.string.pref_accent_pink_summary)
                "teal" -> getString(R.string.pref_accent_teal_summary)
                "red" -> getString(R.string.pref_accent_red_summary)
                "amber" -> getString(R.string.pref_accent_amber_summary)
                "gray" -> getString(R.string.pref_accent_gray_summary)
                "custom" -> getString(R.string.pref_accent_custom_summary)
                else -> getString(R.string.pref_theme_color_summary)
            }
        }.toTypedArray()

        showSingleSelectCheckboxDialog(
            title = getString(R.string.pref_theme_color_title),
            dialogSubtitle = getString(R.string.pref_theme_color_summary),
            entries = entries,
            checkedIndex = checked,
            summaries = summaries,
            iconDrawables = values.map { colorPreviewDrawable(accentColorForValue(this, it)) as Drawable? }.toTypedArray(),
        ) { which, dialog ->
            val selected = values[which]
            if (selected == "custom") {
                dialog.dismiss()
                showCustomColorPicker()
            } else {
                prefs.edit { putString("pref_accent", selected) }
                updateThemeColorSummary()
                dialog.dismiss()
                recreate()
            }
        }
    }

    private fun showCustomColorPicker() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val defaultAccent = AccentColor.getAccentColorInt(this)
        val defaultHex = String.format("#%06X", 0xFFFFFF and defaultAccent)
        val initialHex = prefs.getString("pref_accent_custom", defaultHex) ?: defaultHex
        var color = try { initialHex.toColorInt() } catch (_: IllegalArgumentException) { defaultAccent }

        val view = layoutInflater.inflate(R.layout.dialog_color_picker, FrameLayout(this), false)
        val preview = view.findViewById<View>(R.id.colorPreview)
        val sliderR = view.findViewById<SeekBar>(R.id.sliderR)
        val sliderG = view.findViewById<SeekBar>(R.id.sliderG)
        val sliderB = view.findViewById<SeekBar>(R.id.sliderB)

        val accentList = ColorStateList.valueOf(defaultAccent)
        sliderR.thumbTintList = accentList
        sliderR.progressTintList = accentList
        sliderG.thumbTintList = accentList
        sliderG.progressTintList = accentList
        sliderB.thumbTintList = accentList
        sliderB.progressTintList = accentList

        fun updatePreviewFromColor() { preview.setBackgroundColor(color) }
        fun updateColorFromSliders() {
            color = Color.rgb(sliderR.progress, sliderG.progress, sliderB.progress)
            updatePreviewFromColor()
        }

        sliderR.max = 255; sliderG.max = 255; sliderB.max = 255
        sliderR.progress = Color.red(color)
        sliderG.progress = Color.green(color)
        sliderB.progress = Color.blue(color)

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateColorFromSliders() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        sliderR.setOnSeekBarChangeListener(listener)
        sliderG.setOnSeekBarChangeListener(listener)
        sliderB.setOnSeekBarChangeListener(listener)
        updatePreviewFromColor()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pref_accent_custom_title))
            .setView(view)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val hex = String.format("#%08X", color)
                prefs.edit {
                    putString("pref_accent", "custom")
                    putString("pref_accent_custom", hex)
                }
                updateThemeColorSummary()
                recreate()
            }
            .create()
            .show()
    }

    private fun showLanguageDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("pref_language", "system") ?: "system"

        val entries = resources.getStringArray(R.array.pref_language_entries)
        val values = resources.getStringArray(R.array.pref_language_values)

        val checked = values.indexOf(current).let { idx -> if (idx >= 0) idx else 0 }

        val summaries = arrayOf(
            getString(R.string.pref_language_system_summary),
            getString(R.string.pref_language_en_summary),
            getString(R.string.pref_language_de_summary)
        )

        showSingleSelectCheckboxDialog(
            title = getString(R.string.pref_language_title),
            entries = entries,
            checkedIndex = checked,
            summaries = summaries,
            iconDrawables = arrayOf(
                badgeDrawable("AUTO"),
                badgeDrawable("EN"),
                badgeDrawable("DE")
            ),
        ) { which, dialog ->
            val selected = values[which]
            prefs.edit { putString("pref_language", selected) }
            LocaleHelper.setLanguage(application, selected)
            updateLanguageSummary()
            recreate()
            dialog.dismiss()
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
        onSelected: (index: Int, dialog: AlertDialog) -> Unit,
    ) {
        lateinit var dialog: AlertDialog
        dialog = showSwitchlyOptionDialog(
            title = title,
            subtitle = dialogSubtitle,
            options = entries.mapIndexed { index, label ->
                SwitchlyDialogOption(
                    title = label,
                    summary = summaries?.getOrNull(index),
                    iconRes = iconRes?.getOrNull(index),
                    iconDrawable = iconDrawables?.getOrNull(index),
                    selected = index == checkedIndex
                )
            },
            confirmSelection = true
        ) { which ->
            onSelected(which, dialog)
        }
    }

    private fun colorPreviewDrawable(color: Int): Drawable {
        val outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, 0x33000000)
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), outline)
            setSize(dp(28), dp(28))
        }
    }

    private fun badgeDrawable(text: String): Drawable {
        val accent = getCurrentAccentColor(this)
        val onAccent = if (androidx.core.graphics.ColorUtils.calculateContrast(Color.BLACK, accent) >=
            androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE, accent)
        ) Color.BLACK else Color.WHITE
        return TextBadgeDrawable(text, accent, onAccent)
    }

    private fun accentColorForValue(ctx: Context, value: String): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return when (value) {
            "blue" -> ContextCompat.getColor(ctx, R.color.accent_blue)
            "orange" -> ContextCompat.getColor(ctx, R.color.accent_orange)
            "purple" -> ContextCompat.getColor(ctx, R.color.accent_purple)
            "pink" -> ContextCompat.getColor(ctx, R.color.accent_pink)
            "teal" -> ContextCompat.getColor(ctx, R.color.accent_teal)
            "red" -> ContextCompat.getColor(ctx, R.color.accent_red)
            "amber" -> ContextCompat.getColor(ctx, R.color.accent_amber)
            "gray" -> ContextCompat.getColor(ctx, R.color.accent_gray)
            "custom" -> runCatching {
                (prefs.getString("pref_accent_custom", "#2E8B57") ?: "#2E8B57").toColorInt()
            }.getOrDefault(ContextCompat.getColor(ctx, R.color.accent_green))
            else -> ContextCompat.getColor(ctx, R.color.accent_green)
        }
    }

    private fun getCurrentAccentColor(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = prefs.getString("pref_accent", "default") ?: "default"
        return if (key == "custom") {
            val hex = prefs.getString("pref_accent_custom", "#2E8B57") ?: "#2E8B57"
            try { hex.toColorInt() } catch (_: IllegalArgumentException) { AccentColor.getAccentColorInt(context) }
        } else {
            AccentColor.getAccentColorInt(context)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private class TextBadgeDrawable(
        private val text: String,
        private val backgroundColor: Int,
        private val foregroundColor: Int
    ) : Drawable() {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = backgroundColor
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = foregroundColor
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val radius = b.width().coerceAtMost(b.height()) / 2f
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), radius, bgPaint)
            textPaint.textSize = b.height() * if (text.length > 2) 0.28f else 0.42f
            val y = b.exactCenterY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, b.exactCenterX(), y, textPaint)
        }

        override fun setAlpha(alpha: Int) {
            bgPaint.alpha = alpha
            textPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            bgPaint.colorFilter = colorFilter
            textPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
