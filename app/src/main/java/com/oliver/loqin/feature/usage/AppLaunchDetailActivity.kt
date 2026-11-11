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
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.AppLaunchCountStore
import com.oliver.loqin.data.prefs.UsageStore
import com.oliver.loqin.feature.stats.StatsFormat
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.PageUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.util.LocaleHelper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import java.text.DateFormat
import java.util.Date

class AppLaunchDetailActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var packageName: String
    private lateinit var label: String
    private lateinit var rangeName: String
    private var customStartMs: Long = 0L
    private var customEndMs: Long = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        label = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { packageName }
        rangeName = intent.getStringExtra(EXTRA_RANGE).orEmpty().ifBlank { "week" }
        customStartMs = intent.getLongExtra(EXTRA_START_MS, 0L)
        customEndMs = intent.getLongExtra(EXTRA_END_MS, 0L)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = MaterialToolbar(this).apply {
            minimumHeight = actionBarSize()
            title = label
            setNavigationIcon(R.drawable.keyboard_arrow_left_24)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        UsageInfoAction.attach(this, toolbar, R.string.app_launches_info_title, R.string.app_launches_info_body)
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
        setContentView(root)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        load()
    }

    private fun load() {
        content.removeAllViews()
        content.addView(StatsPageUi.emptyState(this, getString(R.string.usage_timeline_loading)))
        val ctx = applicationContext
        val pkg = packageName
        val range = rangeName
        val hasUsageAccess = UsageStatsRepo.hasUsageAccess(this)
        Thread {
            val (from, to) = if (range == "custom" && customEndMs > customStartMs) {
                customStartMs to customEndMs
            } else {
                UsageTimelineRepo.windowForRange(range)
            }
            if (hasUsageAccess) {
                runCatching { StatsArchiveSync.sync(ctx) }
            }
            val sessions = UsageTimelineRepo.appSessions(ctx, pkg, from, to, limit = 0)
            val storedLaunches = AppLaunchCountStore.getForDateRange(ctx, pkg, from, to)
            val localUsage = UsageStore.getUsageMsSeriesForDateRange(ctx, pkg, from, to).sum()
            val usageTotal = if (hasUsageAccess) {
                UsageStatsRepo.getTotalMsForWindow(ctx, from, to, pkg).takeIf { it > 0L } ?: localUsage
            } else {
                localUsage
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                render(sessions, storedLaunches, usageTotal, hasUsageAccess)
            }
        }.start()
    }

    private fun render(
        sessions: List<UsageTimelineRepo.AppSession>,
        launchCount: Int,
        totalMs: Long,
        hasUsageAccess: Boolean,
    ) {
        content.removeAllViews()
        val effectiveLaunches = launchCount.takeIf { it > 0 } ?: sessions.size
        val effectiveTotal = totalMs.takeIf { it > 0L } ?: sessions.sumOf { it.durationMs }
        if (effectiveLaunches <= 0 && effectiveTotal <= 0L) {
            val message = if (hasUsageAccess) R.string.usage_timeline_empty else R.string.usage_timeline_permission_needed
            content.addView(StatsPageUi.emptyState(this, getString(message), R.drawable.apps_24).apply {
                if (!hasUsageAccess) setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            })
            return
        }
        content.addView(headerCard(effectiveLaunches, effectiveTotal))
        if (sessions.isEmpty()) {
            content.addView(
                StatsPageUi.emptyState(this, getString(R.string.usage_timeline_recent_details_unavailable)).apply {
                    layoutParams = StatsPageUi.blockParams(this@AppLaunchDetailActivity)
                }
            )
            return
        }

        // One header per day (newest first); each day's sessions form one grouped list.
        val grouped = sessions.asReversed().groupBy { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.startMs)) }
        grouped.forEach { (date, daySessions) ->
            StatsPageUi.addSectionHeader(content, date)
            val group = StatsPageUi.listGroup(content)
            daySessions.forEach { session -> group.addView(timelineRow(group, session)) }
            content.addView(group)
        }
    }

    private fun headerCard(launches: Int, totalMs: Long): View {
        val card = StatsPageUi.summaryCard(
            content,
            getString(R.string.app_launches_total_title),
            resources.getQuantityString(R.plurals.app_launches_count, launches, launches)
        )
        card.setComparison(
            getString(
                R.string.app_launch_detail_summary,
                resources.getQuantityString(R.plurals.app_launches_count, launches, launches),
                StatsFormat.prettyMsWithSeconds(totalMs)
            )
        )
        val iconSize = resources.getDimensionPixelSize(R.dimen.stats_app_icon_size)
        card.trailing.addView(ImageView(this).apply {
            setImageDrawable(appIcon(packageName))
            contentDescription = null
        }, iconSize, iconSize)
        card.trailing.visibility = View.VISIBLE
        return card.view
    }

    private fun timelineRow(parent: ViewGroup, session: UsageTimelineRepo.AppSession): View =
        StatsPageUi.row(
            parent = parent,
            title = "${timeText(session.startMs)}–${timeText(session.endMs)}",
            value = StatsFormat.prettyMsWithSeconds(session.durationMs),
        )

    private fun appIcon(packageName: String) =
        runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: ContextCompat.getDrawable(this, R.drawable.apps_24)

    private fun timeText(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

    private fun actionBarSize(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
        return android.util.TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_RANGE = "range"
        private const val EXTRA_START_MS = "start_ms"
        private const val EXTRA_END_MS = "end_ms"

        fun intent(
            context: Context,
            packageName: String,
            label: String,
            rangeName: String = "week",
            startMs: Long = 0L,
            endMs: Long = 0L
        ): Intent {
            return Intent(context, AppLaunchDetailActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_RANGE, rangeName)
                .putExtra(EXTRA_START_MS, startMs)
                .putExtra(EXTRA_END_MS, endMs)
        }
    }
}
