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
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.AppPreferences
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.ui.showWarnPillOnContent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Play Store update prompt. Play remains the source of truth for whether an update is available.
 */
object PlayStoreUpdatePrompt {

    fun check(activity: Activity) {
        runCatching {
            val manager = AppUpdateManagerFactory.create(activity)
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    if (!isUsableUpdate(info)) return@addOnSuccessListener

                    val owner = activity as? LifecycleOwner ?: return@addOnSuccessListener
                    val currentVersionCode = activity.packageManager
                        .getPackageInfo(activity.packageName, 0)
                        .let(PackageInfoCompat::getLongVersionCode)

                    owner.lifecycleScope.launch {
                        val prefs = AppPreferences(activity.applicationContext)
                        if (prefs.lastUpdatePromptedVersionCode.first() == currentVersionCode) return@launch

                        showUpdateDialog(activity)
                        prefs.setLastUpdatePromptedVersionCode(currentVersionCode)
                    }
                }
                .addOnFailureListener {
                    // Play update availability is optional and must never block startup.
                }
        }
    }

    fun checkAvailability(activity: Activity, onResult: (available: Boolean) -> Unit) {
        runCatching {
            val manager = AppUpdateManagerFactory.create(activity)
            manager.appUpdateInfo
                .addOnSuccessListener { info -> onResult(isUsableUpdate(info)) }
                .addOnFailureListener { onResult(false) }
        }.onFailure {
            onResult(false)
        }
    }

    fun promptNow(activity: Activity) {
        runCatching {
            val manager = AppUpdateManagerFactory.create(activity)
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    if (!isUsableUpdate(info)) {
                        activity.showWarnPillOnContent(
                            activity.getString(R.string.loqin_update_up_to_date)
                        )
                        return@addOnSuccessListener
                    }

                    val owner = activity as? LifecycleOwner
                    if (owner == null) {
                        showUpdateDialog(activity)
                        return@addOnSuccessListener
                    }

                    showUpdateDialog(activity)
                }
                .addOnFailureListener {
                    activity.showWarnPillOnContent(
                        activity.getString(R.string.loqin_update_check_failed)
                    )
                }
        }.onFailure {
            activity.showWarnPillOnContent(
                activity.getString(R.string.loqin_update_check_failed)
            )
        }
    }

    private fun isUsableUpdate(info: AppUpdateInfo): Boolean {
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return false
        return info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ||
            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
    }

    private fun showUpdateDialog(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.update_available_title))
            .setMessage(activity.getString(R.string.update_available_message))
            .setPositiveButton(activity.getString(R.string.update_available_cta)) { _, _ ->
                openPlayStore(activity)
            }
            .setNegativeButton(activity.getString(R.string.not_now), null)
            .showAccented()
    }

    private fun openPlayStore(activity: Activity) {
        val pkg = activity.packageName
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri())
        val web = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pkg".toUri())

        if (market.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(market)
        } else {
            activity.startActivity(web)
        }
    }
}
