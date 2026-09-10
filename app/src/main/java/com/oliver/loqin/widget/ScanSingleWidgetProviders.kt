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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.oliver.loqin.R
import com.oliver.loqin.feature.entry.QuickActionIconFactory
import com.oliver.loqin.feature.entry.ScanLauncherActivity
import com.oliver.loqin.feature.theme.AccentColor

abstract class BaseLaunchWidgetProvider : AppWidgetProvider() {

    protected abstract val labelRes: Int
    protected abstract val iconRes: Int
    protected abstract val launchAction: String
    protected abstract val requestCode: Int

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val onSurface = ContextCompat.getColor(context, R.color.foqos_on_surface)
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_action_compact).apply {
                setContentDescription(R.id.widgetActionRoot, context.getString(labelRes))
                setImageViewBitmap(
                    R.id.widgetActionIcon,
                    QuickActionIconFactory.createWidgetBitmap(context, iconRes, onSurface, 40, 36)
                )
                setOnClickPendingIntent(
                    R.id.widgetActionRoot,
                    PendingIntent.getActivity(
                        context,
                        requestCode + appWidgetId,
                        Intent(context, ScanLauncherActivity::class.java)
                            .setAction(launchAction)
                            .setData("loqin://widget/${javaClass.simpleName}/$appWidgetId".toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

class QrScanWidgetProvider : BaseLaunchWidgetProvider() {
    override val labelRes: Int = R.string.shortcut_qr_short
    override val iconRes: Int = R.drawable.qr_code_24
    override val launchAction: String = ScanLauncherActivity.ACTION_OPEN_QR_SCAN
    override val requestCode: Int = 2101
}

class BarcodeScanWidgetProvider : BaseLaunchWidgetProvider() {
    override val labelRes: Int = R.string.shortcut_barcode_short
    override val iconRes: Int = R.drawable.barcode_24
    override val launchAction: String = ScanLauncherActivity.ACTION_OPEN_BARCODE_SCAN
    override val requestCode: Int = 2102
}

class NfcWriteWidgetProvider : BaseLaunchWidgetProvider() {
    override val labelRes: Int = R.string.shortcut_nfc_short
    override val iconRes: Int = R.drawable.nfc_24
    override val launchAction: String = ScanLauncherActivity.ACTION_OPEN_NFC_WRITE
    override val requestCode: Int = 2103
}

class FocusNowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FocusNowWidgetProvider::class.java))
            ids.forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
            }
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val accent = AccentColor.getAccentColorInt(context)
            return RemoteViews(context.packageName, R.layout.widget_action_compact).apply {
                setContentDescription(R.id.widgetActionRoot, context.getString(R.string.shortcut_focus_now_short))
                setImageViewBitmap(
                    R.id.widgetActionIcon,
                    QuickActionIconFactory.createWidgetBitmap(context, R.drawable.play_arrow_24, accent, 40, 36)
                )
                setOnClickPendingIntent(
                    R.id.widgetActionRoot,
                    PendingIntent.getBroadcast(
                        context,
                        2105 + appWidgetId,
                        Intent(context, QuickActionReceiver::class.java)
                            .setAction(QuickActionReceiver.ACTION_FOCUS_NOW)
                            .setData("loqin://widget/focus/$appWidgetId".toUri()),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }
    }
}
