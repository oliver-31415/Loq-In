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

package com.oliver.loqin.feature.picker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.oliver.loqin.R
import com.oliver.loqin.blocking.isBrowserPackage
import com.oliver.loqin.data.prefs.AttemptLimitStore
import com.oliver.loqin.data.prefs.InAppRuleStore
import com.oliver.loqin.data.prefs.OpenCountStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.SessionLimitStore
import com.oliver.loqin.data.prefs.UsageLimitStore
import com.oliver.loqin.data.prefs.UsageLimitResetStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.util.AppBlockSafety
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class AppListAdapter(
    allApps: List<AppEntry>,
    preselectedManaged: Set<String>,
    private val currentProfileProvider: () -> String?,
    private val isAllowModeProvider: () -> Boolean = { false },
    private val onSetLimitClicked: (app: AppEntry) -> Unit,
    private val onSetSessionLimitClicked: ((app: AppEntry) -> Unit)? = null,
    private val onWebsiteRulesClicked: ((app: AppEntry) -> Unit)? = null,
    private val onInAppRulesClicked: ((app: AppEntry) -> Unit)? = null,
    private val onProtectedSelectionRequested: ((app: AppEntry, onAllowed: () -> Unit) -> Unit)? = null,
    private val onSelectionChanged: ((count: Int) -> Unit)? = null,
    private val isReadOnlyProvider: () -> Boolean = { false },
    private val canChangeSelectionProvider: (currentlySelected: Boolean, requestedSelected: Boolean) -> Boolean = { _, _ -> true }
) : ListAdapter<AppEntry, AppListAdapter.VH>(DIFF) {

    private val allApps = allApps.toMutableList()
    private val managed = preselectedManaged.toMutableSet()

    fun getManagedPackages(): Set<String> = managed.toSet()

    fun managedCount(): Int = managed.size

    private fun notifySelectionCountChanged() {
        onSelectionChanged?.invoke(managed.size)
    }

    private fun hasWebsiteRulesShortcut(item: AppEntry): Boolean = item.isAvailable && isBrowserPackage(item.packageName)

    fun replaceManagedPackages(pkgs: Set<String>) {
        val oldManaged = managed.toSet()
        managed.clear()
        managed.addAll(pkgs)

        val changedPackages = oldManaged + managed
        changedPackages.forEach { pkg ->
            currentList.indexOfFirst { it.packageName == pkg }
                .takeIf { it >= 0 }
                ?.let { notifyItemChanged(it) }
        }
        notifySelectionCountChanged()
    }

    private fun hasUnavailableConfiguration(context: Context, profile: String?, item: AppEntry): Boolean {
        if (item.isAvailable) return false
        if (item.packageName in managed) return true
        if (profile.isNullOrBlank()) return false
        if (item.packageName in ProfileStore.getSelectedForProfileMode(context, profile)) return true
        return UsageLimitStore.getLimitMinutes(context, profile, item.packageName) > 0 ||
            SessionLimitStore.getLimitMinutes(context, profile, item.packageName) > 0 ||
            AttemptLimitStore.getLimitAttempts(context, profile, item.packageName) > 0 ||
            InAppRuleStore.hasSelectedRulesForPackage(context, profile, item.packageName)
    }

    fun unavailableManagedCount(context: Context): Int {
        val profile = currentProfileProvider.invoke()
        return allApps.count { hasUnavailableConfiguration(context, profile, it) }
    }

    fun selectAllVisible(): Int {
        var skipped = 0
        val isAllowMode = isAllowModeProvider.invoke()
        currentList.forEachIndexed { index, item ->
            if (!item.isAvailable) return@forEachIndexed
            val shouldSkip = if (isAllowMode) {
                false
            } else {
                item.blockSafety.level != AppBlockSafety.Level.NONE
            }
            if (shouldSkip) {
                skipped++
                return@forEachIndexed
            }
            if (managed.add(item.packageName)) {
                notifyItemChanged(index)
            }
        }
        notifySelectionCountChanged()
        return skipped
    }

    fun clearUnavailable(context: Context): Int {
        val profile = currentProfileProvider.invoke()
        val unavailablePkgs = allApps
            .asSequence()
            .filter { hasUnavailableConfiguration(context, profile, it) }
            .map { it.packageName }
            .toList()

        if (unavailablePkgs.isEmpty()) {
            return 0
        }

        unavailablePkgs.forEach { pkg ->
            managed.remove(pkg)
            if (!profile.isNullOrBlank()) {
                UsageLimitStore.setLimitMinutes(context, profile, pkg, 0)
                SessionLimitStore.setLimitMinutes(context, profile, pkg, 0)
                AttemptLimitStore.setLimitAttempts(context, profile, pkg, 0)
                OpenCountStore.setToday(context, profile, pkg, 0)
                InAppRuleStore.clearRulesForPackage(context, profile, pkg)
            }
        }

        val removed = unavailablePkgs.toSet()
        allApps.removeAll { it.packageName in removed }
        submitList(currentList.filterNot { it.packageName in removed })
        notifySelectionCountChanged()
        return unavailablePkgs.size
    }

    fun clearAllVisible(context: Context) {
        val profile = currentProfileProvider.invoke()
        currentList.forEachIndexed { index, item ->
            // In-app rules stay untouched: clearing only removes whole-app blocking,
            // which is now independent of in-app rules.
            if (hasPinnedLimit(context, profile, item)) return@forEachIndexed

            val wasManaged = managed.remove(item.packageName)

            if (!item.isAvailable && !profile.isNullOrBlank()) {
                UsageLimitStore.setLimitMinutes(context, profile, item.packageName, 0)
                SessionLimitStore.setLimitMinutes(context, profile, item.packageName, 0)
                AttemptLimitStore.setLimitAttempts(context, profile, item.packageName, 0)
                OpenCountStore.setToday(context, profile, item.packageName, 0)
            }

            if (wasManaged) {
                notifyItemChanged(index)
            }
        }
        notifySelectionCountChanged()
    }

    private fun hasPinnedLimit(context: Context, profile: String?, item: AppEntry): Boolean {
        if (profile.isNullOrBlank() || !item.isAvailable) {
            return false
        }
        return UsageLimitStore.getLimitMinutes(context, profile, item.packageName) > 0 ||
            SessionLimitStore.getLimitMinutes(context, profile, item.packageName) > 0 ||
            AttemptLimitStore.getLimitAttempts(context, profile, item.packageName) > 0
    }

    private fun hasActiveInAppRules(context: Context, profile: String?, item: AppEntry): Boolean {
        return !profile.isNullOrBlank() &&
            item.isAvailable &&
            InAppRuleStore.hasEnabledRulesForPackage(context, profile, item.packageName)
    }

    init {
        submitList(allApps)
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).packageName.hashCode().toLong()
    }

    fun notifyPkgChanged(pkg: String) {
        val idx = currentList.indexOfFirst { it.packageName == pkg }
        if (idx >= 0) notifyItemChanged(idx)
    }

    fun filter(query: String?) {
        filter(query, null)
    }

    fun filter(query: String?, category: AppCategory?) {
        val q = query?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        val newList = allApps.filter { entry ->
            (category == null || entry.appCategory == category) &&
                (q.isBlank() || entry.labelLower.contains(q) || entry.pkgLower.contains(q))
        }

        submitList(newList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.grid_app_tile, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val cardRoot: MaterialCardView = v.findViewById(R.id.rowRoot)
        private val ivAppIcon: ImageView = v.findViewById(R.id.ivAppIcon)
        private val tvLabel: TextView = v.findViewById(R.id.tvLabel)
        private val tvSub: TextView = v.findViewById(R.id.tvSub)
        private val ivChecked: ImageView = v.findViewById(R.id.ivChecked)

        private val btnLimit: ImageButton = v.findViewById(R.id.btnLimit)
        private val viewLimitDot: View = v.findViewById(R.id.viewLimitDot)
        private val btnWebsiteRules: ImageButton = v.findViewById(R.id.btnWebsiteRules)
        private val btnInAppRules: ImageButton = v.findViewById(R.id.btnInAppRules)

        private var current: AppEntry? = null
        private var currentSelected = false

        private fun dp(value: Float): Int =
            (value * itemView.resources.displayMetrics.density).toInt()

        private fun updateTileState(selected: Boolean) {
            val ctx = itemView.context
            if (selected) {
                val accent = AccentColor.getAccentColorInt(ctx)
                cardRoot.strokeWidth = dp(2f)
                cardRoot.strokeColor = accent
                cardRoot.setCardBackgroundColor(ColorUtils.setAlphaComponent(accent, 0x30))
            } else {
                cardRoot.strokeWidth = dp(1f)
                cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.foqos_surface))
                cardRoot.strokeColor = ContextCompat.getColor(ctx, R.color.foqos_outline_variant)
            }
            ivChecked.visibility = if (selected) View.VISIBLE else View.GONE
        }

        fun bind(item: AppEntry) {
            val ctx = itemView.context
            val profile = currentProfileProvider.invoke()
            val readOnly = isReadOnlyProvider.invoke()
            val protectedApp = item.blockSafety.level == AppBlockSafety.Level.PROTECTED
            val isAllowMode = isAllowModeProvider.invoke()
            val inAppRulesActive = hasActiveInAppRules(ctx, profile, item)
            val unavailableConfigured = hasUnavailableConfiguration(ctx, profile, item)

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

            val limitMin = if (!profile.isNullOrBlank()) {
                UsageLimitStore.getLimitMinutes(ctx, profile, item.packageName)
            } else 0

            val sessionLimitMin = if (!profile.isNullOrBlank()) {
                SessionLimitStore.getLimitMinutes(ctx, profile, item.packageName)
            } else 0

            val attemptLimit = if (!profile.isNullOrBlank()) {
                AttemptLimitStore.getLimitAttempts(ctx, profile, item.packageName)
            } else 0

            val hasDailyLimit = limitMin > 0
            val hasSessionLimit = sessionLimitMin > 0
            val hasAttemptLimit = attemptLimit > 0
            val hasLimit = hasDailyLimit || hasSessionLimit || hasAttemptLimit

            viewLimitDot.visibility = if (hasLimit) View.VISIBLE else View.GONE
            if (hasLimit) {
                tvSub.visibility = View.VISIBLE
                tvSub.text = buildString {
                    if (hasDailyLimit) {
                        val resetMode = profile?.let { UsageLimitResetStore.getMode(ctx, it, item.packageName) }
                        append(ctx.getString(
                            if (resetMode == UsageLimitResetStore.MODE_SESSION) R.string.session_reset_limit_value_format else R.string.daily_limit_label,
                            limitMin
                        ))
                    }
                    if (hasSessionLimit) {
                        if (isNotEmpty()) append(" · ")
                        append(ctx.getString(R.string.session_limit_label, sessionLimitMin))
                    }
                    if (hasAttemptLimit) {
                        if (isNotEmpty()) append(" · ")
                        append(ctx.getString(R.string.attempt_limit_label, attemptLimit))
                    }
                }
            } else {
                tvSub.visibility = View.GONE
            }

            // Whole-app blocking is independent of in-app rules: an app with active
            // in-app rules is NOT shown ticked unless it is explicitly blocked.
            currentSelected = managed.contains(item.packageName) || unavailableConfigured
            val canToggleSelection = canChangeSelectionProvider(
                currentSelected,
                !currentSelected,
            )
            // Tile stays tappable while locked (dimmed): denied taps warn via pill.
            val dimmed = !item.isAvailable || (readOnly && !canToggleSelection)
            ivAppIcon.alpha = if (dimmed) 0.45f else 1f
            tvLabel.alpha = if (dimmed) 0.55f else 1f
            updateTileState(currentSelected)
            cardRoot.contentDescription = if (currentSelected) {
                ctx.getString(R.string.app_picker_tile_selected_desc, item.label)
            } else {
                ctx.getString(R.string.app_picker_tile_unselected_desc, item.label)
            }

            fun onTileToggle() {
                val checked = !currentSelected
                val before = managed.contains(item.packageName) || hasUnavailableConfiguration(ctx, profile, item)
                if (!canChangeSelectionProvider(before, checked)) {
                    if (isReadOnlyProvider.invoke()) {
                        itemView.showWarnPill(R.string.toast_disable_loqin_to_edit_blocked_apps)
                    }
                    updateTileState(before)
                    return
                }
                if (checked) {
                    val needsWarning = if (isAllowMode) {
                        false
                    } else {
                        item.blockSafety.level != AppBlockSafety.Level.NONE
                    }
                    if (needsWarning) {
                        updateTileState(false)
                        val allowSelection = {
                            managed.add(item.packageName)
                            if (inAppRulesActive) {
                                itemView.showWarnPill(R.string.app_picker_in_app_rules_stays_on_toast)
                            }
                            notifySelectionCountChanged()
                            notifyPkgChanged(item.packageName)
                        }
                        val protectedSelectionHandler = onProtectedSelectionRequested
                        if (protectedSelectionHandler != null) {
                            protectedSelectionHandler.invoke(item, allowSelection)
                        } else {
                            AlertDialog.Builder(ctx)
                                .setTitle(item.blockSafety.warningTitle ?: ctx.getString(R.string.app_picker_protected_warning_title))
                                .setMessage(item.blockSafety.warningMessage ?: item.blockSafety.hint ?: ctx.getString(R.string.app_picker_protected_warning_message))
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(
                                    if (protectedApp) {
                                        R.string.app_picker_block_protected_confirm
                                    } else {
                                        R.string.continue_label
                                    }
                                ) { _, _ -> allowSelection() }
                                .showAccented()
                        }
                    } else {
                        managed.add(item.packageName)
                        if (inAppRulesActive) {
                            itemView.showWarnPill(R.string.app_picker_in_app_rules_stays_on_toast)
                        }
                        notifySelectionCountChanged()
                        currentSelected = true
                        updateTileState(true)
                    }
                } else {
                    managed.remove(item.packageName)
                    if (inAppRulesActive) {
                        itemView.showWarnPill(R.string.app_picker_in_app_rules_stays_on_toast)
                    }
                    notifySelectionCountChanged()
                    currentSelected = false
                    updateTileState(false)

                    if (!item.isAvailable) {
                        if (!profile.isNullOrBlank()) {
                            UsageLimitStore.setLimitMinutes(ctx, profile, item.packageName, 0)
                            SessionLimitStore.setLimitMinutes(ctx, profile, item.packageName, 0)
                            AttemptLimitStore.setLimitAttempts(ctx, profile, item.packageName, 0)
                            OpenCountStore.setToday(ctx, profile, item.packageName, 0)
                            InAppRuleStore.clearRulesForPackage(ctx, profile, item.packageName)
                        }
                        allApps.removeAll { it.packageName == item.packageName }
                        submitList(currentList.filterNot { it.packageName == item.packageName })
                    }
                }
            }

            cardRoot.setOnClickListener { onTileToggle() }
            cardRoot.setOnLongClickListener {
                if (isReadOnlyProvider.invoke()) {
                    itemView.showWarnPill(R.string.toast_disable_loqin_to_edit_app_limits)
                    return@setOnLongClickListener true
                }
                onSetSessionLimitClicked?.invoke(item)
                true
            }

            bindRowActionButtons(item)
        }

        private fun bindRowActionButtons(item: AppEntry) {
            val ctx = itemView.context
            val profile = currentProfileProvider.invoke()
            val isBrowser = hasWebsiteRulesShortcut(item)

            // Browsers get a dedicated website-rules button (left); supported apps with
            // active in-app rules get a dedicated in-app button. The schedule button
            // on the right is the same plain limit button as on every other app.
            if (item.isAvailable && isBrowser) {
                btnWebsiteRules.visibility = View.VISIBLE
                val readOnly = isReadOnlyProvider.invoke()
                btnWebsiteRules.isEnabled = true
                btnWebsiteRules.alpha = if (readOnly) 0.45f else 1f
                btnWebsiteRules.setColorFilter(AccentColor.getAccentColorInt(ctx))
                btnWebsiteRules.setOnClickListener {
                    if (isReadOnlyProvider.invoke()) {
                        itemView.showWarnPill(R.string.toast_disable_loqin_to_edit_app_limits)
                        return@setOnClickListener
                    }
                    onWebsiteRulesClicked?.invoke(item)
                }
            } else {
                btnWebsiteRules.visibility = View.GONE
                btnWebsiteRules.setOnClickListener(null)
            }

            // Dedicated shortcut to the in-app rules page while rules are active for this profile.
            if (item.isAvailable && hasActiveInAppRules(ctx, profile, item)) {
                btnInAppRules.visibility = View.VISIBLE
                val readOnly = isReadOnlyProvider.invoke()
                btnInAppRules.isEnabled = true
                btnInAppRules.alpha = if (readOnly) 0.45f else 1f
                btnInAppRules.setColorFilter(AccentColor.getAccentColorInt(ctx))
                btnInAppRules.setOnClickListener {
                    if (isReadOnlyProvider.invoke()) {
                        itemView.showWarnPill(R.string.toast_disable_loqin_to_edit_app_limits)
                        return@setOnClickListener
                    }
                    onInAppRulesClicked?.invoke(item)
                }
            } else {
                btnInAppRules.visibility = View.GONE
                btnInAppRules.setOnClickListener(null)
            }

            // Plain limit/schedule button, same as every other app.
            btnLimit.setImageResource(R.drawable.schedule_24)
            btnLimit.contentDescription = ctx.getString(R.string.set_daily_limit)
            btnLimit.setColorFilter(AccentColor.getAccentColorInt(ctx))

            if (item.isAvailable) {
                btnLimit.visibility = View.VISIBLE
                val readOnly = isReadOnlyProvider.invoke()
                btnLimit.isEnabled = true
                btnLimit.alpha = if (readOnly) 0.45f else 1f
                btnLimit.setOnClickListener {
                    if (isReadOnlyProvider.invoke()) {
                        itemView.showWarnPill(R.string.toast_disable_loqin_to_edit_app_limits)
                        return@setOnClickListener
                    }
                    onSetLimitClicked(item)
                }
                btnLimit.setOnLongClickListener {
                    if (isReadOnlyProvider.invoke()) {
                        return@setOnLongClickListener true
                    }
                    onSetSessionLimitClicked?.invoke(item)
                    true
                }
            } else {
                btnLimit.visibility = View.GONE
                btnLimit.isEnabled = false
                btnLimit.alpha = 0.45f
                btnLimit.setOnClickListener {
                    itemView.showWarnPill(R.string.cannot_set_limit_unavailable)
                }
                btnLimit.setOnLongClickListener {
                    itemView.showWarnPill(R.string.cannot_set_limit_unavailable)
                    true
                }
            }
        }

    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppEntry>() {
            override fun areItemsTheSame(oldItem: AppEntry, newItem: AppEntry): Boolean {
                return oldItem.packageName == newItem.packageName
            }

            override fun areContentsTheSame(oldItem: AppEntry, newItem: AppEntry): Boolean {
                return oldItem == newItem
            }
        }
    }
}
