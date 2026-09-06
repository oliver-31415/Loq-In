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

package at.saltyy.switchly.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import at.saltyy.switchly.R
import at.saltyy.switchly.blocking.BlockingRuntime
import at.saltyy.switchly.data.prefs.AppLogStore
import at.saltyy.switchly.data.prefs.EmergencyBypassStore
import at.saltyy.switchly.data.prefs.EmergencyPinStore
import at.saltyy.switchly.data.prefs.SwitchModeStore
import at.saltyy.switchly.feature.premium.PremiumInfoActivity
import at.saltyy.switchly.feature.qr.QrGenerateActivity
import at.saltyy.switchly.feature.schedule.SchedulesActivity
import at.saltyy.switchly.feature.tools.RulesHubActivity
import at.saltyy.switchly.feature.tools.ManageKeysActivity
import at.saltyy.switchly.feature.tools.ActivityHubActivity
import at.saltyy.switchly.feature.usage.IgnoredUsageAppsActivity
import at.saltyy.switchly.nfc.NfcWriterActivity
import at.saltyy.switchly.theme.AccentColor
import at.saltyy.switchly.ui.EdgeToEdgeUtils
import at.saltyy.switchly.ui.LockedUi
import at.saltyy.switchly.ui.MainActivity
import at.saltyy.switchly.ui.ThemeUtils
import at.saltyy.switchly.ui.dialog.SwitchlyDialogOption
import at.saltyy.switchly.ui.dialog.SwitchlyInfoRow
import at.saltyy.switchly.ui.dialog.showSwitchlyInfoDialog
import at.saltyy.switchly.ui.dialog.showAccented
import at.saltyy.switchly.ui.dialog.showSwitchlyOptionDialog
import at.saltyy.switchly.ui.dialog.styleSwitchlyDialogButtons
import at.saltyy.switchly.ui.dialog.EmergencyPinDialog
import at.saltyy.switchly.util.LocaleHelper
import at.saltyy.switchly.util.ActivityTransitionCompat
import at.saltyy.switchly.util.SwitchlyAppAccessGuard
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class SettingsActivity : AppCompatActivity() {

    // Owns activity-result launchers: property init runs before onCreate, so
    // registration happens before STARTED. Fragments must use this instance —
    // constructing BackupFlowActions in a later onAttach (nested screens)
    // crashes with "register before they are STARTED".
    val backupFlows = BackupFlowActions(this)

    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var rootScroll: View
    private lateinit var container: View

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupViews()
        setupToolbar()
        setupToolbarTitleSync()
        setupBottomNav()
        setupRootCards()
        restoreScreenState(savedInstanceState)
        updateTitleFromFragment()
        applyRestrictedAccessState()
    }

    override fun onResume() {
        super.onResume()
        applyRestrictedAccessState()
    }

    override fun onSupportNavigateUp(): Boolean {
        return if (supportFragmentManager.backStackEntryCount > 0 || container.isVisible) {
            onBackPressedDispatcher.onBackPressed()
            true
        } else {
            false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_find_setting -> {
                showSettingsFinder()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsFinder() {
        data class SettingsSearchItem(
            val title: String,
            val searchableText: String,
            val open: () -> Unit,
        ) {
            override fun toString(): String = title
        }

        val items = mutableListOf<SettingsSearchItem>()

        fun add(titleRes: Int, extraTerms: String = "", open: () -> Unit) {
            val title = getString(titleRes)
            items += SettingsSearchItem(
                title = title,
                searchableText = "$title $extraTerms".trim(),
                open = open,
            )
        }

        fun addText(title: String, extraTerms: String = "", open: () -> Unit) {
            if (title.isBlank()) return
            items += SettingsSearchItem(
                title = title,
                searchableText = "$title $extraTerms".trim(),
                open = open,
            )
        }

        fun openRootCard(cardId: Int) {
            showRootSettings()
            findViewById<View>(cardId)?.performClick()
        }

        fun openProtectedActivity(intent: Intent) {
            openProtectedSettingsSection { startActivity(intent) }
        }

        fun openToggleSection(section: String, displayTarget: String? = null) {
            val open = {
                startActivity(Intent(this, ToggleOptionsActivity::class.java).apply {
                    putExtra(ToggleOptionsActivity.EXTRA_VIEW_SECTION, section)
                    if (!displayTarget.isNullOrBlank()) {
                        putExtra(ToggleOptionsActivity.EXTRA_DISPLAY_TARGET, displayTarget)
                    }
                })
            }
            if (section == ToggleOptionsActivity.SECTION_BLOCKING) {
                openControlSettingsSection(open)
            } else {
                openProtectedSettingsSection(open)
            }
        }

        fun openPermissionSection(section: String, target: String? = null) {
            openProtectedActivity(Intent(this, PermissionsActivity::class.java).apply {
                putExtra(PermissionsActivity.EXTRA_FOCUS_SECTION, section)
                if (!target.isNullOrBlank()) {
                    putExtra(PermissionsActivity.EXTRA_FOCUS_TARGET, target)
                }
            })
        }

        val oemBackgroundSearchTerms = listOf(
            "manufacturer", "Hersteller", "OEM", "background", "Hintergrund", "autostart", "Autostart",
            "battery", "Akku", "startup", "reboot", "Neustart", "power management",
            "xiaomi", "redmi", "poco", "miui", "hyperos",
            "samsung", "one ui", "oneui",
            "huawei", "honor", "emui", "magic os", "magicos",
            "oppo", "coloros", "realme", "oneplus", "oxygenos",
            "vivo", "iqoo", "funtouch",
            "motorola", "lenovo", "asus", "sony", "nokia", "zte", "tecno", "infinix",
            Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty(),
        ).filter { it.isNotBlank() }.joinToString(" ")

        // Top-level destinations.
        add(R.string.toggle_group_manage_other_blocking_features, "${getString(R.string.settings_search_terms_blocking)} temporary timer temporärer Timer safety Sicherheit status notification Benachrichtigung blocking features") {
            openRootCard(R.id.cardSettingsBlockingFeatures)
        }
        add(R.string.settings_theme_title, "theme appearance color language time home") { openRootCard(R.id.cardSettingsAppearance) }
        add(R.string.ignored_usage_apps_title, "usage statistics ignored apps") { openRootCard(R.id.cardSettingsIgnoredApps) }
        add(R.string.settings_display_shortcuts_title, "${getString(R.string.settings_search_terms_display)} widgets Kacheln tiles quick settings shortcuts Verknüpfungen home Startseite") { openRootCard(R.id.cardSettingsDisplayShortcuts) }
        add(R.string.pref_permissions_title, "${getString(R.string.settings_search_terms_permissions)} battery Akku background Hintergrund accessibility Bedienungshilfe autostart Autostart notifications Benachrichtigungen NFC reliability Zuverlässigkeit") { openRootCard(R.id.cardSettingsPermissions) }
        add(R.string.settings_section_info, "info about app device help FAQ support changelog whats new disclaimer") { openRootCard(R.id.cardSettingsInfo) }

        // Individual control-mode settings.
        add(R.string.pref_mode_nfc_title, "NFC control mode") { openProtectedActivity(Intent(this, BlockingModesActivity::class.java)) }
        add(R.string.pref_mode_qr_title, "QR control mode") { openProtectedActivity(Intent(this, BlockingModesActivity::class.java)) }
        add(R.string.pref_mode_barcode_title, "barcode control mode") { openProtectedActivity(Intent(this, BlockingModesActivity::class.java)) }
        add(R.string.pref_mode_schedule_title, "schedule control mode automation") { openProtectedActivity(Intent(this, BlockingModesActivity::class.java)) }
        add(R.string.pref_require_nfc_unlock_title, "NFC required disable lock") { openToggleSection(ToggleOptionsActivity.SECTION_SAFETY) }
        add(R.string.schedules_title, "schedule Wi-Fi Bluetooth location time automation") { startActivity(Intent(this, SchedulesActivity::class.java)) }

        // NFC/QR/barcode tools.
        add(R.string.nfc_writer_title, "write NFC tag") { openProtectedActivity(Intent(this, NfcWriterActivity::class.java)) }
        add(R.string.keys_codes_paired_tags_title, "paired NFC UID tags") { openProtectedActivity(Intent(this, ManagePairedTagsActivity::class.java)) }
        add(R.string.qr_generate_title, "QR generate manage") { openProtectedActivity(Intent(this, QrGenerateActivity::class.java)) }
        add(R.string.manage_barcodes_title, "barcode manage scan") { openProtectedActivity(Intent(this, ManageBarcodesActivity::class.java)) }

        // Display/Home/shortcuts.
        add(R.string.onb_optional_display_tiles_title, "Quick Settings tiles NFC QR barcode") {
            openToggleSection(ToggleOptionsActivity.SECTION_DISPLAY, ToggleOptionsActivity.DISPLAY_TARGET_TILES)
        }
        add(R.string.onb_optional_display_widgets_title, "widgets scanner timer schedule") {
            openToggleSection(ToggleOptionsActivity.SECTION_DISPLAY, ToggleOptionsActivity.DISPLAY_TARGET_WIDGETS)
        }
        add(R.string.pref_persistent_status_notification_title, "persistent status notification") {
            openToggleSection(ToggleOptionsActivity.SECTION_FEATURES)
        }
        // Permissions & reliability, down to the relevant section.
        add(R.string.permissions_accessibility_title, "accessibility Bedienungshilfe service Dienst blocking Blockierung permission Berechtigung") { openPermissionSection(PermissionsActivity.SECTION_CORE) }
        add(R.string.permissions_battery_title, "battery Akku optimization Optimierung unrestricted uneingeschränkt background Hintergrund") { openPermissionSection(PermissionsActivity.SECTION_BATTERY) }
        add(R.string.permissions_notifications_title, "notifications Benachrichtigungen permission Berechtigung") { openPermissionSection(PermissionsActivity.SECTION_NOTIFICATIONS) }
        add(R.string.permissions_notification_access_title, "notification Benachrichtigung access Zugriff blocked blockiert") { openPermissionSection(PermissionsActivity.SECTION_NOTIFICATIONS) }
        add(R.string.permissions_nfc_title, "NFC launch trigger") { openPermissionSection(PermissionsActivity.SECTION_TRIGGERS) }
        add(R.string.permissions_autostart_title, oemBackgroundSearchTerms) {
            openPermissionSection(PermissionsActivity.SECTION_BATTERY, PermissionsActivity.TARGET_AUTOSTART)
        }

        // Real full-text index for PreferenceScreens and individual Preference rows.
        // Titles/summaries come from the localized XML, so search automatically follows DE/EN copy changes.
        val androidNs = "http://schemas.android.com/apk/res/android"
        val topLevelScreensAlreadyIndexed = setOf(
            "screen_appearance",
            "screen_permissions_reliability",
            "screen_account",
            "screen_help_about",
        )
        val preferenceKeysAlreadyIndexed = setOf("pref_permissions")
        val parser = resources.getXml(R.xml.preferences_settings)
        val screenStack = mutableListOf<String?>()
        fun parserText(attribute: String): String {
            val resId = parser.getAttributeResourceValue(androidNs, attribute, 0)
            if (resId != 0) {
                return runCatching { getString(resId) }.getOrDefault("")
            }
            return parser.getAttributeValue(androidNs, attribute).orEmpty()
        }
        try {
            var event = parser.eventType
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (event) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        val simpleTag = parser.name.substringAfterLast('.')
                        val key = parser.getAttributeValue(androidNs, "key")
                        val title = parserText("title")
                        val summary = parserText("summary")
                        if (simpleTag == "PreferenceScreen") {
                            if (!key.isNullOrBlank() && key !in topLevelScreensAlreadyIndexed && title.isNotBlank()) {
                                val targetScreen = key
                                addText(title, "$summary $targetScreen") {
                                    showNestedSettingsScreen(targetScreen)
                                }
                            }
                            screenStack += key ?: screenStack.lastOrNull()
                        } else {
                            val targetScreen = screenStack.lastOrNull()
                            if (!key.isNullOrBlank() && key !in preferenceKeysAlreadyIndexed && !targetScreen.isNullOrBlank() && title.isNotBlank()) {
                                val targetKey = key
                                val parentScreen = targetScreen
                                addText(title, "$summary $targetKey") {
                                    showNestedSettingsScreen(parentScreen, targetKey)
                                }
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (parser.name.substringAfterLast('.') == "PreferenceScreen" && screenStack.isNotEmpty()) {
                            screenStack.removeAt(screenStack.lastIndex)
                        }
                    }
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }

        val adapter = object : ArrayAdapter<SettingsSearchItem>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            items,
        ) {
            private val allItems = items.toList()

            override fun getFilter(): android.widget.Filter = object : android.widget.Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val query = constraint?.toString()?.trim().orEmpty()
                    val terms = query.split(Regex("\\s+")).filter { it.isNotBlank() }
                    val matches = if (terms.isEmpty()) {
                        emptyList()
                    } else {
                        allItems.filter { item ->
                            terms.all { term -> item.searchableText.contains(term, ignoreCase = true) }
                        }.sortedWith(
                            compareByDescending<SettingsSearchItem> { it.title.startsWith(query, ignoreCase = true) }
                                .thenBy { it.title.length }
                        ).take(12)
                    }
                    return FilterResults().apply {
                        values = matches
                        count = matches.size
                    }
                }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    clear()
                    val filteredItems = (results?.values as? List<*>)
                        .orEmpty()
                        .filterIsInstance<SettingsSearchItem>()
                    addAll(filteredItems)
                    notifyDataSetChanged()
                }
            }
        }

        val content = layoutInflater.inflate(
            R.layout.dialog_settings_find,
            FrameLayout(this),
            false,
        )
        val input = content.findViewById<MaterialAutoCompleteTextView>(R.id.acSettingsFind).apply {
            threshold = 1
            setAdapter(adapter)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_find_title)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .create()
        input.setOnItemClickListener { parent, _, index, _ ->
            val result = (parent.getItemAtPosition(index) as? SettingsSearchItem)
                ?: adapter.getItem(index)
                ?: return@setOnItemClickListener
            input.dismissDropDown()
            dialog.dismiss()
            result.open()
        }
        dialog.setOnShowListener { input.requestFocus() }
        dialog.show()
        dialog.styleSwitchlyDialogButtons()
    }

    fun setToolbarTitle(title: String) {
        supportActionBar?.title = title
        toolbar.title = title
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        bottomNav = findViewById(R.id.bottomNav)
        rootScroll = findViewById(R.id.settingsRootScroll)
        container = findViewById(R.id.container)
    }

    private fun setupToolbar() {
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar, bottomNav = bottomNav)
        EdgeToEdgeUtils.applyBottomNavGestureInset(bottomNav)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))
    }

    private fun setupToolbarTitleSync() {
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                showRootSettings()
            } else {
                showNestedSettingsContainer()
            }
            val canGoBack = container.isVisible
            supportActionBar?.setDisplayHomeAsUpEnabled(canGoBack)
            toolbar.navigationIcon = if (canGoBack) {
                ContextCompat.getDrawable(this, R.drawable.keyboard_arrow_left_24)
            } else {
                null
            }
            updateTitleFromFragment()
        }
    }

    private fun setupBottomNav() {
        bottomNav.selectedItemId = R.id.nav_settings

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
                R.id.nav_rules -> {
                    RulesHubActivity.openWithAccessCheck(
                        source = this,
                        finishSourceAfterOpen = true
                    )
                }
                R.id.nav_activity -> {
                    ActivityTransitionCompat.switchWithoutAnimation(
                        activity = this,
                        intent = Intent(this, ActivityHubActivity::class.java),
                        finishCurrent = true,
                    )
                    true
                }
                R.id.nav_settings -> true
                else -> false
            }
        }

        bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.nav_settings) {
                resetSettingsToTop()
            }
        }
    }

    private fun setupRootCards() {
        findViewById<View>(R.id.cardSettingsBlockingFeatures).setOnClickListener {
            openProtectedSettingsSection {
                startActivity(Intent(this, BlockingFeaturesActivity::class.java))
            }
        }
        findViewById<View>(R.id.cardSettingsAppearance).setOnClickListener {
            showNestedSettingsScreen("screen_appearance")
        }
        findViewById<View>(R.id.cardSettingsIgnoredApps).setOnClickListener {
            startActivity(IgnoredUsageAppsActivity.intent(this))
        }
        findViewById<View>(R.id.cardSettingsDisplayShortcuts).setOnClickListener {
            openProtectedSettingsSection {
                startActivity(Intent(this, ToggleOptionsActivity::class.java).apply {
                    putExtra(ToggleOptionsActivity.EXTRA_VIEW_SECTION, ToggleOptionsActivity.SECTION_DISPLAY)
                })
            }
        }
        findViewById<View>(R.id.cardSettingsPermissions).setOnClickListener {
            openProtectedSettingsSection {
                startActivity(Intent(this, PermissionsActivity::class.java))
            }
        }
        findViewById<View>(R.id.cardSettingsInfo).setOnClickListener {
            startActivity(Intent(this, at.saltyy.switchly.feature.about.InfoActivity::class.java))
        }
    }

    fun isRestrictedAccessActive(): Boolean {
        return SwitchlyAppAccessGuard.isLocked(this)
    }

    private fun applyRestrictedAccessState() {
        if (!::toolbar.isInitialized) {
            return
        }

        val restricted = isRestrictedAccessActive()
        val controlSettingsRestricted = SwitchlyAppAccessGuard.isControlSettingsLocked(this)

        toolbar.subtitle = null
        supportActionBar?.subtitle = null

        val recoveryControlCards = listOf<Int>()
        recoveryControlCards.forEach { cardId ->
            applyRestrictedCardState(findViewById(cardId), controlSettingsRestricted)
        }

        val restrictedCards = listOf(
            R.id.cardSettingsBlockingFeatures,
            R.id.cardSettingsDisplayShortcuts,
            R.id.cardSettingsPermissions
        )
        restrictedCards.forEach { cardId ->
            applyRestrictedCardState(findViewById(cardId), restricted)
        }
    }

    private fun applyRestrictedCardState(card: View, restricted: Boolean) {
        card.isEnabled = !restricted
        card.isClickable = !restricted
        card.isFocusable = !restricted
        card.alpha = if (restricted) LockedUi.cardAlpha(this) else 1f
    }

    private fun openControlSettingsSection(open: () -> Unit) {
        if (SwitchlyAppAccessGuard.isControlSettingsLocked(this)) {
            applyRestrictedAccessState()
            return
        }

        open()
    }

    private fun openProtectedSettingsSection(open: () -> Unit) {
        if (SwitchlyAppAccessGuard.isLocked(this)) {
            applyRestrictedAccessState()
            return
        }

        open()
    }

    private fun restoreScreenState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            // Deep link (e.g. Account's Backup & data cards): open a nested
            // settings screen straight away instead of the root list.
            val nested = intent.getStringExtra(EXTRA_NESTED_SCREEN)
            if (!nested.isNullOrBlank()) {
                showNestedSettingsScreen(
                    nested,
                    intent.getStringExtra(EXTRA_NESTED_FOCUS)
                )
                updateTitleFromFragment()
                return
            }
            showRootSettings()
            return
        }
        if (supportFragmentManager.findFragmentById(R.id.container) != null) {
            showNestedSettingsContainer()
        } else {
            showRootSettings()
        }
    }

    private fun showNestedSettingsScreen(screenKey: String, focusKey: String? = null) {
        showNestedSettingsContainer()
        val fragment = SettingsFragment().apply {
            arguments = Bundle().apply {
                putString("androidx.preference.PreferenceFragmentCompat.PREFERENCE_ROOT", screenKey)
                if (!focusKey.isNullOrBlank()) {
                    putString(SettingsFragment.ARG_FOCUS_KEY, focusKey)
                }
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .addToBackStack(screenKey)
            .commit()
    }

    private fun showRootSettings() {
        rootScroll.visibility = View.VISIBLE
        container.visibility = View.GONE
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        toolbar.navigationIcon = null
        setToolbarTitle(getString(R.string.settings))
    }

    private fun showNestedSettingsContainer() {
        rootScroll.visibility = View.GONE
        container.visibility = View.VISIBLE
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.keyboard_arrow_left_24)
    }

    private fun updateTitleFromFragment() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.container)
        val title = if (container.isVisible && currentFragment is SettingsFragment) {
            currentFragment.currentScreenTitle()
        } else {
            getString(R.string.settings)
        }
        setToolbarTitle(title)
    }

    private fun showEmergencyQuickSheet() {
        if (!EmergencyBypassStore.isFeatureEnabled(this)) {
            AlertDialog.Builder(this)
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
                    AppLogStore.append(this, "Emergency", "Emergency mode paused from Settings")
                    SwitchModeStore.clearTemporary(this)
                    Toast.makeText(this, getString(R.string.emergency_paused_toast), Toast.LENGTH_SHORT).show()
                    BlockingRuntime.ensureRunning(this)
                    applyRestrictedAccessState()
                }
            }
            addAction(getString(R.string.emergency_action_end)) {
                AppLogStore.append(this, "Emergency", "Emergency mode ended from Settings")
                EmergencyBypassStore.cancel(this)
                SwitchModeStore.clearTemporary(this)
                Toast.makeText(this, getString(R.string.emergency_ended_toast), Toast.LENGTH_SHORT).show()
                BlockingRuntime.ensureRunning(this)
                applyRestrictedAccessState()
            }
        } else if (paused) {
            addAction(getString(R.string.emergency_action_resume)) {
                if (EmergencyBypassStore.resume(this)) {
                    val remainingMinutes = EmergencyBypassStore.minutesRemaining(this).coerceAtLeast(1)
                    AppLogStore.append(this, "Emergency", "Emergency mode resumed from Settings with ${remainingMinutes}m remaining")
                    SwitchModeStore.setTemporarilyDisabled(this, remainingMinutes * 60_000L)
                    Toast.makeText(this, getString(R.string.emergency_resumed_toast), Toast.LENGTH_SHORT).show()
                    BlockingRuntime.ensureRunning(this)
                    applyRestrictedAccessState()
                }
            }
            addAction(getString(R.string.emergency_action_end)) {
                AppLogStore.append(this, "Emergency", "Emergency mode ended from Settings")
                EmergencyBypassStore.cancel(this)
                SwitchModeStore.clearTemporary(this)
                Toast.makeText(this, getString(R.string.emergency_ended_toast), Toast.LENGTH_SHORT).show()
                BlockingRuntime.ensureRunning(this)
                applyRestrictedAccessState()
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
            showSwitchlyOptionDialog(
                title = title,
                options = labels.map { label ->
                    SwitchlyDialogOption(
                        title = label,
                        destructive = label == getString(R.string.emergency_action_end)
                    )
                }
            ) { which ->
                runCatching { actions[which].invoke() }
            }
        } else {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(R.string.emergency_used_today)
                .setNegativeButton(R.string.cancel, null)
                .showAccented()
        }
    }

    companion object {
        const val EXTRA_NESTED_SCREEN = "extra_nested_screen"
        const val EXTRA_NESTED_FOCUS = "extra_nested_focus"

        fun openWithAccessCheck(
            source: AppCompatActivity,
            finishSourceAfterOpen: Boolean = false
        ): Boolean {
            if (!SwitchlyAppAccessGuard.isLocked(source)) {
                ActivityTransitionCompat.switchWithoutAnimation(
                    activity = source,
                    intent = Intent(source, SettingsActivity::class.java),
                    finishCurrent = finishSourceAfterOpen,
                )
                return true
            }

            AlertDialog.Builder(source)
                .setTitle(R.string.switchly_settings_locked_title)
                .setMessage(R.string.settings_restricted_open_message)
                .setPositiveButton(R.string.settings_open_restricted) { _, _ ->
                    ActivityTransitionCompat.switchWithoutAnimation(
                        activity = source,
                        intent = Intent(source, SettingsActivity::class.java),
                        finishCurrent = finishSourceAfterOpen,
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .showAccented()
            return false
        }
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
            .setPositiveButton(R.string.ok) { _, _ ->
                if (EmergencyBypassStore.enableIfAllowed(this, 15)) {
                    AppLogStore.append(this, "Emergency", "Emergency mode started from Settings for 15m")
                    SwitchModeStore.setTemporarilyDisabled(this, 15 * 60_000L)
                    Toast.makeText(this, getString(R.string.emergency_enabled_toast, 15), Toast.LENGTH_SHORT).show()
                    BlockingRuntime.ensureRunning(this)
                    applyRestrictedAccessState()
                } else {
                    Toast.makeText(this, getString(R.string.emergency_used_today), Toast.LENGTH_SHORT).show()
                }
            }
            .showAccented()
    }

    private fun resetSettingsToTop() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.executePendingTransactions()
        showRootSettings()
        rootScroll.post { runCatching { rootScroll.scrollTo(0, 0) } }
    }
}
