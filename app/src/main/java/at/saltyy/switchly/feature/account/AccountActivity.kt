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

package at.saltyy.switchly.feature.account

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import at.saltyy.switchly.R
import at.saltyy.switchly.feature.about.PrivacyReportActivity
import at.saltyy.switchly.feature.settings.AppLockSettingsActivity
import at.saltyy.switchly.feature.settings.BackupFlowActions
import at.saltyy.switchly.feature.tools.ManageKeysActivity
import at.saltyy.switchly.feature.usage.SwitchlyOverviewActivity
import at.saltyy.switchly.theme.AccentColor
import at.saltyy.switchly.ui.EdgeToEdgeUtils
import at.saltyy.switchly.ui.ThemeUtils
import at.saltyy.switchly.ui.dialog.showAccented
import com.google.android.material.appbar.MaterialToolbar

/**
 * Account hub: account-level destinations (overview, keys, app lock,
 * onboarding, privacy) plus the Info section (app/device info, help,
 * support, changelog, disclaimer). Same card language as Settings.
 */
class AccountActivity : AppCompatActivity() {

    // Owns activity-result launchers: property init runs before onCreate, so
    // registration happens before STARTED. Same flows as Settings backup screen.
    private val backupFlows = BackupFlowActions(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))
        toolbar.setNavigationIcon(R.drawable.keyboard_arrow_left_24)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.title = getString(R.string.account_title)

        findViewById<View>(R.id.cardAccountOverview).setOnClickListener {
            startActivity(SwitchlyOverviewActivity.intent(this))
        }
        findViewById<View>(R.id.cardAccountKeysCodes).setOnClickListener {
            startActivity(Intent(this, ManageKeysActivity::class.java))
        }
        findViewById<View>(R.id.cardAccountAppLock).setOnClickListener {
            startActivity(Intent(this, AppLockSettingsActivity::class.java))
        }
        findViewById<View>(R.id.cardAccountOnboarding).setOnClickListener {
            startActivity(
                Intent(this, at.saltyy.switchly.feature.onboarding.OnboardingActivity::class.java)
                    .putExtra(at.saltyy.switchly.feature.onboarding.OnboardingActivity.EXTRA_FORCE, true)
            )
        }
        findViewById<View>(R.id.cardAccountPrivacy).setOnClickListener {
            startActivity(Intent(this, PrivacyReportActivity::class.java))
        }
        findViewById<View>(R.id.cardAccountBackup).setOnClickListener {
            backupFlows.showBackupOptions()
        }
        findViewById<View>(R.id.cardAccountDeleteData).setOnClickListener {
            backupFlows.confirmReset()
        }
    }

    companion object {
        fun openWithAccessCheck(
            source: AppCompatActivity,
            finishSourceAfterOpen: Boolean = false,
        ): Boolean {
            // Same guard convention as Settings: locked editing keeps account
            // destinations (app lock, keys) out of reach — but unlike a bare
            // return, offer restricted open so the button never feels dead.
            if (!at.saltyy.switchly.util.SwitchlyAppAccessGuard.isLocked(source)) {
                at.saltyy.switchly.util.ActivityTransitionCompat.switchWithoutAnimation(
                    activity = source,
                    intent = Intent(source, AccountActivity::class.java),
                    finishCurrent = finishSourceAfterOpen,
                )
                return true
            }

            androidx.appcompat.app.AlertDialog.Builder(source)
                .setTitle(at.saltyy.switchly.R.string.switchly_settings_locked_title)
                .setMessage(at.saltyy.switchly.R.string.settings_restricted_open_message)
                .setPositiveButton(at.saltyy.switchly.R.string.settings_open_restricted) { _, _ ->
                    at.saltyy.switchly.util.ActivityTransitionCompat.switchWithoutAnimation(
                        activity = source,
                        intent = Intent(source, AccountActivity::class.java),
                        finishCurrent = finishSourceAfterOpen,
                    )
                }
                .setNegativeButton(at.saltyy.switchly.R.string.cancel, null)
                .showAccented()
            return false
        }
    }
}
