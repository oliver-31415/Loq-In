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

package com.oliver.loqin.feature.theme

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import com.oliver.loqin.R
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.dialog.styledDialogEditText
import com.oliver.loqin.ui.dialog.styleLoqInDialogButtons
import com.google.android.material.color.MaterialColors

/**
 * Curated-palette custom accent picker: a swatch grid with a selected check,
 * live accent/container preview, and a hex field for exact colors. Shared by
 * Appearance and developer-mode settings entry points.
 */
object CustomAccentPickerDialog {

    private val PALETTE = intArrayOf(
        0xFF6BA6E8.toInt(), 0xFF4C7FE0.toInt(), 0xFF2D5FB8.toInt(), 0xFF8FB8F2.toInt(),
        0xFF4FB6A8.toInt(), 0xFF2E8B7A.toInt(), 0xFF66BB6A.toInt(), 0xFF43A047.toInt(),
        0xFF2E7D32.toInt(), 0xFF9CCC65.toInt(), 0xFFF5B942.toInt(), 0xFFE8A212.toInt(),
        0xFFF19A4D.toInt(), 0xFFE07B39.toInt(), 0xFFD2570F.toInt(), 0xFFE57373.toInt(),
        0xFFD9534F.toInt(), 0xFFB8392F.toInt(), 0xFFF08BB4.toInt(), 0xFFD9569A.toInt(),
        0xFF9F7AEA.toInt(), 0xFF8B5CF6.toInt(), 0xFF6D3FC0.toInt(), 0xFFB39DDB.toInt(),
    )

    fun show(context: Context, onApplied: () -> Unit) {
        fun dp(v: Int): Int = (v * context.resources.displayMetrics.density + 0.5f).toInt()

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultAccent = ContextCompat.getColor(context, R.color.accent_default_blue)
        val defaultHex = String.format("#%06X", 0xFFFFFF and defaultAccent)
        val initialHex = prefs.getString("pref_accent_custom", defaultHex) ?: defaultHex
        var selected = runCatching { initialHex.toColorInt() }.getOrDefault(defaultAccent)

        val onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(4))
        }

        // Live preview: accent dot + the container wash it produces
        fun previewDot(colorProvider: () -> Int): View = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorProvider())
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) }
        }
        val accentDot = previewDot { selected }
        val containerDot = previewDot { androidx.core.graphics.ColorUtils.setAlphaComponent(selected, 0x2E) }
        val previewRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(accentDot)
            addView(containerDot)
        }
        root.addView(previewRow)

        fun updatePreview() {
            (accentDot.background as GradientDrawable).setColor(selected)
            (containerDot.background as GradientDrawable).setColor(
                androidx.core.graphics.ColorUtils.setAlphaComponent(selected, 0x2E)
            )
        }

        fun isLight(color: Int): Boolean =
            (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) > 186

        fun applySwatchState(swatch: View, check: ImageView, color: Int, selectedNow: Boolean) {
            (swatch.background as GradientDrawable).apply {
                setColor(color)
                setStroke(
                    if (selectedNow) dp(3) else 0,
                    if (isLight(color)) onSurface else Color.WHITE,
                )
            }
            check.setColorFilter(if (isLight(color)) onSurface else Color.WHITE, PorterDuff.Mode.SRC_IN)
            check.visibility = if (selectedNow) View.VISIBLE else View.GONE
        }

        val grid = GridLayout(context).apply {
            columnCount = 6
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(18) }
        }
        val entries = mutableListOf<Triple<Int, View, ImageView>>()
        PALETTE.forEachIndexed { index, color ->
            val cell = FrameLayout(context).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(44); height = dp(44)
                    columnSpec = GridLayout.spec(index % 6)
                    rowSpec = GridLayout.spec(index / 6)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
            }
            val swatch = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            }
            val check = ImageView(context).apply {
                setImageResource(R.drawable.check_circle_24)
                layoutParams = FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER)
                visibility = View.GONE
            }
            cell.setOnClickListener {
                selected = color
                updatePreview()
                entries.forEach { (c, s, ch) -> applySwatchState(s, ch, c, c == color) }
            }
            cell.addView(swatch)
            cell.addView(check)
            entries.add(Triple(color, swatch, check))
            grid.addView(cell)
        }
        root.addView(grid)

        // Hex field for exact input
        val hexInput: EditText = context.styledDialogEditText().apply {
            hint = context.getString(R.string.color_hex_hint)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(7))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(18) }
            setText(initialHex)
        }
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val parsed = s?.toString()?.trim()?.let { runCatching { it.toColorInt() }.getOrNull() }
                if (parsed != null) {
                    selected = parsed
                    updatePreview()
                    entries.forEach { (_, _, ch) -> ch.visibility = View.GONE }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        root.addView(hexInput)

        entries.forEach { (c, s, ch) -> applySwatchState(s, ch, c, c == selected) }

        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.pref_accent_custom_title))
            .setView(root)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                val parsed = hexInput.text.toString().trim().let { runCatching { it.toColorInt() }.getOrNull() }
                if (parsed == null) {
                    Toast.makeText(context, context.getString(R.string.color_hex_invalid), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val hex = String.format("#%08X", parsed)
                prefs.edit {
                    putString("pref_accent", "custom")
                    putString("pref_accent_custom", hex)
                }
                onApplied()
            }
            .create()

        dialog.setOnShowListener {
            dialog.styleLoqInDialogButtons()
            runCatching { CustomAccentApplier.applyToDialog(dialog) }
        }
        dialog.show()
    }
}
