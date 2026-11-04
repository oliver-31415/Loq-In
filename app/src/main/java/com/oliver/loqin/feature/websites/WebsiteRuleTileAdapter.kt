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

package com.oliver.loqin.feature.websites

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.WebsiteRuleModeStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.RuleTileStyle
import com.oliver.loqin.ui.widgets.ListGroupLayout

/** One path-rule row rendered inside a [WebsiteTile.PathsPanel] or the paths overlay sheet. */
data class WebsitePathRow(
    val rule: String,
    /** Rule is a hard block/allow entry (not a limit-only rule). */
    val isHardBlocked: Boolean,
    val limitMin: Int,
    /** Minutes per visit; 0 when unset. */
    val sessionLimitMin: Int = 0,
    /** Visits (opens) per day; 0 when unset. */
    val visitLimit: Int = 0,
    val enabled: Boolean,
    val pending: Boolean,
)

/**
 * Items of the website rules list. A host groups its host-level rule (if any) with its path
 * rules in one row of the grouped "Your rules" list; suggestions are one wrapping set of chips.
 */
sealed class WebsiteTile {
    abstract val key: String

    data class SectionHeader(
        override val key: String,
        val title: String,
        val actionLabel: String? = null,
    ) : WebsiteTile()

    data class HostTile(
        override val key: String,
        val host: String,
        /** Host-only rule string when the whole site has a rule, else null (path-only group). */
        val hostRule: String?,
        val pathRuleCount: Int,
        val totalRuleCount: Int,
        val isHardBlocked: Boolean,
        val limitMin: Int,
        val sessionLimitMin: Int = 0,
        val visitLimit: Int = 0,
        /** True when anything in the group is active (host rule or any path rule). */
        val enabled: Boolean,
        /** True when at least one path rule is enabled. */
        val pathsActive: Boolean = false,
        /** Host-level rule switch state; only meaningful when [hostRule] is not null. */
        val hostEnabled: Boolean = true,
        val pending: Boolean,
        val expanded: Boolean,
        val suggestion: WebsiteSuggestion? = null,
        /** First / last row of the "Your rules" group, for its segment corners. */
        val groupTop: Boolean = false,
        val groupBottom: Boolean = false,
    ) : WebsiteTile()

    data class SuggestionChips(
        override val key: String = "suggestion_chips",
        val suggestions: List<WebsiteSuggestion>,
    ) : WebsiteTile()

    data class PathsPanel(
        override val key: String,
        val host: String,
        val rows: List<WebsitePathRow>,
    ) : WebsiteTile()

    data class AddCustom(
        override val key: String = "add_custom",
        val query: String,
    ) : WebsiteTile()

    /** Full-width button at the bottom that brings the suggestion section back. */
    data class ShowSuggestions(
        override val key: String = "show_suggestions",
    ) : WebsiteTile()
}

/** Adapter for the website rules list: headers, the add row, rule rows and suggestion chips. */
class WebsiteRuleTileAdapter(
    private val listener: Listener,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        /** Tap a saved host tile: toggles the host rule (or the path group when there is none). */
        fun onHostToggle(host: String)
        /** Long-press a tile: open the rule editor (with delete). */
        fun onHostEdit(host: String)
        /** Limit button: open the rule editor at the limit step. */
        fun onHostLimit(host: String)
        /** Layers badge / stack area: open the path-rules overlay. */
        fun onHostExpand(host: String)
        /** Tap a tile while selection mode is active. */
        fun onHostSelection(host: String, isSuggestion: Boolean)
        /** Long-press a tile: select it and highlight it. */
        fun onHostLongPress(host: String, isSuggestion: Boolean)
        fun onPathToggle(rule: String, enabled: Boolean)
        fun onPathEdit(rule: String)
        fun onSuggestionAdd(suggestion: WebsiteSuggestion)
        fun onSuggestionQuickAdd(rule: String)
        fun onAddCustom(query: String)
        /** Section header action, e.g. hide the suggestions section. */
        fun onSectionAction(key: String)
        /** Bottom button that shows the suggestion section again. */
        fun onShowSuggestions()
        fun isSelectionMode(): Boolean
        fun isSelected(host: String): Boolean
        fun isReadOnly(): Boolean
    }

    private val items = mutableListOf<WebsiteTile>()

    fun submit(newItems: List<WebsiteTile>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun tileAt(position: Int): WebsiteTile? = items.getOrNull(position)

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is WebsiteTile.SectionHeader -> VIEW_TYPE_SECTION_HEADER
        is WebsiteTile.HostTile -> VIEW_TYPE_HOST_TILE
        is WebsiteTile.PathsPanel -> VIEW_TYPE_PATHS_PANEL
        is WebsiteTile.AddCustom -> VIEW_TYPE_ADD_CUSTOM
        is WebsiteTile.ShowSuggestions -> VIEW_TYPE_SHOW_SUGGESTIONS
        is WebsiteTile.SuggestionChips -> VIEW_TYPE_SUGGESTION_CHIPS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SECTION_HEADER ->
                HeaderVH(inflater.inflate(R.layout.item_website_section_header, parent, false))
            VIEW_TYPE_HOST_TILE ->
                HostVH(inflater.inflate(R.layout.item_website_rule_row, parent, false))
            VIEW_TYPE_SUGGESTION_CHIPS ->
                ChipsVH(inflater.inflate(R.layout.item_website_suggestion_chips, parent, false))
            VIEW_TYPE_PATHS_PANEL ->
                PanelVH(inflater.inflate(R.layout.item_website_paths_panel, parent, false))
            VIEW_TYPE_SHOW_SUGGESTIONS ->
                ShowSuggestionsVH(inflater.inflate(R.layout.item_website_show_suggestions, parent, false))
            else ->
                AddCustomVH(inflater.inflate(R.layout.item_website_add_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is WebsiteTile.SectionHeader -> (holder as HeaderVH).bind(item)
            is WebsiteTile.HostTile -> (holder as HostVH).bind(item)
            is WebsiteTile.PathsPanel -> (holder as PanelVH).bind(item)
            is WebsiteTile.AddCustom -> (holder as AddCustomVH).bind(item)
            is WebsiteTile.ShowSuggestions -> (holder as ShowSuggestionsVH).bind(item)
            is WebsiteTile.SuggestionChips -> (holder as ChipsVH).bind(item)
        }
    }

    private inner class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeader: TextView = itemView.findViewById(R.id.tvHeader)
        private val tvHeaderAction: TextView = itemView.findViewById(R.id.tvHeaderAction)
        fun bind(item: WebsiteTile.SectionHeader) {
            tvHeader.text = item.title
            val label = item.actionLabel
            if (label.isNullOrBlank()) {
                tvHeaderAction.visibility = View.GONE
                tvHeaderAction.setOnClickListener(null)
            } else {
                tvHeaderAction.visibility = View.VISIBLE
                tvHeaderAction.text = label
                tvHeaderAction.setOnClickListener { listener.onSectionAction(item.key) }
            }
        }
    }

    private inner class ShowSuggestionsVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val btnShowSuggestions: View = itemView.findViewById(R.id.btnShowSuggestions)
        fun bind(item: WebsiteTile.ShowSuggestions) {
            btnShowSuggestions.setOnClickListener { listener.onShowSuggestions() }
        }
    }

    private inner class AddCustomVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAddLabel: TextView = itemView.findViewById(R.id.tvAddLabel)
        private val ivAddIcon: ImageView = itemView.findViewById(R.id.ivAddIcon)

        fun bind(item: WebsiteTile.AddCustom) {
            val ctx = itemView.context
            val density = ctx.resources.displayMetrics.density
            tvAddLabel.text = ctx.getString(R.string.website_add_custom_tile, item.query)
            ivAddIcon.setColorFilter(RuleTileStyle.strongAccent(ctx))
            itemView.background = ListGroupLayout.segmentBackground(
                itemView, 20 * density, 20 * density, ContextCompat.getColor(ctx, R.color.foqos_surface),
            )
            itemView.setOnClickListener { listener.onAddCustom(item.query) }
        }
    }

    private inner class HostVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivSiteIcon: ImageView = itemView.findViewById(R.id.ivSiteIcon)
        private val tvLabel: TextView = itemView.findViewById(R.id.tvLabel)
        private val tvSub: TextView = itemView.findViewById(R.id.tvSub)
        private val btnLimit: ImageButton = itemView.findViewById(R.id.btnLimit)
        private val ivTrailing: ImageView = itemView.findViewById(R.id.ivTrailing)

        private var boundHost: String? = null

        fun bind(item: WebsiteTile.HostTile) {
            val ctx = itemView.context
            val density = ctx.resources.displayMetrics.density
            val strong = RuleTileStyle.strongAccent(ctx)
            val surface = ContextCompat.getColor(ctx, R.color.foqos_surface)
            val secondary = com.google.android.material.color.MaterialColors.getColor(
                itemView, android.R.attr.textColorSecondary,
            )
            val selecting = listener.isSelectionMode()
            val selected = selecting && listener.isSelected(item.host)
            val hasLimit = item.limitMin > 0 || item.sessionLimitMin > 0 || item.visitLimit > 0
            val paused = !item.enabled && !item.pending

            tvLabel.text = item.host
            boundHost = item.host
            ivSiteIcon.setImageDrawable(null)
            WebsiteIconCache.load(ctx, item.host) { drawable ->
                if (boundHost == item.host) ivSiteIcon.setImageDrawable(drawable)
            }

            // What the rule covers, with an icon so it isn't text-only: whole site, some pages,
            // paused, or its limits.
            val status = subLabel(item, ctx)
            val pages = if (item.pathRuleCount > 0) ctx.resources.getQuantityString(
                R.plurals.website_tile_page_count, item.pathRuleCount, item.pathRuleCount,
            ) else null
            tvSub.text = listOfNotNull(status.takeIf { it.isNotBlank() }, pages).joinToString(" · ")
            tvSub.visibility = if (tvSub.text.isNullOrBlank()) View.GONE else View.VISIBLE
            val statusIcon = when {
                paused -> R.drawable.toggle_off_24
                hasLimit -> R.drawable.timer_24
                item.hostRule == null || !item.hostEnabled -> R.drawable.layers_24
                else -> R.drawable.language_24
            }
            tvSub.setCompoundDrawablesRelative(
                ContextCompat.getDrawable(ctx, statusIcon)?.mutate()?.apply {
                    val size = (16 * density).toInt()
                    setBounds(0, 0, size, size)
                    setTint(secondary)
                },
                null, null, null,
            )

            val contentAlpha = when {
                item.pending -> 0.62f
                paused -> 0.55f
                else -> 1f
            }
            ivSiteIcon.alpha = contentAlpha
            tvLabel.alpha = contentAlpha

            val rowColor = if (selected) {
                ColorUtils.compositeColors(ColorUtils.setAlphaComponent(strong, 0x2E), surface)
            } else surface
            itemView.background = ListGroupLayout.segmentBackground(
                itemView,
                if (item.groupTop) 24 * density else 6 * density,
                if (item.groupBottom) 24 * density else 6 * density,
                rowColor,
            )
            (itemView.layoutParams as? RecyclerView.LayoutParams)?.let { lp ->
                val want = if (item.groupTop) 0 else (2 * density).toInt()
                if (lp.topMargin != want) {
                    lp.topMargin = want
                    itemView.layoutParams = lp
                }
            }

            btnLimit.visibility = if (selecting) View.GONE else View.VISIBLE
            btnLimit.setColorFilter(if (hasLimit) strong else secondary)
            btnLimit.alpha = if (listener.isReadOnly()) 0.45f else 1f
            ivTrailing.setImageResource(
                when {
                    !selecting -> R.drawable.keyboard_arrow_right_24
                    selected -> R.drawable.check_circle_24
                    else -> R.drawable.circle_outline_24
                }
            )
            ivTrailing.setColorFilter(if (selected) strong else secondary)

            itemView.contentDescription = listOfNotNull(item.host, tvSub.text?.toString()).joinToString(", ")
            itemView.setOnClickListener {
                if (listener.isSelectionMode()) listener.onHostSelection(item.host, false)
                else listener.onHostExpand(item.host)
            }
            itemView.setOnLongClickListener {
                listener.onHostLongPress(item.host, false)
                true
            }
            btnLimit.setOnClickListener {
                if (!listener.isReadOnly()) listener.onHostLimit(item.host)
            }
        }

        /**
         * Status line for a rule. "Blocked"/"Allowed" is shown only when the rule actually blocks
         * (or allows) right now: it is enabled, hard-blocking, and not a limit-only rule.
         * Disabled rules read "Rule disabled"; limit-only rules show their limits. A host rule
         * that is off while path rules stay on reads "Paths only".
         */
        private fun subLabel(item: WebsiteTile.HostTile, ctx: android.content.Context): String {
            if (item.suggestion != null) return ctx.getString(R.string.website_tile_suggested)
            if (item.pending) return ctx.getString(R.string.website_rule_pending)
            if (!item.enabled) return ctx.getString(R.string.website_rule_disabled)
            val limits = limitSummary(
                ctx,
                item.limitMin,
                item.sessionLimitMin,
                item.visitLimit,
            )
            if (limits.isNotEmpty()) return limits
            if (item.hostRule == null && item.pathRuleCount > 0) {
                return ctx.getString(R.string.website_tile_paths_only)
            }
            if (item.hostRule != null && !item.hostEnabled && item.pathsActive) {
                return ctx.getString(R.string.website_tile_paths_only)
            }
            if (item.isHardBlocked) {
                return ctx.getString(
                    if (isAllowMode(ctx)) R.string.website_row_whole_site_allowed else R.string.website_row_whole_site_blocked
                )
            }
            return ""
        }
    }

    /** Suggestions: one outlined "+ favicon domain" chip each; long-press selects several. */
    private inner class ChipsVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val group: com.google.android.material.chip.ChipGroup = itemView as com.google.android.material.chip.ChipGroup

        fun bind(item: WebsiteTile.SuggestionChips) {
            val ctx = itemView.context
            val density = ctx.resources.displayMetrics.density
            val strong = RuleTileStyle.strongAccent(ctx)
            val ink = com.google.android.material.color.MaterialColors.getColor(
                itemView, com.google.android.material.R.attr.colorOnSurface,
            )
            val outline = ContextCompat.getColor(ctx, R.color.foqos_outline)
            group.removeAllViews()
            for (suggestion in item.suggestions) {
                val selected = listener.isSelectionMode() && listener.isSelected(suggestion.host)
                val chip = com.google.android.material.chip.Chip(ctx).apply {
                    text = suggestion.host
                    isCheckable = false
                    chipMinHeight = 40 * density
                    shapeAppearanceModel = shapeAppearanceModel.withCornerSize(
                        com.google.android.material.shape.RelativeCornerSize(0.5f)
                    )
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                        if (selected) ColorUtils.compositeColors(ColorUtils.setAlphaComponent(strong, 0x2E), ContextCompat.getColor(ctx, R.color.foqos_surface))
                        else ContextCompat.getColor(ctx, R.color.foqos_surface)
                    )
                    chipStrokeWidth = if (selected) 0f else density
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(ColorUtils.blendARGB(outline, ink, 0.12f))
                    setTextColor(ink)
                    textSize = 14f
                    // The site's favicon leads; the trailing "+" (a check while selected) says what a tap does.
                    chipIconSize = 22 * density
                    isChipIconVisible = true
                    WebsiteIconCache.load(ctx, suggestion.host) { favicon ->
                        if (text == suggestion.host) chipIcon = favicon
                    }
                    closeIcon = ContextCompat.getDrawable(ctx, if (selected) R.drawable.check_circle_24 else R.drawable.add_24)
                    closeIconTint = android.content.res.ColorStateList.valueOf(strong)
                    closeIconSize = 20 * density
                    isCloseIconVisible = true
                    closeIconContentDescription = ctx.getString(R.string.website_suggestion_add_cd, suggestion.host)
                    setOnCloseIconClickListener { performClick() }
                    contentDescription = ctx.getString(R.string.website_suggestion_add_cd, suggestion.host)
                    setOnClickListener {
                        if (listener.isSelectionMode()) listener.onHostSelection(suggestion.host, true)
                        else listener.onSuggestionAdd(suggestion)
                    }
                    setOnLongClickListener {
                        listener.onHostLongPress(suggestion.host, true)
                        true
                    }
                }
                group.addView(chip)
            }
        }
    }

    private inner class PanelVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val pathRows: LinearLayout = itemView.findViewById(R.id.pathRows)

        fun bind(item: WebsiteTile.PathsPanel) {
            pathRows.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)
            item.rows.forEach { row ->
                val rowView = inflater.inflate(R.layout.item_website_path_rule, pathRows, false)
                bindPathRow(rowView, row)
                pathRows.addView(rowView)
            }
        }

        private fun bindPathRow(rowView: View, row: WebsitePathRow) {
            val ctx = rowView.context
            val tvPath = rowView.findViewById<TextView>(R.id.tvPath)
            val tvPathMeta = rowView.findViewById<TextView>(R.id.tvPathMeta)
            val swPathEnabled = rowView.findViewById<SwitchCompat>(R.id.swPathEnabled)
            val ivPathIcon = rowView.findViewById<ImageView>(R.id.ivPathIcon)

            tvPath.text = row.rule
            tvPathMeta.text = pathStateLabel(row, ctx)
            val alpha = when {
                row.pending -> 0.62f
                row.enabled -> 1f
                else -> 0.52f
            }
            tvPath.alpha = alpha
            tvPathMeta.alpha = if (row.enabled) 0.70f else 0.56f
            ivPathIcon.alpha = alpha

            CustomAccentApplier.tintSwitch(swPathEnabled)
            swPathEnabled.setOnCheckedChangeListener(null)
            swPathEnabled.isChecked = row.enabled
            swPathEnabled.isEnabled = true
            swPathEnabled.alpha = if (row.pending || listener.isReadOnly()) 0.5f else 1f
            swPathEnabled.setOnCheckedChangeListener { _, isChecked ->
                listener.onPathToggle(row.rule, isChecked)
            }

            rowView.setOnClickListener { listener.onPathEdit(row.rule) }
            rowView.setOnLongClickListener {
                listener.onPathEdit(row.rule)
                true
            }
        }
    }

    companion object {
        const val VIEW_TYPE_SECTION_HEADER = 0
        const val VIEW_TYPE_HOST_TILE = 1
        const val VIEW_TYPE_PATHS_PANEL = 2
        const val VIEW_TYPE_ADD_CUSTOM = 3
        const val VIEW_TYPE_SHOW_SUGGESTIONS = 4
        const val VIEW_TYPE_SUGGESTION_CHIPS = 5

        fun isAllowMode(ctx: android.content.Context): Boolean =
            WebsiteRuleModeStore.isAllowMode(
                ctx,
                ProfileStore.getCurrent(ctx) ?: "default",
            )

        /**
         * "Blocked"/"Allowed" only when the rule is enabled and hard-blocking with no limits.
         * Disabled -> "Rule disabled"; limits -> the limit summary; otherwise empty.
         */
        fun pathStateLabel(row: WebsitePathRow, ctx: android.content.Context): String {
            if (row.pending) return ctx.getString(R.string.website_rule_pending)
            if (!row.enabled) return ctx.getString(R.string.website_rule_disabled)
            val limits = limitSummary(ctx, row.limitMin, row.sessionLimitMin, row.visitLimit)
            if (limits.isNotEmpty()) return limits
            if (row.isHardBlocked) {
                return ctx.getString(
                    if (isAllowMode(ctx)) R.string.rule_allowed else R.string.rule_blocked
                )
            }
            return ""
        }

        /** Combined "30 min/day · 10 min/visit · 5 visits/day" summary; empty when no limits. */
        fun limitSummary(
            ctx: android.content.Context,
            limitMin: Int,
            sessionLimitMin: Int,
            visitLimit: Int,
        ): String {
            val parts = buildList {
                if (limitMin > 0) add(ctx.getString(R.string.daily_limit_value_format, limitMin))
                if (sessionLimitMin > 0) {
                    add(ctx.getString(R.string.limit_tile_visit_fmt, sessionLimitMin))
                }
                if (visitLimit > 0) {
                    add(ctx.getString(R.string.website_visit_limit_value_format, visitLimit))
                }
            }
            return parts.joinToString(" · ")
        }
    }
}
