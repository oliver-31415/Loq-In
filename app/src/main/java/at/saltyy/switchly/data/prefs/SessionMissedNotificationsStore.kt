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

package at.saltyy.switchly.data.prefs

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

object SessionMissedNotificationsStore {
    private const val PREFS = "switchly_prefs"
    const val KEY_SHOW_SESSION_MISSED_NOTIFICATIONS = "pref_show_session_missed_notifications"
    private const val KEY_PENDING_SESSION_START = "session_missed_pending_start"
    private const val KEY_PENDING_SESSION_END = "session_missed_pending_end"
    private const val KEY_LAST_SHOWN_NOTIFICATION_TIME = "session_missed_last_shown_time"

    fun isFeatureEnabled(ctx: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(KEY_SHOW_SESSION_MISSED_NOTIFICATIONS, true)
    }

    fun setFeatureEnabled(ctx: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit(commit = true) {
            putBoolean(KEY_SHOW_SESSION_MISSED_NOTIFICATIONS, enabled)
        }
        if (!enabled) {
            clearPending(ctx)
        }
    }

    fun onSessionCompleted(ctx: Context, startMs: Long, endMs: Long) {
        if (!isFeatureEnabled(ctx)) return
        if (startMs <= 0L || endMs <= startMs) return

        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit(commit = true) {
            putLong(KEY_PENDING_SESSION_START, startMs)
            putLong(KEY_PENDING_SESSION_END, endMs)
        }
    }

    fun clearPending(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit(commit = true) {
            remove(KEY_PENDING_SESSION_START)
            remove(KEY_PENDING_SESSION_END)
        }
    }

    /**
     * Checks if there are pending notifications from the last finished blocking session.
     * Consumes the pending session window so it won't be shown repeatedly.
     * Returns the list of missed notifications during that session, sorted newest first.
     */
    fun consumePendingMissedNotifications(ctx: Context): List<BlockedNotificationEvent> {
        if (!isFeatureEnabled(ctx)) {
            clearPending(ctx)
            return emptyList()
        }

        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val startMs = sp.getLong(KEY_PENDING_SESSION_START, 0L)
        val endMs = sp.getLong(KEY_PENDING_SESSION_END, 0L)

        if (startMs <= 0L || endMs <= startMs) {
            return emptyList()
        }

        val lastShown = sp.getLong(KEY_LAST_SHOWN_NOTIFICATION_TIME, 0L)
        val windowStart = maxOf(startMs - 3000L, lastShown + 1L)
        val windowEnd = endMs + 3000L

        clearPending(ctx)

        val events = BlockedInboxStore.getAll(ctx)
        val sessionEvents = events.filter { it.timeMillis in windowStart..windowEnd }

        sp.edit(commit = true) {
            val maxTime = if (sessionEvents.isNotEmpty()) sessionEvents.maxOf { it.timeMillis } else 0L
            putLong(KEY_LAST_SHOWN_NOTIFICATION_TIME, maxOf(maxTime, endMs))
        }

        return sessionEvents
    }
}
