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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.StyleSpan
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.BlockedInboxStore
import com.oliver.loqin.data.prefs.BlockedNotificationEvent
import com.oliver.loqin.feature.entry.QuickActionIconFactory
import com.oliver.loqin.feature.entry.ScanLauncherActivity
import kotlin.math.roundToInt

class BlockedNotificationsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetManager, appWidgetId))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetManager, appWidgetId))
    }

    companion object {
        // Height buckets (dp) deciding how many inbox rows fit. Fallback is 2.
        private const val THREE_ROWS_MIN_HEIGHT_DP = 180
        private const val TWO_ROWS_MIN_HEIGHT_DP = 130
        private const val DEFAULT_ROW_COUNT = 2

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BlockedNotificationsWidgetProvider::class.java))
            ids.forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, buildViews(context, manager, appWidgetId))
            }
        }

        private fun buildViews(context: Context, manager: AppWidgetManager, appWidgetId: Int): RemoteViews {
            val allEvents = BlockedInboxStore.getAll(context)
            val rowCount = resolveRowCount(manager.getAppWidgetOptions(appWidgetId))
            val events = allEvents.take(rowCount)

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
            views.setOnClickPendingIntent(
                R.id.widgetBlockedClear,
                PendingIntent.getBroadcast(
                    context,
                    2107 + appWidgetId,
                    Intent(context, BlockedNotificationsActionsReceiver::class.java)
                        .setAction(BlockedNotificationsActionsReceiver.ACTION_CLEAR_ALL)
                        .setData("loqin://widget/blocked-clear/$appWidgetId".toUri()),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            if (allEvents.isEmpty()) {
                views.setTextViewText(R.id.widgetBlockedEmpty, context.getString(R.string.blocked_inbox_empty))
                views.setViewVisibility(R.id.widgetBlockedEmpty, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widgetBlockedEmpty, android.view.View.GONE)
            }
            views.setViewVisibility(
                R.id.widgetBlockedClear,
                if (allEvents.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            )
            views.setImageViewBitmap(
                R.id.widgetBlockedClear,
                QuickActionIconFactory.createWidgetBitmap(
                    context,
                    R.drawable.close_24,
                    canvasSizeDp = 18,
                    iconSizeDp = 16
                )
            )

            val rowIds = listOf(R.id.widgetBlockedRow1, R.id.widgetBlockedRow2, R.id.widgetBlockedRow3)
            val iconIds = listOf(R.id.widgetBlockedIcon1, R.id.widgetBlockedIcon2, R.id.widgetBlockedIcon3)
            val textIds = listOf(R.id.widgetBlockedText1, R.id.widgetBlockedText2, R.id.widgetBlockedText3)
            events.forEachIndexed { index, event ->
                views.setViewVisibility(rowIds[index], android.view.View.VISIBLE)
                views.setImageViewBitmap(iconIds[index], loadAppIconBitmap(context, event.pkg))
                views.setTextViewText(textIds[index], formatRow(context, event))
            }
            for (i in events.size until rowIds.size) {
                views.setViewVisibility(rowIds[i], android.view.View.GONE)
            }
            return views
        }

        private fun resolveRowCount(options: Bundle): Int {
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            return when {
                minHeight <= 0 -> DEFAULT_ROW_COUNT
                minHeight >= THREE_ROWS_MIN_HEIGHT_DP -> 3
                minHeight >= TWO_ROWS_MIN_HEIGHT_DP -> 2
                else -> 1
            }
        }

        private fun loadAppIconBitmap(context: Context, pkg: String): Bitmap {
            val sizePx = (22 * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
            val appIcon = runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
            return appIcon?.let { drawableToBitmap(it, sizePx) }
                ?: QuickActionIconFactory.createWidgetBitmap(
                    context,
                    R.drawable.widget_notifications_24,
                    canvasSizeDp = 22,
                    iconSizeDp = 20
                )
        }

        private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
            }
            return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, sizePx, sizePx)
                drawable.draw(canvas)
            }
        }

        private fun formatRow(context: Context, event: BlockedNotificationEvent): CharSequence {
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
            val trimmedPreview = preview.take(64)
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
