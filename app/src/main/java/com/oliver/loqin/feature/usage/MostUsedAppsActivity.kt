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
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import androidx.core.graphics.ColorUtils
import androidx.core.util.Pair
import androidx.lifecycle.lifecycleScope
import com.oliver.loqin.R
import com.oliver.loqin.feature.onboarding.OnboardingUsageDonutView
import com.oliver.loqin.feature.stats.StatsFormat
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.PageUi
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.showAccented
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Dedicated screen-time distribution view for the most used apps on the device. */
class MostUsedAppsActivity : AppCompatActivity() {

    private enum class Range(val labelRes: Int, val detailRange: String) {
        TODAY(R.string.stats_range_today, AppUsageDetailActivity.RANGE_TODAY),
        WEEK(R.string.stats_range_week, AppUsageDetailActivity.RANGE_WEEK),
        MONTH(R.string.stats_range_month, AppUsageDetailActivity.RANGE_MONTH),
        YEAR(R.string.stats_range_year, AppUsageDetailActivity.RANGE_YEAR),
        CUSTOM(R.string.activity_history_range_custom, AppUsageDetailActivity.RANGE_CUSTOM)
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: LinearLayout
    private var currentRange: Range = Range.TODAY
    private var customRangeStartMillis: Long? = null
    private var customRangeEndMillis: Long? = null
    private var customRangePickerShowing = false
    private var loadJob: Job? = null
    private var loadVersion = 0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.oliver.loqin.util.LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resolveColor(android.R.attr.colorBackground))
        }

        toolbar = MaterialToolbar(this).apply {
            minimumHeight = actionBarSize()
            title = getString(R.string.most_used_apps_title)
            setNavigationIcon(R.drawable.keyboard_arrow_left_24)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            menu.add(R.string.most_used_apps_info_title).apply {
                setIcon(R.drawable.info_24)
                icon?.mutate()?.setTint(toolbarIconColor())
                setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
                setOnMenuItemClickListener {
                    showInfo()
                    true
                }
            }
        }
        root.addView(
            AppBarLayout(this).apply {
                fitsSystemWindows = true
                addView(
                    toolbar,
                    AppBarLayout.LayoutParams(
                        AppBarLayout.LayoutParams.MATCH_PARENT,
                        AppBarLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        PageUi.applyPageContentPadding(content)
        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        EdgeToEdgeUtils.applyThemedSystemBars(this)

        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun showInfo() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.most_used_apps_info_title)
            .setMessage(R.string.most_used_apps_info_body)
            .setPositiveButton(android.R.string.ok, null)
            .showAccented()
    }

    private fun load() {
        renderLoadingShell()
        val request = ++loadVersion
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val hasUsageAccess = withContext(Dispatchers.IO) {
                UsageStatsRepo.hasUsageAccess(this@MostUsedAppsActivity)
            }
            val summary = if (hasUsageAccess) {
                withContext(Dispatchers.IO) {
                    runCatching { StatsArchiveSync.sync(this@MostUsedAppsActivity) }
                    loadSummary(currentRange)
                }
            } else {
                UsageSummary(0L, emptyList())
            }
            if (request != loadVersion) return@launch
            render(hasUsageAccess, summary)
        }
    }

    private fun loadSummary(range: Range): UsageSummary = when (range) {
        Range.TODAY -> AppUsageRepo.getTodaySummary(this, topN = 100)
        Range.WEEK -> AppUsageRepo.getLastNDaysSummary(this, 7, topN = 100)
        Range.MONTH -> AppUsageRepo.getThisMonthSummary(this, topN = 100)
        Range.YEAR -> AppUsageRepo.getThisYearSummary(this, topN = 100)
        Range.CUSTOM -> {
            val start = customRangeStartMillis
            val end = customRangeEndMillis
            if (start != null && end != null) {
                AppUsageRepo.getDateRangeSummary(this, start, end, topN = 100)
            } else {
                UsageSummary(0L, emptyList())
            }
        }
    }

    private fun renderLoadingShell() {
        content.removeAllViews()
        addRangeControls()
        content.addView(StatsPageUi.emptyState(this, getString(R.string.most_used_apps_loading)))
    }

    private fun render(hasUsageAccess: Boolean, summary: UsageSummary) {
        content.removeAllViews()
        addRangeControls()

        if (!hasUsageAccess) {
            content.addView(
                StatsPageUi.emptyState(this, getString(R.string.most_used_apps_usage_access_needed), R.drawable.bar_chart_24).apply {
                    setAction(getString(R.string.usage_open_settings)) {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }
            )
            return
        }

        val apps = summary.topApps.filter { it.timeMs > 0L }
        if (summary.totalTimeMs <= 0L || apps.isEmpty()) {
            content.addView(StatsPageUi.emptyState(this, getString(R.string.most_used_apps_empty), R.drawable.bar_chart_24))
            return
        }

        content.addView(distributionCard(summary, apps))
        PageUi.addSectionHeader(content, R.string.most_used_apps_ranking_title)
        val group = StatsPageUi.listGroup(content)
        apps.forEachIndexed { index, app -> group.addView(rankingRow(group, index + 1, app)) }
        content.addView(group)
    }

    private fun addRangeControls() {
        val ids = mutableMapOf<Int, Range>()
        val group = StatsPageUi.rangeBar(this)

        var checkedId = View.NO_ID
        Range.values().forEach { range ->
            val button = if (range == Range.CUSTOM) {
                StatsPageUi.calendarSegment(this)
            } else {
                StatsPageUi.rangeSegment(this, getString(range.labelRes))
            }
            ids[button.id] = range
            group.addView(button)
            if (range == currentRange) {
                group.check(button.id)
                checkedId = button.id
            }
        }
        SegmentedToggleUi.apply(this, ids.keys.mapNotNull { group.findViewById<MaterialButton>(it) }, checkedId)

        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selected = ids[checkedId] ?: return@addOnButtonCheckedListener
            if (selected == currentRange) return@addOnButtonCheckedListener
            if (selected == Range.CUSTOM) {
                showCustomRangePicker()
            } else {
                currentRange = selected
                load()
            }
        }

        content.addView(group)
        addCustomRangeSummary()
    }

    private fun addCustomRangeSummary() {
        if (currentRange != Range.CUSTOM) return
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

    private fun showCustomRangePicker() {
        if (customRangePickerShowing || supportFragmentManager.isStateSaved) return
        customRangePickerShowing = true
        val now = System.currentTimeMillis()
        val start = customRangeStartMillis ?: startOfTodayMillis()
        val end = customRangeEndMillis ?: now
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTheme(com.oliver.loqin.theme.AccentColor.getDatePickerTheme(this))
            .setTitleText(R.string.activity_history_range_custom)
            .setSelection(Pair(localDayToDatePickerUtcMillis(start), localDayToDatePickerUtcMillis(end)))
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val startUtc = selection.first ?: return@addOnPositiveButtonClickListener
            val endUtc = selection.second ?: startUtc
            customRangeStartMillis = datePickerUtcMillisToLocalDayStart(minOf(startUtc, endUtc))
            customRangeEndMillis = datePickerUtcMillisToLocalDayEnd(maxOf(startUtc, endUtc))
            currentRange = Range.CUSTOM
            load()
        }
        picker.addOnDismissListener {
            customRangePickerShowing = false
            if (currentRange != Range.CUSTOM && !isFinishing && !isDestroyed) {
                load()
            }
        }
        runCatching { picker.show(supportFragmentManager, "most_used_apps_custom_range") }
            .onSuccess { UsageDatePickerAccentTint.apply(this, picker) }
            .onFailure { customRangePickerShowing = false }
    }

    private fun distributionCard(summary: UsageSummary, apps: List<AppUsage>): View {
        val card = StatsPageUi.summaryCard(
            content,
            getString(R.string.most_used_apps_total_title),
            StatsFormat.prettyMsWithSeconds(summary.totalTimeMs)
        )
        card.content.addView(FrameLayout(this).apply {
            addView(OnboardingUsageDonutView(this@MostUsedAppsActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                submitSegments(buildSegments(summary, apps))
            })
        }, LinearLayout.LayoutParams(dp(252), dp(252)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(16)
        })
        return card.view
    }

    private fun buildSegments(summary: UsageSummary, apps: List<AppUsage>): List<OnboardingUsageDonutView.Segment> {
        val chartApps = apps.take(6)
        val accent = AccentColor.getAccentColorInt(this)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        val isDarkAccent = ColorUtils.calculateLuminance(accent) < 0.24
        val colors = IntArray(chartApps.size) { index ->
            if (index == 0) {
                accent
            } else {
                ColorUtils.HSLToColor(
                    floatArrayOf(
                        (hsl[0] + index * 45f) % 360f,
                        (hsl[1] * if (index % 2 == 0) 0.88f else 0.98f).coerceIn(0.44f, 0.94f),
                        (if (isDarkAccent) 0.54f else 0.42f + (index % 3) * 0.045f).coerceIn(0.34f, 0.72f)
                    )
                )
            }
        }

        val total = summary.totalTimeMs.coerceAtLeast(1L).toFloat()
        val segments = chartApps.mapIndexed { index, app ->
            val fraction = (app.timeMs / total).coerceIn(0f, 1f)
            OnboardingUsageDonutView.Segment(
                fraction = fraction,
                color = colors[index],
                icon = app.icon?.constantState?.newDrawable(resources)?.mutate() ?: app.icon,
                percentageLabel = formatPercent(fraction)
            )
        }.toMutableList()

        val shownFraction = segments.sumOf { it.fraction.toDouble() }.toFloat().coerceIn(0f, 1f)
        val otherFraction = (1f - shownFraction).coerceAtLeast(0f)
        if (otherFraction > 0.005f) {
            val otherColor = ColorUtils.blendARGB(
                MaterialColors.getColor(content, com.google.android.material.R.attr.colorOnSurface),
                MaterialColors.getColor(content, com.google.android.material.R.attr.colorSurface),
                0.72f
            )
            segments += OnboardingUsageDonutView.Segment(
                fraction = otherFraction,
                color = otherColor,
                label = getString(R.string.usage_chart_other_apps),
                percentageLabel = formatPercent(otherFraction)
            )
        }
        return segments
    }

    private fun rankingRow(parent: ViewGroup, rank: Int, app: AppUsage): View {
        val row = StatsPageUi.row(
            parent = parent,
            title = app.label,
            summary = getString(
                R.string.most_used_apps_row_summary,
                StatsFormat.prettyMsWithSeconds(app.timeMs),
                StatsFormat.prettyPercent(app.percent)
            ),
            appIcon = app.icon,
        ) { openAppDetail(app) }
        // Rank before the app icon, in the trailing-value style so it reads as a quiet label.
        (row as ViewGroup).addView(TextView(this).apply {
            text = String.format(Locale.getDefault(), "%d", rank)
            gravity = Gravity.CENTER
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_LoqIn_RowValue)
        }, 0, LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(8)
        })
        return row
    }

    private fun openAppDetail(app: AppUsage) {
        val intent = Intent(this, AppUsageDetailActivity::class.java)
            .putExtra(AppUsageDetailActivity.EXTRA_PKG, app.packageName)
            .putExtra(AppUsageDetailActivity.EXTRA_LABEL, app.label)
            .putExtra(AppUsageDetailActivity.EXTRA_INITIAL_RANGE, currentRange.detailRange)
        if (currentRange == Range.CUSTOM) {
            customRangeStartMillis?.let { intent.putExtra(AppUsageDetailActivity.EXTRA_INITIAL_START_MS, it) }
            customRangeEndMillis?.let { intent.putExtra(AppUsageDetailActivity.EXTRA_INITIAL_END_MS, it) }
        }
        startActivity(intent)
    }

    private fun formatPercent(fraction: Float): String {
        val normalized = fraction.coerceIn(0f, 1f)
        val percent = (normalized * 100f).roundToInt()
        return if (normalized > 0f && percent == 0) "<1%" else "$percent%"
    }

    private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun localDayToDatePickerUtcMillis(localMillis: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = localMillis }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun datePickerUtcMillisToLocalDayStart(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun datePickerUtcMillisToLocalDayEnd(utcMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = datePickerUtcMillisToLocalDayStart(utcMillis)
        add(Calendar.DAY_OF_YEAR, 1)
        add(Calendar.MILLISECOND, -1)
    }.timeInMillis

    private fun actionBarSize(): Int {
        val typed = android.util.TypedValue()
        val ok = theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typed, true)
        return if (ok) android.util.TypedValue.complexToDimensionPixelSize(typed.data, resources.displayMetrics) else dp(56)
    }

    private fun toolbarIconColor(): Int {
        return if (MaterialColors.isColorLight(AccentColor.getToolbarColor(this))) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    private fun resolveColor(attr: Int): Int {
        val typed = android.util.TypedValue()
        theme.resolveAttribute(attr, typed, true)
        return typed.data
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        fun intent(context: Context): Intent = Intent(context, MostUsedAppsActivity::class.java)
    }
}
