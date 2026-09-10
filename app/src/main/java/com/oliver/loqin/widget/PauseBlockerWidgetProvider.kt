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

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.feature.entry.QuickActionIconFactory
import com.oliver.loqin.feature.theme.AccentColor
import java.util.Date
import java.util.Locale

class PauseBlockerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // A missed/expired break should flip the widget back to idle and run the
        // app's normal expiry side effects (state counters, notifier, runtime).
        SwitchModeStore.finishTemporaryDisableIfExpired(context)
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
        }
        scheduleTick(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
        scheduleTick(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickPendingIntent(context, IntArray(0)))
    }

    companion object {
        // Single self-addressed minute-tick alarm while a break is running. It targets
        // this provider with ACTION_APPWIDGET_UPDATE, so no manifest change is needed.
        private const val TICK_REQUEST_CODE = 2400
        private const val TICK_INTERVAL_MS = 60_000L

        fun refreshAll(context: Context) {
            SwitchModeStore.finishTemporaryDisableIfExpired(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PauseBlockerWidgetProvider::class.java))
            ids.forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
            }
            scheduleTick(context)
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val remaining = SwitchModeStore.getTemporaryRemainingMillis(context)
            return if (remaining > 0L) {
                buildActiveViews(context, appWidgetId, remaining)
            } else {
                buildIdleViews(context, appWidgetId)
            }
        }

        private fun buildIdleViews(context: Context, appWidgetId: Int): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_pause_blocker).apply {
                setTextViewCompoundDrawablesRelative(R.id.widgetPauseHeader, R.drawable.widget_toggle_off_20, 0, 0, 0)
                bindButton(context, appWidgetId, R.id.widgetPause5, 5, QuickActionReceiver.ACTION_PAUSE_LOQIN_5, 2304)
                bindButton(context, appWidgetId, R.id.widgetPause15, 15, QuickActionReceiver.ACTION_PAUSE_LOQIN_15, 2301)
                bindButton(context, appWidgetId, R.id.widgetPause30, 30, QuickActionReceiver.ACTION_PAUSE_LOQIN_30, 2302)
                bindButton(context, appWidgetId, R.id.widgetPause60, 60, QuickActionReceiver.ACTION_PAUSE_LOQIN_60, 2303)
            }
        }

        private fun buildActiveViews(context: Context, appWidgetId: Int, remainingMs: Long): RemoteViews {
            val accent = AccentColor.getAccentColorInt(context)
            val endsAt = System.currentTimeMillis() + remainingMs
            val endsAtText = DateFormat.getTimeFormat(context).format(Date(endsAt))
            return RemoteViews(context.packageName, R.layout.widget_pause_blocker_active).apply {
                setImageViewBitmap(
                    R.id.widgetBreakIcon,
                    QuickActionIconFactory.createWidgetBitmap(context, R.drawable.timer_24, accent, 20, 18)
                )
                setTextViewText(R.id.widgetBreakTitle, context.getString(R.string.widget_breaks_title))
                setTextColor(R.id.widgetBreakTitle, accent)
                setTextViewText(R.id.widgetBreakSubtitle, context.getString(R.string.widget_breaks_protection_off))
                setTextViewText(R.id.widgetBreakRemaining, formatRemaining(remainingMs))
                setTextColor(R.id.widgetBreakRemaining, accent)
                setTextViewText(R.id.widgetBreakEndsAt, context.getString(R.string.widget_breaks_ends_at_fmt, endsAtText))
                setTextViewText(R.id.widgetBreakEnd, context.getString(R.string.widget_breaks_end_tile))
                setTextColor(R.id.widgetBreakEnd, accent)
                setOnClickPendingIntent(
                    R.id.widgetBreakEnd,
                    PendingIntent.getBroadcast(
                        context,
                        2305 + appWidgetId,
                        Intent(context, QuickActionReceiver::class.java)
                            .setAction(QuickActionReceiver.ACTION_PAUSE_LOQIN_END)
                            .setData("loqin://widget/pause/end/$appWidgetId".toUri()),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }

        private fun RemoteViews.bindButton(
            context: Context,
            appWidgetId: Int,
            viewId: Int,
            minutes: Int,
            action: String,
            requestCodeBase: Int,
        ) {
            setOnClickPendingIntent(
                viewId,
                PendingIntent.getBroadcast(
                    context,
                    requestCodeBase + appWidgetId,
                    Intent(context, QuickActionReceiver::class.java)
                        .setAction(action)
                        .setData("loqin://widget/pause/$minutes/$appWidgetId".toUri()),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        private fun formatRemaining(remainingMs: Long): String {
            val totalSeconds = remainingMs / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.US, "%d:%02d", hours, minutes)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

        /**
         * While a break is running, re-render the countdown once per minute (and once
         * just after expiry) via a self-addressed AppWidgetProvider broadcast. When the
         * widget is idle, any pending tick is cancelled.
         */
        private fun scheduleTick(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PauseBlockerWidgetProvider::class.java))
            val remaining = SwitchModeStore.getTemporaryRemainingMillis(context)

            // PendingIntent matching ignores extras, so this matches the alarm set below.
            val piKey = tickPendingIntent(context, IntArray(0))
            if (ids.isEmpty() || remaining <= 0L) {
                am.cancel(piKey)
                return
            }

            val now = System.currentTimeMillis()
            val triggerAt = minOf(now + TICK_INTERVAL_MS, now + remaining + 1_000L)

            // The fired broadcast must carry the ids so onUpdate re-renders the widgets.
            val pi = tickPendingIntent(context, ids)
            am.cancel(piKey)
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
            } else {
                true
            }
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        private fun tickPendingIntent(context: Context, ids: IntArray): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                TICK_REQUEST_CODE,
                Intent(context, PauseBlockerWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
