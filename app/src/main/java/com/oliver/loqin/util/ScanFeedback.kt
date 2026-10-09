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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.AppLogStore
import com.oliver.loqin.ui.dialog.showAccented

object ScanFeedback {
    /**
     * Camera permission was denied. Loq In's Permissions page has no camera row and may be locked
     * while protection is active, so offer Android's app settings directly; finishes the scanner
     * when the dialog closes.
     */
    fun cameraPermissionDenied(activity: Activity, source: String, @StringRes messageRes: Int) {
        AppLogStore.append(activity, source, "scan_result status=error reason=permission_missing")
        AlertDialog.Builder(activity)
            .setTitle(R.string.scan_camera_permission_title)
            .setMessage(messageRes)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.scan_camera_permission_open_settings) { _, _ ->
                runCatching {
                    activity.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", activity.packageName, null))
                    )
                }
            }
            .setOnDismissListener { activity.finish() }
            .showAccented()
    }

    fun error(
        context: Context,
        source: String,
        reason: String,
        message: CharSequence,
        long: Boolean = false,
    ) {
        AppLogStore.append(
            context,
            source,
            "scan_result status=error reason=$reason"
        )
        Toast.makeText(
            context.applicationContext,
            message,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }

    fun noop(context: Context, source: String, reason: String, message: CharSequence) {
        AppLogStore.append(
            context,
            source,
            "scan_result status=noop reason=$reason"
        )
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
