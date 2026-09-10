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

package com.oliver.loqin.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.AutomationModeStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.feature.entry.QuickActionIconFactory
import com.oliver.loqin.feature.theme.AccentColor
import com.oliver.loqin.ui.HeroArtRenderer
import com.oliver.loqin.ui.MainActivity

class ActiveTimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateWidget(context, manager, id) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle?,
    ) {
        super.onAppWidgetOptionsChanged(context, manager, id, newOptions)
        updateWidget(context, manager, id)
    }

    companion object {
        // Fallbacks used when the launcher has not reported a size yet.
        private const val DEFAULT_WIDTH_DP = 250
        private const val DEFAULT_HEIGHT_DP = 150

        // Height bucket at which the widget switches to the expanded layout.
        private const val TALL_LAYOUT_MIN_HEIGHT_DP = 160

        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, ActiveTimerWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { id -> updateWidget(appContext, manager, id) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val enabled = SwitchModeStore.isEnabled(context)
            val durationMs = SwitchModeStore.getActiveDurationMillis(context)

            val options = manager.getAppWidgetOptions(id)
            val density = context.resources.displayMetrics.density
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                .takeIf { it > 0 } ?: DEFAULT_WIDTH_DP
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                .takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP

            // Render the hero artwork at the widget's real size so resizing never
            // stretches the bitmap or distorts its rounded corners.
            val layoutRes = if (heightDp >= TALL_LAYOUT_MIN_HEIGHT_DP) {
                R.layout.widget_active_timer_tall
            } else {
                R.layout.widget_active_timer
            }
            val views = RemoteViews(context.packageName, layoutRes)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                1200 + id,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            val profile = ProfileStore.getCurrent(context)?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.profile_default_name)
            val appCount = ProfileStore.getProfileApps(context, profile).size

            if (enabled) {
                val accent = AccentColor.getAccentColorInt(context)
                val mode = AutomationModeStore.getMode(context)
                val iconRes = when (mode) {
                    AutomationModeStore.Mode.MIXED -> R.drawable.security_24
                    AutomationModeStore.Mode.NFC -> R.drawable.nfc_24
                    AutomationModeStore.Mode.QR -> R.drawable.qr_code_24
                    AutomationModeStore.Mode.BARCODE -> R.drawable.barcode_24
                    AutomationModeStore.Mode.SCHEDULE -> R.drawable.schedule_24
                }

                // Organic Foqos-style blob artwork at the actual widget size, using the
                // launcher's corner radius so active and idle states match.
                val bgBitmap = HeroArtRenderer.renderBitmap(
                    widthPx = (widthDp * density).toInt().coerceAtLeast(1),
                    heightPx = (heightDp * density).toInt().coerceAtLeast(1),
                    accent = accent,
                    radiusPx = widgetCornerRadiusPx(context, density),
                )
                views.setImageViewBitmap(R.id.widgetActiveTimerBg, bgBitmap)

                // Populate header & badge
                views.setTextViewText(R.id.widgetTitle, profile)
                views.setTextColor(R.id.widgetTitle, Color.WHITE)
                views.setTextViewText(R.id.widgetStatusBadge, modeLabel(context, mode))
                views.setTextColor(R.id.widgetStatusBadge, Color.WHITE)
                views.setInt(R.id.widgetStatusBadge, "setBackgroundResource", R.drawable.widget_status_pill_bg)

                val iconBmp = QuickActionIconFactory.createWidgetBitmap(
                    context = context,
                    drawableRes = iconRes,
                    tint = Color.WHITE,
                    canvasSizeDp = 20,
                    iconSizeDp = 18,
                )
                views.setImageViewBitmap(R.id.widgetIcon, iconBmp)

                // Live counting Chronometer
                val base = SystemClock.elapsedRealtime() - durationMs.coerceAtLeast(0L)
                views.setViewVisibility(R.id.widgetChronometer, View.VISIBLE)
                views.setTextColor(R.id.widgetChronometer, Color.WHITE)
                views.setChronometer(R.id.widgetChronometer, base, null, true)

                // Subtitle
                val subtitle = if (appCount > 0) {
                    context.resources.getQuantityString(R.plurals.profile_app_count, appCount, appCount)
                } else {
                    context.getString(R.string.dashboard_status_enabled)
                }
                views.setTextViewText(R.id.widgetSubtitle, subtitle)
                views.setTextColor(R.id.widgetSubtitle, ColorUtils.setAlphaComponent(Color.WHITE, 0xCC))
            } else {
                val onSurface = ContextCompat.getColor(context, R.color.foqos_on_surface)
                val onSurfaceVariant = ContextCompat.getColor(context, R.color.foqos_on_surface_variant)

                // Calm neutral card (shape drawable, scales to any size)
                views.setImageViewResource(R.id.widgetActiveTimerBg, R.drawable.hero_profile_bg_idle)

                // Header & badge
                views.setTextViewText(R.id.widgetTitle, profile)
                views.setTextColor(R.id.widgetTitle, onSurface)
                views.setTextViewText(R.id.widgetStatusBadge, context.getString(R.string.widget_active_timer_inactive).uppercase())
                views.setTextColor(R.id.widgetStatusBadge, onSurfaceVariant)
                views.setInt(R.id.widgetStatusBadge, "setBackgroundResource", R.drawable.widget_status_pill_idle_bg)

                val iconBmp = QuickActionIconFactory.createWidgetBitmap(
                    context = context,
                    drawableRes = R.drawable.security_24,
                    tint = onSurface,
                    canvasSizeDp = 20,
                    iconSizeDp = 18,
                )
                views.setImageViewBitmap(R.id.widgetIcon, iconBmp)

                // Chronometer idle placeholder
                views.setChronometer(R.id.widgetChronometer, SystemClock.elapsedRealtime(), null, false)
                views.setTextViewText(R.id.widgetChronometer, "—")
                views.setTextColor(R.id.widgetChronometer, onSurface)
                views.setViewVisibility(R.id.widgetChronometer, View.VISIBLE)

                // Subtitle
                views.setTextViewText(R.id.widgetSubtitle, context.getString(R.string.widget_active_timer_inactive))
                views.setTextColor(R.id.widgetSubtitle, onSurfaceVariant)
            }

            manager.updateAppWidget(id, views)
        }

        /**
         * Corner radius used for the active hero bitmap. On API 31+ the launcher
         * publishes its widget corner radius as a system dimen; fall back to the
         * same 28dp used by hero_profile_bg_idle.
         */
        private fun widgetCornerRadiusPx(context: Context, density: Float): Float {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val radius = context.resources.getDimension(
                        android.R.dimen.system_app_widget_background_radius
                    )
                    if (radius > 0f) return radius
                } catch (_: Resources.NotFoundException) {
                    // Fall through to the legacy default.
                }
            }
            return 28f * density
        }

        private fun modeLabel(context: Context, mode: AutomationModeStore.Mode): String =
            context.getString(
                when (mode) {
                    AutomationModeStore.Mode.MIXED -> R.string.blocking_mode_mixed
                    AutomationModeStore.Mode.NFC -> R.string.blocking_mode_nfc
                    AutomationModeStore.Mode.QR -> R.string.blocking_mode_qr
                    AutomationModeStore.Mode.BARCODE -> R.string.blocking_mode_barcode
                    AutomationModeStore.Mode.SCHEDULE -> R.string.blocking_mode_schedule
                }
            )
    }
}
