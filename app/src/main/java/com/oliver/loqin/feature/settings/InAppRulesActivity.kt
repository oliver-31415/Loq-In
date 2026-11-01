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

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import com.oliver.loqin.R
import com.oliver.loqin.blocking.BlockingRuntime
import com.oliver.loqin.data.prefs.BlockingToggleKeys
import com.oliver.loqin.data.prefs.InAppDetectionStore
import com.oliver.loqin.data.prefs.InAppRuleStore
import com.oliver.loqin.data.prefs.ProfileRuleModeStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.SurfaceLimitStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.RuleTile
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.ui.dialog.LoqInInfoRow
import com.oliver.loqin.ui.dialog.showLoqInInfoDialog
import com.oliver.loqin.ui.dialog.styleLoqInDialogButtons
import com.oliver.loqin.ui.widgets.TileGridLayout
import com.oliver.loqin.util.EditingLockGuard
import com.oliver.loqin.util.PackageLaunchIntentCompat
import com.oliver.loqin.util.RelativeTimeFormatter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.oliver.loqin.util.ProtectionFeedback
import com.oliver.loqin.util.ProtectionChangePolicy
import com.oliver.loqin.util.ProtectionChangeGate
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.util.AppBlockSafety

class InAppRulesActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_FOCUS_PACKAGE = "extra_focus_package"
        const val EXTRA_PROFILE_NAME = "extra_profile_name"
    }

    private lateinit var container: LinearLayout
    private lateinit var scroll: NestedScrollView
    private lateinit var modeToggle: MaterialButtonToggleGroup
    private lateinit var modeBlockButton: MaterialButton
    private lateinit var modeAllowButton: MaterialButton
    private lateinit var modeSummary: TextView
    private var updatingModeUi = false

    private data class Surface(
        val labelRes: Int,
        val prefKey: String?,
        val surfaceKey: String?,
        val statusRes: Int
    )

    private data class AppGroup(
        val titleRes: Int,
        val packageName: String,
        val surfaces: List<Surface>
    )

    private data class YouTubeVariantInfo(
        val packageName: String,
        val label: String,
        val versionName: String?,
        val isInstalled: Boolean,
        val isEnabled: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_app_rules)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        scroll = findViewById(R.id.scrollContent)
        container = findViewById(R.id.appContainer)
        modeToggle = findViewById(R.id.toggleInAppRuleMode)
        modeBlockButton = findViewById(R.id.btnInAppModeBlock)
        modeAllowButton = findViewById(R.id.btnInAppModeAllow)
        modeSummary = findViewById(R.id.tvInAppRuleModeSummary)


        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        toolbar.setBackgroundColor(AccentColor.getToolbarBackgroundColor(this))
        toolbar.subtitle = getString(R.string.in_app_rules_profile_subtitle, currentProfile())
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        setupModeToggle()
        render()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_in_app_rules, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_in_app_info -> {
                showInAppRulesInfo()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) render()
    }

    private fun showInAppRulesInfo() {
        showLoqInInfoDialog(
            title = getString(R.string.in_app_rules_info_title),
            rows = listOf(
                LoqInInfoRow(
                    label = getString(R.string.in_app_rules_info_title),
                    value = getString(R.string.in_app_rules_info_body),
                ),
            ),
        )
    }

    private var pendingInAppSelections: Map<String, Boolean> = emptyMap()

    private fun render() {
        CustomAccentApplier.applyIfNeeded(this)
        pendingInAppSelections = ProtectionChangeGate.pendingInAppSelections(this, currentProfile())
        container.removeAllViews()
        val focusPackage = intent.getStringExtra(EXTRA_FOCUS_PACKAGE).orEmpty()
        var focusView: View? = null

        val visibleGroups = groups()
            .map { it to groupStatus(it) }
            .sortedBy { (_, status) -> if (status == AppInstallStatus.NOT_INSTALLED) 1 else 0 }
        updateModeUi()
        if (visibleGroups.isEmpty()) {
            container.addView(emptyState())
            return
        }

        visibleGroups.forEach { (group, status) ->
            val section = buildGroupSection(group, status)
            container.addView(section)
            if (group.packageName == focusPackage) focusView = section
        }

        focusView?.let { targetView ->
            targetView.post {
                targetView.requestFocus()
                scroll.smoothScrollTo(0, targetView.top)
            }
        }
    }

    private fun isInAppAllowMode(): Boolean = InAppRuleStore.isAllowMode(this, currentProfile())

    private fun ruleSwitchText(surfaceLabel: String): String =
        getString(
            if (isInAppAllowMode()) R.string.in_app_rule_allow_surface_fmt else R.string.in_app_rule_block_surface_fmt,
            surfaceLabel
        )

    private fun setSurfaceRuleForMode(surfaceKey: String, checked: Boolean) {
        if (isInAppAllowMode()) {
            SurfaceLimitStore.clear(this, currentProfile(), surfaceKey)
        } else if (checked) {
            SurfaceLimitStore.setRule(this, currentProfile(), surfaceKey, -1)
        } else {
            SurfaceLimitStore.clear(this, currentProfile(), surfaceKey)
        }
    }

    private fun setupModeToggle() {
        modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || updatingModeUi) return@addOnButtonCheckedListener
            if (EditingLockGuard.isLocked(this)) {
                updateModeUi()
                findViewById<View>(android.R.id.content)
                    .showWarnPill(R.string.rules_tighten_only_active_message)
                return@addOnButtonCheckedListener
            }

            val mode = if (checkedId == R.id.btnInAppModeAllow) {
                InAppRuleStore.MODE_ALLOW_SELECTED
            } else {
                InAppRuleStore.MODE_BLOCK_SELECTED
            }
            if (mode == InAppRuleStore.getMode(this, currentProfile())) {
                updateModeUi()
                return@addOnButtonCheckedListener
            }

            if (mode == InAppRuleStore.MODE_ALLOW_SELECTED) {
                confirmAllowSelectedMode()
            } else {
                applyRuleMode(mode)
            }
        }
    }

    /**
     * After enabling an in-app rule for an app that is not whole-app managed, offer to block the
     * entire app. The whole-app block goes through the protection change gate, so it queues while
     * protection is active instead of applying instantly.
     */
    private fun appLabel(packageName: String): String = runCatching {
        val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                android.content.pm.PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun maybeOfferWholeAppBlock(packageName: String, label: String) {
        val profile = currentProfile()
        if (profile.isBlank() || packageName.isBlank()) return
        if (AppBlockSafety.isAlwaysExcluded(this, packageName)) return
        val allowMode = ProfileRuleModeStore.isAllowMode(this, profile)
        val selected = ProfileStore.getSelectedForProfileMode(this, profile)
        if (packageName in selected) return

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.in_app_block_entire_title, label))
            .setIcon(R.drawable.apps_24)
            .setMessage(getString(R.string.in_app_block_entire_message, label))
            .setNegativeButton(R.string.in_app_block_entire_later, null)
            .setPositiveButton(R.string.in_app_block_entire_confirm) { _, _ ->
                when (ProtectionChangeGate.requestAppSelection(
                    context = this,
                    profile = profile,
                    allowMode = allowMode,
                    original = selected,
                    requested = selected + packageName,
                )) {
                    ProtectionChangePolicy.Result.APPLIED -> Unit

                    ProtectionChangePolicy.Result.QUEUED -> ProtectionFeedback.showQueued(this)

                    ProtectionChangePolicy.Result.DENIED -> findViewById<View>(android.R.id.content)
                        .showWarnPill(R.string.rules_tighten_only_active_message)
                }
                render()
            }
            .showAccented()
    }

    private fun confirmAllowSelectedMode() {
        updateModeUi()
        val installedSurfaces = groups()
            .filter { group ->
                group.surfaces.any { surface ->
                    surface.prefKey?.let { key ->
                        InAppRuleStore.isRuleSelected(this, currentProfile(), key)
                    } == true
                }
            }
            .flatMap { it.surfaces }
            .filter { it.prefKey != null }
        val allowedCount = installedSurfaces.count { surface ->
            InAppRuleStore.isRuleSelected(this, currentProfile(), surface.prefKey.orEmpty())
        }
        val blockedCount = (installedSurfaces.size - allowedCount).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.in_app_rule_allow_confirm_title)
            .setMessage(
                getString(
                    R.string.in_app_rule_allow_confirm_body_counted,
                    resources.getQuantityString(
                        R.plurals.in_app_rule_allow_blocked_count,
                        blockedCount,
                        blockedCount,
                    ),
                    resources.getQuantityString(
                        R.plurals.in_app_rule_allow_allowed_count,
                        allowedCount,
                        allowedCount,
                    ),
                )
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.in_app_rule_allow_confirm_action) { _, _ ->
                applyRuleMode(InAppRuleStore.MODE_ALLOW_SELECTED)
            }
            .show()
            .styleLoqInDialogButtons()
    }

    private fun applyRuleMode(mode: String) {
        InAppRuleStore.setMode(this, currentProfile(), mode)
        BlockingRuntime.ensureRunning(this)
        render()
    }

    private fun updateModeUi() {
        val allowMode = isInAppAllowMode()
        val selectedId = if (allowMode) R.id.btnInAppModeAllow else R.id.btnInAppModeBlock
        val readOnly = EditingLockGuard.isLocked(this)

        updatingModeUi = true
        try {
            if (modeToggle.checkedButtonId != selectedId) {
                modeToggle.check(selectedId)
            }
            // Stay tappable (dimmed): denied taps warn via pill instead of doing nothing.
            modeToggle.isEnabled = true
            modeBlockButton.isEnabled = true
            modeAllowButton.isEnabled = true
            modeToggle.alpha = if (readOnly) 0.62f else 1f
            modeSummary.setText(
                if (allowMode) R.string.in_app_rule_mode_allow_summary
                else R.string.in_app_rule_mode_block_summary
            )
            SegmentedToggleUi.apply(
                this,
                listOf(modeBlockButton, modeAllowButton),
                selectedId,
            )
        } finally {
            updatingModeUi = false
        }
    }

    private fun detectYouTubeVariants(): List<YouTubeVariantInfo> {
        val candidates = listOf(
            "app.morphe.android.youtube" to "Morphe",
            "app.revanced.android.youtube" to "ReVanced",
            "com.google.android.youtube" to "Official",
        )
        return candidates.mapNotNull { (pkg, label) ->
            runCatching {
                val ai = packageManager.getApplicationInfo(pkg, 0)
                val pi = packageManager.getPackageInfo(pkg, 0)
                YouTubeVariantInfo(
                    packageName = pkg,
                    label = label,
                    versionName = pi.versionName,
                    isInstalled = true,
                    isEnabled = ai.enabled
                )
            }.getOrNull()
        }
    }

    private fun groupStatus(group: AppGroup): AppInstallStatus {
        if (group.packageName != "com.google.android.youtube") return getAppStatus(group.packageName)
        val variants = detectYouTubeVariants()
        return when {
            variants.any { it.isEnabled } -> AppInstallStatus.INSTALLED
            variants.any { it.isInstalled } -> AppInstallStatus.DISABLED
            else -> AppInstallStatus.NOT_INSTALLED
        }
    }

    private fun buildGroupSection(group: AppGroup, status: AppInstallStatus): View {
        val isYouTube = group.packageName == "com.google.android.youtube"
        val installed = status == AppInstallStatus.INSTALLED

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Header: app icon + name, like a website rule's favicon; the right side holds either
        // why the app can't be used (status pill) or YouTube's extra Shorts setup link.
        val header = layoutInflater.inflate(R.layout.item_website_section_header, section, false)
        header.findViewById<TextView>(R.id.tvHeader).apply {
            text = getString(group.titleRes)
            val icon = installedPackage(group)?.let { pkg ->
                runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()
            }
            if (icon != null) {
                icon.setBounds(0, 0, dp(20), dp(20))
                setCompoundDrawablesRelative(icon, null, null, null)
                compoundDrawablePadding = dp(8)
            }
            gravity = Gravity.CENTER_VERTICAL
        }
        header.findViewById<TextView>(R.id.tvHeaderAction).apply {
            when {
                !installed -> {
                    text = getString(
                        if (status == AppInstallStatus.DISABLED) R.string.in_app_status_disabled
                        else R.string.in_app_status_not_installed
                    )
                    isClickable = false
                    isFocusable = false
                    background = GradientDrawable().apply {
                        cornerRadius = dp(99).toFloat()
                        setColor(ColorUtils.setAlphaComponent(onSurfaceColor(), 0x14))
                    }
                    setTextColor(ColorUtils.setAlphaComponent(onSurfaceColor(), 0xAA))
                    setPadding(dp(10), dp(4), dp(10), dp(4))
                    textSize = 11.5f
                    visibility = View.VISIBLE
                }
                isYouTube -> {
                    text = getString(R.string.in_app_youtube_native_limit_compact_title)
                    setOnClickListener { showYouTubeNativeLimitGuide() }
                    visibility = View.VISIBLE
                }
                else -> visibility = View.GONE
            }
        }
        section.addView(header)

        appUpdateWarning(group)?.let { section.addView(buildNoteRow(it, R.drawable.info_24)) }

        val grid = TileGridLayout(this).apply {
            columns = RuleTile.COMPACT_COLUMNS
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        group.surfaces.forEach { surface ->
            grid.addView(buildSurfaceTile(surface, group.packageName, grid, installed).root)
        }
        section.addView(grid)
        return section
    }

    /** The package actually serving this group (for YouTube, an enabled patched build wins). */
    private fun installedPackage(group: AppGroup): String? {
        val candidates = if (group.packageName == "com.google.android.youtube") {
            listOf("app.morphe.android.youtube", "app.revanced.android.youtube", group.packageName)
        } else {
            listOf(group.packageName)
        }
        return candidates.firstOrNull {
            runCatching { packageManager.getApplicationInfo(it, 0).enabled }.getOrDefault(false)
        }
    }

    private fun showYouTubeNativeLimitGuide() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.in_app_youtube_native_limit_guide_title)
            .setMessage(R.string.in_app_youtube_native_limit_guide_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.in_app_youtube_open_youtube) { _, _ ->
                val ytLaunchIntent = listOf("app.morphe.android.youtube", "app.revanced.android.youtube", "com.google.android.youtube")
                    .firstNotNullOfOrNull { PackageLaunchIntentCompat.getLaunchIntent(this, it) }
                if (ytLaunchIntent != null) {
                    startActivity(ytLaunchIntent)
                } else {
                    PackageLaunchIntentCompat.getLaunchIntent(this, "com.google.android.youtube")?.let { startActivity(it) }
                }
            }
            .show()
            .styleLoqInDialogButtons()
    }

    private enum class AppInstallStatus {
        INSTALLED,
        DISABLED,
        NOT_INSTALLED
    }

    private fun getAppStatus(packageName: String): AppInstallStatus {
        val packagesToCheck = if (packageName == "com.google.android.youtube") {
            listOf("app.morphe.android.youtube", "app.revanced.android.youtube", packageName)
        } else {
            listOf(packageName)
        }

        for (pkg in packagesToCheck) {
            val ai = runCatching { packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
            if (ai != null && ai.enabled) {
                return AppInstallStatus.INSTALLED
            }
        }
        for (pkg in packagesToCheck) {
            val ai = runCatching { packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
            if (ai != null) {
                return AppInstallStatus.DISABLED
            }
        }
        return AppInstallStatus.NOT_INSTALLED
    }

    /** A quiet caption under a section heading, aligned with the heading text. */
    private fun buildNoteRow(message: String, iconRes: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
                bottomMargin = dp(6)
            }
            val secondary = MaterialColors.getColor(this, android.R.attr.textColorSecondary)
            addView(ImageView(this@InAppRulesActivity).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(secondary)
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(6) }
            })
            addView(TextView(this@InAppRulesActivity).apply {
                text = message
                textSize = 12.5f
                setTextColor(secondary)
            })
        }
    }

    private fun appUpdateWarning(group: AppGroup): String? {
        val pkg = installedPackage(group) ?: return null
        val info = runCatching { packageManager.getPackageInfo(pkg, 0) }.getOrNull() ?: return null
        val version = info.versionName.orEmpty().ifBlank { return null }
        val lastDetected = InAppDetectionStore.lastForAny(this, group.surfaces.mapNotNull { it.surfaceKey })
        if (lastDetected <= 0L || info.lastUpdateTime <= lastDetected) return null
        return getString(R.string.in_app_app_diagnostic_stale, version)
    }

    private fun surfaceIconRes(surfaceKey: String?): Int = when (surfaceKey) {
        "yt:shorts", "ig:reels", "snap:spotlight", "fb:reels" -> R.drawable.play_arrow_24
        "yt:subscriptions", "x:foryou", "fb:marketplace" -> R.drawable.apps_24
        "yt:you", "snap:following" -> R.drawable.account_box_24
        "yt:miniplayer", "x:grok" -> R.drawable.layers_24
        "yt:pip" -> R.drawable.share_24
        "ig:search", "x:search" -> R.drawable.search_24
        "ig:stories", "snap:stories" -> R.drawable.photo_camera_24
        "x:notifications" -> R.drawable.notifications_24
        "snap:map" -> R.drawable.location_on_24
        else -> R.drawable.apps_24
    }

    private fun buildSurfaceTile(surface: Surface, packageName: String, parent: ViewGroup, installed: Boolean): RuleTile {
        val tile = RuleTile.inflate(parent, compact = true)
        val prefKey = surface.prefKey
        val readOnly = EditingLockGuard.isLocked(this)
        val currentChecked = prefKey?.let { readProfileBool(it) } ?: false
        val pendingSelected = prefKey?.let { pendingInAppSelections[it] }
        val canToggleWhileLocked = prefKey != null && run {
            if (ProtectionChangeGate.isEditingUnlocked(this)) return@run true
            val direction = ProtectionChangePolicy.inAppDirection(
                allowMode = isInAppAllowMode(),
                currentSelected = currentChecked,
                requestedSelected = !currentChecked,
            )
            ProtectionChangeGate.decision(this, direction) != ProtectionChangePolicy.Decision.DENY
        }
        if (currentChecked && !readOnly && pendingSelected == null) {
            surface.surfaceKey?.let { setSurfaceRuleForMode(it, checked = true) }
        }
        val shown = pendingSelected ?: currentChecked
        // The mode card already says Block/Allow selected, so the tile is just the section's name.
        val label = getString(surface.labelRes)
        val onTap: (() -> Unit)? = prefKey?.let { key ->
            { onSurfaceTapped(tile, surface, packageName, key) }
        }

        tile.setSymbol(surfaceIconRes(surface.surfaceKey))
        tile.setText(label)
        tile.root.contentDescription = ruleSwitchText(label)
        // Tap the tile to pick it, with the same check badge as a picked app.
        tile.setToggle(null)
        tile.setState(on = shown, pending = pendingSelected != null)
        tile.setDimmed(prefKey == null || (readOnly && !canToggleWhileLocked))
        // Not installed: the rule still applies once it is, so keep the name readable.
        if (!installed && !shown) tile.icon.alpha = 0.6f
        tile.root.setOnClickListener(onTap?.let { tap -> View.OnClickListener { tap() } })
        return tile
    }

    private fun onSurfaceTapped(tile: RuleTile, surface: Surface, packageName: String, prefKey: String) {
        val before = readProfileBool(prefKey)
        val checked = !(pendingInAppSelections[prefKey] ?: before)
        when (ProtectionChangeGate.requestInAppSelection(
            context = this,
            profile = currentProfile(),
            packageName = packageName,
            baseKey = prefKey,
            surfaceKey = surface.surfaceKey,
            allowMode = isInAppAllowMode(),
            currentSelected = before,
            requestedSelected = checked,
        )) {
            ProtectionChangePolicy.Result.APPLIED -> {
                BlockingRuntime.ensureRunning(this)
                if (checked) {
                    maybeOfferWholeAppBlock(packageName, appLabel(packageName))
                }
                tile.root.post { render() }
            }

            ProtectionChangePolicy.Result.QUEUED -> {
                ProtectionFeedback.showQueued(this)
                tile.root.post { render() }
            }

            ProtectionChangePolicy.Result.DENIED -> {
                if (EditingLockGuard.isLocked(this)) {
                    findViewById<View>(android.R.id.content)
                        .showWarnPill(R.string.rules_tighten_only_active_message)
                }
                tile.root.post { render() }
            }
        }
    }

    private fun groups(): List<AppGroup> = listOf(
        AppGroup(
            R.string.in_app_rules_youtube,
            "com.google.android.youtube",
            listOf(
                Surface(R.string.in_app_surface_shorts_label, BlockingToggleKeys.KEY_BLOCK_YT_SHORTS, "yt:shorts", R.string.in_app_status_experimental),
                Surface(R.string.in_app_surface_subscriptions_label, BlockingToggleKeys.KEY_BLOCK_YT_SUBSCRIPTIONS, "yt:subscriptions", R.string.in_app_status_supported),
                Surface(R.string.in_app_surface_you_label, BlockingToggleKeys.KEY_BLOCK_YT_YOU, "yt:you", R.string.in_app_status_supported),
                Surface(R.string.in_app_surface_mini_player_label, BlockingToggleKeys.KEY_BLOCK_YT_MINI_PLAYER, "yt:miniplayer", R.string.in_app_status_experimental),
                Surface(R.string.in_app_surface_pip_label, BlockingToggleKeys.KEY_BLOCK_YT_PIP, "yt:pip", R.string.in_app_status_experimental)
            )
        ),
        AppGroup(
            R.string.in_app_rules_instagram,
            "com.instagram.android",
            listOf(
                Surface(R.string.in_app_surface_reels_label, BlockingToggleKeys.KEY_BLOCK_IG_REELS, "ig:reels", R.string.in_app_status_experimental),
                Surface(R.string.in_app_surface_search_label, BlockingToggleKeys.KEY_BLOCK_IG_SEARCH, "ig:search", R.string.in_app_status_experimental),
                Surface(R.string.in_app_surface_stories_label, BlockingToggleKeys.KEY_BLOCK_IG_STORIES, "ig:stories", R.string.in_app_status_experimental)
            )
        ),
        AppGroup(
            R.string.in_app_rules_x,
            "com.twitter.android",
            listOf(
                Surface(R.string.in_app_surface_home_label, BlockingToggleKeys.KEY_BLOCK_X_HOME, "x:foryou", R.string.in_app_status_experimental),
                Surface(R.string.in_app_surface_search_label, BlockingToggleKeys.KEY_BLOCK_X_SEARCH, "x:search", R.string.in_app_status_experimental),
                Surface(R.string.in_app_surface_grok_label, BlockingToggleKeys.KEY_BLOCK_X_GROK, "x:grok", R.string.in_app_status_experimental),
                Surface(R.string.in_app_surface_notifications_label, BlockingToggleKeys.KEY_BLOCK_X_NOTIFICATIONS, "x:notifications", R.string.in_app_status_experimental)
            )
        ),
        AppGroup(
            R.string.in_app_rules_snapchat,
            "com.snapchat.android",
            listOf(
                Surface(R.string.in_app_surface_spotlight_label, BlockingToggleKeys.KEY_BLOCK_SNAP_SPOTLIGHT, "snap:spotlight", R.string.in_app_status_supported),
                Surface(R.string.in_app_surface_stories_label, BlockingToggleKeys.KEY_BLOCK_SNAP_STORIES, "snap:stories", R.string.in_app_status_supported),
                Surface(R.string.in_app_surface_map_label, BlockingToggleKeys.KEY_BLOCK_SNAP_MAP, "snap:map", R.string.in_app_status_supported),
                Surface(R.string.in_app_surface_following_label, BlockingToggleKeys.KEY_BLOCK_SNAP_FOLLOWING, "snap:following", R.string.in_app_status_supported)
            )
        ),
        AppGroup(
            R.string.in_app_rules_facebook,
            "com.facebook.katana",
            listOf(
                Surface(R.string.in_app_surface_reels_label, BlockingToggleKeys.KEY_BLOCK_FB_REELS, "fb:reels", R.string.in_app_status_experimental),
                Surface(R.string.in_app_surface_marketplace_label, BlockingToggleKeys.KEY_BLOCK_FB_MARKETPLACE, "fb:marketplace", R.string.in_app_status_experimental)
            )
        )
    )

    private fun emptyState(): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            setCardBackgroundColor(ContextCompat.getColor(this@InAppRulesActivity, R.color.foqos_surface))
            // Flat tonal card: no outline, same 28dp shape as LoqIn.Card.
            strokeWidth = 0
            radius = dp(28).toFloat()
            cardElevation = 0f
        }
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
            addView(ImageView(this@InAppRulesActivity).apply {
                setImageResource(R.drawable.apps_24)
                imageTintList = ColorStateList.valueOf(AccentColor.getAccentColorInt(this@InAppRulesActivity))
                contentDescription = null
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            })
            addView(TextView(this@InAppRulesActivity).apply {
                text = getString(R.string.in_app_rules_empty_installed)
                setTypeface(typeface, Typeface.BOLD)
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(onSurfaceColor())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(10) }
            })
            addView(TextView(this@InAppRulesActivity).apply {
                text = getString(R.string.in_app_rules_empty_installed_summary)
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(MaterialColors.getColor(this@InAppRulesActivity, android.R.attr.textColorSecondary, 0))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4) }
            })
        })
        return card
    }

    private fun currentProfile(): String =
        intent.getStringExtra(EXTRA_PROFILE_NAME)?.trim()?.takeIf { it.isNotBlank() }
            ?: ProfileStore.getCurrent(this) ?: "default"

    private fun readProfileBool(baseKey: String): Boolean {
        return InAppRuleStore.isRuleSelected(this, currentProfile(), baseKey)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun onSurfaceColor(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0)
}
