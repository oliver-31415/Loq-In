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
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.ui.dialog.LoqInInfoRow
import com.oliver.loqin.ui.dialog.showLoqInInfoDialog
import com.oliver.loqin.ui.dialog.styleLoqInDialogButtons
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

        buildPageTop()
        val (absent, present) = visibleGroups.partition { (_, status) -> status == AppInstallStatus.NOT_INSTALLED }
        present.forEach { (group, status) ->
            val card = buildAppCardView(group, status)
            container.addView(card)
            if (group.packageName == focusPackage) focusView = card
        }
        if (absent.isNotEmpty()) {
            if (absent.any { it.first.packageName == focusPackage }) notInstalledExpanded = true
            container.addView(buildNotInstalledFold(absent.map { it.first }))
            if (notInstalledExpanded) {
                absent.forEach { (group, status) ->
                    val card = buildAppCardView(group, status)
                    container.addView(card)
                    if (group.packageName == focusPackage) focusView = card
                }
            }
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

    private fun surfaceIconRes(surfaceKey: String?): Int = when (surfaceKey) {
        "yt:shorts", "ig:reels", "snap:spotlight", "fb:reels" -> R.drawable.play_arrow_24
        "yt:subscriptions" -> R.drawable.list_alt_24
        "x:foryou" -> R.drawable.loqin_home_nav_24
        "fb:marketplace" -> R.drawable.dashboard_24
        "yt:you", "snap:following" -> R.drawable.account_box_24
        "yt:miniplayer" -> R.drawable.layers_24
        "x:grok" -> R.drawable.star_24
        "yt:pip" -> R.drawable.open_in_new_24
        "ig:search", "x:search" -> R.drawable.search_24
        "ig:stories", "snap:stories" -> R.drawable.photo_camera_24
        "x:notifications" -> R.drawable.notifications_24
        "snap:map" -> R.drawable.location_on_24
        else -> R.drawable.apps_24
    }

    // ===== New In-app card page (replaces buildGroupSection / buildSurfaceTile) =====

    private var notInstalledExpanded = false

    private data class Freshness(val version: String?, val stale: Boolean, val everDetected: Boolean)

    private fun freshness(group: AppGroup): Freshness {
        val pkg = installedPackage(group) ?: return Freshness(null, false, false)
        val info = runCatching { packageManager.getPackageInfo(pkg, 0) }.getOrNull() ?: return Freshness(null, false, false)
        val version = info.versionName?.takeIf { it.isNotBlank() }
        val lastDetected = InAppDetectionStore.lastForAny(this, group.surfaces.mapNotNull { it.surfaceKey })
        return Freshness(version, lastDetected > 0L && info.lastUpdateTime > lastDetected, lastDetected > 0L)
    }

    private fun shownState(surface: Surface): Boolean {
        val key = surface.prefKey ?: return false
        return pendingInAppSelections[key] ?: readProfileBool(key)
    }

    private fun surfaceDescriptionRes(surfaceKey: String?): Int = when (surfaceKey) {
        "yt:shorts" -> R.string.in_app_desc_yt_shorts
        "yt:subscriptions" -> R.string.in_app_desc_yt_subs
        "yt:you" -> R.string.in_app_desc_yt_you
        "yt:miniplayer" -> R.string.in_app_desc_yt_mini
        "yt:pip" -> R.string.in_app_desc_yt_pip
        "ig:reels" -> R.string.in_app_desc_ig_reels
        "ig:search" -> R.string.in_app_desc_ig_search
        "ig:stories" -> R.string.in_app_desc_ig_stories
        "x:foryou" -> R.string.in_app_desc_x_home
        "x:search" -> R.string.in_app_desc_x_search
        "x:grok" -> R.string.in_app_desc_x_grok
        "x:notifications" -> R.string.in_app_desc_x_notif
        "snap:spotlight" -> R.string.in_app_desc_snap_spotlight
        "snap:stories" -> R.string.in_app_desc_snap_stories
        "snap:map" -> R.string.in_app_desc_snap_map
        "snap:following" -> R.string.in_app_desc_snap_following
        "fb:reels" -> R.string.in_app_desc_fb_reels
        "fb:marketplace" -> R.string.in_app_desc_fb_marketplace
        else -> 0
    }

    private val shortVideoKeys = setOf("yt:shorts", "ig:reels", "snap:spotlight", "fb:reels")

    /** Lock banner and the "short videos in every app" master switch, above the app cards. */
    private fun buildPageTop() {
        if (EditingLockGuard.isLocked(this)) {
            container.addView(InAppRuleViews.lockBanner(this, getString(R.string.in_app_locked_banner)))
        }
        val shortVideo = groups().flatMap { g -> g.surfaces.filter { it.surfaceKey in shortVideoKeys }.map { g to it } }
        if (shortVideo.isEmpty()) return
        val onCount = shortVideo.count { (_, s) -> shownState(s) }
        val state = when (onCount) {
            shortVideo.size -> MasterState.ON
            0 -> MasterState.OFF
            else -> MasterState.MIXED
        }
        val stateText = when (state) {
            MasterState.ON -> getString(R.string.in_app_master_state_all)
            MasterState.OFF -> getString(R.string.in_app_master_state_none)
            MasterState.MIXED -> getString(R.string.in_app_master_state_mixed_fmt, onCount, shortVideo.size)
        }
        val allow = isInAppAllowMode()
        container.addView(
            InAppRuleViews.masterCard(
                this,
                getString(if (allow) R.string.in_app_master_allow_title else R.string.in_app_master_block_title),
                getString(R.string.in_app_master_sub_fmt, stateText),
                state,
                stateText,
                EditingLockGuard.isLocked(this),
            ) {
                // Mixed or off → turn every short-video section on; all on → turn them off.
                val target = state != MasterState.ON
                val anchor = container
                shortVideo.forEach { (g, s) ->
                    val key = s.prefKey ?: return@forEach
                    if (shownState(s) != target) toggleSurface(s, g.packageName, key, anchor, requested = target, rerender = false, offerWholeApp = false)
                }
                anchor.post { render() }
            }
        )
    }

    private fun buildAppCardView(group: AppGroup, status: AppInstallStatus): View {
        val allow = isInAppAllowMode()
        val readOnly = EditingLockGuard.isLocked(this)
        val installed = status == AppInstallStatus.INSTALLED
        val appName = getString(group.titleRes)
        val icon = installedPackage(group)?.let { pkg -> runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull() }
        val countable = group.surfaces.filter { it.prefKey != null }
        val onSurfaces = countable.filter { shownState(it) }

        val rows = group.surfaces.map { surface ->
            val prefKey = surface.prefKey
            val current = prefKey?.let { readProfileBool(it) } ?: false
            val pending = prefKey?.let { pendingInAppSelections[it] } != null
            val on = shownState(surface)
            val canToggleWhileLocked = prefKey != null && run {
                if (ProtectionChangeGate.isEditingUnlocked(this)) return@run true
                val direction = ProtectionChangePolicy.inAppDirection(allowMode = allow, currentSelected = current, requestedSelected = !current)
                ProtectionChangeGate.decision(this, direction) != ProtectionChangePolicy.Decision.DENY
            }
            if (current && !readOnly && !pending) surface.surfaceKey?.let { setSurfaceRuleForMode(it, checked = true) }
            val (tag, tagAccent) = when {
                pending -> getString(R.string.in_app_tag_pending) to false
                allow && on -> getString(R.string.in_app_tag_allowed) to true
                allow && !on -> getString(R.string.in_app_tag_blocked) to false
                on -> getString(R.string.in_app_tag_blocked) to true
                else -> null to false
            }
            val lockedDisabled = prefKey == null || (readOnly && !canToggleWhileLocked)
            val spoken = listOfNotNull(tag ?: getString(R.string.state_off), getString(R.string.in_app_row_locked).takeIf { lockedDisabled })
                .joinToString(" · ")
            SectionRowModel(
                key = surface.surfaceKey ?: prefKey ?: getString(surface.labelRes),
                iconRes = surfaceIconRes(surface.surfaceKey),
                title = getString(surface.labelRes),
                description = surfaceDescriptionRes(surface.surfaceKey).takeIf { it != 0 }?.let { getString(it) },
                on = on,
                tag = tag,
                tagAccent = tagAccent,
                pending = pending,
                lockedDisabled = lockedDisabled,
                stateDescription = spoken,
            )
        }

        val labels = onSurfaces.map { getString(it.labelRes) }
        val outcome = when {
            !installed -> null
            allow && labels.isEmpty() -> getString(R.string.in_app_outcome_allow_none)
            allow -> getString(R.string.in_app_outcome_allow_fmt, labels.joinToString(", "), countable.size - labels.size)
            labels.size == 1 -> getString(R.string.in_app_outcome_block_one_fmt, labels.first())
            labels.size > 1 -> getString(R.string.in_app_outcome_block_many_fmt, labels.size)
            else -> null
        }
        val fresh = freshness(group)
        val footnote = when {
            fresh.stale && fresh.version != null -> getString(R.string.in_app_footnote_stale_fmt, appName, fresh.version)
            fresh.everDetected && fresh.version != null -> getString(R.string.in_app_footnote_checked_fmt, appName, fresh.version)
            else -> null
        }
        val pillText = when (status) {
            AppInstallStatus.NOT_INSTALLED -> getString(R.string.in_app_status_not_installed)
            AppInstallStatus.DISABLED -> getString(R.string.in_app_status_disabled)
            else -> getString(if (allow) R.string.in_app_count_allowed_fmt else R.string.in_app_count_blocked_fmt, onSurfaces.size, countable.size)
        }
        val model = AppCardModel(
            packageName = group.packageName,
            appName = appName,
            icon = icon,
            pillText = pillText,
            pillAccent = installed && onSurfaces.isNotEmpty(),
            outcome = outcome,
            showSeeWhere = installed && outcome != null,
            seeWhereLabel = getString(R.string.in_app_see_where),
            seeWhereCd = getString(R.string.in_app_see_where_cd_fmt, appName),
            rows = rows,
            extraShorts = if (group.packageName == "com.google.android.youtube" && installed) {
                getString(R.string.in_app_youtube_native_limit_compact_title) to getString(R.string.in_app_extra_shorts_sub)
            } else null,
            footnote = footnote,
            footnoteWarning = fresh.stale,
        )
        val bySurfaceKey = group.surfaces.associateBy { it.surfaceKey ?: it.prefKey ?: getString(it.labelRes) }
        return InAppRuleViews.appCard(
            this,
            model,
            onRowClick = { key, rowView ->
                val surface = bySurfaceKey[key] ?: return@appCard
                val prefKey = surface.prefKey ?: return@appCard
                toggleSurface(surface, group.packageName, prefKey, rowView)
            },
            onSeeWhere = {
                val keys = onSurfaces.mapNotNull { it.surfaceKey }.toSet()
                val all = countable.mapNotNull { it.surfaceKey }.toSet()
                InAppWhereSheet.show(
                    this, appName, icon, installedPackage(group) ?: group.packageName,
                    blockedSurfaceKeys = if (allow) all - keys else keys,
                    allowMode = allow,
                    allowedSurfaceKeys = if (allow) keys else emptySet(),
                )
            },
            onExtraShorts = { showYouTubeNativeLimitGuide() },
        )
    }

    private fun buildNotInstalledFold(absent: List<AppGroup>): View {
        val rulesSet = absent.sumOf { g -> g.surfaces.count { shownState(it) } }
        val setText = if (rulesSet == 0) getString(R.string.in_app_not_installed_none)
        else resources.getQuantityString(R.plurals.in_app_not_installed_set, rulesSet, rulesSet)
        return InAppRuleViews.notInstalledRow(
            this,
            absent.map { getString(it.titleRes).take(1) },
            resources.getQuantityString(R.plurals.in_app_not_installed_title, absent.size, absent.size),
            getString(R.string.in_app_not_installed_sub_fmt, setText),
            notInstalledExpanded,
            getString(if (notInstalledExpanded) R.string.a11y_state_expanded else R.string.a11y_state_collapsed),
        ) {
            notInstalledExpanded = !notInstalledExpanded
            render()
        }
    }

    /**
     * Applies one section change through the protection gate. [requested] null flips the shown
     * state; the master switch passes an explicit target and renders once at the end.
     */
    private fun toggleSurface(
        surface: Surface,
        packageName: String,
        prefKey: String,
        anchor: View,
        requested: Boolean? = null,
        rerender: Boolean = true,
        offerWholeApp: Boolean = true,
    ) {
        val before = readProfileBool(prefKey)
        val checked = requested ?: !(pendingInAppSelections[prefKey] ?: before)
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
                if (checked && offerWholeApp) maybeOfferWholeAppBlock(packageName, appLabel(packageName))
            }
            ProtectionChangePolicy.Result.QUEUED -> ProtectionFeedback.showQueued(this)
            ProtectionChangePolicy.Result.DENIED -> {
                if (EditingLockGuard.isLocked(this)) {
                    findViewById<View>(android.R.id.content).showWarnPill(R.string.rules_tighten_only_active_message)
                }
            }
        }
        if (rerender) anchor.post { render() }
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
