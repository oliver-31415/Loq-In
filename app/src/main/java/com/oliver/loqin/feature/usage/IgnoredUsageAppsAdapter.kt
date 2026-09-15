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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.oliver.loqin.R
import com.oliver.loqin.feature.picker.AppIconCache
import com.oliver.loqin.theme.AccentColor
import com.google.android.material.card.MaterialCardView
import java.util.Locale

enum class HiddenAppFilter {
    ALL,
    SUGGESTED,
    HIDDEN,
}

data class IgnoredUsageAppItem(
    val packageName: String,
    val label: String,
    val suggested: Boolean,
)

/**
 * Grid adapter for Hidden apps. Renders the same square tile component as the
 * App rules screen so both pickers look and behave consistently.
 */
class IgnoredUsageAppsAdapter(
    initialSelection: Set<String>,
    private val onSelectionChanged: (Set<String>) -> Unit,
) : ListAdapter<IgnoredUsageAppItem, IgnoredUsageAppsAdapter.ViewHolder>(DIFF) {

    private var allItems: List<IgnoredUsageAppItem> = emptyList()
    private var query: String = ""
    private var filter: HiddenAppFilter = HiddenAppFilter.ALL
    private val selectedPackages = initialSelection.toMutableSet()

    init {
        setHasStableIds(true)
    }

    fun setItems(items: List<IgnoredUsageAppItem>) {
        allItems = items
        applyFilter()
    }

    fun setQuery(value: String?) {
        query = value.orEmpty().trim().lowercase(Locale.getDefault())
        applyFilter()
    }

    fun setFilter(value: HiddenAppFilter) {
        filter = value
        applyFilter()
    }

    fun visibleCount(): Int = currentList.size

    fun selectedPackages(): Set<String> = selectedPackages.toSet()

    fun replaceSelection(packages: Set<String>) {
        val previousSelection = selectedPackages.toSet()
        selectedPackages.clear()
        selectedPackages += packages
        currentList.forEachIndexed { index, item ->
            val wasSelected = item.packageName in previousSelection
            val isSelected = item.packageName in selectedPackages
            if (wasSelected != isSelected) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
        onSelectionChanged(selectedPackages())
    }

    /** Selects every currently visible (filtered) app. */
    fun selectAllVisible() {
        currentList.forEachIndexed { index, item ->
            if (selectedPackages.add(item.packageName)) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
        onSelectionChanged(selectedPackages())
    }

    /** Clears the hidden state for every currently visible (filtered) app. */
    fun clearAllVisible() {
        currentList.forEachIndexed { index, item ->
            if (selectedPackages.remove(item.packageName)) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
        onSelectionChanged(selectedPackages())
    }

    override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.grid_hidden_app_tile, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (PAYLOAD_SELECTION in payloads) {
            holder.bindSelection(getItem(position).packageName in selectedPackages)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun applyFilter() {
        val visible = allItems.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.label.lowercase(Locale.getDefault()).contains(query) ||
                item.packageName.lowercase(Locale.getDefault()).contains(query)
            val matchesFilter = when (filter) {
                HiddenAppFilter.ALL -> true
                HiddenAppFilter.SUGGESTED -> item.suggested
                HiddenAppFilter.HIDDEN -> item.packageName in selectedPackages
            }
            matchesQuery && matchesFilter
        }
        submitList(visible)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardRoot: MaterialCardView = view.findViewById(R.id.rowRoot)
        private val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        private val tvLabel: TextView = view.findViewById(R.id.tvLabel)
        private val tvSuggested: TextView = view.findViewById(R.id.tvSuggested)
        private val ivChecked: ImageView = view.findViewById(R.id.ivChecked)

        fun bind(item: IgnoredUsageAppItem) {
            val ctx = itemView.context

            val iconPackage = item.packageName
            ivAppIcon.tag = iconPackage
            val cachedIcon = AppIconCache.getCached(ctx, iconPackage)
            if (cachedIcon != null) {
                ivAppIcon.setImageDrawable(cachedIcon)
            } else {
                ivAppIcon.setImageDrawable(AppIconCache.placeholder(ctx))
                AppIconCache.load(ctx, iconPackage) { icon ->
                    if (ivAppIcon.tag == iconPackage) {
                        ivAppIcon.setImageDrawable(icon)
                    }
                }
            }

            tvLabel.text = item.label
            tvSuggested.isVisible = item.suggested
            tvSuggested.text = ctx.getString(R.string.ignored_usage_apps_suggested)
            if (item.suggested) {
                val accent = AccentColor.getAccentColorInt(ctx)
                tvSuggested.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(999).toFloat()
                    setColor(ColorUtils.setAlphaComponent(accent, 0x24))
                }
                tvSuggested.setTextColor(accent)
            } else {
                tvSuggested.background = null
            }

            bindSelection(item.packageName in selectedPackages)

            cardRoot.setOnClickListener { toggle(item) }
        }

        fun bindSelection(selected: Boolean) {
            updateTileState(selected)
        }

        private fun toggle(item: IgnoredUsageAppItem) {
            val isNowHidden = item.packageName !in selectedPackages
            if (isNowHidden) {
                selectedPackages += item.packageName
            } else {
                selectedPackages -= item.packageName
            }
            updateTileState(isNowHidden)
            onSelectionChanged(selectedPackages())
        }

        private fun updateTileState(selected: Boolean) {
            val ctx = itemView.context
            if (selected) {
                val accent = AccentColor.getAccentColorInt(ctx)
                cardRoot.strokeWidth = dp(2)
                cardRoot.strokeColor = accent
                cardRoot.setCardBackgroundColor(ColorUtils.setAlphaComponent(accent, 0x30))
            } else {
                cardRoot.strokeWidth = dp(1)
                cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.foqos_surface))
                cardRoot.strokeColor = ContextCompat.getColor(ctx, R.color.foqos_outline_variant)
            }
            ivChecked.visibility = if (selected) View.VISIBLE else View.GONE
            cardRoot.contentDescription = if (selected) {
                ctx.getString(R.string.hidden_apps_tile_selected_desc, tvLabel.text)
            } else {
                ctx.getString(R.string.hidden_apps_tile_unselected_desc, tvLabel.text)
            }
        }

        private fun dp(value: Int): Int {
            return (value * itemView.resources.displayMetrics.density).toInt()
        }
    }

    private companion object {
        const val PAYLOAD_SELECTION = "selection"

        val DIFF = object : DiffUtil.ItemCallback<IgnoredUsageAppItem>() {
            override fun areItemsTheSame(oldItem: IgnoredUsageAppItem, newItem: IgnoredUsageAppItem): Boolean {
                return oldItem.packageName == newItem.packageName
            }

            override fun areContentsTheSame(oldItem: IgnoredUsageAppItem, newItem: IgnoredUsageAppItem): Boolean {
                return oldItem.packageName == newItem.packageName &&
                    oldItem.label == newItem.label &&
                    oldItem.suggested == newItem.suggested
            }
        }
    }
}
