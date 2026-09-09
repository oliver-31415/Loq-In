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

package com.oliver.loqin.feature.picker

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oliver.loqin.R
import com.oliver.loqin.blocking.BlockingRuntime
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.AttemptLimitStore
import com.oliver.loqin.data.prefs.IgnoredUsageAppsStore
import com.oliver.loqin.data.prefs.InAppRuleStore
import com.oliver.loqin.data.prefs.ProfileRuleModeStore
import com.oliver.loqin.data.prefs.SessionLimitStore
import com.oliver.loqin.data.prefs.UsageLimitStore
import com.oliver.loqin.feature.settings.ManageBlockedWebsitesActivity
import com.oliver.loqin.feature.settings.InAppRulesActivity
import com.oliver.loqin.feature.usage.QuickLimitDialogs
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.applyLoqInStyle
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.ui.dialog.LoqInInfoRow
import com.oliver.loqin.ui.dialog.showLoqInInfoDialog
import com.oliver.loqin.ui.dialog.LoqInDialogOption
import com.oliver.loqin.ui.dialog.showLoqInOptionDialog
import com.oliver.loqin.util.ActivityTransitionCompat
import com.oliver.loqin.util.AppBlockSafety
import com.oliver.loqin.util.EditingLockGuard
import com.oliver.loqin.util.LocaleHelper
import com.oliver.loqin.util.PackageLaunchIntentCompat
import com.oliver.loqin.util.ProtectionEditPolicy
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

class AppPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROFILE_NAME = "extra_profile_name"
        const val EXTRA_ALLOW_LOCKED_PROFILE_STRICT_EDITS = "extra_allow_locked_profile_strict_edits"
    }

    private lateinit var adapter: AppListAdapter
    private var currentProfile: String? = null
    private var currentRuleMode: String = ProfileRuleModeStore.MODE_BLOCK_SELECTED
    private var autoBlockNewAppsCheckbox: CheckBox? = null
    private var originalManagedPackages: Set<String> = emptySet()
    private var allowLockedProfileStrictEdits: Boolean = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    private fun selectedPackagesForCurrentMode(profile: String): Set<String> {
        // App list selection should only reflect the profile's actual block/allow list.
        // App/session/attempt limits are shown as limit metadata on the row, but they must not pin the checkbox on.
        // Otherwise users cannot remove a previously blocked browser/app from the list just because it has a limit or warning row.
        return if (currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED) {
            ProfileStore.getAllowedForProfile(this, profile)
        } else {
            ProfileStore.getBlockedForProfile(this, profile)
        }
    }

    private fun saveCurrentModeSelection(profile: String) {
        val managed = AppBlockSafety.sanitizeManagedPackages(this, adapter.getManagedPackages())
        if (currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED) {
            ProfileStore.setAllowedForProfile(this, profile, managed)
        } else {
            ProfileStore.setBlockedForProfile(this, profile, managed)
        }
    }

    private fun ensureLoqInDisabledForAppRules(): Boolean {
        if (EditingLockGuard.isLocked(this)) {
            findViewById<View>(android.R.id.content)
                .showWarnPill(R.string.toast_disable_loqin_to_edit_blocked_apps)
            return false
        }
        return true
    }

    private fun canTightenCurrentProfile(): Boolean =
        if (allowLockedProfileStrictEdits && !currentProfile.isNullOrBlank()) {
            true
        } else {
            ProtectionEditPolicy.canTightenActiveProfile(this, currentProfile)
        }

    private fun canChangeSelection(currentlySelected: Boolean, requestedSelected: Boolean): Boolean =
        ProtectionEditPolicy.canChangeSelection(
            context = this,
            profile = currentProfile,
            allowMode = currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED,
            currentlySelected = currentlySelected,
            requestedSelected = requestedSelected,
            allowInactiveProfile = allowLockedProfileStrictEdits,
        )

    private fun syncReadOnlyUi() {
        val readOnly = EditingLockGuard.isLocked(this)
        val canTighten = readOnly && canTightenCurrentProfile()

        // Locked controls stay tappable (dimmed) so the tap can warn via popover
        // instead of silently doing nothing. Guards in the listeners revert + warn.
        findViewById<MaterialButtonToggleGroup>(R.id.toggleProfileRuleMode)?.apply {
            isEnabled = true
            alpha = if (readOnly) 0.62f else 1f
        }
        findViewById<MaterialButton>(R.id.btnBlockSelectedMode)?.isEnabled = true
        findViewById<MaterialButton>(R.id.btnAllowSelectedMode)?.isEnabled = true
        findViewById<CheckBox>(R.id.cbAutoBlockNewApps)?.apply {
            isEnabled = currentRuleMode != ProfileRuleModeStore.MODE_ALLOW_SELECTED && !currentProfile.isNullOrBlank()
            alpha = if (readOnly) 0.45f else if (isEnabled) 1f else 0.55f
        }
        listOf(R.id.btnSelectAll, R.id.btnClearAll).forEach { viewId ->
            findViewById<View>(viewId)?.apply {
                isEnabled = true
                isClickable = true
                alpha = if (readOnly) 0.45f else 1f
            }
        }
        findViewById<View>(R.id.btnSave)?.apply {
            isEnabled = true
            isClickable = true
            alpha = if (!readOnly || canTighten) 1f else 0.45f
        }

        if (::adapter.isInitialized && adapter.itemCount > 0) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)
        CustomAccentApplier.applyIfNeeded(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))
        val toolbarIconColor = toolbarForegroundColor()
        toolbar.navigationIcon?.mutate()?.setTint(toolbarIconColor)

        val rvApps = findViewById<RecyclerView>(R.id.rvApps)
        val searchBox = findViewById<TextInputLayout>(R.id.searchBox)
        val etSearch = findViewById<TextInputEditText>(R.id.etSearch)
        val btnSelectAll = findViewById<MaterialButton>(R.id.btnSelectAll)
        val btnClearAll = findViewById<MaterialButton>(R.id.btnClearAll)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val cbAutoBlockNewApps = findViewById<CheckBox>(R.id.cbAutoBlockNewApps)
        val toggleProfileRuleMode = findViewById<MaterialButtonToggleGroup>(R.id.toggleProfileRuleMode)
        val btnBlockSelectedMode = findViewById<MaterialButton>(R.id.btnBlockSelectedMode)
        val btnAllowSelectedMode = findViewById<MaterialButton>(R.id.btnAllowSelectedMode)
        val tvProfileRuleModeSummary = findViewById<TextView>(R.id.tvProfileRuleModeSummary)
        findViewById<ImageButton>(R.id.btnAppRulesInfo).apply {
            imageTintList = ColorStateList.valueOf(toolbarIconColor)
            setColorFilter(toolbarIconColor)
            post {
                imageTintList = ColorStateList.valueOf(toolbarIconColor)
                setColorFilter(toolbarIconColor)
            }
            setOnClickListener { showAppRulesInfo() }
        }
        autoBlockNewAppsCheckbox = cbAutoBlockNewApps



        rvApps.layoutManager = LinearLayoutManager(this)

        val requestedProfile = intent.getStringExtra(EXTRA_PROFILE_NAME)?.trim().orEmpty()
        val requestedProfileExists = requestedProfile.isNotBlank() && ProfileStore.getProfiles(this).contains(requestedProfile)
        currentProfile = requestedProfile.takeIf { requestedProfileExists }
            ?: ProfileStore.getCurrent(this)
        allowLockedProfileStrictEdits =
            requestedProfileExists && intent.getBooleanExtra(EXTRA_ALLOW_LOCKED_PROFILE_STRICT_EDITS, false)
        currentRuleMode = currentProfile?.let { ProfileRuleModeStore.getMode(this, it) }
            ?: ProfileRuleModeStore.MODE_BLOCK_SELECTED
        setupProfileRuleMode(
            toggleProfileRuleMode,
            btnBlockSelectedMode,
            btnAllowSelectedMode,
            tvProfileRuleModeSummary,
            cbAutoBlockNewApps,
            toolbar,
            btnSelectAll,
            btnClearAll
        )

        val preselectedManaged: Set<String> = currentProfile?.takeIf { it.isNotBlank() }
            ?.let { selectedPackagesForCurrentMode(it) }
            ?: emptySet()
        originalManagedPackages = AppBlockSafety.sanitizeManagedPackages(this, preselectedManaged)

        adapter = AppListAdapter(
            allApps = emptyList(),
            preselectedManaged = AppBlockSafety.sanitizeManagedPackages(this, preselectedManaged),
            currentProfileProvider = { currentProfile },
            isAllowModeProvider = { currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED },
            onSetLimitClicked = { app ->
                QuickLimitDialogs.showForApp(
                    activity = this,
                    pkg = app.packageName,
                    label = app.label
                ) {
                    refreshAdapterSelectionFromStore()
                }
            },
            onSetSessionLimitClicked = { app ->
                QuickLimitDialogs.showForApp(
                    activity = this,
                    pkg = app.packageName,
                    label = app.label
                ) { refreshAdapterSelectionFromStore() }
            },
            onWebsiteRulesClicked = { app -> openWebsiteRulesFromPicker(app) },
            onInAppRulesClicked = { app -> openInAppRulesFromPicker(app) },
            onRowActionsClicked = { app, hasWebsiteRules, hasInAppRules ->
                showPickerRowActions(app, hasWebsiteRules, hasInAppRules)
            },
            onProtectedSelectionRequested = { app, onAllowed ->
                ensureAppCanBeManaged(app, onAllowed)
            },
            onSelectionChanged = { updateClearButtonLabel(btnClearAll) },
            isReadOnlyProvider = { EditingLockGuard.isLocked(this) },
            canChangeSelectionProvider = { current, requested -> canChangeSelection(current, requested) }
        )
        rvApps.adapter = adapter

        btnSave.backgroundTintList = AccentColor.getActiveColor(this)
        btnSave.setTextColor(ContextCompat.getColor(this, R.color.font_white))
        searchBox.boxStrokeColor = AccentColor.getAccentColorInt(this)
        searchBox.hintTextColor = AccentColor.getActiveColor(this)
        etSearch.backgroundTintList = AccentColor.getActiveColor(this)

        setupAutoBlockNewAppsCheckbox(cbAutoBlockNewApps)
        setupBulkButtons(btnSelectAll, btnClearAll, btnSave)
        updateClearButtonLabel(btnClearAll)
        syncReadOnlyUi()

        Thread {
            val load = loadPickerEntries(this, preselectedManaged)
            if (isFinishing || isDestroyed) return@Thread

            runOnUiThread {
                val sanitizedPreselected = AppBlockSafety.sanitizeManagedPackages(this, preselectedManaged)

                adapter = AppListAdapter(
                    allApps = load.entries,
                    preselectedManaged = sanitizedPreselected,
                    currentProfileProvider = { currentProfile },
                    isAllowModeProvider = { currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED },
                    onSetLimitClicked = { app ->
                        QuickLimitDialogs.showForApp(
                            activity = this,
                            pkg = app.packageName,
                            label = app.label
                        ) {
                            refreshAdapterSelectionFromStore()
                        }
                    },
                    onSetSessionLimitClicked = { app ->
                        QuickLimitDialogs.showForApp(
                            activity = this,
                            pkg = app.packageName,
                            label = app.label
                        ) { adapter.notifyPkgChanged(app.packageName) }
                    },
                    onWebsiteRulesClicked = { app -> openWebsiteRulesFromPicker(app) },
                    onInAppRulesClicked = { app -> openInAppRulesFromPicker(app) },
                    onRowActionsClicked = { app, hasWebsiteRules, hasInAppRules ->
                        showPickerRowActions(app, hasWebsiteRules, hasInAppRules)
                    },
                    onProtectedSelectionRequested = { app, onAllowed ->
                        ensureAppCanBeManaged(app, onAllowed)
                    },
                    onSelectionChanged = { updateClearButtonLabel(btnClearAll) },
                    isReadOnlyProvider = { EditingLockGuard.isLocked(this) },
                    canChangeSelectionProvider = { current, requested -> canChangeSelection(current, requested) }
                )
                rvApps.adapter = adapter

                setupSearch(etSearch)
                setupSaveButton(btnSave)
                syncReadOnlyUi()

                if (load.unavailableCount > 0) {
                    updateClearButtonLabel(btnClearAll)
                    showPickerNotice(
                        btnSave,
                        resources.getQuantityString(
                            R.plurals.unavailable_apps_loaded_notice,
                            load.unavailableCount,
                            load.unavailableCount
                        )
                    )
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        syncReadOnlyUi()
        if (::adapter.isInitialized) {
            if (adapter.itemCount > 0) {
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
            }
        }
    }

    override fun finish() {
        super.finish()
        ActivityTransitionCompat.finishWithoutAnimation(this)
    }

    private data class PickerLoadResult(
        val entries: List<AppEntry>,
        val unavailableCount: Int
    )

    private fun loadPickerEntries(context: Context, preselectedManaged: Set<String>): PickerLoadResult {
        val hiddenFromPickers = IgnoredUsageAppsStore.getAppPickerHiddenPackages(context)
        val installed = (loadLaunchableApps(context) + loadSupportedInAppApps(context))
            .distinctBy { it.packageName }
            .filterNot { it.packageName in hiddenFromPickers }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
        val activeProfile = currentProfile?.takeIf { it.isNotBlank() }
        val activeInAppRulePackages = activeProfile
            ?.let { InAppRuleStore.getPackagesWithEnabledRules(context, it) }
            ?: emptySet()
        val activeLimitPackages = activeProfile?.let { profile ->
            buildSet {
                addAll(UsageLimitStore.getAllLimitedPackages(context, profile))
                addAll(SessionLimitStore.getAllLimitedPackages(context, profile))
                addAll(AttemptLimitStore.getAllLimitedPackages(context, profile))
            }
        }.orEmpty()
        // Hidden apps disappear from new selections. Existing configuration stays visible/editable, but protection is ignored for the package until it is unhidden.
        val packagesToResolve = preselectedManaged + activeInAppRulePackages + activeLimitPackages

        if (packagesToResolve.isEmpty()) {
            return PickerLoadResult(entries = installed, unavailableCount = 0)
        }

        val installedByPackage = installed.associateBy { it.packageName }
        val selectedNotInLauncher = mutableListOf<AppEntry>()
        val unavailable = mutableListOf<AppEntry>()
        val packageListLooksIncomplete = installed.size < 50 &&
            packagesToResolve.size >= 50 &&
            packagesToResolve.size > installed.size

        packagesToResolve
            .asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .forEach { pkg ->
                if (pkg in installedByPackage) return@forEach

                val installedEntry = resolveInstalledPackageEntry(context, pkg)
                if (installedEntry != null) {
                    selectedNotInLauncher += installedEntry
                } else if (packageListLooksIncomplete) {
                    // If Android/Samsung only returns a very small visible app list while the profile already contains many selected packages, avoid treating those packages as uninstalled.
                    // Some devices limit package visibility and would otherwise mark real installed apps as unavailable and remove them in bulk.
                    selectedNotInLauncher += AppEntry(
                        packageName = pkg,
                        label = pkg,
                        isAvailable = true,
                        blockSafety = AppBlockSafety.resolve(context, pkg)
                    )
                } else {
                    unavailable += AppEntry(
                        packageName = pkg,
                        label = context.getString(R.string.unavailable_app_label),
                        isAvailable = false
                    )
                }
            }

        val entries = (unavailable + selectedNotInLauncher + installed)
            .distinctBy { it.packageName }

        return PickerLoadResult(entries = entries, unavailableCount = unavailable.size)
    }

    private fun loadSupportedInAppApps(context: Context): List<AppEntry> {
        return InAppRuleStore.supportedPackages()
            .mapNotNull { pkg ->
                if (!PackageLaunchIntentCompat.isLaunchable(context, pkg)) return@mapNotNull null
                resolveInstalledPackageEntry(context, pkg)
            }
    }

    private fun loadLaunchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val byPackage = linkedMapOf<String, AppEntry>()

        fun addEntryFromApplicationInfo(ai: ApplicationInfo?) {
            if (ai == null) {
                return
            }
            val pkg = ai.packageName?.takeIf { it.isNotBlank() } ?: return
            if (pkg == context.packageName) {
                return
            }
            if (pkg in byPackage) {
                return
            }

            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: pkg

            byPackage[pkg] = AppEntry(
                label = label,
                packageName = pkg,
                blockSafety = AppBlockSafety.resolve(context, pkg)
            )
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        runCatching { pm.queryIntentActivities(launcherIntent, 0) }
            .getOrDefault(emptyList())
            .forEach { ri -> addEntryFromApplicationInfo(ri.activityInfo?.applicationInfo) }

        // Keep the base list limited to Android's launcher query.
        // Selected packages are resolved individually below via getApplicationInfoCompat(), so installed apps are not marked unavailable just because a Samsung/Android launcher query returned an incomplete list.

        return byPackage.values.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun resolveInstalledPackageEntry(context: Context, packageName: String): AppEntry? {
        val pm = context.packageManager
        val ai = runCatching { getApplicationInfoCompat(pm, packageName) }.getOrNull() ?: return null
        val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: packageName

        return AppEntry(
            label = label,
            packageName = packageName,
            isAvailable = true,
            blockSafety = AppBlockSafety.resolve(context, packageName)
        )
    }

    private fun getApplicationInfoCompat(pm: PackageManager, packageName: String): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getApplicationInfo(packageName, 0)
        }
    }

    private fun setupProfileRuleMode(
        toggleProfileRuleMode: MaterialButtonToggleGroup,
        btnBlockSelectedMode: MaterialButton,
        btnAllowSelectedMode: MaterialButton,
        tvProfileRuleModeSummary: TextView,
        cbAutoBlockNewApps: CheckBox,
        toolbar: MaterialToolbar,
        btnSelectAll: MaterialButton,
        btnClearAll: MaterialButton
    ) {
        val isAllow = currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED
        toggleProfileRuleMode.clearOnButtonCheckedListeners()
        toggleProfileRuleMode.check(if (isAllow) R.id.btnAllowSelectedMode else R.id.btnBlockSelectedMode)
        toggleProfileRuleMode.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            if (!ensureLoqInDisabledForAppRules()) {
                group.check(if (currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED) R.id.btnAllowSelectedMode else R.id.btnBlockSelectedMode)
                return@addOnButtonCheckedListener
            }

            val activeProfile = currentProfile
            if (activeProfile.isNullOrBlank()) {
                group.check(if (currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED) R.id.btnAllowSelectedMode else R.id.btnBlockSelectedMode)
                findViewById<View>(android.R.id.content).showWarnPill(R.string.select_profile_first)
                return@addOnButtonCheckedListener
            }

            val requested = if (checkedId == R.id.btnAllowSelectedMode) {
                ProfileRuleModeStore.MODE_ALLOW_SELECTED
            } else {
                ProfileRuleModeStore.MODE_BLOCK_SELECTED
            }

            if (requested == currentRuleMode) return@addOnButtonCheckedListener

            fun applyRequestedMode() {
                saveCurrentModeSelection(activeProfile)
                currentRuleMode = requested
                ProfileRuleModeStore.setMode(this, activeProfile, requested)
                adapter.replaceManagedPackages(
                    AppBlockSafety.sanitizeManagedPackages(this, selectedPackagesForCurrentMode(activeProfile))
                )
                applyProfileRuleModeUi(
                    toggleProfileRuleMode,
                    btnBlockSelectedMode,
                    btnAllowSelectedMode,
                    tvProfileRuleModeSummary,
                    cbAutoBlockNewApps,
                    toolbar,
                    btnSelectAll,
                    btnClearAll
                )
                BlockingRuntime.ensureRunning(this)
            }

            if (requested == ProfileRuleModeStore.MODE_ALLOW_SELECTED) {
                group.check(R.id.btnBlockSelectedMode)
                val allowedCount = ProfileStore.getAllowedForProfile(this, activeProfile).size
                val blockedCount = (adapter.itemCount - allowedCount).coerceAtLeast(0)
                AlertDialog.Builder(this)
                    .setTitle(R.string.app_allow_mode_preview_title)
                    .setMessage(
                        getString(
                            R.string.app_allow_mode_preview_body,
                            resources.getQuantityString(
                                R.plurals.app_allow_mode_preview_blocked_count,
                                blockedCount,
                                blockedCount,
                            ),
                            resources.getQuantityString(
                                R.plurals.app_allow_mode_preview_allowed_count,
                                allowedCount,
                                allowedCount,
                            ),
                        )
                    )
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.app_allow_mode_preview_action) { _, _ -> applyRequestedMode() }
                    .showAccented()
            } else {
                applyRequestedMode()
            }
        }

        applyProfileRuleModeUi(
            toggleProfileRuleMode,
            btnBlockSelectedMode,
            btnAllowSelectedMode,
            tvProfileRuleModeSummary,
            cbAutoBlockNewApps,
            toolbar,
            btnSelectAll,
            btnClearAll
        )
    }

    private fun showPickerRowActions(app: AppEntry, hasWebsiteRules: Boolean, hasInAppRules: Boolean) {
        val actions = mutableListOf<Pair<LoqInDialogOption, () -> Unit>>()

        if (hasWebsiteRules) {
            actions += LoqInDialogOption(
                title = getString(R.string.app_picker_row_action_website_rules),
                summary = getString(R.string.app_picker_row_action_website_rules_summary),
                iconRes = R.drawable.language_24
            ) to { openWebsiteRulesFromPicker(app) }
        }

        if (hasInAppRules) {
            actions += LoqInDialogOption(
                title = getString(R.string.app_picker_row_action_in_app_rules),
                summary = getString(R.string.app_picker_row_action_in_app_rules_summary),
                iconRes = R.drawable.tune_24
            ) to { openInAppRulesFromPicker(app) }
        }

        actions += LoqInDialogOption(
            title = getString(R.string.app_picker_row_action_app_limits),
            summary = getString(R.string.app_picker_row_action_app_limits_summary),
            iconRes = R.drawable.schedule_24
        ) to {
            QuickLimitDialogs.showForApp(
                activity = this,
                pkg = app.packageName,
                label = app.label
            ) {
                adapter.notifyPkgChanged(app.packageName)
            }
        }

        showLoqInOptionDialog(
            title = app.label,
            options = actions.map { it.first },
            showCancelButton = false
        ) { index ->
            actions.getOrNull(index)?.second?.invoke()
        }
    }

    private fun openWebsiteRulesFromPicker(app: AppEntry) {
        findViewById<View>(android.R.id.content)
            .showWarnPill(getString(R.string.app_picker_open_website_rules_for, app.label))
        startActivity(Intent(this, ManageBlockedWebsitesActivity::class.java))
    }

    private fun openInAppRulesFromPicker(app: AppEntry) {
        findViewById<View>(android.R.id.content)
            .showWarnPill(getString(R.string.app_picker_open_in_app_rules_for, app.label))
        startActivity(
            Intent(this, InAppRulesActivity::class.java)
                .putExtra(InAppRulesActivity.EXTRA_FOCUS_PACKAGE, app.packageName)
        )
    }

    private fun applyProfileRuleModeUi(
        toggleProfileRuleMode: MaterialButtonToggleGroup,
        btnBlockSelectedMode: MaterialButton,
        btnAllowSelectedMode: MaterialButton,
        tvProfileRuleModeSummary: TextView,
        cbAutoBlockNewApps: CheckBox,
        toolbar: MaterialToolbar,
        btnSelectAll: MaterialButton,
        btnClearAll: MaterialButton
    ) {
        val isAllow = currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED
        val selectedId = if (isAllow) R.id.btnAllowSelectedMode else R.id.btnBlockSelectedMode
        if (toggleProfileRuleMode.checkedButtonId != selectedId) {
            toggleProfileRuleMode.check(selectedId)
        }

        toolbar.title = getString(R.string.app_rules_title)
        toolbar.subtitle = getString(
            R.string.app_rules_profile_subtitle,
            currentProfile?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_label_default)
        )
        tvProfileRuleModeSummary.setText(if (isAllow) R.string.profile_rule_mode_allow_summary else R.string.profile_rule_mode_block_summary)
        btnSelectAll.setText(if (isAllow) R.string.app_picker_select_all_allow else R.string.app_picker_select_all)
        btnClearAll.setText(if (isAllow) R.string.app_picker_clear_all_allow else R.string.app_picker_clear_all)
        SegmentedToggleUi.apply(
            this,
            listOf(btnBlockSelectedMode, btnAllowSelectedMode),
            selectedId,
        )

        // Auto-block only applies to Block selected mode (in Allow selected mode the
        // profile set means "allowed", so there is nothing to auto-add). Hide it there.
        val showAutoBlock = !isAllow
        cbAutoBlockNewApps.visibility = if (showAutoBlock) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvAutoBlockNewAppsSummary)?.visibility =
            if (showAutoBlock) View.VISIBLE else View.GONE
        cbAutoBlockNewApps.isEnabled = showAutoBlock && !currentProfile.isNullOrBlank()
        cbAutoBlockNewApps.isChecked = showAutoBlock && (currentProfile?.let { ProfileStore.isAutoBlockNewAppsEnabled(this, it) } ?: false)
    }

    private fun normalizeDialogBreaks(text: String): String =
        text.replace("/n", "\n").replace("\\n", "\n")

    private fun showAppRulesInfo() {
        showLoqInInfoDialog(
            title = getString(R.string.app_rules_info_title),
            rows = listOf(
                LoqInInfoRow(
                    label = getString(R.string.app_rules_info_title),
                    value = normalizeDialogBreaks(getString(R.string.app_rules_info_body)),
                ),
            ),
        )
    }

    private fun setupSearch(etSearch: TextInputEditText) {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString())
            }
        })

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                etSearch.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun setupAutoBlockNewAppsCheckbox(cbAutoBlockNewApps: CheckBox) {
        val profile = currentProfile
        val isAllow = currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED
        cbAutoBlockNewApps.isEnabled = !isAllow && !profile.isNullOrBlank()
        cbAutoBlockNewApps.isChecked = !isAllow && (profile?.let { ProfileStore.isAutoBlockNewAppsEnabled(this, it) } ?: false)
        cbAutoBlockNewApps.setOnCheckedChangeListener { _, isChecked ->
            if (!ensureLoqInDisabledForAppRules()) {
                cbAutoBlockNewApps.setOnCheckedChangeListener(null)
                cbAutoBlockNewApps.isChecked = !isAllow && (profile?.let { ProfileStore.isAutoBlockNewAppsEnabled(this, it) } ?: false)
                cbAutoBlockNewApps.alpha = 0.45f
                return@setOnCheckedChangeListener
            }
            if (currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED) return@setOnCheckedChangeListener
            val activeProfile = currentProfile
            if (activeProfile.isNullOrBlank()) return@setOnCheckedChangeListener
            ProfileStore.setAutoBlockNewAppsEnabled(this, activeProfile, isChecked)
            if (isChecked) {
                ProfileStore.setAutoBlockKnownPackages(this, activeProfile, ProfileStore.getLaunchablePackages(this))
            }
        }
    }

    private fun setupBulkButtons(btnSelectAll: MaterialButton, btnClearAll: MaterialButton, btnSave: Button) {
        btnSelectAll.setOnClickListener {
            if (!ensureLoqInDisabledForAppRules()) return@setOnClickListener
            val skipped = adapter.selectAllVisible()
            val activeProfile = currentProfile
            if (!activeProfile.isNullOrBlank() && currentRuleMode != ProfileRuleModeStore.MODE_ALLOW_SELECTED) {
                ProfileStore.setAutoBlockNewAppsEnabled(this, activeProfile, true)
                ProfileStore.setAutoBlockKnownPackages(this, activeProfile, ProfileStore.getLaunchablePackages(this))
                autoBlockNewAppsCheckbox?.isChecked = true
            }
            if (skipped > 0) {
                findViewById<View>(android.R.id.content).showWarnPill(
                    resources.getQuantityString(R.plurals.app_picker_select_all_skipped_notice, skipped, skipped)
                )
            }
        }

        btnClearAll.setOnClickListener {
            if (!ensureLoqInDisabledForAppRules()) return@setOnClickListener
            val unavailableCount = adapter.unavailableManagedCount(this)
            if (unavailableCount > 0) {
                val removed = adapter.clearUnavailable(this)
                updateClearButtonLabel(btnClearAll)
                if (removed > 0) {
                    showPickerNotice(
                        btnSave,
                        resources.getQuantityString(
                            R.plurals.unavailable_apps_cleared_notice,
                            removed,
                            removed
                        )
                    )
                }
            } else {
                adapter.clearAllVisible(this)
            }
        }
    }

    private fun refreshAdapterSelectionFromStore() {
        if (!::adapter.isInitialized) return
        val profile = currentProfile ?: return
        // Saving a limit auto-adds the app to the profile (see ensureManaged),
        // so re-read the store or the row keeps showing the stale unchecked state.
        adapter.replaceManagedPackages(ProfileStore.getSelectedForProfileMode(this, profile))
    }

    private fun updateClearButtonLabel(btnClearAll: MaterialButton) {
        btnClearAll.setText(
            if (::adapter.isInitialized && adapter.unavailableManagedCount(this) > 0) {
                R.string.app_picker_clear_unavailable
            } else if (currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED) {
                R.string.app_picker_clear_all_allow
            } else {
                R.string.app_picker_clear_all
            }
        )
    }

    private fun showPickerNotice(anchor: View, message: CharSequence) {
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
            .setAnchorView(anchor)
            .applyLoqInStyle()
            .show()
    }

    private fun setupSaveButton(btnSave: Button) {
        btnSave.setOnClickListener {
            val profile = currentProfile
            if (profile.isNullOrEmpty()) {
                findViewById<View>(android.R.id.content).showWarnPill(R.string.select_profile_first)
                return@setOnClickListener
            }

            val managed = AppBlockSafety.sanitizeManagedPackages(this, adapter.getManagedPackages())
            val allowMode = currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED
            if (!ProtectionEditPolicy.canSaveSelection(
                    context = this,
                    profile = profile,
                    allowMode = allowMode,
                    original = originalManagedPackages,
                    requested = managed,
                    allowInactiveProfile = allowLockedProfileStrictEdits,
                )
            ) {
                EditingLockGuard.showLockedDialog(this, R.string.rules_tighten_only_active_message)
                syncReadOnlyUi()
                return@setOnClickListener
            }
            if (allowMode && managed.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.allow_mode_empty_save_title)
                    .setMessage(R.string.allow_mode_empty_save_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.allow_mode_empty_save_confirm) { _, _ ->
                        saveManagedApps(profile, managed)
                    }
                    .showAccented()
                return@setOnClickListener
            }

            saveManagedApps(profile, managed)
        }
    }

    private fun saveManagedApps(profile: String, managed: Set<String>) {
        val allowMode = currentRuleMode == ProfileRuleModeStore.MODE_ALLOW_SELECTED
        if (!ProtectionEditPolicy.canSaveSelection(
                context = this,
                profile = profile,
                allowMode = allowMode,
                original = originalManagedPackages,
                requested = managed,
                allowInactiveProfile = allowLockedProfileStrictEdits,
            )
        ) {
            syncReadOnlyUi()
            return
        }
        if (allowMode) {
            ProfileStore.setAllowedForProfile(this, profile, managed)
        } else {
            ProfileStore.setBlockedForProfile(this, profile, managed)
        }
        BlockingRuntime.ensureRunning(this)
        finish()
    }

    private fun ensureAppCanBeManaged(app: AppEntry, onAllowed: () -> Unit) {
        when (app.blockSafety.level) {
            AppBlockSafety.Level.PROTECTED -> {
                AlertDialog.Builder(this)
                    .setTitle(app.blockSafety.warningTitle ?: getString(R.string.app_picker_protected_warning_title))
                    .setMessage(app.blockSafety.warningMessage ?: getString(R.string.app_picker_protected_warning_message))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.app_picker_block_protected_confirm) { _, _ -> onAllowed() }
                    .showAccented()
            }

            AppBlockSafety.Level.SOFT_WARNING -> {
                if (AppBlockSafety.requiresStrictModeForBlocking(this, app.packageName)) {
                    if (!AppBlockSafety.canAllowStrictModeBlocking(this, app.packageName)) {
                        AlertDialog.Builder(this)
                            .setTitle(R.string.app_picker_settings_requirements_title)
                            .setMessage(R.string.app_picker_settings_requirements_message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok) { _, _ -> }
                            .showAccented()
                        return
                    }

                    AlertDialog.Builder(this)
                        .setTitle(app.blockSafety.warningTitle ?: getString(R.string.app_picker_protected_caution_title))
                        .setMessage(app.blockSafety.warningMessage ?: app.blockSafety.hint ?: getString(R.string.app_picker_protected_generic_hint))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.continue_label) { _, _ ->
                            AlertDialog.Builder(this)
                                .setTitle(R.string.app_picker_settings_second_warning_title)
                                .setMessage(R.string.app_picker_settings_second_warning_message)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.app_picker_block_settings_confirm) { _, _ -> onAllowed() }
                                .showAccented()
                        }
                        .showAccented()
                    return
                }

                AlertDialog.Builder(this)
                    .setTitle(app.blockSafety.warningTitle ?: getString(R.string.app_picker_protected_caution_title))
                    .setMessage(app.blockSafety.warningMessage ?: app.blockSafety.hint ?: getString(R.string.app_picker_protected_generic_hint))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.continue_label) { _, _ -> onAllowed() }
                    .showAccented()
            }

            else -> onAllowed()
        }
    }

    private fun showSessionLimitDialog(app: AppEntry) {
        ensureAppCanBeManaged(app) {
            val profile = currentProfile
            if (profile.isNullOrBlank()) {
                findViewById<View>(android.R.id.content).showWarnPill(R.string.select_profile_first)
                return@ensureAppCanBeManaged
            }

            if (EditingLockGuard.isLocked(this)) {
                findViewById<View>(android.R.id.content)
                    .showWarnPill(R.string.toast_disable_loqin_to_edit_app_limits)
                return@ensureAppCanBeManaged
            }

            val presets = listOf(0, 3, 5, 10, 15, 30, 45, 60, 90, 120)
            val labels = presets.map { m ->
                if (m == 0) {
                    getString(R.string.no_limit)
                } else {
                    resources.getQuantityString(R.plurals.minutes_format, m, m)
                }
            } + getString(R.string.custom_minutes)

            val currentLimit = SessionLimitStore.getLimitMinutes(this, profile, app.packageName)
            showLoqInOptionDialog(
                title = getString(R.string.set_session_limit_title, app.label),
                options = labels.mapIndexed { index, label ->
                    LoqInDialogOption(
                        title = label,
                        selected = if (index < presets.size) {
                            presets[index] == currentLimit
                        } else {
                            currentLimit !in presets
                        }
                    )
                },
                confirmSelection = true
            ) { which ->
                if (which < presets.size) {
                    val chosen = presets[which]
                    SessionLimitStore.setLimitMinutes(this, profile, app.packageName, chosen)

                    BlockingRuntime.ensureRunning(this)
                    adapter.notifyPkgChanged(app.packageName)
                } else {
                    showCustomSessionMinutesInput(app)
                }
            }
        }
    }

    private fun showCustomSessionMinutesInput(app: AppEntry) {
        ensureAppCanBeManaged(app) {
            val profile = currentProfile ?: return@ensureAppCanBeManaged

            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = getString(R.string.minutes_hint)
                backgroundTintList = AccentColor.getActiveColor(context)
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad, 0)
                addView(
                    input,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }

            AlertDialog.Builder(this)
                .setTitle(R.string.custom_minutes_title)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val m = input.text?.toString()?.trim()?.toIntOrNull()
                    if (m == null || m < 0) {
                        input.showWarnPill(R.string.invalid_value)
                        return@setPositiveButton
                    }

                    if (EditingLockGuard.isLocked(this)) {
                        container.showWarnPill(R.string.toast_disable_loqin_to_edit_app_limits)
                        return@setPositiveButton
                    }

                    SessionLimitStore.setLimitMinutes(this, profile, app.packageName, m)

                    BlockingRuntime.ensureRunning(this)
                    adapter.notifyPkgChanged(app.packageName)
                }
                .showAccented()
        }
    }

    private fun toolbarForegroundColor(): Int {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) {
            Color.WHITE
        } else {
            Color.BLACK
        }
    }
}
