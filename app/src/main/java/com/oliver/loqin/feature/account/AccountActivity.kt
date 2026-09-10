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

package com.oliver.loqin.feature.account

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.oliver.loqin.R
import com.oliver.loqin.feature.about.PrivacyReportActivity
import com.oliver.loqin.feature.inbox.BlockedInboxActivity
import com.oliver.loqin.feature.onboarding.OnboardingActivity
import com.oliver.loqin.feature.settings.AppLockSettingsActivity
import com.oliver.loqin.feature.tools.ManageKeysActivity
import com.oliver.loqin.feature.usage.LoqInOverviewActivity
import com.oliver.loqin.feature.usage.ScreenUnlocksActivity
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.LockedUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.util.ActivityTransitionCompat
import com.oliver.loqin.util.EditingLockGuard
import com.oliver.loqin.util.LoqInAppAccessGuard
import com.google.android.material.appbar.MaterialToolbar

/**
 * Account hub: account-level destinations (overview, keys, app lock,
 * onboarding, privacy, blocked notifications) plus the Info section.
 * Same card language as Settings.
 */
class AccountActivity : AppCompatActivity() {

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
            startActivity(LoqInOverviewActivity.intent(this))
        }
        findViewById<View>(R.id.cardAccountScreenUnlocks).setOnClickListener {
            startActivity(ScreenUnlocksActivity.intent(this))
        }
        findViewById<View>(R.id.cardAccountKeysCodes).setOnClickListener {
            startActivity(Intent(this, ManageKeysActivity::class.java))
        }
        findViewById<View>(R.id.cardAccountAppLock).setOnClickListener {
            startActivity(Intent(this, AppLockSettingsActivity::class.java))
        }
        findViewById<View>(R.id.cardAccountOnboarding).setOnClickListener {
            startActivity(
                Intent(this, OnboardingActivity::class.java)
                    .putExtra(OnboardingActivity.EXTRA_FORCE, true)
            )
        }
        findViewById<View>(R.id.cardAccountPrivacy).setOnClickListener {
            startActivity(Intent(this, PrivacyReportActivity::class.java))
        }
        findViewById<View>(R.id.cardAccountBlockedNotifications).setOnClickListener {
            // Same lock as Settings: warn via pill instead of opening while active.
            if (EditingLockGuard.isLocked(this)) {
                findViewById<View>(android.R.id.content)
                    .showWarnPill(R.string.edit_locked_manage_blocked_notifications)
                return@setOnClickListener
            }
            startActivity(Intent(this, BlockedInboxActivity::class.java))
        }
        syncLockedCardState()
    }

    override fun onResume() {
        super.onResume()
        syncLockedCardState()
    }

    private fun syncLockedCardState() {
        // Gray out (but keep tappable for the warning pill) while protection is active.
        findViewById<View>(R.id.cardAccountBlockedNotifications)?.alpha =
            if (EditingLockGuard.isLocked(this)) LockedUi.cardAlpha(this) else 1f
    }

    companion object {
        fun openWithAccessCheck(
            source: AppCompatActivity,
            finishSourceAfterOpen: Boolean = false,
        ): Boolean {
            // Same guard convention as Settings: locked editing keeps account
            // destinations (app lock, keys) out of reach — but unlike a bare
            // return, offer restricted open so the button never feels dead.
            if (!LoqInAppAccessGuard.isLocked(source)) {
                ActivityTransitionCompat.switchWithoutAnimation(
                    activity = source,
                    intent = Intent(source, AccountActivity::class.java),
                    finishCurrent = finishSourceAfterOpen,
                )
                return true
            }

            AlertDialog.Builder(source)
                .setTitle(R.string.loqin_account_locked_title)
                .setMessage(R.string.account_restricted_open_message)
                .setPositiveButton(R.string.account_open_restricted) { _, _ ->
                    ActivityTransitionCompat.switchWithoutAnimation(
                        activity = source,
                        intent = Intent(source, AccountActivity::class.java),
                        finishCurrent = finishSourceAfterOpen,
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .showAccented()
            return false
        }
    }
}
