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

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.oliver.loqin.R
import com.oliver.loqin.ui.dialog.showAccented

/** Shared protection feedback so queued/blocked edits do not fall back to transient toasts. */
object ProtectionFeedback {

    fun showQueued(context: Context, afterDismiss: (() -> Unit)? = null) {
        val show = {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.protection_pending_changes_title)
                .setMessage(R.string.protection_change_queued)
                .setIcon(R.drawable.schedule_24)
                .setPositiveButton(R.string.ok, null)
                .showAccented()
                .apply {
                    if (afterDismiss != null) setOnDismissListener { afterDismiss() }
                }
        }
        val activity = context as? Activity
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            // Several callers close their own editor dialog in the same callback; posting makes
            // this the next visible dialog instead of stacking two windows.
            activity.window.decorView.post {
                if (!activity.isFinishing && !activity.isDestroyed) show()
            }
        } else if (afterDismiss == null) {
            show()
        }
    }

    fun showInfo(
        context: Context,
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(R.string.ok, null)
            .showAccented()
    }
}
