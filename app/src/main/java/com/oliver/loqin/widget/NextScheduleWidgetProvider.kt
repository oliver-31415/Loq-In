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

package com.oliver.loqin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.AutomationModeStore
import com.oliver.loqin.data.prefs.SchedulePlanner
import com.oliver.loqin.data.prefs.ScheduleStore
import com.oliver.loqin.feature.entry.QuickActionIconFactory
import com.oliver.loqin.feature.schedule.SchedulesActivity
import com.oliver.loqin.feature.theme.AccentColor
import com.oliver.loqin.util.TimeFormatPrefs
import java.util.Calendar

// Updates the home-screen widget with the next enabled schedule.
class NextScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Recompute the countdown whenever the launcher resizes the widget.
        appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            SchedulePlanner.ACTION_NEXT_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> refreshAll(context)
        }
    }

    companion object {
        // Schedules starting within this window are highlighted with the accent color.
        private const val URGENT_WINDOW_MILLIS = 30L * 60L * 1000L

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, NextScheduleWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
            }
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val content = buildContent(context)
            val accent = AccentColor.getAccentColorInt(context)
            val onSurface = ContextCompat.getColor(context, R.color.foqos_on_surface)
            val onSurfaceVariant = ContextCompat.getColor(context, R.color.foqos_on_surface_variant)
            val timeColor = if (content.urgent) accent else onSurface
            val countdownColor = if (content.urgent) accent else onSurfaceVariant
            return RemoteViews(context.packageName, R.layout.widget_next_schedule).apply {
                setImageViewBitmap(R.id.widgetNextScheduleIcon, QuickActionIconFactory.createWidgetBitmap(context, R.drawable.schedule_24))
                setTextViewText(R.id.widgetNextScheduleName, content.name)
                setTextViewText(R.id.widgetNextScheduleTime, content.time)
                setTextViewText(R.id.widgetNextScheduleCountdown, content.countdown)
                setTextViewText(R.id.widgetNextScheduleStatus, content.status)
                setTextColor(R.id.widgetNextScheduleTime, timeColor)
                setTextColor(R.id.widgetNextScheduleCountdown, countdownColor)
                setViewVisibility(
                    R.id.widgetNextScheduleTime,
                    if (content.showTime) android.view.View.VISIBLE else android.view.View.GONE
                )
                setViewVisibility(
                    R.id.widgetNextScheduleCountdown,
                    if (content.countdown != null) android.view.View.VISIBLE else android.view.View.GONE
                )
                setViewVisibility(
                    R.id.widgetNextScheduleEmpty,
                    if (content.showEmptyState) android.view.View.VISIBLE else android.view.View.GONE
                )
                setTextViewText(
                    R.id.widgetNextScheduleBadge,
                    if (findNextBoundary(context) != null) context.getString(R.string.blocking_mode_schedule)
                    else context.getString(R.string.widget_active_timer_inactive)
                )
                setOnClickPendingIntent(
                    R.id.widgetNextScheduleRoot,
                    PendingIntent.getActivity(
                        context,
                        2201 + appWidgetId,
                        Intent(context, SchedulesActivity::class.java)
                            .setData("loqin://widget/schedule/$appWidgetId".toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }

        private fun buildContent(context: Context): DisplayContent {
            if (!AutomationModeStore.isScheduleAllowed(context)) {
                return DisplayContent(
                    name = context.getString(R.string.widget_next_schedule_name),
                    time = context.getString(R.string.schedules_next_inactive_control_mode),
                    status = context.getString(R.string.widget_next_schedule_open)
                )
            }

            val next = findNextBoundary(context)
            if (next == null) {
                return DisplayContent(
                    name = context.getString(R.string.widget_next_schedule_name),
                    time = "",
                    showTime = false,
                    showEmptyState = true,
                    status = context.getString(R.string.widget_glance_empty_schedule_hint)
                )
            }

            val countdown = formatCountdown(context, next.timeMillis)
            val urgent = next.timeMillis - System.currentTimeMillis() < URGENT_WINDOW_MILLIS
            return DisplayContent(
                name = next.label,
                time = formatTime(context, next.timeMillis),
                countdown = countdown,
                urgent = urgent,
                status = context.getString(R.string.widget_next_schedule_open)
            )
        }

        private fun formatCountdown(context: Context, timeMillis: Long): String {
            val deltaMinutes = ((timeMillis - System.currentTimeMillis() + 59_999L) / 60_000L).coerceAtLeast(0L)
            return when {
                deltaMinutes < 60L -> context.getString(R.string.widget_glance_countdown_minutes, deltaMinutes)
                deltaMinutes < 48L * 60L -> context.getString(
                    R.string.widget_glance_countdown_hours_minutes,
                    deltaMinutes / 60L,
                    deltaMinutes % 60L
                )
                else -> context.getString(
                    R.string.widget_glance_countdown_days_hours,
                    deltaMinutes / (24L * 60L),
                    (deltaMinutes % (24L * 60L)) / 60L
                )
            }
        }

        private fun formatTime(context: Context, timeMillis: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
            val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            return TimeFormatPrefs.formatMinutesOfDay(context, minutesOfDay)
        }

        private fun findNextBoundary(context: Context): BoundaryInfo? {
            if (SchedulePlanner.getNextBoundaryMillis(context) <= 0L) {
                return null
            }
            val schedules = ScheduleStore.getAll(context)
                .filter { it.enabled }
                .filterNot { schedule ->
                    val isConnectionOnly = !schedule.wifiSsid.isNullOrBlank() || (!schedule.btDeviceName.isNullOrBlank() || !schedule.btDeviceAddress.isNullOrBlank())
                    isConnectionOnly && schedule.startMinutes == 0 && schedule.endMinutes >= 1439
                }
            if (schedules.isEmpty()) {
                return null
            }

            val now = Calendar.getInstance()
            val nowMs = now.timeInMillis
            var best: BoundaryInfo? = null

            fun consider(candidate: BoundaryInfo) {
                if (candidate.timeMillis <= nowMs) {
                    return
                }
                if (best == null || candidate.timeMillis < best!!.timeMillis) {
                    best = candidate
                }
            }

            schedules.forEach { schedule ->
                when (schedule.type) {
                    ScheduleStore.Type.WEEKLY -> {
                        repeat(14) { offset ->
                            val day = Calendar.getInstance().apply {
                                timeInMillis = nowMs
                                add(Calendar.DAY_OF_YEAR, offset)
                            }
                            val bit = ScheduleStore.Days.fromCalendarDay(day.get(Calendar.DAY_OF_WEEK))
                            if (schedule.daysMask and bit == 0) return@repeat
                            considerBoundary(context, schedule, day)?.let(::consider)
                            considerBoundary(context, schedule, day, endBoundary = true)?.let(::consider)
                        }
                    }
                    ScheduleStore.Type.ONE_TIME -> {
                        if (schedule.startDate <= 0 || schedule.endDate <= 0) return@forEach
                        repeat(30) { offset ->
                            val day = Calendar.getInstance().apply {
                                timeInMillis = nowMs
                                add(Calendar.DAY_OF_YEAR, offset)
                            }
                            val ymd = day.get(Calendar.YEAR) * 10000 + (day.get(Calendar.MONTH) + 1) * 100 + day.get(Calendar.DAY_OF_MONTH)
                            if (ymd !in schedule.startDate..schedule.endDate) return@repeat
                            considerBoundary(context, schedule, day)?.let(::consider)
                            considerBoundary(context, schedule, day, endBoundary = true)?.let(::consider)
                        }
                    }
                }
            }

            return best
        }

        private fun considerBoundary(
            context: Context,
            schedule: ScheduleStore.Schedule,
            day: Calendar,
            endBoundary: Boolean = false,
        ): BoundaryInfo? {
            if (endBoundary && !isRangeAction(schedule.action)) {
                return null
            }
            val minutes = if (endBoundary) schedule.endMinutes else schedule.startMinutes
            val timeMillis = (day.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, minutes / 60)
                set(Calendar.MINUTE, minutes % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            return BoundaryInfo(
                timeMillis = timeMillis,
                label = schedule.title.ifBlank {
                    schedule.note.ifBlank {
                        schedule.profile.ifBlank { context.getString(R.string.widget_next_schedule_name) }
                    }
                }
            )
        }

        private fun isRangeAction(action: ScheduleStore.Action): Boolean {
            return when (action) {
                ScheduleStore.Action.ENABLE_AND_DISABLE,
                ScheduleStore.Action.DISABLE_AND_ENABLE -> true
                else -> false
            }
        }
    }

    private data class DisplayContent(
        val name: String,
        val time: String,
        val countdown: String? = null,
        val showTime: Boolean = true,
        val showEmptyState: Boolean = false,
        val urgent: Boolean = false,
        val status: String,
    )

    private data class BoundaryInfo(
        val timeMillis: Long,
        val label: String,
    )
}
