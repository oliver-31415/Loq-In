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
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.oliver.loqin.R
import com.oliver.loqin.feature.stats.StatsFormat
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.PageUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.util.LocaleHelper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import java.text.DateFormat
import java.util.Date

class ScreenUnlockDetailActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private var startMs: Long = 0L
    private var endMs: Long = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        startMs = intent.getLongExtra(EXTRA_START, 0L)
        endMs = intent.getLongExtra(EXTRA_END, 0L)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = MaterialToolbar(this).apply {
            minimumHeight = actionBarSize()
            title = getString(R.string.screen_unlocks_detail_title)
            setNavigationIcon(R.drawable.keyboard_arrow_left_24)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        UsageInfoAction.attach(this, toolbar, R.string.screen_unlocks_info_title, R.string.screen_unlocks_info_body)
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
        if (!UsageStatsRepo.hasUsageAccess(this)) {
            content.addView(
                StatsPageUi.emptyState(this, getString(R.string.screen_unlocks_permission_needed), R.drawable.lock_open_24).apply {
                    setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                }
            )
            return
        }
        content.addView(StatsPageUi.emptyState(this, getString(R.string.usage_timeline_loading)))
        val ctx = applicationContext
        val from = startMs
        val to = endMs.coerceAtLeast(from)
        Thread {
            val sessions = UsageTimelineRepo.combinedUsageSessions(ctx, from, to, limit = 120)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                render(sessions)
            }
        }.start()
    }

    private fun render(sessions: List<UsageTimelineRepo.UsageTimelineSession>) {
        content.removeAllViews()
        val duration = (endMs - startMs).coerceAtLeast(0L)
        val appCount = sessions.map { it.source.name + ":" + it.id }.distinct().size
        content.addView(
            StatsPageUi.summaryCard(
                content,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(startMs)),
                StatsFormat.prettyMsWithSeconds(duration)
            ).apply {
                setComparison(resources.getQuantityString(R.plurals.screen_unlocks_apps_used, appCount, appCount))
            }.view
        )
        StatsPageUi.addSectionHeader(content, getString(R.string.usage_timeline_title))
        if (sessions.isEmpty()) {
            content.addView(StatsPageUi.emptyState(this, getString(R.string.screen_unlocks_no_apps)))
            return
        }
        // In order of use; each row says when it started and ended.
        val group = StatsPageUi.listGroup(content)
        sessions.forEach { session -> group.addView(timelineRow(group, session)) }
        content.addView(group)
    }

    private fun timelineRow(parent: ViewGroup, session: UsageTimelineRepo.UsageTimelineSession): View {
        val isWebsite = session.source == UsageTimelineRepo.TimelineSource.WEBSITE
        val label = if (isWebsite) session.label else appLabel(session.id)
        return StatsPageUi.row(
            parent = parent,
            title = label,
            // Title and value already name the app and the duration; the summary adds when.
            summary = "${timeText(session.startMs)}–${timeText(session.endMs)}",
            iconRes = if (isWebsite) R.drawable.language_24 else 0,
            appIcon = if (isWebsite) null else appIcon(session.id),
            value = StatsFormat.prettyMsWithSeconds(session.durationMs),
        )
    }

    private fun appIcon(packageName: String) =
        runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: ContextCompat.getDrawable(this, R.drawable.apps_24)

    private fun appLabel(packageName: String): String =
        runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)

    private fun timeText(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

    private fun actionBarSize(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
        return android.util.TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
    }

    companion object {
        private const val EXTRA_START = "start"
        private const val EXTRA_END = "end"

        fun intent(context: Context, startMs: Long, endMs: Long): Intent {
            return Intent(context, ScreenUnlockDetailActivity::class.java)
                .putExtra(EXTRA_START, startMs)
                .putExtra(EXTRA_END, endMs)
        }
    }
}
