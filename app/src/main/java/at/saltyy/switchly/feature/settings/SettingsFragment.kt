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

import android.app.ActivityManager
import android.content.res.ColorStateList
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import at.saltyy.switchly.R
import at.saltyy.switchly.blocking.BlockingRuntime
import at.saltyy.switchly.data.prefs.ActivityHistoryLogStore
import at.saltyy.switchly.data.prefs.AppLogStore
import at.saltyy.switchly.data.prefs.AutomationModeStore
import at.saltyy.switchly.data.prefs.BlockingToggleKeys
import at.saltyy.switchly.data.prefs.EmergencyBypassStore
import at.saltyy.switchly.data.prefs.ProfileStore
import at.saltyy.switchly.data.prefs.SchedulePlanner
import at.saltyy.switchly.data.prefs.SwitchModeStore
import at.saltyy.switchly.data.sync.BackupCategory
import at.saltyy.switchly.data.sync.BackupCategoryFilter
import at.saltyy.switchly.data.sync.BackupSelection
import at.saltyy.switchly.data.sync.BackupSelectionStore
import at.saltyy.switchly.data.statistics.StatsPersistence
import at.saltyy.switchly.data.sync.FileBackupRuntime
import at.saltyy.switchly.feature.about.AppInfoActivity
import at.saltyy.switchly.feature.about.DeveloperInfoActivity
import at.saltyy.switchly.feature.about.DeviceInfoActivity
import at.saltyy.switchly.feature.about.OtherSwitchlyProductsActivity
import at.saltyy.switchly.feature.about.PrivacyReportActivity
import at.saltyy.switchly.feature.about.WhatsNewActivity
import at.saltyy.switchly.feature.faq.FaqActivity
import at.saltyy.switchly.feature.inbox.BlockedInboxActivity
import at.saltyy.switchly.feature.profiles.ManageProfilesActivity
import at.saltyy.switchly.feature.schedule.SchedulesActivity
import at.saltyy.switchly.feature.settings.ManagePairedTagsActivity
import at.saltyy.switchly.feature.support.SupportLogActivity
import at.saltyy.switchly.nfc.NfcWriterActivity
import at.saltyy.switchly.theme.AccentColor
import at.saltyy.switchly.theme.CustomAccentApplier
import at.saltyy.switchly.ui.MainActivity
import at.saltyy.switchly.ui.showWarnPill
import at.saltyy.switchly.ui.dialog.showDestructiveAccented
import at.saltyy.switchly.ui.dialog.showAccented
import at.saltyy.switchly.ui.dialog.SwitchlyDialogOption
import at.saltyy.switchly.ui.dialog.showSwitchlyOptionDialog
import at.saltyy.switchly.ui.dialog.showSwitchlyMultiChoiceDialog
import at.saltyy.switchly.ui.dialog.styleSwitchlyDialogButtons
import at.saltyy.switchly.ui.dialog.EmergencyPinDialog
import at.saltyy.switchly.util.BatteryOptimizationRequest
import at.saltyy.switchly.util.EditingLockGuard
import at.saltyy.switchly.util.LocaleHelper
import at.saltyy.switchly.util.TimeFormatPrefs
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.json.JSONArray
import com.google.android.material.radiobutton.MaterialRadioButton
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : PreferenceFragmentCompat() {

    private fun openToggleOptions(section: String? = null) {
        val intent = Intent(requireContext(), ToggleOptionsActivity::class.java)
        if (!section.isNullOrBlank()) {
            intent.putExtra(ToggleOptionsActivity.EXTRA_SCROLL_TO_SECTION, section)
        }
        startActivity(intent)
    }

    fun currentScreenTitle(): String {
        val t = preferenceScreen?.title?.toString()
        return if (!t.isNullOrBlank()) {
            t
        } else {
            getString(R.string.settings)
        }
    }

    fun scrollToTop() {
        listView?.post {
            runCatching {
                listView?.smoothScrollToPosition(0)
            }
        }
    }

    private val categoryTitles = mutableSetOf<String>()
    private var devVisible: Boolean = false
    private fun refreshBlockedInboxPreferenceState() {
        findPreference<Preference>("pref_blocked_inbox")?.isVisible = true
    }

    private var focusApplied: Boolean = false
    private var nextChangedReceiver: BroadcastReceiver? = null
    private var lastNestedNavKey: String? = null
    private var lastNestedNavAtMs: Long = 0L
    // Shared with the host activity (registered before STARTED there).
    private val backupFlows: BackupFlowActions
        get() = (requireActivity() as SettingsActivity).backupFlows

    private fun isRestrictedSettingsAccess(): Boolean {
        return (activity as? SettingsActivity)?.isRestrictedAccessActive() == true
    }

    // Backup/restore/reset stay tappable while locked: denied taps warn via
    // pill in the click listeners instead of silently doing nothing.
    private fun denyRestrictedAccountData(): Boolean {
        if (isRestrictedSettingsAccess()) {
            requireView().showWarnPill(R.string.settings_restricted_action_unavailable)
            return true
        }
        return false
    }



    override fun onAttach(context: Context) {
        super.onAttach(context)
        backupFlows.onLibraryChanged = { updateCloudPrefVisibility() }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // If we are navigating into a nested PreferenceScreen (Help/Account/...), we pass the target root via fragment arguments.
        val effectiveRoot = rootKey ?: arguments?.getString(ARG_PREFERENCE_ROOT)
        setPreferencesFromResource(R.xml.preferences_settings, effectiveRoot)

        val ctx = requireContext()
        // App-scoped prefs used by SettingsFragment
        val appPrefs = ctx.getSharedPreferences(PREFS, 0)
        devVisible = appPrefs.getBoolean(KEY_DEV_UNLOCKED, false)

        tintCategories()
        ensureDeveloperInfoIconAccent()
        // Extra pass to prevent occasional fallback to default accent in Customize.
        requireActivity().window?.decorView?.post {
            runCatching {
                CustomAccentApplier.applyIfNeeded(requireActivity())
                tintCategories()
                ensureDeveloperInfoIconAccent()
                tintCategoryViewsInList()
            }
        }

        // Hidden master toggle (dev-only)
        findPreference<SwitchPreferenceCompat>("pref_switch_mode")?.apply {
            isVisible = devVisible
            isChecked = SwitchModeStore.isEnabled(ctx)

            setOnPreferenceClickListener {
                val enabled = SwitchModeStore.isEnabled(ctx)
                val requireNfc = SwitchModeStore.isNfcRequiredForDisable(ctx)
                val locked = enabled && requireNfc
                if (locked) {
                    requireView().showWarnPill(R.string.toast_disable_requires_nfc)
                }
                false
            }

            setOnPreferenceChangeListener { _, new ->
                val target = new as Boolean
                val currentlyEnabled = SwitchModeStore.isEnabled(ctx)
                val requireNfc = SwitchModeStore.isNfcRequiredForDisable(ctx)

                val locked = currentlyEnabled && requireNfc && !target
                if (locked) {
                    requireView().showWarnPill(R.string.toast_disable_requires_nfc)
                    false
                } else {
                    SwitchModeStore.setEnabled(ctx, target)
                    AppLogStore.append(
                        ctx,
                        "Profiles",
                        "Manual toggle action=${if (target) "enable" else "disable"} profile=${ProfileStore.getCurrent(ctx)}"
                    )
                    refreshLockUi()
                    true
                }
            }
        }

        // Master toggle (visible)
        findPreference<SwitchPreferenceCompat>("pref_switchly_enabled")?.apply {
            isChecked = SwitchModeStore.isEnabled(ctx)

            setOnPreferenceClickListener {
                val enabled = SwitchModeStore.isEnabled(ctx)
                val requireNfc = SwitchModeStore.isNfcRequiredForDisable(ctx)
                val locked = enabled && requireNfc
                if (locked) {
                    requireView().showWarnPill(R.string.toast_disable_requires_nfc)
                }
                false
            }

            setOnPreferenceChangeListener { _, new ->
                val target = new as Boolean
                val currentlyEnabled = SwitchModeStore.isEnabled(ctx)
                val requireNfc = SwitchModeStore.isNfcRequiredForDisable(ctx)

                val locked = currentlyEnabled && requireNfc && !target
                if (locked) {
                    requireView().showWarnPill(R.string.toast_disable_requires_nfc)
                    false
                } else {
                    SwitchModeStore.setEnabled(ctx, target)
                    AppLogStore.append(
                        ctx,
                        "Profiles",
                        "Manual toggle action=${if (target) "enable" else "disable"} profile=${ProfileStore.getCurrent(ctx)}"
                    )
                    refreshLockUi()
                    true
                }
            }
        }

        // Language
        findPreference<Preference>("pref_language")?.apply {
            updateLanguageSummary(this)
            setOnPreferenceClickListener {
                showLanguageDialog()
                true
            }
        }

        // Appearance (Display mode + Theme color)
        findPreference<Preference>("pref_theme_mode")?.apply {
            updateThemeModeSummary(this)
            setOnPreferenceClickListener {
                showThemeModeDialog()
                true
            }
        }

        findPreference<Preference>("pref_time_format")?.apply {
            updateTimeFormatSummary(this)
            setOnPreferenceClickListener {
                showTimeFormatDialog()
                true
            }
        }

        findPreference<Preference>("pref_theme_color")?.apply {
            updateThemeColorSummary(this)
            setOnPreferenceClickListener {
                showThemeColorDialog()
                true
            }
        }

        // Manage profiles
        findPreference<Preference>("pref_manage_profiles")?.apply {
            isVisible = true
            setOnPreferenceClickListener {
                // Manage Profiles remains available while protection is active.
                // The destination itself allows review/create/duplicate/rename/strictness-only app edits and keeps switching/deletion locked.
                startActivity(Intent(requireContext(), ManageProfilesActivity::class.java))
                true
            }
        }

        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val pairedUiEnabled = defaultPrefs.getBoolean(BlockingToggleKeys.KEY_ENABLE_PAIRED_UIDS, false)

        // NFC tag writer (should always be available; pairing is just one optional action)
        findPreference<Preference>("pref_write_tag")?.apply {
            isVisible = true
            setOnPreferenceClickListener {
                if (SwitchModeStore.isEnabled(requireContext()) &&
                    !AutomationModeStore.isNfcTagWritingAllowedWhileEnabled(requireContext())
                ) {
                    EditingLockGuard.showLockedDialog(requireContext(), R.string.edit_locked_write_nfc_tags)
                } else {
                    startActivity(Intent(requireContext(), NfcWriterActivity::class.java))
                }
                true
            }
        }

        // Manage paired NFC tags (UID list) (only shown when feature is enabled)
        findPreference<Preference>("pref_manage_paired_tags")?.apply {
            isVisible = pairedUiEnabled
            setOnPreferenceClickListener {
                val ctx = requireContext()
                val locked = EditingLockGuard.isLocked(ctx)
                AppLogStore.append(ctx, "NFC", "Manage Paired Tags clicked from Settings locked=$locked")
                if (locked) {
                    EditingLockGuard.showLockedDialog(ctx, R.string.edit_locked_manage_paired_tags)
                } else {
                    runCatching {
                        startActivity(Intent(ctx, ManagePairedTagsActivity::class.java))
                    }.onFailure { error ->
                        AppLogStore.append(ctx, "NFC", "Failed to open Manage Paired Tags from Settings", error)
                        requireView().showWarnPill(R.string.error_open_manage_paired_tags)
                    }
                }
                true
            }
        }

        // Emergency unlock
        findPreference<Preference>("pref_emergency_unlock")?.setOnPreferenceClickListener {
            showEmergencyUnlockWithPin()
            true
        }

        // Permissions overview
        findPreference<Preference>("pref_permissions")?.setOnPreferenceClickListener {
            val enabled = SwitchModeStore.isEnabled(requireContext())
            val requireNfc = SwitchModeStore.isNfcRequiredForDisable(requireContext())
            val locked = enabled && requireNfc
            if (locked) {
                requireView().showWarnPill(R.string.toast_disable_requires_nfc)
                return@setOnPreferenceClickListener true
            }

            startActivity(Intent(requireContext(), PermissionsActivity::class.java))
            true
        }

        // Toggle controls: flattened entry point (single page)
        findPreference<Preference>("pref_toggle_options")?.setOnPreferenceClickListener {
            openToggleOptions()
            true
        }
        findPreference<Preference>("pref_toggle_options_blocking")?.setOnPreferenceClickListener {
            openToggleOptions()
            true
        }

        // Blocked notifications inbox
        findPreference<Preference>("pref_blocked_inbox")?.setOnPreferenceClickListener {
            val ctx = requireContext()
            if (SwitchModeStore.isEnabled(ctx) && !EmergencyBypassStore.isActive(ctx)) {
                EditingLockGuard.showLockedDialog(ctx, R.string.edit_locked_manage_blocked_notifications)
            } else {
                startActivity(Intent(ctx, BlockedInboxActivity::class.java))
            }
            true
        }
        refreshBlockedInboxPreferenceState()

        // Open schedules (Customize -> Schedules)
        findPreference<Preference>("pref_open_schedules")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), SchedulesActivity::class.java))
            true
        }

        // Help -> Other help
        findPreference<Preference>("pref_other_help_battery")?.setOnPreferenceClickListener {
            val ctx = requireContext()
            val directOpened = !BatteryOptimizationRequest.isAlreadyAllowed(ctx) && runCatching {
                startActivity(BatteryOptimizationRequest.intent(ctx))
                true
            }.getOrDefault(false)

            if (!directOpened) {
                runCatching {
                    startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }.onFailure {
                    val uri = Uri.fromParts("package", ctx.packageName, null)
                    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                }
            }
            true
        }

        findPreference<Preference>("pref_other_help_contact")?.setOnPreferenceClickListener {
            // Dedicated diagnostic logs screen
            startActivity(Intent(requireContext(), SupportLogActivity::class.java))
            true
        }

        // About sub pages
        findPreference<Preference>("pref_about_app_info")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AppInfoActivity::class.java))
            true
        }
        findPreference<Preference>("pref_about_device_info")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), DeviceInfoActivity::class.java))
            true
        }
        findPreference<Preference>("pref_about_developer_info")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), DeveloperInfoActivity::class.java))
            true
        }
        findPreference<Preference>("pref_about_other_switchly_products")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), OtherSwitchlyProductsActivity::class.java))
            true
        }

        // What's new
        findPreference<Preference>("pref_changelog")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), WhatsNewActivity::class.java))
            true
        }

        // FAQ
        findPreference<Preference>("pref_faq")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), FaqActivity::class.java))
            true
        }

        // Emergency unlock PIN (Account)
        findPreference<Preference>("pref_change_emergency_pin")?.setOnPreferenceClickListener {
            showChangeEmergencyPinFlow()
            true
        }

        // Backup as standalone prefs
        findPreference<Preference>("pref_file_backup")?.setOnPreferenceClickListener {
            backupFlows.fileBackup()
            true
        }

        findPreference<Preference>("pref_file_restore")?.setOnPreferenceClickListener {
            if (denyRestrictedAccountData()) {
                return@setOnPreferenceClickListener true
            }
            backupFlows.fileRestore()
            true
        }

        // Backup & restore screen itself: warn instead of dead navigation while locked.
        findPreference<Preference>("screen_backup_restore")?.setOnPreferenceClickListener {
            if (denyRestrictedAccountData()) {
                true
            } else {
                false
            }
        }

        findPreference<Preference>("pref_privacy_report")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), PrivacyReportActivity::class.java))
            true
        }

        // Local in-app reset (clear ALL app data)
        findPreference<Preference>("pref_reset_app_data")?.setOnPreferenceClickListener {
            if (denyRestrictedAccountData()) {
                return@setOnPreferenceClickListener true
            }
            backupFlows.confirmReset()
            true
        }

        // Tutorial
        findPreference<Preference>("pref_tutorial")
            ?.setOnPreferenceClickListener {
                startActivity(
                    Intent(requireContext(), at.saltyy.switchly.feature.onboarding.OnboardingActivity::class.java)
                        .putExtra(at.saltyy.switchly.feature.onboarding.OnboardingActivity.EXTRA_FORCE, true)
                )
                true
            }

        findPreference<Preference>("pref_about_store")?.setOnPreferenceClickListener {
            val storeUrl = getString(R.string.about_store_url)
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, storeUrl.toUri()))
            }.onFailure {
                requireView().showWarnPill(R.string.store_open_failed)
            }
            true
        }

        // Initial UI state
        updateCloudPrefVisibility()
        refreshEmergencyPref()
        refreshLockUi()

        // Live updates from SwitchModeStore
        SwitchModeStore.ensureInit(ctx)
        lifecycleScope.launch {
            SwitchModeStore.enabledFlow.collect {
                val enabledNow = SwitchModeStore.isEnabled(ctx)
                findPreference<SwitchPreferenceCompat>("pref_switch_mode")?.isChecked = enabledNow
                findPreference<SwitchPreferenceCompat>("pref_switchly_enabled")?.isChecked = enabledNow
                refreshLockUi()
            }
        }
    }

    // Navigate nested PreferenceScreens (Help/Account/...). Some setups do not automatically open nested screens, so we handle it explicitly.
    private fun openNestedPreferenceScreen(screenKey: String): Boolean {
        if (screenKey.isBlank()) {
            return false
        }

        // Some AndroidX/device combinations can dispatch both callbacks for one tap.
        // Debounce identical navigation to avoid double back-stack entries ("back needs 2 taps").
        val now = SystemClock.uptimeMillis()
        if (lastNestedNavKey == screenKey && (now - lastNestedNavAtMs) < 650L) {
            return true
        }
        lastNestedNavKey = screenKey
        lastNestedNavAtMs = now

        val currentRoot = arguments?.getString(ARG_PREFERENCE_ROOT)
        if (currentRoot == screenKey) {
            return true
        }

        val fragment = SettingsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PREFERENCE_ROOT, screenKey)
            }
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .addToBackStack(screenKey)
            .commit()

        return true
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference is PreferenceScreen && preference.key?.startsWith("screen_") == true) {
            return openNestedPreferenceScreen(preference.key.orEmpty())
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
        // Some devices/androidx versions won't trigger onPreferenceTreeClick for PreferenceScreen, but will call onNavigateToScreen. Handle both to be safe.
        if (preferenceScreen.key?.startsWith("screen_") == true) {
            openNestedPreferenceScreen(preferenceScreen.key.orEmpty())
        } else {
            super.onNavigateToScreen(preferenceScreen)
        }
    }

    // File backup stays available offline; cloud backup preferences are gone.
    private fun updateCloudPrefVisibility() {
        findPreference<PreferenceScreen>("screen_backup")?.isVisible = true
        findPreference<Preference>("pref_file_backup")?.isVisible = true
        findPreference<Preference>("pref_file_restore")?.isVisible = true
    }

    private fun tintCategoryViewsInList() {
        val list = listView ?: return
        if (categoryTitles.isEmpty()) {
            return
        }
        val accent = getCurrentAccentColor(requireContext())

        fun tintInView(v: View) {
            if (v is TextView) {
                val text = v.text?.toString() ?: return
                if (categoryTitles.contains(text)) v.setTextColor(accent)
            } else if (v is ImageView) {
                val d = v.drawable ?: return
                val wrapped = DrawableCompat.wrap(d).mutate()
                DrawableCompat.setTint(wrapped, accent)
                v.setImageDrawable(wrapped)
            } else if (v is ViewGroup) {
                for (i in 0 until v.childCount) tintInView(v.getChildAt(i))
            }
        }

        for (i in 0 until list.childCount) tintInView(list.getChildAt(i))

        list.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                if (child != null) tintInView(child)
            }

            override fun onChildViewRemoved(parent: View?, child: View?) {}
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list = listView ?: return
        // The nested SettingsActivity container already provides the same horizontal padding as the root cards.
        // Do not add a second 16dp inset here, otherwise nested pages such as Appearance and Help/About look zoomed out/narrower than the main Settings screen.
        val padBottom = list.paddingBottom
        list.setPadding(0, 0, 0, padBottom)
        list.clipToPadding = false
        tintCategoryViewsInList()
        CustomAccentApplier.applyIfNeeded(requireActivity())
    }

    // Next schedule indicator
    private fun updateNextScheduleIndicator() {
        val ctx = requireContext()
        val pref = findPreference<Preference>("pref_schedules_next") ?: return

        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        val show = sp.getBoolean(ToggleOptionsActivity.KEY_SHOW_NEXT_SCHEDULE, false)
        pref.isVisible = show
        if (!show) {
            return
        }

        if (!AutomationModeStore.isScheduleAllowed(ctx)) {
            pref.summary = getString(R.string.schedules_next_inactive_control_mode)
            return
        }

        val nextMillis = SchedulePlanner.getNextBoundaryMillis(ctx)
        if (nextMillis <= 0L) {
            pref.summary = getString(R.string.schedules_next_none)
        } else {
            val text = formatLocalScheduleBoundaryTime(ctx, nextMillis)
            pref.summary = getString(R.string.schedules_next_at, text)
        }
    }

    private fun formatLocalScheduleBoundaryTime(context: Context, timeMillis: Long): String {
        val cal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return TimeFormatPrefs.formatMinutesOfDay(context, minutesOfDay)
    }

    private fun pulseFocusedPreference(focusKey: String) {
        val title = findPreference<Preference>(focusKey)?.title?.toString()?.takeIf { it.isNotBlank() } ?: return
        val recycler = listView ?: return
        recycler.postDelayed({
            for (index in 0 until recycler.childCount) {
                val row = recycler.getChildAt(index)
                val matches = ArrayList<View>()
                row.findViewsWithText(matches, title, View.FIND_VIEWS_WITH_TEXT)
                if (matches.isNotEmpty()) {
                    val originalAlpha = row.alpha
                    row.animate()
                        .alpha(minOf(originalAlpha, 0.58f))
                        .setDuration(120L)
                        .withEndAction { row.animate().alpha(originalAlpha).setDuration(240L).start() }
                        .start()
                    break
                }
            }
        }, 180L)
    }

    override fun onResume() {
        super.onResume()
        if (!focusApplied) {
            val focusKey = arguments?.getString(ARG_FOCUS_KEY)
            if (!focusKey.isNullOrBlank()) {
                listView?.post {
                    runCatching { scrollToPreference(focusKey) }
                    pulseFocusedPreference(focusKey)
                }
            }
            focusApplied = true
        }
        (activity as? SettingsActivity)?.setToolbarTitle(currentScreenTitle())
        refreshLockUi()
        refreshEmergencyPref()
        updateTimeFormatSummary(findPreference("pref_time_format"))
        updateNextScheduleIndicator()
        updateCloudPrefVisibility()
        refreshBlockedInboxPreferenceState()
        CustomAccentApplier.applyIfNeeded(requireActivity())
        tintCategories()
        ensureDeveloperInfoIconAccent()

        val hasNextSchedulePref = findPreference<Preference>("pref_schedules_next") != null
        if (hasNextSchedulePref && nextChangedReceiver == null) {
            nextChangedReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == SchedulePlanner.ACTION_NEXT_CHANGED) {
                        updateNextScheduleIndicator()
                    }
                }
            }

            val filter = IntentFilter(SchedulePlanner.ACTION_NEXT_CHANGED)
            ContextCompat.registerReceiver(
                requireContext(),
                nextChangedReceiver!!,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onPause() {
        super.onPause()
        nextChangedReceiver?.let { runCatching { requireContext().unregisterReceiver(it) } }
        nextChangedReceiver = null
    }

    override fun onDestroyView() {
        nextChangedReceiver?.let { runCatching { requireContext().unregisterReceiver(it) } }
        nextChangedReceiver = null

        super.onDestroyView()
    }

    // Language
    private fun updateLanguageSummary(pref: Preference?) {
        pref ?: return
        val ctx = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val current = prefs.getString("pref_language", "system") ?: "system"

        val entries = resources.getStringArray(R.array.pref_language_entries)
        val values = resources.getStringArray(R.array.pref_language_values)

        val label = values.indexOf(current).let { idx ->
            if (idx in entries.indices) entries[idx] else entries.firstOrNull()
        } ?: ""

        pref.summary = label
    }

    // Appearance summaries
    private fun updateThemeModeSummary(pref: Preference?) {
        pref ?: return
        val ctx = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val current = prefs.getString("pref_theme_mode", "system") ?: "system"

        val label = when (current) {
            "light" -> getString(R.string.pref_theme_mode_light)
            "dark" -> getString(R.string.pref_theme_mode_dark)
            else -> getString(R.string.pref_theme_mode_system)
        }
        pref.summary = label
    }

    private fun updateTimeFormatSummary(pref: Preference?) {
        pref ?: return
        pref.summary = when (TimeFormatPrefs.getMode(requireContext())) {
            "12h" -> getString(R.string.pref_time_format_12h)
            "24h" -> getString(R.string.pref_time_format_24h)
            else -> getString(R.string.pref_time_format_system)
        }
    }

    private fun updateThemeColorSummary(pref: Preference?) {
        pref ?: return
        val ctx = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val current = prefs.getString("pref_accent", "default") ?: "default"

        if (current == "custom") {
            val hex = prefs.getString("pref_accent_custom", "").orEmpty()
            pref.summary = if (hex.isNotBlank()) {
                getString(R.string.pref_theme_color_custom_fmt, hex)
            } else {
                getString(R.string.pref_accent_custom_title)
            }
            return
        }

        val entries = resources.getStringArray(R.array.pref_accent_entries)
        val values = resources.getStringArray(R.array.pref_accent_values)
        val label = values.indexOf(current).let { i -> if (i in entries.indices) entries[i] else entries.firstOrNull() }
            ?: ""
        pref.summary = label
    }

    private fun showLanguageDialog() {
        val ctx = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val pref = findPreference<Preference>("pref_language")
        val current = prefs.getString("pref_language", "system") ?: "system"

        val entries = resources.getStringArray(R.array.pref_language_entries)
        val values = resources.getStringArray(R.array.pref_language_values)

        val checked = values.indexOf(current).let { idx -> if (idx >= 0) idx else 0 }

        val summaries = arrayOf(
            getString(R.string.pref_language_system_summary),
            getString(R.string.pref_language_en_summary),
            getString(R.string.pref_language_de_summary)
        )

        showSingleSelectCheckboxDialog(
            title = getString(R.string.pref_language_title),
            entries = entries,
            checkedIndex = checked,
            summaries = summaries,
            iconDrawables = arrayOf<Drawable?>(
                badgeDrawable("AUTO"),
                badgeDrawable("EN"),
                badgeDrawable("DE")
            ),
        ) { which, dialog ->
            val selected = values[which]
            prefs.edit { putString("pref_language", selected) }
            LocaleHelper.setLanguage(requireActivity().application, selected)
            updateLanguageSummary(pref)
            restartAppTask()
            dialog.dismiss()
        }
    }

    // Theme Dialog (Mode + Color)
    private fun showThemeModeDialog() {
        val ctx = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val current = prefs.getString("pref_theme_mode", "system") ?: "system"

        val entries = arrayOf(
            getString(R.string.pref_theme_mode_system),
            getString(R.string.pref_theme_mode_light),
            getString(R.string.pref_theme_mode_dark)
        )
        val values = arrayOf("system", "light", "dark")
        val checked = values.indexOf(current).coerceAtLeast(0)

        val summaries = arrayOf(
            getString(R.string.pref_theme_mode_system_summary),
            getString(R.string.pref_theme_mode_light_summary),
            getString(R.string.pref_theme_mode_dark_summary)
        )

        showSingleSelectCheckboxDialog(
            title = getString(R.string.pref_theme_mode_title),
            entries = entries,
            checkedIndex = checked,
            summaries = summaries,
            iconRes = arrayOf<Int?>(
                R.drawable.tune_24,
                R.drawable.light_mode_24,
                R.drawable.dark_mode_24
            ),
        ) { which, dialog ->
            val selected = values[which]
            prefs.edit { putString("pref_theme_mode", selected) }
            updateThemeModeSummary(findPreference("pref_theme_mode"))

            when (selected) {
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }

            dialog.dismiss()
        }
    }

    private fun showTimeFormatDialog() {
        val ctx = requireContext()
        val current = TimeFormatPrefs.getMode(ctx)
        val entries = arrayOf(
            getString(R.string.pref_time_format_system),
            getString(R.string.pref_time_format_24h),
            getString(R.string.pref_time_format_12h)
        )
        val values = arrayOf("system", "24h", "12h")
        val checked = values.indexOf(current).coerceAtLeast(0)

        val summaries = arrayOf(
            getString(R.string.pref_time_format_system_summary),
            getString(R.string.pref_time_format_24h_summary),
            getString(R.string.pref_time_format_12h_summary)
        )

        showSingleSelectCheckboxDialog(
            title = getString(R.string.pref_time_format_title),
            entries = entries,
            checkedIndex = checked,
            summaries = summaries,
            iconDrawables = arrayOf<Drawable?>(
                badgeDrawable("AUTO"),
                badgeDrawable("24"),
                badgeDrawable("12")
            ),
        ) { which, dialog ->
            TimeFormatPrefs.setMode(ctx, values[which])
            updateTimeFormatSummary(findPreference("pref_time_format"))
            dialog.dismiss()
        }
    }

    private fun showThemeColorDialog() {
        val ctx = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val current = prefs.getString("pref_accent", "default") ?: "default"

        val allEntries = resources.getStringArray(R.array.pref_accent_entries)
        val allValues = resources.getStringArray(R.array.pref_accent_values)

        val entries = allEntries + getString(R.string.pref_accent_custom)
        val values = allValues + "custom"

        val checked = values.indexOf(current).let { idx -> if (idx >= 0) idx else 0 }

        val summaries = values.map { value ->
            when (value) {
                "default" -> getString(R.string.pref_accent_default_summary)
                "blue" -> getString(R.string.pref_accent_blue_summary)
                "orange" -> getString(R.string.pref_accent_orange_summary)
                "purple" -> getString(R.string.pref_accent_purple_summary)
                "pink" -> getString(R.string.pref_accent_pink_summary)
                "teal" -> getString(R.string.pref_accent_teal_summary)
                "red" -> getString(R.string.pref_accent_red_summary)
                "amber" -> getString(R.string.pref_accent_amber_summary)
                "gray" -> getString(R.string.pref_accent_gray_summary)
                "custom" -> getString(R.string.pref_accent_custom_summary)
                else -> getString(R.string.pref_theme_color_summary)
            }
        }.toTypedArray()

        showSingleSelectCheckboxDialog(
            title = getString(R.string.pref_theme_color_title),
            dialogSubtitle = getString(R.string.pref_theme_color_summary),
            entries = entries,
            checkedIndex = checked,
            summaries = summaries,
            iconDrawables = values.map { colorPreviewDrawable(accentColorForValue(ctx, it)) as Drawable? }.toTypedArray(),
        ) { which, dialog ->
            val selected = values[which]
            if (selected == "custom") {
                dialog.dismiss()
                showCustomColorPicker()
            } else {
                prefs.edit { putString("pref_accent", selected) }
                updateThemeColorSummary(findPreference("pref_theme_color"))
                dialog.dismiss()
                restartAppTask()
            }
        }
    }

    /**
     * Shared single-select card dialog.
     * Selection is shown with accent border/background only — no radio/checkmark bubbles.
     */
    private fun showSingleSelectCheckboxDialog(
        title: String,
        dialogSubtitle: String? = null,
        entries: Array<String>,
        checkedIndex: Int,
        summaries: Array<String>? = null,
        iconRes: Array<Int?>? = null,
        iconDrawables: Array<Drawable?>? = null,
        onSelected: (index: Int, dialog: AlertDialog) -> Unit,
    ) {
        lateinit var dialog: AlertDialog
        dialog = requireContext().showSwitchlyOptionDialog(
            title = title,
            subtitle = dialogSubtitle,
            options = entries.mapIndexed { index, label ->
                SwitchlyDialogOption(
                    title = label,
                    summary = summaries?.getOrNull(index),
                    iconRes = iconRes?.getOrNull(index),
                    iconDrawable = iconDrawables?.getOrNull(index),
                    selected = index == checkedIndex
                )
            },
            confirmSelection = true
        ) { which ->
            onSelected(which, dialog)
        }
    }

    private fun colorPreviewDrawable(color: Int): Drawable {
        val outline = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOutline, 0x33000000)
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), outline)
            setSize(dp(28), dp(28))
        }
    }

    private fun badgeDrawable(text: String): Drawable {
        val accent = getCurrentAccentColor(requireContext())
        val onAccent = if (androidx.core.graphics.ColorUtils.calculateContrast(Color.BLACK, accent) >=
            androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE, accent)
        ) Color.BLACK else Color.WHITE
        return TextBadgeDrawable(text, accent, onAccent)
    }

    private fun accentColorForValue(ctx: Context, value: String): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return when (value) {
            "blue" -> ContextCompat.getColor(ctx, R.color.accent_blue)
            "orange" -> ContextCompat.getColor(ctx, R.color.accent_orange)
            "purple" -> ContextCompat.getColor(ctx, R.color.accent_purple)
            "pink" -> ContextCompat.getColor(ctx, R.color.accent_pink)
            "teal" -> ContextCompat.getColor(ctx, R.color.accent_teal)
            "red" -> ContextCompat.getColor(ctx, R.color.accent_red)
            "amber" -> ContextCompat.getColor(ctx, R.color.accent_amber)
            "gray" -> ContextCompat.getColor(ctx, R.color.accent_gray)
            "custom" -> runCatching {
                (prefs.getString("pref_accent_custom", "#2E8B57") ?: "#2E8B57").toColorInt()
            }.getOrDefault(ContextCompat.getColor(ctx, R.color.accent_green))
            else -> ContextCompat.getColor(ctx, R.color.accent_green)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private class TextBadgeDrawable(
        private val text: String,
        private val backgroundColor: Int,
        private val foregroundColor: Int
    ) : Drawable() {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = backgroundColor
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = foregroundColor
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val radius = b.width().coerceAtMost(b.height()) / 2f
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), radius, bgPaint)
            textPaint.textSize = b.height() * if (text.length > 2) 0.28f else 0.42f
            val y = b.exactCenterY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, b.exactCenterX(), y, textPaint)
        }

        override fun setAlpha(alpha: Int) {
            bgPaint.alpha = alpha
            textPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            bgPaint.colorFilter = colorFilter
            textPaint.colorFilter = colorFilter
        }

        @Deprecated("Required by the Android Drawable API")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class SingleSelectCheckboxAdapter(
        private val entries: List<String>,
        initialSelected: Int,
        private val onSelected: (Int) -> Unit,
    ) : RecyclerView.Adapter<SingleSelectCheckboxAdapter.VH>() {

        private var selectedIndex: Int = initialSelected.coerceIn(0, (entries.size - 1).coerceAtLeast(0))

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_single_select_checkbox, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.title.text = entries[position]

            // Ensure checkbox tint is always the current accent (especially important in custom-accent mode where OEM/framework defaults can show up again when views are rebound after scrolling).
            val accent = AccentColor.getAccentColorInt(holder.itemView.context)
            val unchecked = (accent and 0x00FFFFFF) or (0x8C shl 24) // ~55% alpha
            holder.cb.buttonTintList = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(
                    accent,
                    unchecked
                )
            )

            // Avoid recursive click loops: row and checkbox both call select(position), but do NOT call performClick().
            holder.cb.setOnCheckedChangeListener(null)
            holder.cb.isChecked = position == selectedIndex
            // Checkbox is visual-only; the entire row handles clicks for stable behaviour across OEMs.
            holder.cb.isClickable = false
            holder.cb.isFocusable = false

            fun select() {
                // Selecting an entry immediately applies + closes the dialog.
                // Avoid any adapter update churn here (can race with dialog dismissal on some OEMs).
                // Do not treat "position" as stable; view holders can be rebound.
                val p = holder.bindingAdapterPosition
                if (p == RecyclerView.NO_POSITION) {
                    return
                }
                selectedIndex = p
                onSelected(p)
            }

            holder.itemView.setOnClickListener { select() }
        }

        override fun getItemCount(): Int = entries.size

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.title)
            val cb: MaterialRadioButton = itemView.findViewById(R.id.radio)
        }
    }

    private fun showCustomColorPicker() {
        val ctx = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val defaultAccent = AccentColor.getAccentColorInt(requireContext())
        val defaultHex = String.format("#%06X", 0xFFFFFF and defaultAccent)
        val initialHex = prefs.getString("pref_accent_custom", defaultHex) ?: defaultHex
        var color = try { initialHex.toColorInt() } catch (_: IllegalArgumentException) { defaultAccent }

        val view = layoutInflater.inflate(R.layout.dialog_color_picker, FrameLayout(requireContext()), false)
        val preview = view.findViewById<View>(R.id.colorPreview)
        val sliderR = view.findViewById<SeekBar>(R.id.sliderR)
        val sliderG = view.findViewById<SeekBar>(R.id.sliderG)
        val sliderB = view.findViewById<SeekBar>(R.id.sliderB)

        val accentList = ColorStateList.valueOf(defaultAccent)
        sliderR.thumbTintList = accentList
        sliderR.progressTintList = accentList
        sliderG.thumbTintList = accentList
        sliderG.progressTintList = accentList
        sliderB.thumbTintList = accentList
        sliderB.progressTintList = accentList

        fun updatePreviewFromColor() { preview.setBackgroundColor(color) }
        fun updateColorFromSliders() {
            color = Color.rgb(sliderR.progress, sliderG.progress, sliderB.progress)
            updatePreviewFromColor()
        }

        sliderR.max = 255; sliderG.max = 255; sliderB.max = 255
        sliderR.progress = Color.red(color)
        sliderG.progress = Color.green(color)
        sliderB.progress = Color.blue(color)

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateColorFromSliders() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        sliderR.setOnSeekBarChangeListener(listener)
        sliderG.setOnSeekBarChangeListener(listener)
        sliderB.setOnSeekBarChangeListener(listener)
        updatePreviewFromColor()

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.pref_accent_custom_title))
            .setView(view)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val hex = String.format("#%08X", color)
                prefs.edit {
                    putString("pref_accent", "custom")
                    putString("pref_accent_custom", hex)
                }
                updateThemeColorSummary(findPreference("pref_theme_color"))
                restartAppTask()
            }
            .create()

        dialog.setOnShowListener {
            dialog.styleSwitchlyDialogButtons()
            runCatching { CustomAccentApplier.applyToDialog(dialog) }
        }
        dialog.show()
    }

    private fun showProgressDialog(ctx: Context, titleRes: Int, messageRes: Int): AlertDialog {
        val content = LayoutInflater.from(ctx).inflate(R.layout.dialog_progress, null, false)
        content.findViewById<TextView>(R.id.progressMessage).setText(messageRes)
        content.findViewById<CircularProgressIndicator>(R.id.progressIndicator)
            .setIndicatorColor(AccentColor.getAccentColorInt(ctx))

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(titleRes)
            .setView(content)
            .setCancelable(false)
            .create()

        dialog.show()
        return dialog
    }

    private fun confirmAction(title: String, message: String, positiveText: String, onConfirm: () -> Unit) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onConfirm() }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener { dialog.styleSwitchlyDialogButtons() }
        dialog.show()
    }

    // Emergency
    private fun refreshEmergencyPref() {
        val pref = findPreference<Preference>("pref_emergency_unlock") ?: return
        val ctx = requireContext()

        val featureEnabled = EmergencyBypassStore.isFeatureEnabled(ctx)
        pref.isVisible = true

        if (!featureEnabled) {
            pref.isEnabled = true
            pref.summary = getString(R.string.pref_emergency_summary_disabled)
            return
        }

        val active = EmergencyBypassStore.isActive(ctx)
        val paused = EmergencyBypassStore.isPaused(ctx)
        val usedToday = EmergencyBypassStore.hasUsedToday(ctx)
        val remaining = EmergencyBypassStore.minutesRemaining(ctx)

        // Keep this clickable while active/paused so users can pause/resume.
        pref.isEnabled = active || paused || !usedToday
        pref.summary = when {
            paused -> getString(R.string.pref_emergency_summary_paused, remaining)
            active -> getString(R.string.pref_emergency_summary_active_with_time, remaining)
            usedToday -> getString(R.string.pref_emergency_summary_used)
            else -> getString(R.string.pref_emergency_summary)
        }
    }

    private fun refreshLockUi() {
        val ctx = requireContext()
        val enabled = SwitchModeStore.isEnabled(ctx)
        val requireNfc = SwitchModeStore.isNfcRequiredForDisable(ctx)
        val locked = enabled && requireNfc
        stylePreferenceLocked("pref_switch_mode", locked)
        stylePreferenceLocked("pref_permissions", locked)
    }

    private fun stylePreferenceLocked(key: String, locked: Boolean) {
        val pref = findPreference<Preference>(key) ?: return
        val ctx = requireContext()

        val baseTitle = pref.extras.getString("base_title") ?: pref.title?.toString().orEmpty()
        if (!pref.extras.containsKey("base_title")) pref.extras.putString("base_title", baseTitle)

        val baseSummaryStored = pref.extras.getString("base_summary")
        val baseSummary = baseSummaryStored ?: pref.summary?.toString()
        if (!pref.extras.containsKey("base_summary") && pref.summary != null) {
            pref.extras.putString("base_summary", pref.summary.toString())
        }

        if (!locked) {
            pref.title = baseTitle
            if (baseSummary != null) pref.summary = baseSummary
            return
        }

        val disabledColor = ContextCompat.getColor(ctx, R.color.status_neutral)
        val titleText = "🔒 $baseTitle"
        pref.title = SpannableString(titleText).apply {
            setSpan(ForegroundColorSpan(disabledColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val summaryText = baseSummary ?: ""
        if (summaryText.isNotEmpty()) {
            pref.summary = SpannableString(summaryText).apply {
                setSpan(ForegroundColorSpan(disabledColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    // app restart
    private fun restartAppTask() {
        val i = Intent(requireContext(), MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(i)
        requireActivity().finish()
    }

    // accent helper
    private fun getCurrentAccentColor(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = prefs.getString("pref_accent", "default") ?: "default"
        return if (key == "custom") {
            val hex = prefs.getString("pref_accent_custom", "#2E8B57") ?: "#2E8B57"
            try { hex.toColorInt() } catch (_: IllegalArgumentException) { AccentColor.getAccentColorInt(context) }
        } else {
            AccentColor.getAccentColorInt(context)
        }
    }

    private fun tintCategories() {
        val screen = preferenceScreen ?: return
        val accent = getCurrentAccentColor(requireContext())
        tintGroup(screen, accent)
    }

    private fun ensureDeveloperInfoIconAccent() {
        val ctx = context ?: return
        val pref = findPreference<Preference>("pref_about_developer_info") ?: return
        val accent = getCurrentAccentColor(ctx)
        val base = ContextCompat.getDrawable(ctx, R.drawable.info_24)?.mutate() ?: return
        val wrapped = DrawableCompat.wrap(base)
        DrawableCompat.setTint(wrapped, accent)
        pref.icon = wrapped
    }

    private fun tintGroup(group: PreferenceGroup, accent: Int) {
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is PreferenceCategory) {
                val title = pref.title?.toString() ?: ""
                if (title.isNotEmpty()) {
                    categoryTitles.add(title)
                    pref.title = SpannableString(title).apply {
                        setSpan(ForegroundColorSpan(accent), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            } else {
                val title = pref.title?.toString() ?: ""
                if (title.isNotEmpty()) {
                    pref.title = SpannableString(title).apply {
                        setSpan(ForegroundColorSpan(accent), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }

                pref.icon?.let { icon ->
                    val wrapped = DrawableCompat.wrap(icon.mutate())
                    DrawableCompat.setTint(wrapped, accent)
                    pref.icon = wrapped
                }
            }
            if (pref is PreferenceGroup) tintGroup(pref, accent)
        }
    }

    /**
     * Account setting: change the PIN used for Emergency Unlock.
     * Flow:
     * - If no PIN exists yet: directly ask to set one.
     * - If PIN exists: verify current PIN first, then ask to set a new one.
     */
    private fun showChangeEmergencyPinFlow() {
        val act = activity ?: return
        EmergencyPinDialog.showChangePinFlow(act)
    }

    fun openEmergencyUnlockDirect() {
        showEmergencyUnlockWithPin()
    }

    private fun showEmergencyUnlockWithPin() {
        val ctx = requireContext()
        val storedPin = getStoredEmergencyPin(ctx)

        if (!EmergencyBypassStore.isFeatureEnabled(ctx)) {
            val dialog = AlertDialog.Builder(ctx)
                .setTitle(getString(R.string.pref_emergency_title))
                .setMessage(getString(R.string.emergency_disabled_message_controls))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.emergency_open_controls_action)) { _, _ ->
                    startActivity(Intent(ctx, ToggleOptionsActivity::class.java))
                }
                .create()

            dialog.setOnShowListener { dialog.styleSwitchlyDialogButtons() }
            dialog.show()
            return
        }

        val active = EmergencyBypassStore.isActive(ctx)
        val paused = EmergencyBypassStore.isPaused(ctx)
        if (active || paused) {
            showEmergencyManageDialog()
            return
        }

        if (EmergencyBypassStore.hasUsedToday(ctx)) {
            requireView().showWarnPill(R.string.emergency_used_today)
            return
        }

        if (storedPin.isNullOrEmpty()) {
            showSetEmergencyPinDialog { showEmergencyUnlockStartDialog() }
        } else {
            showEnterEmergencyPinDialog(storedPin) { showEmergencyUnlockStartDialog() }
        }
    }

    private fun showEmergencyManageDialog() {
        val ctx = requireContext()
        val active = EmergencyBypassStore.isActive(ctx)
        val paused = EmergencyBypassStore.isPaused(ctx)
        val remaining = EmergencyBypassStore.minutesRemaining(ctx)

        if (!active && !paused) {
            refreshEmergencyPref()
            return
        }

        val title = if (paused) {
            getString(R.string.emergency_manage_title_paused, remaining)
        } else {
            getString(R.string.emergency_manage_title_active, remaining)
        }

        val actions = if (active) {
            listOf(
                getString(R.string.emergency_action_pause),
                getString(R.string.emergency_action_end)
            )
        } else {
            listOf(
                getString(R.string.emergency_action_resume),
                getString(R.string.emergency_action_end)
            )
        }

        ctx.showSwitchlyOptionDialog(
            title = title,
            options = actions.mapIndexed { index, label ->
                SwitchlyDialogOption(
                    title = label,
                    destructive = index == 1
                )
            }
        ) { which ->
            if (active) {
                when (which) {
                    0 -> {
                        val ok = EmergencyBypassStore.pause(ctx)
                        if (ok) {
                            SwitchModeStore.clearTemporary(ctx)
                            AppLogStore.append(ctx, "Emergency", "Emergency mode paused from Settings")
                            requireView().showWarnPill(getString(R.string.emergency_paused_toast))
                        }
                    }
                    1 -> {
                        AppLogStore.append(ctx, "Emergency", "Emergency mode ended from Settings")
                        EmergencyBypassStore.cancel(ctx)
                        SwitchModeStore.clearTemporary(ctx)
                        requireView().showWarnPill(getString(R.string.emergency_ended_toast))
                    }
                }
            } else {
                when (which) {
                    0 -> {
                        val ok = EmergencyBypassStore.resume(ctx)
                        if (ok) {
                            val remaining = EmergencyBypassStore.minutesRemaining(ctx).coerceAtLeast(1)
                            SwitchModeStore.setTemporarilyDisabled(ctx, remaining * 60_000L, isEmergency = true)
                            AppLogStore.append(ctx, "Emergency", "Emergency mode resumed from Settings with ${remaining}m remaining")
                            requireView().showWarnPill(getString(R.string.emergency_resumed_toast))
                        }
                    }
                    1 -> {
                        AppLogStore.append(ctx, "Emergency", "Emergency mode ended from Settings")
                        EmergencyBypassStore.cancel(ctx)
                        SwitchModeStore.clearTemporary(ctx)
                        requireView().showWarnPill(getString(R.string.emergency_ended_toast))
                    }
                }
            }

            BlockingRuntime.ensureRunning(ctx)
            refreshEmergencyPref()
        }
    }

    private fun showEmergencyUnlockStartDialog() {
        val ctx = requireContext()
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.pref_emergency_title))
            .setMessage(getString(R.string.emergency_action_start_15))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.ok)) { _, dialog ->
                // Anchor to the dialog window so the pill is visible above it.
                triggerEmergencyUnlock((dialog as? AlertDialog)?.window?.decorView)
            }
            .create()

        dialog.setOnShowListener { dialog.styleSwitchlyDialogButtons() }
        dialog.show()
    }

    private fun triggerEmergencyUnlock(anchor: View? = null) {

        val ctx = requireContext()
        val minutes = 15
        val ok = EmergencyBypassStore.enableIfAllowed(ctx, minutes)
        val pillAnchor = anchor ?: view ?: return
        if (ok) {
            AppLogStore.append(ctx, "Emergency", "Emergency mode started from Settings for ${minutes}m")
            SwitchModeStore.setTemporarilyDisabled(ctx, minutes * 60_000L, isEmergency = true)
            pillAnchor.showWarnPill(getString(R.string.emergency_enabled_toast, minutes))
            BlockingRuntime.ensureRunning(ctx)
        } else {
            pillAnchor.showWarnPill(getString(R.string.emergency_used_today))
        }
        refreshEmergencyPref()
    }

    private fun getStoredEmergencyPin(ctx: Context): String? {
        val appPrefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)

        val candidates = listOf(
            appPrefs.getString(KEY_EMERGENCY_PIN, null),
            defaultPrefs.getString(KEY_EMERGENCY_PIN, null),
            appPrefs.getString("emergency_pin", null),
            defaultPrefs.getString("emergency_pin", null),
            appPrefs.getString("pref_emergency_unlock_pin", null),
            defaultPrefs.getString("pref_emergency_unlock_pin", null),
            appPrefs.getString("emergency_unlock_pin", null),
            defaultPrefs.getString("emergency_unlock_pin", null),
        )

        val resolved = candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
        if (!resolved.isNullOrEmpty() && appPrefs.getString(KEY_EMERGENCY_PIN, null) != resolved) {
            appPrefs.edit { putString(KEY_EMERGENCY_PIN, resolved) }
        }
        return resolved
    }

    private fun showSetEmergencyPinDialog(onSuccess: () -> Unit) {
        val act = activity ?: return
        EmergencyPinDialog.showSetPin(act, onSuccess)
    }

    private fun showEnterEmergencyPinDialog(expectedPin: String, onSuccess: () -> Unit) {
        val act = activity ?: return
        EmergencyPinDialog.showEnterPin(act, onSuccess)
    }


    companion object {
        const val ARG_FOCUS_KEY = "switchly.settings.focus_key"
        private const val ARG_PREFERENCE_ROOT = "androidx.preference.PreferenceFragmentCompat.PREFERENCE_ROOT"
        private const val PREFS = "switchly_prefs"
        private const val KEY_DEV_UNLOCKED = "pref_dev_unlocked"
        private const val KEY_EMERGENCY_PIN = "pref_emergency_pin"
    }
}
