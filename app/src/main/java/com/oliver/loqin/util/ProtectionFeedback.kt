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
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import com.oliver.loqin.R
import com.oliver.loqin.ui.showWarnPill

/** Shared protection feedback: bottom pills, never full-screen dialogs. */
object ProtectionFeedback {

    fun showQueued(context: Context, message: CharSequence? = null) {
        showPill(context, message ?: context.getString(R.string.protection_change_queued))
    }

    fun showInfo(context: Context, @StringRes titleRes: Int, @StringRes messageRes: Int) {
        showPill(context, context.getString(messageRes))
    }

    private fun showPill(context: Context, message: CharSequence) {
        val activity = context as? Activity
        val anchor = activity?.findViewById<View>(android.R.id.content)
        if (anchor != null) {
            anchor.showWarnPill(message)
        } else {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
