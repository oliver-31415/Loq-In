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

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.BlockCountStore
import com.oliver.loqin.data.prefs.BlockedTimeStore
import com.oliver.loqin.data.prefs.UsageStore
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Posts the local Monday-morning summary of last week (screen time, blocks, focus time). */
class WeeklySummaryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        if (!isEnabled(ctx)) return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val zone = TimeZone.getDefault()
        val lastWeek = WeeklySummary.ymdsForDaysAgo(now, zone, 1..7)
        val weekBefore = WeeklySummary.ymdsForDaysAgo(now, zone, 8..14)
        val totals = WeeklySummary.Totals(
            screenMs = UsageStore.getUsageMsTotalForDays(ctx, lastWeek),
            previousScreenMs = UsageStore.getUsageMsTotalForDays(ctx, weekBefore),
            blocks = BlockCountStore.getTotalForDays(ctx, lastWeek),
            focusMs = lastWeek.sumOf { BlockedTimeStore.getProtectionMsForDay(ctx, it) },
        )
        if (!WeeklySummary.shouldSend(UsageStore.getDaysWithUsageCount(ctx), totals)) {
            return Result.success()
        }
        post(ctx, bodyText(ctx, totals))
        return Result.success()
    }

    private fun bodyText(ctx: Context, totals: WeeklySummary.Totals): String {
        val change = WeeklySummary.changePercent(totals.screenMs, totals.previousScreenMs)
        val changeText = when {
            change == null || change == 0 -> ""
            change > 0 -> ctx.getString(R.string.weekly_summary_less_fmt, change)
            else -> ctx.getString(R.string.weekly_summary_more_fmt, -change)
        }
        return ctx.getString(
            R.string.weekly_summary_body_fmt,
            formatDuration(ctx, totals.screenMs),
            changeText,
            ctx.resources.getQuantityString(R.plurals.weekly_summary_blocks, totals.blocks, totals.blocks),
            formatDuration(ctx, totals.focusMs),
        )
    }

    private fun formatDuration(ctx: Context, ms: Long): String {
        val totalMinutes = (ms / 60_000L).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            ctx.getString(R.string.weekly_summary_duration_hm_fmt, hours, minutes)
        } else {
            ctx.getString(R.string.weekly_summary_duration_m_fmt, minutes)
        }
    }

    private fun post(ctx: Context, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.weekly_summary_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = ctx.getString(R.string.weekly_summary_channel_desc) }
        )
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, LoqInOverviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_blocking_white_24)
            .setContentTitle(ctx.getString(R.string.weekly_summary_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        const val PREF_ENABLED = "pref_weekly_summary_enabled"
        private const val CHANNEL_ID = "weekly_summary"
        private const val NOTIFICATION_ID = 41_007
        private const val WORK_NAME = "weekly_summary"

        fun isEnabled(ctx: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(PREF_ENABLED, true)

        /** Schedules the weekly job (Mondays ~09:00); keeps an existing schedule. */
        fun ensureScheduled(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(
                    WeeklySummary.delayUntilNextSend(System.currentTimeMillis(), TimeZone.getDefault()),
                    TimeUnit.MILLISECONDS,
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
