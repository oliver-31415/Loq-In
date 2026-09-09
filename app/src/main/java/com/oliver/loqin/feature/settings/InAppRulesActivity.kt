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

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.widget.NestedScrollView
import com.oliver.loqin.R
import com.oliver.loqin.blocking.BlockingRuntime
import com.oliver.loqin.data.prefs.BlockingToggleKeys
import com.oliver.loqin.data.prefs.IgnoredUsageAppsStore
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
import com.oliver.loqin.util.ProtectionEditPolicy
import com.oliver.loqin.util.RelativeTimeFormatter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
        val icon: Drawable?,
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
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))
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

    private fun render() {
        CustomAccentApplier.applyIfNeeded(this)
        container.removeAllViews()
        val focusPackage = intent.getStringExtra(EXTRA_FOCUS_PACKAGE).orEmpty()
        var focusView: View? = null

        val visibleGroups = groups()
        updateModeUi()
        if (visibleGroups.isEmpty()) {
            container.addView(emptyState())
            return
        }

        visibleGroups.forEach { group ->
            val card = buildGroupCard(group)
            container.addView(card)
            if (group.packageName == focusPackage) focusView = card
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
                val icon = packageManager.getApplicationIcon(ai)
                YouTubeVariantInfo(
                    packageName = pkg,
                    label = label,
                    versionName = pi.versionName,
                    icon = icon,
                    isInstalled = true,
                    isEnabled = ai.enabled
                )
            }.getOrNull()
        }
    }

    private fun buildGroupCard(group: AppGroup): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            setCardBackgroundColor(ContextCompat.getColor(this@InAppRulesActivity, R.color.foqos_surface))
            strokeColor = ContextCompat.getColor(this@InAppRulesActivity, R.color.foqos_outline_variant)
            strokeWidth = dp(1)
            radius = dp(16).toFloat()
        }

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val isYouTube = group.packageName == "com.google.android.youtube"
        val ytVariants = if (isYouTube) detectYouTubeVariants() else emptyList()
        val enabledYtVariants = ytVariants.filter { it.isEnabled }

        val status = if (isYouTube) {
            when {
                enabledYtVariants.isNotEmpty() -> AppInstallStatus.INSTALLED
                ytVariants.any { it.isInstalled } -> AppInstallStatus.DISABLED
                else -> AppInstallStatus.NOT_INSTALLED
            }
        } else {
            getAppStatus(group.packageName)
        }

        val resolvedTitle = if (isYouTube && enabledYtVariants.isNotEmpty()) {
            val nonOfficial = enabledYtVariants.filter { it.packageName != "com.google.android.youtube" }
            val hasOfficial = enabledYtVariants.any { it.packageName == "com.google.android.youtube" }
            when {
                nonOfficial.isNotEmpty() && hasOfficial ->
                    "YouTube (${nonOfficial.joinToString(" & ") { it.label }} & Official)"
                nonOfficial.isNotEmpty() ->
                    "YouTube (${nonOfficial.joinToString(" & ") { it.label }})"
                else -> getString(group.titleRes)
            }
        } else {
            getString(group.titleRes)
        }

        val resolvedIcon = if (isYouTube && enabledYtVariants.isNotEmpty()) {
            enabledYtVariants.first().icon ?: appIcon(group.packageName)
        } else {
            appIcon(group.packageName)
        }

        // Header Row (Clickable Accordion Dropdown)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(58)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            isFocusable = true
            val outValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }

        // App Icon
        header.addView(ImageView(this).apply {
            if (resolvedIcon != null) {
                setImageDrawable(resolvedIcon)
                imageTintList = null
            } else {
                setImageResource(fallbackIconForPackage(group.packageName))
                val iconTint = if (status == AppInstallStatus.NOT_INSTALLED) {
                    ColorUtils.setAlphaComponent(onSurfaceColor(), 0x77)
                } else {
                    AccentColor.getAccentColorInt(this@InAppRulesActivity)
                }
                imageTintList = ColorStateList.valueOf(iconTint)
            }
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        })

        // Title and Subtitle
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            }
        }
        val tvTitle = TextView(this).apply {
            text = resolvedTitle
            setTypeface(typeface, Typeface.BOLD)
            textSize = 15.5f
            setTextColor(onSurfaceColor())
        }
        val tvSubtitle = TextView(this).apply {
            textSize = 12f
            setTextColor(ColorUtils.setAlphaComponent(onSurfaceColor(), 0x88))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(1) }
        }
        textCol.addView(tvTitle)
        textCol.addView(tvSubtitle)
        header.addView(textCol)

        // Status Badge (unopened chip shows variant/app name without versions)
        val tvStatus = TextView(this).apply {
            text = when {
                isYouTube && enabledYtVariants.isNotEmpty() ->
                    enabledYtVariants.joinToString(" · ") { it.label }
                else -> appDiagnosticLabel(group)
            }
            textSize = 11.5f
            setPadding(dp(8), dp(3), dp(8), dp(3))
            val accent = AccentColor.getAccentColorInt(this@InAppRulesActivity)
            val danger = ContextCompat.getColor(this@InAppRulesActivity, R.color.status_error)
            when (status) {
                AppInstallStatus.NOT_INSTALLED -> {
                    setTextColor(ColorUtils.setAlphaComponent(onSurfaceColor(), 0x99))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(6).toFloat()
                        setColor(ColorUtils.setAlphaComponent(onSurfaceColor(), 0x18))
                    }
                }
                AppInstallStatus.DISABLED -> {
                    setTextColor(danger)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(6).toFloat()
                        setColor(ColorUtils.setAlphaComponent(danger, 0x1E))
                    }
                }
                AppInstallStatus.INSTALLED -> {
                    setTextColor(accent)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(6).toFloat()
                        setColor(ColorUtils.setAlphaComponent(accent, 0x1E))
                    }
                }
            }
        }
        header.addView(tvStatus)

        // Dropdown Chevron
        val ivChevron = ImageView(this).apply {
            setImageResource(R.drawable.keyboard_arrow_down_24)
            imageTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurfaceColor(), 0x90))
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                marginStart = dp(6)
            }
        }
        header.addView(ivChevron)
        cardLayout.addView(header)

        // Subtle Divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(ColorUtils.setAlphaComponent(onSurfaceColor(), 0x14))
            visibility = View.GONE
        }
        cardLayout.addView(divider)

        // Surfaces Container
        val surfacesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(14))
            visibility = View.GONE
        }

        // Recognized variant note
        if (isYouTube && enabledYtVariants.isNotEmpty()) {
            val variantSummary = enabledYtVariants.joinToString(", ") { v ->
                if (v.versionName != null) "${v.label} (v${v.versionName})" else v.label
            }
            val noteRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(ColorUtils.setAlphaComponent(AccentColor.getAccentColorInt(this@InAppRulesActivity), 0x18))
                }
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(6)
                }

                addView(ImageView(this@InAppRulesActivity).apply {
                    setImageResource(R.drawable.check_circle_24)
                    imageTintList = ColorStateList.valueOf(AccentColor.getAccentColorInt(this@InAppRulesActivity))
                    layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) }
                })
                addView(TextView(this@InAppRulesActivity).apply {
                    text = getString(R.string.in_app_recognized_variants, variantSummary)
                    textSize = 12f
                    setTextColor(onSurfaceColor())
                })
            }
            surfacesContainer.addView(noteRow)
        }

        fun updateSubtitle() {
            val isAllow = isInAppAllowMode()
            val activeCount = group.surfaces.count { s ->
                s.prefKey?.let { k -> InAppRuleStore.isRuleSelected(this@InAppRulesActivity, currentProfile(), k) } == true
            }
            val total = group.surfaces.count { it.prefKey != null }
            tvSubtitle.text = if (isAllow) {
                if (activeCount == 0) getString(R.string.in_app_rules_none_active)
                else getString(R.string.in_app_rules_active_summary_allow, activeCount, total)
            } else {
                if (activeCount == 0) getString(R.string.in_app_rules_none_active)
                else getString(R.string.in_app_rules_active_summary_block, activeCount, total)
            }
        }
        updateSubtitle()

        group.surfaces.forEach { surface ->
            val row = buildSurfaceRow(surface, group.packageName, onToggle = { updateSubtitle() })
            surfacesContainer.addView(row)
            if (group.packageName == "com.google.android.youtube" && surface.surfaceKey == "yt:shorts") {
                surfacesContainer.addView(buildYouTubeNativeLimitRow())
            }
        }
        cardLayout.addView(surfacesContainer)

        val focusPackage = intent.getStringExtra(EXTRA_FOCUS_PACKAGE).orEmpty()
        val shouldExpand = focusPackage == group.packageName
        if (shouldExpand) {
            surfacesContainer.visibility = View.VISIBLE
            divider.visibility = View.VISIBLE
            ivChevron.rotation = 180f
        }

        header.setOnClickListener {
            val isExpanded = surfacesContainer.visibility == View.VISIBLE
            val willExpand = !isExpanded
            surfacesContainer.visibility = if (willExpand) View.VISIBLE else View.GONE
            divider.visibility = if (willExpand) View.VISIBLE else View.GONE
            ivChevron.animate().rotation(if (willExpand) 180f else 0f).setDuration(200).start()
        }

        card.addView(cardLayout)
        return card
    }

    private fun buildYouTubeNativeLimitRow(): View {
        val accent = AccentColor.getAccentColorInt(this)
        val rowBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(ColorUtils.setAlphaComponent(accent, 18))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rowBackground
            isClickable = true
            isFocusable = true
            setPadding(dp(10), dp(9), dp(8), dp(9))
            setOnClickListener { showYouTubeNativeLimitGuide() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(7)
                bottomMargin = dp(2)
                marginStart = dp(8)
            }

            addView(ImageView(this@InAppRulesActivity).apply {
                setImageResource(R.drawable.timer_24)
                imageTintList = ColorStateList.valueOf(accent)
                contentDescription = null
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                    marginEnd = dp(10)
                }
            })

            addView(LinearLayout(this@InAppRulesActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@InAppRulesActivity).apply {
                    setText(R.string.in_app_youtube_native_limit_compact_title)
                    setTypeface(typeface, Typeface.BOLD)
                    textSize = 13f
                    setTextColor(onSurfaceColor())
                })
                addView(TextView(this@InAppRulesActivity).apply {
                    setText(R.string.in_app_youtube_native_limit_compact_summary)
                    textSize = 12f
                    setTextColor(onSurfaceColor())
                    alpha = 0.72f
                })
            })

            addView(TextView(this@InAppRulesActivity).apply {
                setText(R.string.in_app_youtube_native_limit_compact_action)
                setTypeface(typeface, Typeface.BOLD)
                textSize = 12f
                setTextColor(accent)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            })
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

    private fun fallbackIconForPackage(packageName: String): Int = when (packageName) {
        "com.google.android.youtube" -> R.drawable.play_arrow_24
        "com.instagram.android" -> R.drawable.photo_camera_24
        "com.twitter.android" -> R.drawable.share_24
        "com.snapchat.android" -> R.drawable.photo_camera_24
        "com.facebook.katana" -> R.drawable.share_24
        "com.zhiliaoapp.musically" -> R.drawable.widget_play_24
        else -> R.drawable.apps_24
    }

    private fun appDiagnosticLabel(group: AppGroup): String {
        return when (getAppStatus(group.packageName)) {
            AppInstallStatus.NOT_INSTALLED -> getString(R.string.in_app_status_not_installed)
            AppInstallStatus.DISABLED -> getString(R.string.in_app_status_disabled)
            AppInstallStatus.INSTALLED -> {
                if (group.packageName == "com.google.android.youtube") {
                    val enabledVariants = detectYouTubeVariants().filter { it.isEnabled }
                    if (enabledVariants.isNotEmpty()) {
                        return enabledVariants.joinToString(" · ") { it.label }
                    }
                }
                val resolvedPkg = if (group.packageName == "com.google.android.youtube") {
                    listOf("app.morphe.android.youtube", "app.revanced.android.youtube", group.packageName)
                        .firstOrNull { runCatching { packageManager.getApplicationInfo(it, 0).enabled }.getOrDefault(false) }
                        ?: group.packageName
                } else {
                    group.packageName
                }
                val info = runCatching { packageManager.getPackageInfo(resolvedPkg, 0) }.getOrNull()
                val version = info?.versionName.orEmpty().ifBlank { null }
                val lastDetected = InAppDetectionStore.lastForAny(
                    this,
                    group.surfaces.mapNotNull { it.surfaceKey },
                )
                when {
                    lastDetected > 0L && info != null && info.lastUpdateTime <= lastDetected -> {
                        if (version != null) {
                            getString(R.string.in_app_app_diagnostic_detected, version, RelativeTimeFormatter.format(this, lastDetected))
                        } else {
                            RelativeTimeFormatter.format(this, lastDetected)
                        }
                    }
                    lastDetected > 0L -> {
                        if (version != null) {
                            getString(R.string.in_app_app_diagnostic_stale, version)
                        } else {
                            getString(R.string.in_app_status_installed)
                        }
                    }
                    version != null -> getString(R.string.in_app_app_diagnostic_never, version)
                    else -> getString(R.string.in_app_status_installed)
                }
            }
        }
    }

    private fun buildSurfaceRow(surface: Surface, packageName: String, onToggle: (() -> Unit)? = null): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            addView(TextView(this@InAppRulesActivity).apply {
                text = ruleSwitchText(getString(surface.labelRes))
                setTypeface(typeface, Typeface.BOLD)
                textSize = 14f
                setTextColor(onSurfaceColor())
            })
            addView(TextView(this@InAppRulesActivity).apply {
                text = getString(surface.statusRes)
                alpha = 0.72f
                textSize = 12f
                setTextColor(onSurfaceColor())
                gravity = Gravity.CENTER_VERTICAL
            })
        })

        val prefKey = surface.prefKey
        val readOnly = EditingLockGuard.isLocked(this)
        val currentChecked = prefKey?.let { readProfileBool(it) } ?: false
        val canToggleWhileLocked = prefKey != null && ProtectionEditPolicy.canChangeSelection(
            context = this,
            profile = currentProfile(),
            allowMode = isInAppAllowMode(),
            currentlySelected = currentChecked,
            requestedSelected = !currentChecked,
        )
        val sw = SwitchCompat(this).apply {
            // Stay tappable (dimmed): denied taps warn via pill instead of doing nothing.
            isEnabled = prefKey != null
            alpha = when {
                prefKey == null -> 0.52f
                readOnly && !canToggleWhileLocked -> 0.45f
                else -> 1f
            }
            if (prefKey != null) {
                isChecked = currentChecked
                if (isChecked && !readOnly) {
                    surface.surfaceKey?.let { setSurfaceRuleForMode(it, checked = true) }
                }
                setOnCheckedChangeListener { button, checked ->
                    val before = readProfileBool(prefKey)
                    if (!ProtectionEditPolicy.canChangeSelection(
                            context = this@InAppRulesActivity,
                            profile = currentProfile(),
                            allowMode = isInAppAllowMode(),
                            currentlySelected = before,
                            requestedSelected = checked,
                        )
                    ) {
                        if (EditingLockGuard.isLocked(this@InAppRulesActivity)) {
                            this@InAppRulesActivity.findViewById<View>(android.R.id.content)
                                .showWarnPill(R.string.rules_tighten_only_active_message)
                        }
                        button.setOnCheckedChangeListener(null)
                        button.isChecked = before
                        button.post { render() }
                        return@setOnCheckedChangeListener
                    }
                    writeProfileBool(prefKey, checked)
                    surface.surfaceKey?.let { surfaceKey ->
                        setSurfaceRuleForMode(surfaceKey, checked)
                    }
                    keepAppAllowedForInAppRule(packageName, prefKey, checked)
                    BlockingRuntime.ensureRunning(this@InAppRulesActivity)
                    onToggle?.invoke()
                    if (EditingLockGuard.isLocked(this@InAppRulesActivity)) {
                        button.post { render() }
                    }
                }
            }
        }
        CustomAccentApplier.tintSwitch(sw)
        row.addView(sw)
        return row
    }

    private fun groups(): List<AppGroup> = listOf(
        AppGroup(
            R.string.in_app_rules_youtube,
            "com.google.android.youtube",
            listOf(
                Surface(R.string.in_app_surface_shorts_label, BlockingToggleKeys.KEY_BLOCK_YT_SHORTS, "yt:shorts", R.string.in_app_status_experimental)
                // NOTE: Temporarily hidden YouTube settings (may add back later):
                // Surface(R.string.in_app_surface_subscriptions_label, BlockingToggleKeys.KEY_BLOCK_YT_SUBSCRIPTIONS, "yt:subscriptions", R.string.in_app_status_supported),
                // Surface(R.string.in_app_surface_you_label, BlockingToggleKeys.KEY_BLOCK_YT_YOU, "yt:you", R.string.in_app_status_supported),
                // Surface(R.string.in_app_surface_mini_player_label, BlockingToggleKeys.KEY_BLOCK_YT_MINI_PLAYER, "yt:miniplayer", R.string.in_app_status_experimental),
                // Surface(R.string.in_app_surface_pip_label, BlockingToggleKeys.KEY_BLOCK_YT_PIP, "yt:pip", R.string.in_app_status_experimental)
            )
        ),
        AppGroup(
            R.string.in_app_rules_instagram,
            "com.instagram.android",
            listOf(
                Surface(R.string.in_app_surface_reels_label, BlockingToggleKeys.KEY_BLOCK_IG_REELS, "ig:reels", R.string.in_app_status_experimental),
                Surface(R.string.in_app_surface_explore_label, BlockingToggleKeys.KEY_BLOCK_IG_EXPLORE, "ig:explore", R.string.in_app_status_experimental),
                Surface(R.string.in_app_surface_search_label, BlockingToggleKeys.KEY_BLOCK_IG_SEARCH, "ig:search", R.string.in_app_status_experimental),
                Surface(R.string.in_app_surface_stories_label, BlockingToggleKeys.KEY_BLOCK_IG_STORIES, "ig:stories", R.string.in_app_status_experimental),
                Surface(R.string.in_app_surface_comments_label, BlockingToggleKeys.KEY_BLOCK_IG_COMMENTS, "ig:comments", R.string.in_app_status_experimental)
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
                Surface(R.string.in_app_surface_reels_label, BlockingToggleKeys.KEY_BLOCK_FB_REELS, "fb:reels", R.string.in_app_status_experimental)
            )
        ),
        AppGroup(
            R.string.in_app_rules_tiktok,
            "com.zhiliaoapp.musically",
            listOf(Surface(R.string.in_app_rules_for_you, null, null, R.string.in_app_status_planned))
        )
    )

    private fun emptyState(): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
            setCardBackgroundColor(ContextCompat.getColor(this@InAppRulesActivity, R.color.foqos_surface))
            strokeColor = ContextCompat.getColor(this@InAppRulesActivity, R.color.foqos_outline_variant)
            strokeWidth = dp(1)
            radius = dp(20).toFloat()
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
                alpha = 0.72f
                gravity = Gravity.CENTER
                setTextColor(onSurfaceColor())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4) }
            })
        })
        return card
    }

    private fun isAppInstalled(packageName: String): Boolean =
        getAppStatus(packageName) == AppInstallStatus.INSTALLED

    private fun appIcon(packageName: String): Drawable? {
        val candidates = if (packageName == "com.google.android.youtube") {
            listOf("app.morphe.android.youtube", "app.revanced.android.youtube", packageName)
        } else {
            listOf(packageName)
        }
        for (pkg in candidates) {
            val d = runCatching {
                val ai = packageManager.getApplicationInfo(pkg, 0)
                if (ai.enabled) packageManager.getApplicationIcon(ai) else null
            }.getOrNull()
            if (d != null) return d
        }
        for (pkg in candidates) {
            val d = runCatching {
                val ai = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationIcon(ai)
            }.getOrNull()
            if (d != null) return d
        }
        return null
    }

    private fun currentProfile(): String =
        intent.getStringExtra(EXTRA_PROFILE_NAME)?.trim()?.takeIf { it.isNotBlank() }
            ?: ProfileStore.getCurrent(this) ?: "default"

    private fun readProfileBool(baseKey: String): Boolean {
        return InAppRuleStore.isRuleSelected(this, currentProfile(), baseKey)
    }

    private fun writeProfileBool(baseKey: String, value: Boolean) {
        InAppRuleStore.setRuleSelected(this, currentProfile(), baseKey, value)
    }

    private fun keepAppAllowedForInAppRule(packageName: String, baseKey: String, enabled: Boolean) {
        if (!enabled) {
            return
        }
        val profile = currentProfile()
        if (!ProfileRuleModeStore.isAllowMode(this, profile)) {
            return
        }
        if (InAppRuleStore.packageForRuleKey(baseKey) != packageName) {
            return
        }
        val packagesToAdd = if (packageName == "com.google.android.youtube") {
            listOf("app.morphe.android.youtube", "app.revanced.android.youtube", packageName)
        } else {
            listOf(packageName)
        }
        val currentAllowed = ProfileStore.getAllowedForProfile(this, profile)
        val missing = packagesToAdd.filter {
            it !in currentAllowed && runCatching { packageManager.getApplicationInfo(it, 0).enabled }.getOrDefault(false)
        }
        if (missing.isNotEmpty()) {
            ProfileStore.setAllowedForProfile(this, profile, currentAllowed + missing)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun onSurfaceColor(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0)
}
