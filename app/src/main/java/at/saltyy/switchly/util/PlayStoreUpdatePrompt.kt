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

package at.saltyy.switchly.util

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import at.saltyy.switchly.BuildConfig
import at.saltyy.switchly.R
import at.saltyy.switchly.data.prefs.AppPreferences
import at.saltyy.switchly.ui.dialog.showAccented
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Play Store update prompt with optional release context from release.saltyy.at. 
 * Remote metadata is an enhancement only; Play remains the source of truth for whether an update is actually available.
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

                        val details = UpdateReleaseInfo.resolve(
                            context = activity.applicationContext,
                            currentVersionName = BuildConfig.VERSION_NAME,
                            targetVersionCode = info.availableVersionCode().toLong(),
                        )
                        showUpdateDialog(activity, details)
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
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.switchly_update_up_to_date),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@addOnSuccessListener
                    }

                    val owner = activity as? LifecycleOwner
                    if (owner == null) {
                        showUpdateDialog(activity, null)
                        return@addOnSuccessListener
                    }

                    owner.lifecycleScope.launch {
                        val details = UpdateReleaseInfo.resolve(
                            context = activity.applicationContext,
                            currentVersionName = BuildConfig.VERSION_NAME,
                            targetVersionCode = info.availableVersionCode().toLong(),
                        )
                        showUpdateDialog(activity, details)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.switchly_update_check_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
        }.onFailure {
            Toast.makeText(
                activity,
                activity.getString(R.string.switchly_update_check_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun isUsableUpdate(info: AppUpdateInfo): Boolean {
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return false
        return info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ||
            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
    }

    private fun showUpdateDialog(activity: Activity, details: UpdateReleaseDetails?) {
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(updateTitle(activity, details))
            .setMessage(updateMessage(activity, details))
            .setPositiveButton(activity.getString(R.string.update_available_cta)) { _, _ ->
                openPlayStore(activity)
            }
            .setNegativeButton(activity.getString(R.string.not_now), null)

        if (details != null && details.allChanges.isNotEmpty()) {
            builder.setNeutralButton(activity.getString(R.string.update_whats_new)) { _, _ ->
                showReleaseDetailsDialog(activity, details)
            }
        }

        builder.showAccented()
    }

    private fun updateTitle(activity: Activity, details: UpdateReleaseDetails?): String {
        if (details == null) return activity.getString(R.string.update_available_title)
        if (details.breaking) return activity.getString(R.string.update_breaking_title)
        return when (details.impact) {
            UpdateImpact.MAINTENANCE -> activity.getString(R.string.update_maintenance_title)
            UpdateImpact.FEATURE -> activity.getString(R.string.update_feature_title)
            UpdateImpact.MAJOR -> activity.getString(R.string.update_major_title)
        }
    }

    private fun updateMessage(activity: Activity, details: UpdateReleaseDetails?): String {
        if (details == null) return activity.getString(R.string.update_available_message)

        val intro = when {
            details.breaking -> activity.getString(R.string.update_breaking_intro)
            details.impact == UpdateImpact.MAJOR -> activity.getString(R.string.update_major_intro)
            details.impact == UpdateImpact.FEATURE -> activity.getString(R.string.update_feature_intro)
            else -> activity.getString(R.string.update_maintenance_intro)
        }

        val body = StringBuilder()
            .append(activity.getString(
                R.string.update_version_transition_fmt,
                BuildConfig.VERSION_NAME,
                details.targetVersion,
            ))
            .append("\n\n")
            .append(intro)

        if (details.highlights.isNotEmpty()) {
            body.append("\n\n")
                .append(activity.getString(R.string.update_highlights_title))
                .append('\n')
            details.highlights.take(4).forEach { line ->
                body.append("• ").append(line).append('\n')
            }
        }

        return body.toString().trim()
    }

    private fun showReleaseDetailsDialog(activity: Activity, details: UpdateReleaseDetails) {
        val message = buildString {
            append(activity.getString(
                R.string.update_changes_since_fmt,
                BuildConfig.VERSION_NAME,
            ))
            append("\n\n")
            details.allChanges.take(16).forEach { line ->
                append("• ").append(line).append('\n')
            }
        }.trim()

        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.update_whats_new_for_fmt, details.targetVersion))
            .setMessage(message)
            .setPositiveButton(activity.getString(R.string.update_available_cta)) { _, _ ->
                openPlayStore(activity)
            }
            .setNeutralButton(activity.getString(R.string.update_release_timeline)) { _, _ ->
                openReleaseTimeline(activity)
            }
            .setNegativeButton(activity.getString(R.string.close), null)
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

    private fun openReleaseTimeline(activity: Activity) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, "https://release.saltyy.at/".toUri()))
    }
}
