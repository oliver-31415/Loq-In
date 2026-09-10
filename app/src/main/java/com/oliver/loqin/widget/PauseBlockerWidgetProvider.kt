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
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.oliver.loqin.R

class PauseBlockerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PauseBlockerWidgetProvider::class.java))
            ids.forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
            }
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_pause_blocker).apply {
                setTextViewCompoundDrawablesRelative(R.id.widgetPauseHeader, R.drawable.widget_toggle_off_20, 0, 0, 0)
                bindButton(context, appWidgetId, R.id.widgetPause15, 15, QuickActionReceiver.ACTION_PAUSE_LOQIN_15, 2301)
                bindButton(context, appWidgetId, R.id.widgetPause30, 30, QuickActionReceiver.ACTION_PAUSE_LOQIN_30, 2302)
                bindButton(context, appWidgetId, R.id.widgetPause60, 60, QuickActionReceiver.ACTION_PAUSE_LOQIN_60, 2303)
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
    }
}
