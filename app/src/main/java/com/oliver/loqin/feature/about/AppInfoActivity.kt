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

package com.oliver.loqin.feature.about

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import com.oliver.loqin.BuildConfig
import com.oliver.loqin.R
import com.oliver.loqin.ui.showWarnPillOnContent
import com.oliver.loqin.util.AndroidSystemPackages
import com.oliver.loqin.data.prefs.AdvancedModeStore
import com.oliver.loqin.util.PlayStoreUpdatePrompt
import java.text.DateFormat
import java.util.Date

class AppInfoActivity : TilesInfoActivity() {

    override fun screenTitle(): String = getString(R.string.about_app_info_title)

    override fun tiles(): List<Tile> {
        val appName = getString(R.string.app_name)
        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE.toString()
        val pkg = BuildConfig.APPLICATION_ID

        val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val pi = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val firstInstall = pi?.firstInstallTime?.takeIf { it > 0L }?.let { df.format(Date(it)) } ?: "-"
        val lastUpdate = pi?.lastUpdateTime?.takeIf { it > 0L }?.let { df.format(Date(it)) } ?: "-"

        val installerPackage = resolveInstallerPackageName()
        val installer = formatInstallerLabel(installerPackage)

        val buildType = if (BuildConfig.DEBUG) "Debug" else "Release"

        return listOf(
            Tile(
                getString(R.string.about_app_name_label),
                appName,
                sectionTitle = getString(R.string.about_section_app),
                iconRes = R.drawable.apps_24
            ),
            Tile(
                getString(R.string.about_version_label),
                "$versionName ($versionCode)",
                sectionTitle = getString(R.string.about_section_app),
                onClick = { PlayStoreUpdatePrompt.promptNow(this) },
                onLongClick = {
                    unlockAndOpenDeveloperMode()
                    true
                },
                enableLongPressCopy = false,
                showOpenButton = true,
                iconRes = R.drawable.cloud_download_24,
                actionIconRes = R.drawable.cloud_download_24
            ),
            Tile(
                getString(R.string.about_build_type_label),
                buildType,
                sectionTitle = getString(R.string.about_section_build),
                iconRes = R.drawable.tune_24
            ),
            Tile(
                getString(R.string.about_package_label),
                pkg,
                sectionTitle = getString(R.string.about_section_build),
                onClick = {
                    runCatching {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:$pkg".toUri()
                            }
                        )
                    }
                },
                showOpenButton = true,
                iconRes = R.drawable.info_24
            ),
            Tile(
                getString(R.string.about_install_source_label),
                installer,
                sectionTitle = getString(R.string.about_section_install),
                showCopyButton = true,
                iconRes = R.drawable.cloud_download_24,
                copiedToast = getString(R.string.copied)
            ),
            Tile(
                getString(R.string.about_first_install_label),
                firstInstall,
                sectionTitle = getString(R.string.about_section_install),
                iconRes = R.drawable.schedule_24
            ),
            Tile(
                getString(R.string.about_last_update_label),
                lastUpdate,
                sectionTitle = getString(R.string.about_section_install),
                iconRes = R.drawable.schedule_24
            ),
            Tile(
                getString(R.string.disclaimer_title),
                getString(R.string.disclaimer_summary),
                sectionTitle = getString(R.string.about_section_legal),
                onClick = { startActivity(Intent(this, DisclaimerActivity::class.java)) },
                showOpenButton = true,
                enableLongPressCopy = false,
                iconRes = R.drawable.info_24
            ),
        )
    }

    private fun unlockAndOpenDeveloperMode() {
        val wasEnabled = AdvancedModeStore.isEnabled(this)
        if (!wasEnabled) {
            AdvancedModeStore.setEnabled(this, true)
            showWarnPillOnContent(getString(R.string.developer_mode_unlocked_toast))
        }
        openDeveloperMode()
    }

    private fun openDeveloperMode() {
        startActivity(Intent(this, AdvancedModeActivity::class.java))
    }

    private fun resolveInstallerPackageName(): String? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                packageManager.javaClass
                    .getMethod("getInstallerPackageName", String::class.java)
                    .invoke(packageManager, packageName) as? String
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun formatInstallerLabel(installerPackage: String?): String {
        if (installerPackage.isNullOrBlank()) {
            return getString(R.string.about_install_source_unknown)
        }

        val known = mapOf(
            AndroidSystemPackages.PLAY_STORE to getString(R.string.about_install_source_google_play),
            "com.google.android.feedback" to getString(R.string.about_install_source_google_play),
            "com.amazon.venezia" to getString(R.string.about_install_source_amazon),
            "org.fdroid.fdroid" to "F-Droid",
            "com.sec.android.app.samsungapps" to getString(R.string.about_install_source_galaxy_store),
            "com.huawei.appmarket" to "Huawei AppGallery",
            "com.xiaomi.mipicks" to "Xiaomi GetApps",
            AndroidSystemPackages.ANDROID_PACKAGE_INSTALLER to getString(R.string.about_install_source_package_installer),
            AndroidSystemPackages.GOOGLE_PACKAGE_INSTALLER to getString(R.string.about_install_source_package_installer),
            AndroidSystemPackages.MIUI_PACKAGE_INSTALLER to getString(R.string.about_install_source_package_installer)
        )
        known[installerPackage]?.let { return it }

        val label = runCatching {
            val appInfo = packageManager.getApplicationInfo(installerPackage, 0)
            packageManager.getApplicationLabel(appInfo).toString().trim()
        }.getOrNull()

        return label?.takeIf { it.isNotBlank() } ?: installerPackage
    }
}
