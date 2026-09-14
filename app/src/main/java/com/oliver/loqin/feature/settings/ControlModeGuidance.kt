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

package com.oliver.loqin.feature.settings

import android.app.Activity
import android.content.Intent
import androidx.annotation.StringRes
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.AppLogStore
import com.oliver.loqin.ui.dialog.showAccented
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Turns a dead-end "this channel is disabled" message into a guided route back to Control modes.
 * It never changes protection by itself; protected control settings keep their normal guards.
 */
object ControlModeGuidance {

    fun show(
        activity: Activity,
        source: String,
        @StringRes blockedMessageRes: Int,
        finishOnDismiss: Boolean = false,
    ) {
        AppLogStore.append(
            activity,
            source,
            "scan_result status=error reason=control_mode_blocked guidance=shown",
        )

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.control_mode_blocked_guidance_title)
            .setMessage(
                activity.getString(
                    R.string.control_mode_blocked_guidance_message,
                    activity.getString(blockedMessageRes),
                )
            )
            .setNegativeButton(R.string.close) { _, _ ->
                if (finishOnDismiss) activity.finish()
            }
            .setPositiveButton(R.string.control_mode_blocked_open_controls) { _, _ ->
                activity.startActivity(Intent(activity, BlockingModesActivity::class.java))
                if (finishOnDismiss) activity.finish()
            }
            .setOnCancelListener {
                if (finishOnDismiss) activity.finish()
            }
            .showAccented()
    }
}
