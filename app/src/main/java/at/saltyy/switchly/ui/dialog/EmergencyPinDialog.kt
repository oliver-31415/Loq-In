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

package at.saltyy.switchly.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import at.saltyy.switchly.R
import at.saltyy.switchly.data.prefs.EmergencyPinStore
import at.saltyy.switchly.theme.AccentColor
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object EmergencyPinDialog {

    fun showEnterPin(
        activity: Activity,
        onSuccess: () -> Unit
    ): AlertDialog {
        fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

        val storedPin = EmergencyPinStore.getPin(activity).orEmpty().trim()
        val pinLength = if (storedPin.isNotEmpty()) storedPin.length else 4

        val accent = AccentColor.getAccentColorInt(activity)
        val surfaceVariant = ContextCompat.getColor(activity, R.color.foqos_surface_variant)
        val onSurface = ContextCompat.getColor(activity, R.color.foqos_on_surface)
        val errorColor = Color.rgb(220, 54, 54)

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(4))
        }

        // Header roundel with lock icon
        val roundel = FrameLayout(activity).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x22), surfaceVariant))
            }
            background = bg
            val icon = ImageView(activity).apply {
                setImageResource(R.drawable.lock_24)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(accent))
            }
            addView(
                icon,
                FrameLayout.LayoutParams(dp(22), dp(22)).apply { gravity = Gravity.CENTER }
            )
        }
        container.addView(roundel, LinearLayout.LayoutParams(dp(44), dp(44)))

        // Title
        val tvTitle = TextView(activity).apply {
            text = activity.getString(R.string.emergency_pin_enter_current_title)
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(onSurface)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        }
        container.addView(tvTitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Subtitle
        val tvSubtitle = TextView(activity).apply {
            text = activity.getString(
                if (pinLength == 4) R.string.emergency_pin_enter_current_message
                else R.string.emergency_pin_enter_current_message
            )
            textSize = 13f
            setTextColor(ColorUtils.setAlphaComponent(onSurface, 0xB0))
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), dp(16))
        }
        container.addView(tvSubtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Square digit boxes container
        val boxesLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val boxWidth = if (pinLength <= 4) dp(50) else dp(40)
        val boxHeight = dp(56)
        val boxMargin = if (pinLength <= 4) dp(6) else dp(3)
        val boxRadius = dp(14).toFloat()

        val boxes = mutableListOf<Pair<FrameLayout, TextView>>()

        for (i in 0 until pinLength) {
            val box = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(boxWidth, boxHeight).apply {
                    setMargins(boxMargin, 0, boxMargin, 0)
                }
            }
            val tvDigit = TextView(activity).apply {
                gravity = Gravity.CENTER
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(accent)
            }
            box.addView(tvDigit, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            boxes += Pair(box, tvDigit)
            boxesLayout.addView(box)
        }

        // Invisible EditText for input
        val hiddenInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(pinLength))
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.TRANSPARENT)
            isCursorVisible = false
            layoutParams = FrameLayout.LayoutParams(1, 1)
        }

        val boxesContainer = FrameLayout(activity).apply {
            addView(boxesLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            })
            addView(hiddenInput)
        }
        container.addView(boxesContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Error message
        val tvError = TextView(activity).apply {
            text = activity.getString(R.string.emergency_pin_incorrect)
            textSize = 12.5f
            setTextColor(errorColor)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(10), 0, 0)
        }
        container.addView(tvError, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun updateBoxes(text: String, isError: Boolean = false) {
            boxes.forEachIndexed { i, (box, tv) ->
                val isFilled = i < text.length
                val isCurrent = i == text.length && !isError

                val strokeColor = when {
                    isError -> errorColor
                    isCurrent -> accent
                    isFilled -> ColorUtils.setAlphaComponent(accent, 0x88)
                    else -> Color.TRANSPARENT
                }
                val strokeWidth = if (isError || isCurrent) dp(2) else if (isFilled) dp(1) else 0

                box.background = GradientDrawable().apply {
                    cornerRadius = boxRadius
                    setColor(
                        if (isFilled && !isError) {
                            ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x14), surfaceVariant)
                        } else {
                            surfaceVariant
                        }
                    )
                    if (strokeWidth > 0) {
                        setStroke(strokeWidth, strokeColor)
                    }
                }

                if (isFilled) {
                    tv.text = "●"
                    tv.setTextColor(if (isError) errorColor else accent)
                } else {
                    tv.text = ""
                }
            }
        }

        fun shakeView(v: View) {
            val d = 45L
            v.animate().translationX(-14f).setDuration(d).withEndAction {
                v.animate().translationX(14f).setDuration(d).withEndAction {
                    v.animate().translationX(-10f).setDuration(d).withEndAction {
                        v.animate().translationX(10f).setDuration(d).withEndAction {
                            v.animate().translationX(0f).setDuration(d).start()
                        }
                    }.start()
                }.start()
            }.start()
        }

        updateBoxes("", isError = false)

        boxesContainer.setOnClickListener {
            hiddenInput.requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
        }

        lateinit var dialog: AlertDialog

        fun verifyPin(pin: String) {
            if (EmergencyPinStore.matchesPin(activity, pin)) {
                updateBoxes(pin, isError = false)
                Handler(Looper.getMainLooper()).postDelayed({
                    dialog.dismiss()
                    onSuccess()
                }, 120L)
            } else {
                tvError.visibility = View.VISIBLE
                updateBoxes(pin, isError = true)
                shakeView(boxesLayout)
                Handler(Looper.getMainLooper()).postDelayed({
                    hiddenInput.text?.clear()
                    updateBoxes("", isError = false)
                }, 500L)
            }
        }

        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString().orEmpty()
                tvError.visibility = View.GONE
                updateBoxes(text, isError = false)

                val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positiveBtn?.isEnabled = text.length == pinLength

                if (text.length == pinLength) {
                    verifyPin(text)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialog = MaterialAlertDialogBuilder(activity)
            .setView(container)
            .setPositiveButton(activity.getString(R.string.ok)) { _, _ ->
                val text = hiddenInput.text?.toString().orEmpty()
                verifyPin(text)
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.styleSwitchlyDialogButtons()
            dialog.applySwitchlyDialogWidth(0.90f)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false

            hiddenInput.requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
        return dialog
    }

    fun showSetPin(
        activity: Activity,
        onSuccess: () -> Unit = {}
    ): AlertDialog {
        fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

        val pinLength = 4
        val accent = AccentColor.getAccentColorInt(activity)
        val surfaceVariant = ContextCompat.getColor(activity, R.color.foqos_surface_variant)
        val onSurface = ContextCompat.getColor(activity, R.color.foqos_on_surface)

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(4))
        }

        // Roundel with lock icon
        val roundel = FrameLayout(activity).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x22), surfaceVariant))
            }
            background = bg
            val icon = ImageView(activity).apply {
                setImageResource(R.drawable.lock_24)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(accent))
            }
            addView(
                icon,
                FrameLayout.LayoutParams(dp(22), dp(22)).apply { gravity = Gravity.CENTER }
            )
        }
        container.addView(roundel, LinearLayout.LayoutParams(dp(44), dp(44)))

        // Title
        val tvTitle = TextView(activity).apply {
            text = activity.getString(R.string.emergency_pin_title)
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(onSurface)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        }
        container.addView(tvTitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Subtitle
        val tvSubtitle = TextView(activity).apply {
            text = activity.getString(R.string.emergency_pin_message)
            textSize = 13f
            setTextColor(ColorUtils.setAlphaComponent(onSurface, 0xB0))
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), dp(16))
        }
        container.addView(tvSubtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Square boxes
        val boxesLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val boxWidth = dp(50)
        val boxHeight = dp(56)
        val boxMargin = dp(6)
        val boxRadius = dp(14).toFloat()

        val boxes = mutableListOf<Pair<FrameLayout, TextView>>()

        for (i in 0 until pinLength) {
            val box = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(boxWidth, boxHeight).apply {
                    setMargins(boxMargin, 0, boxMargin, 0)
                }
            }
            val tvDigit = TextView(activity).apply {
                gravity = Gravity.CENTER
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(accent)
            }
            box.addView(tvDigit, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            boxes += Pair(box, tvDigit)
            boxesLayout.addView(box)
        }

        val hiddenInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(pinLength))
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.TRANSPARENT)
            isCursorVisible = false
            layoutParams = FrameLayout.LayoutParams(1, 1)
        }

        val boxesContainer = FrameLayout(activity).apply {
            addView(boxesLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            })
            addView(hiddenInput)
        }
        container.addView(boxesContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun updateBoxes(text: String) {
            boxes.forEachIndexed { i, (box, tv) ->
                val isFilled = i < text.length
                val isCurrent = i == text.length

                val strokeColor = when {
                    isCurrent -> accent
                    isFilled -> ColorUtils.setAlphaComponent(accent, 0x88)
                    else -> Color.TRANSPARENT
                }
                val strokeWidth = if (isCurrent) dp(2) else if (isFilled) dp(1) else 0

                box.background = GradientDrawable().apply {
                    cornerRadius = boxRadius
                    setColor(
                        if (isFilled) {
                            ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x14), surfaceVariant)
                        } else {
                            surfaceVariant
                        }
                    )
                    if (strokeWidth > 0) {
                        setStroke(strokeWidth, strokeColor)
                    }
                }

                if (isFilled) {
                    tv.text = "●"
                    tv.setTextColor(accent)
                } else {
                    tv.text = ""
                }
            }
        }

        updateBoxes("")

        boxesContainer.setOnClickListener {
            hiddenInput.requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
        }

        lateinit var dialog: AlertDialog

        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString().orEmpty()
                updateBoxes(text)
                val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positiveBtn?.isEnabled = text.length == pinLength
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialog = MaterialAlertDialogBuilder(activity)
            .setView(container)
            .setPositiveButton(activity.getString(R.string.save)) { _, _ ->
                val pin = hiddenInput.text?.toString()?.trim().orEmpty()
                if (pin.length < 4) {
                    Toast.makeText(activity, R.string.emergency_pin_too_short, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                EmergencyPinStore.setPin(activity, pin)
                Toast.makeText(activity, R.string.emergency_pin_changed, Toast.LENGTH_SHORT).show()
                onSuccess()
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.styleSwitchlyDialogButtons()
            dialog.applySwitchlyDialogWidth(0.90f)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false

            hiddenInput.requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
        return dialog
    }
}
