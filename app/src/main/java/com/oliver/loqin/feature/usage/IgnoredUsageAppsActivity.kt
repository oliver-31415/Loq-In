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
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.IgnoredUsageAppsStore
import com.oliver.loqin.data.prefs.InAppRuleStore
import com.oliver.loqin.data.prefs.UsageStore
import com.oliver.loqin.data.statistics.UsageInsightsAppCatalog
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.util.PackageLaunchIntentCompat
import com.oliver.loqin.util.PackageManagerApiCompat
import com.oliver.loqin.util.LocaleHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

/**
 * Manages separate visibility filters for Usage & Insights and app-selection screens.
 * Apps hidden from app-selection screens are also excluded from LoqIn protection while hidden.
 * Changes in either tab remain staged until Save is pressed.
 *
 * The layout mirrors the App rules screen: collapsing section card, pinned search and
 * filter chips, a three-column grid of square tiles, and a pinned Save button.
 */
class IgnoredUsageAppsActivity : AppCompatActivity() {
    private enum class Section {
        USAGE_INSIGHTS,
        APP_PICKERS,
    }

    private lateinit var adapter: IgnoredUsageAppsAdapter
    private lateinit var countView: TextView
    private lateinit var rv: RecyclerView
    private lateinit var searchBox: TextInputLayout
    private lateinit var bulkBar: View
    private var currentSection = Section.USAGE_INSIGHTS
    private var currentFilter = HiddenAppFilter.ALL
    private var searchQuery = ""
    private var usageSelection: Set<String> = emptySet()
    private var appPickerSelection: Set<String> = emptySet()
    private var suggestedPackages: Set<String> = emptySet()
    private var usagePackages: Set<String> = emptySet()
    private var appPickerPackages: Set<String> = emptySet()
    private var loadedItems: List<IgnoredUsageAppItem> = emptyList()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ignored_usage_apps)
        CustomAccentApplier.applyIfNeeded(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationIcon(R.drawable.keyboard_arrow_left_24)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))
        findViewById<View>(R.id.btnHiddenAppsInfo)?.setOnClickListener { showInfoDialog() }
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)

        usageSelection = IgnoredUsageAppsStore.getIgnoredPackages(this)
        appPickerSelection = IgnoredUsageAppsStore.getAppPickerHiddenPackages(this)
        currentSection = if (intent.getBooleanExtra(EXTRA_SHOW_APP_PICKERS, true)) {
            Section.APP_PICKERS
        } else {
            Section.USAGE_INSIGHTS
        }

        countView = findViewById(R.id.tvIgnoredCount)
        searchBox = findViewById(R.id.searchBox)
        bulkBar = findViewById(R.id.bulkBar)
        rv = findViewById(R.id.rvIgnoredUsageApps)

        adapter = IgnoredUsageAppsAdapter(currentSelection()) { selection ->
            when (currentSection) {
                Section.USAGE_INSIGHTS -> usageSelection = selection
                Section.APP_PICKERS -> appPickerSelection = selection
            }
            updateCount(selection.size)
            // The "Hidden" filter depends on the selection, so refresh it live.
            if (currentFilter == HiddenAppFilter.HIDDEN) refreshSection()
        }
        rv.layoutManager = GridLayoutManager(this, 3)
        rv.adapter = adapter

        // Accent the search field to match App rules.
        val accent = AccentColor.getAccentColorInt(this)
        searchBox.setBoxStrokeColorStateList(ColorStateList.valueOf(accent))
        searchBox.setHintTextColor(ColorStateList.valueOf(accent))

        setupSearch()
        setupSectionToggle()
        setupFilterChips()
        setupBulkActions()

        findViewById<MaterialButton>(R.id.btnSaveIgnoredApps).setOnClickListener {
            IgnoredUsageAppsStore.setIgnoredPackages(this, usageSelection)
            IgnoredUsageAppsStore.setAppPickerHiddenPackages(this, appPickerSelection)
            findViewById<View>(R.id.btnSaveIgnoredApps).showWarnPill(R.string.hidden_apps_saved_notice)
        }

        updateCount(currentSelection().size)
        loadApps(usageSelection + appPickerSelection)
    }

    private fun setupSearch() {
        val etSearch = findViewById<TextInputEditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                applyListFilter()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                etSearch.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun applyListFilter() {
        if (!::adapter.isInitialized) return
        adapter.setQuery(searchQuery)
        rv.scrollToPosition(0)
        updateBulkBarVisibility()
        updateCount(currentSelection().size)
    }

    private fun updateBulkBarVisibility() {
        val filtering = searchQuery.isNotBlank() || currentFilter != HiddenAppFilter.ALL
        bulkBar.visibility = if (filtering) View.VISIBLE else View.GONE
    }

    private fun setupBulkActions() {
        findViewById<MaterialButton>(R.id.btnSelectAll).setOnClickListener {
            adapter.selectAllVisible()
        }
        findViewById<MaterialButton>(R.id.btnClearAll).setOnClickListener {
            adapter.clearAllVisible()
        }
    }

    private fun setupFilterChips() {
        val group = findViewById<ChipGroup>(R.id.chipFilters) ?: return

        fun refreshChecks() {
            val accent = AccentColor.getAccentColorInt(this)
            val density = resources.displayMetrics.density
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? Chip ?: continue
                val selected = chip.tag == currentFilter
                chip.isChecked = selected
                chip.chipBackgroundColor = ColorStateList.valueOf(
                    if (selected) ColorUtils.setAlphaComponent(accent, 0x2E) else android.graphics.Color.TRANSPARENT
                )
                chip.chipStrokeColor = ColorStateList.valueOf(
                    if (selected) accent else ContextCompat.getColor(this, R.color.foqos_outline_variant)
                )
                chip.chipStrokeWidth = 1f * density
            }
        }

        fun addChip(label: String, filter: HiddenAppFilter) {
            val chip = Chip(this, null, com.google.android.material.R.style.Widget_Material3_Chip_Suggestion).apply {
                text = label
                isClickable = true
                isFocusable = true
                tag = filter
                id = View.generateViewId()
                setOnClickListener {
                    currentFilter = tag as HiddenAppFilter
                    refreshChecks()
                    adapter.setFilter(currentFilter)
                    rv.scrollToPosition(0)
                    updateBulkBarVisibility()
                }
            }
            group.addView(chip)
        }

        addChip(getString(R.string.hidden_apps_filter_all), HiddenAppFilter.ALL)
        addChip(getString(R.string.hidden_apps_filter_suggested), HiddenAppFilter.SUGGESTED)
        addChip(getString(R.string.hidden_apps_filter_hidden), HiddenAppFilter.HIDDEN)
        refreshChecks()
        updateBulkBarVisibility()
    }

    private fun setupSectionToggle() {
        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.toggleHiddenAppsSection)
        val pickerButton = findViewById<MaterialButton>(R.id.btnHiddenAppPickers)
        val usageButton = findViewById<MaterialButton>(R.id.btnHiddenUsageInsights)
        val selectedId = if (currentSection == Section.APP_PICKERS) {
            R.id.btnHiddenAppPickers
        } else {
            R.id.btnHiddenUsageInsights
        }
        toggle.check(selectedId)
        SegmentedToggleUi.apply(this, listOf(pickerButton, usageButton), selectedId)
        updateSectionDescription()
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentSection = if (checkedId == R.id.btnHiddenUsageInsights) {
                Section.USAGE_INSIGHTS
            } else {
                Section.APP_PICKERS
            }
            SegmentedToggleUi.apply(this, listOf(pickerButton, usageButton), checkedId)
            updateSectionDescription()
            refreshSection()
        }
    }

    private fun updateSectionDescription() {
        val descView = findViewById<TextView>(R.id.tvSectionDescription) ?: return
        val descRes = when (currentSection) {
            Section.USAGE_INSIGHTS -> R.string.hidden_apps_tab_usage_subtitle
            Section.APP_PICKERS -> R.string.hidden_apps_tab_pickers_subtitle
        }
        descView.setText(descRes)
    }

    private fun refreshSection() {
        val selection = currentSelection()
        adapter.replaceSelection(selection)
        val visiblePackages = when (currentSection) {
            Section.USAGE_INSIGHTS -> usagePackages
            Section.APP_PICKERS -> appPickerPackages
        }
        val items = loadedItems
            .asSequence()
            .filter { it.packageName in visiblePackages }
            .map { item ->
                item.copy(
                    suggested = item.packageName in suggestedPackages,
                )
            }
            .sortedWith(
                compareByDescending<IgnoredUsageAppItem> { it.packageName in selection }
                    .thenByDescending { it.suggested }
                    .thenBy { it.label.lowercase(Locale.getDefault()) },
            )
            .toList()
        adapter.setItems(items)
        adapter.setFilter(currentFilter)
        updateCount(selection.size)
    }

    private fun currentSelection(): Set<String> {
        return when (currentSection) {
            Section.USAGE_INSIGHTS -> usageSelection
            Section.APP_PICKERS -> appPickerSelection
        }
    }

    private fun showInfoDialog() {
        val titleRes = when (currentSection) {
            Section.USAGE_INSIGHTS -> R.string.hidden_apps_tab_usage
            Section.APP_PICKERS -> R.string.hidden_apps_tab_pickers
        }
        val message = when (currentSection) {
            Section.USAGE_INSIGHTS -> R.string.hidden_apps_usage_description
            Section.APP_PICKERS -> R.string.hidden_apps_picker_description
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .showAccented()
    }

    private fun loadApps(selected: Set<String>) {
        val appContext = applicationContext
        Thread {
            suggestedPackages = IgnoredUsageAppsStore.suggestedPackages(appContext)
            val launchablePackages = queryLaunchablePackages(appContext)
            usagePackages = buildSet {
                addAll(launchablePackages)
                addAll(UsageStore.getUsageMsMapOverall(appContext).keys)
                addAll(suggestedPackages)
                addAll(usageSelection)
            }
            appPickerPackages = buildSet {
                addAll(launchablePackages.filterNot { it == appContext.packageName })
                addAll(
                    InAppRuleStore.supportedPackages()
                        .filter { PackageLaunchIntentCompat.isLaunchable(appContext, it) }
                )
                addAll(appPickerSelection)
            }
            val packages = usagePackages + appPickerPackages + selected

            loadedItems = packages
                .filterNot(UsageInsightsAppCatalog::shouldAlwaysHide)
                .mapNotNull { packageName ->
                    val appInfo = applicationInfo(appContext, packageName) ?: return@mapNotNull null
                    IgnoredUsageAppItem(
                        packageName = packageName,
                        label = appContext.packageManager.getApplicationLabel(appInfo).toString(),
                        // Icons load through AppIconCache in the adapter off the main thread.
                        suggested = false,
                    )
                }

            runOnUiThread {
                if (!isFinishing && !isDestroyed) refreshSection()
            }
        }.start()
    }

    private fun queryLaunchablePackages(context: Context): Set<String> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            PackageManagerApiCompat.queryIntentActivities(
                packageManager = context.packageManager,
                intent = launcherIntent,
            ).mapNotNullTo(linkedSetOf()) { it.activityInfo?.packageName }
        }.getOrDefault(emptySet())
    }

    private fun applicationInfo(context: Context, packageName: String): ApplicationInfo? {
        return runCatching {
            PackageManagerApiCompat.getApplicationInfo(
                packageManager = context.packageManager,
                packageName = packageName,
            )
        }.getOrNull()
    }

    private fun updateCount(count: Int) {
        countView.text = resources.getQuantityString(
            R.plurals.ignored_usage_apps_count,
            count,
            count,
        )
    }

    companion object {
        private const val EXTRA_SHOW_APP_PICKERS = "extra_show_app_pickers"

        fun intent(context: Context, showAppPickers: Boolean = true): Intent =
            Intent(context, IgnoredUsageAppsActivity::class.java)
                .putExtra(EXTRA_SHOW_APP_PICKERS, showAppPickers)
    }
}
