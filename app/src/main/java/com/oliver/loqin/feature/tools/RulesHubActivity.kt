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

package com.oliver.loqin.feature.tools

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.oliver.loqin.R
import com.oliver.loqin.feature.picker.AppPickerActivity
import com.oliver.loqin.feature.profiles.ManageProfilesActivity
import com.oliver.loqin.feature.schedule.SchedulesActivity
import com.oliver.loqin.feature.settings.InAppRulesActivity
import com.oliver.loqin.feature.settings.ManageBlockedWebsitesActivity
import com.oliver.loqin.feature.settings.SettingsActivity
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.MainActivity
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.util.ActivityTransitionCompat
import com.oliver.loqin.util.LocaleHelper
import com.oliver.loqin.util.LoqInAppAccessGuard
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class RulesHubActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rules_hub)

        toolbar = findViewById(R.id.toolbar)
        bottomNav = findViewById(R.id.bottomNav)

        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar, bottomNav = bottomNav)
        EdgeToEdgeUtils.applyBottomNavGestureInset(bottomNav)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))

        setupCards()
        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_rules
    }

    private fun card(@IdRes id: Int): View = findViewById(id)

    private fun setupCards() {
        card(R.id.cardManageProfiles).setOnClickListener {
            startActivity(Intent(this, ManageProfilesActivity::class.java))
        }

        card(R.id.cardManageApps).setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }

        card(R.id.cardManageWebsites).setOnClickListener {
            startActivity(Intent(this, ManageBlockedWebsitesActivity::class.java))
        }

        card(R.id.cardInAppBlocking).setOnClickListener {
            startActivity(Intent(this, InAppRulesActivity::class.java))
        }

        card(R.id.cardSchedules).setOnClickListener {
            startActivity(Intent(this, SchedulesActivity::class.java))
        }
    }

    companion object {
        fun openWithAccessCheck(
            source: AppCompatActivity,
            finishSourceAfterOpen: Boolean = false,
        ): Boolean {
            if (!LoqInAppAccessGuard.isLocked(source)) {
                ActivityTransitionCompat.switchWithoutAnimation(
                    activity = source,
                    intent = Intent(source, RulesHubActivity::class.java),
                    finishCurrent = finishSourceAfterOpen,
                )
                return true
            }

            AlertDialog.Builder(source)
                .setTitle(R.string.loqin_rules_locked_title)
                .setMessage(R.string.rules_restricted_open_message)
                .setPositiveButton(R.string.rules_open_restricted) { _, _ ->
                    ActivityTransitionCompat.switchWithoutAnimation(
                        activity = source,
                        intent = Intent(source, RulesHubActivity::class.java),
                        finishCurrent = finishSourceAfterOpen,
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .showAccented()
            return false
        }
    }

    private fun setupBottomNav() {
        bottomNav.selectedItemId = R.id.nav_rules
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    ActivityTransitionCompat.switchWithoutAnimation(
                        activity = this,
                        intent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        },
                        finishCurrent = true,
                    )
                    true
                }

                R.id.nav_rules -> true

                R.id.nav_activity -> {
                    ActivityTransitionCompat.switchWithoutAnimation(
                        activity = this,
                        intent = Intent(this, ActivityHubActivity::class.java),
                        finishCurrent = true,
                    )
                    true
                }

                R.id.nav_settings -> {
                    SettingsActivity.openWithAccessCheck(
                        source = this,
                        finishSourceAfterOpen = true
                    )
                }

                else -> false
            }
        }
    }
}
