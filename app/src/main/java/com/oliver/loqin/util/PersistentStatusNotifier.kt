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

package com.oliver.loqin.util

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
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.ProfileRuleModeStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.feature.profiles.ManageProfilesActivity
import com.oliver.loqin.ui.MainActivity

// Maintains the optional ongoing notification that shows LoqIn's current state and profile.
object PersistentStatusNotifier {

    private const val CHANNEL_ID = "loqin_status"
    private const val NOTIFICATION_ID = 9002
    private const val KEY_ENABLED = "pref_persistent_status_notification"
    private const val KEY_DETAIL_MODE = "pref_persistent_status_notification_detail_mode"

    const val MODE_STATUS_ONLY = "status_only"
    const val MODE_ACTIVE_TIME = "active_time"
    const val MODE_APP_COUNT = "app_count"
    const val MODE_FULL = "full"

    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(KEY_ENABLED, enabled)
        }
        refresh(context)
    }

    fun detailMode(context: Context): String {
        val stored = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_DETAIL_MODE, MODE_FULL)
        return when (stored) {
            MODE_STATUS_ONLY, MODE_ACTIVE_TIME, MODE_APP_COUNT, MODE_FULL -> stored
            else -> MODE_FULL
        }
    }

    fun setDetailMode(context: Context, mode: String) {
        val normalized = when (mode) {
            MODE_STATUS_ONLY, MODE_ACTIVE_TIME, MODE_APP_COUNT, MODE_FULL -> mode
            else -> MODE_FULL
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(KEY_DETAIL_MODE, normalized)
        }
        refresh(context)
    }

    fun refresh(context: Context) {
        val ctx = context.applicationContext
        if (!isEnabled(ctx)) {
            cancel(ctx)
            return
        }
        if (!canPostNotifications(ctx)) {
            cancel(ctx)
            return
        }

        val notificationManager = ctx.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.status_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = ctx.getString(R.string.status_notification_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )

        val enabled = SwitchModeStore.isEnabled(ctx)
        val tempDisableRemaining = SwitchModeStore.getTemporaryRemainingMillis(ctx)
        val tempEnableRemaining = SwitchModeStore.getTemporaryEnableRemainingMillis(ctx)
        val tempCountdownRemaining = maxOf(tempDisableRemaining, tempEnableRemaining)
        val tempDisableActive = tempDisableRemaining > 0L
        val tempEnableActive = tempEnableRemaining > 0L
        val mode = detailMode(ctx)
        val needsAppCount = mode == MODE_APP_COUNT || mode == MODE_FULL
        val rawProfile = if (needsAppCount) ProfileStore.getCurrent(ctx).orEmpty() else ""
        val appCountText = if (needsAppCount) {
            val selectedAppCount = if (rawProfile.isBlank()) {
                0
            } else {
                ProfileStore.getSelectedForProfileMode(ctx, rawProfile).size
            }
            ctx.resources.getQuantityString(
                if (rawProfile.isNotBlank() && ProfileRuleModeStore.isAllowMode(ctx, rawProfile)) {
                    R.plurals.status_notification_allowed_apps
                } else {
                    R.plurals.status_notification_blocked_apps
                },
                selectedAppCount,
                selectedAppCount,
            )
        } else {
            ""
        }

        val openAppIntent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            ctx,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openProfilesIntent = Intent(ctx, ManageProfilesActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openProfilesPendingIntent = PendingIntent.getActivity(
            ctx,
            1,
            openProfilesIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(if (enabled) R.drawable.lock_24 else R.drawable.lock_open_24)
            .setContentTitle(
                ctx.getString(
                    when {
                        tempDisableActive -> R.string.status_notification_temp_disable_title
                        tempEnableActive -> R.string.status_notification_temp_enable_title
                        enabled -> R.string.status_notification_enabled_title
                        else -> R.string.status_notification_disabled_title
                    }
                )
            )
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.switch_account_24,
                ctx.getString(R.string.status_notification_profiles_action),
                openProfilesPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setDefaults(0)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when {
            tempDisableActive -> notificationBuilder.setContentText(
                ctx.getString(R.string.status_notification_temp_disable_body)
            )
            tempEnableActive -> notificationBuilder.setContentText(
                ctx.getString(R.string.status_notification_temp_enable_body)
            )
            else -> when (mode) {
                MODE_APP_COUNT -> notificationBuilder.setContentText(appCountText)
                MODE_FULL -> notificationBuilder.setContentText(
                    ctx.getString(
                        R.string.status_notification_profile_apps_fmt,
                        rawProfile.ifBlank { ctx.getString(R.string.status_notification_no_profile) },
                        appCountText,
                    )
                )
                MODE_STATUS_ONLY, MODE_ACTIVE_TIME -> notificationBuilder.setContentText(null)
            }
        }

        val showActiveTime = enabled && (mode == MODE_ACTIVE_TIME || mode == MODE_FULL)
        if (tempCountdownRemaining > 0L) {
            notificationBuilder
                .setWhen(System.currentTimeMillis() + tempCountdownRemaining)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
        } else if (showActiveTime) {
            val activeSince = SwitchModeStore.getActiveSinceMillis(ctx)
            if (activeSince in 1..System.currentTimeMillis()) {
                notificationBuilder
                    .setWhen(activeSince)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(false)
                    .setShowWhen(true)
            } else {
                notificationBuilder.setShowWhen(false)
            }
        } else {
            notificationBuilder.setShowWhen(false)
        }

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notificationBuilder.build())
        } catch (_: SecurityException) {
            cancel(ctx)
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
