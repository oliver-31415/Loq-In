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

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputType
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.ui.SheetRows

/**
 * Bottom sheet for entering or choosing a value, in the same style as the profile
 * and blocking-method sheets: grab handle, bold title with a close button, tonal
 * fields, and pill buttons at the bottom.
 *
 * Add content with [message], [field], [choices] or directly to [body], then call
 * [show]. Button callbacks run without closing the sheet; call [dismiss] when the
 * input is accepted, so invalid input can keep the sheet open with an error.
 */
class InputSheet(private val context: Context, title: CharSequence) {

    val dialog = BottomSheetDialog(context)
    val accent: Int = AccentColor.getAccentColorInt(context)

    /** Content between the header and the buttons. */
    val body: LinearLayout

    private val root: LinearLayout
    private val buttons: LinearLayout
    private var focusOnShow: EditText? = null

    init {
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(20))
        }
        root.addView(SheetRows.header(context, root, title) { dismiss() })
        body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(body, matchWrap())
        buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        root.addView(buttons, matchWrap().apply { topMargin = dp(24) })

        dialog.setContentView(
            NestedScrollView(context).apply {
                setBackgroundResource(R.drawable.sheet_profile_bg)
                addView(root)
            }
        )
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        @Suppress("DEPRECATION")
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    /** Adds a ready-made view (for example an inflated layout) to the body. */
    fun content(view: View): View {
        tintFieldsIn(view)
        body.addView(view, matchWrap())
        return view
    }

    private fun tintFieldsIn(view: View) {
        if (view is TextInputLayout) tintField(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) tintFieldsIn(view.getChildAt(i))
    }

    /** Explanatory text under the title. */
    fun message(text: CharSequence): TextView = TextView(context).apply {
        this.text = text
        setTextAppearance(R.style.TextAppearance_LoqIn_RowSummary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(0f, 1.15f)
        body.addView(this, matchWrap().apply { topMargin = dp(4) })
    }

    /** A small heading above a group of inputs. */
    fun label(text: CharSequence): TextView = TextView(context).apply {
        this.text = text
        setTextAppearance(R.style.TextAppearance_LoqIn_RowTitle)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        body.addView(this, matchWrap().apply { topMargin = dp(20) })
    }

    /**
     * A tonal text field with a floating [hint]. The returned layout shows errors via
     * [TextInputLayout.setError]; its edit text is [TextInputLayout.getEditText].
     */
    fun field(
        hint: CharSequence,
        text: CharSequence? = null,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        parent: ViewGroup = body,
        focus: Boolean = false,
    ): TextInputLayout {
        val layout = LayoutInflater.from(context)
            .inflate(R.layout.sheet_input_field, parent, false) as TextInputLayout
        layout.hint = hint
        val edit = layout.findViewById<TextInputEditText>(R.id.sheetFieldInput)
        edit.inputType = inputType
        if (!text.isNullOrEmpty()) {
            edit.setText(text)
            edit.setSelection(edit.text?.length ?: 0)
        }
        // Typing clears a previous error.
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (layout.error != null) layout.error = null
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        tintField(layout)
        parent.addView(layout)
        if (focus) focusOnShow = edit
        return layout
    }

    /**
     * Segmented pill choices, as on the Appearance page. [selected] may be -1 for none.
     * Unless [required], tapping the selected choice again clears it (index -1).
     * Added to the end of [parent], or at [index] when given.
     */
    fun choices(
        labels: List<CharSequence>,
        selected: Int,
        required: Boolean = false,
        parent: ViewGroup = body,
        index: Int = -1,
        onSelect: (index: Int) -> Unit,
    ): MaterialButtonToggleGroup {
        val group = MaterialButtonToggleGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = required
        }
        val ids = labels.map { label ->
            MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId()
                text = label
                maxLines = 1
                group.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }.id
        }
        val buttonsList = ids.map { group.findViewById<MaterialButton>(it) }
        if (selected in ids.indices) group.check(ids[selected])
        SegmentedToggleUi.apply(context, buttonsList, ids.getOrElse(selected) { View.NO_ID })
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            // One uncheck + one check per change; report once, after the check.
            if (!isChecked && group.checkedButtonId != View.NO_ID) return@addOnButtonCheckedListener
            val checked = group.checkedButtonId
            SegmentedToggleUi.apply(context, buttonsList, checked)
            onSelect(ids.indexOf(checked))
        }
        parent.addView(group, index, matchWrap().apply { topMargin = dp(16) })
        return group
    }

    /** The main action: an accent pill, filling the row. */
    fun primary(text: CharSequence, onClick: () -> Unit): MaterialButton =
        addButton(text, filled = true, destructive = false, onClick = onClick)

    /** A second, quieter action placed before the primary one. */
    fun secondary(text: CharSequence, destructive: Boolean = false, onClick: () -> Unit): MaterialButton =
        addButton(text, filled = false, destructive = destructive, onClick = onClick)

    /** Focuses [edit] and raises the keyboard when the sheet opens. */
    fun focus(edit: EditText) {
        focusOnShow = edit
    }

    fun show() {
        dialog.padForNavigationBar()
        dialog.show()
        focusOnShow?.let { edit ->
            edit.requestFocus()
            edit.post {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    fun dismiss() = dialog.dismiss()

    fun setOnDismissListener(listener: () -> Unit) {
        dialog.setOnDismissListener { listener() }
    }

    private fun addButton(
        text: CharSequence,
        filled: Boolean,
        destructive: Boolean,
        onClick: () -> Unit,
    ): MaterialButton {
        val button = MaterialButton(context).apply {
            this.text = text
            isAllCaps = false
            maxLines = 2
            minHeight = dp(52)
            minimumHeight = dp(52)
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(100)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            if (filled) {
                backgroundTintList = ColorStateList.valueOf(accent)
                setTextColor(if (AccentColor.prefersDarkText(accent)) Color.BLACK else Color.WHITE)
            } else {
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.foqos_surface_container_high)
                )
                setTextColor(
                    if (destructive) ContextCompat.getColor(context, R.color.status_error)
                    else ContextCompat.getColor(context, R.color.foqos_on_surface)
                )
            }
            strokeWidth = 0
            setOnClickListener { onClick() }
        }
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        if (filled) {
            if (buttons.childCount > 0) lp.marginStart = dp(12)
            buttons.addView(button, lp)
        } else {
            // Secondary actions sit before the primary one.
            val primaryIndex = (0 until buttons.childCount)
                .firstOrNull { buttons.getChildAt(it).getTag(R.id.tag_sheet_primary) == true }
            if (primaryIndex != null) {
                buttons.getChildAt(primaryIndex).updateMarginStart(dp(12))
                buttons.addView(button, primaryIndex, lp)
            } else {
                if (buttons.childCount > 0) lp.marginStart = dp(12)
                buttons.addView(button, lp)
            }
        }
        if (filled) button.setTag(R.id.tag_sheet_primary, true)
        buttons.visibility = View.VISIBLE
        return button
    }

    private fun tintField(layout: TextInputLayout) {
        // Labels stay in the secondary text colour: the accent is often too light to read
        // as small text on the tonal field. The cursor and handles carry the accent.
        val label = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.foqos_on_surface_variant))
        layout.hintTextColor = label
        layout.defaultHintTextColor = label
        layout.setBoxStrokeColorStateList(ColorStateList.valueOf(accent))
        layout.editText?.let { edit ->
            edit.highlightColor = (accent and 0x00FFFFFF) or 0x55000000
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                edit.textCursorDrawable?.mutate()?.setTint(accent)
                edit.textSelectHandle?.mutate()?.setTint(accent)
                edit.textSelectHandleLeft?.mutate()?.setTint(accent)
                edit.textSelectHandleRight?.mutate()?.setTint(accent)
            }
        }
    }

    private fun View.updateMarginStart(value: Int) {
        (layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart = value
        requestLayout()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = SheetRows.dp(context, value.toFloat())
}
