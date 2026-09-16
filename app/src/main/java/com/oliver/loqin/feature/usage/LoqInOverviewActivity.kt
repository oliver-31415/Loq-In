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
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.ActiveDurationStore
import com.oliver.loqin.data.prefs.AppLaunchCountStore
import com.oliver.loqin.data.prefs.BarcodeScanCountStore
import com.oliver.loqin.data.prefs.BlockCategoryCountStore
import com.oliver.loqin.data.prefs.BlockCountStore
import com.oliver.loqin.data.prefs.EmergencyUnlockCountStore
import com.oliver.loqin.data.prefs.LimitHitCountStore
import com.oliver.loqin.data.prefs.NfcScanCountStore
import com.oliver.loqin.data.prefs.QrScanCountStore
import com.oliver.loqin.data.prefs.ScheduleExecutionCountStore
import com.oliver.loqin.data.prefs.LoqInActionCountStore
import com.oliver.loqin.data.prefs.TempEnableCountStore
import com.oliver.loqin.feature.stats.StatsFormat
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.showAccented
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.NumberFormat
import java.util.Calendar

/**
 * Loq In Stats: counters for scans, protection activity, actions and schedules.
 *
 * Rendered as square tiles (same card language as the widgets and app blocking
 * grids) grouped under flat section headers, with the insights-style range
 * selector at the top.
 */
class LoqInOverviewActivity : AppCompatActivity() {

    private enum class Range {
        TODAY,
        WEEK,
        MONTH,
        YEAR,
        OVERALL,
    }

    private data class Stat(
        val iconRes: Int,
        val labelRes: Int,
        val valueText: String,
        val onClick: (() -> Unit)? = null,
    )

    private lateinit var toolbar: MaterialToolbar
    private lateinit var rangeGroup: MaterialButtonToggleGroup
    private lateinit var scansGrid: LinearLayout
    private lateinit var activityGrid: LinearLayout
    private lateinit var actionsGrid: LinearLayout

    private val rangeButtons: MutableMap<Range, MaterialButton> = linkedMapOf()
    private var selectedRange: Range = Range.TODAY
    @Volatile private var archiveSyncRunning = false

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
            title = getString(R.string.loqin_overview_title)
            setNavigationIcon(R.drawable.keyboard_arrow_left_24)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            setBackgroundColor(AccentColor.getToolbarColor(this@LoqInOverviewActivity))
            navigationIcon?.mutate()?.setTint(toolbarIconColor())
            menu.add(R.string.loqin_overview_info_title).apply {
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

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        rangeGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }
        addRangeButton(Range.TODAY, R.string.stats_range_today)
        addRangeButton(Range.WEEK, R.string.stats_range_week)
        addRangeButton(Range.MONTH, R.string.stats_range_month)
        addRangeButton(Range.YEAR, R.string.stats_range_year)
        addRangeButton(Range.OVERALL, R.string.loqin_overview_range_overall)
        content.addView(rangeGroup)

        content.addView(sectionTitle(R.string.loqin_overview_section_activity))
        activityGrid = newStatGrid()
        content.addView(activityGrid)

        content.addView(sectionTitle(R.string.loqin_overview_section_scans).apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(20)
        })
        scansGrid = newStatGrid()
        content.addView(scansGrid)

        content.addView(sectionTitle(R.string.loqin_overview_section_actions).apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(20)
        })
        actionsGrid = newStatGrid()
        content.addView(actionsGrid)

        content.addView(TextView(this).apply {
            text = getString(R.string.loqin_overview_storage_note)
            textSize = 13f
            alpha = 0.72f
            setPadding(0, dp(16), 0, dp(12))
        })

        setContentView(root)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)

        val todayButton = rangeButtons.getValue(Range.TODAY)
        rangeGroup.check(todayButton.id)
        syncRangeButtonUi()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        syncStatsArchive()
    }

    private fun syncStatsArchive() {
        if (archiveSyncRunning || !UsageStatsRepo.hasUsageAccess(this)) {
            return
        }
        archiveSyncRunning = true
        val ctx = applicationContext
        Thread {
            val changed = runCatching { StatsArchiveSync.sync(ctx) }.getOrDefault(false)
            runOnUiThread {
                archiveSyncRunning = false
                if (changed && !isFinishing && !isDestroyed) {
                    refresh()
                }
            }
        }.start()
    }

    private fun addRangeButton(range: Range, labelRes: Int) {
        val button = MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            id = View.generateViewId()
            text = getString(labelRes)
            isCheckable = true
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(40)
            minimumHeight = dp(40)
            insetTop = 0
            insetBottom = 0
            setPadding(dp(3), 0, dp(3), 0)
            cornerRadius = dp(14)
            setAllCaps(false)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
            setOnClickListener {
                selectRange(range)
            }
        }
        rangeButtons[range] = button
        rangeGroup.addView(button)
    }

    private fun selectRange(requestedRange: Range) {
        selectedRange = requestedRange
        rangeGroup.check(rangeButtons.getValue(selectedRange).id)
        syncRangeButtonUi()
        refresh()
    }

    private fun syncRangeButtonUi() {
        // Match the insights range selector's segmented-bar style exactly.
        SegmentedToggleUi.apply(
            this,
            rangeButtons.values.toList(),
            rangeButtons.getValue(selectedRange).id,
        )
    }

    // ---------------------------------------------------------------------
    // Tiles
    // ---------------------------------------------------------------------

    private fun newStatGrid(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
    }

    private fun fillStatGrid(grid: LinearLayout, stats: List<Stat>) {
        grid.removeAllViews()
        val columns = 3
        val spacing = dp(4)
        val inflater = LayoutInflater.from(this)
        stats.chunked(columns).forEach { chunk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            chunk.forEach { stat ->
                val tile = inflater.inflate(R.layout.grid_stat_tile, row, false)
                tile.findViewById<ImageView>(R.id.ivStatIcon).setImageResource(stat.iconRes)
                tile.findViewById<TextView>(R.id.tvStatValue).text = stat.valueText
                tile.findViewById<TextView>(R.id.tvStatLabel).setText(stat.labelRes)
                if (stat.onClick != null) {
                    tile.isClickable = true
                    tile.isFocusable = true
                    tile.setOnClickListener { stat.onClick.invoke() }
                } else {
                    tile.isClickable = false
                    tile.isFocusable = false
                }
                tile.layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { setMargins(spacing, spacing, spacing, spacing) }
                row.addView(tile)
            }
            repeat(columns - chunk.size) {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
            }
            grid.addView(row)
        }
    }

    // ---------------------------------------------------------------------
    // Data
    // ---------------------------------------------------------------------

    private fun refresh() {
        val integer = NumberFormat.getIntegerInstance()

        fillStatGrid(
            scansGrid,
            listOf(
                Stat(
                    R.drawable.nfc_24,
                    R.string.loqin_overview_nfc_scans,
                    integer.format(
                        countForRange(
                            today = { NfcScanCountStore.getToday(this) },
                            week = { NfcScanCountStore.getForLastNDays(this, 7) },
                            month = { year, month -> NfcScanCountStore.getForMonth(this, year, month) },
                            year = { year -> NfcScanCountStore.getForYear(this, year) },
                            overall = { NfcScanCountStore.getOverall(this) }
                        )
                    )
                ),
                Stat(
                    R.drawable.qr_code_24,
                    R.string.loqin_overview_qr_scans,
                    integer.format(
                        countForRange(
                            today = { QrScanCountStore.getToday(this) },
                            week = { QrScanCountStore.getForLastNDays(this, 7) },
                            month = { year, month -> QrScanCountStore.getForMonth(this, year, month) },
                            year = { year -> QrScanCountStore.getForYear(this, year) },
                            overall = { QrScanCountStore.getOverall(this) }
                        )
                    )
                ),
                Stat(
                    R.drawable.barcode_24,
                    R.string.loqin_overview_barcode_scans,
                    integer.format(
                        countForRange(
                            today = { BarcodeScanCountStore.getToday(this) },
                            week = { BarcodeScanCountStore.getForLastNDays(this, 7) },
                            month = { year, month -> BarcodeScanCountStore.getForMonth(this, year, month) },
                            year = { year -> BarcodeScanCountStore.getForYear(this, year) },
                            overall = { BarcodeScanCountStore.getOverall(this) }
                        )
                    )
                ),
            )
        )

        fillStatGrid(
            activityGrid,
            listOf(
                Stat(
                    R.drawable.layers_24,
                    R.string.loqin_overview_in_app_blocks,
                    integer.format(blockCategoryCount(BlockCategoryCountStore.Category.IN_APP))
                ),
                Stat(
                    R.drawable.language_24,
                    R.string.loqin_overview_website_blocks,
                    integer.format(blockCategoryCount(BlockCategoryCountStore.Category.WEBSITE))
                ),
                Stat(
                    R.drawable.security_24,
                    R.string.loqin_overview_app_blocks,
                    integer.format(blockCategoryCount(BlockCategoryCountStore.Category.APP))
                ),
                Stat(
                    R.drawable.timer_24,
                    R.string.loqin_overview_active_time,
                    StatsFormat.prettyMsWithSeconds(activeTimeForRange()),
                    onClick = { startActivity(ActiveTimeActivity.intent(this)) }
                ),
                Stat(
                    R.drawable.apps_24,
                    R.string.loqin_overview_app_launches,
                    integer.format(visibleAppLaunchesForRange())
                ),
                Stat(
                    R.drawable.bar_chart_24,
                    R.string.loqin_overview_limits_reached,
                    integer.format(
                        countForRange(
                            today = { LimitHitCountStore.getToday(this) },
                            week = { LimitHitCountStore.getForLastNDays(this, 7) },
                            month = { year, month -> LimitHitCountStore.getForMonth(this, year, month) },
                            year = { year -> LimitHitCountStore.getForYear(this, year) },
                            overall = { LimitHitCountStore.getOverall(this) }
                        )
                    )
                ),
            )
        )

        fillStatGrid(
            actionsGrid,
            listOf(
                Stat(
                    R.drawable.toggle_on_24,
                    R.string.loqin_overview_enabled,
                    integer.format(actionCount(LoqInActionCountStore.Action.ENABLE))
                ),
                Stat(
                    R.drawable.toggle_off_24,
                    R.string.loqin_overview_disabled,
                    integer.format(actionCount(LoqInActionCountStore.Action.DISABLE))
                ),
                Stat(
                    R.drawable.play_arrow_24,
                    R.string.loqin_overview_temporary_enables,
                    integer.format(
                        countForRange(
                            today = { TempEnableCountStore.getToday(this) },
                            week = { TempEnableCountStore.getForLastNDays(this, 7) },
                            month = { year, month -> TempEnableCountStore.getForMonth(this, year, month) },
                            year = { year -> TempEnableCountStore.getForYear(this, year) },
                            overall = { TempEnableCountStore.getOverall(this) }
                        )
                    )
                ),
                Stat(
                    R.drawable.lock_open_24,
                    R.string.loqin_overview_emergency_unlocks,
                    integer.format(
                        countForRange(
                            today = { EmergencyUnlockCountStore.getToday(this) },
                            week = { EmergencyUnlockCountStore.getForLastNDays(this, 7) },
                            month = { year, month -> EmergencyUnlockCountStore.getForMonth(this, year, month) },
                            year = { year -> EmergencyUnlockCountStore.getForYear(this, year) },
                            overall = { EmergencyUnlockCountStore.getOverall(this) }
                        )
                    )
                ),
                Stat(
                    R.drawable.toggle_on_24,
                    R.string.loqin_overview_schedule_enables,
                    integer.format(actionCount(LoqInActionCountStore.Action.SCHEDULE_ENABLE))
                ),
                Stat(
                    R.drawable.toggle_off_24,
                    R.string.loqin_overview_schedule_disables,
                    integer.format(actionCount(LoqInActionCountStore.Action.SCHEDULE_DISABLE))
                ),
            )
        )
    }

    private fun blockCategoryCount(category: BlockCategoryCountStore.Category): Int {
        return countForRange(
            today = { BlockCategoryCountStore.getToday(this, category) },
            week = { BlockCategoryCountStore.getForLastNDays(this, category, 7) },
            month = { year, month -> BlockCategoryCountStore.getForMonth(this, category, year, month) },
            year = { year -> BlockCategoryCountStore.getForYear(this, category, year) },
            overall = { BlockCategoryCountStore.getOverall(this, category) }
        )
    }

    private fun visibleAppLaunchesForRange(): Int {
        val counts = if (selectedRange == Range.OVERALL) {
            AppLaunchCountStore.getMapOverall(this)
        } else {
            val rangeName = when (selectedRange) {
                Range.TODAY -> "today"
                Range.WEEK -> "week"
                Range.MONTH -> "month"
                Range.YEAR -> "year"
                Range.OVERALL -> error("Handled above")
            }
            val (from, to) = UsageTimelineRepo.windowForRange(rangeName)
            AppLaunchCountStore.getMapForDateRange(this, from, to)
        }
        return counts
            .filterKeys { packageName -> !UsageInsightsAppFilter.shouldHide(this, packageName) }
            .values
            .sumOf { it.toLong() }
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun activeTimeForRange(): Long {
        return when (selectedRange) {
            Range.TODAY -> ActiveDurationStore.todayMs(this)
            Range.WEEK -> ActiveDurationStore.lastNDaysMs(this, 7)
            Range.MONTH -> ActiveDurationStore.thisMonthMs(this)
            Range.YEAR -> ActiveDurationStore.thisYearMs(this)
            Range.OVERALL -> ActiveDurationStore.overallMs(this)
        }
    }

    private fun actionCount(action: LoqInActionCountStore.Action): Int {
        return countForRange(
            today = { LoqInActionCountStore.getToday(this, action) },
            week = { LoqInActionCountStore.getForLastNDays(this, action, 7) },
            month = { year, month ->
                LoqInActionCountStore.getForMonth(this, action, year, month)
            },
            year = { year -> LoqInActionCountStore.getForYear(this, action, year) },
            overall = { LoqInActionCountStore.getOverall(this, action) }
        )
    }

    private fun countForRange(
        today: () -> Int,
        week: () -> Int,
        month: (Int, Int) -> Int,
        year: (Int) -> Int,
        overall: () -> Int,
    ): Int {
        val calendar = Calendar.getInstance()
        return when (selectedRange) {
            Range.TODAY -> today()
            Range.WEEK -> week()
            Range.MONTH -> month(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1
            )
            Range.YEAR -> year(calendar.get(Calendar.YEAR))
            Range.OVERALL -> overall()
        }
    }

    private fun sectionTitle(textRes: Int): TextView {
        return TextView(this).apply {
            text = getString(textRes)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                MaterialColors.getColor(
                    this@LoqInOverviewActivity,
                    com.google.android.material.R.attr.colorOnSurface,
                    Color.GRAY
                )
            )
            alpha = 0.72f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun showInfo() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.loqin_overview_info_title)
            .setMessage(R.string.loqin_overview_info_body)
            .setPositiveButton(android.R.string.ok, null)
            .showAccented()
    }

    private fun toolbarIconColor(): Int {
        return if (MaterialColors.isColorLight(AccentColor.getToolbarColor(this))) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    private fun resolveColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun actionBarSize(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
        return android.util.TypedValue.complexToDimensionPixelSize(
            typedValue.data,
            resources.displayMetrics
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, LoqInOverviewActivity::class.java)
        }
    }
}
