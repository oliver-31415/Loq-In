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

package com.oliver.loqin.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.oliver.loqin.R
import com.oliver.loqin.blocking.BlockingRuntime
import com.oliver.loqin.blocking.isBrowserPackage
import com.oliver.loqin.data.prefs.ActiveDurationStore
import com.oliver.loqin.data.prefs.AppLogStore
import com.oliver.loqin.data.prefs.AttemptLimitStore
import com.oliver.loqin.data.prefs.AutomationModeStore
import com.oliver.loqin.data.prefs.EmergencyBypassStore
import com.oliver.loqin.data.prefs.EmergencyPinStore
import com.oliver.loqin.data.prefs.ExactAlarmPermissionSync
import com.oliver.loqin.data.prefs.InAppRuleStore
import com.oliver.loqin.data.prefs.LimitReachedStore
import com.oliver.loqin.data.prefs.OpenCountStore
import com.oliver.loqin.data.prefs.PauseUntilStore
import com.oliver.loqin.data.prefs.SchedulePlanner
import com.oliver.loqin.data.prefs.ScheduleRuntimeStore
import com.oliver.loqin.data.prefs.ScheduleStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.TempPauseStore
import com.oliver.loqin.data.prefs.ProfileRuleModeStore
import com.oliver.loqin.data.prefs.DomainBlockStore
import com.oliver.loqin.data.prefs.SessionLimitStore
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.data.prefs.UsageLimitStore
import com.oliver.loqin.data.prefs.UsageLimitResetStore
import com.oliver.loqin.data.prefs.UsageLimitSessionRuntimeStore
import com.oliver.loqin.feature.inbox.BlockedInboxActivity
import com.oliver.loqin.data.prefs.SessionMissedNotificationsStore
import com.oliver.loqin.data.prefs.BlockedNotificationEvent
import com.oliver.loqin.feature.onboarding.OnboardingActivity
import com.oliver.loqin.feature.picker.AppPickerActivity
import com.oliver.loqin.feature.profiles.TempPauseDialogs
import com.oliver.loqin.feature.profiles.ManageProfilesActivity
import com.oliver.loqin.feature.qr.QrGenerateActivity
import com.oliver.loqin.feature.scan.UnifiedScanActivity
import com.oliver.loqin.feature.schedule.SchedulesActivity
import com.oliver.loqin.feature.settings.ManageBarcodesActivity
import com.oliver.loqin.feature.settings.ManageBlockedWebsitesActivity
import com.oliver.loqin.feature.settings.PermissionsActivity
import com.oliver.loqin.feature.settings.InAppRulesActivity
import com.oliver.loqin.feature.settings.SettingsActivity
import com.oliver.loqin.feature.settings.ToggleOptionsActivity
import com.oliver.loqin.feature.settings.HomeModeDialogHelper
import com.oliver.loqin.feature.support.SupportLogActivity
import com.oliver.loqin.feature.tools.RulesHubActivity
import com.oliver.loqin.feature.tools.ActivityHubActivity
import com.oliver.loqin.feature.usage.ActiveTimeActivity
import com.oliver.loqin.feature.usage.AppWebsiteUsageActivity
import com.oliver.loqin.feature.usage.QuickLimitDialogs
import com.oliver.loqin.feature.stats.StatsFormat
import com.oliver.loqin.nfc.NfcWriterActivity
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.dialog.Dialogs
import com.oliver.loqin.ui.dialog.EmergencyPinDialog
import com.oliver.loqin.ui.dialog.styledDialogEditText
import com.oliver.loqin.ui.dialog.applyLoqInDialogWidth
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.ui.dialog.showDestructiveAccented
import com.oliver.loqin.ui.dialog.showLoqInInputDialog
import com.oliver.loqin.ui.dialog.styleLoqInDialogButtons
import com.oliver.loqin.ui.dialog.LoqInDialogOption
import com.oliver.loqin.ui.dialog.showLoqInOptionDialog
import com.oliver.loqin.ui.LoqInDropdownAdapter
import com.oliver.loqin.util.ActivityTransitionCompat
import com.oliver.loqin.util.EditingLockGuard
import com.oliver.loqin.util.LocaleHelper
import com.oliver.loqin.util.PlayStoreUpdatePrompt
import com.oliver.loqin.util.ProtectionStatusNotifier
import com.oliver.loqin.util.BatteryOptimizationCompat
import com.oliver.loqin.util.TimeFormatPrefs
import com.oliver.loqin.util.getIntCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import android.util.TypedValue
import java.util.Locale
import java.text.DateFormat
import com.oliver.loqin.data.prefs.BlockedTimeStore
import com.oliver.loqin.data.prefs.BlockCountStore
import com.oliver.loqin.ui.widgets.FoqosHeatmapView
import com.oliver.loqin.ui.widgets.ClockDurationDialView
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SKIP_ONBOARDING_GATE_ONCE = "extra_skip_onboarding_gate_once"

        private const val PREFS_UI_HINTS = "loqin_ui_hints"
        private const val KEY_TEMP_MODE_DISCOVERED = "temp_mode_discovered"
        private const val KEY_PRIMARY_TOGGLE_TAP_COUNT = "primary_toggle_tap_count"
        private const val KEY_QUICK_ACTIONS_EXPANDED = "home_quick_actions_expanded"
        private const val KEY_BLOCKED_APPS_EXPANDED = "home_blocked_apps_expanded"
        private const val KEY_BOTTOM_NAV_TOUR_PENDING = "bottom_nav_tour_pending"
        private const val KEY_BOTTOM_NAV_TOUR_VERSION = "bottom_nav_tour_version"
        private const val BOTTOM_NAV_TOUR_VERSION = 1
        private const val KEY_QA_APPS = "home_quick_tile_apps"
        private const val KEY_QA_PROFILES = "home_quick_tile_profiles"
        private const val KEY_QA_WEBSITES = "home_quick_tile_websites"
        private const val KEY_QA_INAPP = "home_quick_tile_inapp"
        private const val KEY_QA_NFC_WRITE = "home_quick_tile_nfc_write"
        private const val KEY_QA_BLOCKED_NOTIFICATIONS = "home_quick_tile_blocked_notifications"
        private const val KEY_QA_QR = "home_quick_tile_qr"
        private const val KEY_QA_BARCODE = "home_quick_tile_barcode"
        private const val KEY_QA_ORDER = "home_quick_tile_order"
        private const val TAG_QUICK_ACTIONS_DYNAMIC_ROW = "quick_actions_dynamic_row"
        private val PAYLOAD_BLOCKED_CHIPS = Any()
        private val PAYLOAD_BLOCKED_EDIT_STATE = Any()

        fun queueBottomNavTour(context: Context) {
            context.getSharedPreferences(PREFS_UI_HINTS, MODE_PRIVATE).edit {
                putBoolean(KEY_BOTTOM_NAV_TOUR_PENDING, true)
            }
        }
    }

    // Core status
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvSwitchMode: TextView
    private lateinit var tvActiveDuration: TextView
    private lateinit var tvControlModeHint: TextView
    private lateinit var tvActiveProfile: TextView
    private lateinit var rowActiveProfile: View
    private lateinit var tvNfcLockedHint: TextView
    private lateinit var btnToggle: MaterialButton
    private lateinit var btnSimplePickApps: MaterialButton
    private lateinit var tvTempHint: LinearLayout
    private lateinit var tvEmergencyHint: LinearLayout
    private lateinit var tvTempTileTitle: TextView
    private lateinit var tvTempTileSubtitle: TextView
    private lateinit var tvEmergencyTileTitle: TextView
    private lateinit var tvEmergencyTileSubtitle: TextView
    private lateinit var layoutHomeRoot: LinearLayout
    private lateinit var layoutStatusContent: LinearLayout
    private lateinit var layoutProtectionStatus: View
    private lateinit var cardStatus: MaterialCardView
    private lateinit var spaceBeforeStatusCard: View
    private var bottomNavTourScheduled = false
    private var bottomNavTourShowing = false
    private var bottomNavTourDialog: AlertDialog? = null
    private var bottomNavTourHighlight: BottomNavTourHighlight? = null
    private var bottomNavTourOriginalIconTint: ColorStateList? = null
    private var bottomNavTourOriginalTextTint: ColorStateList? = null

    // Profile controls
    private lateinit var profileDropdown: MaterialAutoCompleteTextView
    private lateinit var layoutProfileDropdown: TextInputLayout
    private lateinit var dividerBeforeProfileDropdown: View

    // Setup banner
    private lateinit var cardSetup: MaterialCardView
    private lateinit var tvSetupTitle: TextView
    private lateinit var tvSetupSubtitle: TextView
    private lateinit var tvSetupDesc: TextView
    private lateinit var btnFinishSetup: MaterialButton

    // Activity heatmap (Foqos-style)
    private lateinit var cardActivity: MaterialCardView
    private lateinit var activityHeatmap: FoqosHeatmapView
    private lateinit var tvHeatmapLegend: TextView
    private lateinit var btnActivityHide: LinearLayout
    private lateinit var tvHeroProfileName: TextView
    private lateinit var tvHeroChips: TextView
    private lateinit var tvHeroStrategy: TextView
    private lateinit var heroProfileRoot: View
    private lateinit var scrollMain: androidx.core.widget.NestedScrollView
    private lateinit var tvHeroStatApps: TextView
    private lateinit var tvHeroStatDomains: TextView
    private lateinit var tvHeroStatBlocks: TextView
    private var activityHidden = false
    private lateinit var tvActivityDetail: TextView
    @Volatile private var activityDaysMs: LongArray = LongArray(FoqosHeatmapView.DAYS)

    // Foqos-style profile rows
    private lateinit var profileRowsContainer: LinearLayout
    private lateinit var btnManageProfiles: LinearLayout

    // Quick actions tiles
    private lateinit var tileManageApps: MaterialCardView
    private lateinit var tileProfiles: MaterialCardView
    private lateinit var tileWriteNfc: MaterialCardView
    private lateinit var tileToggleOptions: MaterialCardView
    private lateinit var tileNfcWrite: MaterialCardView
    private lateinit var tileBlockedNotifications: MaterialCardView
    private lateinit var tileQr: MaterialCardView
    private lateinit var tileBarcode: MaterialCardView
    private lateinit var rowToolsShortcuts: LinearLayout
    private lateinit var rowScanShortcuts: LinearLayout

    private lateinit var cardQuickActions: View
    private lateinit var rowQuickActionsHeader: View
    private lateinit var dividerQuickActions: View
    private lateinit var tvQuickActionsTitle: TextView
    private lateinit var ivQuickActionsEdit: ImageView
    private lateinit var ivQuickActionsChevron: ImageView
    private lateinit var gridQuickActions: View
    private lateinit var rowManageShortcuts: LinearLayout
    private lateinit var rowUtilityShortcuts: LinearLayout

    // Next schedule
    private lateinit var cardNextSchedule: MaterialCardView
    private lateinit var tvNextScheduleValue: TextView
    private var nextChangedReceiver: BroadcastReceiver? = null

    // Blocked list
    private lateinit var cardBlockedApps: MaterialCardView
    private lateinit var rowBlockedAppsHeader: View
    private lateinit var ivBlockedAppsChevron: ImageView
    private lateinit var layoutBlockedAppsContent: View
    private lateinit var rvBlocked: RecyclerView
    private lateinit var layoutBlockedAppsEmpty: View
    private lateinit var blockedHeader: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnPickApps: MaterialButton

    private val blockedAdapter = BlockedAppsAdapter(
        onAppClick = { app -> showBlockedAppQuickActions(app) },
        onEditClick = { app -> showBlockedAppQuickActions(app) },
        canEdit = { ensureCanRemoveBlockedApp(showFeedback = false) }
    )

    private fun notifyBlockedChipsChanged() {
        notifyBlockedAdapterRangeChanged(PAYLOAD_BLOCKED_CHIPS)
    }

    private fun notifyBlockedEditStateChanged() {
        notifyBlockedAdapterRangeChanged(PAYLOAD_BLOCKED_EDIT_STATE)
    }

    private fun notifyBlockedAdapterRangeChanged(payload: Any) {
        val count = blockedAdapter.itemCount
        if (count <= 0) {
            return
        }

        rvBlocked.post {
            val postedCount = blockedAdapter.itemCount
            if (postedCount > 0) {
                blockedAdapter.notifyItemRangeChanged(0, postedCount, payload)
            }
        }
    }

    private var blockedListRefreshSeq: Int = 0
    private var blockedListRefreshJob: Job? = null

    // For micro-animations: avoid animating every 1s refresh
    private var lastEnabledUi: Boolean? = null

    // The blocked list rows show a "Blocked now" chip.
    // That chip depends on runtime state (enabled/emergency).
    // Keep a small key so we can refresh row bindings when these states change without re-binding every 1s tick.
    private var lastBlockedChipKey: String? = null
    private var lastBlockedEditEnabled: Boolean? = null

    // Live updates: refresh "Blocked now" chips as soon as limits are reached while the app is open.
    private var livePrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ensure the flow in SwitchModeStore reflects the current prefs
        SwitchModeStore.ensureInit(applicationContext)

        // Onboarding gate (versioned)
        val sp = getSharedPreferences("loqin_prefs", MODE_PRIVATE)

        val onboardingVersion = sp.getIntCompat("onboarding_version", 0)

        // Versioned onboarding follows the build version, so refreshed setup flows can run once after updates.
        val shouldRunOnboarding = onboardingVersion < OnboardingActivity.ONBOARDING_VERSION
        val skipOnboardingGateOnce = intent.getBooleanExtra(EXTRA_SKIP_ONBOARDING_GATE_ONCE, false)
        if (shouldRunOnboarding && !skipOnboardingGateOnce) {
            startActivity(Intent(this, OnboardingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // Classic system-bars for the main screen:
        // - Bottom nav sits flush (no extra inset padding)
        // - Status bar keeps system look (no accent color bleed)
        EdgeToEdgeUtils.setupClassic(
            activity = this,
            toolbar = toolbar,
            bottomNav = bottomNav
        )

        // Match Schedules look: keep BottomNav slightly above the gesture area on all devices
        EdgeToEdgeUtils.applyBottomNavGestureInset(bottomNav)

        // Keep status/navigation bars neutral (no accent bleed into system bar)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setSupportActionBar(toolbar)

        scrollMain = findViewById(R.id.scrollMain)
        // Foqos restyle: toolbar is inline in the scroll (scrolls away like Foqos's title),
        // so the scroll view must consume the status-bar inset itself.
        ViewCompat.setOnApplyWindowInsetsListener(scrollMain) { v, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = status + homeDp(4f))
            insets
        }

        // UI refs

        layoutHomeRoot = findViewById(R.id.layoutHomeRoot)
        layoutStatusContent = findViewById(R.id.layoutStatusContent)
        layoutProtectionStatus = findViewById(R.id.layoutProtectionStatus)
        cardStatus = findViewById(R.id.cardStatus)
        spaceBeforeStatusCard = findViewById(R.id.spaceBeforeStatusCard)
        ivStatusIcon = findViewById(R.id.ivStatusIcon)
        tvSwitchMode = findViewById(R.id.tvSwitchMode)
        tvActiveDuration = findViewById(R.id.tvActiveDuration)
        tvControlModeHint = findViewById(R.id.tvControlModeHint)
        styleActiveDurationPill()
        tvActiveDuration.isClickable = true
        tvActiveDuration.isFocusable = true
        tvActiveDuration.setOnClickListener {
            startActivity(ActiveTimeActivity.intent(this))
        }
        tvActiveProfile = findViewById(R.id.tvActiveProfile)
        rowActiveProfile = findViewById(R.id.rowActiveProfile)
        tvNfcLockedHint = findViewById(R.id.tvNfcLockedHint)
        btnToggle = findViewById(R.id.btnToggle)
        btnSimplePickApps = findViewById(R.id.btnSimplePickApps)
        tvTempHint = findViewById(R.id.tvTempHint)
        tvEmergencyHint = findViewById(R.id.tvEmergencyHint)
        tvTempTileTitle = findViewById(R.id.tvTempTileTitle)
        tvTempTileSubtitle = findViewById(R.id.tvTempTileSubtitle)
        tvEmergencyTileTitle = findViewById(R.id.tvEmergencyTileTitle)
        tvEmergencyTileSubtitle = findViewById(R.id.tvEmergencyTileSubtitle)

        profileDropdown = findViewById(R.id.profileDropdown)
        layoutProfileDropdown = findViewById(R.id.layoutProfileDropdown)
        dividerBeforeProfileDropdown = findViewById(R.id.dividerBeforeProfileDropdown)
        cardSetup = findViewById(R.id.cardSetup)
        tvSetupTitle = findViewById(R.id.tvSetupTitle)
        tvSetupSubtitle = findViewById(R.id.tvSetupSubtitle)
        tvSetupDesc = findViewById(R.id.tvSetupDesc)
        btnFinishSetup = findViewById(R.id.btnFinishSetup)

        cardActivity = findViewById(R.id.cardActivity)
        activityHeatmap = findViewById(R.id.activityHeatmap)
        tvHeatmapLegend = findViewById(R.id.tvHeatmapLegend)
        btnActivityHide = findViewById(R.id.btnActivityHide)
        tvActivityDetail = findViewById(R.id.tvActivityDetail)
        tvHeroProfileName = findViewById(R.id.tvHeroProfileName)
        tvHeroChips = findViewById(R.id.tvHeroChips)
        tvHeroStrategy = findViewById(R.id.tvHeroStrategy)
        heroProfileRoot = findViewById(R.id.heroProfileRoot)
        tvHeroStatApps = findViewById(R.id.tvHeroStatApps)
        tvHeroStatDomains = findViewById(R.id.tvHeroStatDomains)
        tvHeroStatBlocks = findViewById(R.id.tvHeroStatBlocks)
        findViewById<View>(R.id.heroProfileRoot).setOnClickListener {
            ProfileStore.getCurrent(this)?.let { openProfileEditSheet(it) }
        }
        findViewById<View>(R.id.ibtnHeroEdit).setOnClickListener {
            ProfileStore.getCurrent(this)?.let { openProfileEditSheet(it) }
        }
        findViewById<View>(R.id.rowHeroStrategy).setOnClickListener {
            openBlockingModeSheet()
        }
        btnActivityHide.setOnClickListener {
            activityHidden = !activityHidden
            val gridVisible = !activityHidden
            cardActivity.visibility = if (gridVisible) View.VISIBLE else View.GONE
            btnActivityHide.findViewById<TextView>(R.id.tvHeaderPillLabel)?.text =
                getString(if (gridVisible) R.string.activity_hide else R.string.activity_show)
        }
        findViewById<View>(R.id.btnMoreInsights)?.setOnClickListener {
            ActivityTransitionCompat.switchWithoutAnimation(
                activity = this,
                intent = Intent(this, AppWebsiteUsageActivity::class.java),
            )
        }
        applyHeatmapLegend()
        activityHeatmap.onDaySelected = { index -> onHeatmapDaySelected(index) }
        refreshActivityHeatmap()

        profileRowsContainer = findViewById(R.id.profileRowsContainer)
        btnManageProfiles = findViewById(R.id.btnManageProfiles)
        btnManageProfiles.setOnClickListener {
            openSwitchProfileSheet()
        }

        tileManageApps = findViewById(R.id.tileManageApps)
        tileProfiles = findViewById(R.id.tileProfiles)
        tileWriteNfc = findViewById(R.id.tileWriteNfc)
        tileToggleOptions = findViewById(R.id.tileToggleOptions)
        tileNfcWrite = findViewById(R.id.tileNfcWrite)
        tileBlockedNotifications = findViewById(R.id.tileBlockedNotifications)
        tileQr = findViewById(R.id.tileQr)
        tileBarcode = findViewById(R.id.tileBarcode)
        rowToolsShortcuts = findViewById(R.id.rowToolsShortcuts)
        rowScanShortcuts = findViewById(R.id.rowScanShortcuts)

        cardQuickActions = findViewById(R.id.cardQuickActions)
        rowQuickActionsHeader = findViewById(R.id.rowQuickActionsHeader)
        dividerQuickActions = findViewById(R.id.dividerQuickActions)
        tvQuickActionsTitle = findViewById(R.id.tvQuickActionsTitle)
        ivQuickActionsEdit = findViewById(R.id.ivQuickActionsEdit)
        ivQuickActionsChevron = findViewById(R.id.ivQuickActionsChevron)
        gridQuickActions = findViewById(R.id.gridQuickActions)
        rowManageShortcuts = findViewById(R.id.rowManageShortcuts)
        rowUtilityShortcuts = findViewById(R.id.rowUtilityShortcuts)

        cardNextSchedule = findViewById(R.id.cardNextSchedule)
        tvNextScheduleValue = findViewById(R.id.tvNextScheduleValue)

        cardBlockedApps = findViewById(R.id.cardBlockedApps)
        rowBlockedAppsHeader = findViewById(R.id.rowBlockedAppsHeader)
        ivBlockedAppsChevron = findViewById(R.id.ivBlockedAppsChevron)
        layoutBlockedAppsContent = findViewById(R.id.layoutBlockedAppsContent)
        rvBlocked = findViewById(R.id.rvBlocked)
        layoutBlockedAppsEmpty = findViewById(R.id.layoutBlockedAppsEmpty)
        blockedHeader = findViewById(R.id.blockedHeader)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnPickApps = findViewById(R.id.btnPickApps)

        // Update button colors to the selected accent color
        applyAccentToButtons()

        // Micro animations: subtle press-scale on interactive cards/buttons
        listOf(
            tileManageApps, tileProfiles, tileWriteNfc, tileToggleOptions, tileNfcWrite, tileBlockedNotifications, tileQr, tileBarcode,
            cardNextSchedule, rowActiveProfile, rowQuickActionsHeader
        ).forEach { applyPressScale(it) }
        applyPressScale(btnToggle)

        // Primary toggle
        btnToggle.setOnClickListener {
            trackPrimaryToggleTapForTempNudge()
            toggleSwitchIfAllowed()
        }

        btnSimplePickApps.setOnClickListener { openAppPickerIfUnlocked() }

        // Optional text hint also opens temp mode
        tvTempHint.setOnClickListener {
            val opened = showTempToggleSheet()
            if (opened) {
                markTempModeDiscovered(showSnack = false)
            }
        }

        // Emergency quick entry near temporary mode
        tvEmergencyHint.setOnClickListener {
            showEmergencyQuickSheet()
        }

        // Active profile quick-jump
        rowActiveProfile.setOnClickListener { openProfiles() }

        // Setup CTA
        btnFinishSetup.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        // Quick actions
        rowQuickActionsHeader.setOnClickListener { toggleQuickActionsExpanded() }
        ivQuickActionsEdit.setOnClickListener { showQuickActionsCustomizeDialog() }
        tileManageApps.setOnClickListener {
            openRulesDestination(Intent(this, AppPickerActivity::class.java))
        }
        tileProfiles.setOnClickListener {
            openRulesDestination(Intent(this, ManageProfilesActivity::class.java))
        }
        tileWriteNfc.setOnClickListener {
            openRulesDestination(Intent(this, ManageBlockedWebsitesActivity::class.java))
        }
        tileToggleOptions.setOnClickListener {
            openRulesDestination(Intent(this, InAppRulesActivity::class.java))
        }
        tileQr.setOnClickListener { openQrScannerDirectly() }
        tileQr.setOnLongClickListener {
            showQrChoiceDialog()
            true
        }
        tileBarcode.setOnClickListener { openBarcodeScannerDirectly() }
        tileBarcode.setOnLongClickListener {
            showBarcodeChoiceDialog()
            true
        }
        tileNfcWrite.setOnClickListener {
            if (isNfcTagWritingLocked()) {
                EditingLockGuard.showLockedDialog(this, R.string.edit_locked_write_nfc_tags)
            } else {
                startActivity(Intent(this, NfcWriterActivity::class.java))
            }
        }
        tileBlockedNotifications.setOnClickListener {
            openBlockedNotificationsIfAllowed()
        }

        syncScanQuickActions()

        // Next schedule card
        cardNextSchedule.setOnClickListener {
            startActivity(Intent(this, SchedulesActivity::class.java))
        }

        // Managed app list
        rowBlockedAppsHeader.setOnClickListener { toggleManagedAppListExpanded() }
        btnPickApps.setOnClickListener { openAppPickerIfUnlocked() }
        updateManagedAppListExpandedUi(animate = false)

        // Profile dropdown: selection only, no free text allowed
        profileDropdown.inputType = InputType.TYPE_NULL
        profileDropdown.keyListener = null
        profileDropdown.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !ensureCanSwitchProfiles(showFeedback = false)) {
                profileDropdown.dismissDropDown()
                profileDropdown.clearFocus()
            }
        }
        profileDropdown.setOnClickListener { v ->
            if (!ensureCanSwitchProfiles(showFeedback = true)) {
                profileDropdown.dismissDropDown()
                profileDropdown.clearFocus()
                return@setOnClickListener
            }
            v.post {
                if (v.windowToken != null && !isFinishing && !isDestroyed) {
                    profileDropdown.showDropDown()
                }
            }
        }

        // Blocked apps list (non-scrollable – parent scrolls)
        rvBlocked.layoutManager = object : LinearLayoutManager(this) {
            override fun canScrollVertically(): Boolean = false
        }
        rvBlocked.isNestedScrollingEnabled = false
        rvBlocked.setHasFixedSize(false)
        rvBlocked.adapter = blockedAdapter

        // React to global enable/disable state
        lifecycleScope.launch {
            SwitchModeStore.enabledFlow.collect { enabled ->
                updateSwitchState()
                if (enabled) {
                    BlockingRuntime.ensureRunning(this@MainActivity)
                } else {
                    val keepForTimer =
                        SwitchModeStore.getTemporaryRemainingMillis(this@MainActivity) > 0L ||
                            SwitchModeStore.getTemporaryEnableRemainingMillis(this@MainActivity) > 0L ||
                            EmergencyBypassStore.isActive(this@MainActivity)

                    if (keepForTimer) {
                        BlockingRuntime.ensureRunning(this@MainActivity)
                    } else {
                        BlockingRuntime.stop(this@MainActivity)
                    }
                }
            }
        }

        // only runs when activity is active
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (currentCoroutineContext().isActive) {
                    updateSwitchState()
                    delay(1000)
                }
            }
        }

        refreshProfilesUi()
        refreshBlockedList()
        updateSetupCard()
        updateNextScheduleCard()
        updateQuickActionsVisibility()
        updateTempHintVisibility()
        updateEmergencyHintVisibility()
        refreshHomeLayout()
        checkAndShowSessionMissedNotifications()

        setupBottomNav(bottomNav)
    }

    override fun onResume() {
        super.onResume()
        syncScanQuickActions()
        refreshActivityHeatmap()

        ExactAlarmPermissionSync.syncAndReschedule(this, reason = "main_resume")
        BlockingRuntime.ensureRunning(this)

        refreshProfilesUi()
        refreshBlockedList()
        updateSwitchState()
        updateSetupCard()
        updateNextScheduleCard()
        updateQuickActionsVisibility()
        updateTempHintVisibility()
        updateEmergencyHintVisibility()
        refreshHomeLayout()
        checkAndShowSessionMissedNotifications()

        // Refresh accents when theme changes (toolbar stays flat surface — Foqos restyle)
        applyAccentToButtons()

        // Bottom navigation state
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav)
        scheduleBottomNavTour(bottomNav)

        // refresh menu visibility (QR toggle)
        invalidateOptionsMenu()

        // Keep the user informed when protection is inactive (e.g. Accessibility disabled)
        ProtectionStatusNotifier.refresh(this)

        // Optional: show "update available" prompt when Google Play has a newer version
        PlayStoreUpdatePrompt.check(this)

        // Live next schedule updates (optional)
        if (nextChangedReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == SchedulePlanner.ACTION_NEXT_CHANGED) {
                        updateNextScheduleCard()
                    }
                }
            }
            val filter = IntentFilter(SchedulePlanner.ACTION_NEXT_CHANGED)
            val registered = runCatching {
                ContextCompat.registerReceiver(
                    this,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }.isSuccess
            nextChangedReceiver = if (registered) receiver else null
        }

        // Listen for limit reached/open-count changes so "Blocked now" chips update immediately while the app is in the foreground.
        if (livePrefsListener == null) {
            val sp = getSharedPreferences("loqin_prefs", MODE_PRIVATE)
            livePrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key.isNullOrBlank()) return@OnSharedPreferenceChangeListener

                val isLimitReachedKey = key.startsWith("limit_reached_")
                val isOpenCountKey = key.startsWith("open_count_")
                val isManagedRuleKey =
                    key.startsWith("blocked_apps_") ||
                        key.startsWith("allowed_apps_") ||
                        key.startsWith("profile_rule_mode__") ||
                        key.startsWith("usage_limit_min__") ||
                        key.startsWith("attempt_limit__") ||
                        key.startsWith("session_limit_min__")

                when {
                    isManagedRuleKey -> {
                        // Limits can add/remove apps from the main managed list, so fully refresh it.
                        refreshBlockedList()
                    }
                    isLimitReachedKey || isOpenCountKey -> {
                        // Re-bind rows so chips update; keep it lightweight.
                        notifyBlockedChipsChanged()
                    }
                }
            }
            sp.registerOnSharedPreferenceChangeListener(livePrefsListener)
        }
    }

    override fun onPause() {
        super.onPause()
        nextChangedReceiver?.let { runCatching { unregisterReceiver(it) } }
        nextChangedReceiver = null

        livePrefsListener?.let {
            runCatching {
                getSharedPreferences("loqin_prefs", MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(it)
            }
        }
        livePrefsListener = null
    }

    private fun setupBottomNav(bottomNav: BottomNavigationView) {
        // Always set "home" active in main
        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true

                R.id.nav_rules -> {
                    RulesHubActivity.openWithAccessCheck(this)
                }

                R.id.nav_activity -> {
                    ActivityTransitionCompat.switchWithoutAnimation(
                        activity = this,
                        intent = Intent(this, ActivityHubActivity::class.java),
                    )
                    true
                }

                R.id.nav_settings -> {
                    SettingsActivity.openWithAccessCheck(this)
                }

                else -> false
            }
        }
    }

    private data class BottomNavTourStep(
        val itemId: Int,
        @param:DrawableRes val iconRes: Int,
        @param:StringRes val titleRes: Int,
        @param:StringRes val descriptionRes: Int,
    )

    private data class BottomNavTourHighlight(
        val itemId: Int,
    )

    private fun scheduleBottomNavTour(bottomNav: BottomNavigationView) {
        // SPA restyle: bottom navigation removed from Home.
        return
        val prefs = getSharedPreferences(PREFS_UI_HINTS, MODE_PRIVATE)
        val pending = prefs.getBoolean(KEY_BOTTOM_NAV_TOUR_PENDING, false)
        val completedVersion = prefs.getIntCompat(KEY_BOTTOM_NAV_TOUR_VERSION, 0)
        if (!pending || completedVersion >= BOTTOM_NAV_TOUR_VERSION) {
            return
        }
        if (bottomNavTourScheduled || bottomNavTourShowing) {
            return
        }

        bottomNavTourScheduled = true
        bottomNav.postDelayed({
            bottomNavTourScheduled = false
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                return@postDelayed
            }
            if (!hasWindowFocus()) {
                scheduleBottomNavTour(bottomNav)
                return@postDelayed
            }
            showBottomNavTourStep(bottomNav, 0)
        }, 700L)
    }

    private fun showBottomNavTourStep(
        bottomNav: BottomNavigationView,
        index: Int,
    ) {
        val steps = listOf(
            BottomNavTourStep(
                itemId = R.id.nav_home,
                iconRes = R.drawable.loqin_home_nav_24,
                titleRes = R.string.nav_tour_home_title,
                descriptionRes = R.string.nav_tour_home_desc,
            ),
            BottomNavTourStep(
                itemId = R.id.nav_rules,
                iconRes = R.drawable.apps_24,
                titleRes = R.string.nav_tour_rules_title,
                descriptionRes = R.string.nav_tour_rules_desc,
            ),
            BottomNavTourStep(
                itemId = R.id.nav_activity,
                iconRes = R.drawable.bar_chart_24,
                titleRes = R.string.nav_tour_activity_title,
                descriptionRes = R.string.nav_tour_activity_desc,
            ),
            BottomNavTourStep(
                itemId = R.id.nav_settings,
                iconRes = R.drawable.tune_24,
                titleRes = R.string.nav_tour_settings_title,
                descriptionRes = R.string.nav_tour_settings_desc,
            ),
        )
        val step = steps.getOrNull(index) ?: run {
            finishBottomNavTour()
            return
        }

        bottomNavTourShowing = true
        val accent = AccentColor.getAccentColorInt(this)
        showBottomNavTourDimOverlay(bottomNav)

        val anchor = bottomNav.findViewById<View>(step.itemId)
        highlightBottomNavTourAnchor(bottomNav, anchor, accent)

        val content = layoutInflater.inflate(R.layout.dialog_bottom_nav_tour, null)
        content.findViewById<ImageView>(R.id.ivNavTourIcon).apply {
            setImageResource(step.iconRes)
            imageTintList = ColorStateList.valueOf(accent)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = resources.displayMetrics.density * 22f
                setColor(ColorUtils.setAlphaComponent(accent, 0x24))
            }
        }
        content.findViewById<TextView>(R.id.tvNavTourStep).apply {
            text = getString(R.string.nav_tour_step, index + 1, steps.size)
            setTextColor(accent)
        }
        content.findViewById<TextView>(R.id.tvNavTourTitle).setText(step.titleRes)
        content.findViewById<TextView>(R.id.tvNavTourDescription).setText(step.descriptionRes)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(content)
            .setCancelable(false)
            .setNegativeButton(R.string.nav_tour_skip) { _, _ ->
                finishBottomNavTour()
            }
            .setPositiveButton(
                if (index == steps.lastIndex) R.string.nav_tour_done else R.string.nav_tour_next
            ) { _, _ ->
                if (index == steps.lastIndex) {
                    finishBottomNavTour()
                } else {
                    // Keep the navigation highlight alive while the current dialog closes.
                    // The next step swaps the checked/tinted destination immediately instead of briefly restoring the whole bar between steps.
                    bottomNavTourDialog = null
                    bottomNav.post {
                        showBottomNavTourStep(bottomNav, index + 1)
                    }
                }
            }
            .showAccented()

        dialog.window?.apply {
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
        }
        dialog.setOnDismissListener {
            // During Next, bottomNavTourDialog is cleared before this dialog dismisses so the current highlight is intentionally preserved until the next step replaces it.
            if (bottomNavTourDialog === dialog) {
                bottomNavTourDialog = null
                if (bottomNavTourShowing) {
                    finishBottomNavTour()
                }
            }
        }
        bottomNavTourDialog = dialog
    }

    private fun highlightBottomNavTourAnchor(
        bottomNav: BottomNavigationView,
        anchor: View?,
        accent: Int,
    ) {
        if (anchor == null) {
            return
        }

        if (bottomNavTourOriginalIconTint == null) {
            bottomNavTourOriginalIconTint = bottomNav.itemIconTintList
        }
        if (bottomNavTourOriginalTextTint == null) {
            bottomNavTourOriginalTextTint = bottomNav.itemTextColor
        }

        val fallback = MaterialColors.getColor(
            bottomNav,
            com.google.android.material.R.attr.colorOnSurface
        )
        val inactiveIconColor = bottomNavTourOriginalIconTint?.getColorForState(
            intArrayOf(-android.R.attr.state_checked),
            bottomNavTourOriginalIconTint?.defaultColor ?: fallback,
        ) ?: ColorUtils.setAlphaComponent(fallback, 0x99)
        val inactiveTextColor = bottomNavTourOriginalTextTint?.getColorForState(
            intArrayOf(-android.R.attr.state_checked),
            bottomNavTourOriginalTextTint?.defaultColor ?: fallback,
        ) ?: ColorUtils.setAlphaComponent(fallback, 0x99)

        // Drive the tour through BottomNavigationView's own checked state instead of directly tinting its internal ImageView/TextViews. 
        // The old approach was fragile:
        // Material could re-bind the child views while dialogs changed, which briefly made every destination accented (or every destination neutral). 
        // A checked-state tint guarantees exactly one highlighted destination at a time.
        bottomNav.itemIconTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(),
            ),
            intArrayOf(accent, inactiveIconColor),
        )
        bottomNav.itemTextColor = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(),
            ),
            intArrayOf(accent, inactiveTextColor),
        )
        bottomNav.menu.findItem(anchor.id)?.isChecked = true
        bottomNavTourHighlight = BottomNavTourHighlight(anchor.id)
    }

    private fun restoreBottomNavTourIcons(bottomNav: BottomNavigationView) {
        bottomNav.itemIconTintList = bottomNavTourOriginalIconTint
        bottomNav.itemTextColor = bottomNavTourOriginalTextTint
        bottomNav.menu.findItem(R.id.nav_home)?.isChecked = true
        bottomNavTourHighlight = null
        bottomNavTourOriginalIconTint = null
        bottomNavTourOriginalTextTint = null
    }

    private fun showBottomNavTourDimOverlay(bottomNav: BottomNavigationView) {
        val overlay = findViewById<View>(R.id.bottomNavTourDimOverlay)
        val params = overlay.layoutParams as ViewGroup.MarginLayoutParams
        if (params.bottomMargin != bottomNav.height) {
            params.bottomMargin = bottomNav.height
            overlay.layoutParams = params
        }
        overlay.isVisible = true
    }

    private fun finishBottomNavTour() {
        bottomNavTourShowing = false
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        restoreBottomNavTourIcons(bottomNav)
        findViewById<View>(R.id.bottomNavTourDimOverlay).isVisible = false

        val dialog = bottomNavTourDialog
        bottomNavTourDialog = null
        dialog?.dismiss()
        getSharedPreferences(PREFS_UI_HINTS, MODE_PRIVATE).edit {
            putBoolean(KEY_BOTTOM_NAV_TOUR_PENDING, false)
            putInt(KEY_BOTTOM_NAV_TOUR_VERSION, BOTTOM_NAV_TOUR_VERSION)
        }
    }

    private fun applyAccentToButtons() {
        val accent = AccentColor.getAccentColorInt(this)
        val tint = ColorStateList.valueOf(accent)
        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.52) {
            Color.BLACK
        } else {
            Color.WHITE
        }

        if (::btnToggle.isInitialized) {
            styleHeroToggle(SwitchModeStore.isEnabled(this))
        }
        btnFinishSetup.backgroundTintList = tint
        btnFinishSetup.setTextColor(onAccent)
        // Make the icon match the button text (otherwise it may stay default/black).
        btnFinishSetup.iconTint = ColorStateList.valueOf(onAccent)

    }

    private fun applyPressScale(v: View) {
        // Small scale feedback on press.
        // IMPORTANT: never consume touch events here, otherwise long-press handlers
        // (e.g. btnToggle temporary enable/disable sheet) stop firing.
        var downX = 0f
        var downY = 0f
        val slop = android.view.ViewConfiguration.get(v.context).scaledTouchSlop

        v.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    view.animate().scaleX(0.985f).scaleY(0.985f).setDuration(80).start()
                }

                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    val movedTooFar = kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop
                    if (!movedTooFar && !view.hasOnClickListeners()) {
                        view.performClick()
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            false // let the view handle click + long-click normally
        }
    }

    // Subtle fade helpers (used for cards and empty states)
    private fun View.showFade(duration: Long = 160) {
        if (isVisible) {
            return
        }
        alpha = 0f
        isVisible = true
        animate().alpha(1f).setDuration(duration).start()
    }

    private fun View.hideFade(duration: Long = 140) {
        if (!isVisible) {
            return
        }
        animate().alpha(0f).setDuration(duration).withEndAction {
            isVisible = false
            alpha = 1f
        }.start()
    }

    private fun View.hideNow() {
        animate().cancel()
        alpha = 1f
        isVisible = false
    }

    private fun View.hideHomeLayoutView(mode: String = homeLayoutMode()) {
        // Custom Home can reorder views in the same refresh pass.
        // Reordering a fading view can cancel the animation end action and leave an empty row/card behind, so hidden Custom Home blocks are removed immediately instead of fading out.
        if (mode == ToggleOptionsActivity.HOME_MODE_CUSTOM) {
            hideNow()
        } else {
            hideFade()
        }
    }

    private fun isNfcLocked(): Boolean {
        val enabled = SwitchModeStore.isEnabled(this)
        val requireNfc = SwitchModeStore.isNfcRequiredForDisable(this)
        return enabled && requireNfc
    }

    private fun isAppPickingLockedWhileEnabled(): Boolean {
        if (!EditingLockGuard.isLocked(this)) return false
        return !AutomationModeStore.isMixedAllowAppPicking(this)
    }

    private fun isProfileSwitchLockedWhileEnabled(): Boolean {
        val enabled = SwitchModeStore.isEnabled(this)
        val temporaryOverrideActive = SwitchModeStore.hasActiveTemporaryOverride(this)
        val emergencyActive = EmergencyBypassStore.isActive(this)
        val emergencyPaused = EmergencyBypassStore.isPaused(this)
        if (!enabled && !temporaryOverrideActive && !emergencyActive) {
            return false
        }

        if (temporaryOverrideActive) {
            return true
        }

        if (emergencyActive) {
            return false
        }

        val requireNfc = SwitchModeStore.isNfcRequiredForDisable(this)
        if (requireNfc || (enabled && emergencyPaused)) {
            return true
        }

        return !AutomationModeStore.isProfileSwitchingAllowedWhileEnabled(this)
    }

    private fun ensureCanRemoveBlockedApp(showFeedback: Boolean = true): Boolean {
        if (isNfcLocked() || EditingLockGuard.isLocked(this)) {
            if (showFeedback) {
                snackRoot().showWarnPill(R.string.toast_disable_loqin_to_edit_app_limits)
            }
            return false
        }
        return true
    }

    // Anchor defaults to the activity content, but callers inside a dialog/sheet
    // window must pass a view from that window, otherwise the pill is hidden behind it.
    private fun ensureCanSwitchProfiles(showFeedback: Boolean = true, anchor: View? = null): Boolean {
        val pillAnchor = anchor ?: snackRoot()
        if (isNfcLocked()) {
            if (showFeedback) {
                pillAnchor.showWarnPill(R.string.toast_cannot_change_profile_while_locked)
            }
            return false
        }
        if (isProfileSwitchLockedWhileEnabled()) {
            if (showFeedback) {
                pillAnchor.showWarnPill(R.string.toast_disable_loqin_to_switch_profiles)
            }
            return false
        }
        return true
    }

    private fun toggleSwitchIfAllowed() {
        val enabled = SwitchModeStore.isEnabled(this)
        val allowMissingBarcodeSafetyDisable =
            enabled && AutomationModeStore.shouldAllowManualDisableForMissingBarcodeSetup(this)

        val canChange = if (enabled) {
            AutomationModeStore.isButtonAllowed(this) || allowMissingBarcodeSafetyDisable
        } else {
            AutomationModeStore.canButtonEnable(this)
        }
        if (!canChange) {
            val msg = if (enabled && AutomationModeStore.isButtonEnableAllowed(this)) {
                R.string.mode_blocked_button_disable_enable_only
            } else {
                R.string.mode_blocked_button_action
            }
            snackRoot().showWarnPill(msg)
            return
        }

        if (allowMissingBarcodeSafetyDisable) {
            AppLogStore.append(this, "Safety", "Allowing manual disable because only barcode control is enabled but no managed barcodes exist")
        }

        if (enabled && isNfcLocked()) {
            snackRoot().showWarnPill(R.string.toast_cannot_disable_while_locked)
            return
        }
        val nextEnabled = !enabled
        if (SwitchModeStore.setEnabled(this, nextEnabled, allowNfcBypass = false)) {
            AppLogStore.append(
                this,
                "Profiles",
                "Manual toggle action=${if (nextEnabled) "enable" else "disable"} profile=${ProfileStore.getCurrent(this)}"
            )
        }
        updateSwitchState()
    }

    private enum class TempSheetMode { DISABLE, ENABLE }
    private data class PauseUntilOption(
        val label: String,
        val runAction: () -> Unit
    )

    /**
     * Styles a bottom sheet and opens it already expanded. Configuring the
     * behavior before show avoids the appear-then-glide jump that moves taps
     * onto a moving target.
     */
    private fun BottomSheetDialog.prepareExpandedSheet() {
        findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { bs ->
            val topRadius = 24 * resources.displayMetrics.density + 0.5f
            bs.background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    topRadius, topRadius,
                    topRadius, topRadius,
                    0f, 0f,
                    0f, 0f
                )
                setColor(ContextCompat.getColor(this@MainActivity, R.color.foqos_surface))
            }
            BottomSheetBehavior.from(bs).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun showTempToggleSheet(): Boolean {
        val enabledNow = SwitchModeStore.isEnabled(this)
        val tempDisableRemaining = SwitchModeStore.getTemporaryRemainingMillis(this)
        val tempEnableRemaining = SwitchModeStore.getTemporaryEnableRemainingMillis(this)
        val canUseTemporaryAction = when {
            tempDisableRemaining > 0L -> true // Cancelling a pause only restores protection.
            tempEnableRemaining > 0L -> AutomationModeStore.canButtonEnable(this)
            enabledNow -> AutomationModeStore.isButtonAllowed(this)
            else -> AutomationModeStore.canButtonEnable(this)
        }

        if (!canUseTemporaryAction) {
            snackRoot().showWarnPill(getString(R.string.mode_blocked_button_action))
            return false
        }

        val mode = when {
            tempDisableRemaining > 0L -> TempSheetMode.DISABLE
            tempEnableRemaining > 0L -> TempSheetMode.ENABLE
            enabledNow -> TempSheetMode.DISABLE
            else -> TempSheetMode.ENABLE
        }

        val hasActive = (tempDisableRemaining > 0L) || (tempEnableRemaining > 0L)
        val currentProfile = ProfileStore.getCurrent(this).orEmpty().trim().ifEmpty { "Default" }
        val hasCaps = (mode == TempSheetMode.DISABLE) && TempPauseStore.hasCaps(this, currentProfile)
        val remainingPauses = if (hasCaps) TempPauseStore.remainingPauses(this, currentProfile) else Int.MAX_VALUE
        val remainingMinutes = if (hasCaps) TempPauseStore.remainingMinutes(this, currentProfile) else Int.MAX_VALUE
        val isPauseExhausted = hasCaps && (remainingPauses <= 0 || remainingMinutes <= 0)
        val maxAllowed = if (hasCaps) TempPauseStore.maxAllowedDurationMinutes(this, currentProfile) else Int.MAX_VALUE

        if (!hasActive && mode == TempSheetMode.DISABLE && isPauseExhausted) {
            val msg = if (remainingPauses <= 0) {
                getString(R.string.temp_pause_exhausted_pauses, TempPauseStore.usedCountToday(this, currentProfile))
            } else {
                getString(R.string.temp_pause_exhausted_minutes, TempPauseStore.usedMinutesToday(this, currentProfile))
            }
            snackRoot().showWarnPill(msg)
            return false
        }

        // If NFC lock is active while enabled, temporary disable actions are locked.
        // We still open the sheet so users can access emergency actions from here.
        val lockedByNfc = (mode == TempSheetMode.DISABLE && isNfcLocked())

        val accent = AccentColor.getAccentColorInt(this)
        val tint = ColorStateList.valueOf(accent)

        val sheet = BottomSheetDialog(this)
        val parent = findViewById<ViewGroup>(android.R.id.content)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_temp_toggle, parent, false)
        sheet.setContentView(view)
        sheet.prepareExpandedSheet()

        val ivIcon = view.findViewById<ImageView>(R.id.ivIcon)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvSubtitle)
        val tvNote = view.findViewById<TextView>(R.id.tvNote)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        val cardActiveTimer = view.findViewById<View>(R.id.cardActiveTimer)
        val dotActiveTimer = view.findViewById<View>(R.id.dotActiveTimer)
        val tvActiveTimerBadge = view.findViewById<TextView>(R.id.tvActiveTimerBadge)
        val tvRemaining = view.findViewById<TextView>(R.id.tvRemaining)
        val btnCancelTimer = view.findViewById<MaterialButton>(R.id.btnCancelTimer)

        val layoutPresetsSection = view.findViewById<View>(R.id.layoutPresetsSection)
        val layoutMoreOptionsSection = view.findViewById<View>(R.id.layoutMoreOptionsSection)

        val preset5m = view.findViewById<View>(R.id.preset5m)
        val preset15m = view.findViewById<View>(R.id.preset15m)
        val preset30m = view.findViewById<View>(R.id.preset30m)
        val preset60m = view.findViewById<View>(R.id.preset60m)

        val rowCustomDuration = view.findViewById<View>(R.id.rowCustomDuration)
        val roundelCustom = view.findViewById<View>(R.id.roundelCustom)
        val ivCustomIcon = view.findViewById<ImageView>(R.id.ivCustomIcon)

        val rowPauseUntil = view.findViewById<View>(R.id.rowPauseUntil)
        val roundelPauseUntil = view.findViewById<View>(R.id.roundelPauseUntil)
        val ivPauseUntilIcon = view.findViewById<ImageView>(R.id.ivPauseUntilIcon)
        val tvPauseUntilTitle = view.findViewById<TextView>(R.id.tvPauseUntilTitle)
        val tvPauseUntilSubtitle = view.findViewById<TextView>(R.id.tvPauseUntilSubtitle)

        val cardLockedNotice = view.findViewById<View>(R.id.cardLockedNotice)
        val tvLockedNotice = view.findViewById<TextView>(R.id.tvLockedNotice)

        val surfaceVariant = ContextCompat.getColor(this, R.color.foqos_surface_variant)
        val onSurface = ContextCompat.getColor(this, R.color.foqos_on_surface)

        ivIcon.imageTintList = tint
        view.findViewById<View>(R.id.roundelBg)?.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AccentColor.getAccentContainerColorInt(this@MainActivity))
            }

        btnClose.setOnClickListener { sheet.dismiss() }

        if (mode == TempSheetMode.DISABLE) {
            tvTitle.text = getString(R.string.nfc_action_temp_disable)
            tvSubtitle.text = if (lockedByNfc) {
                getString(R.string.dashboard_temp_sheet_sub_disable_locked)
            } else {
                getString(R.string.dashboard_temp_sheet_sub_disable)
            }
        } else {
            tvTitle.text = getString(R.string.nfc_action_temp_enable)
            val currentProfile = ProfileStore.getCurrent(this).orEmpty().trim()
            tvSubtitle.text = if (currentProfile.isBlank()) {
                getString(R.string.dashboard_temp_sheet_sub_enable)
            } else {
                getString(R.string.dashboard_temp_sheet_sub_enable_profile, currentProfile)
            }
        }

        val lockActiveTimerChanges = hasActive &&
            PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(ToggleOptionsActivity.KEY_LOCK_ACTIVE_TEMPORARY_TIMER, true)

        tvNote.text = getString(R.string.nfc_temp_hint_timer_behavior)

        if (lockedByNfc) {
            cardLockedNotice.visibility = View.VISIBLE
            cardLockedNotice.background = GradientDrawable().apply {
                cornerRadius = 14 * resources.displayMetrics.density + 0.5f
                setColor(surfaceVariant)
            }
            tvLockedNotice.text = getString(R.string.dashboard_temp_hint_locked_nfc)
        } else if (lockActiveTimerChanges) {
            cardLockedNotice.visibility = View.VISIBLE
            cardLockedNotice.background = GradientDrawable().apply {
                cornerRadius = 14 * resources.displayMetrics.density + 0.5f
                setColor(surfaceVariant)
            }
            tvLockedNotice.text = getString(R.string.dashboard_temp_active_changes_locked)
        } else if (isPauseExhausted) {
            cardLockedNotice.visibility = View.VISIBLE
            cardLockedNotice.background = GradientDrawable().apply {
                cornerRadius = 14 * resources.displayMetrics.density + 0.5f
                setColor(surfaceVariant)
            }
            tvLockedNotice.text = if (remainingPauses <= 0) {
                getString(R.string.temp_pause_exhausted_pauses, TempPauseStore.usedCountToday(this, currentProfile))
            } else {
                getString(R.string.temp_pause_exhausted_minutes, TempPauseStore.usedMinutesToday(this, currentProfile))
            }
        } else {
            cardLockedNotice.visibility = View.GONE
        }

        // Active timer card setup
        if (hasActive) {
            cardActiveTimer.visibility = View.VISIBLE
            cardActiveTimer.background = GradientDrawable().apply {
                cornerRadius = 16 * resources.displayMetrics.density + 0.5f
                setColor(surfaceVariant)
                setStroke((1.5f * resources.displayMetrics.density + 0.5f).toInt(), accent)
            }
            dotActiveTimer.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
            }
            tvActiveTimerBadge.setTextColor(accent)
            tvActiveTimerBadge.text = if (tempDisableRemaining > 0L) {
                getString(R.string.tile_temp_title_active_paused).uppercase()
            } else {
                getString(R.string.tile_temp_title_active_enabled).uppercase()
            }

            val handler = Handler(Looper.getMainLooper())
            val tick = object : Runnable {
                override fun run() {
                    val rem = when {
                        tempDisableRemaining > 0L -> SwitchModeStore.getTemporaryRemainingMillis(this@MainActivity)
                        tempEnableRemaining > 0L -> SwitchModeStore.getTemporaryEnableRemainingMillis(this@MainActivity)
                        else -> 0L
                    }

                    if (rem > 0L) {
                        tvRemaining.text = getString(
                            R.string.dashboard_temp_remaining_fmt,
                            formatRemainingShort(rem)
                        )
                        handler.postDelayed(this, 1000L)
                    } else {
                        cardActiveTimer.visibility = View.GONE
                    }
                }
            }
            sheet.setOnDismissListener { handler.removeCallbacksAndMessages(null) }
            tick.run()

            val canCancelActiveTimer = tempDisableRemaining > 0L || !lockActiveTimerChanges
            if (canCancelActiveTimer && !(lockedByNfc && tempDisableRemaining > 0L)) {
                btnCancelTimer.visibility = View.VISIBLE
                val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.5) Color.BLACK else Color.WHITE
                btnCancelTimer.backgroundTintList = ColorStateList.valueOf(accent)
                btnCancelTimer.setTextColor(onAccent)
                btnCancelTimer.text = getString(R.string.dashboard_temp_sheet_cancel_timer)
                btnCancelTimer.setOnClickListener {
                    if (tempDisableRemaining > 0L) {
                        SwitchModeStore.cancelTemporaryDisable(this)
                    } else if (tempEnableRemaining > 0L) {
                        SwitchModeStore.cancelTemporaryEnable(this)
                    }
                    sheet.dismiss()
                    updateSwitchState()
                }
            } else {
                btnCancelTimer.visibility = View.GONE
            }
        } else {
            cardActiveTimer.visibility = View.GONE
        }

        fun applyCardStyle(v: View) {
            val r = 14 * resources.displayMetrics.density + 0.5f
            v.background = GradientDrawable().apply {
                cornerRadius = r
                setColor(surfaceVariant)
            }
            val outValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            v.foreground = ContextCompat.getDrawable(this@MainActivity, outValue.resourceId)
        }

        fun setDuration(minutes: Int) {
            if (mode == TempSheetMode.DISABLE && hasCaps) {
                val max = TempPauseStore.maxAllowedDurationMinutes(this, currentProfile)
                if (minutes > max) {
                    view.showWarnPill(getString(R.string.temp_pause_exceeds_limit, max))
                    return
                }
            }
            val ms = minutes * 60_000L
            if (mode == TempSheetMode.DISABLE) {
                val ok = SwitchModeStore.setTemporarilyDisabled(this, ms)
                if (!ok) return
            } else {
                SwitchModeStore.setTemporarilyEnabled(this, ms)
            }
            sheet.dismiss()
            updateSwitchState()
        }

        if (!lockedByNfc && !lockActiveTimerChanges && !isPauseExhausted) {
            layoutPresetsSection.visibility = View.VISIBLE
            layoutMoreOptionsSection.visibility = View.VISIBLE

            fun setupPreset(v: View, minutes: Int) {
                applyCardStyle(v)
                if (minutes > maxAllowed) {
                    v.alpha = 0.35f
                    v.setOnClickListener { tapped ->
                        tapped.showWarnPill(getString(R.string.temp_pause_exceeds_limit, maxAllowed))
                    }
                } else {
                    v.alpha = 1.0f
                    v.setOnClickListener { setDuration(minutes) }
                }
            }

            setupPreset(preset5m, 5)
            setupPreset(preset15m, 15)
            setupPreset(preset30m, 30)
            setupPreset(preset60m, 60)

            applyCardStyle(rowCustomDuration)
            roundelCustom.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x22), surfaceVariant))
            }
            ivCustomIcon.imageTintList = tint
            if (maxAllowed < 1) {
                rowCustomDuration.alpha = 0.35f
                rowCustomDuration.setOnClickListener { tapped ->
                    tapped.showWarnPill(getString(R.string.temp_pause_exhausted))
                }
            } else {
                rowCustomDuration.alpha = 1.0f
                rowCustomDuration.setOnClickListener {
                    sheet.dismiss()
                    showClockDialDurationPicker(mode) { minutes -> setDuration(minutes) }
                }
            }

            if (mode == TempSheetMode.DISABLE) {
                val options = buildPauseUntilOptions(sheet)
                if (options.isNotEmpty()) {
                    rowPauseUntil.visibility = View.VISIBLE
                    applyCardStyle(rowPauseUntil)
                    roundelPauseUntil.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x22), surfaceVariant))
                    }
                    ivPauseUntilIcon.imageTintList = tint

                    if (options.size == 1) {
                        tvPauseUntilTitle.text = options.first().label
                        rowPauseUntil.setOnClickListener { options.first().runAction() }
                    } else {
                        tvPauseUntilTitle.text = getString(R.string.dashboard_pause_until_title)
                        tvPauseUntilSubtitle.text = options.joinToString(" · ") { it.label }
                        rowPauseUntil.setOnClickListener { showPauseUntilDialog(sheet) }
                    }
                } else {
                    rowPauseUntil.visibility = View.GONE
                }
            } else {
                rowPauseUntil.visibility = View.GONE
            }
        } else {
            layoutPresetsSection.visibility = View.GONE
            layoutMoreOptionsSection.visibility = View.GONE
        }
        sheet.show()
        return true
    }

    private fun addPauseUntilMenuOption(
        sheet: BottomSheetDialog,
        addOption: (String, () -> Unit) -> Unit
    ) {
        val options = buildPauseUntilOptions(sheet)
        if (options.size == 1) {
            val option = options.first()
            addOption(option.label) { option.runAction() }
        } else if (options.size > 1) {
            addOption(getString(R.string.dashboard_pause_until_title)) {
                showPauseUntilDialog(sheet)
            }
        }
    }

    private fun buildPauseUntilOptions(sheet: BottomSheetDialog): List<PauseUntilOption> {
        val now = System.currentTimeMillis()
        val currentProfile = ProfileStore.getCurrent(this).orEmpty().trim().ifEmpty { "Default" }
        val maxAllowedMin = TempPauseStore.maxAllowedDurationMinutes(this, currentProfile)
        val maxAllowedMs = if (maxAllowedMin == Int.MAX_VALUE) Long.MAX_VALUE else maxAllowedMin * 60_000L

        fun pauseFor(ms: Long, log: String) {
            val clamped = ms.coerceIn(60_000L, 24L * 60L * 60L * 1000L)
            SwitchModeStore.setTemporarilyDisabled(this, clamped)
            AppLogStore.append(this, "Profiles", "Temp disable quick pause reason=$log duration=${clamped}ms")
            sheet.dismiss()
            updateSwitchState()
        }

        val options = mutableListOf<PauseUntilOption>()
        val nextBoundary = runCatching { SchedulePlanner.getNextBoundaryMillis(this) }.getOrDefault(0L)
        if (nextBoundary > now + 60_000L && (nextBoundary - now) <= maxAllowedMs) {
            options += PauseUntilOption(getString(R.string.dashboard_pause_until_next_schedule)) {
                pauseFor(nextBoundary - now, "next_schedule")
            }
        }

        val activeRangeId = runCatching { ScheduleRuntimeStore.getActiveRangeScheduleId(this) }.getOrDefault(-1)
        val activeSchedule = runCatching { ScheduleStore.getAll(this).firstOrNull { it.id == activeRangeId } }.getOrNull()
        if (activeSchedule?.isLocationSchedule() == true && (24L * 60L * 60L * 1000L) <= maxAllowedMs) {
            options += PauseUntilOption(getString(R.string.dashboard_pause_until_leave_location)) {
                PauseUntilStore.markUntilLocationExit(this, activeSchedule.id)
                ScheduleRuntimeStore.setManualSchedulePauseActive(this, true, activeSchedule.id)
                pauseFor(24L * 60L * 60L * 1000L, "location_exit scheduleId=${activeSchedule.id}")
            }
        }

        return options
    }

    private fun showPauseUntilDialog(sheet: BottomSheetDialog) {
        val options = buildPauseUntilOptions(sheet)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dashboard_pause_until_title)
            .setItems(options.map { it.label }.toTypedArray()) { dialog, which ->
                options.getOrNull(which)?.runAction?.invoke()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showAccented()
    }

    private fun showClockDialDurationPicker(mode: TempSheetMode, onPicked: (Int) -> Unit) {
        val accent = AccentColor.getAccentColorInt(this)
        val surfaceVariant = ContextCompat.getColor(this, R.color.foqos_surface_variant)
        val onSurface = ContextCompat.getColor(this, R.color.foqos_on_surface)
        val tint = ColorStateList.valueOf(accent)

        val dialSheet = BottomSheetDialog(this)
        val parent = findViewById<ViewGroup>(android.R.id.content)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_temp_clock_dial, parent, false)
        dialSheet.setContentView(view)
        dialSheet.prepareExpandedSheet()

        val clockDialView = view.findViewById<ClockDurationDialView>(R.id.clockDialView)
        val tvDialDuration = view.findViewById<TextView>(R.id.tvDialDuration)
        val tvDialUnit = view.findViewById<TextView>(R.id.tvDialUnit)
        val tvDialEndTime = view.findViewById<TextView>(R.id.tvDialEndTime)
        val btnApplyDuration = view.findViewById<MaterialButton>(R.id.btnApplyDuration)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        view.findViewById<View>(R.id.roundelBg)?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AccentColor.getAccentContainerColorInt(this@MainActivity))
        }
        view.findViewById<ImageView>(R.id.ivIcon)?.imageTintList = tint

        val maxAllowed = if (mode == TempSheetMode.DISABLE) {
            val profile = ProfileStore.getCurrent(this@MainActivity).orEmpty().trim().ifEmpty { "Default" }
            TempPauseStore.maxAllowedDurationMinutes(this@MainActivity, profile)
        } else {
            180
        }
        clockDialView.accentColor = accent
        clockDialView.minMinutes = 0
        clockDialView.maxMinutes = minOf(180, maxAllowed).coerceAtLeast(0)
        val initialMinutes = if (clockDialView.maxMinutes == 0) 0 else minOf(25, clockDialView.maxMinutes).coerceAtLeast(1)
        clockDialView.setDurationMinutes(initialMinutes, animate = false)

        val timeFormat = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        fun formatDurationString(minutes: Int): String {
            return if (minutes < 60) {
                "$minutes min"
            } else {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0) "${h}h" else "${h}h ${m}m"
            }
        }

        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.5) Color.BLACK else Color.WHITE
        btnApplyDuration.backgroundTintList = ColorStateList.valueOf(accent)
        btnApplyDuration.setTextColor(onAccent)

        fun updateDialDisplay(minutes: Int) {
            if (minutes <= 0) {
                tvDialDuration.text = "0"
                tvDialUnit.text = getString(R.string.minutes).uppercase()
                tvDialEndTime.text = getString(R.string.tile_temp_subtitle_choose)
                btnApplyDuration.isEnabled = false
                btnApplyDuration.alpha = 0.45f
                btnApplyDuration.text = getString(R.string.tile_temp_subtitle_choose)
                return
            }

            btnApplyDuration.isEnabled = true
            btnApplyDuration.alpha = 1.0f

            if (minutes < 60) {
                tvDialDuration.text = "$minutes"
                tvDialUnit.text = getString(R.string.minutes).uppercase()
            } else {
                val h = minutes / 60
                val m = minutes % 60
                tvDialDuration.text = if (m == 0) "${h}h" else "${h}h ${m}m"
                tvDialUnit.text = getString(R.string.tile_temp_title_plain).uppercase()
            }

            val endTimeMillis = System.currentTimeMillis() + minutes * 60_000L
            val formattedEndTime = timeFormat.format(java.util.Date(endTimeMillis))
            tvDialEndTime.text = getString(R.string.dashboard_temp_ends_at, formattedEndTime)

            val durLabel = formatDurationString(minutes)
            btnApplyDuration.text = if (mode == TempSheetMode.DISABLE) {
                getString(R.string.dashboard_action_lock_in_duration, durLabel)
            } else {
                getString(R.string.dashboard_action_enable_duration, durLabel)
            }
        }

        updateDialDisplay(initialMinutes)

        clockDialView.onDurationChanged = { mins ->
            updateDialDisplay(mins)
        }

        btnApplyDuration.setOnClickListener {
            val mins = clockDialView.durationMinutes
            if (mins > maxAllowed) {
                btnApplyDuration.showWarnPill(getString(R.string.temp_pause_exceeds_limit, maxAllowed))
                return@setOnClickListener
            }
            if (mins > 0) {
                dialSheet.dismiss()
                onPicked(mins)
            }
        }

        btnClose.setOnClickListener {
            dialSheet.dismiss()
        }

        dialSheet.show()
    }

    private fun showCustomTempMinutesInput(onPicked: (Int) -> Unit) {
        val input = styledDialogEditText().apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.minutes_hint)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density + 0.5f).toInt()
            setPadding(pad, (8 * resources.displayMetrics.density + 0.5f).toInt(), pad, 0)
            addView(
                input,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.custom_minutes_title)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val m = input.text?.toString()?.trim()?.toIntOrNull()
                if (m == null || m < 1 || m > 1440) {
                    input.showWarnPill(R.string.nfc_time_custom_invalid)
                    return@setPositiveButton
                }
                onPicked(m)
            }
            .create()

        dialog.applyLoqInDialogWidth(0.90f)
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        dialog.setOnShowListener {
            dialog.styleLoqInDialogButtons()
            input.requestFocus()
        }
        dialog.show()
    }

    private fun openRulesDestination(intent: Intent) {
        if (!EditingLockGuard.isLocked(this)) {
            startActivity(intent)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.loqin_rules_locked_title)
            .setMessage(R.string.rules_restricted_open_message)
            .setPositiveButton(R.string.rules_open_restricted) { _, _ ->
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .showAccented()
    }

    private fun openAppPickerIfUnlocked() {
        if (isAppPickingLockedWhileEnabled()) {
            snackRoot().showWarnPill(R.string.toast_disable_loqin_to_edit_blocked_apps)
            return
        }
        // AppPickerActivity enforces one-way strictness while protection is active.
        startActivity(Intent(this, AppPickerActivity::class.java))
    }

    private fun openProfiles() {
        // Viewing and non-destructive profile management are safe.
        // ManageProfilesActivity keeps Set active/Delete locked while protection is active and permits create/duplicate/rename plus strictness-only app edits.
        startActivity(Intent(this, ManageProfilesActivity::class.java))
    }

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

    // =========================
    // Foqos-style profile edit sheet (SPA: all profile edits from the home pill)
    // =========================
    /** Foqos-style quick profile switcher. */
    private fun openSwitchProfileSheet() {
        val profiles = ProfileStore.getProfiles(this).toList().sorted()
        val current = ProfileStore.getCurrent(this)
        val sheet = BottomSheetDialog(this)
        val onSurface = ContextCompat.getColor(this@MainActivity, com.oliver.loqin.R.color.foqos_on_surface)
        val onSurfaceSoft = ContextCompat.getColor(this@MainActivity, com.oliver.loqin.R.color.foqos_on_surface_variant)
        val accent = AccentColor.getAccentColorInt(this)

        val surfaceVariant = ContextCompat.getColor(this@MainActivity, com.oliver.loqin.R.color.foqos_surface_variant)

        fun profileRowBg(selected: Boolean): android.graphics.drawable.GradientDrawable =
            android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = homeDp(16f).toFloat()
                setColor(
                    if (selected) ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x18), surfaceVariant)
                    else surfaceVariant
                )
                setStroke(
                    homeDp(2f),
                    if (selected) accent else Color.TRANSPARENT
                )
            }

        fun roundelBg(): android.graphics.drawable.GradientDrawable =
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(foqosSurfaceColor())
            }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = homeDp(16f)
            setPadding(pad, homeDp(8f), pad, homeDp(22f))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, com.oliver.loqin.R.color.foqos_surface))
        }

        // Grab handle
        list.addView(View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = homeDp(2f).toFloat()
                setColor(ColorUtils.setAlphaComponent(onSurfaceSoft, 0x61))
            }
            layoutParams = LinearLayout.LayoutParams(homeDp(36f), homeDp(4f)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        })

        // Title + close
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, homeDp(12f), 0, 0)
        }
        header.addView(TextView(this).apply {
            text = getString(R.string.profile_rows_switch)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(onSurface)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(FrameLayout(this).apply {
            background = roundelBg()
            isClickable = true
            isFocusable = true
            setOnClickListener { sheet.dismiss() }
            addView(TextView(this@MainActivity).apply {
                text = "\u2715"
                textSize = 14f
                setTextColor(onSurface)
                layoutParams = FrameLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = android.view.Gravity.CENTER }
            })
            layoutParams = LinearLayout.LayoutParams(homeDp(34f), homeDp(34f))
        })
        list.addView(header)

        // Create new profile (accent tile)
        val newRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val padV = homeDp(11f)
            setPadding(homeDp(10f), padV, homeDp(10f), padV)
            background = profileRowBg(false).apply { setStroke(homeDp(1f), ColorUtils.setAlphaComponent(accent, 0x66)) }
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = homeDp(14f) }
        }
        val newIcon = TextView(this).apply {
            text = "\uFF0B"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(accent)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(homeDp(38f), homeDp(38f)).apply {
                background = roundelBg()
            }
        }
        val newLabel = TextView(this).apply {
            text = getString(R.string.profile_sheet_new_profile)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(accent)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = homeDp(12f)
            }
        }
        newRow.addView(newIcon)
        newRow.addView(newLabel)
        newRow.setOnClickListener {
            // Creating a profile also activates it: blocked while switching is locked.
            // Anchor to the sheet window so the pill is visible above it.
            if (!ensureCanSwitchProfiles(showFeedback = true, anchor = newRow)) {
                return@setOnClickListener
            }
            sheet.dismiss()
            showCreateProfileDialog()
        }
        list.addView(newRow)

        val profileRows = mutableListOf<Pair<String, View>>()
        profiles.forEachIndexed { index, profile ->
            val isSelected = profile == current
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val padV = homeDp(11f)
                setPadding(homeDp(12f), padV, homeDp(12f), padV)
                background = profileRowBg(isSelected)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = homeDp(8f) }
            }
            val name = TextView(this).apply {
                text = profile
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(onSurface)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(name)
            profileRows += profile to row
            row.setOnClickListener {
                // Read the live current profile: the `current` snapshot above
                // goes stale after the first switch while the sheet is open.
                if (profile != ProfileStore.getCurrent(this@MainActivity)) {
                    // Only move the highlight when the switch actually happened:
                    // a blocked tap warns via pill and must not look selected.
                    // Anchor to the sheet window so the pill is visible above it.
                    if (!switchToProfile(profile, anchor = row)) {
                        return@setOnClickListener
                    }
                }
                profileRows.forEach { (p, r) ->
                    r.background = profileRowBg(p == profile)
                }
            }
            list.addView(row)
        }

        sheet.setContentView(list)
        sheet.show()
    }

    private fun foqosSurfaceColor(): Int =
        ContextCompat.getColor(this@MainActivity, com.oliver.loqin.R.color.foqos_surface)

    private fun openProfileEditSheet(profile: String) {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_profile_edit, null)
        sheet.setContentView(view)
        sheet.behavior.peekHeight = resources.displayMetrics.heightPixels / 2

        // Row icons + green labels follow the live accent (?attr/colorPrimary would fall
        // back to the compile-time green since Home never applies an accent theme variant).
        val sheetAccent = AccentColor.getAccentColorInt(this)
        val sheetGreen = ContextCompat.getColor(this, R.color.accent_default_blue) and 0x00FFFFFF
        val dangerRow = view.findViewById<View>(R.id.rowSheetDelete)
        fun inDangerRow(v: android.view.View): Boolean {
            var p = v.parent
            while (p is android.view.View) {
                if (p === dangerRow) return true
                p = p.parent
            }
            return false
        }
        fun tintAccented(v: android.view.View) {
            when (v) {
                is android.view.ViewGroup -> for (i in 0 until v.childCount) tintAccented(v.getChildAt(i))
                is android.widget.TextView -> if (!inDangerRow(v) &&
                    (v.currentTextColor and 0x00FFFFFF) == sheetGreen
                ) {
                    v.setTextColor(sheetAccent)
                }
                is android.widget.ImageView -> if (!inDangerRow(v)) {
                    androidx.core.widget.ImageViewCompat.setImageTintList(
                        v,
                        android.content.res.ColorStateList.valueOf(sheetAccent)
                    )
                }
            }
        }
        tintAccented(view)

        val nameView = view.findViewById<TextView>(R.id.tvSheetProfileName)
        nameView.text = profile

        var currentProfileName = profile
        view.findViewById<View>(R.id.btnSheetClose).setOnClickListener { sheet.dismiss() }
        val renameAction = {
            showRenameDialog(currentProfileName) { newName ->
                currentProfileName = newName
                nameView.text = newName
                refreshProfileRowsUi()
            }
        }
        view.findViewById<View>(R.id.rowSheetRename)?.setOnClickListener { renameAction() }
        nameView?.setOnClickListener { renameAction() }
        view.findViewById<View>(R.id.rowSheetApps).setOnClickListener {
            sheet.dismiss()
            openAppPickerIfUnlocked()
        }
        view.findViewById<View>(R.id.rowSheetWebsites).setOnClickListener {
            sheet.dismiss()
            val intent = Intent(this, ManageBlockedWebsitesActivity::class.java).apply {
                putExtra(ManageBlockedWebsitesActivity.EXTRA_PROFILE_NAME, profile)
            }
            openRulesDestination(intent)
        }
        view.findViewById<View>(R.id.rowSheetInApp).setOnClickListener {
            sheet.dismiss()
            val intent = Intent(this, InAppRulesActivity::class.java).apply {
                putExtra(InAppRulesActivity.EXTRA_PROFILE_NAME, profile)
            }
            openRulesDestination(intent)
        }
        view.findViewById<View>(R.id.rowSheetSchedules).setOnClickListener {
            sheet.dismiss()
            val intent = Intent(this, SchedulesActivity::class.java).apply {
                putExtra(SchedulesActivity.EXTRA_PROFILE_NAME, profile)
            }
            openRulesDestination(intent)
        }
        val tvTempPausesSummary = view.findViewById<TextView>(R.id.tvSheetTempPausesSummary)
        fun refreshTempPausesSummary() {
            tvTempPausesSummary?.text = TempPauseDialogs.summaryText(this, currentProfileName)
        }
        refreshTempPausesSummary()
        // Temporary pause limits cannot change while protection is active:
        // gray the row out and warn immediately instead of opening the editor.
        view.findViewById<View>(R.id.rowSheetTempPauses)?.apply {
            alpha = if (EditingLockGuard.isLocked(this@MainActivity)) 0.45f else 1f
            setOnClickListener { tapped ->
                if (EditingLockGuard.isLocked(this@MainActivity)) {
                    tapped.showWarnPill(R.string.edit_locked_manage_temp_pauses)
                    return@setOnClickListener
                }
                TempPauseDialogs.show(this@MainActivity, currentProfileName) {
                    refreshTempPausesSummary()
                }
            }
        }
        view.findViewById<View>(R.id.rowSheetDelete).setOnClickListener {
            val profiles = ProfileStore.getProfiles(this)
            if (profiles.size <= 1) {
                com.google.android.material.snackbar.Snackbar.make(
                    snackRoot(),
                    getString(R.string.profile_sheet_last_profile),
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                ).applyLoqInStyle().show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.profile_sheet_delete))
                .setMessage(getString(R.string.profile_sheet_delete_confirm, profile))
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    ProfileStore.removeProfile(this, profile)
                    sheet.dismiss()
                    refreshProfileRowsUi()
                    refreshBlockedList()
                    updateSwitchState()
                }
                .setNegativeButton(getString(android.R.string.cancel), null)
                .showDestructiveAccented()
        }

        sheet.show()
    }

    private fun showCreateProfileDialog() {
        showLoqInInputDialog(
            title = getString(R.string.profile_sheet_new_profile),
            hint = getString(R.string.profile_sheet_rename_hint),
            onConfirm = { name ->
                if (name.isNotEmpty() && ProfileStore.addProfile(this, name)) {
                    ProfileStore.setCurrent(this, name)
                    refreshProfileRowsUi()
                    refreshBlockedList()
                    updateSwitchState()
                }
            }
        )
    }

    private fun refreshProfileRowsUi() {
        val profiles = ProfileStore.getProfiles(this).toList().sorted()
        refreshProfileRows(profiles, ProfileStore.getCurrent(this))
        refreshProfileHero(ProfileStore.getCurrent(this))
    }

    private fun showRenameDialog(profile: String, onRenamed: (String) -> Unit) {
        showLoqInInputDialog(
            title = getString(R.string.profile_sheet_rename),
            initialText = profile,
            onConfirm = { newName ->
                if (newName.isNotEmpty() && newName != profile &&
                    ProfileStore.renameProfile(this, profile, newName)
                ) {
                    onRenamed(newName)
                    refreshBlockedList()
                    updateSwitchState()
                }
            }
        )
    }

    // =========================
    // Activity heatmap (Foqos BlockedSessionsHabitTracker equivalent)
    // =========================
    private fun refreshActivityHeatmap() {
        if (!::activityHeatmap.isInitialized) {
            return
        }
        thread {
            // "4 Week Activity" = time LoqIn was actually up & blocking each day.
            val days = BlockedTimeStore.getFocusDayTotalsMs(this, FoqosHeatmapView.DAYS)
            if (SwitchModeStore.isEnabled(this)) {
                val activeToday = ActiveDurationStore.todayMs(this)
                val last = days.size - 1
                if (activeToday > days[last]) {
                    days[last] = activeToday
                }
            }
            runOnUiThread {
                activityDaysMs = days
                activityHeatmap.setData(days)
                onHeatmapDaySelected(-1)
            }
        }
    }

    /** Foqos legend chips: colored dots + hour-range labels, colors from bucket ramp. */
    private fun applyHeatmapLegend() {
        val colors = FoqosHeatmapView.bucketColors(AccentColor.getAccentColorInt(this))
        val labels = FoqosHeatmapView.bucketLabels()
        val sb = android.text.SpannableStringBuilder()
        labels.forEachIndexed { i, label ->
            val start = sb.length
            sb.append("\u25CF ")
            sb.setSpan(
                android.text.style.ForegroundColorSpan(colors[i]),
                start,
                start + 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            sb.append(label)
            if (i < labels.size - 1) sb.append("    ")
        }
        tvHeatmapLegend.text = sb
    }

    /** Foqos hero card: active profile name, feature chips and stat columns. */
    private fun refreshProfileHero(current: String?) {
        val profile = current ?: return
        if (!::tvHeroProfileName.isInitialized) {
            return
        }
        tvHeroProfileName.text = profile

        val appCount = ProfileStore.getSelectedForProfileMode(this, profile).size
        val domainCount = DomainBlockStore.getDomainsForProfile(this, profile).size
        tvHeroStatApps.text = appCount.toString()
        tvHeroStatDomains.text = domainCount.toString()
        thread {
            val blocks28d = BlockCountStore.getTotalForLastNDays(this, 28)
            runOnUiThread {
                if (::tvHeroStatBlocks.isInitialized) {
                    tvHeroStatBlocks.text = blocks28d.toString()
                }
            }
        }

        val chips = buildList {
            add(
                if (ProfileRuleModeStore.getMode(this@MainActivity, profile) ==
                    ProfileRuleModeStore.MODE_ALLOW_SELECTED
                ) {
                    getString(R.string.hero_chip_mode_allow)
                } else {
                    getString(R.string.hero_chip_mode_block)
                }
            )
            if (ProfileStore.isAutoBlockNewAppsEnabled(this@MainActivity, profile)) {
                add(getString(R.string.hero_chip_autoblock))
            }
            if (EmergencyBypassStore.isFeatureEnabled(this@MainActivity)) {
                add(getString(R.string.hero_chip_emergency))
            }
        }
        tvHeroChips.text = chips.joinToString("  ·  ")
        tvHeroStrategy.text = blockingModeLabel(AutomationModeStore.getMode(this))
        findViewById<ImageView>(R.id.ivHeroStrategyIcon)?.setImageResource(
            blockingModeIcon(AutomationModeStore.getMode(this))
        )
    }

    private var lastHeroArtActive: Boolean? = null
    private var lastHeroArtAccent: Int? = null

    /** Builds the hero card background from the CURRENT accent so accent switching works.
     *  Rebuilds only when state/accent changes (updateSwitchState ticks every second). */
    private fun applyHeroBackground(active: Boolean) {
        if (!::heroProfileRoot.isInitialized) {
            return
        }
        val accent = AccentColor.getAccentColorInt(this)
        if (lastHeroArtActive == active && lastHeroArtAccent == accent && heroProfileRoot.background != null) {
            return
        }
        lastHeroArtActive = active
        lastHeroArtAccent = accent

        val radius = homeDp(28f).toFloat()
        if (!active) {
            val gd = android.graphics.drawable.GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@MainActivity, com.oliver.loqin.R.color.foqos_surface_variant))
                cornerRadius = radius
                setStroke(homeDp(1f), ContextCompat.getColor(this@MainActivity, com.oliver.loqin.R.color.foqos_outline))
            }
            heroProfileRoot.background = gd
        } else {
            heroProfileRoot.background = HeroArtDrawable(accent, radius)
        }
        applyHeroIdleContrast(!active)
    }

    /**
     * The hero's text is white (designed for the saturated blob art). While IDLE the
     * card is a light surface in light mode, so the text flips to on-surface colors
     * and back when protection runs.
     */
    private fun applyHeroIdleContrast(idle: Boolean) {
        if (!::heroProfileRoot.isInitialized) {
            return
        }
        val onSurface = ContextCompat.getColor(this, com.oliver.loqin.R.color.foqos_on_surface)
        val onSurfaceSoft = ContextCompat.getColor(this, com.oliver.loqin.R.color.foqos_on_surface_variant)
        val white = Color.WHITE
        val whiteSoft = ColorUtils.setAlphaComponent(Color.WHITE, 0xCC)
        fun tv(id: Int, activeColor: Int, idleColor: Int) {
            findViewById<TextView>(id)?.setTextColor(if (idle) idleColor else activeColor)
        }
        tv(R.id.tvHeroProfileName, white, onSurface)
        tv(R.id.tvHeroChips, whiteSoft, onSurfaceSoft)
        tv(R.id.tvHeroStrategy, white, onSurface)
        tv(R.id.tvHeroStatAppsLabel, whiteSoft, onSurfaceSoft)
        tv(R.id.tvHeroStatDomainsLabel, whiteSoft, onSurfaceSoft)
        tv(R.id.tvHeroStatBlocksLabel, whiteSoft, onSurfaceSoft)
        tv(R.id.tvHeroStatApps, white, onSurface)
        tv(R.id.tvHeroStatDomains, white, onSurface)
        tv(R.id.tvHeroStatBlocks, white, onSurface)
        listOf(R.id.ibtnHeroEdit, R.id.ivHeroStrategyIcon).forEach { id ->
            findViewById<ImageView>(id)?.setColorFilter(if (idle) onSurface else white)
        }
    }

    private var lastHeroToggleActive: Boolean? = null

    /**
     * The launcher pill inside the hero card (Foqos "Hold to Start"): frosted
     * translucent white while blocking is active (reads on the saturated blob art),
     * plain accent pill while idle (reads on the neutral card).
     */
    private fun styleHeroToggle(active: Boolean) {
        if (!::btnToggle.isInitialized) return
        if (active) {
            btnToggle.backgroundTintList =
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(Color.WHITE, 0x2B))
            btnToggle.setTextColor(Color.WHITE)
            btnToggle.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(Color.WHITE, 0x40))
        } else {
            val accent = AccentColor.getAccentColorInt(this)
            val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.52) Color.BLACK else Color.WHITE
            btnToggle.backgroundTintList = ColorStateList.valueOf(accent)
            btnToggle.setTextColor(onAccent)
            btnToggle.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onAccent, 0x33))
        }
    }

    /** Hero strategy icon mirrors the active control channel (shield = mixed/manual). */
    private fun blockingModeIcon(mode: AutomationModeStore.Mode): Int = when (mode) {
        AutomationModeStore.Mode.MIXED -> R.drawable.security_24
        AutomationModeStore.Mode.NFC -> R.drawable.nfc_24
        AutomationModeStore.Mode.QR -> R.drawable.qr_code_24
        AutomationModeStore.Mode.BARCODE -> R.drawable.barcode_24
        AutomationModeStore.Mode.SCHEDULE -> R.drawable.schedule_24
    }

    private fun blockingModeLabel(mode: AutomationModeStore.Mode): String = getString(        when (mode) {
            AutomationModeStore.Mode.MIXED -> R.string.blocking_mode_mixed
            AutomationModeStore.Mode.NFC -> R.string.blocking_mode_nfc
            AutomationModeStore.Mode.QR -> R.string.blocking_mode_qr
            AutomationModeStore.Mode.BARCODE -> R.string.blocking_mode_barcode
            AutomationModeStore.Mode.SCHEDULE -> R.string.blocking_mode_schedule
        }
    )

    /** Foqos strategy-picker equivalent: choose how LoqIn may be changed. */
    private fun openBlockingModeSheet() {
        val sheet = BottomSheetDialog(this)
        val ctx = this
        val onSurface = ContextCompat.getColor(ctx, com.oliver.loqin.R.color.foqos_on_surface)
        val onSurfaceSoft = ContextCompat.getColor(ctx, com.oliver.loqin.R.color.foqos_on_surface_variant)
        val accent = AccentColor.getAccentColorInt(this)

        val surfaceVariant = ContextCompat.getColor(ctx, com.oliver.loqin.R.color.foqos_surface_variant)

        fun rowBg(selected: Boolean): android.graphics.drawable.GradientDrawable =
            android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = homeDp(16f).toFloat()
                setColor(
                    if (selected) ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x18), surfaceVariant)
                    else surfaceVariant
                )
                setStroke(
                    homeDp(2f),
                    if (selected) accent else Color.TRANSPARENT
                )
            }

        fun roundelBg(): android.graphics.drawable.GradientDrawable =
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(ContextCompat.getColor(ctx, com.oliver.loqin.R.color.foqos_surface))
            }

        fun createEditButton(onClick: () -> Unit, sizeDp: Float = 34f, iconSizeDp: Float = 17f): View {
            return FrameLayout(ctx).apply {
                val base = roundelBg()
                val mask = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                val ripple = android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x33)),
                    base,
                    mask,
                )
                background = ripple
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.edit)
                layoutParams = LinearLayout.LayoutParams(homeDp(sizeDp), homeDp(sizeDp)).apply {
                    marginStart = homeDp(6f)
                }
                addView(ImageView(ctx).apply {
                    setImageResource(R.drawable.edit_24)
                    setColorFilter(accent)
                    layoutParams = FrameLayout.LayoutParams(homeDp(iconSizeDp), homeDp(iconSizeDp)).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                })
                setOnClickListener { onClick() }
            }
        }

        fun editNfc() {
            if (isNfcTagWritingLocked()) {
                EditingLockGuard.showLockedDialog(this, R.string.edit_locked_write_nfc_tags)
            } else {
                startActivity(Intent(this, NfcWriterActivity::class.java))
            }
        }

        fun editBarcode() {
            if (EditingLockGuard.isLocked(this)) {
                EditingLockGuard.showLockedDialog(this, R.string.edit_locked_manage_barcodes)
            } else {
                startActivity(
                    Intent(this, ManageBarcodesActivity::class.java)
                        .putExtra(ManageBarcodesActivity.EXTRA_FORCE_ALLOW, true)
                )
            }
        }

        fun editQr() {
            if (EditingLockGuard.isLocked(this)) {
                EditingLockGuard.showLockedDialog(this, R.string.edit_locked_manage_qr_codes)
            } else {
                startActivity(
                    Intent(this, QrGenerateActivity::class.java)
                        .putExtra(QrGenerateActivity.EXTRA_FORCE_ALLOW, true)
                )
            }
        }

        fun editSchedule() {
            openRulesDestination(Intent(this, SchedulesActivity::class.java))
        }

        val list = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = homeDp(16f)
            setPadding(pad, homeDp(8f), pad, homeDp(22f))
            setBackgroundColor(ContextCompat.getColor(ctx, com.oliver.loqin.R.color.foqos_surface))
        }

        // Grab handle
        list.addView(View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = homeDp(2f).toFloat()
                setColor(ColorUtils.setAlphaComponent(onSurfaceSoft, 0x61))
            }
            layoutParams = LinearLayout.LayoutParams(homeDp(36f), homeDp(4f)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        })

        // Title + close
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, homeDp(12f), 0, homeDp(4f))
        }
        header.addView(TextView(ctx).apply {
            text = getString(R.string.blocking_mode_title)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(onSurface)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(FrameLayout(ctx).apply {
            background = roundelBg()
            isClickable = true
            isFocusable = true
            setOnClickListener { sheet.dismiss() }
            addView(TextView(ctx).apply {
                text = "\u2715"
                textSize = 14f
                setTextColor(onSurface)
                layoutParams = FrameLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = android.view.Gravity.CENTER }
            })
            layoutParams = LinearLayout.LayoutParams(homeDp(34f), homeDp(34f))
        })
        list.addView(header)

        var current = AutomationModeStore.getMode(ctx)

        fun setHero(mode: AutomationModeStore.Mode) {
            tvHeroStrategy.text = blockingModeLabel(mode)
            findViewById<ImageView>(R.id.ivHeroStrategyIcon)?.setImageResource(blockingModeIcon(mode))
        }

        val modeRowViews = mutableListOf<Pair<AutomationModeStore.Mode, View>>()

        fun modeRow(
            mode: AutomationModeStore.Mode,
            nameRes: Int,
            descRes: Int,
            iconRes: Int,
            supported: Boolean,
            onEdit: (() -> Unit)? = null,
        ): LinearLayout {
            val isSelected = mode == current
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(homeDp(10f), homeDp(9f), homeDp(10f), homeDp(9f))
                background = rowBg(isSelected)
                isClickable = supported
                isFocusable = supported
                alpha = if (supported) 1f else 0.4f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            row.addView(FrameLayout(ctx).apply {
                background = roundelBg()
                addView(ImageView(ctx).apply {
                    setImageResource(iconRes)
                    setColorFilter(accent)
                    layoutParams = FrameLayout.LayoutParams(homeDp(19f), homeDp(19f)).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                })
                layoutParams = LinearLayout.LayoutParams(homeDp(38f), homeDp(38f))
            })
            val texts = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = homeDp(12f)
                }
            }
            texts.addView(TextView(ctx).apply {
                text = getString(nameRes)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(onSurface)
            })
            texts.addView(TextView(ctx).apply {
                text = if (supported) getString(descRes) else getString(R.string.blocking_mode_unsupported)
                textSize = 12f
                setTextColor(onSurfaceSoft)
            })
            row.addView(texts)

            if (onEdit != null && supported) {
                row.addView(createEditButton(onEdit, sizeDp = 34f, iconSizeDp = 17f))
            }

            modeRowViews += mode to row
            return row
        }

        // ---- Mixed mode: expands an inline channel sub-menu ----
        val mixedRow = modeRow(
            AutomationModeStore.Mode.MIXED,
            R.string.blocking_mode_mixed,
            R.string.blocking_mode_mixed_desc,
            R.drawable.security_24,
            supported = true,
        )
        val mixedSubmenu = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = homeDp(8f) }
        }

        // Collapsible disclosure for the mixed channel toggles
        var mixedExpanded = true
        val mixedChannelsArrow = ImageView(ctx).apply {
            setImageResource(R.drawable.keyboard_arrow_right_24)
            setColorFilter(onSurfaceSoft)
        }

        fun applyMixedChannelsVisibility() {
            val showChannels = current == AutomationModeStore.Mode.MIXED && mixedExpanded
            mixedSubmenu.isVisible = showChannels
            mixedChannelsArrow.animate()
                .rotation(if (mixedExpanded) 90f else 0f)
                .setDuration(140)
                .start()
        }

        val mixedChannelsHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(homeDp(10f), homeDp(8f), homeDp(10f), homeDp(4f))
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.attr.selectableItemBackground.resId(ctx))
            addView(TextView(ctx).apply {
                text = getString(R.string.toggle_section_mixed_channels)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(onSurfaceSoft)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(mixedChannelsArrow)
            setOnClickListener {
                mixedExpanded = !mixedExpanded
                applyMixedChannelsVisibility()
            }
        }

        fun channelSwitchRow(
            iconRes: Int,
            titleRes: Int,
            summaryRes: Int,
            supported: Boolean,
            getter: () -> Boolean,
            setter: (Boolean) -> Unit,
            onChanged: ((Boolean) -> Unit)? = null,
            indent: Boolean = false,
            into: LinearLayout = mixedSubmenu,
            onEdit: (() -> Unit)? = null,
        ) {
            val switch = com.google.android.material.materialswitch.MaterialSwitch(ctx).apply {
                isChecked = getter()
                isEnabled = supported
                alpha = if (supported) 1f else 0.4f
                thumbTintList = android.content.res.ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)),
                    intArrayOf(Color.WHITE, Color.WHITE),
                )
                trackTintList = android.content.res.ColorStateList(
                    arrayOf(
                        intArrayOf(-android.R.attr.state_checked),
                        intArrayOf(android.R.attr.state_checked),
                    ),
                    intArrayOf(
                        ColorUtils.setAlphaComponent(onSurfaceSoft, 0x55),
                        ColorUtils.setAlphaComponent(accent, 0x88),
                    ),
                )
            }
            switch.setOnCheckedChangeListener { _, checked ->
                if (checked && !supported) {
                    switch.isChecked = false
                    return@setOnCheckedChangeListener
                }
                setter(checked)
                onChanged?.invoke(checked)
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(homeDp(10f), homeDp(6f), homeDp(10f), homeDp(6f))
                alpha = if (supported) 1f else 0.4f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = if (indent) homeDp(14f) else 0
                }
                setOnClickListener { switch.toggle() }
            }
            row.addView(FrameLayout(ctx).apply {
                background = roundelBg()
                addView(ImageView(ctx).apply {
                    setImageResource(iconRes)
                    setColorFilter(if (supported) accent else onSurfaceSoft)
                    layoutParams = FrameLayout.LayoutParams(homeDp(17f), homeDp(17f)).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                })
                layoutParams = LinearLayout.LayoutParams(homeDp(34f), homeDp(34f))
            })
            val texts = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = homeDp(12f)
                }
            }
            texts.addView(TextView(ctx).apply {
                text = getString(titleRes)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(onSurface)
            })
            texts.addView(TextView(ctx).apply {
                text = getString(summaryRes)
                textSize = 11.5f
                setTextColor(onSurfaceSoft)
            })
            row.addView(texts)
            if (onEdit != null && supported) {
                row.addView(createEditButton(onEdit, sizeDp = 32f, iconSizeDp = 16f).apply {
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        marginStart = homeDp(6f)
                        marginEnd = homeDp(6f)
                    }
                })
            }
            row.addView(switch)
            into.addView(row)
        }

        // Manual controls toggle: when full control is OFF, buttons/tiles may still
        // ENABLE protection — the nested "enable-only" switch mirrors the Feature
        // access setting elsewhere in the app.
        val enableOnlyRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (AutomationModeStore.isMixedAllowButton(ctx)) View.GONE else View.VISIBLE
        }
        channelSwitchRow(
            R.drawable.security_24,
            R.string.pref_mixed_allow_button_title,
            R.string.pref_mixed_allow_button_summary,
            supported = true,
            getter = { AutomationModeStore.isMixedAllowButton(ctx) },
            setter = { AutomationModeStore.setMixedAllowButton(ctx, it) },
            onChanged = { manualAllowed -> enableOnlyRow.isVisible = !manualAllowed },
        )
        channelSwitchRow(
            R.drawable.lock_24,
            R.string.pref_allow_button_enable_title,
            R.string.pref_allow_button_enable_summary,
            supported = true,
            getter = { AutomationModeStore.isButtonEnableAllowed(ctx) },
            setter = { AutomationModeStore.setButtonEnableAllowed(ctx, it) },
            indent = true,
            into = enableOnlyRow,
        )
        mixedSubmenu.addView(enableOnlyRow)
        channelSwitchRow(
            R.drawable.schedule_24,
            R.string.pref_mixed_allow_schedule_title,
            R.string.pref_mixed_allow_schedule_summary,
            supported = true,
            getter = { AutomationModeStore.isMixedAllowSchedule(ctx) },
            setter = { AutomationModeStore.setMixedAllowSchedule(ctx, it) },
            onEdit = ::editSchedule,
        )
        channelSwitchRow(
            R.drawable.nfc_24,
            R.string.pref_mixed_allow_nfc_title,
            R.string.pref_mixed_allow_nfc_summary,
            supported = AutomationModeStore.isNfcSupported(ctx),
            getter = { AutomationModeStore.isMixedAllowNfc(ctx) },
            setter = { AutomationModeStore.setMixedAllowNfc(ctx, it) },
            onEdit = ::editNfc,
        )
        channelSwitchRow(
            R.drawable.qr_code_24,
            R.string.pref_mixed_allow_qr_title,
            R.string.pref_mixed_allow_qr_summary,
            supported = AutomationModeStore.isCameraSupported(ctx),
            getter = { AutomationModeStore.isMixedAllowQr(ctx) },
            setter = { AutomationModeStore.setMixedAllowQr(ctx, it) },
            onEdit = ::editQr,
        )
        channelSwitchRow(
            R.drawable.barcode_24,
            R.string.pref_mixed_allow_barcode_title,
            R.string.pref_mixed_allow_barcode_summary,
            supported = AutomationModeStore.isCameraSupported(ctx),
            getter = { AutomationModeStore.isMixedAllowBarcode(ctx) },
            setter = { AutomationModeStore.setMixedAllowBarcode(ctx, it) },
            onEdit = ::editBarcode,
        )

        fun selectMode(mode: AutomationModeStore.Mode) {
            current = mode
            AutomationModeStore.setMode(ctx, mode)
            setHero(mode)
            modeRowViews.forEach { (m, rowView) ->
                rowView.background = rowBg(m == mode)
            }
            applyMixedChannelsVisibility()
        }

        mixedRow.setOnClickListener { selectMode(AutomationModeStore.Mode.MIXED) }
        list.addView(mixedRow)
        list.addView(mixedChannelsHeader)
        list.addView(mixedSubmenu)

        // ---- Single-channel modes: select and close ----
        data class SingleModeSpec(
            val mode: AutomationModeStore.Mode,
            val iconRes: Int,
            val nameRes: Int,
            val descRes: Int,
            val onEdit: (() -> Unit)?,
        )

        val singleModes = listOf(
            SingleModeSpec(
                AutomationModeStore.Mode.NFC,
                R.drawable.nfc_24,
                R.string.blocking_mode_nfc,
                R.string.blocking_mode_nfc_desc,
                ::editNfc,
            ),
            SingleModeSpec(
                AutomationModeStore.Mode.QR,
                R.drawable.qr_code_24,
                R.string.blocking_mode_qr,
                R.string.blocking_mode_qr_desc,
                ::editQr,
            ),
            SingleModeSpec(
                AutomationModeStore.Mode.BARCODE,
                R.drawable.barcode_24,
                R.string.blocking_mode_barcode,
                R.string.blocking_mode_barcode_desc,
                ::editBarcode,
            ),
            SingleModeSpec(
                AutomationModeStore.Mode.SCHEDULE,
                R.drawable.schedule_24,
                R.string.blocking_mode_schedule,
                R.string.blocking_mode_schedule_desc,
                ::editSchedule,
            ),
        )
        singleModes.forEach { spec ->
            val row = modeRow(
                spec.mode,
                spec.nameRes,
                spec.descRes,
                spec.iconRes,
                supported = AutomationModeStore.isModeSupported(ctx, spec.mode),
                onEdit = spec.onEdit,
            )
            row.layoutParams = (row.layoutParams as LinearLayout.LayoutParams).apply {
                topMargin = homeDp(8f)
            }
            row.setOnClickListener {
                if (!AutomationModeStore.isModeSupported(ctx, spec.mode)) {
                    return@setOnClickListener
                }
                selectMode(spec.mode)
            }
            list.addView(row)
        }

        applyMixedChannelsVisibility()

        sheet.setContentView(list)
        sheet.show()
    }

    private fun Int.resId(ctx: Context): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(this, tv, true)
        return tv.resourceId
    }

    private fun onHeatmapDaySelected(index: Int) {
        if (!::tvActivityDetail.isInitialized) {
            return
        }
        if (index < 0 || index >= activityDaysMs.size) {
            tvActivityDetail.text = ""
            tvActivityDetail.isVisible = false
            return
        }
        val daysAgo = (activityDaysMs.size - 1) - index
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }
        val label = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
            .format(cal.time)
        val ms = activityDaysMs[index]
        tvActivityDetail.text = if (ms > 0L) {
            getString(R.string.activity_day_detail_fmt, label, formatDurationShort(ms))
        } else {
            getString(R.string.activity_day_none_fmt, label)
        }
        tvActivityDetail.isVisible = true
    }

    private fun formatDurationShort(ms: Long): String {
        val totalMin = ms / 60_000L
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> String.format(Locale.getDefault(), "%dh %02dm", h, m)
            m > 0 -> String.format(Locale.getDefault(), "%dm", m)
            else -> String.format(Locale.getDefault(), "<1m")
        }
    }

    /**
     * Active-session timer pill next to the launcher pill inside the hero card.
     * Only shown while blocking is active (saturated hero art), so it is styled
     * as a frosted translucent white pill — color-independent.
     */
    private fun styleActiveDurationPill() {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = homeDp(999f).toFloat()
            setColor(ColorUtils.setAlphaComponent(Color.WHITE, 0x2B))
            setStroke(homeDp(1f), ColorUtils.setAlphaComponent(Color.WHITE, 0x55))
        }
        tvActiveDuration.background = bg
        tvActiveDuration.setTextColor(Color.WHITE)
        tvActiveDuration.setPadding(homeDp(14f), homeDp(4f), homeDp(14f), homeDp(4f))
    }

    private fun homeDp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    // Formats remaining milliseconds as a compact time (m:ss or h:mm:ss).
    private fun formatRemainingShort(ms: Long): String {
        val totalSec = (ms/1000L).coerceAtLeast(0L)
        val h = totalSec/3600L
        val m = (totalSec % 3600L)/60L
        val s = totalSec % 60L
        return if (h > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", m, s)
        }
    }

    private fun formatActiveDuration(ms: Long): String {
        val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
        if (totalMinutes <= 0L) {
            return getString(R.string.dashboard_active_duration_less_than_minute)
        }

        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val minutes = totalMinutes % 60L

        return when {
            days > 0L && hours > 0L -> String.format(Locale.getDefault(), "%dd %dh", days, hours)
            days > 0L -> String.format(Locale.getDefault(), "%dd", days)
            hours > 0L && minutes > 0L -> String.format(Locale.getDefault(), "%dh %dm", hours, minutes)
            hours > 0L -> String.format(Locale.getDefault(), "%dh", hours)
            else -> String.format(Locale.getDefault(), "%dm", minutes)
        }
    }

    private fun updateSetupCard() {
        val missing = mutableListOf<String>()

        // required for blocking
        val accessibilityOk = BlockingRuntime.isAccessibilityActive(this)
        if (!accessibilityOk) {
            val accessibilityEnabledInSettings = BlockingRuntime.isAccessibilityEnabledInSettings(this)
            missing.add(
                getString(
                    if (accessibilityEnabledInSettings) R.string.dashboard_accessibility_not_connected
                    else R.string.permissions_accessibility_title
                )
            )
        }

        // allow notifications (optional, but recommended for tips + status)
        val notifEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val postNotifOk = hasPostNotificationsPermission()
        if (!notifEnabled || !postNotifOk) {
            missing.add(getString(R.string.permissions_notifications_title))
        }

        // battery optimization (highly recommended – otherwise OEMs may kill the app/service)
        val batteryOk = BatteryOptimizationCompat.isEffectivelyOk(this)
        if (!batteryOk) {
            missing.add(getString(R.string.permissions_battery_title))
        }

        val show = missing.isNotEmpty()

        // Smooth reveal/hide (feels less "jumpy")
        if (show && !cardSetup.isVisible) {
            cardSetup.alpha = 0f
            cardSetup.isVisible = true
            cardSetup.animate().alpha(1f).setDuration(180).start()
        } else if (!show && cardSetup.isVisible) {
            cardSetup.animate().alpha(0f).setDuration(150).withEndAction {
                cardSetup.isVisible = false
                cardSetup.alpha = 1f
            }.start()
        }

        if (show) {
            val missingText = resources.getQuantityString(
                R.plurals.dashboard_badge_missing,
                missing.size,
                missing.size
            )
            tvSetupTitle.text = missingText
            tvSetupTitle.setTextColor(AccentColor.getAccentColorInt(this))
            tvSetupSubtitle.text = resources.getQuantityString(
                R.plurals.dashboard_setup_subtitle_missing,
                missing.size
            )
            val bulletList = missing.joinToString(separator = "\n") { "• $it" }
            tvSetupDesc.text = bulletList
        }

    }

    private fun homeLayoutMode(): String = HomeModeDialogHelper.currentHomeLayoutMode(this)

    private fun customHomeEnabled(key: String, defaultValue: Boolean): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(key, defaultValue)
    }

    private fun shouldShowHomeProtectionControl(): Boolean = when (homeLayoutMode()) {
        ToggleOptionsActivity.HOME_MODE_CUSTOM -> customHomeEnabled(ToggleOptionsActivity.KEY_HOME_CUSTOM_PROTECTION_CONTROL, true)
        else -> true
    }

    private fun shouldShowHomeActiveTimer(): Boolean = when (homeLayoutMode()) {
        ToggleOptionsActivity.HOME_MODE_CUSTOM -> shouldShowHomeProtectionControl() &&
            customHomeEnabled(ToggleOptionsActivity.KEY_HOME_CUSTOM_ACTIVE_TIMER, true)
        else -> true
    }

    private fun shouldShowHomeMainButton(): Boolean = when (homeLayoutMode()) {
        ToggleOptionsActivity.HOME_MODE_CUSTOM -> shouldShowHomeProtectionControl() &&
            customHomeEnabled(ToggleOptionsActivity.KEY_HOME_CUSTOM_MAIN_BUTTON, true)
        else -> true
    }

    private fun shouldShowHomeActiveProfile(): Boolean = false // Foqos parity: hero card replaced this row

    private fun shouldShowHomeControlMode(): Boolean = when (homeLayoutMode()) {
        ToggleOptionsActivity.HOME_MODE_DEFAULT -> false
        ToggleOptionsActivity.HOME_MODE_CUSTOM -> shouldShowHomeProtectionControl() &&
            customHomeEnabled(ToggleOptionsActivity.KEY_HOME_CUSTOM_CONTROL_MODE, false)
        else -> true
    }

    private fun shouldShowHomePickApps(): Boolean = when (homeLayoutMode()) {
        ToggleOptionsActivity.HOME_MODE_SIMPLE -> true
        else -> false
    }

    private fun shouldShowHomeProfileDropdown(): Boolean = when (homeLayoutMode()) {
        ToggleOptionsActivity.HOME_MODE_ADVANCED -> true
        ToggleOptionsActivity.HOME_MODE_CUSTOM -> shouldShowHomeProtectionControl() &&
            customHomeEnabled(ToggleOptionsActivity.KEY_HOME_CUSTOM_PROFILE_DROPDOWN, false)
        else -> false
    }

    private fun shouldShowHomeQuickActions(): Boolean = when (homeLayoutMode()) {
        ToggleOptionsActivity.HOME_MODE_ADVANCED -> true
        ToggleOptionsActivity.HOME_MODE_CUSTOM -> customHomeEnabled(ToggleOptionsActivity.KEY_HOME_CUSTOM_QUICK_ACTIONS, false)
        else -> false
    }

    private fun shouldShowHomeNextSchedule(): Boolean = when (homeLayoutMode()) {
        ToggleOptionsActivity.HOME_MODE_ADVANCED -> true
        ToggleOptionsActivity.HOME_MODE_CUSTOM -> customHomeEnabled(ToggleOptionsActivity.KEY_HOME_CUSTOM_NEXT_SCHEDULE, false)
        else -> false
    }

    private fun shouldShowHomeBlockedApps(): Boolean = false // SPA/Foqos parity: app editing lives in the profile sheet

    private fun shouldShowHomeTemporaryShortcut(): Boolean = when (homeLayoutMode()) {
        ToggleOptionsActivity.HOME_MODE_DEFAULT, ToggleOptionsActivity.HOME_MODE_ADVANCED -> true
        ToggleOptionsActivity.HOME_MODE_CUSTOM -> shouldShowHomeProtectionControl() &&
            customHomeEnabled(ToggleOptionsActivity.KEY_HOME_CUSTOM_TEMPORARY, true)
        else -> false
    }

    private fun shouldShowHomeEmergencyShortcut(): Boolean = when (homeLayoutMode()) {
        ToggleOptionsActivity.HOME_MODE_ADVANCED -> true
        ToggleOptionsActivity.HOME_MODE_CUSTOM -> shouldShowHomeProtectionControl() &&
            customHomeEnabled(ToggleOptionsActivity.KEY_HOME_CUSTOM_EMERGENCY, true)
        else -> true
    }

    private fun refreshHomeLayout() {
        val mode = homeLayoutMode()
        val showProtectionControl = shouldShowHomeProtectionControl()
        if (showProtectionControl) {
            spaceBeforeStatusCard.showFade()
            cardStatus.showFade()
        } else {
            spaceBeforeStatusCard.hideHomeLayoutView(mode)
            cardStatus.hideHomeLayoutView(mode)
        }

        updateTempHintVisibility()
        updateEmergencyHintVisibility()

        btnToggle.isVisible = shouldShowHomeMainButton()
        rowActiveProfile.isVisible = shouldShowHomeActiveProfile()
        tvControlModeHint.isVisible = shouldShowHomeControlMode()
        btnSimplePickApps.isVisible = shouldShowHomePickApps()

        if (shouldShowHomeProfileDropdown()) {
            if (::dividerBeforeProfileDropdown.isInitialized) dividerBeforeProfileDropdown.showFade()
            if (::layoutProfileDropdown.isInitialized) layoutProfileDropdown.showFade()
        } else {
            if (::dividerBeforeProfileDropdown.isInitialized) dividerBeforeProfileDropdown.hideFade()
            if (::layoutProfileDropdown.isInitialized) layoutProfileDropdown.hideFade()
        }

        if (shouldShowHomeQuickActions()) {
            updateQuickActionsVisibility()
        } else {
            cardQuickActions.hideHomeLayoutView(mode)
        }

        if (shouldShowHomeNextSchedule()) updateNextScheduleCard() else cardNextSchedule.hideHomeLayoutView(mode)
        if (!shouldShowHomeBlockedApps()) {
            cardBlockedApps.hideHomeLayoutView(mode)
            layoutBlockedAppsEmpty.hideHomeLayoutView(mode)
            rvBlocked.hideHomeLayoutView(mode)
        } else {
            refreshBlockedList()
        }

        if (mode == ToggleOptionsActivity.HOME_MODE_CUSTOM) {
            applyProtectionChildOrder(HomeModeDialogHelper.customProtectionChildOrderKeys(this))
            applyCustomHomeOrderIfNeeded()
        } else {
            applyProtectionChildOrder(HomeModeDialogHelper.defaultProtectionChildOrderKeys())
            applyDefaultAndAdvancedHomeOrderIfNeeded()
        }
    }

    private fun applyProtectionChildOrder(orderedKeys: List<String>) {
        if (!::layoutStatusContent.isInitialized || !::layoutProtectionStatus.isInitialized) return

        val groups = linkedMapOf(
            ToggleOptionsActivity.KEY_HOME_CUSTOM_ACTIVE_TIMER to listOf(layoutProtectionStatus),
            ToggleOptionsActivity.KEY_HOME_CUSTOM_MAIN_BUTTON to listOf(btnToggle, tvNfcLockedHint),
            ToggleOptionsActivity.KEY_HOME_CUSTOM_ACTIVE_PROFILE to listOf(rowActiveProfile),
            ToggleOptionsActivity.KEY_HOME_CUSTOM_CONTROL_MODE to listOf(tvControlModeHint),
            ToggleOptionsActivity.KEY_HOME_CUSTOM_TEMPORARY to listOf(tvTempHint),
            ToggleOptionsActivity.KEY_HOME_CUSTOM_EMERGENCY to listOf(tvEmergencyHint),
            ToggleOptionsActivity.KEY_HOME_CUSTOM_PROFILE_DROPDOWN to listOf(
                dividerBeforeProfileDropdown,
                layoutProfileDropdown,
            ),
        )
        val orderedViews = buildList {
            orderedKeys.forEach { key ->
                addAll(groups[key].orEmpty())
                if (key == ToggleOptionsActivity.KEY_HOME_CUSTOM_CONTROL_MODE) {
                    add(btnSimplePickApps)
                }
            }
        }
        if (orderedViews.isEmpty()) return

        val currentOrder = (0 until layoutStatusContent.childCount)
            .map(layoutStatusContent::getChildAt)
            .filter { it in orderedViews }
        if (currentOrder == orderedViews) return

        val attachedViews = orderedViews.filter { it.parent === layoutStatusContent }
        val insertionIndex = attachedViews
            .map { layoutStatusContent.indexOfChild(it) }
            .filter { it >= 0 }
            .minOrNull()
            ?: return
        val paramsByView = attachedViews.associateWith { it.layoutParams }
        attachedViews.forEach(layoutStatusContent::removeView)

        var targetIndex = insertionIndex.coerceAtMost(layoutStatusContent.childCount)
        orderedViews.forEach { view ->
            val params = paramsByView[view] ?: return@forEach
            layoutStatusContent.addView(
                view,
                targetIndex.coerceAtMost(layoutStatusContent.childCount),
                params,
            )
            targetIndex++
        }
    }

    private fun applyDefaultAndAdvancedHomeOrderIfNeeded() {
        val mode = homeLayoutMode()
        if (mode != ToggleOptionsActivity.HOME_MODE_DEFAULT && mode != ToggleOptionsActivity.HOME_MODE_ADVANCED) {
            return
        }
        if (!::layoutHomeRoot.isInitialized || !::cardStatus.isInitialized || !::cardBlockedApps.isInitialized) {
            return
        }
        if (cardStatus.parent !== layoutHomeRoot || cardBlockedApps.parent !== layoutHomeRoot) {
            return
        }

        val statusIndex = layoutHomeRoot.indexOfChild(cardStatus)
        if (statusIndex < 0) {
            return
        }
        val targetIndex = (statusIndex + 1).coerceAtMost(layoutHomeRoot.childCount - 1)
        val currentIndex = layoutHomeRoot.indexOfChild(cardBlockedApps)
        if (currentIndex == targetIndex) {
            return
        }

        val params = cardBlockedApps.layoutParams
        layoutHomeRoot.removeView(cardBlockedApps)
        val insertIndex = (layoutHomeRoot.indexOfChild(cardStatus) + 1).coerceAtMost(layoutHomeRoot.childCount)
        layoutHomeRoot.addView(cardBlockedApps, insertIndex, params)
    }

    private fun applyCustomHomeOrderIfNeeded() {
        if (homeLayoutMode() != ToggleOptionsActivity.HOME_MODE_CUSTOM) {
            return
        }
        if (!::layoutHomeRoot.isInitialized || !::cardStatus.isInitialized || !::spaceBeforeStatusCard.isInitialized) {
            return
        }

        reorderCustomHomeGroups(
            parent = layoutHomeRoot,
            order = HomeModeDialogHelper.customHomeOrderKeys(this),
            viewsByKey = mapOf(
                ToggleOptionsActivity.KEY_HOME_CUSTOM_PROTECTION_CONTROL to listOf(spaceBeforeStatusCard, cardStatus),
                ToggleOptionsActivity.KEY_HOME_CUSTOM_QUICK_ACTIONS to listOf(cardQuickActions),
                ToggleOptionsActivity.KEY_HOME_CUSTOM_NEXT_SCHEDULE to listOf(cardNextSchedule),
                ToggleOptionsActivity.KEY_HOME_CUSTOM_BLOCKED_APPS to listOf(cardBlockedApps)
            )
        )
    }

    private fun reorderCustomHomeGroups(
        parent: ViewGroup,
        order: List<String>,
        viewsByKey: Map<String, List<View>>
    ) {
        val orderedViews = order
            .flatMap { key -> viewsByKey[key].orEmpty() }
            .filter { view -> view.parent === parent }
            .distinct()
        reorderDirectChildren(parent, orderedViews)
    }

    private fun reorderDirectChildren(parent: ViewGroup, orderedViews: List<View>) {
        if (orderedViews.size <= 1) {
            return
        }

        val firstIndex = orderedViews
            .mapNotNull { view -> parent.indexOfChild(view).takeIf { it >= 0 } }
            .minOrNull()
            ?: return
        val layoutParams = orderedViews.associateWith { view -> view.layoutParams }

        orderedViews.forEach { view -> parent.removeView(view) }
        var insertAt = firstIndex.coerceAtMost(parent.childCount)
        orderedViews.forEach { view ->
            val params = layoutParams[view]
            if (params != null) {
                parent.addView(view, insertAt, params)
            } else {
                parent.addView(view, insertAt)
            }
            insertAt += 1
        }
    }

    private fun updateNextScheduleCard() {
        if (!shouldShowHomeNextSchedule()) {
            cardNextSchedule.hideFade()
            return
        }

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val show = homeLayoutMode() == ToggleOptionsActivity.HOME_MODE_CUSTOM ||
            sp.getBoolean(ToggleOptionsActivity.KEY_SHOW_NEXT_SCHEDULE, false)
        if (show) {
            if (!cardNextSchedule.isVisible) {
                cardNextSchedule.showFade()
            }
        } else {
            if (cardNextSchedule.isVisible) {
                cardNextSchedule.hideFade()
            }
            return
        }

        if (!AutomationModeStore.isScheduleAllowed(this)) {
            tvNextScheduleValue.text = getString(R.string.schedules_next_inactive_control_mode)
            return
        }

        val nextMillis = SchedulePlanner.getNextBoundaryMillis(this)
        if (nextMillis <= 0L) {
            tvNextScheduleValue.text = getString(R.string.schedules_next_none)
        } else {
            val text = formatLocalScheduleBoundaryTime(nextMillis)
            tvNextScheduleValue.text = getString(R.string.schedules_next_at, text)
        }
    }

    private fun formatLocalScheduleBoundaryTime(timeMillis: Long): String {
        val cal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return TimeFormatPrefs.formatMinutesOfDay(this, minutesOfDay)
    }

    private fun updateQuickActionsVisibility() {
        val mode = homeLayoutMode()

        if (!shouldShowHomeQuickActions() || !areQuickActionsEnabled()) {
            cardQuickActions.hideHomeLayoutView(mode)
            return
        }

        val expanded = areQuickActionsExpanded()
        val enabledCount = getVisibleQuickActionsCount()

        tvQuickActionsTitle.text = if (expanded || enabledCount <= 0) {
            getString(R.string.dashboard_quick_actions)
        } else {
            resources.getQuantityString(R.plurals.dashboard_quick_actions_count, enabledCount, enabledCount)
        }

        cardQuickActions.showFade()
        rowQuickActionsHeader.isVisible = true
        tvQuickActionsTitle.isVisible = true
        ivQuickActionsEdit.isVisible = true
        ivQuickActionsChevron.isVisible = true
        if (expanded) {
            dividerQuickActions.showFade()
            gridQuickActions.showFade()
        } else {
            dividerQuickActions.hideFade()
            gridQuickActions.hideFade()
        }

        ivQuickActionsChevron.animate().cancel()
        ivQuickActionsChevron.rotation = 0f
        ivQuickActionsChevron.setImageResource(
            if (expanded) R.drawable.keyboard_arrow_up_24 else R.drawable.keyboard_arrow_down_24
        )
        ivQuickActionsChevron.contentDescription = getString(
            if (expanded) R.string.dashboard_quick_actions_collapse else R.string.dashboard_quick_actions_expand
        )

        syncScanQuickActions()
        invalidateOptionsMenu()
    }

    private fun areQuickActionsEnabled(): Boolean {
        if (homeLayoutMode() == ToggleOptionsActivity.HOME_MODE_CUSTOM) {
            return true
        }
        return PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(ToggleOptionsActivity.KEY_SHOW_QUICK_ACTIONS, true)
    }

    private fun isProtectionActivelyEnforced(): Boolean {
        return EditingLockGuard.isLocked(this)
    }

    private fun openBlockedNotificationsIfAllowed() {
        if (isProtectionActivelyEnforced()) {
            EditingLockGuard.showLockedDialog(this, R.string.edit_locked_manage_blocked_notifications)
            return
        }
        startActivity(Intent(this, BlockedInboxActivity::class.java))
    }

    private fun areQuickActionsExpanded(): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        return sp.getBoolean(KEY_QUICK_ACTIONS_EXPANDED, false)
    }

    private fun isQuickActionTileEnabled(key: String, defaultValue: Boolean = true): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        return sp.getBoolean(key, defaultValue)
    }

    private fun getVisibleQuickActionsCount(): Int {
        var count = 0
        if (isQuickActionTileEnabled(KEY_QA_APPS)) count++
        if (isQuickActionTileEnabled(KEY_QA_PROFILES)) count++
        if (isQuickActionTileEnabled(KEY_QA_WEBSITES)) count++
        if (isQuickActionTileEnabled(KEY_QA_INAPP)) count++
        if (isQuickActionTileEnabled(KEY_QA_NFC_WRITE, defaultValue = false)) count++
        if (isQuickActionTileEnabled(KEY_QA_BLOCKED_NOTIFICATIONS, defaultValue = false)) count++
        if (isQuickActionTileEnabled(KEY_QA_QR) && AutomationModeStore.isQrAllowed(this)) count++
        if (isQuickActionTileEnabled(KEY_QA_BARCODE) && AutomationModeStore.isBarcodeAllowed(this)) count++
        return count
    }

    private fun setQuickActionTileEnabled(key: String, enabled: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        sp.edit { putBoolean(key, enabled) }
    }

    private fun quickActionCheckBoxTint(accent: Int): ColorStateList {
        val checked = intArrayOf(android.R.attr.state_checked)
        val unchecked = intArrayOf(-android.R.attr.state_checked)
        return ColorStateList(
            arrayOf(checked, unchecked),
            intArrayOf(accent, ColorUtils.setAlphaComponent(accent, 140))
        )
    }

    private fun defaultQuickActionOrderKeys(): List<String> = listOf(
        KEY_QA_APPS,
        KEY_QA_PROFILES,
        KEY_QA_WEBSITES,
        KEY_QA_INAPP,
        KEY_QA_NFC_WRITE,
        KEY_QA_BLOCKED_NOTIFICATIONS,
        KEY_QA_QR,
        KEY_QA_BARCODE
    )

    private fun quickActionOrderKeys(availableKeys: Set<String>? = null): List<String> {
        val defaultKeys = defaultQuickActionOrderKeys()
        val allowedKeys = availableKeys?.let { available -> defaultKeys.filter { it in available } } ?: defaultKeys
        val raw = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(KEY_QA_ORDER, null)
            .orEmpty()
        val stored = raw.split("|")
            .map { it.trim() }
            .filter { it in allowedKeys }
            .distinct()
        return stored + allowedKeys.filterNot { it in stored }
    }

    private fun saveQuickActionOrder(orderedVisibleKeys: List<String>) {
        val currentFullOrder = quickActionOrderKeys()
        val remainingKeys = currentFullOrder.filterNot { it in orderedVisibleKeys }
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putString(KEY_QA_ORDER, (orderedVisibleKeys + remainingKeys).joinToString("|"))
        }
    }

    private fun showQuickActionsCustomizeDialog() {
        data class QuickActionDialogItem(
            val key: String,
            val label: String,
            val summary: String,
            val iconRes: Int,
            var checked: Boolean
        )

        val allItemsByKey = buildMap {
            put(KEY_QA_APPS, QuickActionDialogItem(KEY_QA_APPS, getString(R.string.dashboard_tile_apps), getString(R.string.dashboard_tile_apps_sub), R.drawable.apps_24, isQuickActionTileEnabled(KEY_QA_APPS)))
            put(KEY_QA_PROFILES, QuickActionDialogItem(KEY_QA_PROFILES, getString(R.string.dashboard_tile_profiles), getString(R.string.dashboard_tile_profiles_sub), R.drawable.switch_account_24, isQuickActionTileEnabled(KEY_QA_PROFILES)))
            put(KEY_QA_WEBSITES, QuickActionDialogItem(KEY_QA_WEBSITES, getString(R.string.dashboard_tile_blocked_websites), getString(R.string.dashboard_tile_blocked_websites_sub), R.drawable.language_24, isQuickActionTileEnabled(KEY_QA_WEBSITES)))
            put(KEY_QA_INAPP, QuickActionDialogItem(KEY_QA_INAPP, getString(R.string.dashboard_tile_in_app), getString(R.string.dashboard_tile_in_app_sub), R.drawable.layers_24, isQuickActionTileEnabled(KEY_QA_INAPP)))
            put(KEY_QA_NFC_WRITE, QuickActionDialogItem(KEY_QA_NFC_WRITE, getString(R.string.dashboard_tile_write_nfc), getString(R.string.dashboard_tile_write_nfc_sub), R.drawable.nfc_24, isQuickActionTileEnabled(KEY_QA_NFC_WRITE, defaultValue = false)))
            put(KEY_QA_BLOCKED_NOTIFICATIONS, QuickActionDialogItem(KEY_QA_BLOCKED_NOTIFICATIONS, getString(R.string.dashboard_tile_blocked_notifications), getString(R.string.dashboard_tile_blocked_notifications_sub), R.drawable.notifications_24, isQuickActionTileEnabled(KEY_QA_BLOCKED_NOTIFICATIONS, defaultValue = false)))
            if (AutomationModeStore.isQrAllowed(this@MainActivity)) {
                put(KEY_QA_QR, QuickActionDialogItem(KEY_QA_QR, getString(R.string.dashboard_tile_qr), getString(R.string.dashboard_tile_qr_sub), R.drawable.qr_code_24, isQuickActionTileEnabled(KEY_QA_QR)))
            }
            if (AutomationModeStore.isBarcodeAllowed(this@MainActivity)) {
                put(KEY_QA_BARCODE, QuickActionDialogItem(KEY_QA_BARCODE, getString(R.string.dashboard_tile_barcode), getString(R.string.dashboard_tile_barcode_sub), R.drawable.barcode_24, isQuickActionTileEnabled(KEY_QA_BARCODE)))
            }
        }
        val items = quickActionOrderKeys(allItemsByKey.keys).mapNotNull { allItemsByKey[it] }.toMutableList()

        val accent = AccentColor.getAccentColorInt(this)
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val surfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, surface)
        val checkBoxTint = quickActionCheckBoxTint(accent)
        fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

        lateinit var rowsContainer: LinearLayout
        lateinit var renderRows: () -> Unit
        var draggedKey: String? = null

        fun moveItem(fromIndex: Int, toIndex: Int) {
            if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) {
                return
            }
            val moved = items.removeAt(fromIndex)
            items.add(toIndex, moved)
            rowsContainer.removeAllViews()
            renderRows()
        }

        fun moveItemByKey(fromKey: String, toKey: String) {
            val fromIndex = items.indexOfFirst { it.key == fromKey }
            val toIndex = items.indexOfFirst { it.key == toKey }
            moveItem(fromIndex, toIndex)
        }

        fun attachDragReorder(card: MaterialCardView, dragHandle: View, key: String) {
            dragHandle.setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    view.performClick()
                    return@setOnTouchListener true
                }
                if (event.actionMasked != MotionEvent.ACTION_DOWN) {
                    return@setOnTouchListener false
                }
                draggedKey = key
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val data = ClipData.newPlainText("quick_action_tile", key)
                val started = view.startDragAndDrop(data, View.DragShadowBuilder(card), key, 0)
                if (!started) draggedKey = null
                started
            }
            card.setOnDragListener { view, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> (event.localState as? String) != null
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        if ((draggedKey ?: event.localState as? String) != key) view.alpha = 0.72f
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        view.alpha = 1f
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        view.alpha = 1f
                        val fromKey = draggedKey ?: event.localState as? String
                        if (!fromKey.isNullOrBlank() && fromKey != key) {
                            moveItemByKey(fromKey, key)
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        view.alpha = 1f
                        draggedKey = null
                        true
                    }
                    else -> true
                }
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(18)
            setPadding(pad, dp(8), pad, 0)
        }

        val hint = TextView(this).apply {
            text = getString(R.string.dashboard_quick_actions_reorder_hint)
            setTextColor(ColorUtils.setAlphaComponent(onSurface, 0xAA))
            textSize = 12.5f
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), 0, dp(6), dp(10))
        }
        container.addView(hint)

        rowsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(rowsContainer)

        renderRows = {
            items.forEach { item ->
                val card = MaterialCardView(this).apply {
                    radius = dp(16).toFloat()
                    cardElevation = 0f
                    useCompatPadding = false
                    setCardBackgroundColor(if (item.checked) ColorUtils.setAlphaComponent(accent, 0x12) else surfaceVariant)
                    setStrokeWidth(dp(if (item.checked) 2 else 1))
                    strokeColor = if (item.checked) accent else ColorUtils.setAlphaComponent(onSurface, 0x24)
                    isClickable = true
                    isFocusable = true
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    minimumHeight = dp(64)
                    setPadding(dp(8), dp(7), dp(8), dp(7))
                }

                val dragHandle = ImageView(this).apply {
                    setImageResource(R.drawable.drag_handle_24)
                    contentDescription = getString(R.string.pref_home_layout_custom_drag_handle)
                    setPadding(dp(8), dp(9), dp(8), dp(9))
                    setColorFilter(ColorUtils.setAlphaComponent(onSurface, 0x99))
                }
                row.addView(dragHandle, LinearLayout.LayoutParams(dp(40), dp(46)))

                val icon = ImageView(this).apply {
                    setImageResource(item.iconRes)
                    setColorFilter(accent)
                }
                row.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))

                val textColumn = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(4), 0)
                }
                val label = TextView(this).apply {
                    text = item.label
                    setTextColor(onSurface)
                    textSize = 15f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val summary = TextView(this).apply {
                    text = item.summary
                    setTextColor(ColorUtils.setAlphaComponent(onSurface, 0xA8))
                    textSize = 12.5f
                    maxLines = 2
                    setPadding(0, dp(2), 0, 0)
                }
                textColumn.addView(label)
                textColumn.addView(summary)
                row.addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                val box = MaterialCheckBox(this).apply {
                    isChecked = item.checked
                    setUseMaterialThemeColors(false)
                    buttonTintList = checkBoxTint
                    contentDescription = item.label
                }
                row.addView(box, LinearLayout.LayoutParams(dp(48), dp(48)))

                fun updateChecked(checked: Boolean) {
                    item.checked = checked
                    box.isChecked = checked
                    card.setCardBackgroundColor(if (checked) ColorUtils.setAlphaComponent(accent, 0x12) else surfaceVariant)
                    card.setStrokeWidth(dp(if (checked) 2 else 1))
                    card.strokeColor = if (checked) accent else ColorUtils.setAlphaComponent(onSurface, 0x24)
                }

                card.setOnClickListener { updateChecked(!item.checked) }
                box.setOnClickListener { updateChecked(box.isChecked) }

                attachDragReorder(card, dragHandle, item.key)
                card.addView(row)
                rowsContainer.addView(
                    card,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(8)
                    }
                )
            }
        }
        renderRows()
        val scroll = android.widget.ScrollView(this).apply { addView(container) }

        Dialogs.builder(this)
            .setTitle(R.string.dashboard_quick_actions_customize)
            .setView(scroll)
            .setPositiveButton(R.string.ok) { _, _ ->
                items.forEach { item -> setQuickActionTileEnabled(item.key, item.checked) }
                saveQuickActionOrder(items.map { it.key })
                syncScanQuickActions()
                updateQuickActionsVisibility()
            }
            .setNegativeButton(R.string.cancel, null)
            .showAccented()
    }

    private fun toggleQuickActionsExpanded() {
        if (!areQuickActionsEnabled()) {
            return
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val expanded = !areQuickActionsExpanded()
        sp.edit { putBoolean(KEY_QUICK_ACTIONS_EXPANDED, expanded) }
        ivQuickActionsChevron.animate().cancel()
        ivQuickActionsChevron.rotation = 0f
        ivQuickActionsChevron.setImageResource(
            if (expanded) R.drawable.keyboard_arrow_up_24 else R.drawable.keyboard_arrow_down_24
        )
        if (expanded) {
            dividerQuickActions.showFade()
            gridQuickActions.showFade()
        } else {
            dividerQuickActions.hideFade()
            gridQuickActions.hideFade()
        }
        tvQuickActionsTitle.text = if (expanded || getVisibleQuickActionsCount() <= 0) {
            getString(R.string.dashboard_quick_actions)
        } else {
            resources.getQuantityString(R.plurals.dashboard_quick_actions_count, getVisibleQuickActionsCount(), getVisibleQuickActionsCount())
        }
        ivQuickActionsChevron.contentDescription = getString(
            if (expanded) R.string.dashboard_quick_actions_collapse else R.string.dashboard_quick_actions_expand
        )
    }

    private fun syncScanQuickActions() {
        if (!::tileQr.isInitialized) {
            return
        }

        val appsVisible = isQuickActionTileEnabled(KEY_QA_APPS)
        val profilesVisible = isQuickActionTileEnabled(KEY_QA_PROFILES)
        val websitesVisible = isQuickActionTileEnabled(KEY_QA_WEBSITES)
        val inAppVisible = isQuickActionTileEnabled(KEY_QA_INAPP)
        val nfcWriteVisible = isQuickActionTileEnabled(KEY_QA_NFC_WRITE, defaultValue = false)
        val blockedNotificationsVisible = isQuickActionTileEnabled(KEY_QA_BLOCKED_NOTIFICATIONS, defaultValue = false)
        val qrVisible = isQuickActionTileEnabled(KEY_QA_QR) && AutomationModeStore.isQrAllowed(this)
        val barcodeVisible = isQuickActionTileEnabled(KEY_QA_BARCODE) && AutomationModeStore.isBarcodeAllowed(this)

        val tilesByKey = mapOf(
            KEY_QA_APPS to (tileManageApps to appsVisible),
            KEY_QA_PROFILES to (tileProfiles to profilesVisible),
            KEY_QA_WEBSITES to (tileWriteNfc to websitesVisible),
            KEY_QA_INAPP to (tileToggleOptions to inAppVisible),
            KEY_QA_NFC_WRITE to (tileNfcWrite to nfcWriteVisible),
            KEY_QA_BLOCKED_NOTIFICATIONS to (tileBlockedNotifications to blockedNotificationsVisible),
            KEY_QA_QR to (tileQr to qrVisible),
            KEY_QA_BARCODE to (tileBarcode to barcodeVisible)
        )
        val orderedTiles = quickActionOrderKeys().mapNotNull { key -> tilesByKey[key] }

        listOf(tileManageApps, tileProfiles, tileWriteNfc, tileToggleOptions, tileNfcWrite, tileBlockedNotifications, tileQr, tileBarcode).forEach { tile ->
            (tile.parent as? LinearLayout)?.removeView(tile)
            tile.visibility = View.GONE
        }

        val grid = gridQuickActions as? LinearLayout
        if (grid != null) {
            for (index in grid.childCount - 1 downTo 0) {
                val child = grid.getChildAt(index)
                if (child.tag == TAG_QUICK_ACTIONS_DYNAMIC_ROW) {
                    grid.removeViewAt(index)
                }
            }
        }

        val staticRows = listOf(rowManageShortcuts, rowUtilityShortcuts, rowToolsShortcuts, rowScanShortcuts)
        staticRows.forEach { row ->
            row.removeAllViews()
            row.visibility = View.GONE
        }

        val visibleTiles = orderedTiles.filter { it.second }.map { it.first }
        visibleTiles.chunked(2).forEachIndexed { index, chunk ->
            val row = staticRows.getOrNull(index) ?: createQuickActionRow().also { dynamicRow ->
                grid?.addView(dynamicRow)
            }
            row.visibility = View.VISIBLE
            val spanFullRow = chunk.size == 1
            chunk.forEach { tile ->
                tile.visibility = View.VISIBLE
                tile.minimumHeight = dpQuickAction(136)
                tile.layoutParams = quickActionTileLayoutParams(spanFullRow)
                row.addView(tile)
            }
            equalizeQuickActionRowHeights(row)
        }
    }

    private fun equalizeQuickActionRowHeights(row: LinearLayout) {
        row.post {
            val tiles = (0 until row.childCount).map { row.getChildAt(it) }
                .filter { it.isVisible }
            val maxHeight = tiles.maxOfOrNull { it.measuredHeight } ?: return@post
            tiles.forEach { tile ->
                val lp = tile.layoutParams
                if (lp.height != maxHeight) {
                    lp.height = maxHeight
                    tile.layoutParams = lp
                }
            }
        }
    }

    private fun createQuickActionRow(): LinearLayout {
        return LinearLayout(this).apply {
            tag = TAG_QUICK_ACTIONS_DYNAMIC_ROW
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun dpQuickAction(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun quickActionTileLayoutParams(spanFullRow: Boolean): LinearLayout.LayoutParams {
        val density = resources.displayMetrics.density
        val margin = (6 * density).toInt()
        return LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            if (spanFullRow) 2f else 1f
        ).apply {
            setMargins(margin, margin, margin, margin)
        }
    }

    @StringRes
    private fun controlModeLabelRes(mode: AutomationModeStore.Mode): Int {
        return when (mode) {
            AutomationModeStore.Mode.SCHEDULE -> R.string.dashboard_control_mode_schedule
            AutomationModeStore.Mode.NFC -> R.string.dashboard_control_mode_nfc
            AutomationModeStore.Mode.QR -> R.string.dashboard_control_mode_qr
            AutomationModeStore.Mode.BARCODE -> R.string.dashboard_control_mode_barcode
            AutomationModeStore.Mode.MIXED -> R.string.dashboard_control_mode_mixed
        }
    }

    private fun mixedEnabledChannelsLabel(): String {
        val methods = mutableListOf<String>()

        if (AutomationModeStore.isNfcAllowed(this)) {
            methods += getString(R.string.dashboard_control_method_nfc)
        }
        if (AutomationModeStore.isScheduleAllowed(this)) {
            methods += getString(R.string.dashboard_control_method_schedule)
        }
        if (AutomationModeStore.isQrAllowed(this)) {
            methods += getString(R.string.dashboard_control_method_qr)
        }
        if (AutomationModeStore.isBarcodeAllowed(this)) {
            methods += getString(R.string.dashboard_control_method_barcode)
        }
        if (AutomationModeStore.isButtonAllowed(this)) {
            methods += getString(R.string.dashboard_control_method_manual)
        }

        if (methods.isEmpty()) {
            return getString(R.string.dashboard_control_method_configured)
        }

        return methods.joinToString(separator = ", ")
    }

    private fun updateControlModeHint(enabled: Boolean) {
        val mode = AutomationModeStore.getMode(this)
        tvControlModeHint.text = if (mode == AutomationModeStore.Mode.MIXED) {
            getString(
                R.string.dashboard_control_mode_hint_mixed_fmt,
                getString(controlModeLabelRes(mode)),
                mixedEnabledChannelsLabel()
            )
        } else {
            getString(
                R.string.dashboard_control_mode_hint_fmt,
                getString(controlModeLabelRes(mode))
            )
        }
    }

    /**
     * Updates:
     * - LoqIn enabled/disabled header (with temp disable/enable + emergency info)
     * - icon + primary enable/disable button
     * - active profile label
     * - NFC lock UI
     */
    private fun updateSwitchState() {
        SwitchModeStore.finishTemporaryDisableIfExpired(this)
        SwitchModeStore.finishTemporaryEnableIfExpired(this)

        val enabled = SwitchModeStore.isEnabled(this)
        val baseEnabled = SwitchModeStore.isBaseEnabled(this)

        // Subtle icon animation when state changes (only on change, not every refresh)
        val prev = lastEnabledUi
        if (prev != null && prev != enabled) {
            ivStatusIcon.animate().cancel()
            ivStatusIcon.scaleX = 0.9f
            ivStatusIcon.scaleY = 0.9f
            ivStatusIcon.alpha = 0.6f
            ivStatusIcon.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(180)
                .start()

            if (prev && !enabled) {
                checkAndShowSessionMissedNotifications()
            }
        }
        lastEnabledUi = enabled

        val tempDisableRemaining = SwitchModeStore.getTemporaryRemainingMillis(this)
        val tempEnableRemaining = SwitchModeStore.getTemporaryEnableRemainingMillis(this)

        val emergencyActive = EmergencyBypassStore.isActive(this)
        val emergencyPaused = EmergencyBypassStore.isPaused(this)
        val emergencyRemMinutes = EmergencyBypassStore.minutesRemaining(this)

        // Icon + button
        ivStatusIcon.setImageResource(if (enabled) R.drawable.lock_24 else R.drawable.lock_open_24)
        btnToggle.text = if (enabled) getString(R.string.dashboard_toggle_disable) else getString(R.string.dashboard_toggle_enable)

        val locked = enabled && !emergencyActive && SwitchModeStore.isNfcRequiredForDisable(this)
        tvNfcLockedHint.visibility = if (locked) View.VISIBLE else View.GONE
        btnToggle.isEnabled = !locked

        // Apply lock to profile/app editing controls
        applyLockedUi(locked)

        // Foqos-style hero: vivid layered blob artwork (built from the live accent)
        // while blocking is active; calm neutral card while idle.
        if (::heroProfileRoot.isInitialized) {
            applyHeroBackground(enabled)
            styleHeroToggle(enabled)
        }

        // Profile label
        val current = ProfileStore.getCurrent(this)
        tvActiveProfile.text = if (current.isNullOrBlank()) {
            getString(R.string.dashboard_active_profile_unknown)
        } else {
            getString(R.string.dashboard_active_profile_fmt, current)
        }

        // Status line
        val stateWord = when {
            tempDisableRemaining > 0L -> getString(R.string.state_temporarily_paused)
            tempEnableRemaining > 0L -> getString(R.string.state_temporarily_enabled)
            enabled -> getString(R.string.state_enabled)
            else -> getString(R.string.state_disabled)
        }
        val sb = StringBuilder()
        sb.append(getString(R.string.protection_status_label, stateWord))

        when {
            emergencyActive -> {
                sb.append("  •  ")
                sb.append(getString(R.string.emergency_enabled_inline, emergencyRemMinutes))
            }
            emergencyPaused -> {
                sb.append("  •  ")
                sb.append(getString(R.string.emergency_paused_inline, emergencyRemMinutes))
            }
        }

        val full = sb.toString()
        val span = SpannableString(full)
        val start = full.indexOf(stateWord)
        if (start >= 0) {
            val color =
                if (enabled) AccentColor.getAccentColorInt(this)
                else ContextCompat.getColor(this, R.color.status_error)

            span.setSpan(
                ForegroundColorSpan(color),
                start,
                start + stateWord.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        tvSwitchMode.text = span
        updateControlModeHint(enabled)

        val showActiveTimerOnHome = enabled && shouldShowHomeActiveTimer()
        val activeDurationMs = SwitchModeStore.getActiveDurationMillis(this)
        tvActiveDuration.isVisible = showActiveTimerOnHome
        if (showActiveTimerOnHome) {
            tvActiveDuration.text = getString(
                R.string.dashboard_active_duration_fmt,
                formatActiveDuration(activeDurationMs)
            )
        }

        // The heatmap's "today" must never read BELOW the running session: tick accrual
        // pauses on reinstalls/screen-off, so while blocking is active we lift the
        // STORED total to the live session length (persisted — later sessions then
        // accumulate on top of it) and read the accumulated value back for display.
        if (::activityHeatmap.isInitialized && activityDaysMs.isNotEmpty()) {
            if (enabled) {
                val activeTodayMs = ActiveDurationStore.todayMs(this)
                BlockedTimeStore.ensureProtectionTodayAtLeast(this, activeTodayMs)
            }
            val todayMs = BlockedTimeStore.getProtectionTodayMs(this)
            activityHeatmap.updateTodayValue(todayMs)
            if (todayMs != activityDaysMs.last()) {
                activityDaysMs[activityDaysMs.size - 1] = todayMs
            }
        }

        // Keep quick-entry text in sync with current state
        updateEmergencyHintVisibility()
        updateTempHintVisibility()

        // Refresh "Blocked now" chip visibility in the blocked-apps list immediately when state changes (previously it only updated after re-opening the app).
        val chipKey = "$enabled|$emergencyActive"
        if (lastBlockedChipKey != chipKey) {
            lastBlockedChipKey = chipKey
            // Re-bind rows so chips update; keep it lightweight.
            notifyBlockedChipsChanged()
        }

        val editEnabled = ensureCanRemoveBlockedApp(showFeedback = false)
        if (lastBlockedEditEnabled != editEnabled) {
            lastBlockedEditEnabled = editEnabled
            notifyBlockedEditStateChanged()
        }

        // Cleanup stale timers
        if (tempDisableRemaining == 0L && enabled && baseEnabled) {
            SwitchModeStore.clearTemporary(this)
        }
        if (tempEnableRemaining == 0L && !baseEnabled) {
            SwitchModeStore.clearTemporaryEnable(this)
        }
    }

    private fun uiHintsPrefs() = getSharedPreferences(PREFS_UI_HINTS, MODE_PRIVATE)

    private fun hasDiscoveredTempMode(): Boolean {
        return uiHintsPrefs().getBoolean(KEY_TEMP_MODE_DISCOVERED, false)
    }

    private fun updateTempHintVisibility() {
        val lockedByNfc = isNfcLocked()
        val tempDisableRemaining = SwitchModeStore.getTemporaryRemainingMillis(this)
        val tempEnableRemaining = SwitchModeStore.getTemporaryEnableRemainingMillis(this)
        val hasActiveTemp = tempDisableRemaining > 0L || tempEnableRemaining > 0L
        val showTemporaryMode = homeLayoutMode() == ToggleOptionsActivity.HOME_MODE_CUSTOM ||
            PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(ToggleOptionsActivity.KEY_SHOW_TEMPORARY_MODE, true)

        tvTempHint.isVisible = hasActiveTemp || (shouldShowHomeTemporaryShortcut() && showTemporaryMode)

        var active = false
        when {
            tempDisableRemaining > 0L -> {
                tvTempTileTitle.text = getString(R.string.tile_temp_title_active_paused)
                tvTempTileSubtitle.text = getString(
                    R.string.tile_temp_subtitle_time,
                    formatRemainingShort(tempDisableRemaining)
                )
                active = true
            }
            tempEnableRemaining > 0L -> {
                tvTempTileTitle.text = getString(R.string.tile_temp_title_active_enabled)
                tvTempTileSubtitle.text = getString(
                    R.string.tile_temp_subtitle_time,
                    formatRemainingShort(tempEnableRemaining)
                )
                active = true
            }
            lockedByNfc -> {
                tvTempTileTitle.text = getString(R.string.tile_temp_title_plain)
                tvTempTileSubtitle.text = getString(R.string.tile_temp_subtitle_locked)
            }
            SwitchModeStore.isEnabled(this) -> {
                tvTempTileTitle.text = getString(R.string.tile_temp_title_pause)
                tvTempTileSubtitle.text = getString(R.string.tile_temp_subtitle_choose)
            }
            else -> {
                val currentProfile = ProfileStore.getCurrent(this).orEmpty().trim()
                tvTempTileTitle.text = getString(R.string.tile_temp_title_enable)
                tvTempTileSubtitle.text = if (currentProfile.isBlank()) {
                    getString(R.string.tile_temp_subtitle_choose)
                } else {
                    currentProfile
                }
            }
        }
        styleQuickTile(tvTempHint, tvTempTileTitle, active)
    }

    private fun updateEmergencyHintVisibility() {
        val featureEnabled = EmergencyBypassStore.isFeatureEnabled(this)
        val active = EmergencyBypassStore.isActive(this)
        val paused = EmergencyBypassStore.isPaused(this)
        val usedToday = EmergencyBypassStore.hasUsedToday(this)
        val rem = EmergencyBypassStore.minutesRemaining(this)
        val showEmergencyUnlock = homeLayoutMode() == ToggleOptionsActivity.HOME_MODE_CUSTOM ||
            PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(ToggleOptionsActivity.KEY_SHOW_EMERGENCY_UNLOCK, true)

        tvEmergencyHint.isVisible = shouldShowHomeEmergencyShortcut() && ((showEmergencyUnlock && featureEnabled) || active || paused)

        var running = false
        when {
            active -> {
                tvEmergencyTileTitle.text = getString(R.string.tile_emergency_title_active)
                tvEmergencyTileSubtitle.text = getString(R.string.tile_emergency_subtitle_active, rem)
                running = true
            }
            paused -> {
                tvEmergencyTileTitle.text = getString(R.string.tile_emergency_title_paused)
                tvEmergencyTileSubtitle.text = getString(R.string.tile_emergency_subtitle_paused, rem)
                running = true
            }
            !featureEnabled -> {
                tvEmergencyTileTitle.text = getString(R.string.tile_emergency_title)
                tvEmergencyTileSubtitle.text = getString(R.string.tile_emergency_subtitle_off)
            }
            usedToday -> {
                tvEmergencyTileTitle.text = getString(R.string.tile_emergency_title)
                tvEmergencyTileSubtitle.text = getString(R.string.tile_emergency_subtitle_used)
            }
            else -> {
                tvEmergencyTileTitle.text = getString(R.string.tile_emergency_title)
                tvEmergencyTileSubtitle.text = getString(R.string.tile_emergency_subtitle_ready)
            }
        }
        styleQuickTile(tvEmergencyHint, tvEmergencyTileTitle, running)
    }

    /**
     * Quick-entry action tiles (temporary timer / emergency bypass): neutral
     * sub-card at rest, accent-tinted while their feature is running — the same
     * tile design reads in both enabled and disabled mode.
     */
    private fun styleQuickTile(tile: LinearLayout, title: TextView, active: Boolean) {
        val bg = GradientDrawable().apply {
            cornerRadius = homeDp(16f).toFloat()
            if (active) {
                val accent = AccentColor.getAccentColorInt(this@MainActivity)
                setColor(ColorUtils.setAlphaComponent(accent, 0x1F))
                setStroke(homeDp(1f), ColorUtils.setAlphaComponent(accent, 0x55))
            } else {
                setColor(ContextCompat.getColor(this@MainActivity, R.color.foqos_surface_variant))
            }
        }
        tile.background = bg
        title.setTextColor(
            if (active) {
                AccentColor.getAccentColorInt(this)
            } else {
                ContextCompat.getColor(this, R.color.foqos_on_surface)
            }
        )
    }

    private fun requestEmergencyPinBeforeStart() {
        val storedPin = EmergencyPinStore.getPin(this)
        if (storedPin.isNullOrBlank()) {
            showSetEmergencyPinDialog { showEmergencyUnlockStartDialog() }
        } else {
            showEnterEmergencyPinDialog { showEmergencyUnlockStartDialog() }
        }
    }

    private fun showSetEmergencyPinDialog(onSuccess: () -> Unit) {
        EmergencyPinDialog.showSetPin(this, onSuccess)
    }

    private fun showEnterEmergencyPinDialog(onSuccess: () -> Unit) {
        EmergencyPinDialog.showEnterPin(this, onSuccess)
    }

    private fun showEmergencyUnlockStartDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.pref_emergency_title))
            .setMessage(getString(R.string.emergency_action_start_15))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, dialog ->
                val ok = EmergencyBypassStore.enableIfAllowed(this, 15)
                // Anchor to the dialog window so the pill is visible above it.
                val pillAnchor = (dialog as? AlertDialog)?.window?.decorView ?: snackRoot()
                if (ok) {
                    AppLogStore.append(this, "Emergency", "Emergency mode started from Home for 15m")
                    SwitchModeStore.setTemporarilyDisabled(this, 15 * 60_000L, isEmergency = true)
                    pillAnchor.showWarnPill(getString(R.string.emergency_enabled_toast, 15))
                    BlockingRuntime.ensureRunning(this)
                    updateSwitchState()
                } else {
                    pillAnchor.showWarnPill(getString(R.string.emergency_used_today))
                }
            }
            .showAccented()
    }

    private fun showEmergencyQuickSheet() {
        val featureEnabled = EmergencyBypassStore.isFeatureEnabled(this)
        if (!featureEnabled) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pref_emergency_title)
                .setMessage(R.string.emergency_disabled_message_controls)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.emergency_open_controls_action) { _, _ ->
                    startActivity(Intent(this, ToggleOptionsActivity::class.java))
                }
                .showAccented()
            return
        }

        val active = EmergencyBypassStore.isActive(this)
        val paused = EmergencyBypassStore.isPaused(this)
        val usedToday = EmergencyBypassStore.hasUsedToday(this)
        val remaining = EmergencyBypassStore.minutesRemaining(this)

        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        fun addAction(label: String, action: () -> Unit) {
            labels += label
            actions += action
        }

        if (active) {
            addAction(getString(R.string.emergency_action_pause)) {
                if (EmergencyBypassStore.pause(this)) {
                    AppLogStore.append(this, "Emergency", "Emergency mode paused from Home")
                    SwitchModeStore.clearTemporary(this)
                    snackRoot().showWarnPill(getString(R.string.emergency_paused_toast))
                    BlockingRuntime.ensureRunning(this)
                    updateSwitchState()
                }
            }
            addAction(getString(R.string.emergency_action_end)) {
                AppLogStore.append(this, "Emergency", "Emergency mode ended from Home")
                EmergencyBypassStore.cancel(this)
                SwitchModeStore.clearTemporary(this)
                snackRoot().showWarnPill(getString(R.string.emergency_ended_toast))
                BlockingRuntime.ensureRunning(this)
                updateSwitchState()
            }
        } else if (paused) {
            addAction(getString(R.string.emergency_action_resume)) {
                if (EmergencyBypassStore.resume(this)) {
                    val remainingMinutes = EmergencyBypassStore.minutesRemaining(this).coerceAtLeast(1)
                    AppLogStore.append(this, "Emergency", "Emergency mode resumed from Home with ${remainingMinutes}m remaining")
                    SwitchModeStore.setTemporarilyDisabled(this, remainingMinutes * 60_000L, isEmergency = true)
                    snackRoot().showWarnPill(getString(R.string.emergency_resumed_toast))
                    BlockingRuntime.ensureRunning(this)
                    updateSwitchState()
                }
            }
            addAction(getString(R.string.emergency_action_end)) {
                AppLogStore.append(this, "Emergency", "Emergency mode ended from Home")
                EmergencyBypassStore.cancel(this)
                SwitchModeStore.clearTemporary(this)
                snackRoot().showWarnPill(getString(R.string.emergency_ended_toast))
                BlockingRuntime.ensureRunning(this)
                updateSwitchState()
            }
        } else if (!usedToday) {
            requestEmergencyPinBeforeStart()
            return
        }

        val title = when {
            active -> getString(R.string.emergency_manage_title_active, remaining)
            paused -> getString(R.string.emergency_manage_title_paused, remaining)
            else -> getString(R.string.pref_emergency_title)
        }

        if (labels.isNotEmpty()) {
            showLoqInOptionDialog(
                title = title,
                subtitle = getString(R.string.emergency_manage_subtitle),
                options = labels.map { label ->
                    LoqInDialogOption(
                        title = label,
                        iconRes = when (label) {
                            getString(R.string.emergency_action_pause) -> R.drawable.schedule_24
                            getString(R.string.emergency_action_resume) -> R.drawable.play_arrow_24
                            else -> R.drawable.security_24
                        },
                        destructive = label == getString(R.string.emergency_action_end)
                    )
                }
            ) { which ->
                runCatching { actions[which].invoke() }
                updateEmergencyHintVisibility()
            }
        } else {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(R.string.emergency_used_today)
                .setNegativeButton(R.string.cancel, null)
                .showAccented()
        }
    }

    private fun markTempModeDiscovered(showSnack: Boolean) {
        if (!hasDiscoveredTempMode()) {
            uiHintsPrefs().edit { putBoolean(KEY_TEMP_MODE_DISCOVERED, true) }
            updateTempHintVisibility()
        }

        if (showSnack) {
            val snack = Snackbar.make(
                findViewById(android.R.id.content),
                getString(R.string.dashboard_temp_discovery_done),
                Snackbar.LENGTH_SHORT
            )
            runCatching {
                val anchor = findViewById<View>(R.id.bottomNav)
                if (anchor != null) snack.setAnchorView(anchor)
            }
            snack.applyLoqInStyle().show()
        }
    }

    private fun trackPrimaryToggleTapForTempNudge() {
        if (hasDiscoveredTempMode()) {
            return
        }

        val prefs = uiHintsPrefs()
        val taps = prefs.getInt(KEY_PRIMARY_TOGGLE_TAP_COUNT, 0) + 1
        prefs.edit { putInt(KEY_PRIMARY_TOGGLE_TAP_COUNT, taps) }

        if (taps == 5 || taps == 12) {
            val snack = Snackbar.make(
                findViewById(android.R.id.content),
                getString(R.string.dashboard_temp_nudge),
                Snackbar.LENGTH_SHORT
            )
            runCatching {
                val anchor = findViewById<View>(R.id.bottomNav)
                if (anchor != null) snack.setAnchorView(anchor)
            }
            snack.applyLoqInStyle().show()
        }
    }

    private fun applyLockedUi(locked: Boolean) {
        val profileLocked = locked || isProfileSwitchLockedWhileEnabled()
        val appPickingLocked = isAppPickingLockedWhileEnabled()
        val websitesLocked = EditingLockGuard.isLocked(this)
        val inAppLocked = EditingLockGuard.isLocked(this)
        val nfcWriteLocked = isNfcTagWritingLocked()
        val blockedNotificationsLocked = isProtectionActivelyEnforced()

        // Keep locked profile controls clickable so they can show the explanatory dialog instead of silently disabling the dropdown/end icon.
        profileDropdown.isEnabled = true
        rowActiveProfile.isEnabled = true
        btnPickApps.isEnabled = !appPickingLocked

        val profileAlpha = if (profileLocked) 0.5f else 1f
        val appPickingAlpha = if (appPickingLocked) 0.5f else 1f

        // Keep locked quick actions clickable so they can show explanatory dialogs where needed.
        // Keep locked tiles clickable so they can show the explanatory lock dialog.
        tileManageApps.isEnabled = true
        tileProfiles.isEnabled = true
        tileWriteNfc.isEnabled = true
        tileToggleOptions.isEnabled = true
        tileNfcWrite.isEnabled = true
        tileBlockedNotifications.isEnabled = true
        tileQr.isEnabled = true
        tileBarcode.isEnabled = true

        tileManageApps.alpha = appPickingAlpha
        tileProfiles.alpha = profileAlpha
        tileWriteNfc.alpha = if (websitesLocked) lockedTileAlpha() else 1f
        tileToggleOptions.alpha = if (inAppLocked) lockedTileAlpha() else 1f
        tileNfcWrite.alpha = if (nfcWriteLocked) lockedTileAlpha() else 1f
        tileBlockedNotifications.alpha = if (blockedNotificationsLocked) lockedTileAlpha() else 1f
        tileQr.alpha = 1f
        tileBarcode.alpha = 1f
        rowActiveProfile.alpha = profileAlpha
        btnPickApps.alpha = appPickingAlpha
    }

    private fun lockedTileAlpha(): Float = LockedUi.cardAlpha(this)

    private fun isNfcTagWritingLocked(): Boolean {
        return EditingLockGuard.isLocked(this) &&
            !AutomationModeStore.isNfcTagWritingAllowedWhileEnabled(this)
    }

    private fun refreshProfilesUi() {
        val profiles: List<String> = ProfileStore.getProfiles(this).toList().sorted()
        val adapter = LoqInDropdownAdapter(this, profiles)
        profileDropdown.setAdapter(adapter)

        // Keep dropdown width aligned with the field (prevents full-screen-wide popup on some OEMs).
        profileDropdown.post {
            profileDropdown.dropDownWidth = profileDropdown.width
        }

        val current = ProfileStore.getCurrent(this)
        if (current.isNullOrEmpty() && profiles.isNotEmpty()) {
            ProfileStore.setCurrent(this, profiles.first())
            profileDropdown.setText(profiles.first(), false)
        } else {
            profileDropdown.setText(current ?: "", false)
        }

        profileDropdown.setOnItemClickListener { _, _, pos, _ ->
            val selected = adapter.getItem(pos) ?: return@setOnItemClickListener
            switchToProfile(selected)
        }

        refreshProfileRows(profiles, current)
    }

    /**
     * Foqos HomeProfilesListView equivalent: one tappable row per profile showing
     * name, "N Apps | M Domains" metadata and an Active chip on the current profile.
     * Tapping a row switches the active profile (same flow as the old dropdown).
     */
    private fun refreshProfileRows(profiles: List<String>, current: String?) {
        if (!::profileRowsContainer.isInitialized) {
            return
        }
        profileRowsContainer.removeAllViews()
        refreshProfileHero(current)
        val inflater = android.view.LayoutInflater.from(this)

        profiles.forEachIndexed { index, profile ->
            val row = inflater.inflate(R.layout.row_home_profile, profileRowsContainer, false)
            val name = row.findViewById<TextView>(R.id.tvProfileRowName)
            val meta = row.findViewById<TextView>(R.id.tvProfileRowMeta)
            val active = row.findViewById<TextView>(R.id.tvProfileRowActive)

            name.text = profile
            val appCount = ProfileStore.getSelectedForProfileMode(this, profile).size
            val domainCount = DomainBlockStore.getDomainsForProfile(this, profile).size
            val appsLabel = resources.getQuantityString(R.plurals.profile_app_count, appCount, appCount)
            val domainsLabel = resources.getQuantityString(R.plurals.profile_website_count, domainCount, domainCount)
            meta.text = getString(R.string.profile_row_meta_fmt, appsLabel, domainsLabel)
            active.visibility = if (profile == current) View.VISIBLE else View.GONE

            row.setOnClickListener {
                if (profile == current) {
                    openProfileManagement()
                    return@setOnClickListener
                }
                switchToProfile(profile)
            }
            row.setOnLongClickListener {
                openProfileManagement()
                true
            }

            profileRowsContainer.addView(row)
            if (index < profiles.size - 1) {
                val divider = com.google.android.material.divider.MaterialDivider(this)
                divider.setDividerColorResource(com.oliver.loqin.R.color.foqos_outline_variant)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.marginStart = homeDp(52f)
                divider.layoutParams = lp
                profileRowsContainer.addView(divider)
            }
        }
    }

    private fun openProfileManagement() {
        startActivity(Intent(this, ManageProfilesActivity::class.java))
    }

    private fun switchToProfile(selected: String, anchor: View? = null): Boolean {
        if (!ensureCanSwitchProfiles(showFeedback = true, anchor = anchor)) {
            val cur = ProfileStore.getCurrent(this)
            profileDropdown.setText(cur ?: "", false)
            return false
        }

        ProfileStore.setCurrent(this, selected)
        refreshBlockedList()
        updateSwitchState()
        refreshProfilesUi()
        Snackbar.make(
            snackRoot(),
            getString(
                R.string.profile_set_active_rules_toast,
                selected,
                ProfileStore.getSelectedForProfileMode(this, selected).size.let { count -> resources.getQuantityString(R.plurals.profile_app_count, count, count) },
                DomainBlockStore.getDomains(this).size.let { count -> resources.getQuantityString(R.plurals.profile_website_count, count, count) },
            ),
            Snackbar.LENGTH_SHORT
        ).applyLoqInStyle().show()
        return true
    }

    private fun snackRoot(): View {
        return findViewById(android.R.id.content) ?: window.decorView
    }

    private fun showBlockedAppQuickActions(item: AppDisplay) {
        if (!item.isAvailable) {
            if (!ensureCanRemoveBlockedApp(showFeedback = true)) {
                return
            }
            confirmRemoveBlockedApp(item)
            return
        }

        val actions = mutableListOf<Pair<LoqInDialogOption, () -> Unit>>()

        if (hasWebsiteRulesShortcut(item)) {
            actions += LoqInDialogOption(
                title = getString(R.string.app_picker_row_action_website_rules),
                summary = getString(R.string.app_picker_row_action_website_rules_summary),
                iconRes = R.drawable.language_24
            ) to { openWebsiteRulesFromHomeList(item) }
        }

        if (hasInAppRulesShortcut(item)) {
            actions += LoqInDialogOption(
                title = getString(R.string.app_picker_row_action_in_app_rules),
                summary = getString(R.string.app_picker_row_action_in_app_rules_summary),
                iconRes = R.drawable.tune_24
            ) to { openInAppRulesFromHomeList(item) }
        }

        actions += LoqInDialogOption(
            title = getString(R.string.dashboard_blocked_app_action_edit_limits),
            summary = getString(R.string.dashboard_blocked_app_action_edit_limits_summary, item.label),
            iconRes = R.drawable.schedule_24
        ) to { showBlockedAppLimitActions(item) }

        actions += LoqInDialogOption(
            title = getString(R.string.dashboard_blocked_app_action_remove),
            summary = getString(R.string.dashboard_blocked_app_action_remove_summary, item.label),
            iconRes = R.drawable.delete_24,
            destructive = true
        ) to { confirmRemoveBlockedApp(item) }

        showLoqInOptionDialog(
            title = item.label,
            options = actions.map { it.first },
            showCancelButton = false
        ) { index ->
            actions.getOrNull(index)?.second?.invoke()
        }
    }

    private fun hasWebsiteRulesShortcut(item: AppDisplay): Boolean =
        item.isAvailable && isBrowserPackage(item.pkg)

    private fun hasInAppRulesShortcut(item: AppDisplay): Boolean =
        item.isAvailable && item.pkg in InAppRuleStore.supportedPackages()

    private fun openWebsiteRulesFromHomeList(item: AppDisplay) {
        snackRoot().showWarnPill(getString(R.string.app_picker_open_website_rules_for, item.label))
        startActivity(Intent(this, ManageBlockedWebsitesActivity::class.java))
    }

    private fun openInAppRulesFromHomeList(item: AppDisplay) {
        snackRoot().showWarnPill(getString(R.string.app_picker_open_in_app_rules_for, item.label))
        startActivity(
            Intent(this, InAppRulesActivity::class.java)
                .putExtra(InAppRulesActivity.EXTRA_FOCUS_PACKAGE, item.pkg)
        )
    }

    private fun showBlockedAppLimitActions(item: AppDisplay) {
        QuickLimitDialogs.showForApp(
            activity = this,
            pkg = item.pkg,
            label = item.label,
            onChanged = { refreshBlockedList() }
        )
    }

    private fun confirmRemoveBlockedApp(item: AppDisplay) {
        if (!ensureCanRemoveBlockedApp(showFeedback = true)) {
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dashboard_blocked_app_remove_title)
            .setMessage(getString(R.string.dashboard_blocked_app_remove_message, item.label))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                removeBlockedApp(item)
            }
            .showAccented()
    }

    private fun removeBlockedApp(item: AppDisplay) {
        val profile = ProfileStore.getCurrent(this)
        if (profile.isNullOrBlank()) {
            return
        }

        val selected = ProfileStore.getSelectedForProfileMode(this, profile).toMutableSet()
        selected.remove(item.pkg)
        ProfileStore.setSelectedForProfileMode(this, profile, selected)

        UsageLimitStore.setLimitMinutes(this, profile, item.pkg, 0)
        SessionLimitStore.setLimitMinutes(this, profile, item.pkg, 0)
        AttemptLimitStore.setLimitAttempts(this, profile, item.pkg, 0)
        OpenCountStore.setToday(this, profile, item.pkg, 0)
        LimitReachedStore.clearToday(this, item.pkg)

        BlockingRuntime.ensureRunning(this)
        refreshBlockedList()

        Snackbar.make(
            snackRoot(),
            getString(R.string.dashboard_blocked_app_removed, item.label),
            Snackbar.LENGTH_SHORT
        ).applyLoqInStyle().show()
    }

    private fun updateManagedAppListHeader(profile: String?) {
        val isAllow = !profile.isNullOrBlank() && ProfileRuleModeStore.isAllowMode(this, profile)
        if (::blockedHeader.isInitialized) {
            blockedHeader.setText(if (isAllow) R.string.pick_allowed_apps else R.string.pick_blocked_apps)
        }
        if (::tvEmpty.isInitialized) {
            tvEmpty.setText(if (isAllow) R.string.no_allowed_apps else R.string.no_blocked_apps)
        }
    }

    private fun isManagedAppListExpanded(): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        return sp.getBoolean(KEY_BLOCKED_APPS_EXPANDED, false)
    }

    private fun toggleManagedAppListExpanded() {
        val expanded = !isManagedAppListExpanded()
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putBoolean(KEY_BLOCKED_APPS_EXPANDED, expanded)
        }
        updateManagedAppListExpandedUi(animate = true)
    }

    private fun updateManagedAppListExpandedUi(animate: Boolean) {
        if (!::layoutBlockedAppsContent.isInitialized || !::ivBlockedAppsChevron.isInitialized) {
            return
        }

        val expanded = isManagedAppListExpanded()
        ivBlockedAppsChevron.animate().cancel()
        ivBlockedAppsChevron.rotation = 0f
        ivBlockedAppsChevron.setImageResource(
            if (expanded) R.drawable.keyboard_arrow_up_24 else R.drawable.keyboard_arrow_down_24
        )
        ivBlockedAppsChevron.contentDescription = getString(
            if (expanded) R.string.dashboard_blocked_apps_collapse else R.string.dashboard_blocked_apps_expand
        )

        if (animate) {
            if (expanded) {
                layoutBlockedAppsContent.showFade()
            } else {
                layoutBlockedAppsContent.hideFade()
            }
        } else {
            layoutBlockedAppsContent.visibility = if (expanded) View.VISIBLE else View.GONE
            layoutBlockedAppsContent.alpha = 1f
        }
    }

    private fun refreshBlockedList() {
        val current = ProfileStore.getCurrent(this)
        updateManagedAppListHeader(current)
        val pkgs: List<String> = loadBlockedPkgsFor(current)
        val appContext = applicationContext
        val requestSeq = ++blockedListRefreshSeq

        blockedListRefreshJob?.cancel()
        blockedListRefreshJob = lifecycleScope.launch {
            val items: List<AppDisplay> = withContext(Dispatchers.IO) {
                val ioContext = currentCoroutineContext()
                pkgs
                    .asSequence()
                    .mapNotNull { pkg ->
                        if (!ioContext.isActive) return@mapNotNull null
                        resolveAppDisplay(appContext, pkg)
                    }
                    .sortedBy { app -> app.label.lowercase() }
                    .toList()
            }

            if (requestSeq != blockedListRefreshSeq || !currentCoroutineContext().isActive) return@launch

            val isEmpty = items.isEmpty()
            if (!shouldShowHomeBlockedApps()) {
                cardBlockedApps.hideFade()
                layoutBlockedAppsEmpty.hideFade()
                rvBlocked.hideFade()
            } else {
                cardBlockedApps.showFade()
                updateManagedAppListExpandedUi(animate = false)
                if (isEmpty) {
                    layoutBlockedAppsEmpty.showFade()
                    rvBlocked.hideFade()
                } else {
                    layoutBlockedAppsEmpty.hideFade()
                    rvBlocked.showFade()
                }
            }

            blockedAdapter.submitList(items) {
                // Rule/limit summaries are backed by stores rather than AppDisplay fields, so DiffUtil can legitimately see the same item after an edit.
                // Rebind the committed managed-app rows without invalidating the whole RecyclerView.
                val itemCount = blockedAdapter.itemCount
                if (itemCount > 0) {
                    blockedAdapter.notifyItemRangeChanged(0, itemCount)
                }
            }

        }
    }

    private fun loadBlockedPkgsFor(profile: String?): List<String> {
        if (profile.isNullOrEmpty()) {
            return emptyList()
        }

        // Limited apps are also managed by the profile and should appear together with the other selected apps, even if the stored selected-app set was not updated.
        val explicitlyBlocked = ProfileStore.getSelectedForProfileMode(this, profile)

        val limited = buildSet {
            addAll(UsageLimitStore.getAllLimitedPackages(this@MainActivity, profile))
            addAll(SessionLimitStore.getAllLimitedPackages(this@MainActivity, profile))
            addAll(AttemptLimitStore.getAllLimitedPackages(this@MainActivity, profile))
        }
        val inAppRulePackages = InAppRuleStore.getPackagesWithEnabledRules(this, profile)

        return (explicitlyBlocked + limited + inAppRulePackages)
            .asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
    }

    private fun isInAppOnlyManagedApp(ctx: Context, profile: String, pkgName: String): Boolean {
        if (!InAppRuleStore.hasEnabledRulesForPackage(ctx, profile, pkgName)) {
            return false
        }
        return !ProfileStore.getBlockedForProfile(ctx, profile).contains(pkgName)
    }

    private fun resolveAppDisplay(context: Context, pkg: String): AppDisplay {
        val pm = context.packageManager
        val fallbackIcon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)!!
        return try {
            val ai = pm.getApplicationInfo(pkg, 0)
            val label = runCatching { pm.getApplicationLabel(ai)?.toString() }.getOrNull() ?: pkg
            val icon = runCatching { pm.getApplicationIcon(pkg).toSafeListIcon(context) }.getOrDefault(fallbackIcon)
            AppDisplay(label, pkg, icon, isAvailable = true)
        } catch (_: PackageManager.NameNotFoundException) {
            AppDisplay(
                label = pkg,
                pkg = pkg,
                icon = fallbackIcon,
                isAvailable = false
            )
        }
    }

    private fun Drawable.toSafeListIcon(context: Context): Drawable {
        val size = (48f * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val oldBounds = copyBounds()
        setBounds(0, 0, size, size)
        draw(canvas)
        bounds = oldBounds
        return bitmap.toDrawable(context.resources)
    }

    data class AppDisplay(
        val label: String,
        val pkg: String,
        val icon: Drawable,
        val isAvailable: Boolean
    )

    private class BlockedAppsAdapter(
        private val onAppClick: (AppDisplay) -> Unit,
        private val onEditClick: (AppDisplay) -> Unit,
        private val canEdit: () -> Boolean
    ) : ListAdapter<AppDisplay, BlockedAppsAdapter.VH>(DIFF) {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val cardRoot: MaterialCardView = v.findViewById(R.id.cardRoot)
            val icon: ImageView = v.findViewById(R.id.appIcon)
            val name: TextView = v.findViewById(R.id.appName)
            val pkg: TextView = v.findViewById(R.id.appPkg)
            val unavailableChip: TextView = v.findViewById(R.id.tvUnavailableChip)
            val blockedNowChip: TextView = v.findViewById(R.id.tvBlockedNowChip)
            val rule: TextView = v.findViewById(R.id.tvRule)
            val unavailableHint: TextView = v.findViewById(R.id.tvUnavailableHint)
            val quickEditButton: View = v.findViewById(R.id.btnQuickEdit)

            private fun dp(value: Float): Int =
                (value * itemView.resources.displayMetrics.density).toInt()

            fun updateBlockedNowChipOnly(item: AppDisplay) {
                val ctx = itemView.context

                // Uninstalled apps should only show the "Not installed" chip on the main list.
                if (!item.isAvailable) {
                    blockedNowChip.visibility = View.GONE
                    return
                }

                val enabled = SwitchModeStore.isEnabled(ctx)
                val emergency = EmergencyBypassStore.isActive(ctx)
                val profile = ProfileStore.getCurrent(ctx)

                val limitMin = if (!profile.isNullOrBlank()) {
                    UsageLimitStore.getLimitMinutes(ctx, profile, item.pkg)
                } else 0

                val sessionLimitMin = if (!profile.isNullOrBlank()) {
                    SessionLimitStore.getLimitMinutes(ctx, profile, item.pkg)
                } else 0

                val attemptLimit = if (!profile.isNullOrBlank()) {
                    AttemptLimitStore.getLimitAttempts(ctx, profile, item.pkg)
                } else 0

                val opensAtOrOver = if (attemptLimit > 0) {
                    OpenCountStore.getToday(ctx, profile ?: "default", item.pkg) >= attemptLimit
                } else false

                val limitReached = if (limitMin > 0 && !profile.isNullOrBlank()) {
                    val resetMode = UsageLimitResetStore.getMode(ctx, profile, item.pkg)
                    if (resetMode == UsageLimitResetStore.MODE_SESSION) {
                        UsageLimitSessionRuntimeStore.get(ctx, profile, item.pkg)?.reached == true
                    } else {
                        LimitReachedStore.isReachedToday(ctx, profile, item.pkg)
                    }
                } else false

                val hasAnyLimit = limitMin > 0 || sessionLimitMin > 0 || attemptLimit > 0
                val isActive = enabled && !emergency && !profile.isNullOrBlank()
                val reasonRes = when {
                    !isActive -> null
                    (limitMin > 0 && limitReached) -> R.string.dashboard_blocked_now_reason_limit
                    (attemptLimit > 0 && opensAtOrOver) -> R.string.dashboard_blocked_now_reason_attempts
                    !hasAnyLimit -> R.string.dashboard_blocked_now_reason_profile
                    else -> null
                }

                if (reasonRes == null) {
                    blockedNowChip.visibility = View.GONE
                } else {
                    blockedNowChip.text = ctx.getString(reasonRes)
                    blockedNowChip.visibility = View.VISIBLE
                }
            }

            private fun buildRuleText(ctx: Context, pkgName: String): String {
                val profile = ProfileStore.getCurrent(ctx)
                val limitMin = if (!profile.isNullOrBlank()) {
                    UsageLimitStore.getLimitMinutes(ctx, profile, pkgName)
                } else 0
                val sessionLimitMin = if (!profile.isNullOrBlank()) {
                    SessionLimitStore.getLimitMinutes(ctx, profile, pkgName)
                } else 0
                val attemptLimit = if (!profile.isNullOrBlank()) {
                    AttemptLimitStore.getLimitAttempts(ctx, profile, pkgName)
                } else 0

                val lines = mutableListOf<String>()
                if (limitMin > 0) {
                    val profileName = profile.orEmpty()
                    val resetMode = if (profileName.isNotBlank()) {
                        UsageLimitResetStore.getMode(ctx, profileName, pkgName)
                    } else UsageLimitResetStore.MODE_DAY
                    lines += ctx.getString(
                        if (resetMode == UsageLimitResetStore.MODE_SESSION) R.string.session_reset_limit_value_format else R.string.daily_limit_value_format,
                        limitMin
                    )
                    if (resetMode == UsageLimitResetStore.MODE_SESSION && profileName.isNotBlank()) {
                        UsageLimitSessionRuntimeStore.get(ctx, profileName, pkgName)?.let { state ->
                            lines += ctx.getString(
                                R.string.session_limit_usage_summary,
                                StatsFormat.prettyMsWithSeconds(state.usedMs),
                                StatsFormat.prettyMsWithSeconds(state.limitMs),
                                StatsFormat.prettyMsWithSeconds(state.remainingMs)
                            )
                        }
                    }
                }
                if (sessionLimitMin > 0) {
                    lines += ctx.getString(R.string.session_limit_label, sessionLimitMin)
                }
                if (attemptLimit > 0) {
                    lines += ctx.resources.getQuantityString(
                        R.plurals.daily_attempt_limit_value_format,
                        attemptLimit,
                        attemptLimit
                    )
                }

                if (lines.isNotEmpty()) {
                    return when (lines.size) {
                        1 -> lines.first()
                        2 -> lines.joinToString(separator = "  –  ")
                        else -> lines.joinToString(separator = "  •  ")
                    }
                }

                if (!profile.isNullOrBlank() && isInAppOnlyManagedApp(ctx, profile, pkgName)) {
                    return ctx.getString(R.string.app_picker_in_app_rules_pinned_hint)
                }

                return ctx.getString(R.string.in_app_surface_always_block)
            }

            private fun isInAppOnlyManagedApp(ctx: Context, profile: String, pkgName: String): Boolean {
                if (!InAppRuleStore.hasEnabledRulesForPackage(ctx, profile, pkgName)) {
                    return false
                }
                return !ProfileStore.getBlockedForProfile(ctx, profile).contains(pkgName)
            }

            fun bind(item: AppDisplay, editEnabled: Boolean) {
                val ctx = itemView.context
                icon.setImageDrawable(item.icon)
                pkg.text = item.pkg

                updateBlockedNowChipOnly(item)
                updateQuickEditState(item, editEnabled)

                if (item.isAvailable) {
                    name.text = item.label
                    rule.text = buildRuleText(ctx, item.pkg)
                    rule.visibility = View.VISIBLE
                    cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.foqos_surface))
                    cardRoot.strokeColor = ContextCompat.getColor(ctx, R.color.foqos_outline_variant)
                    unavailableChip.visibility = View.GONE
                    unavailableHint.visibility = View.GONE
                } else {
                    rule.visibility = View.GONE
                    name.text = ctx.getString(R.string.unavailable_app_label)
                    cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.unavailable_row_bg))
                    cardRoot.strokeColor = ContextCompat.getColor(ctx, R.color.unavailable_row_stroke)
                    blockedNowChip.visibility = View.GONE

                    val chipBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(999f).toFloat()
                        setColor(ContextCompat.getColor(ctx, R.color.unavailable_chip_bg))
                    }
                    unavailableChip.background = chipBg
                    unavailableChip.setTextColor(ContextCompat.getColor(ctx, R.color.unavailable_chip_text))
                    unavailableChip.visibility = View.VISIBLE
                    unavailableHint.visibility = View.GONE
                }
            }

            fun updateQuickEditState(item: AppDisplay, editEnabled: Boolean) {
                val enabled = editEnabled
                quickEditButton.visibility = View.VISIBLE
                quickEditButton.isEnabled = enabled
                quickEditButton.isClickable = enabled
                quickEditButton.alpha = when {
                    enabled -> if (item.isAvailable) 1f else 0.72f
                    else -> 0.38f
                }

                cardRoot.isClickable = enabled
                cardRoot.isFocusable = enabled
            }
        }

        private fun bindActions(holder: VH, item: AppDisplay, editEnabled: Boolean) {
            holder.cardRoot.setOnClickListener {
                if (editEnabled) onAppClick(item)
            }
            holder.quickEditButton.setOnClickListener {
                if (editEnabled) onEditClick(item)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.row_blocked_app, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
            val item = getItem(position)
            var handled = false

            if (payloads.contains(PAYLOAD_BLOCKED_CHIPS)) {
                holder.updateBlockedNowChipOnly(item)
                handled = true
            }
            if (payloads.contains(PAYLOAD_BLOCKED_EDIT_STATE)) {
                val editEnabled = canEdit()
                holder.updateQuickEditState(item, editEnabled)
                bindActions(holder, item, editEnabled)
                handled = true
            }
            if (handled) {
                return
            }
            super.onBindViewHolder(holder, position, payloads)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            val editEnabled = canEdit()
            holder.bind(item, editEnabled)
            bindActions(holder, item, editEnabled)
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<AppDisplay>() {
                override fun areItemsTheSame(oldItem: AppDisplay, newItem: AppDisplay): Boolean {
                    return oldItem.pkg == newItem.pkg
                }

                override fun areContentsTheSame(oldItem: AppDisplay, newItem: AppDisplay): Boolean {
                    return oldItem.pkg == newItem.pkg &&
                        oldItem.label == newItem.label &&
                        oldItem.isAvailable == newItem.isAvailable
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_top_main, menu)

        // Icons follow the surface text color (the old hardcoded white vanishes on the
        // light header; night keeps them light via the foqos_* night tokens).
        val onSurface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurface,
            android.graphics.Color.BLACK,
        )
        for (item in menu) {
            item.icon?.mutate()?.setTint(onSurface)
        }

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val scannerAvailable = AutomationModeStore.isQrAllowed(this) ||
            AutomationModeStore.isBarcodeAllowed(this)

        // The shared scanner entry stays reachable while LoqIn is active, even when Settings is locked or the optional Home quick-action cards are hidden.
        menu.findItem(R.id.action_scanner_header)?.isVisible = scannerAvailable
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_scanner_header -> {
                openHeaderScanner()
                true
            }
            R.id.action_settings_gear -> {
                SettingsActivity.openWithAccessCheck(this)
                true
            }
            R.id.action_info -> {
                showDevelopmentInfoDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private var sessionMissedBottomSheet: BottomSheetDialog? = null

    private fun checkAndShowSessionMissedNotifications() {
        if (isFinishing || isDestroyed) return
        if (sessionMissedBottomSheet?.isShowing == true) return
        if (!SessionMissedNotificationsStore.isFeatureEnabled(this)) return

        val events = SessionMissedNotificationsStore.consumePendingMissedNotifications(this)
        if (events.isEmpty()) return

        showSessionMissedNotificationsSheet(events)
    }

    private fun showSessionMissedNotificationsSheet(events: List<BlockedNotificationEvent>) {
        if (isFinishing || isDestroyed) return

        val sheet = BottomSheetDialog(this)
        sessionMissedBottomSheet = sheet
        val parent = findViewById<ViewGroup>(android.R.id.content)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_session_missed_notifications, parent, false)
        sheet.setContentView(view)
        sheet.prepareExpandedSheet()

        val count = events.size
        val subtitleText = if (count == 1) {
            getString(R.string.session_missed_notifications_count_single)
        } else {
            getString(R.string.session_missed_notifications_count_fmt, count)
        }
        view.findViewById<TextView>(R.id.tvSessionMissedSubtitle)?.text = subtitleText

        view.findViewById<View>(R.id.roundelBg)?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AccentColor.getAccentContainerColorInt(this@MainActivity))
        }
        view.findViewById<ImageView>(R.id.ivIcon)?.imageTintList =
            ColorStateList.valueOf(AccentColor.getAccentColorInt(this@MainActivity))

        view.findViewById<View>(R.id.btnClose)?.setOnClickListener {
            sheet.dismiss()
        }

        val recycler = view.findViewById<RecyclerView>(R.id.rvSessionMissedNotifications)
        recycler?.layoutManager = LinearLayoutManager(this)

        val maxRecyclerHeight = (280 * resources.displayMetrics.density + 0.5f).toInt()
        recycler?.viewTreeObserver?.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                recycler.viewTreeObserver.removeOnPreDrawListener(this)
                if (recycler.height > maxRecyclerHeight) {
                    recycler.layoutParams.height = maxRecyclerHeight
                    recycler.requestLayout()
                }
                return true
            }
        })

        val timeFmt = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        val pm = packageManager

        class MissedNotificationAdapter : RecyclerView.Adapter<MissedNotificationAdapter.VH>() {
            inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
                val icon: ImageView = itemView.findViewById(R.id.ivNotificationIcon)
                val title: TextView = itemView.findViewById(R.id.tvNotificationTitle)
                val time: TextView = itemView.findViewById(R.id.tvNotificationTime)
                val body: TextView = itemView.findViewById(R.id.tvNotificationBody)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
                val v = layoutInflater.inflate(R.layout.item_session_missed_notification, parent, false)
                return VH(v)
            }

            override fun onBindViewHolder(holder: VH, position: Int) {
                val item = events[position]
                val appLabel = runCatching {
                    val ai = pm.getApplicationInfo(item.pkg, 0)
                    pm.getApplicationLabel(ai)?.toString().orEmpty().ifBlank { item.pkg }
                }.getOrDefault(item.pkg)

                holder.title.text = appLabel
                holder.time.text = timeFmt.format(java.util.Date(item.timeMillis))

                val content = listOfNotNull(
                    item.title.takeIf { it.isNotBlank() },
                    item.text.takeIf { it.isNotBlank() }
                ).joinToString(" — ").ifBlank {
                    getString(R.string.blocked_inbox_content_unknown)
                }
                holder.body.text = content

                val appIcon = runCatching {
                    val ai = pm.getApplicationInfo(item.pkg, 0)
                    pm.getApplicationIcon(ai)
                }.getOrNull()
                if (appIcon != null) {
                    holder.icon.setImageDrawable(appIcon)
                } else {
                    holder.icon.setImageResource(R.drawable.notifications_24)
                }

                holder.itemView.setOnClickListener {
                    sheet.dismiss()
                    startActivity(Intent(this@MainActivity, BlockedInboxActivity::class.java))
                }
            }

            override fun getItemCount(): Int = events.size
        }

        recycler?.adapter = MissedNotificationAdapter()

        val accent = AccentColor.getAccentColorInt(this)
        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.5) Color.BLACK else Color.WHITE

        val cbNeverAgain = view.findViewById<MaterialCheckBox>(R.id.cbSessionMissedNeverAgain)
        cbNeverAgain?.let { cb ->
            val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.DKGRAY)
            cb.buttonTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    accent,
                    ColorUtils.setAlphaComponent(onSurface, 0x8A)
                )
            )
        }

        val handleNeverAgainIfChecked: () -> Unit = {
            if (cbNeverAgain?.isChecked == true && SessionMissedNotificationsStore.isFeatureEnabled(this)) {
                SessionMissedNotificationsStore.setFeatureEnabled(this, false)
                snackRoot().showWarnPill(R.string.session_missed_notifications_disabled_hint)
            }
        }

        sheet.setOnDismissListener {
            sessionMissedBottomSheet = null
            handleNeverAgainIfChecked()
        }

        view.findViewById<View>(R.id.btnClose)?.setOnClickListener {
            sheet.dismiss()
            handleNeverAgainIfChecked()
        }

        val btnDone = view.findViewById<MaterialButton>(R.id.btnSessionMissedDone)
        btnDone?.backgroundTintList = ColorStateList.valueOf(accent)
        btnDone?.setTextColor(onAccent)
        btnDone?.setOnClickListener {
            handleNeverAgainIfChecked()
            sheet.dismiss()
        }

        val btnInbox = view.findViewById<MaterialButton>(R.id.btnSessionMissedInbox)
        btnInbox?.strokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x66))
        btnInbox?.setTextColor(accent)
        btnInbox?.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x1A))
        btnInbox?.setOnClickListener {
            handleNeverAgainIfChecked()
            sheet.dismiss()
            startActivity(Intent(this, BlockedInboxActivity::class.java))
        }

        sheet.show()
    }

    private fun showDevelopmentInfoDialog() {
        // Legacy info dialog for the header info button.
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.main_info_title))
            .setMessage(getString(R.string.main_development_info_message))
            .setPositiveButton(getString(R.string.support_logs_unified_title)) { _, _ ->
                startActivity(Intent(this, SupportLogActivity::class.java))
            }
            .setNegativeButton(getString(R.string.close), null)
            .showAccented()
    }

    private fun openHeaderScanner() {
        val qrAllowed = AutomationModeStore.isQrAllowed(this)
        val barcodeAllowed = AutomationModeStore.isBarcodeAllowed(this)
        val mode = when {
            qrAllowed && barcodeAllowed -> UnifiedScanActivity.ScanMode.AUTO
            qrAllowed -> UnifiedScanActivity.ScanMode.QR_ONLY
            barcodeAllowed -> UnifiedScanActivity.ScanMode.BARCODE_ONLY
            else -> return
        }
        openUnifiedScannerDirectly(mode)
    }

    private fun openUnifiedScannerDirectly(mode: UnifiedScanActivity.ScanMode) {
        startActivity(
            Intent(this, UnifiedScanActivity::class.java)
                .putExtra(UnifiedScanActivity.EXTRA_ALLOW_DIRECT_OPEN, true)
                .putExtra(UnifiedScanActivity.EXTRA_SCAN_MODE, mode.raw)
        )
    }

    private fun openQrScannerDirectly() {
        openUnifiedScannerDirectly(UnifiedScanActivity.ScanMode.QR_ONLY)
    }

    private fun openBarcodeScannerDirectly() {
        openUnifiedScannerDirectly(UnifiedScanActivity.ScanMode.BARCODE_ONLY)
    }

    private fun showBarcodeChoiceDialog() {
        showLoqInOptionDialog(
            title = getString(R.string.dashboard_tile_barcode),
            options = listOf(
                LoqInDialogOption(
                    title = getString(R.string.barcode_scan_title),
                    summary = getString(R.string.barcode_scan_option_summary),
                    iconRes = R.drawable.barcode_24
                ),
                LoqInDialogOption(
                    title = getString(R.string.manage_barcodes_title),
                    summary = getString(R.string.barcode_manage_option_summary),
                    iconRes = R.drawable.edit_24
                )
            )
        ) { which ->
            when (which) {
                0 -> openBarcodeScannerDirectly()
                1 -> {
                    when {
                        !AutomationModeStore.shouldShowBarcodeTools(this) ->
                            snackRoot().showWarnPill(R.string.toast_manage_barcodes_requires_enabled)
                        EditingLockGuard.isLocked(this) ->
                            EditingLockGuard.showLockedDialog(this, R.string.edit_locked_manage_barcodes)
                        else -> startActivity(Intent(this, ManageBarcodesActivity::class.java))
                    }
                }
            }
        }
    }

    private fun showQrChoiceDialog() {
        showLoqInOptionDialog(
            title = getString(R.string.dashboard_tile_qr),
            options = listOf(
                LoqInDialogOption(
                    title = getString(R.string.qr_scan_title),
                    summary = getString(R.string.qr_scan_option_summary),
                    iconRes = R.drawable.qr_code_24
                ),
                LoqInDialogOption(
                    title = getString(R.string.qr_generate_title),
                    summary = getString(R.string.qr_manage_option_summary),
                    iconRes = R.drawable.edit_24
                )
            )
        ) { which ->
            when (which) {
                0 -> openQrScannerDirectly()
                1 -> {
                    when {
                        !AutomationModeStore.shouldShowQrTools(this) ->
                            snackRoot().showWarnPill(R.string.toast_manage_qr_requires_enabled)
                        EditingLockGuard.isLocked(this) ->
                            EditingLockGuard.showLockedDialog(this, R.string.edit_locked_manage_qr_codes)
                        else -> startActivity(Intent(this, QrGenerateActivity::class.java))
                    }
                }
            }
        }
    }
}

// Foqos-style hero artwork rendering is now located in HeroArtRenderer.kt




