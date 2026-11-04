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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor

/**
 * One tile of a rule grid (grid_rule_tile), styled like the app and website tiles: a symbol on a
 * roundel, a short name, and the check badge when picked. Used by the in-app rules grid.
 */
class RuleTile(val root: MaterialCardView, private val compact: Boolean = false) {
    val icon: ImageView = root.findViewById(R.id.tileIcon)
    val label: TextView = root.findViewById(R.id.tileLabel)
    val badge: ImageView = root.findViewById(R.id.tileBadge)

    private val context get() = root.context
    private var symbolMode = false

    /** A symbol on an accent roundel, for things that have no app icon of their own. */
    fun setSymbol(@DrawableRes res: Int) {
        symbolMode = true
        val pad = SheetRows.dp(context, if (compact) 8f else 10f)
        icon.setPadding(pad, pad, pad, pad)
        icon.setImageResource(res)
        styleSymbol(on = root.isSelected)
    }

    /**
     * Off: soft accent roundel. On: a surface-coloured roundel, so the accent glyph still stands
     * out on the tile's accent wash instead of melting into it.
     */
    private fun styleSymbol(on: Boolean) {
        if (!symbolMode) return
        if (on) {
            icon.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, R.color.foqos_surface))
            }
        } else {
            icon.setBackgroundResource(R.drawable.bg_roundel_accent)
        }
        icon.imageTintList = android.content.res.ColorStateList.valueOf(RuleTileStyle.strongAccent(context))
    }

    fun setText(label: CharSequence) {
        this.label.text = label
    }

    /**
     * On = accent outline and wash, as for a selected app; pending = the lighter "change queued"
     * look; both carry the app tiles' check badge.
     */
    fun setState(on: Boolean, pending: Boolean = false) {
        RuleTileStyle.apply(root, on, pending)
        root.isSelected = on
        styleSymbol(on)
        val showBadge = on || pending
        badge.isVisible = showBadge
        if (showBadge) {
            RuleTileStyle.bindBadge(badge, pending)
        }
        if (compact) {
            label.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    /** Faded like a locked app tile; still tappable so callers can explain why. */
    fun setDimmed(dimmed: Boolean) {
        icon.alpha = if (dimmed) 0.45f else 1f
        label.alpha = if (dimmed) 0.55f else 1f
    }

    companion object {
        /** Columns for a [compact] grid: a symbol and a short name fit four across. */
        const val COMPACT_COLUMNS = 4

        /**
         * A square tile that only grows past square when its text needs it. [compact] shrinks the
         * symbol and name for four-across grids like the in-app rules.
         */
        fun inflate(parent: ViewGroup, compact: Boolean = false): RuleTile {
            val card = LayoutInflater.from(parent.context)
                .inflate(R.layout.grid_rule_tile, parent, false) as SquareCardView
            card.growToFit = true
            val tile = RuleTile(card, compact)
            if (compact) {
                val size = SheetRows.dp(card.context, 32f)
                tile.icon.layoutParams = tile.icon.layoutParams.apply {
                    width = size
                    height = size
                }
                tile.label.textSize = 11.5f
                tile.label.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                // Narrow tiles: let "Picture-in-picture" wrap at its hyphens, not mid-word.
                tile.label.hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NORMAL
                tile.label.breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
                (tile.icon.parent as View).updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = 0
                    bottomMargin = 0
                }
            }
            return tile
        }
    }
}

/** The tile outline and fill shared by every grid: apps, websites, in-app rules, schedules, breaks. */
object RuleTileStyle {
    /**
     * The picked badge: a solid accent disc with a white check (an hourglass while a change is
     * queued), so "picked" reads without relying on the tile's tint.
     */
    fun bindBadge(badge: android.widget.ImageView, pending: Boolean) {
        val ctx = badge.context
        val strong = strongAccent(ctx)
        badge.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(if (pending) ColorUtils.setAlphaComponent(strong, 0x99) else strong)
        }
        badge.setImageResource(if (pending) R.drawable.hourglass_24 else R.drawable.loqin_switch_check_16)
        // ImageViewCompat: a layout's app:tint would otherwise win over imageTintList.
        androidx.core.widget.ImageViewCompat.setImageTintList(
            badge, android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        )
        val pad = SheetRows.dp(ctx, 3f)
        badge.setPadding(pad, pad, pad, pad)
        badge.alpha = 1f
    }

    /** The accent darkened (or lightened, in dark mode) until it reads as a state on a tile. */
    fun strongAccent(ctx: android.content.Context): Int {
        val surface = ContextCompat.getColor(ctx, R.color.foqos_surface)
        val ink = if (ColorUtils.calculateLuminance(surface) > 0.5) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        return AccentColor.readableOn(AccentColor.getAccentColorInt(ctx), surface, ink, 4.0)
    }

    fun apply(card: MaterialCardView, on: Boolean, pending: Boolean = false) {
        val ctx = card.context
        val accent = AccentColor.getAccentColorInt(ctx)
        val strokePx = SheetRows.dp(ctx, 2f)
        when {
            pending -> {
                card.strokeWidth = strokePx
                card.strokeColor = ColorUtils.setAlphaComponent(accent, 0x66)
                card.setCardBackgroundColor(ColorUtils.setAlphaComponent(accent, 0x14))
            }
            on -> {
                val strong = strongAccent(ctx)
                card.strokeWidth = strokePx
                card.strokeColor = strong
                card.setCardBackgroundColor(ColorUtils.setAlphaComponent(strong, 0x2E))
            }
            else -> {
                card.strokeWidth = 0
                card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.foqos_surface))
            }
        }
    }
}

/** Binds view_rule_summary: icon roundel, headline and an optional detail line. */
object RuleSummary {
    fun bind(card: View, @DrawableRes iconRes: Int, title: CharSequence, detail: CharSequence? = null) {
        card.findViewById<ImageView>(R.id.ruleSummaryIcon).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(AccentColor.getAccentColorInt(card.context))
        }
        card.findViewById<TextView>(R.id.ruleSummaryTitle).text = title
        card.findViewById<TextView>(R.id.ruleSummaryDetail).apply {
            text = detail
            isVisible = !detail.isNullOrEmpty()
        }
    }
}
