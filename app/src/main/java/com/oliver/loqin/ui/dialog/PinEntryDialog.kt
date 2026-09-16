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
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.showWarnPill
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Shared square per-digit PIN dialog used by App lock and Emergency unlock.
 *
 * - [showVerify] renders exactly as many boxes as the stored PIN, highlights the
 *   active box, and auto-submits.
 * - [showCreate] collects a new PIN (boxes appear as you type) and then expands
 *   the same dialog with a "Re-enter PIN" row, so confirmation happens in place.
 *
 * The rows are added to the dialog BEFORE `show()`; focus is requested in
 * `onShow`. Requesting focus for a row added during `onShow` gives it a 0x0 size
 * and the IME silently refuses to open.
 */
object PinEntryDialog {

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    /** One stacked PIN row: an optional label, the boxes and a hidden input. */
    private class Row(
        private val activity: Activity,
        private val dp: (Float) -> Int,
        private val availableDp: Float,
        private val accent: Int,
        private val surfaceVariant: Int,
        private val onSurface: Int,
        private val errorColor: Int,
        private val hairline: Int,
        private val showActive: Boolean,
        private val growOnType: Boolean,
    ) {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        private val boxes = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(MAX_LENGTH))
            alpha = 0f
            // A real, non-zero size: a 0x0 editor makes the IME ignore the request.
            layoutParams = ViewGroup.LayoutParams(1, 1)
        }

        private var frames = ArrayList<FrameLayout>()
        private var digits = ArrayList<TextView>()
        private var count = 0

        var onChanged: ((String) -> Unit)? = null

        init {
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val entered = s?.toString() ?: ""
                    if (growOnType) {
                        // Grow one box per extra digit past the minimum.
                        val desired = if (entered.length <= MIN_LENGTH) {
                            MIN_LENGTH
                        } else {
                            entered.length.coerceAtMost(MAX_LENGTH)
                        }
                        if (desired != count) build(desired)
                    }
                    render()
                    onChanged?.invoke(entered)
                }
            })
            root.addView(
                FrameLayout(activity).apply {
                    addView(
                        boxes,
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    )
                    addView(input)
                    setOnClickListener { focusInput() }
                }
            )
        }

        fun addLabel(text: String) {
            root.addView(
                TextView(activity).apply {
                    this.text = text
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(onSurface)
                    alpha = 0.7f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4f), 0, dp(7f))
                },
                0
            )
        }

        fun setLocked(locked: Boolean) {
            input.isEnabled = !locked
            root.alpha = if (locked) 0.6f else 1f
        }

        fun currentLength(): Int = input.text?.length ?: 0

        fun clear() {
            input.text?.clear()
            render()
        }

        fun focusInput() {
            input.requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        fun flashError(@StringRes messageRes: Int, errorView: TextView) {
            boxes.animate()
                .translationX(dp(12f).toFloat()).setDuration(50)
                .withEndAction {
                    boxes.animate().translationX(dp(-12f).toFloat()).setDuration(50)
                        .withEndAction {
                            boxes.animate().translationX(dp(8f).toFloat()).setDuration(40)
                                .withEndAction {
                                    boxes.animate().translationX(0f).setDuration(40).start()
                                }.start()
                        }.start()
                }.start()
            for (i in 0 until count) styleBox(i, 0, isError = true)
            errorView.text = activity.getString(messageRes)
            errorView.visibility = View.VISIBLE
            Handler(Looper.getMainLooper()).postDelayed({
                clear()
                errorView.visibility = View.INVISIBLE
            }, 650)
        }

        fun build(newCount: Int) {
            boxes.removeAllViews()
            frames = ArrayList()
            digits = ArrayList()

            val marginDp = when {
                newCount <= 4 -> 8f
                newCount <= 6 -> 5f
                else -> 3f
            }
            val widthDp = minOf(50f, (availableDp - (newCount - 1) * marginDp) / newCount)
            val heightDp = widthDp * 1.12f
            val digitSize = when {
                newCount <= 4 -> 22f
                newCount <= 6 -> 20f
                else -> 17f
            }

            for (i in 0 until newCount) {
                val frame = FrameLayout(activity)
                val lp = LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)).apply {
                    if (i > 0) marginStart = dp(marginDp)
                }
                val digit = TextView(activity).apply {
                    textSize = digitSize
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(onSurface)
                    gravity = Gravity.CENTER
                }
                frame.addView(
                    digit,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                boxes.addView(frame, lp)
                frames.add(frame)
                digits.add(digit)
                styleBox(i, 0)
            }
            count = newCount
        }

        private fun render() {
            val len = input.text?.length ?: 0
            for (i in 0 until count) {
                digits[i].text = if (i < len) "\u25CF" else ""
                styleBox(i, len)
            }
        }

        private fun styleBox(index: Int, textLength: Int, isError: Boolean = false) {
            val frame = frames.getOrNull(index) ?: return
            frame.background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                when {
                    isError -> {
                        setColor(ColorUtils.setAlphaComponent(errorColor, 0x1A))
                        setStroke(dp(2f), errorColor)
                    }
                    showActive && index == textLength -> {
                        setColor(ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x1A), surfaceVariant))
                        setStroke(dp(2f), accent)
                    }
                    index < textLength -> {
                        setColor(surfaceVariant)
                        setStroke(dp(1f), ColorUtils.setAlphaComponent(accent, 0x66))
                    }
                    else -> {
                        setColor(surfaceVariant)
                        setStroke(dp(1f), hairline)
                    }
                }
            }
        }
    }

    /** Verify the stored PIN (challenge, or confirm-before-change). */
    fun showVerify(
        activity: Activity,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int,
        @StringRes incorrectRes: Int,
        verifyLength: Int,
        validator: (String) -> Boolean,
        @StringRes neutralButtonRes: Int? = null,
        neutralAction: (() -> Unit)? = null,
        onCancel: () -> Unit = {},
        onSuccess: () -> Unit,
    ): AlertDialog {
        val ui = PinUi(activity)
        val length = verifyLength.coerceAtLeast(MIN_LENGTH)
        return ui.buildDialog(
            titleRes = titleRes,
            subtitle = activity.getString(subtitleRes),
            showPositive = false,
            neutralButtonRes = neutralButtonRes,
            neutralAction = neutralAction,
            onCancel = onCancel,
        ) { errorView, _, hooks ->
            val row = ui.newRow(showActive = true, growOnType = false)
            row.build(length)
            ui.rowsContainer.addView(row.root)
            row.onChanged = { entered ->
                if (entered.length == length) {
                    if (validator(entered)) ui.complete { onSuccess() }
                    else row.flashError(incorrectRes, errorView)
                }
            }
            hooks.focus = { row.focusInput() }
        }
    }

    /**
     * Collect a new PIN, then expand the dialog with a "Re-enter PIN" row and
     * confirm the two match before [onCreated] runs.
     */
    fun showCreate(
        activity: Activity,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int,
        @StringRes tooShortRes: Int,
        @StringRes mismatchRes: Int,
        onRemove: (() -> Unit)? = null,
        onCancel: () -> Unit = {},
        onCreated: (String) -> Unit,
    ): AlertDialog {
        val ui = PinUi(activity)
        val subtitle = activity.getString(subtitleRes) + " \u00B7 " + activity.getString(R.string.pin_length_inline)
        return ui.buildDialog(
            titleRes = titleRes,
            subtitle = subtitle,
            showPositive = true,
            neutralButtonRes = if (onRemove != null) R.string.pin_action_remove else null,
            neutralAction = onRemove?.let { action -> { ui.complete { action() } } },
            neutralDestructive = onRemove != null,
            onCancel = onCancel,
        ) { errorView, positiveButton, hooks ->
            var firstPin: String? = null
            var confirmRow: Row? = null

            val newRow = ui.newRow(showActive = false, growOnType = true)
            newRow.addLabel(activity.getString(R.string.pin_label_new))
            newRow.build(MIN_LENGTH)
            ui.rowsContainer.addView(newRow.root)

            // Wire the button from onShow: AlertDialog re-installs its own
            // dismiss handler when it is shown, which would close the dialog.
            hooks.onShown = {
                val button = positiveButton()
                button?.text = activity.getString(R.string.pin_continue)
                button?.setOnClickListener {
                    val confirm = confirmRow
                    if (confirm == null) {
                        if (newRow.currentLength() < MIN_LENGTH) {
                            ui.container.showWarnPill(tooShortRes)
                            return@setOnClickListener
                        }
                        val pin = newRow.input.text?.toString().orEmpty()
                        firstPin = pin
                        newRow.setLocked(true)

                        val row = ui.newRow(showActive = true, growOnType = false)
                        row.addLabel(activity.getString(R.string.pin_label_confirm))
                        row.build(pin.length)
                        row.root.alpha = 0f
                        TransitionManager.beginDelayedTransition(ui.container, AutoTransition().setDuration(180))
                        ui.rowsContainer.addView(row.root)
                        row.root.animate().alpha(1f).setDuration(180).start()
                        confirmRow = row

                        button?.text = activity.getString(R.string.save)
                        row.onChanged = { entered ->
                            if (entered.length == pin.length) {
                                if (entered == pin) ui.complete { onCreated(pin) }
                                else row.flashError(mismatchRes, errorView)
                            }
                        }
                        // Wait for the new row to be laid out before asking the IME.
                        row.root.postDelayed({ row.focusInput() }, 80)
                    } else {
                        val entered = confirm.input.text?.toString().orEmpty()
                        if (entered.isNotEmpty() && entered == firstPin) ui.complete { onCreated(entered) }
                        else confirm.flashError(mismatchRes, errorView)
                    }
                }
            }
            hooks.focus = { newRow.focusInput() }
        }
    }

    /** Callbacks used by [PinUi.buildDialog]: [onShown] runs after the dialog is
     *  shown (safe place to wire buttons), [focus] raises the keyboard, [clear]
     *  backs the in-dialog "Clear" button. */
    private class Hooks {
        var onShown: (() -> Unit)? = null
        var focus: (() -> Unit)? = null
    }

    // ---------------------------------------------------------------------
    // Implementation
    // ---------------------------------------------------------------------

    private class PinUi(val activity: Activity) {
        val dp: (Float) -> Int = { value ->
            (value * activity.resources.displayMetrics.density + 0.5f).toInt()
        }
        val accent: Int = AccentColor.getAccentColorInt(activity)
        val surfaceVariant: Int = ContextCompat.getColor(activity, R.color.foqos_surface_variant)
        val onSurface: Int = ContextCompat.getColor(activity, R.color.foqos_on_surface)
        val errorColor: Int = Color.rgb(220, 54, 54)
        val hairline: Int = ColorUtils.setAlphaComponent(Color.WHITE, 0x1A)

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20f), dp(20f), dp(20f), dp(4f))
        }
        val rowsContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        private val availableDp: Float = run {
            val screenWidthDp = activity.resources.displayMetrics.widthPixels /
                activity.resources.displayMetrics.density
            (screenWidthDp * 0.90f) - 24f - 40f
        }

        private var dialog: AlertDialog? = null
        private var completed = false

        fun newRow(showActive: Boolean, growOnType: Boolean) = Row(
            activity = activity,
            dp = dp,
            availableDp = availableDp,
            accent = accent,
            surfaceVariant = surfaceVariant,
            onSurface = onSurface,
            errorColor = errorColor,
            hairline = hairline,
            showActive = showActive,
            growOnType = growOnType,
        )

        fun complete(action: () -> Unit) {
            if (completed) return
            completed = true
            dialog?.dismiss()
            action()
        }

        /**
         * Builds the shell, creates the dialog, lets [content] add the rows and
         * wire the buttons, then shows it. [content] returns the focus action,
         * which runs from `onShow` once the rows have been laid out.
         */
        fun buildDialog(
            @StringRes titleRes: Int,
            subtitle: String,
            showPositive: Boolean,
            @StringRes neutralButtonRes: Int?,
            neutralAction: (() -> Unit)?,
            neutralDestructive: Boolean = false,
            onCancel: () -> Unit,
            content: (errorView: TextView, positiveButton: () -> Button?, hooks: Hooks) -> Unit,
        ): AlertDialog {
            container.addView(
                FrameLayout(activity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x22), surfaceVariant))
                    }
                    addView(
                        ImageView(activity).apply {
                            setImageResource(R.drawable.lock_24)
                            ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(accent))
                        },
                        FrameLayout.LayoutParams(dp(22f), dp(22f)).apply { gravity = Gravity.CENTER }
                    )
                },
                LinearLayout.LayoutParams(dp(44f), dp(44f))
            )
            container.addView(
                TextView(activity).apply {
                    text = activity.getString(titleRes)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(onSurface)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12f), 0, dp(4f))
                }
            )
            container.addView(
                TextView(activity).apply {
                    text = subtitle
                    textSize = 13f
                    setTextColor(onSurface)
                    alpha = 0.7f
                    gravity = Gravity.CENTER
                    setPadding(dp(8f), 0, dp(8f), dp(18f))
                }
            )
            container.addView(rowsContainer)

            val errorView = TextView(activity).apply {
                textSize = 12.5f
                setTextColor(errorColor)
                gravity = Gravity.CENTER
                visibility = View.INVISIBLE
                setPadding(0, dp(12f), 0, 0)
            }
            container.addView(errorView)

            val builder = MaterialAlertDialogBuilder(activity).setView(container)
            builder.setNegativeButton(activity.getString(R.string.cancel)) { _, _ -> }
            if (showPositive) {
                builder.setPositiveButton(activity.getString(R.string.pin_continue), null)
            }
            if (neutralButtonRes != null) {
                builder.setNeutralButton(activity.getString(neutralButtonRes), null)
            }

            val created = builder.create()
            dialog = created
            created.applyLoqInDialogCorners()
            created.applyLoqInDialogWidth(0.90f)

            val hooks = Hooks()
            // getButton() only returns a real button once the dialog is shown.
            content(errorView, { created.getButton(AlertDialog.BUTTON_POSITIVE) }, hooks)

            created.setOnShowListener {
                created.styleLoqInDialogButtons()
                if (neutralButtonRes != null) {
                    created.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
                        setOnClickListener { neutralAction?.invoke() }
                        if (neutralDestructive) setTextColor(errorColor)
                    }
                }
                hooks.onShown?.invoke()
                hooks.focus?.invoke()
            }
            created.setOnDismissListener {
                if (!completed) onCancel()
            }
            created.show()
            return created
        }
    }
}
