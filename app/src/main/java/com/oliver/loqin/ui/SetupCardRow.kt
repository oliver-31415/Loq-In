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
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/**
 * One shared "setup / permission" card used by onboarding and the Permissions screen.
 *
 * Visual language: a flat page card (LoqIn.PageCard) holding one list row in the shared row
 * anatomy (docs/EXPRESSIVE_DESIGN.md, "Cohesion rules"): 24dp readable-accent icon, RowTitle,
 * RowSummary info line, a LoqIn.RowStatus status line, and a trailing check (when satisfied)
 * or chevron. When satisfied the card takes the accent container and the status line turns
 * positive.
 *
 * Extracted from OnboardingPagerAdapter.addUnifiedOnboardingRow so both screens render
 * exactly the same component. [Holder.setState] lets long-lived screens (Permissions)
 * refresh the status in place without rebuilding the card.
 */
object SetupCardRow {

    class Holder internal constructor(
        val card: MaterialCardView,
        private val context: Context,
        private val row: LinearLayout,
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
            card.alpha = 1f
            PageUi.setRowDimmed(row, locked)
        }

        /**
         * Recolors the card for the current state. [error] renders the status line in the
         * error color instead of muted when not satisfied.
         */
        fun setState(status: CharSequence?, ok: Boolean, error: Boolean = false) {
            // Flat: a done step is a tonal accent container, a missing one the plain surface.
            val surface = ContextCompat.getColor(context, R.color.foqos_surface)
            card.setCardBackgroundColor(
                if (ok) ColorUtils.compositeColors(AccentColor.getAccentContainerColorInt(context), surface) else surface
            )

            statusView?.let { view ->
                view.text = status
                view.isVisible = !status.isNullOrEmpty()
                PageUi.setRowStatus(
                    view,
                    when {
                        ok -> PageUi.Status.POSITIVE
                        error -> PageUi.Status.ERROR
                        else -> PageUi.Status.NEUTRAL
                    },
                )
            }

            trailingView?.apply {
                if (ok) {
                    setImageResource(R.drawable.check_circle_24)
                    imageTintList = ColorStateList.valueOf(PageUi.readableAccent(context))
                } else {
                    setImageResource(R.drawable.keyboard_arrow_right_24)
                    imageTintList = ColorStateList.valueOf(
                        MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, resolveOnSurface(context))
                    )
                }
            }
        }

        /** Dims the card while keeping it tappable, matching the app's locked-row convention. */
        fun setDimmed(dimmed: Boolean, baseAlpha: Float = 1f) {
            card.alpha = baseAlpha
            PageUi.setRowDimmed(row, dimmed)
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
        val res = context.resources
        val density = res.displayMetrics.density
        fun dp(value: Float): Int = (value * density).toInt()

        val surface = ContextCompat.getColor(context, R.color.foqos_surface)
        val onSurfaceVariant = MaterialColors.getColor(
            context, com.google.android.material.R.attr.colorOnSurfaceVariant, resolveOnSurface(context)
        )
        val iconSize = res.getDimensionPixelSize(R.dimen.row_icon_size)

        val ok = spec.statusOk
        val clickable = spec.onClick != null

        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(spec.topMarginDp) }
            spec.minHeightDp?.let { minimumHeight = dp(it) }
            radius = res.getDimension(R.dimen.page_card_corner)
            cardElevation = 0f
            strokeWidth = 0
            useCompatPadding = false
            preventCornerOverlap = false
            setCardBackgroundColor(surface)
            isClickable = false
            isFocusable = false
        }

        // Same anatomy and paddings as LoqIn.ListRow.
        val row = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            minimumHeight = spec.minHeightDp?.let { dp(it) } ?: res.getDimensionPixelSize(R.dimen.row_min_height)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val vertical = res.getDimensionPixelSize(R.dimen.row_padding_vertical)
            setPaddingRelative(
                res.getDimensionPixelSize(R.dimen.row_padding_start),
                vertical,
                res.getDimensionPixelSize(R.dimen.row_padding_end),
                vertical,
            )
        }

        spec.iconRes?.let { iconRes ->
            row.addView(
                ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        marginEnd = res.getDimensionPixelSize(R.dimen.row_icon_gap)
                    }
                    setImageResource(iconRes)
                    imageTintList = ColorStateList.valueOf(PageUi.readableAccent(context))
                    importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            )
        }

        val texts = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        texts.addView(
            TextView(context).apply {
                text = spec.title
                TextViewCompat.setTextAppearance(this, R.style.TextAppearance_LoqIn_RowTitle)
            }
        )

        spec.info?.takeIf { it.isNotBlank() }?.let { info ->
            texts.addView(
                TextView(context).apply {
                    text = info
                    TextViewCompat.setTextAppearance(this, R.style.TextAppearance_LoqIn_RowSummary)
                    setPadding(0, dp(2f), 0, 0)
                }
            )
        }

        val statusView = TextView(context).apply {
            text = spec.status
            setPadding(0, dp(2f), 0, 0)
            isVisible = !spec.status.isNullOrEmpty()
        }
        texts.addView(statusView)

        row.addView(texts)

        spec.onInfoClick?.let { onInfo ->
            row.addView(
                ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48f), dp(48f)).apply { marginStart = dp(4f) }
                    setImageResource(R.drawable.info_24)
                    imageTintList = ColorStateList.valueOf(onSurfaceVariant)
                    isClickable = true
                    isFocusable = true
                    contentDescription = spec.infoContentDescription
                    setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
                    val tv = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
                    setBackgroundResource(tv.resourceId)
                    setOnClickListener { onInfo.invoke() }
                }
            )
        }

        var trailingView: ImageView? = null
        if (clickable) {
            trailingView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { marginStart = dp(8f) }
                importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(trailingView)
        }

        card.addView(row)

        val holder = Holder(card, context, row, statusView, trailingView, spec.onClick)
        holder.setState(spec.status, ok, spec.statusError)
        return holder
    }
}
