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

package com.oliver.loqin.feature.onboarding

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.oliver.loqin.R
import com.oliver.loqin.ui.showWarnPillOnContent

// Helper for requesting LoqIn Quick Settings tiles.
object QuickTileHelper {

    /**
     * Android 13+ (API 33):
     * Uses the official system dialog to request adding a Quick Settings tile.
     * Returns true if the request could be started successfully.
     */
    fun requestAddTileIfAvailable(activity: Activity, onResult: ((Int) -> Unit)? = null): Boolean {
        return requestAddTileIfAvailable(
            activity = activity,
            serviceClassName = "com.oliver.loqin.platform.tile.LoqInTileService",
            label = activity.getString(R.string.app_name),
            iconRes = R.drawable.qs_loqin_24,
            onResult = onResult
        )
    }

    fun requestAddQrScanTileIfAvailable(activity: Activity, onResult: ((Int) -> Unit)? = null): Boolean {
        return requestAddTileIfAvailable(
            activity = activity,
            serviceClassName = "com.oliver.loqin.platform.tile.QrScanTileService",
            label = activity.getString(R.string.qr_scan_title),
            iconRes = R.drawable.qs_qr_24,
            onResult = onResult
        )
    }

    fun requestAddBarcodeScanTileIfAvailable(activity: Activity, onResult: ((Int) -> Unit)? = null): Boolean {
        return requestAddTileIfAvailable(
            activity = activity,
            serviceClassName = "com.oliver.loqin.platform.tile.BarcodeScanTileService",
            label = activity.getString(R.string.barcode_scan_title),
            iconRes = R.drawable.qs_barcode_24,
            onResult = onResult
        )
    }

    private fun requestAddTileIfAvailable(
        activity: Activity,
        serviceClassName: String,
        label: String,
        iconRes: Int,
        onResult: ((Int) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < 33) {
            return false
        }
        val sb = activity.getSystemService(StatusBarManager::class.java) ?: return false

        val component = ComponentName(activity, serviceClassName)
        val icon = Icon.createWithResource(activity, iconRes)
        val mainExecutor = ContextCompat.getMainExecutor(activity)

        sb.requestAddTileService(component, label, icon, mainExecutor) { result ->
            onResult?.invoke(result)
            when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                    activity.showWarnPillOnContent(activity.getString(R.string.qs_added_ok))

                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                    activity.showWarnPillOnContent(activity.getString(R.string.qs_added_already))

                else ->
                    // Canceled or any other status
                    activity.showWarnPillOnContent(activity.getString(R.string.qs_added_cancel))
            }
        }

        return true
    }
}
