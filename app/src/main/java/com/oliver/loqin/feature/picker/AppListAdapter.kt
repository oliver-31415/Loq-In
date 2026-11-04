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
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import com.oliver.loqin.ui.RuleTileStyle
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.util.AppBlockSafety
import com.google.android.material.card.MaterialCardView
import java.util.Locale
import com.oliver.loqin.util.ProtectionChangeGate

class AppListAdapter(
    allApps: List<AppEntry>,
    preselectedManaged: Set<String>,
    private val currentProfileProvider: () -> String?,
    private val isAllowModeProvider: () -> Boolean = { false },
    private val onSetLimitClicked: (app: AppEntry) -> Unit,
    /** Press and hold (or the TalkBack action): the app's sheet with its limit and rules. */
    private val onAppMenuRequested: ((app: AppEntry) -> Unit)? = null,
    private val onWebsiteRulesClicked: ((app: AppEntry) -> Unit)? = null,
    private val onInAppRulesClicked: ((app: AppEntry) -> Unit)? = null,
    private val onProtectedSelectionRequested: ((app: AppEntry, onAllowed: () -> Unit) -> Unit)? = null,
    private val onSelectionChanged: ((count: Int) -> Unit)? = null,
    private val isReadOnlyProvider: () -> Boolean = { false },
    private val canChangeSelectionProvider: (currentlySelected: Boolean, requestedSelected: Boolean) -> Boolean = { _, _ -> true },
    private val pendingPackagesProvider: () -> Set<String> = { emptySet() },
    private val onUncheckWithLimits: ((packageName: String, label: String, proceed: () -> Unit) -> Unit)? = null,
    private val onCancelPendingSelection: ((packageName: String) -> Unit)? = null,
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
                ProtectionChangeGate.requestClearAppData(
                    context = context,
                    profile = profile,
                    packages = listOf(pkg),
                    includeInAppRules = true,
                )
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
                ProtectionChangeGate.requestClearAppData(
                    context = context,
                    profile = profile,
                    packages = listOf(item.packageName),
                    includeInAppRules = false,
                )
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

    /** Filter chips beyond categories: the picked apps, or apps with a limit. */
    enum class StatusFilter { SELECTED, LIMITED }

    fun limitedCount(context: Context): Int {
        val profile = currentProfileProvider.invoke()
        return allApps.count { hasPinnedLimit(context, profile, it) }
    }

    fun filter(query: String?, category: AppCategory?, status: StatusFilter? = null, context: Context? = null) {
        val q = query?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        val profile = currentProfileProvider.invoke()
        val newList = allApps.filter { entry ->
            (category == null || entry.appCategory == category) &&
                (q.isBlank() || entry.labelLower.contains(q) || entry.pkgLower.contains(q)) &&
                when (status) {
                    null -> true
                    StatusFilter.SELECTED -> managed.contains(entry.packageName)
                    StatusFilter.LIMITED -> context != null && hasPinnedLimit(context, profile, entry)
                }
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

        private val ivWebsiteRules: ImageView = v.findViewById(R.id.ivWebsiteRules)
        private val ivInAppRules: ImageView = v.findViewById(R.id.ivInAppRules)
        private var menuActionId: Int = View.NO_ID

        private var current: AppEntry? = null
        private var currentSelected = false
        private var currentHasLimit = false
        private var currentPending = false

        private fun dp(value: Float): Int =
            (value * itemView.resources.displayMetrics.density).toInt()

        private fun updateTileState(selected: Boolean) {
            val ctx = itemView.context
            // Pending (a weakening change is queued) keeps the accent hue in a lighter shade.
            RuleTileStyle.apply(cardRoot, selected, currentPending)
            val showBadge = selected || currentPending
            ivChecked.visibility = if (showBadge) View.VISIBLE else View.GONE
            if (showBadge) RuleTileStyle.bindBadge(ivChecked, currentPending)
            tvLabel.setTypeface(
                android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL),
                if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
            )
            cardRoot.isSelected = selected
            androidx.core.view.ViewCompat.setStateDescription(
                cardRoot,
                ctx.getString(
                    when {
                        currentPending -> R.string.app_picker_pending_badge
                        !selected -> R.string.state_off
                        isAllowModeProvider.invoke() -> R.string.app_picker_state_allowed
                        else -> R.string.app_picker_state_blocked
                    }
                ),
            )
        }

        fun bind(item: AppEntry) {
            val ctx = itemView.context
            currentPending = pendingPackagesProvider.invoke().contains(item.packageName)
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
            currentHasLimit = hasLimit

            if (hasLimit) {
                tvSub.visibility = View.VISIBLE
                val strong = RuleTileStyle.strongAccent(ctx)
                tvSub.setTextColor(strong)
                tvSub.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.timer_24)?.mutate()?.apply {
                        val size = dp(11f)
                        setBounds(0, 0, size, size)
                        setTint(strong)
                    },
                    null, null, null,
                )
                tvSub.setCompoundDrawablesRelative(tvSub.compoundDrawablesRelative[0]?.apply {
                    setBounds(0, 0, dp(11f), dp(11f))
                }, null, null, null)
                // Compact tile labels: long "Daily limit: …" style labels overflow the square tile.
                tvSub.text = buildString {
                    if (hasDailyLimit) {
                        val resetMode = profile?.let { UsageLimitResetStore.getMode(ctx, it, item.packageName) }
                        append(ctx.getString(
                            if (resetMode == UsageLimitResetStore.MODE_SESSION) R.string.session_reset_limit_value_format else R.string.limit_tile_daily_fmt,
                            limitMin
                        ))
                    }
                    if (hasSessionLimit) {
                        if (isNotEmpty()) append(" · ")
                        append(ctx.getString(R.string.limit_tile_visit_fmt, sessionLimitMin))
                    }
                    if (hasAttemptLimit) {
                        if (isNotEmpty()) append(" · ")
                        append(ctx.resources.getQuantityString(R.plurals.daily_attempt_limit_value_format, attemptLimit, attemptLimit))
                    }
                }
            } else {
                tvSub.visibility = View.GONE
            }

            // Whole-app blocking is independent of in-app rules: an app with active
            // in-app rules is NOT shown ticked unless it is explicitly blocked.
            currentSelected = managed.contains(item.packageName) || unavailableConfigured
            // A queued selection change shows its target state (faded + hourglass) so the list
            // does not look like the toggle was lost after re-entering the screen.
            val intendedSelected = if (currentPending) !currentSelected else currentSelected
            val canToggleSelection = canChangeSelectionProvider(
                intendedSelected,
                !intendedSelected,
            )
            // Tile stays tappable while locked (dimmed): denied taps warn via pill.
            val dimmed = !item.isAvailable || (readOnly && !canToggleSelection)
            ivAppIcon.alpha = if (dimmed) 0.45f else 1f
            tvLabel.alpha = if (dimmed) 0.55f else 1f
            updateTileState(intendedSelected)
            // One spoken line: name, then limit and rule markers; the state is the state description.
            cardRoot.contentDescription = listOfNotNull(
                item.label,
                tvSub.text.takeIf { hasLimit }?.let { ctx.getString(R.string.app_picker_tile_limit_cd_fmt, it) },
                ctx.getString(R.string.app_picker_website_rules_open).takeIf { item.isAvailable && hasWebsiteRulesShortcut(item) },
                ctx.getString(R.string.app_picker_in_app_rules_open).takeIf { item.isAvailable && inAppRulesActive },
            ).joinToString(", ")

            fun onTileToggle() {
                val checked = !intendedSelected
                // Tapping a tile that has a queued change back to its stored state undoes it.
                if (currentPending && checked == currentSelected) {
                    onCancelPendingSelection?.invoke(item.packageName)
                    return
                }
                val before = intendedSelected
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
                    fun applyUncheck() {
                        managed.remove(item.packageName)
                        if (inAppRulesActive) {
                            itemView.showWarnPill(R.string.app_picker_in_app_rules_stays_on_toast)
                        }
                        notifySelectionCountChanged()
                        currentSelected = false
                        updateTileState(false)

                        if (!item.isAvailable) {
                            if (!profile.isNullOrBlank()) {
                            ProtectionChangeGate.requestClearAppData(
                                context = ctx,
                                profile = profile,
                                packages = listOf(item.packageName),
                                includeInAppRules = true,
                            )
                        }
                            allApps.removeAll { it.packageName == item.packageName }
                            submitList(currentList.filterNot { it.packageName == item.packageName })
                        }
                    }
                    // An app with limits stays limited after unselecting, so ask what to do with them.
                    val limitHandler = onUncheckWithLimits
                    if (currentHasLimit && limitHandler != null) {
                        limitHandler.invoke(item.packageName, item.label, ::applyUncheck)
                    } else {
                        applyUncheck()
                    }
                }
            }

            cardRoot.setOnClickListener { onTileToggle() }
            cardRoot.setOnLongClickListener {
                // The sheet opens while protection is active; each editor's gate decides on save.
                onAppMenuRequested?.invoke(item)
                true
            }
            if (menuActionId != View.NO_ID) {
                androidx.core.view.ViewCompat.removeAccessibilityAction(cardRoot, menuActionId)
                menuActionId = View.NO_ID
            }
            if (item.isAvailable && onAppMenuRequested != null) {
                menuActionId = androidx.core.view.ViewCompat.addAccessibilityAction(
                    cardRoot, ctx.getString(R.string.app_picker_sheet_action),
                ) { _, _ -> onAppMenuRequested.invoke(item); true }
            }

            bindRuleMarkers(item, inAppRulesActive)
        }

        /** Small outlined markers for apps that also have website or in-app rules. */
        private fun bindRuleMarkers(item: AppEntry, inAppRulesActive: Boolean) {
            val ctx = itemView.context
            val strong = RuleTileStyle.strongAccent(ctx)
            val outline = androidx.core.graphics.ColorUtils.setAlphaComponent(strong, 0x80)
            fun style(marker: ImageView, show: Boolean) {
                marker.visibility = if (show) View.VISIBLE else View.GONE
                if (!show) return
                marker.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.foqos_surface))
                    setStroke(dp(1.5f), outline)
                }
                androidx.core.widget.ImageViewCompat.setImageTintList(marker, ColorStateList.valueOf(strong))
            }
            style(ivWebsiteRules, item.isAvailable && hasWebsiteRulesShortcut(item))
            style(ivInAppRules, item.isAvailable && inAppRulesActive)
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
