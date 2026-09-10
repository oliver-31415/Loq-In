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
import com.oliver.loqin.feature.entry.QuickActionIconFactory
import com.oliver.loqin.feature.entry.ScanLauncherActivity
import com.oliver.loqin.feature.theme.AccentColor
import com.oliver.loqin.ui.MainActivity

class ScannerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetManager, appWidgetId))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetManager, appWidgetId))
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScannerWidgetProvider::class.java))
            ids.forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, buildViews(context, manager, appWidgetId))
            }
        }

        private fun buildViews(context: Context, manager: AppWidgetManager, appWidgetId: Int): RemoteViews {
            val options = manager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val showExpandedRow = minWidth >= 260 && minHeight >= 190
            val layoutRes = when {
                // Wide and short (e.g. 4x1 / 5x1): single-row 1x4 layout.
                minWidth >= 250 && minHeight < 80 -> R.layout.widget_scanner_row
                // Short widgets: drop the header, keep the 2x2 grid.
                minHeight < 110 -> R.layout.widget_scanner_compact
                else -> R.layout.widget_scanner
            }
            val accent = AccentColor.getAccentColorInt(context)
            val onSurface = ContextCompat.getColor(context, R.color.foqos_on_surface)
            val views = RemoteViews(context.packageName, layoutRes)
            return views.apply {
                setImageViewBitmap(R.id.widgetOpenAppIcon, QuickActionIconFactory.createWidgetBitmap(context, R.drawable.apps_24, onSurface))
                setImageViewBitmap(R.id.widgetOpenQrIcon, QuickActionIconFactory.createWidgetBitmap(context, R.drawable.qr_code_24, onSurface))
                setImageViewBitmap(R.id.widgetOpenBarcodeIcon, QuickActionIconFactory.createWidgetBitmap(context, R.drawable.barcode_24, onSurface))
                setImageViewBitmap(R.id.widgetOpenFocusNowIcon, QuickActionIconFactory.createWidgetBitmap(context, R.drawable.play_arrow_24, accent))
                setImageViewBitmap(R.id.widgetOpenNfcWriteIcon, QuickActionIconFactory.createWidgetBitmap(context, R.drawable.nfc_24, onSurface))
                setImageViewBitmap(R.id.widgetOpenBlockedNotificationsIcon, QuickActionIconFactory.createWidgetBitmap(context, R.drawable.notifications_24, onSurface))

                setViewVisibility(
                    R.id.widgetExpandedRow,
                    if (showExpandedRow) android.view.View.VISIBLE else android.view.View.GONE
                )

                setOnClickPendingIntent(
                    R.id.widgetOpenApp,
                    pendingActivity(
                        context,
                        2000 + appWidgetId,
                        Intent(context, MainActivity::class.java)
                            .setData("loqin://widget/hub/app/$appWidgetId".toUri())
                    )
                )
                setOnClickPendingIntent(
                    R.id.widgetOpenQr,
                    quickActionPending(context, 3000 + appWidgetId, ScanLauncherActivity.ACTION_OPEN_QR_SCAN, appWidgetId)
                )
                setOnClickPendingIntent(
                    R.id.widgetOpenBarcode,
                    quickActionPending(context, 4000 + appWidgetId, ScanLauncherActivity.ACTION_OPEN_BARCODE_SCAN, appWidgetId)
                )
                setOnClickPendingIntent(
                    R.id.widgetOpenFocusNow,
                    PendingIntent.getBroadcast(
                        context,
                        4500 + appWidgetId,
                        Intent(context, QuickActionReceiver::class.java)
                            .setAction(QuickActionReceiver.ACTION_FOCUS_NOW)
                            .setData("loqin://widget/hub/focus/$appWidgetId".toUri()),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                setOnClickPendingIntent(
                    R.id.widgetOpenNfcWrite,
                    quickActionPending(context, 5000 + appWidgetId, ScanLauncherActivity.ACTION_OPEN_NFC_WRITE, appWidgetId)
                )
                setOnClickPendingIntent(
                    R.id.widgetOpenBlockedNotifications,
                    quickActionPending(context, 6000 + appWidgetId, ScanLauncherActivity.ACTION_OPEN_BLOCKED_NOTIFICATIONS, appWidgetId)
                )
            }
        }

        private fun quickActionPending(context: Context, requestCode: Int, action: String, appWidgetId: Int): PendingIntent {
            return pendingActivity(
                context,
                requestCode,
                Intent(context, ScanLauncherActivity::class.java)
                    .setAction(action)
                    .setData("loqin://widget/hub/${action.substringAfterLast('.')}/$appWidgetId".toUri())
            )
        }

        private fun pendingActivity(context: Context, requestCode: Int, intent: Intent): PendingIntent {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
