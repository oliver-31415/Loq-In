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
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.oliver.loqin.R
import com.oliver.loqin.blocking.BlockingRuntime
import com.oliver.loqin.blocking.OemAccessibilityKeepAlive
import com.oliver.loqin.blocking.UsageAccessFallbackBlocking
import com.oliver.loqin.data.prefs.AppPreferences
import com.oliver.loqin.data.prefs.AutomationModeStore
import com.oliver.loqin.data.prefs.ExactAlarmPermissionSync
import com.oliver.loqin.data.prefs.NfcUidPairingStore
import com.oliver.loqin.data.prefs.NotificationBlockStore
import com.oliver.loqin.data.prefs.ScheduleStore
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.feature.faq.FaqActivity
import com.oliver.loqin.feature.usage.UsageStatsRepo
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.SetupCardRow
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.showWarnPillOnContent
import com.oliver.loqin.ui.dialog.LoqInInfoRow
import com.oliver.loqin.ui.dialog.showLoqInInfoDialog
import com.oliver.loqin.util.BatteryOptimizationRequest
import com.oliver.loqin.util.AdvancedProtectionCompat
import com.oliver.loqin.util.BatteryOptimizationCompat
import com.oliver.loqin.util.LocaleHelper
import com.oliver.loqin.util.NfcLaunchAccessCompat
import com.oliver.loqin.util.PermissionSetupChecks
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Permissions overview screen.
 *
 * Redesigned to use the same setup-card component as onboarding ([SetupCardRow]):
 * a section header followed by full-width cards, each with a status line and a
 * trailing check (satisfied) or chevron. Tapping a card requests the runtime
 * permission when missing, otherwise opens the relevant system settings. Long
 * explanations live behind the per-card info affordance.
 */
class PermissionsActivity : AppCompatActivity() {

    private enum class LocationState {
        OK,
        APPROX_ONLY,
        BACKGROUND_MISSING,
        NEARBY_WIFI_MISSING,
        MISSING
    }

    private val cards = mutableListOf<SetupCardRow.Holder>()

    private lateinit var accessibilityCard: SetupCardRow.Holder
    private lateinit var usageAccessCard: SetupCardRow.Holder
    private lateinit var notificationsCard: SetupCardRow.Holder
    private lateinit var notificationAccessCard: SetupCardRow.Holder
    private lateinit var locationCard: SetupCardRow.Holder
    private lateinit var bluetoothCard: SetupCardRow.Holder
    private lateinit var exactAlarmsCard: SetupCardRow.Holder
    private lateinit var nfcCard: SetupCardRow.Holder
    private lateinit var batteryCard: SetupCardRow.Holder
    private lateinit var autostartCard: SetupCardRow.Holder

    private lateinit var permissionStatusButton: AppCompatTextView

    private var lastMissingPermissionCount: Int = 0
    private var lastHasAccessibilityMismatch: Boolean = false

    private var continueLocationFlowAfterBackground = false

    private lateinit var groupAutostart: View

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))
        val toolbarIconColor = toolbarForegroundColor()
        toolbar.navigationIcon?.mutate()?.setTint(toolbarIconColor)
        toolbar.setTitleTextColor(toolbarIconColor)

        setupPermissionStatusAction(toolbar)

        groupAutostart = findViewById(R.id.groupAutostart)

        buildCards()

        updateUi()
        focusRequestedSection()

        if (intent.getBooleanExtra(EXTRA_SHOW_ACCESSIBILITY_DISCLOSURE, false)) {
            findViewById<View>(R.id.root).post {
                AccessibilityDisclosure.openSettingsWithDisclosure(this, forceShow = true)
            }
        }
    }

    private fun buildCards() {
        val core = findViewById<LinearLayout>(R.id.cardsCore)
        val notifications = findViewById<LinearLayout>(R.id.cardsNotifications)
        val triggers = findViewById<LinearLayout>(R.id.cardsTriggers)
        val battery = findViewById<LinearLayout>(R.id.cardsBattery)
        val autostart = findViewById<LinearLayout>(R.id.cardsAutostart)

        accessibilityCard = addCard(
            core,
            iconRes = R.drawable.security_24,
            titleRes = R.string.permissions_accessibility_title,
            whyTitleRes = R.string.permissions_accessibility_title,
            whyMessageProvider = {
                getString(
                    if (AdvancedProtectionCompat.isEnabled(this)) {
                        R.string.permissions_accessibility_desc_advanced_protection
                    } else {
                        R.string.permissions_accessibility_desc
                    }
                )
            },
            onClick = { AccessibilityDisclosure.openSettingsWithDisclosure(this) },
        )

        usageAccessCard = addCard(
            core,
            iconRes = R.drawable.bar_chart_24,
            titleRes = R.string.permissions_usage_access_title,
            whyTitleRes = R.string.permissions_usage_access_title,
            whyMessageRes = R.string.permissions_usage_access_desc,
            onClick = { safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        )

        notificationsCard = addCard(
            notifications,
            iconRes = R.drawable.notifications_24,
            titleRes = R.string.permissions_notifications_title,
            whyTitleRes = R.string.permissions_notifications_title,
            whyMessageRes = R.string.permissions_notifications_desc,
            onClick = { openOrRequestNotifications() },
        )

        notificationAccessCard = addCard(
            notifications,
            iconRes = R.drawable.security_24,
            titleRes = R.string.permissions_notification_access_title,
            whyTitleRes = R.string.permissions_notification_access_title,
            whyMessageRes = R.string.permissions_notification_access_desc,
            onClick = {
                if (!safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
                    openAppDetails()
                }
            },
        )

        exactAlarmsCard = addCard(
            triggers,
            iconRes = R.drawable.alarm_24,
            titleRes = R.string.permissions_exact_alarms_title,
            whyTitleRes = R.string.permissions_exact_alarms_title,
            whyMessageProvider = {
                getString(R.string.permissions_exact_alarms_summary) + "\n\n" +
                    getString(R.string.permissions_exact_alarms_note)
            },
            onClick = { openExactAlarmSettings() },
        )

        nfcCard = addCard(
            triggers,
            iconRes = R.drawable.nfc_24,
            titleRes = R.string.permissions_nfc_title,
            whyTitleRes = R.string.permissions_nfc_title,
            whyMessageRes = R.string.permissions_nfc_desc,
            onClick = { openNfcSettings() },
        )

        locationCard = addCard(
            triggers,
            iconRes = R.drawable.location_on_24,
            titleRes = R.string.permissions_location_title,
            whyTitleRes = R.string.permissions_location_title,
            whyMessageRes = R.string.permissions_location_desc,
            onClick = {
                // One tap: request what is missing; if we already have everything, open settings.
                if (getLocationStateForWifi() == LocationState.OK) {
                    openLocationSettingsForApp()
                } else {
                    requestLocationFlow()
                }
            },
        )

        bluetoothCard = addCard(
            triggers,
            iconRes = R.drawable.bluetooth_24,
            titleRes = R.string.permissions_bluetooth_title,
            whyTitleRes = R.string.permissions_bluetooth_title,
            whyMessageRes = R.string.permissions_bluetooth_desc,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission()) {
                    requestBluetoothPermissionIfMissing()
                } else {
                    openBluetoothSettingsOrAppDetails()
                }
            },
        )

        batteryCard = addCard(
            battery,
            iconRes = R.drawable.battery_24,
            titleRes = R.string.permissions_battery_title,
            whyTitleRes = R.string.permissions_battery_title,
            whyMessageProvider = {
                getString(R.string.permissions_battery_desc) + "\n\n" +
                    getString(R.string.permissions_battery_oem_note)
            },
            onClick = {
                if (!isBatteryOptimizationEffectivelyOk()) {
                    requestIgnoreBatteryOptimizationsSystemPopup()
                } else {
                    openBatteryOptimizationSettingsPages()
                }
            },
        )

        autostartCard = addCard(
            autostart,
            iconRes = R.drawable.battery_24,
            titleRes = R.string.permissions_autostart_title,
            whyTitleRes = R.string.permissions_autostart_title,
            whyMessageRes = R.string.permissions_autostart_desc,
            onClick = {
                startActivity(
                    FaqActivity.intent(
                        context = this,
                        category = FaqActivity.CATEGORY_BACKGROUND_ACCESS,
                        questionResId = if (isVivoOrIqooDevice()) {
                            R.string.faq_q_vivo_iqoo_background
                        } else {
                            R.string.faq_q_device_background_steps
                        }
                    )
                )
            },
        )
    }

    private fun addCard(
        container: LinearLayout,
        iconRes: Int,
        titleRes: Int,
        whyTitleRes: Int,
        whyMessageRes: Int? = null,
        whyMessageProvider: (() -> String)? = null,
        onClick: () -> Unit,
    ): SetupCardRow.Holder {
        val holder = SetupCardRow.build(
            this,
            SetupCardRow.Spec(
                title = getString(titleRes),
                iconRes = iconRes,
                onClick = onClick,
                onInfoClick = {
                    val message = whyMessageProvider?.invoke() ?: getString(whyMessageRes!!)
                    showWhyDialog(getString(whyTitleRes), message)
                },
                infoContentDescription = getString(R.string.permissions_why_dialog_header),
            ),
        )
        container.addView(holder.card)
        cards += holder
        return holder
    }

    override fun onResume() {
        super.onResume()
        CustomAccentApplier.applyIfNeeded(this)
        ExactAlarmPermissionSync.syncAndReschedule(this, reason = "permissions_resume")
        updateUi()
        if (continueLocationFlowAfterBackground && hasBackgroundLocationPermission()) {
            continueLocationFlowAfterBackground = false
            window.decorView.postDelayed({
                if (!isFinishing && !isDestroyed) requestLocationFlow()
            }, 220L)
        }
        lifecycleScope.launch {
            delay(350)
            updateUi()
            delay(900)
            updateUi()
        }
    }

    private fun updateUi(forceHeartbeat: Boolean = false) {
        val postNotifGranted = hasPostNotificationsPermission()
        val notificationsOk = PermissionSetupChecks.notificationsReady(
            this,
            requireListenerAccess = false
        )
        val notificationAccessGranted = NotificationBlockStore.hasListenerAccess(this)
        val notificationBlockingEnabled = NotificationBlockStore.isEnabled(this)

        val accessibilityRuntime = BlockingRuntime.isAccessibilityActive(this)
        val accessibilityDirect = BlockingRuntime.isAccessibilityEnabledInSettings(this)
        val accessibilityMismatchNow = accessibilityRuntime != accessibilityDirect
        val stickyAccessibilityMismatch = refreshStickyAccessibilityMismatch(accessibilityMismatchNow)
        val accessibilityEnabled = accessibilityRuntime

        val usageAccessOk = UsageStatsRepo.hasUsageAccess(this)
        val advancedProtectionEnabled = AdvancedProtectionCompat.isEnabled(this)
        if (advancedProtectionEnabled && SwitchModeStore.isEnabled(this)) {
            runCatching { UsageAccessFallbackBlocking.sync(this) }
        }
        val fallbackRunning = UsageAccessFallbackBlocking.isRunning(this)
        val locationState = getLocationStateForWifi()
        val locationOk = locationState == LocationState.OK

        val btGranted = hasBluetoothPermission()

        val locationNeeded = ScheduleStore.hasEnabledWifiSchedules(this) || ScheduleStore.hasEnabledLocationSchedules(this)
        val bluetoothNeeded = ScheduleStore.hasEnabledBluetoothSchedules(this)

        val batteryOk = isBatteryOptimizationEffectivelyOk()
        val hasEnabledSchedules = ScheduleStore.getAll(this).any { it.enabled }
        val batteryRelevant = hasEnabledSchedules

        val exactAlarmsOk = canScheduleExactAlarms()
        val exactAlarmsRelevant = hasEnabledSchedules && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        val permissionsLocked = SwitchModeStore.isEnabled(this) && SwitchModeStore.isNfcRequiredForDisable(this)
        val nfcRelevant = AutomationModeStore.isNfcAllowed(this) ||
            runCatching { NfcUidPairingStore.getPairedUidsHex(this).isNotEmpty() }.getOrDefault(false)
        val nfcMissing = nfcRelevant && NfcLaunchAccessCompat.state(this) in setOf(
            NfcLaunchAccessCompat.State.NOT_ALLOWED,
            NfcLaunchAccessCompat.State.NFC_DISABLED
        )

        val enabledText = getString(R.string.permissions_status_enabled)
        val disabledText = getString(R.string.permissions_status_disabled)

        // Accessibility (special multi-state status).
        val accStatus = accessibilityStatusText(
            runtimeActive = accessibilityRuntime,
            enabledInSettings = accessibilityDirect,
            advancedProtectionEnabled = advancedProtectionEnabled,
            usageAccessEnabled = usageAccessOk,
            fallbackRunning = fallbackRunning,
        )
        accessibilityCard.setState(accStatus.text, ok = accessibilityRuntime, error = accStatus.error)

        usageAccessCard.setState(if (usageAccessOk) enabledText else disabledText, ok = usageAccessOk, error = !usageAccessOk)

        notificationsCard.setState(
            if (notificationsOk) enabledText else disabledText,
            ok = notificationsOk,
            error = !notificationsOk,
        )

        notificationAccessCard.setState(
            if (notificationAccessGranted) enabledText else disabledText,
            ok = notificationAccessGranted,
            error = notificationBlockingEnabled && !notificationAccessGranted,
        )

        // Exact alarms
        exactAlarmsCard.setState(
            getString(
                if (exactAlarmsOk) R.string.permissions_exact_alarms_allowed
                else R.string.permissions_exact_alarms_not_allowed
            ),
            ok = exactAlarmsOk,
            error = !exactAlarmsOk,
        )

        // NFC
        val nfcSupported = AutomationModeStore.isNfcSupported(this)
        when {
            !nfcSupported -> nfcCard.setState(getString(R.string.mode_not_supported_on_device), ok = false)
            else -> when (NfcLaunchAccessCompat.state(this)) {
                NfcLaunchAccessCompat.State.ALLOWED ->
                    nfcCard.setState(enabledText, ok = true)
                NfcLaunchAccessCompat.State.NOT_ALLOWED ->
                    nfcCard.setState(getString(R.string.permissions_nfc_status_not_allowed), ok = false, error = true)
                NfcLaunchAccessCompat.State.NFC_DISABLED ->
                    nfcCard.setState(getString(R.string.permissions_nfc_status_system_disabled), ok = false, error = true)
                NfcLaunchAccessCompat.State.UNKNOWN ->
                    nfcCard.setState(getString(R.string.permissions_nfc_status_manual), ok = false)
            }
        }
        nfcCard.setLocked(false)

        // Location
        val locationStatus = when (locationState) {
            LocationState.OK -> null to false
            LocationState.MISSING -> disabledText to true
            LocationState.APPROX_ONLY -> getString(R.string.permissions_status_location_approx) to true
            LocationState.BACKGROUND_MISSING -> getString(R.string.permissions_status_location_background_missing) to true
            LocationState.NEARBY_WIFI_MISSING -> getString(R.string.permissions_status_nearby_wifi_missing) to true
        }
        locationCard.setState(
            locationStatus.first ?: enabledText,
            ok = locationOk,
            error = locationStatus.second,
        )

        bluetoothCard.setState(if (btGranted) enabledText else disabledText, ok = btGranted, error = !btGranted)

        // Battery
        batteryCard.setState(
            when {
                batteryOk && isBatteryOptimizationUserConfirmedMaxAvailable() ->
                    getString(R.string.permissions_battery_highest_available)
                batteryOk -> getString(R.string.permissions_battery_allowed)
                else -> getString(R.string.permissions_battery_not_allowed)
            },
            ok = batteryOk,
            error = !batteryOk,
        )

        // OEM autostart
        val showOem = isLikelyAggressiveOem()
        groupAutostart.visibility = if (showOem) View.VISIBLE else View.GONE
        if (showOem) {
            autostartCard.setState(
                when {
                    OemAccessibilityKeepAlive.isLikelyAccessibilityDisabledByOem(this) ->
                        getString(R.string.permissions_autostart_desc_vivo_accessibility_disabled)
                    isVivoOrIqooDevice() -> getString(R.string.permissions_autostart_desc_vivo)
                    else -> getString(R.string.permissions_autostart_desc)
                },
                ok = false,
            )
            autostartCard.setLocked(permissionsLocked)
        }

        // Locked-while-armed: dim the cards whose settings must not change while protection is on.
        accessibilityCard.setLocked(permissionsLocked && accessibilityEnabled)
        usageAccessCard.setLocked(permissionsLocked && usageAccessOk)
        notificationsCard.setLocked(permissionsLocked && notificationsOk)
        notificationAccessCard.setLocked(permissionsLocked && notificationAccessGranted)
        locationCard.setLocked(permissionsLocked && locationOk)
        bluetoothCard.setLocked(permissionsLocked && btGranted)
        exactAlarmsCard.setLocked(permissionsLocked && exactAlarmsOk)
        batteryCard.setLocked(permissionsLocked && batteryOk)

        val missingCount = listOf(
            (!accessibilityEnabled),
            (!usageAccessOk),
            !notificationsOk,
            (notificationBlockingEnabled && !notificationAccessGranted),
            (locationNeeded && !locationOk),
            (bluetoothNeeded && !btGranted),
            (batteryRelevant && !batteryOk),
            (exactAlarmsRelevant && !exactAlarmsOk),
            nfcMissing
        ).count { it }

        lastMissingPermissionCount = missingCount
        lastHasAccessibilityMismatch = stickyAccessibilityMismatch || accessibilityMismatchNow

        updateBanner(
            missingCount = missingCount,
            stickyAccessibilityMismatch = stickyAccessibilityMismatch || accessibilityMismatchNow
        )
        if (forceHeartbeat) {
            permissionStatusButton.animate().alpha(0.55f).setDuration(80L).withEndAction {
                permissionStatusButton.animate().alpha(1f).setDuration(140L).start()
            }.start()
        }
    }

    private data class StatusText(val text: String, val error: Boolean)

    private fun accessibilityStatusText(
        runtimeActive: Boolean,
        enabledInSettings: Boolean,
        advancedProtectionEnabled: Boolean,
        usageAccessEnabled: Boolean,
        fallbackRunning: Boolean,
    ): StatusText = when {
        runtimeActive -> StatusText(getString(R.string.permissions_status_enabled), false)
        advancedProtectionEnabled && usageAccessEnabled && SwitchModeStore.isEnabled(this) -> StatusText(
            getString(
                if (fallbackRunning) {
                    R.string.permissions_status_advanced_protection_fallback_active
                } else {
                    R.string.permissions_status_advanced_protection_fallback_starting
                }
            ),
            false,
        )
        advancedProtectionEnabled ->
            StatusText(getString(R.string.permissions_status_advanced_protection), true)
        enabledInSettings ->
            StatusText(getString(R.string.permissions_status_not_connected), true)
        OemAccessibilityKeepAlive.isLikelyAccessibilityDisabledByOem(this) ->
            StatusText(getString(R.string.permissions_status_oem_accessibility_disabled), true)
        else -> StatusText(getString(R.string.permissions_status_disabled), true)
    }

    private fun refreshStickyAccessibilityMismatch(mismatchNow: Boolean): Boolean {
        val prefs = getSharedPreferences(PREFS_PERMISSION_HEALTH, MODE_PRIVATE)
        val wasSticky = prefs.getBoolean(KEY_STICKY_ACCESSIBILITY_MISMATCH, false)
        return if (mismatchNow) {
            if (!wasSticky) prefs.edit { putBoolean(KEY_STICKY_ACCESSIBILITY_MISMATCH, true) }
            true
        } else {
            if (wasSticky) prefs.edit { putBoolean(KEY_STICKY_ACCESSIBILITY_MISMATCH, false) }
            false
        }
    }

    private fun updateBanner(missingCount: Int, stickyAccessibilityMismatch: Boolean) {
        val hasIssues = missingCount > 0 || stickyAccessibilityMismatch
        permissionStatusButton.setText(
            if (hasIssues) R.string.permissions_toolbar_issues
            else R.string.ok
        )

        // Keep the toolbar action flat and consistently visible on every accent color.
        val contentColor = ContextCompat.getColor(this, android.R.color.white)
        val shield = ContextCompat.getDrawable(this, R.drawable.security_24)?.mutate()?.apply {
            setTint(contentColor)
        }
        permissionStatusButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
            shield,
            null,
            null,
            null
        )
        permissionStatusButton.setTextColor(contentColor)
        permissionStatusButton.background = null
        permissionStatusButton.alpha = 1f
    }

    private fun setupPermissionStatusAction(toolbar: MaterialToolbar) {
        val horizontalPadding = (8 * resources.displayMetrics.density + 0.5f).toInt()
        val actionHeight = (48 * resources.displayMetrics.density + 0.5f).toInt()
        permissionStatusButton = AppCompatTextView(this).apply {
            minWidth = 0
            minHeight = actionHeight
            gravity = Gravity.CENTER
            isSingleLine = true
            includeFontPadding = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(this@PermissionsActivity, android.R.color.white))
            background = null
            compoundDrawablePadding =
                resources.getDimensionPixelSize(R.dimen.permission_toolbar_status_icon_padding)
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            contentDescription = getString(R.string.permissions_health_recheck)
            setOnClickListener {
                updateUi(forceHeartbeat = true)
                val message = when {
                    lastHasAccessibilityMismatch -> getString(R.string.permissions_health_rechecked_with_mismatch)
                    lastMissingPermissionCount > 0 -> resources.getQuantityString(
                        R.plurals.permissions_health_rechecked_missing,
                        lastMissingPermissionCount,
                        lastMissingPermissionCount
                    )
                    else -> getString(R.string.permissions_health_rechecked_all_good)
                }
                showWarnPillOnContent(message)
            }
        }

        val toolbarSpacing = (2 * resources.displayMetrics.density + 0.5f).toInt()
        toolbar.addView(
            permissionStatusButton,
            Toolbar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                actionHeight
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                marginEnd = toolbarSpacing
            }
        )
    }

    // LOCATION
    private fun hasCoarseLocationPermission(): Boolean {
        return PermissionSetupChecks.hasCoarseLocation(this)
    }

    private fun hasFineLocationPermission(): Boolean {
        return PermissionSetupChecks.hasFineLocation(this)
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return PermissionSetupChecks.hasBackgroundLocation(this)
    }

    /**
     * Wi‑Fi schedules need location permissions so the connected SSID/BSSID can be read reliably.
     * For best reliability on many devices, especially when triggers run while the app is not
     * in the foreground, we also guide users to enable "Allow all the time" (ACCESS_BACKGROUND_LOCATION) via a 2‑step flow.
     */
    private fun getLocationStateForWifi(): LocationState {
        val fine = hasFineLocationPermission()
        val coarse = hasCoarseLocationPermission()

        if (!fine) {
            return if (coarse) LocationState.APPROX_ONLY else LocationState.MISSING
        }

        // For best reliability, especially when Wi‑Fi triggers run in the background, guide users to "Allow all the time" (ACCESS_BACKGROUND_LOCATION).
        if (!hasBackgroundLocationPermission()) {
            return LocationState.BACKGROUND_MISSING
        }
        if (!PermissionSetupChecks.hasNearbyWifiDevices(this)) {
            return LocationState.NEARBY_WIFI_MISSING
        }
        return LocationState.OK
    }

    /**
     * Runtime request flow for Wi‑Fi schedules.
     * 1) Request ACCESS_FINE_LOCATION (Android popup: "While using the app")
     * 2) Request/guide to ACCESS_BACKGROUND_LOCATION ("Allow all the time")
     * 3) (Optional, Android 13+) Request NEARBY_WIFI_DEVICES
     */
    private fun requestLocationFlow() {
        if (!hasFineLocationPermission()) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOC_FINE
            )
            return
        }

        if (!hasBackgroundLocationPermission()) {
            requestBackgroundLocationFlow()
            return
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (!PermissionSetupChecks.hasNearbyWifiDevices(this)) {
                requestPermissions(
                    arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES),
                    REQ_NEARBY_WIFI
                )
                return
            }
        }

        updateUi()
    }

    private fun requestBackgroundLocationFlow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            updateUi()
            return
        }

        // Android 10: we can still request it directly.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            continueLocationFlowAfterBackground = true
            requestPermissions(
                arrayOf(ACCESS_BACKGROUND_LOCATION_PERMISSION),
                REQ_LOC_BACKGROUND
            )
            return
        }

        // Android 11+: requesting ACCESS_BACKGROUND_LOCATION will usually open the system permission controller where the user can switch to
        // "Allow all the time" for Location.
        // On some devices/ROMs this may still not show the exact location page.
        // Keep the flow armed while Android sends the user through Settings so returning with "Allow all the time" can continue directly to the remaining Nearby Wi-Fi grant.
        continueLocationFlowAfterBackground = true
        requestPermissions(
            arrayOf(ACCESS_BACKGROUND_LOCATION_PERMISSION),
            REQ_LOC_BACKGROUND
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            // After "Request" -> optionally ask for NEARBY_WIFI_DEVICES
            REQ_LOC_FINE -> {
                if (hasFineLocationPermission()) {
                    // Continue the 2‑step flow: "While using" -> "All the time"
                    requestLocationFlow()
                } else {
                    updateUi()
                }
            }

            REQ_LOC_BACKGROUND -> {
                if (hasBackgroundLocationPermission()) {
                    continueLocationFlowAfterBackground = false
                    requestLocationFlow()
                } else {
                    // Some Android 11+ permission controllers return before the user finishes the Settings step.
                    // Keep the flow armed; onResume will continue once the permission actually becomes "Allow all the time".
                    updateUi()
                }
            }

            REQ_NEARBY_WIFI,
            REQ_BT,
            REQ_POST_NOTIF -> updateUi()
        }
    }

    // BLUETOOTH
    private fun hasBluetoothPermission(): Boolean {
        return PermissionSetupChecks.bluetoothTriggersReady(this)
    }

    private fun requestBluetoothPermissionIfMissing() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        if (hasBluetoothPermission()) {
            return
        }
        requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQ_BT)
    }

    private fun isBatteryOptimizationUserConfirmedMaxAvailable(): Boolean {
        return BatteryOptimizationCompat.isUserConfirmedMaxAvailable(this)
    }

    private fun isBatteryOptimizationEffectivelyOk(): Boolean {
        return PermissionSetupChecks.batteryOptimizationReady(this)
    }

    private fun requestIgnoreBatteryOptimizationsSystemPopup() {
        if (!BatteryOptimizationRequest.isAlreadyAllowed(this) &&
            safeStart(BatteryOptimizationRequest.intent(this))
        ) {
            return
        }
        openBatteryOptimizationSettingsPages()
    }

    // EXACT ALARMS
    private fun canScheduleExactAlarms(): Boolean {
        return ExactAlarmPermissionSync.canScheduleExactAlarms(this)
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:$packageName".toUri()
            })
        }
    }

    private fun openNfcSettings() {
        for (intent in NfcLaunchAccessCompat.settingsIntents(this)) {
            if (safeStart(intent)) {
                return
            }
        }
    }

    // NOTIFICATIONS (Android 13+)
    private fun hasPostNotificationsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun openOrRequestNotifications() {
        // If runtime permission is missing, request it once.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationsPermission()) {
            lifecycleScope.launch {
                val prefs = AppPreferences(applicationContext)
                val askedBefore = prefs.notificationsPermissionAsked.first()
                if (!askedBefore) {
                    prefs.setNotificationsPermissionAsked(true)
                    requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQ_POST_NOTIF
                    )
                } else {
                    // If user denied previously, open settings instead of spamming prompts.
                    openNotificationSettings()
                }
            }
            return
        }

        openNotificationSettings()
    }

    private fun openNotificationSettings() {
        safeStart(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        )
    }

    private fun openBatteryOptimizationSettingsPages() {
        val intents = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
        )
        for (i in intents) {
            if (safeStart(i)) {
                return
            }
        }
    }

    private fun openLocationSettingsForApp() {
        val pkg = packageName
        val packageUri = "package:$pkg".toUri()

        val extraPermissionName = "android.intent.extra.PERMISSION_NAME"
        val locationGroup = Manifest.permission_group.LOCATION

        val intents = listOf(
            // Best-effort deep link directly into the Location permission entry.
            Intent("android.intent.action.MANAGE_APP_PERMISSION").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)
                putExtra(extraPermissionName, "android.permission.ACCESS_BACKGROUND_LOCATION")
            },
            Intent("android.intent.action.MANAGE_APP_PERMISSION").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)
                putExtra(extraPermissionName, Manifest.permission.ACCESS_FINE_LOCATION)
            },
            // Some ROMs behave better with the permission *group*
            Intent("android.intent.action.MANAGE_APP_PERMISSION").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)
                putExtra(extraPermissionName, locationGroup)
            },

            // App permissions list (often available, sometimes hidden)
            Intent("android.settings.APP_PERMISSIONS_SETTINGS").apply {
                data = packageUri
                putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)
            },
            Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)
            },

            // Fallbacks
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = packageUri },
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        )

        for (i in intents) {
            if (safeStart(i)) {
                return
            }
        }
    }

    private fun openAppDetails() {
        safeStart(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
        )
    }

    private fun openBluetoothSettingsOrAppDetails() {
        if (!safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) {
            openAppDetails()
        }
    }

    private fun isVivoOrIqooDevice(): Boolean {
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val brand = (Build.BRAND ?: "").lowercase()
        val combined = "$manufacturer $brand"
        return combined.contains("vivo") || combined.contains("iqoo")
    }

    private fun isLikelyAggressiveOem(): Boolean {
        val m = (Build.MANUFACTURER ?: "").lowercase()
        val b = (Build.BRAND ?: "").lowercase()
        val all = "$m $b"
        return listOf(
            "xiaomi",
            "redmi",
            "poco",
            "huawei",
            "honor",
            "oppo",
            "realme",
            "oneplus",
            "vivo",
            "samsung",
            "motorola",
            "lenovo",
            "asus",
            "sony",
            "nokia",
            "zte",
            "tecno",
            "infinix"
        ).any { all.contains(it) }
    }

    private fun showWhyDialog(title: String, message: String) {
        showLoqInInfoDialog(
            title = title,
            rows = listOf(
                LoqInInfoRow(
                    label = getString(R.string.permissions_why_dialog_header),
                    value = message,
                ),
            ),
        )
    }

    private fun safeStart(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun focusRequestedSection() {
        val sectionId = when (intent.getStringExtra(EXTRA_FOCUS_SECTION)) {
            SECTION_CORE -> R.id.sectionCore
            SECTION_NOTIFICATIONS -> R.id.sectionNotifications
            SECTION_TRIGGERS -> R.id.sectionTriggers
            SECTION_BATTERY -> R.id.sectionBattery
            else -> return
        }
        val requestedTargetId = when (intent.getStringExtra(EXTRA_FOCUS_TARGET)) {
            TARGET_AUTOSTART -> R.id.groupAutostart
            else -> sectionId
        }
        val scroll = findViewById<ScrollView>(R.id.permissionsScroll)
        scroll.post {
            val requested = findViewById<View>(requestedTargetId)
            val fallback = findViewById<View>(sectionId)
            val target = requested.takeIf { it.isVisible } ?: fallback
            scroll.smoothScrollTo(
                0,
                (target.top - resources.displayMetrics.density * 8f).toInt().coerceAtLeast(0),
            )
            if (requestedTargetId != sectionId && requested.isVisible) {
                requested.animate().alpha(0.72f).setDuration(120L).withEndAction {
                    requested.animate().alpha(1f).setDuration(220L).start()
                }.start()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun toolbarForegroundColor(): Int {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) Color.WHITE else Color.BLACK
    }

    companion object {
        private const val ACCESS_BACKGROUND_LOCATION_PERMISSION = "android.permission.ACCESS_BACKGROUND_LOCATION"

        const val REQ_LOC_FINE = 1001
        const val REQ_LOC_BACKGROUND = 1003
        const val REQ_BT = 1002
        const val REQ_NEARBY_WIFI = 1004
        const val REQ_POST_NOTIF = 1005

        private const val PREFS_PERMISSION_HEALTH = "permissions_health"
        private const val KEY_STICKY_ACCESSIBILITY_MISMATCH = "sticky_accessibility_mismatch"

        const val EXTRA_FROM_ONBOARDING = "extra_from_onboarding"
        const val EXTRA_SHOW_ACCESSIBILITY_DISCLOSURE = "extra_show_accessibility_disclosure"
        const val EXTRA_FOCUS_SECTION = "extra_focus_section"
        const val EXTRA_FOCUS_TARGET = "extra_focus_target"

        const val SECTION_CORE = "core"
        const val SECTION_NOTIFICATIONS = "notifications"
        const val SECTION_TRIGGERS = "triggers"
        const val SECTION_BATTERY = "battery"
        const val TARGET_AUTOSTART = "autostart"
    }
}
