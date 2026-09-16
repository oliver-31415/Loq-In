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

package com.oliver.loqin.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.AutomationModeStore
import com.oliver.loqin.data.prefs.AutostartStore
import com.oliver.loqin.data.prefs.BlockingToggleKeys
import com.oliver.loqin.data.prefs.EmergencyBypassStore
import com.oliver.loqin.data.prefs.NotificationBlockStore
import com.oliver.loqin.data.prefs.SessionMissedNotificationsStore
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.applyLoqInStyle
import com.oliver.loqin.ui.dialog.EmergencyPinDialog
import com.oliver.loqin.ui.dialog.LoqInDialogOption
import com.oliver.loqin.ui.dialog.applyLoqInDialogCorners
import com.oliver.loqin.ui.dialog.showLoqInOptionDialog
import com.oliver.loqin.ui.dialog.styleLoqInDialogButtons
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.util.EditingLockGuard
import com.oliver.loqin.util.LocaleHelper
import com.oliver.loqin.util.LoqInAppAccessGuard
import com.oliver.loqin.util.PersistentStatusNotifier
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Feature access screen.
 *
 * A compact, self-contained screen: flat section headers over grouped
 * [com.google.android.material.card.MaterialCardView] cards of toggle rows,
 * matching the Settings screen's card language. It intentionally no longer
 * reuses the shared ToggleOptions layout, which stacked oversized section
 * blocks and duplicated the "Feature access while blocking" header.
 *
 * Behaviour mirrors the previous SECTION_OTHER implementation: access-while-
 * active exceptions, NFC pairing, notification/safety options and the status
 * notification, all gated by the same Loq In lock guard.
 */
class BlockingFeaturesActivity : AppCompatActivity() {

    private val accentSwitches = mutableListOf<SwitchMaterial>()
    private var updatingUi = false

    private lateinit var switchMixedAllowAppPicking: SwitchMaterial
    private lateinit var switchMixedAllowProfileSwitching: SwitchMaterial
    private lateinit var switchMixedAllowNfcTagWriting: SwitchMaterial
    private lateinit var switchEnablePairedUids: SwitchMaterial
    private lateinit var switchAutoPairOnWrite: SwitchMaterial
    private lateinit var switchBlockNotifications: SwitchMaterial
    private lateinit var switchSessionMissedNotifications: SwitchMaterial
    private lateinit var switchLockWarnings: SwitchMaterial
    private lateinit var switchAutostart: SwitchMaterial
    private lateinit var switchEmergency: SwitchMaterial
    private lateinit var switchPersistentStatusNotification: SwitchMaterial

    private lateinit var rowMixedAllowNfcTagWriting: View
    private lateinit var rowSessionMissedNotifications: View
    private lateinit var rowChangeEmergencyPin: View
    private lateinit var rowPersistentStatusNotificationMode: View
    private lateinit var tvPersistentStatusNotificationModeValue: TextView

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        if (LoqInAppAccessGuard.blockIfLocked(this)) {
            return
        }
        setContentView(R.layout.activity_blocking_features)
        CustomAccentApplier.applyIfNeeded(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))

        val ctx = this
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        SwitchModeStore.ensureInit(this)

        // Require-NFC-to-disable stays off from this screen (legacy behaviour).
        SwitchModeStore.setNfcRequiredForDisable(ctx, false)

        bindViews()
        applyInitialState(sp)
        wireInfoButtons()
        wireRowClicks()

        applySwitchAccentTints()
        refreshPageState()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SwitchModeStore.enabledFlow.collect {
                    runOnUiThread { refreshPageState() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (LoqInAppAccessGuard.blockIfLocked(this)) {
            return
        }
        refreshPageState()
        CustomAccentApplier.applyIfNeeded(this)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ---------------------------------------------------------------------
    // Binding
    // ---------------------------------------------------------------------

    private fun bindViews() {
        switchMixedAllowAppPicking = findViewById(R.id.switchMixedAllowAppPicking)
        switchMixedAllowProfileSwitching = findViewById(R.id.switchMixedAllowProfileSwitching)
        switchMixedAllowNfcTagWriting = findViewById(R.id.switchMixedAllowNfcTagWriting)
        switchEnablePairedUids = findViewById(R.id.switchEnablePairedUids)
        switchAutoPairOnWrite = findViewById(R.id.switchAutoPairOnWrite)
        switchBlockNotifications = findViewById(R.id.switchBlockNotifications)
        switchSessionMissedNotifications = findViewById(R.id.switchSessionMissedNotifications)
        switchLockWarnings = findViewById(R.id.switchLockWarnings)
        switchAutostart = findViewById(R.id.switchAutostart)
        switchEmergency = findViewById(R.id.switchEmergency)
        switchPersistentStatusNotification = findViewById(R.id.switchPersistentStatusNotification)

        rowMixedAllowNfcTagWriting = findViewById(R.id.rowMixedAllowNfcTagWriting)
        rowSessionMissedNotifications = findViewById(R.id.rowSessionMissedNotifications)
        rowChangeEmergencyPin = findViewById(R.id.rowChangeEmergencyPin)
        rowPersistentStatusNotificationMode = findViewById(R.id.rowPersistentStatusNotificationMode)
        tvPersistentStatusNotificationModeValue = findViewById(R.id.tvPersistentStatusNotificationModeValue)

        accentSwitches.clear()
        accentSwitches += listOf(
            switchMixedAllowAppPicking,
            switchMixedAllowProfileSwitching,
            switchMixedAllowNfcTagWriting,
            switchEnablePairedUids,
            switchAutoPairOnWrite,
            switchBlockNotifications,
            switchSessionMissedNotifications,
            switchLockWarnings,
            switchAutostart,
            switchEmergency,
            switchPersistentStatusNotification,
        )
    }

    private fun applyInitialState(sp: android.content.SharedPreferences) {
        val ctx = this
        val nfcSupported = AutomationModeStore.isNfcSupported(ctx)

        switchMixedAllowAppPicking.isChecked = AutomationModeStore.isMixedAllowAppPicking(ctx)
        switchMixedAllowProfileSwitching.isChecked = AutomationModeStore.isMixedAllowProfileSwitching(ctx)
        switchMixedAllowNfcTagWriting.isChecked = AutomationModeStore.isMixedAllowNfcTagWriting(ctx)
        switchEnablePairedUids.isChecked =
            nfcSupported && sp.getBoolean(BlockingToggleKeys.KEY_ENABLE_PAIRED_UIDS, false)
        switchAutoPairOnWrite.isChecked =
            nfcSupported && sp.getBoolean(BlockingToggleKeys.KEY_AUTO_PAIR_ON_WRITE, false)

        switchBlockNotifications.isChecked = NotificationBlockStore.isEnabled(ctx)
        switchSessionMissedNotifications.isChecked = SessionMissedNotificationsStore.isFeatureEnabled(ctx)
        switchLockWarnings.isChecked = !EditingLockGuard.isSuppressed(ctx)
        switchAutostart.isChecked = AutostartStore.isEnabled(ctx)
        switchEmergency.isChecked = EmergencyBypassStore.isFeatureEnabled(ctx)
        switchPersistentStatusNotification.isChecked = PersistentStatusNotifier.isEnabled(ctx)

        bindAccessSwitch(switchMixedAllowAppPicking) {
            AutomationModeStore.setMixedAllowAppPicking(ctx, it)
        }
        bindAccessSwitch(switchMixedAllowProfileSwitching) {
            AutomationModeStore.setMixedAllowProfileSwitching(ctx, it)
        }
        bindAccessSwitch(switchMixedAllowNfcTagWriting) {
            AutomationModeStore.setMixedAllowNfcTagWriting(ctx, it)
        }
        bindAccessSwitch(switchAutostart) { AutostartStore.setEnabled(ctx, it) }
        bindAccessSwitch(switchEmergency) {
            EmergencyBypassStore.setFeatureEnabled(ctx, it)
            refreshPageState()
        }

        switchEnablePairedUids.setOnCheckedChangeListener { buttonView, isChecked ->
            if (updatingUi) return@setOnCheckedChangeListener
            val supported = AutomationModeStore.isNfcSupported(ctx)
            if (isChecked && !supported) {
                revertSwitch(buttonView, false)
                return@setOnCheckedChangeListener
            }
            sp.edit { putBoolean(BlockingToggleKeys.KEY_ENABLE_PAIRED_UIDS, isChecked && supported) }
        }
        switchAutoPairOnWrite.setOnCheckedChangeListener { buttonView, isChecked ->
            if (updatingUi) return@setOnCheckedChangeListener
            val supported = AutomationModeStore.isNfcSupported(ctx)
            if (isChecked && !supported) {
                revertSwitch(buttonView, false)
                return@setOnCheckedChangeListener
            }
            sp.edit { putBoolean(BlockingToggleKeys.KEY_AUTO_PAIR_ON_WRITE, isChecked && supported) }
        }

        switchBlockNotifications.setOnCheckedChangeListener { _, isChecked ->
            NotificationBlockStore.setEnabled(ctx, isChecked)
            if (isChecked && !NotificationBlockStore.hasListenerAccess(ctx)) {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    getString(R.string.toast_notification_listener_required),
                    Snackbar.LENGTH_LONG
                )
                    .setAction(R.string.permissions_btn_permissions) {
                        runCatching {
                            startActivity(Intent(ctx, PermissionsActivity::class.java))
                        }.onFailure {
                            runCatching {
                                startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        }
                    }
                    .applyLoqInStyle()
                    .show()
            }
            refreshPageState()
        }
        switchSessionMissedNotifications.setOnCheckedChangeListener { _, isChecked ->
            SessionMissedNotificationsStore.setFeatureEnabled(ctx, isChecked)
        }
        switchLockWarnings.setOnCheckedChangeListener { _, isChecked ->
            EditingLockGuard.setSuppressed(ctx, !isChecked)
        }

        switchPersistentStatusNotification.setOnCheckedChangeListener { buttonView, isChecked ->
            if (updatingUi) return@setOnCheckedChangeListener
            if (isChecked &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                revertSwitch(buttonView, false)
                statusNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@setOnCheckedChangeListener
            }
            if (isChecked && !NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                revertSwitch(buttonView, false)
                findViewById<View>(android.R.id.content)
                    .showWarnPill(getString(R.string.status_notification_permission_denied))
                return@setOnCheckedChangeListener
            }
            PersistentStatusNotifier.setEnabled(this, isChecked)
            refreshPageState()
        }
    }

    private val statusNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            PersistentStatusNotifier.setEnabled(this, true)
            switchPersistentStatusNotification.isChecked = true
            refreshPageState()
        } else {
            PersistentStatusNotifier.setEnabled(this, false)
            switchPersistentStatusNotification.isChecked = false
            refreshPageState()
            findViewById<View>(android.R.id.content)
                .showWarnPill(getString(R.string.status_notification_permission_denied))
        }
    }

    private fun bindAccessSwitch(sw: SwitchMaterial, onChecked: (Boolean) -> Unit) {
        sw.setOnCheckedChangeListener { buttonView, isChecked ->
            if (updatingUi) return@setOnCheckedChangeListener
            if (!canEditActiveAccess()) {
                revertSwitch(buttonView, !isChecked)
                return@setOnCheckedChangeListener
            }
            onChecked(isChecked)
        }
    }

    private fun revertSwitch(buttonView: CompoundButton, previous: Boolean) {
        updatingUi = true
        buttonView.isChecked = previous
        updatingUi = false
    }

    // ---------------------------------------------------------------------
    // Row interactions
    // ---------------------------------------------------------------------

    private fun wireRowClicks() {
        rowToggle(R.id.rowMixedAllowAppPicking, switchMixedAllowAppPicking)
        rowToggle(R.id.rowMixedAllowProfileSwitching, switchMixedAllowProfileSwitching)
        rowToggle(R.id.rowMixedAllowNfcTagWriting, switchMixedAllowNfcTagWriting)
        rowToggle(R.id.rowEnablePairedUids, switchEnablePairedUids)
        rowToggle(R.id.rowAutoPairOnWrite, switchAutoPairOnWrite)
        rowToggle(R.id.rowBlockNotifications, switchBlockNotifications)
        rowToggle(R.id.rowLockWarnings, switchLockWarnings)
        rowToggle(R.id.rowAutostart, switchAutostart)
        rowToggle(R.id.rowEmergency, switchEmergency)
        rowToggle(R.id.rowPersistentStatusNotification, switchPersistentStatusNotification)

        findViewById<View>(R.id.rowSessionMissedNotifications).setOnClickListener {
            if (switchBlockNotifications.isChecked) switchSessionMissedNotifications.toggle()
        }
        findViewById<View>(R.id.rowChangeEmergencyPin).setOnClickListener {
            if (!canEditActiveAccess()) return@setOnClickListener
            EmergencyPinDialog.showChangePinFlow(this)
        }
        rowPersistentStatusNotificationMode.setOnClickListener { showStatusNotificationModeDialog() }
    }

    private fun rowToggle(rowId: Int, sw: SwitchMaterial) {
        findViewById<View>(rowId).setOnClickListener { sw.toggle() }
    }

    private fun wireInfoButtons() {
        wireInfo(
            R.id.btnInfoAppPicking,
            R.string.pref_mixed_allow_app_picking_title,
            R.string.pref_mixed_allow_app_picking_summary,
            R.string.toggle_detail_mixed_allow_app_picking
        )
        wireInfo(
            R.id.btnInfoProfileSwitching,
            R.string.pref_mixed_allow_profile_switching_title,
            R.string.pref_mixed_allow_profile_switching_summary,
            R.string.toggle_detail_mixed_allow_profile_switching
        )
        wireInfo(
            R.id.btnInfoNfcTagWriting,
            R.string.pref_mixed_allow_nfc_tag_writing_title,
            R.string.pref_mixed_allow_nfc_tag_writing_summary,
            R.string.toggle_detail_mixed_allow_nfc_tag_writing
        )
        wireInfo(
            R.id.btnInfoAutoPairOnWrite,
            R.string.pref_auto_pair_on_write_title,
            R.string.pref_auto_pair_on_write_summary,
            R.string.toggle_detail_auto_pair_on_write
        )
        wireInfo(
            R.id.btnInfoBlockNotifications,
            R.string.pref_block_notifications_title,
            R.string.pref_block_notifications_summary,
            R.string.toggle_detail_block_notifications
        )
        wireInfo(
            R.id.btnInfoAutostart,
            R.string.pref_autostart_title,
            R.string.pref_autostart_summary,
            R.string.toggle_detail_autostart
        )
        wireInfo(
            R.id.btnInfoEmergency,
            R.string.pref_emergency_title,
            R.string.pref_emergency_summary,
            R.string.toggle_detail_emergency
        )
        wireInfo(
            R.id.btnInfoPersistentStatus,
            R.string.pref_persistent_status_notification_title,
            R.string.pref_persistent_status_notification_summary,
            R.string.toggle_detail_persistent_status_notification
        )
    }

    private fun wireInfo(buttonId: Int, titleRes: Int, summaryRes: Int, detailRes: Int) {
        findViewById<ImageButton>(buttonId).setOnClickListener {
            showDetailDialog(getString(titleRes), getString(summaryRes), getString(detailRes))
        }
    }

    private fun showDetailDialog(title: String, summary: String, detail: String? = null) {
        val message = buildString {
            append(summary)
            if (!detail.isNullOrBlank()) {
                append("\n\n")
                append(detail)
            }
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .create()
        dialog.applyLoqInDialogCorners()
        dialog.setOnShowListener { dialog.styleLoqInDialogButtons() }
        dialog.show()
    }

    private fun showStatusNotificationModeDialog() {
        if (!switchPersistentStatusNotification.isChecked) return
        val modes = arrayOf(
            PersistentStatusNotifier.MODE_STATUS_ONLY,
            PersistentStatusNotifier.MODE_ACTIVE_TIME,
            PersistentStatusNotifier.MODE_APP_COUNT,
            PersistentStatusNotifier.MODE_FULL,
        )
        val labels = modes.map(::statusNotificationModeLabel)
        val checked = modes.indexOf(PersistentStatusNotifier.detailMode(this)).coerceAtLeast(0)
        showLoqInOptionDialog(
            titleRes = R.string.pref_persistent_status_notification_mode_title,
            options = labels.mapIndexed { index, label ->
                LoqInDialogOption(title = label, selected = index == checked)
            },
        ) { which ->
            PersistentStatusNotifier.setDetailMode(this, modes[which])
            refreshPageState()
        }
    }

    private fun statusNotificationModeLabel(mode: String): String = getString(
        when (mode) {
            PersistentStatusNotifier.MODE_STATUS_ONLY -> R.string.status_notification_mode_status_only
            PersistentStatusNotifier.MODE_ACTIVE_TIME -> R.string.status_notification_mode_active_time
            PersistentStatusNotifier.MODE_APP_COUNT -> R.string.status_notification_mode_app_count
            else -> R.string.status_notification_mode_full
        }
    )

    // ---------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------

    private fun refreshPageState() {
        val mode = AutomationModeStore.getMode(this)
        val nfcSupported = AutomationModeStore.isNfcSupported(this)

        // NFC tag writing only matters when NFC is a live control channel.
        rowMixedAllowNfcTagWriting.visibility =
            if (nfcSupported && (mode == AutomationModeStore.Mode.NFC ||
                    mode == AutomationModeStore.Mode.MIXED)
            ) View.VISIBLE else View.GONE

        val nfcPairingVisibility = if (nfcSupported) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sectionNfcPairing).visibility = nfcPairingVisibility
        findViewById<View>(R.id.cardNfcPairing).visibility = nfcPairingVisibility

        val blockNotifications = switchBlockNotifications.isChecked
        rowSessionMissedNotifications.isEnabled = blockNotifications
        rowSessionMissedNotifications.isClickable = blockNotifications
        rowSessionMissedNotifications.alpha = if (blockNotifications) 1f else 0.5f
        switchSessionMissedNotifications.isEnabled = blockNotifications

        rowChangeEmergencyPin.visibility =
            if (switchEmergency.isChecked) View.VISIBLE else View.GONE

        val statusEnabled = switchPersistentStatusNotification.isChecked
        rowPersistentStatusNotificationMode.isEnabled = statusEnabled
        rowPersistentStatusNotificationMode.isClickable = statusEnabled
        rowPersistentStatusNotificationMode.alpha = if (statusEnabled) 1f else 0.5f
        tvPersistentStatusNotificationModeValue.text =
            statusNotificationModeLabel(PersistentStatusNotifier.detailMode(this))

        applyLockedState()
        syncDividers()
    }

    private fun applyLockedState() {
        val locked = LoqInAppAccessGuard.isControlSettingsLocked(this)
        val rowAlpha = if (locked) 0.68f else 1f
        val switchAlpha = if (locked) 0.58f else 1f

        listOf(
            R.id.rowMixedAllowAppPicking,
            R.id.rowMixedAllowProfileSwitching,
            R.id.rowMixedAllowNfcTagWriting,
            R.id.rowAutostart,
            R.id.rowEmergency,
        ).forEach { findViewById<View>(it)?.alpha = rowAlpha }

        listOf(
            switchMixedAllowAppPicking,
            switchMixedAllowProfileSwitching,
            switchMixedAllowNfcTagWriting,
            switchAutostart,
            switchEmergency,
        ).forEach {
            it.isEnabled = !locked
            it.alpha = switchAlpha
        }
    }

    private fun syncDividers() {
        syncDivider(R.id.dividerAccessAppPicking, R.id.rowMixedAllowAppPicking, R.id.rowMixedAllowProfileSwitching)
        syncDivider(
            R.id.dividerAccessProfileSwitching,
            R.id.rowMixedAllowProfileSwitching,
            R.id.rowLockWarnings
        )
        syncDivider(
            R.id.dividerAccessLockWarnings,
            R.id.rowLockWarnings,
            R.id.rowMixedAllowNfcTagWriting
        )
        syncDivider(R.id.dividerEnablePairedUids, R.id.rowEnablePairedUids, R.id.rowAutoPairOnWrite)
        syncDivider(R.id.dividerBlockNotifications, R.id.rowBlockNotifications, R.id.rowSessionMissedNotifications)
        syncDivider(
            R.id.dividerSessionMissedNotifications,
            R.id.rowSessionMissedNotifications,
            R.id.rowAutostart
        )
        syncDivider(R.id.dividerAutostart, R.id.rowAutostart, R.id.rowEmergency)
        syncDivider(R.id.dividerEmergency, R.id.rowEmergency, R.id.rowChangeEmergencyPin)
        syncDivider(
            R.id.dividerPersistentStatusNotification,
            R.id.rowPersistentStatusNotification,
            R.id.rowPersistentStatusNotificationMode
        )
    }

    private fun syncDivider(dividerId: Int, aboveId: Int, belowId: Int) {
        val aboveVisible = findViewById<View>(aboveId)?.visibility == View.VISIBLE
        val belowVisible = findViewById<View>(belowId)?.visibility == View.VISIBLE
        findViewById<View>(dividerId)?.visibility =
            if (aboveVisible && belowVisible) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun canEditActiveAccess(showToast: Boolean = true): Boolean {
        val allowed = !LoqInAppAccessGuard.isControlSettingsLocked(this)
        if (!allowed && showToast) {
            findViewById<View>(android.R.id.content)
                .showWarnPill(R.string.mixed_channels_locked_while_loqin_enabled)
        }
        return allowed
    }

    private fun applySwitchAccentTints() {
        val accent = AccentColor.getAccentColorInt(this)
        val onSurface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurface,
            Color.DKGRAY
        )

        val thumbTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(accent, Color.WHITE)
        )
        val trackTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(
                ColorUtils.setAlphaComponent(accent, 0x66),
                ColorUtils.setAlphaComponent(onSurface, 0x3D)
            )
        )

        accentSwitches.forEach { sw ->
            sw.thumbTintList = thumbTint
            sw.trackTintList = trackTint
        }
    }
}
