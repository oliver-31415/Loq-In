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
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.InsightsGoalStore
import com.oliver.loqin.data.prefs.PauseRuleStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.UsageStore
import com.oliver.loqin.feature.stats.StatsFormat
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.ui.widgets.BudgetRingView
import com.oliver.loqin.ui.widgets.SplitBarRowView
import com.oliver.loqin.ui.widgets.StackedDailyBarChartView
import java.text.SimpleDateFormat
import java.util.Calendar

/**
 * The Insights cards at the top of the Loq In overview.
 *
 * [heroView] shows 7 or 30 days of screen time as stacked bars, a daily average headline and the
 * daily goal ring. [pauseView] shows the last 7 days of pause outcomes and is GONE unless the
 * active profile has paused apps.
 *
 * Data is read on a background thread. Results are applied on the UI thread and dropped when a
 * newer [refresh] has started or the activity is finishing.
 */
class LoqInInsightsCards(private val activity: AppCompatActivity) {

    private val ctx: Context = activity

    private var rangeDays = 7
    private var loadToken = 0

    private val rangeButtons = linkedMapOf<Int, MaterialButton>()
    private lateinit var rangeGroup: MaterialButtonToggleGroup
    private lateinit var topRow: View
    private lateinit var headline: TextView
    private lateinit var subline: TextView
    private lateinit var emptyText: TextView
    private lateinit var chartGroup: LinearLayout
    private lateinit var chart: StackedDailyBarChartView
    private lateinit var legend: ChipGroup
    private lateinit var goalSlot: FrameLayout
    private lateinit var ringColumn: LinearLayout
    private lateinit var ring: BudgetRingView
    private lateinit var ringLabel: TextView
    private lateinit var setGoalButton: MaterialButton

    private lateinit var pauseSummary: TextView
    private lateinit var pauseRow: SplitBarRowView

    /** Hero card: screen time for the selected range with the goal ring. */
    val heroView: View

    /** Pause results card; GONE until the active profile has paused apps. */
    val pauseView: View

    init {
        heroView = buildHero()
        pauseView = buildPause().apply { visibility = View.GONE }
    }

    /** Re-reads usage, goal and pause data off the main thread and redraws both cards. */
    fun refresh() {
        val token = ++loadToken
        val days = rangeDays
        Thread {
            val hero = runCatching { loadHero(days) }.getOrNull()
            val pause = runCatching { loadPause() }.getOrNull()
            activity.runOnUiThread {
                if (token != loadToken || activity.isFinishing || activity.isDestroyed) {
                    return@runOnUiThread
                }
                hero?.let { applyHero(it) }
                pause?.let { applyPause(it) }
            }
        }.start()
    }

    // ---------------------------------------------------------------------
    // Hero card
    // ---------------------------------------------------------------------

    private fun buildHero(): View {
        val card = card()
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        card.addView(col)

        rangeGroup = MaterialButtonToggleGroup(ctx).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = matchWrap().apply { bottomMargin = dp(12) }
        }
        addRangeButton(7, R.string.charts_range_7_days)
        addRangeButton(30, R.string.charts_range_30_days)
        rangeGroup.check(rangeButtons.getValue(rangeDays).id)
        syncRangeUi()
        col.addView(rangeGroup)

        headline = TextView(ctx).apply {
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(onSurface())
        }
        subline = TextView(ctx).apply {
            textSize = 13f
            alpha = 0.72f
        }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(headline)
            addView(subline)
        }

        ring = BudgetRingView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
        }
        ringLabel = TextView(ctx).apply {
            textSize = 12f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(onSurface())
        }
        ringColumn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            setOnClickListener { showGoalDialog() }
            addView(ring)
            addView(ringLabel)
        }
        setGoalButton = MaterialButton(ctx).apply {
            text = ctx.getString(R.string.charts_goal_set)
            setAllCaps(false)
            backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            setTextColor(AccentColor.getAccentColorInt(ctx))
            setOnClickListener { showGoalDialog() }
        }
        goalSlot = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(12) }
            addView(ringColumn)
            addView(setGoalButton)
        }

        topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(texts)
            addView(goalSlot)
        }
        col.addView(topRow)

        emptyText = TextView(ctx).apply {
            text = ctx.getString(R.string.charts_empty_insights)
            textSize = 14f
            alpha = 0.72f
            setPadding(0, dp(24), 0, dp(8))
            visibility = View.GONE
        }
        col.addView(emptyText)

        chart = StackedDailyBarChartView(ctx).apply {
            layoutParams = matchWrap().apply { height = dp(168) }
        }
        // Wraps onto a second line instead of running off the card.
        legend = ChipGroup(ctx).apply {
            chipSpacingHorizontal = dp(12)
            chipSpacingVertical = dp(6)
            layoutParams = matchWrap().apply { topMargin = dp(8) }
        }
        chartGroup = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchWrap().apply { topMargin = dp(16) }
            addView(chart)
            addView(legend)
        }
        col.addView(chartGroup)
        return card
    }

    private fun addRangeButton(days: Int, labelRes: Int) {
        val button = MaterialButton(
            ctx,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            id = View.generateViewId()
            text = ctx.getString(labelRes)
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
            setOnClickListener { selectRange(days) }
        }
        rangeButtons[days] = button
        rangeGroup.addView(button)
    }

    private fun selectRange(days: Int) {
        rangeDays = days
        rangeGroup.check(rangeButtons.getValue(days).id)
        syncRangeUi()
        refresh()
    }

    private fun syncRangeUi() {
        SegmentedToggleUi.apply(ctx, rangeButtons.values.toList(), rangeButtons.getValue(rangeDays).id)
    }

    private data class HeroData(
        val days: Int,
        val hasData: Boolean,
        val result: DailyStackedUsage.Result,
        val labels: List<String>,
        val currentMs: Long,
        val currentDaysWithData: Int,
        val previousDaysWithData: Int,
        val previousMs: Long,
        val todayMs: Long,
        val goalMin: Int,
        val appLabels: Map<String, String>,
    )

    private fun loadHero(days: Int): HeroData {
        val hidden = HashMap<String, Boolean>()
        val isHidden: (String) -> Boolean = { pkg ->
            hidden.getOrPut(pkg) { UsageInsightsAppFilter.shouldHide(ctx, pkg) }
        }
        // Oldest first: the first [days] entries are the previous period, the rest the current one.
        val ymds = InsightsWindow.ymdsEndingOn(Calendar.getInstance(), days * 2)
        val maps = ymds.map { UsageStore.getUsageMsMapForDay(ctx, it) }
        val totals = maps.map { map ->
            map.entries.sumOf { (pkg, ms) -> if (ms > 0L && !isHidden(pkg)) ms else 0L }
        }
        val previous = totals.subList(0, days)
        val current = totals.subList(days, days * 2)
        val result = DailyStackedUsage.build(
            days = maps.subList(days, days * 2),
            previous = previous,
            topN = 4,
            hide = isHidden,
        )
        val labels = (0 until days).map { i ->
            if (InsightsWindow.shouldLabel(i, days)) dayLabel(ymds[days + i], days) else ""
        }
        val appLabels = result.legendKeys
            .filter { it != DailyStackedUsage.OTHER_KEY }
            .associateWith { appLabel(it) }
        return HeroData(
            days = days,
            hasData = UsageStore.getDaysWithUsageCount(ctx) >= 1,
            result = result,
            labels = labels,
            currentMs = current.sum(),
            currentDaysWithData = current.count { it > 0L },
            previousDaysWithData = previous.count { it > 0L },
            previousMs = previous.sum(),
            todayMs = current.last(),
            goalMin = InsightsGoalStore.getDailyGoalMin(ctx),
            appLabels = appLabels,
        )
    }

    private fun applyHero(data: HeroData) {
        val hasData = data.hasData
        emptyText.visibility = if (hasData) View.GONE else View.VISIBLE
        topRow.visibility = if (hasData) View.VISIBLE else View.GONE
        chartGroup.visibility = if (hasData) View.VISIBLE else View.GONE
        if (!hasData) return

        headline.text = ctx.getString(
            R.string.charts_headline_per_day,
            StatsFormat.prettyMs(averagePerDay(data.currentMs, data.currentDaysWithData))
        )
        val subText = changeText(UsageComparison.summaryChange(
                averagePerDay(data.currentMs, data.currentDaysWithData),
                averagePerDay(data.previousMs, data.previousDaysWithData),
            ), data.days)
        subline.text = subText
        subline.visibility = if (subText == null) View.GONE else View.VISIBLE

        chart.setData(data.result, data.labels, data.goalMin * MS_PER_MIN)
        chart.contentDescription = ctx.getString(R.string.charts_chart_description, data.days)
        renderLegend(data)
        renderGoal(data.goalMin, data.todayMs)
    }

    private fun changeText(change: UsageComparison.Change?, days: Int): String? {
        return when (change) {
            null -> null
            UsageComparison.Change.Same -> ctx.getString(R.string.charts_change_same, days)
            is UsageComparison.Change.Percent -> if (change.value < 0) {
                ctx.getString(R.string.charts_change_down_percent, -change.value, days)
            } else {
                ctx.getString(R.string.charts_change_up_percent, change.value, days)
            }
            is UsageComparison.Change.Minutes -> if (change.value < 0) {
                ctx.getString(
                    R.string.charts_change_down_minutes,
                    StatsFormat.prettyMs(-change.value * MS_PER_MIN),
                    days
                )
            } else {
                ctx.getString(
                    R.string.charts_change_up_minutes,
                    StatsFormat.prettyMs(change.value * MS_PER_MIN),
                    days
                )
            }
        }
    }

    private fun renderLegend(data: HeroData) {
        legend.removeAllViews()
        data.result.legendKeys.forEach { key ->
            val label = if (key == DailyStackedUsage.OTHER_KEY) {
                ctx.getString(R.string.charts_legend_other)
            } else {
                data.appLabels[key] ?: key
            }
            legend.addView(legendItem(chart.colorForKey(key), label))
        }
    }

    private fun renderGoal(goalMin: Int, todayMs: Long) {
        if (goalMin <= 0) {
            ringColumn.visibility = View.GONE
            setGoalButton.visibility = View.VISIBLE
            return
        }
        val goalMs = goalMin * MS_PER_MIN
        setGoalButton.visibility = View.GONE
        ringColumn.visibility = View.VISIBLE
        ring.setProgress(todayMs, goalMs)
        ringLabel.text = if (todayMs > goalMs) {
            ctx.getString(R.string.charts_goal_over, StatsFormat.prettyMs(todayMs - goalMs))
        } else {
            ctx.getString(R.string.charts_goal_left, StatsFormat.prettyMs(goalMs - todayMs))
        }
        ring.contentDescription = ctx.getString(
            R.string.charts_goal_ring_description,
            StatsFormat.prettyMs(todayMs),
            StatsFormat.prettyMs(goalMs)
        )
    }

    private fun showGoalDialog() {
        val current = InsightsGoalStore.getDailyGoalMin(ctx)
        val presets = listOf(60, 120, 180, 240)

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        content.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.charts_goal_dialog_message)
            textSize = 14f
            alpha = 0.72f
        })
        val chips = ChipGroup(ctx).apply {
            isSingleSelection = true
            layoutParams = matchWrap().apply { topMargin = dp(12) }
        }
        presets.forEach { minutes ->
            chips.addView(Chip(ctx).apply {
                id = View.generateViewId()
                text = ctx.getString(R.string.charts_goal_preset, minutes / 60)
                isCheckable = true
                isChecked = minutes == current
                tag = minutes
            })
        }
        content.addView(chips)

        val input = TextInputEditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            if (current > 0 && current !in presets) setText(current.toString())
        }
        val inputLayout = TextInputLayout(ctx).apply {
            hint = ctx.getString(R.string.charts_goal_custom_hint)
            layoutParams = matchWrap().apply { topMargin = dp(8) }
            addView(input)
        }
        content.addView(inputLayout)

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.charts_goal_dialog_title)
            .setView(content)
            .setPositiveButton(R.string.charts_goal_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .apply {
                if (current > 0) {
                    setNeutralButton(R.string.charts_goal_remove) { _, _ ->
                        InsightsGoalStore.clearDailyGoal(ctx)
                        refresh()
                    }
                }
            }
            .showAccented()

        // Overridden after show so that invalid input keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val typed = input.text?.toString()?.trim().orEmpty()
            val minutes: Int? = if (typed.isNotEmpty()) {
                typed.toIntOrNull()
            } else {
                chips.checkedChipId
                    .takeIf { it != View.NO_ID }
                    ?.let { chips.findViewById<Chip>(it)?.tag as? Int }
            }
            if (minutes == null) {
                if (typed.isEmpty()) {
                    dialog.dismiss()
                } else {
                    inputLayout.error = ctx.getString(R.string.charts_goal_invalid)
                }
                return@setOnClickListener
            }
            if (minutes !in 1..MAX_GOAL_MIN) {
                inputLayout.error = ctx.getString(R.string.charts_goal_invalid)
                return@setOnClickListener
            }
            InsightsGoalStore.setDailyGoalMin(ctx, minutes)
            dialog.dismiss()
            refresh()
        }
    }

    // ---------------------------------------------------------------------
    // Pause results card
    // ---------------------------------------------------------------------

    private fun buildPause(): View {
        val card = card()
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        card.addView(col)

        col.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.charts_pause_title)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(onSurface())
        })
        pauseSummary = TextView(ctx).apply {
            textSize = 13f
            alpha = 0.72f
            setPadding(0, dp(4), 0, dp(12))
        }
        col.addView(pauseSummary)

        pauseRow = SplitBarRowView(ctx).apply {
            layoutParams = matchWrap().apply { height = dp(96) }
        }
        col.addView(pauseRow)

        val legendRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = matchWrap().apply { topMargin = dp(8) }
        }
        legendRow.addView(legendItem(AccentColor.getAccentColorInt(ctx), ctx.getString(R.string.charts_pause_legend_left)))
        legendRow.addView(legendItem(continuedColor(), ctx.getString(R.string.charts_pause_legend_continued)))
        col.addView(legendRow)
        return card
    }

    private data class PauseData(
        val visible: Boolean,
        val days: List<SplitBarRowView.Day>,
        val labels: List<String>,
        val summary: PauseWeekSummary.Result,
        val thisLeft: Int,
        val thisContinued: Int,
    )

    private fun loadPause(): PauseData {
        val profile = ProfileStore.getCurrent(ctx) ?: "default"
        val visible = PauseRuleStore.getPausedApps(ctx, profile).isNotEmpty()
        val ymds = InsightsWindow.ymdsEndingOn(Calendar.getInstance(), 14)
        val previousWeek = ymds.subList(0, 7)
        val thisWeek = ymds.subList(7, 14)

        fun left(ymd: Int) = PauseRuleStore.outcomesForDay(ctx, PauseRuleStore.Outcome.LEFT, ymd)
        fun continued(ymd: Int) = PauseRuleStore.outcomesForDay(ctx, PauseRuleStore.Outcome.CONTINUED, ymd)

        val thisLeft = thisWeek.sumOf { left(it) }
        val thisContinued = thisWeek.sumOf { continued(it) }
        val previousLeft = previousWeek.sumOf { left(it) }
        val previousContinued = previousWeek.sumOf { continued(it) }
        return PauseData(
            visible = visible,
            days = thisWeek.map { SplitBarRowView.Day(left(it), continued(it)) },
            labels = thisWeek.map { dayLabel(it, 7) },
            summary = PauseWeekSummary.build(thisLeft, thisContinued, previousLeft, previousContinued),
            thisLeft = thisLeft,
            thisContinued = thisContinued,
        )
    }

    private fun applyPause(data: PauseData) {
        pauseView.visibility = if (data.visible) View.VISIBLE else View.GONE
        if (!data.visible) return

        val percent = data.summary.percent
        val previous = data.summary.previousPercent ?: 0
        pauseSummary.text = if (percent == null) {
            ctx.getString(R.string.charts_pause_none)
        } else {
            when (data.summary.trend) {
                PauseWeekSummary.Trend.UP -> ctx.getString(R.string.charts_pause_summary_up, percent, previous)
                PauseWeekSummary.Trend.DOWN -> ctx.getString(R.string.charts_pause_summary_down, percent, previous)
                null -> ctx.getString(R.string.charts_pause_summary, percent)
            }
        }
        pauseRow.setData(data.days, data.labels)
        pauseRow.contentDescription = ctx.getString(
            R.string.charts_pause_chart_description,
            data.thisLeft,
            data.thisContinued
        )
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun dayLabel(ymd: Int, days: Int): String {
        if (days > 7) {
            return ((ymd % 100)).toString()
        }
        val cal = Calendar.getInstance().apply {
            clear()
            set(ymd / 10000, (ymd / 100) % 100 - 1, ymd % 100)
        }
        return SimpleDateFormat("EEE", ctx.resources.configuration.locales.get(0)).format(cal.time)
    }

    private fun appLabel(pkg: String): String {
        val pm = ctx.packageManager
        return runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }
            .getOrDefault(pkg)
    }

    // Days without any recorded use (before install, or with usage access off) would drag the
    // average down, so average over the days that have data.
    private fun averagePerDay(totalMs: Long, daysWithData: Int): Long =
        if (daysWithData <= 0) 0L else totalMs / daysWithData

    private fun legendItem(color: Int, labelText: String): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(12) }
            addView(View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(color)
                }
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(6) }
            })
            addView(TextView(ctx).apply {
                text = labelText
                textSize = 12f
                setTextColor(onSurface())
            })
        }
    }

    private fun continuedColor(): Int = ColorUtils.setAlphaComponent(onSurface(), CONTINUED_ALPHA)

    private fun card(): MaterialCardView = MaterialCardView(ctx).apply {
        cardElevation = 0f
        radius = dp(16).toFloat()
        strokeWidth = 0
        setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.foqos_surface_variant))
    }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun onSurface(): Int = MaterialColors.getColor(
        ctx,
        com.google.android.material.R.attr.colorOnSurface,
        android.graphics.Color.GRAY
    )

    private fun dp(value: Int): Int = (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val MS_PER_MIN = 60_000L
        const val MAX_GOAL_MIN = 1440
        const val CONTINUED_ALPHA = 110
    }
}
