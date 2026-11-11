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
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.ActiveDurationStore
import com.oliver.loqin.feature.stats.StatsFormat
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.PageUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.util.LocaleHelper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import java.text.DateFormat

class ActiveTimeDetailActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)

        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val timeMillis = intent.getLongExtra(EXTRA_TIME_MILLIS, 0L)
        val isMonth = intent.getBooleanExtra(EXTRA_IS_MONTH, false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val toolbar = MaterialToolbar(this).apply {
            minimumHeight = actionBarSize()
            title = label.ifBlank { getString(R.string.active_time_title) }
            setNavigationIcon(R.drawable.keyboard_arrow_left_24)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        UsageInfoAction.attach(this, toolbar, R.string.active_time_info_title, R.string.active_time_info_body)

        root.addView(AppBarLayout(this).apply {
            fitsSystemWindows = true
            addView(
                toolbar,
                AppBarLayout.LayoutParams(
                    AppBarLayout.LayoutParams.MATCH_PARENT,
                    AppBarLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        PageUi.applyPageContentPadding(content)
        scroll.addView(
            content,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        if (isMonth) {
            renderMonthDetails(content, timeMillis)
        } else {
            renderDayDetails(content, timeMillis)
        }

        setContentView(root)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
    }

    private fun renderDayDetails(content: LinearLayout, timeMillis: Long) {
        val sessions = ActiveDurationStore.daySessions(this, timeMillis)
        val total = sessions.sumOf { it.durationMs }
        val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT)

        content.addView(totalCard(content, StatsFormat.prettyMs(total)))

        StatsPageUi.addSectionHeader(content, getString(R.string.active_time_sessions_title))
        if (sessions.isEmpty()) {
            content.addView(StatsPageUi.emptyState(this, getString(R.string.active_time_no_sessions)))
        } else {
            val group = StatsPageUi.listGroup(content)
            sessions.forEachIndexed { index, session ->
                group.addView(StatsPageUi.row(
                    parent = group,
                    title = getString(R.string.active_time_session_title, index + 1),
                    summary = "${timeFmt.format(java.util.Date(session.startMs))} – ${timeFmt.format(java.util.Date(session.endMs))}",
                    value = StatsFormat.prettyMs(session.durationMs),
                    iconRes = R.drawable.schedule_24
                ))
            }
            content.addView(group)
        }

        StatsPageUi.addSectionHeader(content, getString(R.string.active_time_activity_history_title))
        val historyEntries = ActivityHistoryRepository.entriesForDay(this, timeMillis)
        if (historyEntries.isEmpty()) {
            content.addView(StatsPageUi.emptyState(this, getString(R.string.active_time_activity_history_empty)))
        } else {
            val group = StatsPageUi.listGroup(content)
            historyEntries.forEach { entry ->
                group.addView(StatsPageUi.row(
                    parent = group,
                    title = entry.title,
                    summary = entry.summary,
                    value = timeFmt.format(java.util.Date(entry.timeMillis)),
                    iconRes = entry.iconRes
                ))
            }
            content.addView(group)
        }
    }

    private fun renderMonthDetails(content: LinearLayout, monthStartMs: Long) {
        val days = ActiveDurationStore.dailyBucketsForMonth(this, monthStartMs)
            .filter { it.valueMs > 0L }
        val total = days.sumOf { it.valueMs }

        content.addView(totalCard(content, StatsFormat.prettyMs(total)))

        if (days.isEmpty()) {
            content.addView(
                StatsPageUi.emptyState(this, getString(R.string.active_time_no_sessions)).apply {
                    layoutParams = StatsPageUi.blockParams(this@ActiveTimeDetailActivity)
                }
            )
            return
        }

        // One list: each day's bar is its row, and the row opens that day (the graph used to
        // repeat every day as a second list below it).
        StatsPageUi.addSectionHeader(content, getString(R.string.active_time_graph_days))
        val max = days.maxOf { it.valueMs }.coerceAtLeast(1L)
        val group = StatsPageUi.listGroup(content)
        days.forEach { day ->
            group.addView(StatsPageUi.barRow(
                parent = group,
                title = day.label,
                value = StatsFormat.prettyMs(day.valueMs),
                fraction = day.valueMs.toFloat() / max.toFloat(),
            ) {
                startActivity(intent(this, day.label, day.timeMillis, isMonth = false))
            })
        }
        content.addView(group)
    }

    private fun totalCard(parent: LinearLayout, value: String): View =
        StatsPageUi.summaryCard(parent, getString(R.string.active_time_details_total_label), value).view

    private fun actionBarSize(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
        return android.util.TypedValue.complexToDimensionPixelSize(
            typedValue.data,
            resources.displayMetrics
        )
    }

    companion object {
        private const val EXTRA_LABEL = "extra_label"
        private const val EXTRA_TIME_MILLIS = "extra_time_millis"
        private const val EXTRA_IS_MONTH = "extra_is_month"

        fun intent(context: Context, label: String, timeMillis: Long, isMonth: Boolean): Intent =
            Intent(context, ActiveTimeDetailActivity::class.java)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_TIME_MILLIS, timeMillis)
                .putExtra(EXTRA_IS_MONTH, isMonth)
    }
}
