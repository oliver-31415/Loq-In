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

package at.saltyy.switchly.feature.tools

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import at.saltyy.switchly.R
import at.saltyy.switchly.feature.inbox.BlockedInboxActivity
import at.saltyy.switchly.feature.usage.AppWebsiteUsageActivity
import at.saltyy.switchly.feature.usage.ScreenUnlocksActivity
import at.saltyy.switchly.theme.AccentColor
import at.saltyy.switchly.ui.EdgeToEdgeUtils
import at.saltyy.switchly.ui.ThemeUtils
import com.google.android.material.appbar.MaterialToolbar

class ActivityHubActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hub)

        setupViews()
        setupToolbar()
        tintActivityIcons()
        setupActivityCardActions()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
    }

    private fun setupToolbar() {
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        toolbar.setNavigationIcon(R.drawable.keyboard_arrow_left_24)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))
        toolbar.title = getString(R.string.nav_activity)
        supportActionBar?.title = getString(R.string.nav_activity)
    }

    private fun tintActivityIcons() {
        val iconTint = ColorStateList.valueOf(AccentColor.getAccentColorInt(this))

        listOf(
            R.id.ivAppWebsiteUsageIcon,
            R.id.ivScreenUnlocksIcon,
            R.id.ivBlockedNotificationsIcon,
        ).forEach { iconId ->
            findViewById<ImageView>(iconId)?.let { icon ->
                icon.imageTintList = iconTint
                icon.setColorFilter(iconTint.defaultColor)
                icon.isEnabled = true
                icon.alpha = 1f
            }
        }
    }

    private fun setupActivityCardActions() {
        findViewById<View>(R.id.cardAppWebsiteUsage).setOnClickListener {
            startActivity(Intent(this, AppWebsiteUsageActivity::class.java))
        }
        findViewById<View>(R.id.cardScreenUnlocks).setOnClickListener {
            startActivity(ScreenUnlocksActivity.intent(this))
        }
        findViewById<View>(R.id.cardBlockedNotifications).setOnClickListener {
            startActivity(Intent(this, BlockedInboxActivity::class.java))
        }
    }

}
