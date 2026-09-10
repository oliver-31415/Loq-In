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
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import com.oliver.loqin.R
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.dialog.styledDialogEditText
import com.oliver.loqin.ui.dialog.styleLoqInDialogButtons

/**
 * Curated-palette custom accent picker: a swatch grid with a selected check,
 * live accent/container preview, and a hex field for exact colors. Shared by
 * Appearance and developer-mode settings entry points.
 */
object CustomAccentPickerDialog {

    fun show(context: Context, onApplied: () -> Unit) {
        fun dp(v: Int): Int = (v * context.resources.displayMetrics.density + 0.5f).toInt()

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultAccent = ContextCompat.getColor(context, R.color.accent_default_blue)
        val defaultHex = String.format("#%06X", 0xFFFFFF and defaultAccent)
        val initialHex = prefs.getString("pref_accent_custom", defaultHex) ?: defaultHex
        val initial = runCatching { initialHex.toColorInt() }.getOrDefault(defaultAccent)

        var selected = initial
        var updatingHex = false

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(4))
        }

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
            )
            setText(String.format("#%06X", 0xFFFFFF and initial))
        }

        // Single drag-through spectrum (hue horizontally, white→hue→black vertically)
        val pad = SpectrumPadView(context).apply {
            setColor(initial)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(200),
            ).apply { topMargin = dp(16) }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
                }
            }
            onColorPicked = { _, _ ->
                selected = currentColor()
                updatingHex = true
                hexInput.setText(String.format("#%06X", 0xFFFFFF and selected))
                updatingHex = false
            }
        }
        root.addView(pad)
        root.addView(hexInput)
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (updatingHex) return
                val parsed = s?.toString()?.trim()?.let { runCatching { it.toColorInt() }.getOrNull() }
                if (parsed != null) {
                    selected = parsed
                    pad.setColor(parsed)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.pref_accent_custom_title))
            .setView(root)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                val parsed = hexInput.text.toString().trim().let { runCatching { it.toColorInt() }.getOrNull() }
                val color = parsed ?: selected
                val hex = String.format("#%08X", color)
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
