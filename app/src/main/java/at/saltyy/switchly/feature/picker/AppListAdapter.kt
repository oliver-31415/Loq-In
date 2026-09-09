/*
 * Switchly
 * Copyright (C) 2025-2026 Saltyy
 * Copyright (C) 2026 Switchly Contributors
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

package at.saltyy.switchly.feature.picker

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import at.saltyy.switchly.R
import at.saltyy.switchly.blocking.isBrowserPackage
import at.saltyy.switchly.data.prefs.AttemptLimitStore
import at.saltyy.switchly.data.prefs.InAppRuleStore
import at.saltyy.switchly.data.prefs.OpenCountStore
import at.saltyy.switchly.data.prefs.ProfileStore
import at.saltyy.switchly.data.prefs.SessionLimitStore
import at.saltyy.switchly.data.prefs.UsageLimitStore
import at.saltyy.switchly.data.prefs.UsageLimitResetStore
import at.saltyy.switchly.theme.AccentColor
import at.saltyy.switchly.ui.dialog.showAccented
import at.saltyy.switchly.ui.showWarnPill
import at.saltyy.switchly.util.AppBlockSafety
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
    private val onRowActionsClicked: ((app: AppEntry, hasWebsiteRules: Boolean, hasInAppRules: Boolean) -> Unit)? = null,
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

    private fun hasInAppRulesShortcut(item: AppEntry): Boolean = item.isAvailable && item.packageName in IN_APP_RULE_PACKAGES

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
            if (hasPinnedLimit(context, profile, item) || hasPinnedInAppRule(context, profile, item)) return@forEachIndexed

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

    private fun hasPinnedInAppRule(context: Context, profile: String?, item: AppEntry): Boolean {
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
        val q = query?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        val newList =
            if (q.isBlank()) allApps
            else allApps.filter { it.labelLower.contains(q) || it.pkgLower.contains(q) }

        submitList(newList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_app_picker, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val cardRoot: MaterialCardView = v.findViewById(R.id.rowRoot)
        private val cb: CheckBox = v.findViewById(R.id.cbSelect)
        private val ivAppIcon: ImageView = v.findViewById(R.id.ivAppIcon)
        private val tvLabel: TextView = v.findViewById(R.id.tvLabel)
        private val tvPkg: TextView = v.findViewById(R.id.tvPkg)
        private val tvStateChip: TextView = v.findViewById(R.id.tvUnavailableChip)
        private val tvHint: TextView = v.findViewById(R.id.tvUnavailableHint)

        private val btnLimit: ImageButton = v.findViewById(R.id.btnLimit)

        private fun dp(value: Float): Int =
            (value * itemView.resources.displayMetrics.density).toInt()

        private fun applyStateChipStyle() {
            val ctx = itemView.context
            val chipBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999f).toFloat()
                setColor(ContextCompat.getColor(ctx, R.color.unavailable_chip_bg))
            }
            tvStateChip.background = chipBg
            tvStateChip.setTextColor(ContextCompat.getColor(ctx, R.color.unavailable_chip_text))
        }

        private fun applyUnavailableRowStyle() {
            val ctx = itemView.context
            cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.unavailable_row_bg))
            cardRoot.strokeColor = ContextCompat.getColor(ctx, R.color.unavailable_row_stroke)
        }

        private fun applyNormalRowStyle() {
            val ctx = itemView.context
            cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.foqos_surface))
            cardRoot.strokeColor = ContextCompat.getColor(ctx, R.color.foqos_outline_variant)
        }

        private fun updateCardState(selected: Boolean, unavailable: Boolean) {
            val ctx = itemView.context
            cardRoot.strokeWidth = dp(1f)
            if (selected) {
                val accent = AccentColor.getAccentColorInt(ctx)
                cardRoot.strokeColor = ColorUtils.setAlphaComponent(accent, 0x88)
                cardRoot.setCardBackgroundColor(ColorUtils.setAlphaComponent(accent, 0x14))
            } else if (unavailable) {
                applyUnavailableRowStyle()
            } else {
                applyNormalRowStyle()
            }
        }

        fun bind(item: AppEntry) {
            val ctx = itemView.context
            val profile = currentProfileProvider.invoke()
            val readOnly = isReadOnlyProvider.invoke()
            val protectedApp = item.blockSafety.level == AppBlockSafety.Level.PROTECTED
            val isAllowMode = isAllowModeProvider.invoke()
            val pinnedByInAppRules = hasPinnedInAppRule(ctx, profile, item)
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
            val effectiveHasLimit = hasLimit
            val accent = AccentColor.getAccentColorInt(ctx)

            cb.buttonTintList = AccentColor.getActiveColor(ctx)
            applyStateChipStyle()

            if (item.isAvailable) {
                if (!item.blockSafety.hint.isNullOrBlank()) {
                    tvStateChip.visibility = View.VISIBLE
                    tvHint.visibility = View.VISIBLE
                    tvStateChip.text = ctx.getString(
                        if (protectedApp) R.string.app_picker_protected_chip else R.string.app_picker_caution_chip
                    )
                    tvHint.text = item.blockSafety.hint
                } else if (pinnedByInAppRules) {
                    tvStateChip.visibility = View.VISIBLE
                    tvHint.visibility = View.VISIBLE
                    tvStateChip.text = ctx.getString(R.string.app_picker_in_app_rules_chip)
                    tvHint.text = ctx.getString(R.string.app_picker_in_app_rules_pinned_hint)
                } else {
                    tvStateChip.text = ""
                    tvHint.text = ""
                    tvStateChip.visibility = View.GONE
                    tvHint.visibility = View.GONE
                }

                if (effectiveHasLimit) {
                    tvPkg.text = buildString {
                        if (hasDailyLimit) {
                            val resetMode = profile?.let { UsageLimitResetStore.getMode(ctx, it, item.packageName) }
                            append(ctx.getString(
                                if (resetMode == UsageLimitResetStore.MODE_SESSION) R.string.session_reset_limit_value_format else R.string.daily_limit_label,
                                limitMin
                            ))
                        }
                        if (hasSessionLimit) {
                            if (isNotEmpty()) append("  •  ")
                            append(ctx.getString(R.string.session_limit_label, sessionLimitMin))
                        }
                        if (hasAttemptLimit) {
                            if (isNotEmpty()) append("  •  ")
                            append(ctx.getString(R.string.attempt_limit_label, attemptLimit))
                        }
                    }
                    tvPkg.setTextColor(accent)
                } else {
                    tvPkg.text = item.packageName
                    tvPkg.setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            ctx,
                            android.R.attr.textColorSecondary,
                            tvPkg.currentTextColor
                        )
                    )
                }
            } else {
                tvStateChip.visibility = View.VISIBLE
                tvHint.visibility = View.VISIBLE
                tvStateChip.text = ctx.getString(R.string.unavailable_app_state)
                tvHint.text = ctx.getString(R.string.unavailable_app_remove_hint)
                tvPkg.text = item.packageName
            }

            cb.setOnCheckedChangeListener(null)

            val currentlySelected = managed.contains(item.packageName) || pinnedByInAppRules || unavailableConfigured
            val canToggleSelection = canChangeSelectionProvider(
                currentlySelected,
                !currentlySelected,
            )
            cb.isChecked = currentlySelected
            // Keep tappable while locked (dimmed): denied taps warn via popover in the listener.
            cb.isEnabled = if (item.isAvailable) {
                true
            } else {
                unavailableConfigured && (!readOnly || canToggleSelection)
            }
            cb.alpha = when {
                !cb.isEnabled -> 0.45f
                !item.isAvailable -> 0.85f
                readOnly && !canToggleSelection -> 0.45f
                else -> 1f
            }
            updateCardState(currentlySelected, !item.isAvailable)

            lateinit var listener: CompoundButton.OnCheckedChangeListener
            fun setCheckedSilently(value: Boolean) {
                cb.setOnCheckedChangeListener(null)
                cb.isChecked = value
                cb.setOnCheckedChangeListener(listener)
            }

            listener = CompoundButton.OnCheckedChangeListener { _, checked ->
                val before = managed.contains(item.packageName) || pinnedByInAppRules || hasUnavailableConfiguration(ctx, profile, item)
                if (!canChangeSelectionProvider(before, checked)) {
                    if (isReadOnlyProvider.invoke()) {
                        itemView.showWarnPill(R.string.toast_disable_switchly_to_edit_blocked_apps)
                    }
                    setCheckedSilently(before)
                    // Stay tappable so repeated taps keep warning instead of going dead.
                    if (!item.isAvailable) {
                        cb.isEnabled = before && canChangeSelectionProvider(before, !before)
                        cb.alpha = if (cb.isEnabled) 1f else 0.45f
                    } else {
                        cb.alpha = 0.45f
                    }
                    return@OnCheckedChangeListener
                }
                if (checked) {
                    val needsWarning = if (isAllowMode) {
                        false
                    } else {
                        item.blockSafety.level != AppBlockSafety.Level.NONE
                    }
                    if (needsWarning) {
                        setCheckedSilently(false)
                        val allowSelection = {
                            managed.add(item.packageName)
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
                        notifySelectionCountChanged()
                        updateCardState(true, !item.isAvailable)
                    }
                } else {
                    if (pinnedByInAppRules) {
                        setCheckedSilently(true)
                        itemView.showWarnPill(R.string.app_picker_in_app_rules_pinned_toast)
                    } else {
                        managed.remove(item.packageName)
                        notifySelectionCountChanged()
                        updateCardState(false, !item.isAvailable)

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
            }
            cb.setOnCheckedChangeListener(listener)

            bindRowActionButtons(item)
        }

        private fun bindRowActionButtons(item: AppEntry) {
            val ctx = itemView.context
            val hasWebsiteRules = hasWebsiteRulesShortcut(item)
            val hasInAppRules = hasInAppRulesShortcut(item)
            val hasSecondaryRules = hasWebsiteRules || hasInAppRules

            // Browsers and supported in-app apps use the main row action button as a compact rules menu.
            btnLimit.setImageResource(if (hasSecondaryRules) R.drawable.tune_24 else R.drawable.schedule_24)
            btnLimit.contentDescription = ctx.getString(
                if (hasSecondaryRules) R.string.app_picker_row_actions else R.string.set_daily_limit
            )
            btnLimit.setColorFilter(AccentColor.getAccentColorInt(ctx))

            if (item.isAvailable) {
                btnLimit.visibility = View.VISIBLE
                val readOnly = isReadOnlyProvider.invoke()
                btnLimit.isEnabled = true
                btnLimit.alpha = if (readOnly) 0.45f else 1f
                btnLimit.setOnClickListener {
                    if (isReadOnlyProvider.invoke()) {
                        itemView.showWarnPill(R.string.toast_disable_switchly_to_edit_app_limits)
                        return@setOnClickListener
                    }
                    if (hasSecondaryRules && onRowActionsClicked != null) {
                        onRowActionsClicked.invoke(item, hasWebsiteRules, hasInAppRules)
                    } else {
                        onSetLimitClicked(item)
                    }
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
        private val IN_APP_RULE_PACKAGES = setOf(
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "app.morphe.android.youtube",
            "com.instagram.android",
            "com.twitter.android",
            "com.snapchat.android"
        )

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
