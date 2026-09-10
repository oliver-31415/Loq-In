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
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.StyleSpan
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.oliver.loqin.R
import com.oliver.loqin.feature.entry.ScanLauncherActivity

class BlockedNotificationsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BlockedNotificationsWidgetProvider::class.java))
            ids.forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
            }
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val events = com.oliver.loqin.data.prefs.BlockedInboxStore.getAll(context).take(5)
            val views = RemoteViews(context.packageName, R.layout.widget_blocked_notifications)
            views.setTextViewCompoundDrawablesRelative(R.id.widgetBlockedHeader, R.drawable.widget_notifications_20, 0, 0, 0)
            views.setOnClickPendingIntent(
                R.id.widgetBlockedRoot,
                PendingIntent.getActivity(
                    context,
                    2106 + appWidgetId,
                    Intent(context, ScanLauncherActivity::class.java)
                        .setAction(ScanLauncherActivity.ACTION_OPEN_BLOCKED_NOTIFICATIONS)
                        .setData("loqin://widget/blocked/$appWidgetId".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            if (events.isEmpty()) {
                views.setTextViewText(R.id.widgetBlockedEmpty, context.getString(R.string.blocked_inbox_empty))
                views.setViewVisibility(R.id.widgetBlockedEmpty, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widgetBlockedEmpty, android.view.View.GONE)
            }

            val rowIds = listOf(R.id.widgetBlockedRow1, R.id.widgetBlockedRow2, R.id.widgetBlockedRow3, R.id.widgetBlockedRow4, R.id.widgetBlockedRow5)
            events.forEachIndexed { index, event ->
                val rowId = rowIds[index]
                views.setViewVisibility(rowId, android.view.View.VISIBLE)
                views.setTextViewText(rowId, formatRow(context, event))
            }
            for (i in events.size until rowIds.size) {
                views.setViewVisibility(rowIds[i], android.view.View.GONE)
            }
            return views
        }

        private fun formatRow(context: Context, event: com.oliver.loqin.data.prefs.BlockedNotificationEvent): CharSequence {
            val appName = runCatching {
                val ai = context.packageManager.getApplicationInfo(event.pkg, 0)
                context.packageManager.getApplicationLabel(ai)?.toString().orEmpty().trim()
            }.getOrNull().takeUnless { it.isNullOrBlank() } ?: event.pkg

            val preview = sequenceOf(event.title, event.text, event.bigText, event.subText, event.summaryText, event.reason)
                .map { it.replace("\n", " ").trim() }
                .firstOrNull { it.isNotBlank() }
                ?: context.getString(R.string.blocked_inbox_content_unknown)

            val relativeTime = DateUtils.getRelativeTimeSpanString(
                event.timeMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()

            val header = "$appName • $relativeTime"
            val trimmedPreview = preview.take(72)
            return SpannableStringBuilder().apply {
                append(header)
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    appName.length.coerceAtMost(length),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (trimmedPreview.isNotBlank()) {
                    append("\n")
                    append(trimmedPreview)
                }
            }
        }
    }
}
