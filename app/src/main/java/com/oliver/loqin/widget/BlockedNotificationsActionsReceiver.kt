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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oliver.loqin.data.prefs.BlockedInboxStore

// Handles the widget's clear-all action for the blocked notifications inbox.
class BlockedNotificationsActionsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CLEAR_ALL -> {
                BlockedInboxStore.clear(context)
                BlockedNotificationsWidgetProvider.refreshAll(context)
            }
        }
    }

    companion object {
        const val ACTION_CLEAR_ALL = "com.oliver.loqin.widget.ACTION_CLEAR_BLOCKED_NOTIFICATIONS"
    }
}
