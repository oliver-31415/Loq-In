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

package com.oliver.loqin.feature.usage

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.oliver.loqin.R
import com.oliver.loqin.ui.PageUi
import com.oliver.loqin.ui.widgets.EmptyStateView
import com.oliver.loqin.ui.widgets.ListGroupLayout

/**
 * The shared pieces of the statistics pages (App & website usage, Screen unlocks, Active time,
 * App launches, Most used apps, Activity history and their detail pages), so every page has the
 * same range bar, summary card, chart card, grouped rows and empty state. Built on the page
 * styles in docs/EXPRESSIVE_DESIGN.md ("Cohesion rules").
 */
object StatsPageUi {

    /**
     * The range bar ("Today | Week | Month | Year | calendar"), the first thing under the top bar.
     * Add segments with [rangeSegment] / [calendarSegment]; SegmentedToggleUi owns their look.
     */
    fun rangeBar(context: Context): MaterialButtonToggleGroup = MaterialButtonToggleGroup(context).apply {
        isSingleSelection = true
        isSelectionRequired = true
        layoutParams = rangeBarParams(context)
    }

    /** Layout params of the range bar: full width, block gap to whatever follows. */
    fun rangeBarParams(context: Context): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = context.resources.getDimensionPixelSize(R.dimen.block_gap) }

    /** A text segment of the range bar. Five segments share the width, so the side padding is slim. */
    fun rangeSegment(context: Context, text: CharSequence): MaterialButton =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = View.generateViewId()
            this.text = text
            isCheckable = true
            val side = dp(context, RANGE_SEGMENT_PADDING_DP)
            setPaddingRelative(side, paddingTop, side, paddingBottom)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

    /** The icon-only calendar segment that opens the custom range picker. */
    fun calendarSegment(context: Context): MaterialButton =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = View.generateViewId()
            text = ""
            contentDescription = context.getString(R.string.activity_history_range_custom)
            icon = ContextCompat.getDrawable(context, R.drawable.calendar_month_24)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            isCheckable = true
            setPaddingRelative(0, paddingTop, 0, paddingBottom)
            layoutParams = LinearLayout.LayoutParams(
                context.resources.getDimensionPixelSize(R.dimen.stats_calendar_segment_width),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

    /**
     * The chosen custom date range with its Clear action, shown right under the range bar.
     * Returns the row; its value text is [R.id.statsCustomRangeValue].
     */
    fun customRangeRow(parent: ViewGroup, onClear: () -> Unit): View =
        LayoutInflater.from(parent.context).inflate(R.layout.stats_custom_range_row, parent, false).apply {
            findViewById<View>(R.id.statsCustomRangeClear).setOnClickListener { onClear() }
        }

    /** Summary card: [label] over the hero [value], with an optional comparison line. */
    class SummaryCard(val view: MaterialCardView) {
        val label: TextView = view.findViewById(R.id.statsSummaryLabel)
        val value: TextView = view.findViewById(R.id.statsSummaryValue)
        val comparison: TextView = view.findViewById(R.id.statsSummaryComparison)
        val trailing: ViewGroup = view.findViewById(R.id.statsSummaryTrailing)
        val content: LinearLayout = view.findViewById(R.id.statsSummaryContent)

        /** Sets the comparison line; null hides it. [more] marks "more than before" in the warning tone. */
        fun setComparison(text: CharSequence?, more: Boolean = false) {
            setComparisonLine(comparison, text, more)
        }
    }

    fun summaryCard(parent: ViewGroup, label: CharSequence, value: CharSequence): SummaryCard {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.stats_summary_card, parent, false) as MaterialCardView
        return SummaryCard(view).apply {
            this.label.text = label
            this.value.text = value
        }
    }

    /**
     * The comparison line of a summary card: neutral, or the warning tone for more use than the
     * previous period. The words carry the meaning; the tone only reinforces it.
     */
    fun setComparisonLine(view: TextView, text: CharSequence?, more: Boolean) {
        if (text.isNullOrEmpty()) {
            view.visibility = View.GONE
            return
        }
        view.text = text
        TextViewCompat.setTextAppearance(
            view,
            if (more) R.style.TextAppearance_LoqIn_RowStatus_Warning else R.style.TextAppearance_LoqIn_RowSummary,
        )
        view.visibility = View.VISIBLE
    }

    /** A chart / content card: page card with an optional title and summary; add content to [content]. */
    class ChartCard(val view: MaterialCardView) {
        val title: TextView = view.findViewById(R.id.statsCardTitle)
        val summary: TextView = view.findViewById(R.id.statsCardSummary)
        val content: LinearLayout = view.findViewById(R.id.statsCardContent)

        fun setSummary(text: CharSequence?) {
            summary.text = text
            summary.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        /** Adds chart content under the title, with the card's content gap above it. */
        fun addBody(child: View, params: LinearLayout.LayoutParams = matchWrap()) {
            val hasContentAbove = (0 until content.childCount).any { content.getChildAt(it).visibility != View.GONE }
            if (hasContentAbove) {
                params.topMargin = maxOf(params.topMargin, dp(view.context, CARD_CONTENT_GAP_DP))
            }
            content.addView(child, params)
        }
    }

    fun chartCard(parent: ViewGroup, title: CharSequence? = null): ChartCard {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.stats_chart_card, parent, false) as MaterialCardView
        return ChartCard(view).apply {
            if (!title.isNullOrEmpty()) {
                this.title.text = title
                this.title.visibility = View.VISIBLE
            }
        }
    }

    /** A section header with dynamic text (PageUi.addSectionHeader takes a string resource). */
    fun addSectionHeader(parent: ViewGroup, text: CharSequence, first: Boolean = false): TextView {
        val header = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_section_header, parent, false) as TextView
        header.text = text
        header.setTextColor(PageUi.readableAccent(parent.context))
        if (first) {
            (header.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin =
                parent.resources.getDimensionPixelSize(R.dimen.section_first_gap)
        }
        parent.addView(header)
        return header
    }

    /**
     * A grouped list on the page. [afterHeader] drops the top margin (the header above already
     * leaves its gap); otherwise the group sits one block gap below the previous card.
     */
    fun listGroup(parent: ViewGroup, afterHeader: Boolean = true): ListGroupLayout {
        val group = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_group_on_page, parent, false) as ListGroupLayout
        (group.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin =
            if (afterHeader) 0 else parent.resources.getDimensionPixelSize(R.dimen.block_gap)
        return group
    }

    /** One standard row (icon, title, optional summary, optional trailing value and chevron). */
    fun row(
        parent: ViewGroup,
        title: CharSequence,
        summary: CharSequence? = null,
        @DrawableRes iconRes: Int = 0,
        value: CharSequence? = null,
        onClick: (() -> Unit)? = null,
    ): View {
        val row = LayoutInflater.from(parent.context).inflate(R.layout.stats_list_row, parent, false)
        val icon = row.findViewById<ImageView>(R.id.statsRowIcon)
        if (iconRes != 0) {
            icon.setImageResource(iconRes)
        } else {
            icon.visibility = View.GONE
            (row.findViewById<View>(R.id.statsRowText).layoutParams as ViewGroup.MarginLayoutParams).marginStart = 0
        }
        row.findViewById<TextView>(R.id.statsRowTitle).text = title
        row.findViewById<TextView>(R.id.statsRowSummary).apply {
            text = summary
            visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        row.findViewById<TextView>(R.id.statsRowValue).apply {
            text = value
            visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        if (onClick != null) {
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener { onClick() }
            row.findViewById<View>(R.id.statsRowChevron).visibility = View.VISIBLE
        }
        return row
    }

    /** The shared empty / message state, full content width. */
    fun emptyState(
        context: Context,
        title: CharSequence,
        @DrawableRes iconRes: Int = 0,
        body: CharSequence? = null,
    ): EmptyStateView = EmptyStateView(context).apply {
        layoutParams = matchWrap()
        setTitle(title)
        setIcon(iconRes)
        setBody(body)
    }

    fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    /** Match/wrap params with the block gap above, for a card stacked under another card. */
    fun blockParams(context: Context): LinearLayout.LayoutParams = matchWrap().apply {
        topMargin = context.resources.getDimensionPixelSize(R.dimen.block_gap)
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private const val RANGE_SEGMENT_PADDING_DP = 4
    private const val CARD_CONTENT_GAP_DP = 16
}
