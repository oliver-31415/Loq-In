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

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.ActiveDurationStore
import com.oliver.loqin.data.prefs.AttemptLimitStore
import com.oliver.loqin.data.prefs.InAppRuleStore
import com.oliver.loqin.data.prefs.PauseRuleStore
import com.oliver.loqin.data.prefs.ProfileRuleModeStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.SessionLimitStore
import com.oliver.loqin.data.prefs.UsageLimitStore
import com.oliver.loqin.data.statistics.StatsPersistence
import com.oliver.loqin.databinding.ActivityPatternsBinding
import com.oliver.loqin.feature.usage.PatternsMath.Span
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.widgets.ProtectionDay
import com.oliver.loqin.ui.widgets.patternsOnSurfaceColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Patterns: when and how the phone is used. Shows a weekday by hour heatmap, the first pickup of
 * each day and protected time per day. Scope toggles between distracting apps and all apps.
 */
class PatternsActivity : AppCompatActivity() {

    private enum class Scope { DISTRACTING, ALL }

    /** Per-scope figures, computed off the main thread so the toggle only re-renders. */
    private data class ScopeData(
        val grid: Array<DoubleArray>,
        val peak: PatternsMath.PeakBlock?,
        val pickups: List<Double?>,
        val pickupAverage: Double?,
        val blockCells: Set<Int>,
    )

    private data class Snapshot(
        val collected: Int,
        val allowMode: Boolean,
        val hasDistracting: Boolean,
        val distracting: ScopeData,
        val all: ScopeData,
        val protection: List<ProtectionDay?>,
    )

    /** Which apps count as distracting for the active profile. Empty without a profile. */
    private class DistractingRules(
        private val profileActive: Boolean,
        val allowMode: Boolean,
        private val selected: Set<String>,
        private val extras: Set<String>,
    ) {
        fun matches(packageName: String): Boolean = when {
            !profileActive -> false
            allowMode -> packageName !in selected || packageName in extras
            else -> packageName in selected || packageName in extras
        }
    }

    private lateinit var b: ActivityPatternsBinding
    private var scope: Scope = Scope.DISTRACTING
    private var snapshot: Snapshot? = null
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        b = ActivityPatternsBinding.inflate(layoutInflater)
        setContentView(b.root)

        EdgeToEdgeUtils.setupClassic(
            activity = this,
            toolbar = b.toolbar
        )

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        EdgeToEdgeUtils.applyThemedSystemBars(this)

        b.scopeToggle.check(R.id.btnScopeDistracting)
        syncScopeButtons()
        b.scopeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            scope = if (checkedId == R.id.btnScopeAll) Scope.ALL else Scope.DISTRACTING
            syncScopeButtons()
            render()
        }
    }

    // Same segmented style as the other insights range selectors.
    private fun syncScopeButtons() {
        SegmentedToggleUi.apply(
            this,
            listOf(b.btnScopeDistracting, b.btnScopeAll),
            if (scope == Scope.ALL) R.id.btnScopeAll else R.id.btnScopeDistracting,
        )
    }

    override fun onResume() {
        super.onResume()
        applyLegendSwatches()
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadSnapshot() }.getOrElse { emptySnapshot() }
            }
            snapshot = loaded
            // With no distracting apps there is nothing to show under that scope, so show all apps.
            if (!loaded.hasDistracting && scope == Scope.DISTRACTING) {
                scope = Scope.ALL
                b.scopeToggle.check(R.id.btnScopeAll)
                syncScopeButtons()
            }
            render()
        }
    }

    private fun render() {
        val snap = snapshot ?: return
        val enough = snap.collected >= PatternsMath.MIN_DAYS_COLLECTED
        b.cardEmpty.isVisible = !enough
        b.contentGroup.isVisible = enough
        if (!enough) {
            b.emptyProgress.text = getString(
                R.string.patterns_empty_progress,
                snap.collected,
                PatternsMath.MIN_DAYS_COLLECTED,
            )
            return
        }

        val data = if (scope == Scope.DISTRACTING) snap.distracting else snap.all
        val initials = weekdayInitials()
        val dayLabels = pickupDays().map { dayOfMonth(it) }

        b.heatmap.setData(data.grid, initials)
        b.heatmap.setBlockMarks(data.blockCells)
        b.heatmapMarksNote.isVisible = data.blockCells.isNotEmpty()
        b.peakText.text = data.peak?.let { peak ->
            val range = listOf(PatternsMath.hourLabel(peak.startHour), PatternsMath.hourLabel(peak.endHour))
            when (peak.group) {
                PatternsMath.PeakGroup.WEEKDAYS -> getString(R.string.patterns_peak_weekdays, range[0], range[1])
                PatternsMath.PeakGroup.WEEKENDS -> getString(R.string.patterns_peak_weekends, range[0], range[1])
                PatternsMath.PeakGroup.EVERY_DAY -> getString(R.string.patterns_peak_every_day, range[0], range[1])
            }
        } ?: getString(R.string.patterns_peak_none)

        b.pickupChart.setData(data.pickups, dayLabels, data.pickupAverage)
        b.pickupAverage.text = data.pickupAverage?.let {
            getString(R.string.patterns_pickup_average, PatternsMath.clockLabel(it))
        } ?: getString(R.string.patterns_pickup_none)

        b.protectionChart.setData(
            snap.protection,
            dayLabels,
            getString(R.string.patterns_protection_no_data),
        )

        b.allowModeNote.isVisible = scope == Scope.DISTRACTING && snap.allowMode
    }

    private fun applyLegendSwatches() {
        val size = (10 * resources.displayMetrics.density).toInt()
        val padding = (6 * resources.displayMetrics.density).toInt()
        val onSurface = patternsOnSurfaceColor(this)
        val accent = AccentColor.getAccentColorInt(this)
        fun swatch(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(size, size)
        }
        b.legendProtected.setCompoundDrawablesRelativeWithIntrinsicBounds(swatch(accent), null, null, null)
        b.legendProtected.compoundDrawablePadding = padding
        b.legendUnprotected.setCompoundDrawablesRelativeWithIntrinsicBounds(
            swatch(ColorUtils.setAlphaComponent(onSurface, 40)), null, null, null,
        )
        b.legendUnprotected.compoundDrawablePadding = padding
    }

    /** Runs on Dispatchers.IO. Backfill runs first so the load sees any imported history. */
    private fun loadSnapshot(): Snapshot {
        UsageHistoryBackfill.maybeRun(this)

        val zone = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val windowStart = PatternsMath.lastDayStarts(now, HEATMAP_DAYS, zone).first()
        val pickupDayStarts = PatternsMath.lastDayStarts(now, PICKUP_DAYS, zone)
        val weekStarts = PatternsMath.lastDayStarts(now, PICKUP_AVERAGE_DAYS, zone)

        val rules = distractingRules()
        val sessions = UsageTimelineRepo.allAppSessions(this, windowStart, now, limit = 0)
        val allSpans = sessions.map { Span(it.startMs, it.endMs) }
        val distractingSpans = sessions.filter { rules.matches(it.packageName) }
            .map { Span(it.startMs, it.endMs) }

        // Coverage starts at the first recorded session so days before data existed do not dilute the grid.
        val coverageStart = allSpans.minOfOrNull { it.startMs } ?: windowStart
        val blocks = StatsPersistence.blockEventsForRange(this, windowStart, now)

        val all = scopeData(
            spans = allSpans,
            blockStarts = blocks.map { it.startMs },
            windowStart = windowStart,
            now = now,
            coverageStart = coverageStart,
            zone = zone,
            pickupDayStarts = pickupDayStarts,
            weekStarts = weekStarts,
        )
        val distracting = scopeData(
            spans = distractingSpans,
            blockStarts = blocks.filter { rules.matches(it.subject) }.map { it.startMs },
            windowStart = windowStart,
            now = now,
            coverageStart = coverageStart,
            zone = zone,
            pickupDayStarts = pickupDayStarts,
            weekStarts = weekStarts,
        )

        val todayStart = PatternsMath.startOfDay(now, zone)
        val protection = pickupDayStarts.map { dayStart ->
            val segments = ActiveDurationStore.daySessions(this, dayStart)
            if (segments.isEmpty()) {
                null
            } else {
                val protectedMinutes = segments.sumOf { it.durationMs }.toDouble() / MINUTE_MS
                val totalMinutes = if (dayStart == todayStart) {
                    (now - dayStart).coerceAtLeast(0L) / MINUTE_MS.toDouble()
                } else {
                    FULL_DAY_MINUTES
                }
                ProtectionDay(protectedMinutes, totalMinutes)
            }
        }

        return Snapshot(
            collected = PatternsMath.daysCollected(allSpans, zone),
            allowMode = rules.allowMode,
            hasDistracting = sessions.any { rules.matches(it.packageName) },
            distracting = distracting,
            all = all,
            protection = protection,
        )
    }

    private fun scopeData(
        spans: List<Span>,
        blockStarts: List<Long>,
        windowStart: Long,
        now: Long,
        coverageStart: Long,
        zone: TimeZone,
        pickupDayStarts: List<Long>,
        weekStarts: List<Long>,
    ): ScopeData {
        val grid = PatternsMath.heatmapAverages(spans, windowStart, now, coverageStart, zone)
        val pickups = PatternsMath.firstPickupMinutesByDay(spans, zone)
        return ScopeData(
            grid = grid,
            peak = PatternsMath.peakBlock(grid),
            pickups = pickupDayStarts.map { pickups[it]?.toDouble() },
            pickupAverage = PatternsMath.averagePickupMinutes(pickups, weekStarts),
            blockCells = blockStarts.map { cellFor(it, zone) }.toSet(),
        )
    }

    private fun distractingRules(): DistractingRules {
        val profile = ProfileStore.getCurrent(this)?.takeIf { it.isNotBlank() }
            ?: return DistractingRules(false, false, emptySet(), emptySet())
        val extras = buildSet {
            addAll(UsageLimitStore.getAllLimitedPackages(this@PatternsActivity, profile))
            addAll(SessionLimitStore.getAllLimitedPackages(this@PatternsActivity, profile))
            addAll(AttemptLimitStore.getAllLimitedPackages(this@PatternsActivity, profile))
            addAll(PauseRuleStore.getPausedApps(this@PatternsActivity, profile))
            addAll(InAppRuleStore.getPackagesWithEnabledRules(this@PatternsActivity, profile))
        }
        return DistractingRules(
            profileActive = true,
            allowMode = ProfileRuleModeStore.isAllowMode(this, profile),
            selected = ProfileStore.getSelectedForProfileMode(this, profile),
            extras = extras,
        )
    }

    private fun emptySnapshot(): Snapshot {
        val empty = ScopeData(
            grid = Array(PatternsMath.WEEKDAY_COUNT) { DoubleArray(PatternsMath.HOUR_COUNT) },
            peak = null,
            pickups = List(PICKUP_DAYS) { null },
            pickupAverage = null,
            blockCells = emptySet(),
        )
        return Snapshot(
            collected = 0,
            allowMode = false,
            hasDistracting = false,
            distracting = empty,
            all = empty,
            protection = List(PICKUP_DAYS) { null },
        )
    }

    private fun pickupDays(): List<Long> =
        PatternsMath.lastDayStarts(System.currentTimeMillis(), PICKUP_DAYS, TimeZone.getDefault())

    private fun cellFor(timeMs: Long, zone: TimeZone): Int {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = timeMs }
        return PatternsMath.weekdayIndex(cal) * PatternsMath.HOUR_COUNT + cal.get(Calendar.HOUR_OF_DAY)
    }

    private fun dayOfMonth(dayStart: Long): String =
        Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = dayStart }
            .get(Calendar.DAY_OF_MONTH).toString()

    /** Single letter weekday names, Monday first, in the current locale. */
    private fun weekdayInitials(): List<String> {
        val formatter = SimpleDateFormat("EEEEE", Locale.getDefault())
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        return List(PatternsMath.WEEKDAY_COUNT) {
            formatter.format(cal.time).also { cal.add(Calendar.DAY_OF_YEAR, 1) }
        }
    }

    private companion object {
        const val HEATMAP_DAYS = 28
        const val PICKUP_DAYS = 14
        const val PICKUP_AVERAGE_DAYS = 7
        const val MINUTE_MS = 60_000L
        const val FULL_DAY_MINUTES = 24.0 * 60.0
    }
}
