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
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.core.view.children
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.oliver.loqin.R
import com.oliver.loqin.ui.dialog.InputSheet
import com.oliver.loqin.data.prefs.AttemptLimitStore
import com.oliver.loqin.data.prefs.ScreenUnlockHistoryStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.SessionLimitStore
import com.oliver.loqin.data.prefs.UsageLimitStore
import com.oliver.loqin.feature.stats.StatsFormat
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.PageUi
import com.oliver.loqin.ui.LoqInDropdownAdapter
import com.oliver.loqin.ui.ToolbarIconAction
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.styleForDialog
import com.oliver.loqin.util.LocaleHelper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class ScreenUnlocksActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: LinearLayout
    private var firstResume = true
    private var currentRange = Range.TODAY
    private var currentSort = Sort.NEWEST
    private var currentScope = Scope.ALL
    private var hideVeryShortUnlocks = false
    private var customRangeStartMillis: Long? = null
    private var customRangeEndMillis: Long? = null
    private var customRangePickerShowing = false

    private enum class Range(val labelRes: Int, val queryName: String) {
        TODAY(R.string.stats_range_today, "today"),
        WEEK(R.string.stats_range_week, "week"),
        MONTH(R.string.stats_range_month, "month"),
        YEAR(R.string.stats_range_year, "year"),
        CUSTOM(R.string.activity_history_range_custom, "custom")
    }

    private enum class Sort {
        NEWEST,
        OLDEST,
        LONGEST,
        SHORTEST
    }

    private enum class Scope {
        ALL,
        CURRENT_PROFILE
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toolbar = MaterialToolbar(this).apply {
            minimumHeight = actionBarSize()
            title = getString(R.string.activity_entry_screen_unlocks)
            setNavigationIcon(R.drawable.keyboard_arrow_left_24)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        UsageInfoAction.attach(this, toolbar, R.string.screen_unlocks_info_title, R.string.screen_unlocks_info_body)
        ToolbarIconAction.attach(
            activity = this,
            toolbar = toolbar,
            iconRes = R.drawable.tune_24,
            titleRes = R.string.screen_unlocks_sort_filter_title,
        ) { showSortFilterDialog() }
        root.addView(AppBarLayout(this).apply {
            fitsSystemWindows = true
            addView(toolbar, AppBarLayout.LayoutParams(
                AppBarLayout.LayoutParams.MATCH_PARENT,
                AppBarLayout.LayoutParams.WRAP_CONTENT
            ))
        })

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        PageUi.applyPageContentPadding(content)
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        val coordinator = CoordinatorLayout(this)
        coordinator.addView(root, CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(coordinator)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        updateScopeSubtitle()
        load()
    }

    override fun onResume() {
        super.onResume()
        if (firstResume) {
            firstResume = false
            return
        }
        if (::content.isInitialized) load()
    }

    private fun load() {
        content.removeAllViews()
        addRangeChips()
        content.addView(StatsPageUi.emptyState(this, getString(R.string.usage_timeline_loading)))
        val ctx = applicationContext
        val range = currentRange
        val scope = currentScope
        val hasUsageAccess = UsageStatsRepo.hasUsageAccess(this)
        val profilePackages = if (scope == Scope.CURRENT_PROFILE) currentProfilePackages() else emptySet()
        Thread {
            val (from, to) = windowForRange(range)
            if (hasUsageAccess) {
                runCatching { StatsArchiveSync.sync(ctx) }
            }
            val profileAppSessions = if (hasUsageAccess && scope == Scope.CURRENT_PROFILE && profilePackages.isNotEmpty()) {
                UsageTimelineRepo.allAppSessions(ctx, from, to, limit = 0)
                    .filter { it.packageName in profilePackages }
            } else {
                emptyList()
            }
            val archived = ScreenUnlockHistoryStore.sessionsForRange(ctx, from, to)
                .map { UsageTimelineRepo.UnlockSession(it.startMs, it.endMs) }
            val live = if (hasUsageAccess) UsageTimelineRepo.screenUnlockSessions(ctx, from, to, limit = 0) else emptyList()
            if (live.isNotEmpty()) {
                ScreenUnlockHistoryStore.mergeSessions(ctx, live.map { ScreenUnlockHistoryStore.Session(it.startMs, it.endMs) })
            }
            val sessions = (archived + live)
                .groupBy { it.startMs }
                .map { (_, sameStart) -> sameStart.maxByOrNull { it.endMs } ?: sameStart.first() }
                .let { list ->
                    if (scope == Scope.CURRENT_PROFILE && hasUsageAccess) {
                        list.filter { unlock ->
                            profileAppSessions.any { app ->
                                app.endMs > unlock.startMs && app.startMs < unlock.endMs
                            }
                        }
                    } else {
                        list
                    }
                }
                .filter { !hideVeryShortUnlocks || it.durationMs >= 30_000L }
                .let { list ->
                    when (currentSort) {
                        Sort.NEWEST -> list.sortedByDescending { it.startMs }
                        Sort.OLDEST -> list.sortedBy { it.startMs }
                        Sort.LONGEST -> list.sortedByDescending { it.durationMs }
                        Sort.SHORTEST -> list.sortedBy { it.durationMs }
                    }
                }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (range != currentRange) return@runOnUiThread
                if (scope != currentScope) return@runOnUiThread
                if (sessions.isEmpty() && !hasUsageAccess) {
                    content.removeAllViews()
                    addRangeChips()
                    content.addView(
                        StatsPageUi.emptyState(this, getString(R.string.screen_unlocks_permission_needed), R.drawable.lock_open_24).apply {
                            setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        }
                    )
                } else {
                    render(sessions)
                }
            }
        }.start()
    }

    private fun showSortFilterDialog() {
        val root = layoutInflater.inflate(R.layout.dialog_statistics_dropdown_sort_filter, FrameLayout(this), false)
        val scopeDropdown = root.findViewById<MaterialAutoCompleteTextView>(R.id.dropdownStatsPrimary)
        val sortDropdown = root.findViewById<MaterialAutoCompleteTextView>(R.id.dropdownStatsSort)
        val hideShort = root.findViewById<CheckBox>(R.id.cbStatsExtraFilter)

        root.findViewById<TextView>(R.id.tvStatsDropdownPrimaryLabel).text = getString(R.string.stats_scope_title)
        val scopeOptions = listOf(
            Scope.ALL to getString(R.string.stats_scope_all),
            Scope.CURRENT_PROFILE to getString(R.string.stats_scope_current_profile)
        )
        val sortOptions = listOf(
            Sort.NEWEST to getString(R.string.screen_unlocks_sort_newest),
            Sort.OLDEST to getString(R.string.screen_unlocks_sort_oldest),
            Sort.LONGEST to getString(R.string.screen_unlocks_sort_longest),
            Sort.SHORTEST to getString(R.string.screen_unlocks_sort_shortest)
        )
        var selectedScope = currentScope
        var selectedSort = currentSort

        scopeDropdown.setAdapter(LoqInDropdownAdapter(this, scopeOptions.map { it.second }))
        scopeDropdown.setText(scopeOptions.first { it.first == currentScope }.second, false)
        scopeDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedScope = scopeOptions.getOrElse(position) { scopeOptions.first() }.first
        }
        scopeDropdown.setOnClickListener { scopeDropdown.showDropDown() }

        sortDropdown.setAdapter(LoqInDropdownAdapter(this, sortOptions.map { it.second }))
        sortDropdown.setText(sortOptions.first { it.first == currentSort }.second, false)
        sortDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedSort = sortOptions.getOrElse(position) { sortOptions.first() }.first
        }
        sortDropdown.setOnClickListener { sortDropdown.showDropDown() }

        hideShort.text = getString(R.string.screen_unlocks_filter_short)
        hideShort.isChecked = hideVeryShortUnlocks
        hideShort.visibility = View.VISIBLE
        hideShort.styleForDialog(this)

        val sheet = InputSheet(this, getString(R.string.screen_unlocks_sort_filter_title))
        sheet.content(root)
        sheet.primary(getString(R.string.stats_apply)) {
            currentScope = selectedScope
            currentSort = selectedSort
            hideVeryShortUnlocks = hideShort.isChecked
            updateScopeSubtitle()
            load()
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun updateScopeSubtitle() {
        toolbar.subtitle = if (currentScope == Scope.CURRENT_PROFILE) {
            ProfileStore.getCurrent(this)?.let { getString(R.string.profile_active_fmt, it) }
        } else {
            null
        }
    }

    private fun currentProfilePackages(): Set<String> {
        val profile = ProfileStore.getCurrent(this)?.takeIf { it.isNotBlank() } ?: return emptySet()
        return buildSet {
            addAll(ProfileStore.getSelectedForProfileMode(this@ScreenUnlocksActivity, profile))
            addAll(UsageLimitStore.getAllLimitedPackages(this@ScreenUnlocksActivity, profile))
            addAll(SessionLimitStore.getAllLimitedPackages(this@ScreenUnlocksActivity, profile))
            addAll(AttemptLimitStore.getAllLimitedPackages(this@ScreenUnlocksActivity, profile))
        }
    }

    private fun render(sessions: List<UsageTimelineRepo.UnlockSession>) {
        content.removeAllViews()
        addRangeChips()
        if (sessions.isEmpty()) {
            content.addView(StatsPageUi.emptyState(this, getString(R.string.screen_unlocks_empty), R.drawable.lock_open_24))
            return
        }
        content.addView(
            StatsPageUi.summaryCard(
                content,
                getString(R.string.screen_unlocks_detail_summary_title),
                resources.getQuantityString(R.plurals.screen_unlocks_count, sessions.size, sessions.size)
            ).view
        )

        val grouped = linkedMapOf<Long, MutableList<UsageTimelineRepo.UnlockSession>>()
        for (session in sessions) {
            grouped.getOrPut(dayStartMillis(session.startMs)) { mutableListOf() }.add(session)
        }

        grouped.forEach { (dayStart, daySessions) ->
            StatsPageUi.addSectionHeader(content, formatDayHeader(dayStart))
            val group = StatsPageUi.listGroup(content)
            daySessions.forEach { session -> group.addView(unlockRow(group, session)) }
            content.addView(group)
        }
    }

    private fun unlockRow(parent: ViewGroup, session: UsageTimelineRepo.UnlockSession): View =
        StatsPageUi.row(
            parent = parent,
            title = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(session.startMs)),
            summary = getString(R.string.screen_unlocks_row_summary, StatsFormat.prettyMsWithSeconds(session.durationMs)),
            iconRes = R.drawable.lock_open_24,
        ) {
            startActivity(ScreenUnlockDetailActivity.intent(this, session.startMs, session.endMs))
        }

    private fun dayStartMillis(timeMs: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun formatDayHeader(dayStartMs: Long): String {
        val todayCal = Calendar.getInstance().apply {
            timeInMillis = startOfTodayMillis()
        }
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = dayStartMs
        }

        val isToday = targetCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                targetCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

        todayCal.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = targetCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                targetCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

        val dateStr = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dayStartMs))
        return when {
            isToday -> "${getString(R.string.stats_range_today)} · $dateStr"
            isYesterday -> "${getString(R.string.relative_time_yesterday).replaceFirstChar { it.uppercase() }} · $dateStr"
            else -> dateStr
        }
    }

    private fun addRangeChips() {
        val ids = mutableMapOf<Int, Range>()
        var checkedId = View.NO_ID
        val group = StatsPageUi.rangeBar(this)
        Range.values().forEach { range ->
            val button = if (range == Range.CUSTOM) {
                StatsPageUi.calendarSegment(this)
            } else {
                StatsPageUi.rangeSegment(this, getString(range.labelRes))
            }
            ids[button.id] = range
            if (range == currentRange) checkedId = button.id
            group.addView(button)
        }
        if (checkedId != View.NO_ID) group.check(checkedId)
        ids.forEach { (buttonId, range) ->
            group.findViewById<MaterialButton>(buttonId)?.let { it.isChecked = (range == currentRange); styleRangeButton(it, range == currentRange) }
        }
        group.addOnButtonCheckedListener { _, checkedButtonId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selected = ids[checkedButtonId] ?: return@addOnButtonCheckedListener
            if (selected == currentRange) return@addOnButtonCheckedListener
            if (!ensureRangeAllowed(selected)) {
                load()
                return@addOnButtonCheckedListener
            }
            if (selected == Range.CUSTOM) {
                showCustomRangePicker()
                return@addOnButtonCheckedListener
            }
            currentRange = selected
            load()
        }
        content.addView(group)
        addCustomRangeSummary()
    }

    private fun ensureRangeAllowed(range: Range): Boolean {
        // Extended stat ranges are available to everyone.
        return true
    }

    private fun addCustomRangeSummary() {
        if (currentRange != Range.CUSTOM) {
            return
        }
        val start = customRangeStartMillis ?: return
        val end = customRangeEndMillis ?: return
        val fmt = DateFormat.getDateInstance(DateFormat.SHORT)

        val row = StatsPageUi.customRangeRow(content) {
            customRangeStartMillis = null
            customRangeEndMillis = null
            currentRange = Range.TODAY
            load()
        }
        row.findViewById<TextView>(R.id.statsCustomRangeValue).text = getString(
            R.string.activity_history_range_custom_value,
            fmt.format(Date(start)),
            fmt.format(Date(end))
        )
        content.addView(row)
    }


    private fun styleRangeButton(button: MaterialButton, active: Boolean) {
        // Shared segmented look: one pill control, filled-accent selection.
        // (Same language as SegmentedToggleUi on the detail pages.)
        val buttons = (button.parent as? android.view.ViewGroup)
            ?.children
            ?.filterIsInstance<com.google.android.material.button.MaterialButton>()
            ?.toList() ?: listOf(button)
        val selectedId = buttons.firstOrNull { it.isChecked }?.id ?: button.id
        com.oliver.loqin.ui.SegmentedToggleUi.apply(this, buttons, selectedId)
    }

    private fun windowForRange(range: Range): Pair<Long, Long> {
        if (range != Range.CUSTOM) {
            val (from, to) = UsageTimelineRepo.windowForRange(range.queryName)
            return Pair(from, to)
        }
        return Pair(customRangeStartMillis ?: startOfTodayMillis(), customRangeEndMillis ?: System.currentTimeMillis())
    }

    private fun showCustomRangePicker() {
        if (!ensureRangeAllowed(Range.CUSTOM)) {
            load()
            return
        }
        if (customRangePickerShowing || supportFragmentManager.isStateSaved) {
            return
        }
        customRangePickerShowing = true
        val now = System.currentTimeMillis()
        val currentStart = customRangeStartMillis ?: startOfTodayMillis()
        val currentEnd = customRangeEndMillis ?: now
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTheme(com.oliver.loqin.theme.AccentColor.getDatePickerTheme(this))
            .setTitleText(R.string.activity_history_range_custom)
            .setSelection(androidx.core.util.Pair(localDayToDatePickerUtcMillis(currentStart), localDayToDatePickerUtcMillis(currentEnd)))
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            val startUtc = selection.first ?: return@addOnPositiveButtonClickListener
            val endUtc = selection.second ?: startUtc
            customRangeStartMillis = datePickerUtcMillisToLocalDayStart(minOf(startUtc, endUtc))
            customRangeEndMillis = datePickerUtcMillisToLocalDayEnd(maxOf(startUtc, endUtc))
            currentRange = Range.CUSTOM
            load()
        }
        picker.addOnDismissListener { customRangePickerShowing = false }
        runCatching { picker.show(supportFragmentManager, "screen_unlocks_custom_range") }
            .onSuccess { UsageDatePickerAccentTint.apply(this, picker) }
            .onFailure { customRangePickerShowing = false }
    }

    private fun actionBarSize(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
        return android.util.TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
    }

    private fun startOfTodayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun localDayToDatePickerUtcMillis(localMillis: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = localMillis }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    private fun datePickerUtcMillisToLocalDayStart(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    private fun datePickerUtcMillisToLocalDayEnd(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }


    companion object {
        fun intent(context: Context) = Intent(context, ScreenUnlocksActivity::class.java)
    }
}
