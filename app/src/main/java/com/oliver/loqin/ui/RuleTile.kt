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

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor

/**
 * One tile of a rule grid (grid_rule_tile), styled exactly like the app and website tiles so
 * in-app rules, schedules and breaks read as the same kind of page.
 */
class RuleTile(val root: MaterialCardView) {
    val icon: ImageView = root.findViewById(R.id.tileIcon)
    val label: TextView = root.findViewById(R.id.tileLabel)
    val value: TextView = root.findViewById(R.id.tileValue)
    val sub: TextView = root.findViewById(R.id.tileSub)
    val corner: TextView = root.findViewById(R.id.tileCorner)
    val toggle: ImageButton = root.findViewById(R.id.tileToggle)
    val badge: ImageView = root.findViewById(R.id.tileBadge)

    private val context get() = root.context
    private var symbolMode = false

    /** A real app icon, drawn as-is like on the app tiles. */
    fun setAppIcon(drawable: Drawable?) {
        symbolMode = false
        icon.background = null
        icon.setPadding(0, 0, 0, 0)
        icon.imageTintList = null
        icon.setImageDrawable(drawable)
    }

    /** A symbol on an accent roundel, for things that have no app icon of their own. */
    fun setSymbol(@DrawableRes res: Int) {
        symbolMode = true
        val pad = SheetRows.dp(context, 10f)
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
        icon.imageTintList = android.content.res.ColorStateList.valueOf(AccentColor.getAccentColorInt(context))
    }

    fun setText(label: CharSequence, sub: CharSequence? = null, value: CharSequence? = null) {
        this.label.text = label
        this.sub.text = sub
        this.sub.isVisible = !sub.isNullOrEmpty()
        this.value.text = value
        this.value.isVisible = !value.isNullOrEmpty()
    }

    /** Small top-left marker (e.g. a schedule's priority "#1"); null hides it. */
    fun setCorner(text: CharSequence?) {
        corner.text = text
        corner.isVisible = !text.isNullOrEmpty()
    }

    /**
     * On = accent outline and wash, as for a selected app; pending = the lighter "change queued"
     * look. [showCheck] adds the app tiles' check badge (off for tiles that carry a toggle).
     */
    fun setState(on: Boolean, pending: Boolean = false, showCheck: Boolean = true) {
        RuleTileStyle.apply(root, on, pending)
        root.isSelected = on
        styleSymbol(on)
        val showBadge = showCheck && (on || pending) && !toggle.isVisible
        badge.isVisible = showBadge
        if (showBadge) {
            badge.setImageResource(if (pending) R.drawable.hourglass_24 else R.drawable.check_circle_24)
            badge.alpha = if (pending) 0.85f else 1f
        }
    }

    /**
     * Corner on/off control, separate from the tile tap; null hides it. Drawn as the app tiles'
     * check badge when on and an empty ring when off, so "on" looks the same on every grid.
     */
    fun setToggle(on: Boolean?, contentDescription: CharSequence? = null, onClick: (() -> Unit)? = null) {
        if (on == null) {
            toggle.isVisible = false
            toggle.setOnClickListener(null)
            return
        }
        toggle.isVisible = true
        badge.isVisible = false
        toggle.setImageResource(if (on) R.drawable.check_circle_24 else R.drawable.circle_outline_24)
        toggle.imageTintList = android.content.res.ColorStateList.valueOf(
            if (on) AccentColor.getAccentColorInt(context)
            else MaterialColors.getColor(toggle, com.google.android.material.R.attr.colorOnSurfaceVariant)
        )
        toggle.imageAlpha = if (on) 255 else 0xCC
        toggle.contentDescription = contentDescription
        androidx.core.view.ViewCompat.setStateDescription(
            toggle,
            context.getString(if (on) R.string.state_on else R.string.state_off),
        )
        toggle.setOnClickListener(onClick?.let { action -> View.OnClickListener { action() } })
    }

    /** Faded like a locked app tile; still tappable so callers can explain why. */
    fun setDimmed(dimmed: Boolean) {
        icon.alpha = if (dimmed) 0.45f else 1f
        label.alpha = if (dimmed) 0.55f else 1f
        value.alpha = if (dimmed) 0.55f else 1f
        sub.alpha = if (dimmed) 0.55f else 1f
        toggle.alpha = if (dimmed) 0.45f else 1f
    }

    companion object {
        fun inflate(parent: ViewGroup): RuleTile =
            RuleTile(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.grid_rule_tile, parent, false) as MaterialCardView
            )
    }
}

/** The tile outline and fill shared by every grid: apps, websites, in-app rules, schedules, breaks. */
object RuleTileStyle {
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
                card.strokeWidth = strokePx
                card.strokeColor = accent
                card.setCardBackgroundColor(ColorUtils.setAlphaComponent(accent, 0x30))
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
