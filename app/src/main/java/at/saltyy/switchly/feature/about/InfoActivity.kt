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

package at.saltyy.switchly.feature.about

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import at.saltyy.switchly.R
import at.saltyy.switchly.feature.faq.FaqActivity
import at.saltyy.switchly.feature.support.SupportLogActivity
import at.saltyy.switchly.theme.AccentColor
import at.saltyy.switchly.ui.EdgeToEdgeUtils
import at.saltyy.switchly.ui.ThemeUtils
import at.saltyy.switchly.ui.dialog.SwitchlyInfoRow
import at.saltyy.switchly.ui.dialog.showSwitchlyInfoDialog
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
            showSwitchlyInfoDialog(
                title = getString(R.string.account_disclaimer_title),
                rows = listOf(
                    SwitchlyInfoRow(
                        label = "",
                        value = getString(R.string.account_disclaimer_body)
                    )
                )
            )
        }
    }
}
