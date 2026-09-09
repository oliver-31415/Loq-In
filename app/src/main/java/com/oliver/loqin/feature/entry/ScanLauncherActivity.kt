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

package com.oliver.loqin.feature.entry

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.pm.ShortcutManagerCompat
import com.oliver.loqin.data.prefs.AppLogStore
import com.oliver.loqin.feature.inbox.BlockedInboxActivity
import com.oliver.loqin.feature.scan.UnifiedScanActivity
import com.oliver.loqin.nfc.NfcWriterActivity
import com.oliver.loqin.ui.MainActivity
import com.oliver.loqin.util.ActivityTransitionCompat
import com.oliver.loqin.widget.QuickActionReceiver

/**
 * Lightweight exported trampoline for launcher shortcuts, widgets and Quick Settings tiles.
 * Keeps internal activities non-exported while still allowing the launcher and system UI to trigger selected quick actions.
 */
class ScanLauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun reportShortcutUsage(intent: Intent?) {
        val shortcutId = shortcutIdFrom(intent) ?: return
        ShortcutManagerCompat.reportShortcutUsed(this, shortcutId)
    }

    private fun shortcutIdFrom(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != SHORTCUT_SCHEME || data.host != SHORTCUT_HOST) {
            return null
        }
        return data.lastPathSegment?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun isTrustedFocusNowIntent(intent: Intent?): Boolean {
        // Only allow Focus Now from LoqIn-created launcher shortcuts/widgets.
        // The activity is exported as a trampoline for system surfaces, so do not let arbitrary explicit external intents trigger focus mode by action string alone.
        return shortcutIdFrom(intent) == SHORTCUT_FOCUS_NOW_ID
    }

    private fun handleIntent(intent: Intent?) {
        reportShortcutUsage(intent)
        AppLogStore.append(
            this,
            "Shortcut",
            "Launcher action=${intent?.action.orEmpty()} id=${shortcutIdFrom(intent).orEmpty()}",
        )

        when (intent?.action) {
            ACTION_FOCUS_NOW -> {
                if (isTrustedFocusNowIntent(intent)) {
                    QuickActionReceiver.handleFocusNow(this)
                }
                finishAndNoAnim()
                return
            }
        }

        val launchIntent = when (intent?.action) {
            ACTION_OPEN_QR_SCAN -> Intent(this, UnifiedScanActivity::class.java)
                .putExtra(UnifiedScanActivity.EXTRA_ALLOW_DIRECT_OPEN, true)
                .putExtra(UnifiedScanActivity.EXTRA_SCAN_MODE, UnifiedScanActivity.ScanMode.QR_ONLY.raw)

            ACTION_OPEN_BARCODE_SCAN -> Intent(this, UnifiedScanActivity::class.java)
                .putExtra(UnifiedScanActivity.EXTRA_ALLOW_DIRECT_OPEN, true)
                .putExtra(UnifiedScanActivity.EXTRA_SCAN_MODE, UnifiedScanActivity.ScanMode.BARCODE_ONLY.raw)

            ACTION_OPEN_NFC_WRITE -> Intent(this, NfcWriterActivity::class.java)
            ACTION_OPEN_BLOCKED_NOTIFICATIONS -> Intent(this, BlockedInboxActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching {
            startActivity(launchIntent)
            ActivityTransitionCompat.startWithoutAnimation(this)
        }.onFailure { error ->
            AppLogStore.append(
                this,
                "Shortcut",
                "Launch failed action=${intent?.action.orEmpty()} error=${error.javaClass.simpleName}",
            )
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            ActivityTransitionCompat.startWithoutAnimation(this)
        }
        finishAndNoAnim()
    }

    private fun finishAndNoAnim() {
        finish()
        ActivityTransitionCompat.finishWithoutAnimation(this)
    }

    companion object {
        private const val SHORTCUT_SCHEME = "loqin"
        private const val SHORTCUT_HOST = "shortcut"
        private const val SHORTCUT_FOCUS_NOW_ID = "quick_focus_now"

        const val ACTION_OPEN_QR_SCAN = "com.oliver.loqin.action.OPEN_QR_SCAN"
        const val ACTION_OPEN_BARCODE_SCAN = "com.oliver.loqin.action.OPEN_BARCODE_SCAN"
        const val ACTION_OPEN_NFC_WRITE = "com.oliver.loqin.action.OPEN_NFC_WRITE"
        const val ACTION_OPEN_BLOCKED_NOTIFICATIONS = "com.oliver.loqin.action.OPEN_BLOCKED_NOTIFICATIONS"
        const val ACTION_FOCUS_NOW = "com.oliver.loqin.action.FOCUS_NOW"
    }
}
