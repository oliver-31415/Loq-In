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
import com.google.android.material.card.MaterialCardView
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.WebsiteRuleModeStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.StackSquareCardView

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
 * Grid tiles for website rules. A host groups its host-level rule (if any) with its path rules:
 * the square tile shows the favicon and stack depth. Section headers and the "add custom" tile
 * round out the list.
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

/**
 * Adapter for the website rule grid. Section headers and expanded path panels span the full
 * three-column grid; host tiles and the "add custom" tile occupy one cell.
 */
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SECTION_HEADER ->
                HeaderVH(inflater.inflate(R.layout.item_website_section_header, parent, false))
            VIEW_TYPE_HOST_TILE ->
                HostVH(inflater.inflate(R.layout.grid_website_tile, parent, false))
            VIEW_TYPE_PATHS_PANEL ->
                PanelVH(inflater.inflate(R.layout.item_website_paths_panel, parent, false))
            VIEW_TYPE_SHOW_SUGGESTIONS ->
                ShowSuggestionsVH(inflater.inflate(R.layout.item_website_show_suggestions, parent, false))
            else ->
                AddCustomVH(inflater.inflate(R.layout.item_website_add_custom, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is WebsiteTile.SectionHeader -> (holder as HeaderVH).bind(item)
            is WebsiteTile.HostTile -> (holder as HostVH).bind(item)
            is WebsiteTile.PathsPanel -> (holder as PanelVH).bind(item)
            is WebsiteTile.AddCustom -> (holder as AddCustomVH).bind(item)
            is WebsiteTile.ShowSuggestions -> (holder as ShowSuggestionsVH).bind(item)
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
            tvAddLabel.text = ctx.getString(R.string.website_add_custom_tile, item.query)
            ivAddIcon.setColorFilter(AccentColor.getAccentColorInt(ctx))
            itemView.setOnClickListener { listener.onAddCustom(item.query) }
        }
    }

    private inner class HostVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowRoot: StackSquareCardView = itemView.findViewById(R.id.rowRoot)
        private val ivSiteIcon: ImageView = itemView.findViewById(R.id.ivSiteIcon)
        private val tvLabel: TextView = itemView.findViewById(R.id.tvLabel)
        private val tvSub: TextView = itemView.findViewById(R.id.tvSub)
        private val btnLimit: ImageButton = itemView.findViewById(R.id.btnLimit)
        private val viewLimitDot: View = itemView.findViewById(R.id.viewLimitDot)
        private val btnPages: ImageButton = itemView.findViewById(R.id.btnPages)
        private val tvPagesCount: TextView = itemView.findViewById(R.id.tvPagesCount)
        private val ivChecked: ImageView = itemView.findViewById(R.id.ivChecked)
        private val ivSuggested: ImageView = itemView.findViewById(R.id.ivSuggested)

        private var boundHost: String? = null

        fun bind(item: WebsiteTile.HostTile) {
            val ctx = itemView.context
            val accent = AccentColor.getAccentColorInt(ctx)
            val selecting = listener.isSelectionMode()
            val selected = selecting && listener.isSelected(item.host)
            val isSuggestion = item.suggestion != null
            val hasLimit = item.limitMin > 0 || item.sessionLimitMin > 0 || item.visitLimit > 0

            tvLabel.text = item.host
            boundHost = item.host
            ivSiteIcon.setImageDrawable(null)
            WebsiteIconCache.load(ctx, item.host) { drawable ->
                if (boundHost == item.host) ivSiteIcon.setImageDrawable(drawable)
            }

            tvSub.text = subLabel(item, ctx)
            tvSub.visibility = if (tvSub.text.isNullOrBlank()) View.GONE else View.VISIBLE

            rowRoot.stackDepth = (item.totalRuleCount - 1).coerceIn(0, StackSquareCardView.MAX_DEPTH)

            // Tick means "selected in multi-select", never "rule is active". On suggestion
            // tiles it replaces the plus badge so the two never overlap.
            ivChecked.visibility = if (selected) View.VISIBLE else View.GONE
            ivSuggested.visibility = if (isSuggestion && !selected) View.VISIBLE else View.GONE

            btnLimit.visibility = if (selecting || isSuggestion) View.GONE else View.VISIBLE
            viewLimitDot.visibility =
                if (!isSuggestion && hasLimit) View.VISIBLE else View.GONE
            btnLimit.setColorFilter(accent)
            btnLimit.alpha = if (listener.isReadOnly()) 0.45f else 1f

            val hasPaths = item.pathRuleCount > 0
            btnPages.visibility = if (hasPaths) View.VISIBLE else View.GONE
            tvPagesCount.visibility = if (hasPaths) View.VISIBLE else View.GONE
            tvPagesCount.text = ctx.resources.getQuantityString(
                R.plurals.website_tile_page_count,
                item.pathRuleCount,
                item.pathRuleCount,
            )
            btnPages.setColorFilter(accent)

            val contentAlpha = when {
                item.pending -> 0.62f
                isSuggestion -> 1f
                item.enabled -> 1f
                else -> 0.52f
            }
            ivSiteIcon.alpha = contentAlpha
            tvLabel.alpha = contentAlpha
            tvSub.alpha = if (item.enabled) 0.70f else 0.56f

            val density = ctx.resources.displayMetrics.density
            (rowRoot as? MaterialCardView)?.let { card ->
                val defaultStroke = ContextCompat.getColor(ctx, R.color.foqos_outline_variant)
                val defaultBg = ContextCompat.getColor(ctx, R.color.foqos_surface)
                val accentActive = !isSuggestion && item.enabled
                card.strokeColor = when {
                    selected -> accent
                    accentActive -> ColorUtils.setAlphaComponent(accent, 0x88)
                    else -> defaultStroke
                }
                card.strokeWidth = when {
                    selected -> (2.5f * density).toInt()
                    accentActive -> (1.5f * density).toInt()
                    else -> density.toInt()
                }
                card.setCardBackgroundColor(
                    if (selected) ColorUtils.setAlphaComponent(accent, 0x40) else defaultBg
                )
            }
            // Unselected tiles step back during selection so the chosen one is unmistakable.
            rowRoot.alpha =
                if (listener.isSelectionMode() && !selected && !isSuggestion) 0.4f else 1f

            itemView.setOnClickListener {
                when {
                    listener.isSelectionMode() -> listener.onHostSelection(item.host, isSuggestion)
                    isSuggestion -> item.suggestion?.let { listener.onSuggestionAdd(it) }
                    else -> listener.onHostToggle(item.host)
                }
            }
            itemView.setOnLongClickListener {
                listener.onHostLongPress(item.host, isSuggestion)
                true
            }
            btnLimit.setOnClickListener {
                if (!listener.isReadOnly()) listener.onHostLimit(item.host)
            }
            btnPages.setOnClickListener { listener.onHostExpand(item.host) }
            tvPagesCount.setOnClickListener { listener.onHostExpand(item.host) }
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
                    if (isAllowMode(ctx)) R.string.rule_allowed else R.string.rule_blocked
                )
            }
            return ""
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
