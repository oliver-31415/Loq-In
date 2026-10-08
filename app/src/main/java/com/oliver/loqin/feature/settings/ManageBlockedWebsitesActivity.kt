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

package com.oliver.loqin.feature.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.oliver.loqin.R
import com.oliver.loqin.blocking.BlockingRuntime
import com.oliver.loqin.data.prefs.DomainBlockStore
import com.oliver.loqin.data.prefs.DomainLimitStore
import com.oliver.loqin.data.prefs.DomainVisitLimitStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.data.prefs.WebsiteRuleModeStore
import com.oliver.loqin.data.prefs.WebsiteSuggestionsVisibilityStore
import com.oliver.loqin.feature.usage.QuickLimitDialogs
import com.oliver.loqin.feature.websites.WebsitePathRow
import com.oliver.loqin.feature.websites.WebsitePathsSheet
import com.oliver.loqin.feature.websites.WebsiteRuleTileAdapter
import com.oliver.loqin.feature.websites.WebsiteSuggestion
import com.oliver.loqin.feature.websites.WebsiteSuggestionCategory
import com.oliver.loqin.feature.websites.WebsiteSuggestions
import com.oliver.loqin.feature.websites.WebsiteTile
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.LoqInDropdownAdapter
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.ui.dialog.showDestructiveAccented
import com.oliver.loqin.ui.dialog.styleLoqInDestructivePositiveButton
import com.oliver.loqin.ui.dialog.styleLoqInDialogButtons
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.ui.updateSelectionSubtitle
import com.oliver.loqin.util.EditingLockGuard
import com.oliver.loqin.util.ProtectionChangeGate
import com.oliver.loqin.util.ProtectionChangePolicy
import com.oliver.loqin.util.ProtectionFeedback
import kotlinx.coroutines.launch

class ManageBlockedWebsitesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROFILE_NAME = "extra_profile_name"
    }

    private fun websiteEditingLocked(): Boolean {
        return EditingLockGuard.isLocked(this)
    }

    /**
     * Adding a block rule is stricter and stays available while protection is active; in allow mode
     * an addition would widen the allow list, so it requires protection to be off.
     */
    private fun canAddBlockedWebsite(): Boolean {
        val profile = currentProfile()
        if (profile.isNullOrBlank()) return false
        if (!EditingLockGuard.isLocked(this)) return true
        return ProfileStore.getCurrent(this) == profile && !isAllowMode()
    }

    private fun denyWebsiteEditWithPopover(): Boolean {
        if (EditingLockGuard.isLocked(this)) {
            findViewById<View>(android.R.id.content)
                .showWarnPill(R.string.edit_locked_manage_websites)
            return true
        }
        return false
    }

    private fun syncEditingLockUi() {
        val locked = EditingLockGuard.isLocked(this)
        val canAdd = canAddBlockedWebsite()
        findViewById<MaterialButton>(R.id.btnAddWebsite)?.apply {
            isEnabled = true
            isClickable = true
            alpha = if (canAdd && !isSelectionMode) 1f else 0.45f
            iconTint = ColorStateList.valueOf(AccentColor.getAccentColorInt(this@ManageBlockedWebsitesActivity))
        }
        val accent = AccentColor.getAccentColorInt(this)
        findViewById<View>(R.id.btnEmptyAddWebsite)?.apply {
            isEnabled = true
            isClickable = true
            alpha = if (canAdd) 1f else 0.45f
            (this as? MaterialButton)?.apply {
                strokeColor = ColorStateList.valueOf(accent)
                setTextColor(accent)
                iconTint = ColorStateList.valueOf(accent)
            }
        }
        findViewById<MaterialButtonToggleGroup>(R.id.toggleWebsiteRuleMode)?.apply {
            isEnabled = true
            alpha = if (locked) 0.62f else 1f
        }
        findViewById<View>(R.id.btnWebsiteModeBlock)?.apply {
            isEnabled = true
            alpha = if (locked) 0.62f else 1f
        }
        findViewById<View>(R.id.btnWebsiteModeAllow)?.apply {
            isEnabled = true
            alpha = if (locked) 0.62f else 1f
        }

        if (::adapter.isInitialized && adapter.itemCount > 0) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
        invalidateOptionsMenu()
    }

    private var emptyCard: View? = null
    private lateinit var rv: RecyclerView
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private lateinit var adapter: WebsiteRuleTileAdapter
    private lateinit var toolbar: MaterialToolbar
    private var searchQuery: String = ""
    private var allRules: List<DomainRule> = emptyList()

    private var isSelectionMode: Boolean = false
    /** True while selecting suggestions (add flow); false while selecting saved rules (delete). */
    private var selectionIsSuggestion: Boolean = false
    /** Selected hosts; deleting a host expands to every rule it owns. */
    private val selectedDomains = linkedSetOf<String>()
    private var updatingModeUi = false

    private var suggestionCategory: WebsiteSuggestionCategory = WebsiteSuggestionCategory.ALL

    private fun currentProfile(): String =
        intent.getStringExtra(EXTRA_PROFILE_NAME)?.trim()?.takeIf { it.isNotBlank() }
            ?: ProfileStore.getCurrent(this) ?: "default"

    private fun isAllowMode(): Boolean =
        WebsiteRuleModeStore.isAllowMode(this, currentProfile())

    private fun suggestionsVisible(): Boolean =
        WebsiteSuggestionsVisibilityStore.isVisible(this)

    private fun syncRuleModeUi() {
        val allow = isAllowMode()
        toolbar.title = getString(R.string.website_rules_title)
        toolbar.subtitle = websiteRulesSubtitle()
        emptyTitle.text = getString(
            if (allow) R.string.allowed_websites_empty_title else R.string.blocked_websites_empty_title
        )
        emptyBody.text = getString(
            if (allow) R.string.allowed_websites_empty_body else R.string.blocked_websites_empty_body
        )
        val targetId = if (allow) R.id.btnWebsiteModeAllow else R.id.btnWebsiteModeBlock
        val group = findViewById<MaterialButtonToggleGroup>(R.id.toggleWebsiteRuleMode)
        if (group != null && group.checkedButtonId != targetId) {
            updatingModeUi = true
            try {
                group.check(targetId)
            } finally {
                updatingModeUi = false
            }
        }
        applyWebsiteRuleModeButtonStyle()
    }

    private fun websiteRulesSubtitle(): String {
        return getString(R.string.blocked_websites_profile_subtitle, currentProfile())
    }

    private fun setupWebsiteRuleMode() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.toggleWebsiteRuleMode) ?: return
        updatingModeUi = true
        try {
            group.check(if (isAllowMode()) R.id.btnWebsiteModeAllow else R.id.btnWebsiteModeBlock)
        } finally {
            updatingModeUi = false
        }
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || updatingModeUi) return@addOnButtonCheckedListener
            if (websiteEditingLocked()) {
                syncRuleModeUi()
                denyWebsiteEditWithPopover()
                return@addOnButtonCheckedListener
            }
            val mode = if (checkedId == R.id.btnWebsiteModeAllow) {
                WebsiteRuleModeStore.MODE_ALLOW_SELECTED
            } else {
                WebsiteRuleModeStore.MODE_BLOCK_SELECTED
            }
            if (mode == WebsiteRuleModeStore.getMode(this, currentProfile())) return@addOnButtonCheckedListener
            fun applyMode() {
                WebsiteRuleModeStore.setMode(this, currentProfile(), mode)
                syncRuleModeUi()
                refreshList()
            }
            if (mode == WebsiteRuleModeStore.MODE_ALLOW_SELECTED) {
                syncRuleModeUi()
                val allowedCount = DomainBlockStore.getAllowedDomainsForProfile(this, currentProfile()).size
                AlertDialog.Builder(this)
                    .setTitle(R.string.website_allow_mode_preview_title)
                    .setMessage(
                        resources.getQuantityString(
                            R.plurals.website_allow_mode_preview_body,
                            allowedCount,
                            allowedCount,
                        )
                    )
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.website_allow_mode_preview_action) { _, _ -> applyMode() }
                    .showAccented()
            } else {
                applyMode()
            }
        }
        applyWebsiteRuleModeButtonStyle()
    }

    private fun applyWebsiteRuleModeButtonStyle() {
        val blockButton = findViewById<MaterialButton>(R.id.btnWebsiteModeBlock) ?: return
        val allowButton = findViewById<MaterialButton>(R.id.btnWebsiteModeAllow) ?: return
        SegmentedToggleUi.apply(
            this,
            listOf(blockButton, allowButton),
            if (isAllowMode()) allowButton.id else blockButton.id,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_blocked_websites)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(this) {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                finish()
            }
        }

        rv = findViewById(R.id.list)
        emptyTitle = findViewById(R.id.tvEmptyTitle)
        emptyBody = findViewById(R.id.tvEmptyBody)
        emptyCard = findViewById(R.id.emptyCard)
        val accent = AccentColor.getAccentColorInt(this)
        findViewById<ImageView>(R.id.ivEmptyWebsitesIcon)?.imageTintList = ColorStateList.valueOf(accent)

        adapter = WebsiteRuleTileAdapter(object : WebsiteRuleTileAdapter.Listener {
            override fun onHostToggle(host: String) = handleHostToggle(host)
            override fun onHostEdit(host: String) = handleHostEdit(host)
            override fun onHostLongPress(host: String, isSuggestion: Boolean) = handleHostLongPress(host, isSuggestion)
            override fun onHostLimit(host: String) = handleHostLimit(host)
            override fun onHostExpand(host: String) = showPathsSheet(host)
            override fun onHostSelection(host: String, isSuggestion: Boolean) = toggleSelection(host, isSuggestion)
            override fun onPathToggle(rule: String, enabled: Boolean) = handlePathToggle(rule, enabled)
            override fun onPathEdit(rule: String) = handlePathEdit(rule)
            override fun onSuggestionAdd(suggestion: WebsiteSuggestion) = handleSuggestionAdd(suggestion)
            override fun onSuggestionQuickAdd(rule: String) = quickAddRule(rule)
            override fun onAddCustom(query: String) = showAddDialog(query)
            override fun onSectionAction(key: String) = handleSectionAction(key)
            override fun onShowSuggestions() = handleSectionAction("suggestions_show")
            override fun isSelectionMode(): Boolean = this@ManageBlockedWebsitesActivity.isSelectionMode
            override fun isSelected(host: String): Boolean = selectedDomains.contains(host)
            override fun isReadOnly(): Boolean = websiteEditingLocked()
        })

        val layoutManager = GridLayoutManager(this, 3)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = when (adapter.getItemViewType(position)) {
                WebsiteRuleTileAdapter.VIEW_TYPE_SECTION_HEADER,
                WebsiteRuleTileAdapter.VIEW_TYPE_PATHS_PANEL,
                WebsiteRuleTileAdapter.VIEW_TYPE_SHOW_SUGGESTIONS -> 3
                else -> 1
            }
        }
        rv.layoutManager = layoutManager
        rv.adapter = adapter

        DomainBlockStore.migrateLegacyDomainsIntoCurrentProfileIfNeeded(this)
        setupWebsiteRuleMode()
        setupSuggestionChips()
        syncRuleModeUi()

        findViewById<MaterialButton>(R.id.btnAddWebsite).setOnClickListener {
            if (!canAddBlockedWebsite()) {
                denyWebsiteEditWithPopover()
                return@setOnClickListener
            }
            showAddDialog()
        }
        findViewById<View>(R.id.btnEmptyAddWebsite).setOnClickListener {
            if (!canAddBlockedWebsite()) {
                denyWebsiteEditWithPopover()
                return@setOnClickListener
            }
            showAddDialog()
        }
        findViewById<View>(R.id.btnEmptyShowSuggestions)?.setOnClickListener {
            handleSectionAction("suggestions_show")
        }

        val etSearch = findViewById<TextInputEditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                applyListFilter()
            }
        })
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            ) {
                etSearch.clearFocus()
                true
            } else {
                false
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SwitchModeStore.enabledFlow.collect {
                    runOnUiThread { syncEditingLockUi() }
                }
            }
        }

        updateMenuState()
    }

    override fun onResume() {
        super.onResume()
        syncRuleModeUi()
        refreshList()
        syncEditingLockUi()
    }

    private fun setupSuggestionChips() {
        val group = findViewById<ChipGroup>(R.id.chipCategories) ?: return
        // Category pills filter both "Your rules" and suggestions, so they stay visible
        // even when the suggestion section itself is hidden.
        (group.parent as? View)?.visibility = View.VISIBLE
        group.removeAllViews()
        val entries = listOf(
            null to getString(R.string.website_category_all),
            WebsiteSuggestionCategory.SOCIAL to getString(R.string.website_category_social),
            WebsiteSuggestionCategory.VIDEO to getString(R.string.website_category_video),
            WebsiteSuggestionCategory.NEWS to getString(R.string.website_category_news),
            WebsiteSuggestionCategory.SHOPPING to getString(R.string.website_category_shopping),
            WebsiteSuggestionCategory.GAMING to getString(R.string.website_category_gaming),
        )

        fun refreshChecks() {
            val accent = AccentColor.getAccentColorInt(this)
            val density = resources.displayMetrics.density
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? Chip ?: continue
                val selected = (chip.tag as? WebsiteSuggestionCategory) == suggestionCategory ||
                    (chip.tag == null && suggestionCategory == WebsiteSuggestionCategory.ALL)
                chip.isChecked = selected
                chip.chipBackgroundColor = ColorStateList.valueOf(
                    if (selected) ColorUtils.setAlphaComponent(accent, 0x2E) else Color.TRANSPARENT
                )
                chip.chipStrokeColor = ColorStateList.valueOf(
                    if (selected) accent
                    else ContextCompat.getColor(this, R.color.foqos_outline_variant)
                )
                chip.chipStrokeWidth = 1f * density
            }
        }

        entries.forEach { (category, label) ->
            val chip = Chip(
                this,
                null,
                com.google.android.material.R.style.Widget_Material3_Chip_Suggestion
            ).apply {
                text = label
                isClickable = true
                isFocusable = true
                tag = category
                id = View.generateViewId()
                setOnClickListener {
                    suggestionCategory = (tag as? WebsiteSuggestionCategory)
                        ?: WebsiteSuggestionCategory.ALL
                    refreshChecks()
                    applyListFilter()
                }
            }
            group.addView(chip)
        }
        refreshChecks()
    }

    private fun refreshList() {
        val profile = currentProfile()
        val blocked = DomainBlockStore.getDomainsForProfileAndMode(this, profile)
        val limited = DomainLimitStore.getDomainsWithLimitForProfile(this, profile)
        val visitLimited = DomainVisitLimitStore.getDomainsWithLimitsForProfile(this, profile)

        val all = (blocked + limited + visitLimited)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSortedSet()

        val rules = all.map { d ->
            DomainRule(
                domain = d,
                isHardBlocked = blocked.contains(d),
                limitMin = DomainLimitStore.getLimitMinutesForProfile(this, profile, d),
                sessionLimitMin = DomainVisitLimitStore.getSessionLimitMinutesForProfile(this, profile, d),
                visitLimit = DomainVisitLimitStore.getVisitLimitCountForProfile(this, profile, d),
                enabled = DomainBlockStore.isDomainEnabledForProfile(this, profile, d)
            )
        }

        allRules = rules
        applyListFilter()

        val savedHosts = rules.mapNotNull { DomainBlockStore.hostPart(it.domain) }.toSet()
        selectedDomains.retainAll(savedHosts)
        if (isSelectionMode && rules.isEmpty()) {
            exitSelectionMode()
        } else {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
            updateMenuState()
        }
    }

    private fun applyListFilter() {
        if (!::adapter.isInitialized) return
        val q = searchQuery.trim().lowercase()
        val filteredRules = allRules.filter { rule ->
            val matchesQuery = q.isBlank() || rule.domain.lowercase().contains(q)
            val host = DomainBlockStore.hostPart(rule.domain) ?: rule.domain
            val matchesCategory = suggestionCategory == WebsiteSuggestionCategory.ALL ||
                WebsiteSuggestions.categoryOf(host) == suggestionCategory
            matchesQuery && matchesCategory
        }
        val groups = buildGroups(filteredRules)
        val savedHosts = allRules.mapNotNull { DomainBlockStore.hostPart(it.domain) }.toSet()
        val suggestions = if (suggestionsVisible()) {
            WebsiteSuggestions.search(q, suggestionCategory).filterNot { it.host in savedHosts }
        } else {
            emptyList()
        }
        adapter.submit(buildTiles(groups, suggestions, searchQuery.trim()))
        // Never overlay the grid: the empty card only shows when there is nothing below it
        // (no rules, no suggestions, no add-custom tile), so suggestions stay tappable.
        val showEmptyCard = allRules.isEmpty() && suggestions.isEmpty() && searchQuery.isBlank()
        emptyCard?.visibility = if (showEmptyCard) View.VISIBLE else View.GONE
        // With the suggestion section hidden the empty card offers to bring it back; the
        // grid button is reserved for when rules already exist (no overlap with the card).
        findViewById<View>(R.id.btnEmptyShowSuggestions)?.visibility =
            if (showEmptyCard && !suggestionsVisible()) View.VISIBLE else View.GONE
    }

    private data class WebsiteRuleGroup(
        val host: String,
        val hostRule: DomainRule?,
        val pathRules: List<DomainRule>,
    )

    private fun buildGroups(rules: List<DomainRule>): List<WebsiteRuleGroup> {
        val byHost = LinkedHashMap<String, MutableList<DomainRule>>()
        for (rule in rules) {
            val host = DomainBlockStore.hostPart(rule.domain) ?: continue
            byHost.getOrPut(host) { mutableListOf() }.add(rule)
        }
        return byHost.map { (host, rs) ->
            val hostRule = rs.firstOrNull { !DomainBlockStore.isPathRule(it.domain) }
            val paths = rs.filter { DomainBlockStore.isPathRule(it.domain) }.sortedBy { it.domain }
            WebsiteRuleGroup(host, hostRule, paths)
        }
    }

    private fun buildTiles(
        groups: List<WebsiteRuleGroup>,
        suggestions: List<WebsiteSuggestion>,
        query: String,
    ): List<WebsiteTile> {
        val tiles = mutableListOf<WebsiteTile>()

        if (groups.isNotEmpty()) {
            tiles += WebsiteTile.SectionHeader("rules", getString(R.string.website_section_your_rules))
            for (group in groups) {
                val totalRules = (if (group.hostRule != null) 1 else 0) + group.pathRules.size
                val hostRule = group.hostRule
                val limitMin = hostRule?.limitMin
                    ?: DomainLimitStore.getLimitMinutesForProfile(this, currentProfile(), group.host)
                val sessionLimitMin = hostRule?.sessionLimitMin
                    ?: DomainVisitLimitStore.getSessionLimitMinutesForProfile(this, currentProfile(), group.host)
                val visitLimit = hostRule?.visitLimit
                    ?: DomainVisitLimitStore.getVisitLimitCountForProfile(this, currentProfile(), group.host)
                // A group is "on" when anything in it is active. The host switch itself is
                // tracked separately so the label can say "Paths only" when the host rule is
                // off while path rules remain on.
                val hostEnabled = hostRule?.enabled ?: true
                val pathsActive = group.pathRules.any { it.enabled }
                val enabled = if (hostRule != null) (hostEnabled || pathsActive) else pathsActive
                val pending = hostRule?.let { isRulePending(it.domain) }
                    ?: group.pathRules.any { isRulePending(it.domain) }
                tiles += WebsiteTile.HostTile(
                    key = "host:${group.host}",
                    host = group.host,
                    hostRule = hostRule?.domain,
                    pathRuleCount = group.pathRules.size,
                    totalRuleCount = totalRules,
                    isHardBlocked = hostRule?.isHardBlocked ?: group.pathRules.any { it.isHardBlocked },
                    limitMin = limitMin,
                    sessionLimitMin = sessionLimitMin,
                    visitLimit = visitLimit,
                    enabled = enabled,
                    pathsActive = pathsActive,
                    hostEnabled = hostEnabled,
                    pending = pending,
                    expanded = false,
                )
            }
        }

        if (suggestions.isNotEmpty()) {
            tiles += WebsiteTile.SectionHeader(
                "suggestions",
                getString(R.string.website_section_suggestions),
                actionLabel = getString(R.string.website_hide_suggestions),
            )
            for (suggestion in suggestions) {
                tiles += WebsiteTile.HostTile(
                    key = "sug:${suggestion.host}",
                    host = suggestion.host,
                    hostRule = null,
                    pathRuleCount = suggestion.quickPaths.size,
                    totalRuleCount = 0,
                    isHardBlocked = true,
                    limitMin = 0,
                    enabled = false,
                    pending = false,
                    expanded = false,
                    suggestion = suggestion,
                )
            }
        }

        if (!suggestionsVisible() && query.isBlank() && groups.isNotEmpty()) {
            tiles += WebsiteTile.ShowSuggestions()
        }

        if (query.isNotBlank() && groups.isEmpty()) {
            tiles += WebsiteTile.AddCustom(query = query)
        }
        return tiles
    }

    private fun isRulePending(rule: String): Boolean {
        val profile = currentProfile()
        return ProtectionChangeGate.pendingWebsiteEnabled(this, profile).containsKey(rule) ||
            rule in ProtectionChangeGate.pendingWebsiteRemovals(this, profile)
    }

    /**
     * Host tile toggle:
     * - host rule present: toggles only the whole-site rule (path rules stay independent).
     * - path-only group: toggles every path rule together (any disabled -> enable all,
     *   otherwise disable all), so the group switch matches what the tile shows.
     */
    private fun handleHostToggle(host: String) {
        if (denyWebsiteEditWithPopoverIfNeeded()) return
        val group = buildGroups(allRules).find { it.host == host } ?: return
        val hostRule = group.hostRule
        if (hostRule != null) {
            setRuleEnabled(hostRule.domain, !hostRule.enabled)
            return
        }
        if (group.pathRules.isEmpty()) return
        val enableAll = group.pathRules.any { !it.enabled }
        setRulesEnabled(group.pathRules.map { it.domain }, enableAll)
    }

    /**
     * Long-press selects a tile. Saved rules and suggestions are separate selections and can
     * never be mixed: rule selection surfaces Delete, suggestion selection surfaces Add.
     */
    private fun handleHostLongPress(host: String, isSuggestion: Boolean) {
        if (isSelectionMode) {
            if (selectionIsSuggestion != isSuggestion) return
            toggleSelection(host, isSuggestion)
            return
        }
        enterSelectionMode(preselect = host, forSuggestions = isSuggestion)
    }

    private fun handleHostEdit(host: String) {
        if (denyWebsiteEditWithPopoverIfNeeded()) return
        val hostRule = allRules.find {
            DomainBlockStore.hostPart(it.domain) == host && !DomainBlockStore.isPathRule(it.domain)
        }
        if (hostRule != null) {
            showEditDialog(hostRule.domain)
        } else {
            showAddDialog(host)
        }
    }

    /** Limit button: comprehensive limits editor for the whole-site (host-keyed) limits. */
    private fun handleHostLimit(host: String) {
        if (denyWebsiteEditWithPopoverIfNeeded()) return
        buildGroups(allRules).find { it.host == host } ?: return
        showWebsiteLimitsDialog(host)
    }

    private fun handlePathToggle(rule: String, enabled: Boolean) {
        if (allRules.none { it.domain == rule }) return
        if (denyWebsiteEditWithPopoverIfNeeded()) return
        setRuleEnabled(rule, enabled)
    }

    private fun handlePathEdit(rule: String) {
        if (allRules.none { it.domain == rule }) {
            quickAddRule(rule)
            return
        }
        if (denyWebsiteEditWithPopoverIfNeeded()) return
        showEditDialog(rule)
    }

    private fun denyWebsiteEditWithPopoverIfNeeded(): Boolean {
        return websiteEditingLocked() && denyWebsiteEditWithPopover().let { true }
    }

    private fun showPathsSheet(host: String) {
        val group = buildGroups(allRules).find { it.host == host } ?: return
        val hostRule = group.hostRule
        val rows = group.pathRules.map {
            WebsitePathRow(
                rule = it.domain,
                isHardBlocked = it.isHardBlocked,
                limitMin = it.limitMin,
                sessionLimitMin = it.sessionLimitMin,
                visitLimit = it.visitLimit,
                enabled = it.enabled,
                pending = isRulePending(it.domain),
            )
        }
        // Label for the root rule when it is on. When the root has no rule yet the sheet
        // shows "Not added"; toggling it adds the whole-site rule.
        val hostStateLabel = WebsiteRuleTileAdapter.limitSummary(
            this,
            hostRule?.limitMin ?: 0,
            hostRule?.sessionLimitMin ?: 0,
            hostRule?.visitLimit ?: 0,
        ).ifEmpty {
            getString(if (isAllowMode()) R.string.rule_allowed else R.string.rule_blocked)
        }
        WebsitePathsSheet.show(
            activity = this,
            host = host,
            hostRule = hostRule?.domain,
            hostEnabled = hostRule?.enabled == true,
            hostPending = hostRule?.let { isRulePending(it.domain) } ?: false,
            hostStateLabel = hostStateLabel,
            rows = rows,
            listener = object : WebsitePathsSheet.Listener {
                override fun onRootRuleToggle(host: String, enabled: Boolean) {
                    val existing = allRules.find {
                        !DomainBlockStore.isPathRule(it.domain) &&
                            DomainBlockStore.hostPart(it.domain) == host
                    }
                    when {
                        existing != null -> setRuleEnabled(existing.domain, enabled)
                        enabled -> quickAddRule(host)
                    }
                }

                override fun onRootEdit(host: String) = handleHostEdit(host)
                override fun onPathToggle(rule: String, enabled: Boolean) = handlePathToggle(rule, enabled)
                override fun onPathEdit(rule: String) = handlePathEdit(rule)
                override fun onAddPath(host: String) {
                    if (!canAddBlockedWebsite()) {
                        denyWebsiteEditWithPopover()
                        return
                    }
                    showAddDialog(host)
                }

                override fun isReadOnly(): Boolean = websiteEditingLocked()
            },
        )
    }

    private fun handleSectionAction(key: String) {
        when (key) {
            "suggestions" -> {
                WebsiteSuggestionsVisibilityStore.setVisible(this, false)
                setupSuggestionChips()
                applyListFilter()
                invalidateOptionsMenu()
            }
            "suggestions_show" -> {
                WebsiteSuggestionsVisibilityStore.setVisible(this, true)
                setupSuggestionChips()
                applyListFilter()
                invalidateOptionsMenu()
            }
        }
    }

    /**
     * Readable multi-select picker: the whole site is always an explicit option (checked by
     * default) and quick paths can be added alongside it.
     */
    private fun handleSuggestionAdd(suggestion: WebsiteSuggestion) {
        if (!canAddBlockedWebsite()) {
            denyWebsiteEditWithPopover()
            return
        }
        val options = mutableListOf(suggestion.host)
        if (!isAllowMode()) options.addAll(suggestion.quickPaths)

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val onSurface = ContextCompat.getColor(this, R.color.foqos_on_surface)
        val onSurfaceSoft = ContextCompat.getColor(this, R.color.foqos_on_surface_variant)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(0))
        }
        val checkBoxes = mutableListOf<android.widget.CheckBox>()

        options.forEachIndexed { index, rule ->
            val isHost = rule == suggestion.host
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                isClickable = true
                isFocusable = true
            }
            val cb = android.widget.CheckBox(this).apply {
                isChecked = index == 0
                minWidth = 0
                minHeight = 0
            }
            checkBoxes += cb
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10)
                }
            }
            texts.addView(TextView(this).apply {
                text = if (isHost) suggestion.host else (DomainBlockStore.pathPart(rule) ?: rule)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(onSurface)
            })
            texts.addView(TextView(this).apply {
                text = if (isHost) {
                    getString(R.string.website_tile_whole_site)
                } else {
                    rule
                }
                textSize = 12.5f
                setTextColor(onSurfaceSoft)
            })
            row.addView(cb)
            row.addView(texts)
            row.setOnClickListener { cb.isChecked = !cb.isChecked }
            container.addView(row)
        }

        AlertDialog.Builder(this)
            .setTitle(suggestion.host)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                options.forEachIndexed { i, rule ->
                    if (checkBoxes[i].isChecked) quickAddRule(rule)
                }
            }
            .show()
            .styleLoqInDialogButtons()
    }

    private fun quickAddRule(rule: String) {
        if (!canAddBlockedWebsite()) {
            denyWebsiteEditWithPopover()
            return
        }
        val normalized = DomainBlockStore.normalize(rule) ?: return
        if (isAllowMode() && DomainBlockStore.isPathRule(normalized)) {
            findViewById<View>(android.R.id.content)
                .showWarnPill(R.string.website_rule_path_allow_mode_error)
            return
        }
        val profile = currentProfile()
        DomainBlockStore.addDomainForProfile(this, profile, normalized)
        DomainBlockStore.setDomainEnabledForProfile(this, profile, normalized, true)
        BlockingRuntime.ensureRunning(this)
        findViewById<View>(android.R.id.content)
            .showWarnPill(getString(R.string.website_quick_path_added, normalized))
        refreshList()
    }

    // ---------------------------------------------------------------------------------------------
    // List plumbing / menus / selection
    // ---------------------------------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_manage_blocked_websites, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val readOnly = websiteEditingLocked()
        val hasItems = adapter.itemCount > 0
        menu.findItem(R.id.action_browser_support)?.isVisible = true
        menu.findItem(R.id.action_select)?.isVisible = !readOnly && !isSelectionMode && hasItems
        menu.findItem(R.id.action_cancel_selection)?.isVisible = isSelectionMode
        val selectingSuggestions = isSelectionMode && selectionIsSuggestion
        menu.findItem(R.id.action_delete_selected)?.isVisible = !readOnly && isSelectionMode && !selectionIsSuggestion
        menu.findItem(R.id.action_add_selected)?.isVisible = selectingSuggestions
        val deleteItem = menu.findItem(R.id.action_delete_selected)
        deleteItem?.isEnabled = selectedDomains.isNotEmpty()
        menu.findItem(R.id.action_add_selected)?.isEnabled = selectedDomains.isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (isSelectionMode) {
                    exitSelectionMode()
                    true
                } else {
                    finish()
                    true
                }
            }
            R.id.action_browser_support -> {
                showSupportedBrowsersInfo()
                true
            }
            R.id.action_add_selected -> {
                val toAdd = selectedDomains.toList()
                exitSelectionMode()
                toAdd.forEach { quickAddRule(it) }
                true
            }
            R.id.action_select -> {
                enterSelectionMode()
                true
            }
            R.id.action_cancel_selection -> {
                exitSelectionMode()
                true
            }
            R.id.action_delete_selected -> {
                confirmDeleteSelected()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateMenuState() {
        invalidateOptionsMenu()
        findViewById<MaterialButton>(R.id.btnAddWebsite)?.visibility =
            if (isSelectionMode) View.GONE else View.VISIBLE
        toolbar.updateSelectionSubtitle(
            selectionMode = isSelectionMode,
            selectedCount = selectedDomains.size,
            normalSubtitle = websiteRulesSubtitle()
        )
        syncEditingLockUi()
    }

    private fun normalizeDialogBreaks(text: String): String =
        text.replace("/n", "\n").replace("\\n", "\n")

    private fun showSupportedBrowsersInfo() {
        AlertDialog.Builder(this)
            .setTitle(R.string.website_rules_info_title)
            .setMessage(normalizeDialogBreaks(getString(R.string.website_rules_info_body)))
            .setPositiveButton(android.R.string.ok, null)
            .show()
            .styleLoqInDialogButtons()
    }

    private fun enterSelectionMode(preselect: String? = null, forSuggestions: Boolean = false) {
        if (forSuggestions) {
            if (!canAddBlockedWebsite()) {
                denyWebsiteEditWithPopover()
                return
            }
        } else if (websiteEditingLocked()) {
            return
        }
        selectionIsSuggestion = forSuggestions
        isSelectionMode = true
        selectedDomains.clear()
        preselect?.let { selectedDomains.add(it) }
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
        updateMenuState()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectionIsSuggestion = false
        selectedDomains.clear()
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
        updateMenuState()
    }

    private fun toggleSelection(host: String, isSuggestion: Boolean) {
        if (!isSelectionMode || selectionIsSuggestion != isSuggestion) {
            return
        }
        if (!selectedDomains.remove(host)) {
            selectedDomains.add(host)
        }
        if (selectedDomains.isEmpty()) {
            // Deselecting the last website leaves selection mode entirely.
            exitSelectionMode()
            return
        }
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
        updateMenuState()
    }

    /** Every saved rule owned by [host] (its host rule plus all of its path rules). */
    private fun rulesForHost(host: String): List<String> =
        allRules.filter { DomainBlockStore.hostPart(it.domain) == host }.map { it.domain }

    private fun confirmDeleteSelected() {
        if (selectedDomains.isEmpty()) {
            return
        }
        val rules = selectedDomains.flatMap { rulesForHost(it) }.distinct()
        if (rules.isEmpty()) return
        val count = rules.size
        val dlg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete))
            .setMessage(resources.getQuantityString(R.plurals.delete_websites_confirm, count, count) + "\n\n" + getString(R.string.destructive_cannot_be_undone))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete, null)
            .create()

        dlg.setOnShowListener {
            dlg.styleLoqInDestructivePositiveButton()
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val profile = currentProfile()
                var queued = 0
                var denied = 0
                rules.forEach { rule ->
                    when (ProtectionChangeGate.requestWebsiteRemoval(this@ManageBlockedWebsitesActivity, profile, rule)) {
                        ProtectionChangePolicy.Result.APPLIED -> Unit
                        ProtectionChangePolicy.Result.QUEUED -> queued++
                        ProtectionChangePolicy.Result.DENIED -> denied++
                    }
                }
                rules.forEach { clearLimitIfNoRulesRemainForHost(profile, it) }
                exitSelectionMode()
                refreshList()
                dlg.dismiss()
                when {
                    denied > 0 -> findViewById<View>(android.R.id.content)
                        .showWarnPill(R.string.edit_locked_manage_websites)

                    queued > 0 -> ProtectionFeedback.showQueued(this)
                }
            }
        }

        dlg.show()
    }

    private fun removeRule(rule: String) {
        when (ProtectionChangeGate.requestWebsiteRemoval(this, currentProfile(), rule)) {
            ProtectionChangePolicy.Result.APPLIED -> {
                clearLimitIfNoRulesRemainForHost(currentProfile(), rule)
                refreshList()
            }

            ProtectionChangePolicy.Result.QUEUED -> {
                ProtectionFeedback.showQueued(this)
                refreshList()
            }

            ProtectionChangePolicy.Result.DENIED -> findViewById<View>(android.R.id.content)
                .showWarnPill(R.string.edit_locked_manage_websites)
        }
    }

    /**
     * Path rules are independent of a host rule: deleting "example.com" must leave its path rules
     * (for example "example.com/blocked/&lt;path&gt;") untouched. Limits are stored per host, so the
     * host limit may only be cleared when no rule for that host remains.
     */
    private fun clearLimitIfNoRulesRemainForHost(profile: String, removedRule: String) {
        val normalized = DomainBlockStore.normalize(removedRule) ?: return
        val host = DomainBlockStore.hostPart(normalized)?.takeIf { it.isNotBlank() } ?: return
        val remaining = DomainBlockStore.getDomainsForProfileAndMode(this, profile).any {
            DomainBlockStore.hostPart(it) == host
        } || DomainBlockStore.getDisabledDomainsForProfile(this, profile).any {
            DomainBlockStore.hostPart(it) == host
        }
        if (!remaining) {
            DomainLimitStore.clearForProfile(this, profile, host)
            DomainVisitLimitStore.clearForProfile(this, profile, host)
        }
        DomainVisitLimitStore.clearForProfile(this, profile, normalized)
    }

    private fun setRuleEnabled(rule: String, enabled: Boolean) {
        setRulesEnabled(listOf(rule), enabled)
    }

    private fun setRulesEnabled(rules: List<String>, enabled: Boolean) {
        var queued = 0
        var denied = 0
        rules.forEach { rule ->
            when (ProtectionChangeGate.requestWebsiteEnabled(
                context = this,
                profile = currentProfile(),
                rule = rule,
                currentEnabled = !enabled,
                requestedEnabled = enabled,
            )) {
                ProtectionChangePolicy.Result.APPLIED -> Unit
                ProtectionChangePolicy.Result.QUEUED -> queued++
                ProtectionChangePolicy.Result.DENIED -> denied++
            }
        }
        refreshList()
        when {
            denied > 0 -> findViewById<View>(android.R.id.content)
                .showWarnPill(R.string.edit_locked_manage_websites)

            queued > 0 -> ProtectionFeedback.showQueued(this)
        }
    }

    private fun confirmDeleteSingle(rule: String) {
        val isPath = DomainBlockStore.isPathRule(rule)
        val host = DomainBlockStore.hostPart(rule) ?: rule
        // Deleting a root rule removes its whole group (root + all of its pages) so no
        // orphaned subpages are left stuck on the screen. A page rule deletes only itself.
        val rules = if (isPath) listOf(rule) else rulesForHost(host)
        val count = rules.size
        val message = if (count > 1) {
            resources.getQuantityString(R.plurals.delete_websites_confirm, count, count) +
                "\n\n" + getString(R.string.destructive_cannot_be_undone)
        } else {
            rule + "\n\n" + getString(R.string.destructive_cannot_be_undone)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                rules.forEach { removeRule(it) }
            }
            .showDestructiveAccented()
    }

    private fun showAddDialog(prefill: String = "") {
        if (!canAddBlockedWebsite()) {
            return
        }
        showRuleDialog(
            title = getString(R.string.add_website_rule_title),
            initialDomain = prefill,
            initialHardBlock = true,
            initialLimit = 0,
            allowDomainEdit = true
        )
    }

    private fun showEditDialog(rule: String) {
        if (websiteEditingLocked()) {
            return
        }
        val profile = currentProfile()
        val hard = DomainBlockStore.getDomainsForProfileAndMode(this, profile).contains(rule)
        val limit = DomainLimitStore.getLimitMinutesForProfile(this, profile, rule)

        showRuleDialog(
            title = rule,
            initialDomain = rule,
            initialHardBlock = hard,
            initialLimit = limit,
            allowDomainEdit = false
        )
    }

    /** Limits for one rule: same dialog as the app block page (daily / opens / per-visit). */
    private fun showWebsiteLimitsDialog(rule: String) {
        if (websiteEditingLocked()) return
        val normalized = DomainBlockStore.normalize(rule) ?: return
        QuickLimitDialogs.showWebsiteLimitEditor(
            activity = this,
            profile = currentProfile(),
            rule = normalized,
            label = normalized,
        ) { refreshList() }
    }

    private fun showRuleDialog(
        title: String,
        initialDomain: String,
        initialHardBlock: Boolean,
        initialLimit: Int,
        allowDomainEdit: Boolean
    ) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_domain_rule, FrameLayout(this), false)

        if (CustomAccentApplier.isCustomAccentEnabled(this)) {
            runCatching { CustomAccentApplier.applyToView(v, this) }
        }

        val tilDomain = v.findViewById<TextInputLayout>(R.id.tilDomain)
        val etDomain = v.findViewById<TextInputEditText>(R.id.etDomain)
        val acMode = v.findViewById<AutoCompleteTextView>(R.id.acMode)
        val tilLimit = v.findViewById<TextInputLayout>(R.id.tilDailyLimit)
        val etLimit = v.findViewById<TextInputEditText>(R.id.etDailyLimit)

        etDomain.setText(initialDomain)
        etDomain.isEnabled = allowDomainEdit
        tilDomain.helperText = if (allowDomainEdit && !isAllowMode()) {
            getString(R.string.website_rule_path_hint)
        } else {
            null
        }

        val modeAlways = getString(if (isAllowMode()) R.string.rule_allowed_always else R.string.rule_block_always)
        val modeLimit = getString(R.string.website_mode_limits)

        val modeAdapter = LoqInDropdownAdapter(this, listOf(modeAlways, modeLimit))
        acMode.setAdapter(modeAdapter)
        acMode.threshold = 0
        acMode.setOnClickListener { acMode.showDropDown() }
        val tightenOnlyAdd = websiteEditingLocked() && allowDomainEdit && !isAllowMode()
        acMode.setText(if (tightenOnlyAdd || initialHardBlock) modeAlways else modeLimit, false)
        acMode.isEnabled = !tightenOnlyAdd
        acMode.alpha = if (tightenOnlyAdd) 0.62f else 1f

        etLimit.inputType = InputType.TYPE_CLASS_NUMBER
        etLimit.setText(if (initialLimit > 0) initialLimit.toString() else "")

        fun applyMode() {
            // Values are edited in the limits screen; this dialog only picks the mode.
            val hard = acMode.text?.toString() == modeAlways
            tilLimit.visibility = View.GONE
            if (hard) etLimit.setText("")
        }
        applyMode()

        acMode.setOnItemClickListener { _, _, _, _ ->
            applyMode()
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(v)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
        if (!allowDomainEdit) {
            builder.setNeutralButton(R.string.website_delete_rule, null)
        }
        val dlg = builder.create()

        dlg.setOnShowListener {
            dlg.styleLoqInDialogButtons()
            if (CustomAccentApplier.isCustomAccentEnabled(this)) {
                runCatching { CustomAccentApplier.applyToDialog(dlg) }
            }

            if (!allowDomainEdit) {
                dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    dlg.dismiss()
                    confirmDeleteSingle(initialDomain)
                }
            }

            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (websiteEditingLocked() && !tightenOnlyAdd) {
                    dlg.dismiss()
                    refreshList()
                    return@setOnClickListener
                }
                val domainRaw = etDomain.text?.toString()?.trim().orEmpty()

                if (domainRaw.isBlank()) {
                    tilDomain.error = getString(R.string.domain_required)
                    return@setOnClickListener
                }

                val normalized = DomainBlockStore.normalize(domainRaw)
                if (normalized.isNullOrBlank()) {
                    tilDomain.error = getString(R.string.domain_required)
                    return@setOnClickListener
                }
                if (isAllowMode() && DomainBlockStore.isPathRule(normalized)) {
                    tilDomain.error = getString(R.string.website_rule_path_allow_mode_error)
                    return@setOnClickListener
                }
                tilDomain.error = null

                val hardBlock = acMode.text?.toString() == modeAlways

                if (tightenOnlyAdd && (!hardBlock || isAllowMode())) {
                    return@setOnClickListener
                }

                val profile = currentProfile()
                if (hardBlock) {
                    if (isAllowMode()) {
                        DomainBlockStore.addDomainForProfile(this@ManageBlockedWebsitesActivity, profile, normalized)
                        DomainBlockStore.setDomainEnabledForProfile(this@ManageBlockedWebsitesActivity, profile, normalized, true)
                        DomainLimitStore.clearForProfile(this@ManageBlockedWebsitesActivity, profile, normalized)
                    } else {
                        DomainLimitStore.clearForProfile(this@ManageBlockedWebsitesActivity, profile, normalized)
                        DomainBlockStore.addDomainForProfile(this@ManageBlockedWebsitesActivity, profile, normalized)
                        DomainBlockStore.setDomainEnabledForProfile(this@ManageBlockedWebsitesActivity, profile, normalized, true)
                    }
                    BlockingRuntime.ensureRunning(this@ManageBlockedWebsitesActivity)
                    refreshList()
                    dlg.dismiss()
                } else {
                    // Limits mode: create the rule shell and open the limits screen.
                    if (isAllowMode()) {
                        DomainBlockStore.addDomainForProfile(this@ManageBlockedWebsitesActivity, profile, normalized)
                    } else {
                        DomainBlockStore.removeDomainForProfile(this@ManageBlockedWebsitesActivity, profile, normalized)
                    }
                    DomainBlockStore.setDomainEnabledForProfile(this@ManageBlockedWebsitesActivity, profile, normalized, true)
                    BlockingRuntime.ensureRunning(this@ManageBlockedWebsitesActivity)
                    refreshList()
                    dlg.dismiss()
                    showWebsiteLimitsDialog(normalized)
                }
            }
        }

        dlg.show()
    }

    private data class DomainRule(
        val domain: String,
        val isHardBlocked: Boolean,
        val limitMin: Int,
        val sessionLimitMin: Int,
        val visitLimit: Int,
        val enabled: Boolean
    )
}
