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

package com.oliver.loqin.ui.dialog

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
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.EmergencyPinStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.showWarnPill
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Modern square per-digit PIN dialogs for Emergency Unlock.
 * Features dynamic digit count, square boxes with rounded corners, masked bullet display,
 * active box accent highlighting, horizontal shake animation on error, and automatic verification.
 */
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
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        }
        container.addView(tvTitle)

        // Subtitle
        val tvSubtitle = TextView(activity).apply {
            text = activity.getString(
                if (pinLength == 4) R.string.emergency_pin_enter_current_message
                else R.string.emergency_pin_enter_current_message
            )
            textSize = 13f
            setTextColor(onSurface)
            alpha = 0.7f
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), dp(18))
        }
        container.addView(tvSubtitle)

        // Square digit boxes container
        val boxesLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val boxWidth = if (pinLength <= 4) dp(50) else dp(42)
        val boxHeight = if (pinLength <= 4) dp(56) else dp(48)
        val boxMargin = if (pinLength <= 4) dp(8) else dp(5)

        val digitViews = ArrayList<TextView>()
        val boxFrames = ArrayList<FrameLayout>()

        fun styleBox(index: Int, textLength: Int, isError: Boolean = false) {
            val frame = boxFrames[index]
            val bg = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                if (isError) {
                    setColor(ColorUtils.setAlphaComponent(errorColor, 0x1A))
                    setStroke(dp(2), errorColor)
                } else if (index == textLength) {
                    // Active box
                    setColor(ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x1A), surfaceVariant))
                    setStroke(dp(2), accent)
                } else if (index < textLength) {
                    // Filled box
                    setColor(surfaceVariant)
                    setStroke(dp(1), ColorUtils.setAlphaComponent(accent, 0x66))
                } else {
                    // Empty box
                    setColor(surfaceVariant)
                    setStroke(dp(1), ColorUtils.setAlphaComponent(Color.WHITE, 0x1A))
                }
            }
            frame.background = bg
        }

        for (i in 0 until pinLength) {
            val frame = FrameLayout(activity)
            val lp = LinearLayout.LayoutParams(boxWidth, boxHeight).apply {
                if (i > 0) marginStart = boxMargin
            }
            val digitTv = TextView(activity).apply {
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(onSurface)
                gravity = Gravity.CENTER
            }
            frame.addView(digitTv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            boxesLayout.addView(frame, lp)

            boxFrames.add(frame)
            digitViews.add(digitTv)
        }

        for (i in 0 until pinLength) {
            styleBox(i, 0)
        }

        // Hidden EditText capturing keyboard input
        val hiddenInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(pinLength))
            alpha = 0f
            // Minimal height so it doesn't shift layout
            layoutParams = ViewGroup.LayoutParams(1, 1)
        }

        val inputOverlay = FrameLayout(activity).apply {
            addView(boxesLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(hiddenInput)
            setOnClickListener {
                hiddenInput.requestFocus()
                val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        container.addView(inputOverlay)

        // Error message text view
        val tvError = TextView(activity).apply {
            textSize = 12.5f
            setTextColor(errorColor)
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            setPadding(0, dp(12), 0, 0)
        }
        container.addView(tvError)

        var dialog: AlertDialog? = null

        fun shakeAndReset() {
            // Shake animation
            boxesLayout.animate()
                .translationX(dp(12).toFloat())
                .setDuration(50)
                .withEndAction {
                    boxesLayout.animate()
                        .translationX(-dp(12).toFloat())
                        .setDuration(50)
                        .withEndAction {
                            boxesLayout.animate()
                                .translationX(dp(8).toFloat())
                                .setDuration(40)
                                .withEndAction {
                                    boxesLayout.animate()
                                        .translationX(0f)
                                        .setDuration(40)
                                        .start()
                                }
                                .start()
                        }
                        .start()
                }
                .start()

            for (i in 0 until pinLength) {
                styleBox(i, 0, isError = true)
            }

            tvError.text = activity.getString(R.string.emergency_pin_incorrect)
            tvError.visibility = View.VISIBLE

            Handler(Looper.getMainLooper()).postDelayed({
                hiddenInput.text?.clear()
                for (i in 0 until pinLength) {
                    digitViews[i].text = ""
                    styleBox(i, 0)
                }
                tvError.visibility = View.INVISIBLE
            }, 650)
        }

        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val entered = s?.toString() ?: ""
                val len = entered.length

                for (i in 0 until pinLength) {
                    digitViews[i].text = if (i < len) "●" else ""
                    styleBox(i, len)
                }

                if (len == pinLength) {
                    if (EmergencyPinStore.matchesPin(activity, entered)) {
                        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(hiddenInput.windowToken, 0)
                        // Dismissing here would detach the input while this text
                        // dispatch is still running: detach unregisters a watcher
                        // mid-dispatch and crashes with IndexOutOfBoundsException.
                        // Defer past the dispatch.
                        hiddenInput.post {
                            dialog?.dismiss()
                            onSuccess()
                        }
                    } else {
                        shakeAndReset()
                    }
                }
            }
        })

        dialog = MaterialAlertDialogBuilder(activity)
            .setView(container)
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .create()

        dialog.applyLoqInDialogWidth(0.90f)
        dialog.setOnShowListener {
            dialog.styleLoqInDialogButtons()

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

        // Header roundel
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
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        }
        container.addView(tvTitle)

        // Subtitle
        val tvSubtitle = TextView(activity).apply {
            text = activity.getString(R.string.emergency_pin_message)
            textSize = 13f
            setTextColor(onSurface)
            alpha = 0.7f
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), dp(18))
        }
        container.addView(tvSubtitle)

        // Digit boxes
        val boxesLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val boxWidth = dp(50)
        val boxHeight = dp(56)
        val boxMargin = dp(8)

        val digitViews = ArrayList<TextView>()
        val boxFrames = ArrayList<FrameLayout>()

        fun styleBox(index: Int, textLength: Int) {
            val frame = boxFrames[index]
            val bg = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                if (index == textLength) {
                    setColor(ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x1A), surfaceVariant))
                    setStroke(dp(2), accent)
                } else if (index < textLength) {
                    setColor(surfaceVariant)
                    setStroke(dp(1), ColorUtils.setAlphaComponent(accent, 0x66))
                } else {
                    setColor(surfaceVariant)
                    setStroke(dp(1), ColorUtils.setAlphaComponent(Color.WHITE, 0x1A))
                }
            }
            frame.background = bg
        }

        for (i in 0 until pinLength) {
            val frame = FrameLayout(activity)
            val lp = LinearLayout.LayoutParams(boxWidth, boxHeight).apply {
                if (i > 0) marginStart = boxMargin
            }
            val digitTv = TextView(activity).apply {
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(onSurface)
                gravity = Gravity.CENTER
            }
            frame.addView(digitTv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            boxesLayout.addView(frame, lp)

            boxFrames.add(frame)
            digitViews.add(digitTv)
            styleBox(i, 0)
        }

        val hiddenInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(pinLength))
            alpha = 0f
            layoutParams = ViewGroup.LayoutParams(1, 1)
        }

        val inputOverlay = FrameLayout(activity).apply {
            addView(boxesLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(hiddenInput)
            setOnClickListener {
                hiddenInput.requestFocus()
                val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        container.addView(inputOverlay)

        var dialog: AlertDialog? = null

        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val entered = s?.toString() ?: ""
                val len = entered.length

                for (i in 0 until pinLength) {
                    digitViews[i].text = if (i < len) "●" else ""
                    styleBox(i, len)
                }

                dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = (len == pinLength)
            }
        })

        dialog = MaterialAlertDialogBuilder(activity)
            .setView(container)
            .setPositiveButton(activity.getString(R.string.save)) { _, _ ->
                val pin = hiddenInput.text?.toString()?.trim().orEmpty()
                if (pin.length < 4) {
                    container.showWarnPill(R.string.emergency_pin_too_short)
                    return@setPositiveButton
                }
                EmergencyPinStore.setPin(activity, pin)
                container.showWarnPill(R.string.emergency_pin_changed)
                onSuccess()
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .create()

        dialog.applyLoqInDialogWidth(0.90f)
        dialog.setOnShowListener {
            dialog.styleLoqInDialogButtons()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false

            hiddenInput.requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
        return dialog
    }

    /**
     * Complete change emergency PIN flow:
     * - If a PIN is currently stored, prompt to verify the current PIN first.
     * - Then prompt to set the new PIN.
     * - If no PIN exists yet, prompt directly to set a new PIN.
     */
    fun showChangePinFlow(
        activity: Activity,
        onComplete: () -> Unit = {}
    ) {
        if (EmergencyPinStore.hasPin(activity)) {
            showEnterPin(activity) {
                showSetPin(activity, onComplete)
            }
        } else {
            showSetPin(activity, onComplete)
        }
    }
}
