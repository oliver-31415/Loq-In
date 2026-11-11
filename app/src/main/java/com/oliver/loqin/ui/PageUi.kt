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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.widget.TextViewCompat
import com.oliver.loqin.R
import java.util.WeakHashMap

/**
 * Code-side twin of the shared page styles (docs/EXPRESSIVE_DESIGN.md, "Cohesion rules"), for
 * screens that build their views in Kotlin. Each helper produces exactly what the XML style does.
 */
object PageUi {

    /** Status line tone, see LoqIn.RowStatus. The words must carry the meaning; colour only helps. */
    enum class Status(val styleRes: Int) {
        NEUTRAL(R.style.TextAppearance_LoqIn_RowStatus),
        POSITIVE(R.style.TextAppearance_LoqIn_RowStatus_Positive),
        WARNING(R.style.TextAppearance_LoqIn_RowStatus_Warning),
        ERROR(R.style.TextAppearance_LoqIn_RowStatus_Error),
    }

    private val dimmedChildAlpha = WeakHashMap<View, Float>()

    /**
     * The accent as text or icon colour on the page: the raw accent, darkened (light theme) or
     * lightened (dark theme) until it reads. Follows preset and custom accents.
     */
    fun readableAccent(context: Context): Int = RuleTileStyle.strongAccent(context)

    /**
     * A LoqIn.SectionHeader added to [parent] (a vertical LinearLayout), with the page rhythm from
     * the style: 24dp above, 8dp below; [first] uses the smaller top gap of the first header.
     */
    fun addSectionHeader(parent: ViewGroup, @StringRes textRes: Int, first: Boolean = false): TextView {
        val header = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_section_header, parent, false) as TextView
        header.setText(textRes)
        // Inflaters without the theme's view inflater (e.g. application context) skip the upgrade.
        header.setTextColor(readableAccent(parent.context))
        if (first) {
            (header.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin =
                parent.resources.getDimensionPixelSize(R.dimen.section_first_gap)
        }
        parent.addView(header)
        return header
    }

    /** Same padding as style LoqIn.PageContent, for a scrolling container built in code. */
    fun applyPageContentPadding(view: View) {
        val res = view.resources
        val side = res.getDimensionPixelSize(R.dimen.page_margin)
        view.setPaddingRelative(
            side,
            res.getDimensionPixelSize(R.dimen.page_top_padding),
            side,
            res.getDimensionPixelSize(R.dimen.page_bottom_padding),
        )
        (view as? ViewGroup)?.clipToPadding = false
    }

    /** Gives a row's status line (LoqIn.RowStatus) the [status] tone. */
    fun setRowStatus(textView: TextView, status: Status) {
        TextViewCompat.setTextAppearance(textView, status.styleRes)
    }

    /**
     * Disabled / unavailable look for a row inside a ListGroupLayout: its content fades, but the
     * segment it sits on stays solid (fading the whole row also faded the white segment, which
     * read as "broken" rather than "unavailable"). The row stays clickable if it was, so it can
     * still explain why it is unavailable.
     */
    fun setRowDimmed(row: View, dimmed: Boolean) {
        row.alpha = 1f
        val group = row as? ViewGroup
        if (group == null) {
            row.alpha = if (dimmed) disabledAlpha(row.context) else 1f
            return
        }
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            val base = dimmedChildAlpha.getOrPut(child) { child.alpha }
            child.alpha = if (dimmed) base * disabledAlpha(row.context) else base
        }
    }

    /**
     * Locked look for a row that stays tappable so it can explain why it can't open (protection on,
     * feature turned off): full contrast, with a lock in place of the trailing chevron. Dimming
     * such rows read as "disabled" (see [setRowDimmed] for rows that really are disabled).
     * [stateDescription] is announced by TalkBack while locked.
     */
    fun setRowLocked(row: View, locked: Boolean, stateDescription: CharSequence? = null) {
        row.isEnabled = true
        row.alpha = 1f
        setRowDimmed(row, false)
        ((row as? ViewGroup)?.let { it.getChildAt(it.childCount - 1) } as? ImageView)
            ?.setImageResource(if (locked) R.drawable.lock_24 else R.drawable.keyboard_arrow_right_24)
        ViewCompat.setStateDescription(row, if (locked) stateDescription else null)
    }

    fun disabledAlpha(context: Context): Float =
        context.resources.getFraction(R.fraction.row_disabled_alpha, 1, 1)
}
