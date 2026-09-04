/*
 * Switchly
 * Copyright (C) 2025-2026 Saltyy
 * Copyright (C) 2026 Switchly Contributors
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

package at.saltyy.switchly.blocking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import at.saltyy.switchly.R
import at.saltyy.switchly.data.prefs.AppLogStore
import at.saltyy.switchly.data.prefs.SwitchModeStore
import at.saltyy.switchly.ui.MainActivity

/**
 * Small OEM-specific foreground guard used only on vivo/iQOO while Switchly is enabled.
 * Some vivo builds remove the process together with the Recent Apps task and leave the Accessibility UI in a misleading "enabled but malfunctioning" state. 
 * Keeping one service in foreground importance gives Android/OEM process management a stronger reason to preserve the process and therefore the system-bound AccessibilityService.
 */
class OemAccessibilityKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        AppLogStore.append(
            this,
            "Accessibility",
            "OEM keep-alive created manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND}",
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!OemAccessibilityKeepAlive.isAffectedDevice() || !SwitchModeStore.isEnabled(this)) {
            stopGuard()
            return START_NOT_STICKY
        }

        val promoted = runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.oem_accessibility_guard_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.oem_accessibility_guard_channel_description)
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )

            val openApp = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.lock_24)
                .setContentTitle(getString(R.string.oem_accessibility_guard_title))
                .setContentText(getString(R.string.oem_accessibility_guard_text))
                .setContentIntent(openApp)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { error ->
            AppLogStore.append(
                this,
                "Accessibility",
                "OEM keep-alive foreground failed reason=${error.javaClass.simpleName}: ${error.message.orEmpty()}",
            )
        }.isSuccess

        if (!promoted) {
            stopSelf()
            return START_NOT_STICKY
        }

        AppLogStore.append(this, "Accessibility", "OEM keep-alive active")
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        AppLogStore.append(
            this,
            "Accessibility",
            "OEM keep-alive task removed; service retained stopWithTask=false",
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        AppLogStore.append(this, "Accessibility", "OEM keep-alive destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopGuard() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "switchly_oem_accessibility_guard"
        private const val NOTIFICATION_ID = 9011
    }
}
