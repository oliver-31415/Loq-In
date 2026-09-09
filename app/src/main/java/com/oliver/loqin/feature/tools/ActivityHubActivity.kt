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

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.oliver.loqin.R
import com.oliver.loqin.feature.usage.AppWebsiteUsageActivity
import com.oliver.loqin.feature.usage.ScreenUnlocksActivity
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.ThemeUtils
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
    }

}
