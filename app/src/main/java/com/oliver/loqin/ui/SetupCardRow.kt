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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/**
 * One shared "setup / permission" card used by onboarding and the Permissions screen.
 *
 * Visual language: flat foqos surface, 1dp hairline, accent-washed rounded-square icon,
 * bold title, muted info line, bold status line, and a trailing check (when satisfied)
 * or chevron. When satisfied the whole card takes a soft accent wash and the status
 * text turns accent.
 *
 * Extracted from OnboardingPagerAdapter.addUnifiedOnboardingRow so both screens render
 * exactly the same component. [Holder.setState] lets long-lived screens (Permissions)
 * refresh the status in place without rebuilding the card.
 */
object SetupCardRow {

    class Holder internal constructor(
        val card: MaterialCardView,
        private val context: Context,
        private val iconView: ImageView?,
        private val iconBackground: GradientDrawable?,
        private val statusView: TextView?,
        private val trailingView: ImageView?,
        private val clickAction: (() -> Unit)?,
    ) {
        private var clickEnabled = true

        /** The status line's view, if the card has one. */
        val statusTextView: TextView? get() = statusView

        init {
            if (clickAction != null) {
                card.setOnClickListener { if (clickEnabled) clickAction.invoke() }
            }
        }

        /** Locks/unlocks the row while keeping it visible; locked rows dim but stay tappable-safe. */
        fun setLocked(locked: Boolean) {
            clickEnabled = !locked
            card.isClickable = true
            card.isFocusable = true
            val baseAlpha = 1f
            card.alpha = if (locked) baseAlpha * 0.55f else baseAlpha
        }

        /**
         * Recolors the card for the current state. [error] renders the status line in the
         * error color instead of muted when not satisfied.
         */
        fun setState(status: CharSequence?, ok: Boolean, error: Boolean = false) {
            val accent = AccentColor.getAccentColorInt(context)
            val onSurface = resolveOnSurface(context)
            val outline = ContextCompat.getColor(context, R.color.foqos_outline_variant)
            val softAccent = ColorUtils.setAlphaComponent(accent, 18)

            card.strokeColor = if (ok) ColorUtils.setAlphaComponent(accent, 150) else outline
            card.setCardBackgroundColor(if (ok) softAccent else ContextCompat.getColor(context, R.color.foqos_surface))

            iconBackground?.setColor(if (ok) ColorUtils.setAlphaComponent(accent, 38) else softAccent)
            iconView?.imageTintList = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(accent, if (ok) 255 else 225)
            )

            statusView?.let { view ->
                view.text = status
                view.setTextColor(
                    when {
                        ok -> accent
                        error -> ContextCompat.getColor(context, R.color.status_error)
                        else -> ColorUtils.setAlphaComponent(onSurface, 185)
                    }
                )
            }

            trailingView?.apply {
                if (ok) {
                    setImageResource(R.drawable.check_circle_24)
                    imageTintList = ColorStateList.valueOf(accent)
                } else {
                    setImageResource(R.drawable.keyboard_arrow_right_24)
                    imageTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, 130))
                }
            }
        }

        /** Dims the card while keeping it tappable, matching the app's locked-row convention. */
        fun setDimmed(dimmed: Boolean, baseAlpha: Float = 1f) {
            card.alpha = if (dimmed) baseAlpha * 0.55f else baseAlpha
        }
    }

    data class Spec(
        val title: CharSequence,
        @DrawableRes val iconRes: Int? = null,
        val info: CharSequence? = null,
        val status: CharSequence? = null,
        /** Satisfied / ready: shapes stroke, background, icon strength and status color. */
        val statusOk: Boolean = false,
        val statusError: Boolean = false,
        val onClick: (() -> Unit)? = null,
        /** Optional info affordance on the right that opens the "why" dialog. */
        val onInfoClick: (() -> Unit)? = null,
        val infoContentDescription: CharSequence? = null,
        val topMarginDp: Float = 8f,
        /** Optional minimum card height; onboarding uses fixed heights for a steady rhythm. */
        val minHeightDp: Float? = null,
    )

    private fun resolveOnSurface(context: Context): Int {
        val out = android.util.TypedValue()
        val resolved = context.theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnSurface, out, true)
        return if (resolved) {
            if (out.resourceId != 0) ContextCompat.getColor(context, out.resourceId)
            else if (out.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
                out.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) out.data
            else ContextCompat.getColor(context, R.color.foqos_on_surface)
        } else {
            ContextCompat.getColor(context, R.color.foqos_on_surface)
        }
    }

    fun build(context: Context, spec: Spec): Holder {
        val density = context.resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density).toInt()

        val accent = AccentColor.getAccentColorInt(context)
        val surface = ContextCompat.getColor(context, R.color.foqos_surface)
        val onSurface = resolveOnSurface(context)
        val outline = ContextCompat.getColor(context, R.color.foqos_outline_variant)

        val ok = spec.statusOk
        val clickable = spec.onClick != null

        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(spec.topMarginDp) }
            spec.minHeightDp?.let { minimumHeight = dp(it) }
            radius = dp(16f).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1f)
            setCardBackgroundColor(surface)
            isClickable = false
            isFocusable = false
        }

        val row = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            spec.minHeightDp?.let { minimumHeight = dp(it) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(11f), dp(12f), dp(11f))
        }

        var iconView: ImageView? = null
        var iconBackground: GradientDrawable? = null
        spec.iconRes?.let { iconRes ->
            iconBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14f).toFloat()
            }
            iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(38f), dp(38f)).apply { marginEnd = dp(13f) }
                background = iconBackground
                setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
                setImageResource(iconRes)
                contentDescription = null
            }
            row.addView(iconView)
        }

        val texts = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        texts.addView(
            TextView(context).apply {
                text = spec.title
                textSize = 14.8f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(onSurface)
                maxLines = Int.MAX_VALUE
                ellipsize = null
                includeFontPadding = false
            }
        )

        spec.info?.takeIf { it.isNotBlank() }?.let { info ->
            texts.addView(
                TextView(context).apply {
                    text = info
                    textSize = 12.4f
                    alpha = 0.78f
                    setTextColor(onSurface)
                    setPadding(0, dp(5f), 0, 0)
                    maxLines = Int.MAX_VALUE
                    ellipsize = null
                    includeFontPadding = false
                }
            )
        }

        var statusView: TextView? = null
        statusView = TextView(context).apply {
            text = spec.status
            textSize = 12.2f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(7f), 0, 0)
            maxLines = Int.MAX_VALUE
            ellipsize = null
            includeFontPadding = false
        }
        texts.addView(statusView)

        row.addView(texts)

        spec.onInfoClick?.let { onInfo ->
            row.addView(
                ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(42f), dp(42f)).apply { marginStart = dp(4f) }
                    setImageResource(R.drawable.info_24)
                    imageTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, 150))
                    isClickable = true
                    isFocusable = true
                    contentDescription = spec.infoContentDescription
                    setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
                    setOnClickListener { onInfo.invoke() }
                }
            )
        }

        var trailingView: ImageView? = null
        if (clickable) {
            trailingView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { marginStart = dp(6f) }
                contentDescription = null
            }
            row.addView(trailingView)
        }

        card.addView(row)

        val holder = Holder(card, context, iconView, iconBackground, statusView, trailingView, spec.onClick)
        holder.setState(spec.status, ok, spec.statusError)
        return holder
    }
}
