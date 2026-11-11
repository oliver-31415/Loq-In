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

package com.oliver.loqin.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.ImageViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.theme.MaterialComponentsViewInflater
import com.oliver.loqin.R

/**
 * The theme's `viewInflaterClass`. Views whose `style=` is one of the shared accent-label or
 * accent-icon styles get the *readable* accent ([PageUi.readableAccent]) instead of the raw one.
 *
 * Soft accents (Soft Blue, Amber, light custom colours) are too pale as text or icons on the page,
 * and `?attr/colorPrimary` can't express "the accent, darkened until it reads", so the style alone
 * can't do it. Doing it here means every screen gets the same header and icon colour with no
 * Activity code (Settings used to call AccentContrast.strengthen; other pages didn't, which is why
 * their headers looked paler).
 */
class LoqInViewInflater : MaterialComponentsViewInflater() {

    override fun createTextView(context: Context, attrs: AttributeSet?): AppCompatTextView {
        val view = super.createTextView(context, attrs)
        if (attrs != null && attrs.styleAttribute in ACCENT_TEXT_STYLES) {
            view.setTextColor(PageUi.readableAccent(context))
        }
        return view
    }

    override fun createImageView(context: Context, attrs: AttributeSet?): AppCompatImageView {
        val view = super.createImageView(context, attrs)
        if (attrs != null && attrs.styleAttribute in ACCENT_ICON_STYLES) {
            ImageViewCompat.setImageTintList(view, ColorStateList.valueOf(PageUi.readableAccent(context)))
        }
        return view
    }

    // A text button is accent text on the page, so it needs the readable accent like a header.
    override fun createButton(context: Context, attrs: AttributeSet): AppCompatButton =
        super.createButton(context, attrs).also { tintTextButton(it, attrs) }

    // Layouts mostly spell the tag out in full, which AppCompat doesn't route to createButton.
    override fun createView(context: Context, name: String, attrs: AttributeSet): View? {
        if (name == MaterialButton::class.java.name && attrs.styleAttribute == R.style.LoqIn_TextButton) {
            return MaterialButton(context, attrs).also { tintTextButton(it, attrs) }
        }
        return super.createView(context, name, attrs)
    }

    private fun tintTextButton(view: AppCompatButton, attrs: AttributeSet) {
        if (attrs.styleAttribute != R.style.LoqIn_TextButton) return
        // Keep the disabled look the style's own colour list had.
        val accent = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(view.currentHintTextColor, PageUi.readableAccent(view.context)),
        )
        view.setTextColor(accent)
        (view as? MaterialButton)?.iconTint = accent
    }

    private companion object {
        val ACCENT_TEXT_STYLES = setOf(
            R.style.LoqIn_SectionHeader,
            R.style.LoqIn_SectionHeader_First,
            R.style.LoqIn_SectionHeader_Inline,
            R.style.LoqIn_StatValue,
        )
        val ACCENT_ICON_STYLES = setOf(
            R.style.LoqIn_RowIcon,
            R.style.LoqIn_StatIcon,
            R.style.LoqIn_EmptyStateIcon,
        )
    }
}
