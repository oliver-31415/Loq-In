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

package com.oliver.loqin.ui.widgets

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.widget.ImageViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.oliver.loqin.R
import com.oliver.loqin.ui.PageUi

/**
 * The one empty state: a flat page card (style LoqIn.EmptyState, full content width) with a
 * centred 24dp accent icon, a title, an optional body line and an optional tonal action.
 *
 * ```xml
 * <com.oliver.loqin.ui.widgets.EmptyStateView
 *     android:id="@+id/emptySchedules"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:emptyIcon="@drawable/schedule_24"
 *     app:emptyTitle="@string/..."
 *     app:emptyBody="@string/..."
 *     app:emptyActionText="@string/..." />
 * ```
 * On a sheet or dialog add `style="@style/LoqIn.EmptyState.OnSurface"`. From code, set the action
 * listener with [setAction]; show/hide the whole view with `visibility` as before.
 */
class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.loqinEmptyStateStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val icon: ImageView
    private val title: TextView
    private val body: TextView
    private val action: MaterialButton

    init {
        LayoutInflater.from(getContext()).inflate(R.layout.view_empty_state, this, true)
        icon = findViewById(R.id.emptyStateIcon)
        title = findViewById(R.id.emptyStateTitle)
        body = findViewById(R.id.emptyStateBody)
        action = findViewById(R.id.emptyStateAction)
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(PageUi.readableAccent(getContext())))

        val a = getContext().obtainStyledAttributes(attrs, R.styleable.EmptyStateView, defStyleAttr, 0)
        val iconRes = a.getResourceId(R.styleable.EmptyStateView_emptyIcon, 0)
        if (iconRes != 0) setIcon(iconRes)
        a.getText(R.styleable.EmptyStateView_emptyTitle)?.let { setTitle(it) }
        setBody(a.getText(R.styleable.EmptyStateView_emptyBody))
        a.getText(R.styleable.EmptyStateView_emptyActionText)?.let { action.text = it }
        a.recycle()
        syncIconGap()
    }

    fun setIcon(@DrawableRes res: Int) {
        if (res == 0) {
            icon.setImageDrawable(null)
            icon.visibility = View.GONE
        } else {
            icon.setImageResource(res)
            icon.visibility = View.VISIBLE
        }
        syncIconGap()
    }

    fun setTitle(text: CharSequence) {
        title.text = text
    }

    fun setTitle(@StringRes res: Int) {
        title.setText(res)
    }

    fun setBody(text: CharSequence?) {
        body.text = text
        body.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun setBody(@StringRes res: Int) {
        setBody(context.getText(res))
    }

    /** Shows the tonal action button with [text]; pass a null [text] to hide it. */
    fun setAction(text: CharSequence?, onClick: ((View) -> Unit)?) {
        if (text.isNullOrEmpty()) {
            action.visibility = View.GONE
            action.setOnClickListener(null)
            return
        }
        action.text = text
        action.visibility = View.VISIBLE
        action.setOnClickListener(onClick?.let { listener -> View.OnClickListener { listener(it) } })
    }

    /** Wires a click listener to the action whose label came from `app:emptyActionText`. */
    fun setOnActionClickListener(onClick: (View) -> Unit) {
        if (!action.text.isNullOrEmpty()) action.visibility = View.VISIBLE
        action.setOnClickListener { onClick(it) }
    }

    private fun syncIconGap() {
        (title.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            val want = if (icon.visibility == View.VISIBLE) resources.getDimensionPixelSize(R.dimen.section_gap) / 2 else 0
            if (lp.topMargin != want) {
                lp.topMargin = want
                title.layoutParams = lp
            }
        }
    }
}
