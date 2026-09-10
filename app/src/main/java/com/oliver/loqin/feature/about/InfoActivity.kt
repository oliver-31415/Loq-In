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
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.oliver.loqin.R
import com.oliver.loqin.feature.faq.FaqActivity
import com.oliver.loqin.feature.support.SupportLogActivity
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.ThemeUtils
import com.google.android.material.appbar.MaterialToolbar

/**
 * Info hub: app/device info, help center, support, changelog and disclaimer
 * as individual cards. Same card language as Settings/Account.
 */
class InfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.title = getString(R.string.settings_section_info)

        findViewById<View>(R.id.cardInfoApp).setOnClickListener {
            startActivity(Intent(this, AppInfoActivity::class.java))
        }
        findViewById<View>(R.id.cardInfoDevice).setOnClickListener {
            startActivity(Intent(this, DeviceInfoActivity::class.java))
        }
        findViewById<View>(R.id.cardInfoHelp).setOnClickListener {
            startActivity(Intent(this, FaqActivity::class.java))
        }
        findViewById<View>(R.id.cardInfoSupport).setOnClickListener {
            startActivity(Intent(this, SupportLogActivity::class.java))
        }
        findViewById<View>(R.id.cardInfoWhatsNew).setOnClickListener {
            startActivity(Intent(this, WhatsNewActivity::class.java))
        }
        findViewById<View>(R.id.cardInfoDisclaimer).setOnClickListener {
            startActivity(Intent(this, DisclaimerActivity::class.java))
        }
    }
}
