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

package at.saltyy.switchly.feature.usage

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.util.Pair
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import at.saltyy.switchly.R
import at.saltyy.switchly.feature.onboarding.OnboardingUsageDonutView
import at.saltyy.switchly.feature.stats.StatsFormat
import at.saltyy.switchly.theme.AccentColor
import at.saltyy.switchly.ui.EdgeToEdgeUtils
import at.saltyy.switchly.ui.ThemeUtils
import at.saltyy.switchly.ui.dialog.showAccented
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Dedicated screen-time distribution view for the most used apps on the device. */
class MostUsedAppsActivity : AppCompatActivity() {

    private enum class Range(val labelRes: Int, val detailRange: String) {
        TODAY(R.string.stats_range_today, AppUsageDetailActivity.RANGE_TODAY),
        WEEK(R.string.stats_range_week, AppUsageDetailActivity.RANGE_WEEK),
        MONTH(R.string.stats_range_month, AppUsageDetailActivity.RANGE_MONTH),
        YEAR(R.string.stats_range_year, AppUsageDetailActivity.RANGE_YEAR),
        CUSTOM(R.string.activity_history_range_custom, AppUsageDetailActivity.RANGE_CUSTOM)
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: LinearLayout
    private var currentRange: Range = Range.TODAY
    private var customRangeStartMillis: Long? = null
    private var customRangeEndMillis: Long? = null
    private var customRangePickerShowing = false
    private var loadJob: Job? = null
    private var loadVersion = 0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(at.saltyy.switchly.util.LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resolveColor(android.R.attr.colorBackground))
        }

        toolbar = MaterialToolbar(this).apply {
            minimumHeight = actionBarSize()
            title = getString(R.string.most_used_apps_title)
            setNavigationIcon(R.drawable.keyboard_arrow_left_24)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            setBackgroundColor(AccentColor.getToolbarColor(this@MostUsedAppsActivity))
            navigationIcon?.mutate()?.setTint(toolbarIconColor())
            menu.add(R.string.most_used_apps_info_title).apply {
                setIcon(R.drawable.info_24)
                icon?.mutate()?.setTint(toolbarIconColor())
                setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
                setOnMenuItemClickListener {
                    showInfo()
                    true
                }
            }
        }
        root.addView(
            AppBarLayout(this).apply {
                fitsSystemWindows = true
                addView(
                    toolbar,
                    AppBarLayout.LayoutParams(
                        AppBarLayout.LayoutParams.MATCH_PARENT,
                        AppBarLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }
        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false

        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun showInfo() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.most_used_apps_info_title)
            .setMessage(R.string.most_used_apps_info_body)
            .setPositiveButton(android.R.string.ok, null)
            .showAccented()
    }

    private fun load() {
        renderLoadingShell()
        val request = ++loadVersion
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val hasUsageAccess = withContext(Dispatchers.IO) {
                UsageStatsRepo.hasUsageAccess(this@MostUsedAppsActivity)
            }
            val summary = if (hasUsageAccess) {
                withContext(Dispatchers.IO) {
                    runCatching { StatsArchiveSync.sync(this@MostUsedAppsActivity) }
                    loadSummary(currentRange)
                }
            } else {
                UsageSummary(0L, emptyList())
            }
            if (request != loadVersion) return@launch
            render(hasUsageAccess, summary)
        }
    }

    private fun loadSummary(range: Range): UsageSummary = when (range) {
        Range.TODAY -> AppUsageRepo.getTodaySummary(this, topN = 100)
        Range.WEEK -> AppUsageRepo.getLastNDaysSummary(this, 7, topN = 100)
        Range.MONTH -> AppUsageRepo.getThisMonthSummary(this, topN = 100)
        Range.YEAR -> AppUsageRepo.getThisYearSummary(this, topN = 100)
        Range.CUSTOM -> {
            val start = customRangeStartMillis
            val end = customRangeEndMillis
            if (start != null && end != null) {
                AppUsageRepo.getDateRangeSummary(this, start, end, topN = 100)
            } else {
                UsageSummary(0L, emptyList())
            }
        }
    }

    private fun renderLoadingShell() {
        content.removeAllViews()
        addRangeControls()
        content.addView(messageCard(getString(R.string.most_used_apps_loading)), sectionCardLayoutParams())
    }

    private fun render(hasUsageAccess: Boolean, summary: UsageSummary) {
        content.removeAllViews()
        addRangeControls()

        if (!hasUsageAccess) {
            content.addView(messageCard(getString(R.string.most_used_apps_usage_access_needed)), sectionCardLayoutParams())
            content.addView(MaterialButton(this).apply {
                text = getString(R.string.usage_open_settings)
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })
            return
        }

        val apps = summary.topApps.filter { it.timeMs > 0L }
        if (summary.totalTimeMs <= 0L || apps.isEmpty()) {
            content.addView(messageCard(getString(R.string.most_used_apps_empty)), sectionCardLayoutParams())
            return
        }

        content.addView(distributionCard(summary, apps), sectionCardLayoutParams())
        content.addView(sectionTitle(R.string.most_used_apps_ranking_title).apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(22)
        })

        apps.forEachIndexed { index, app ->
            content.addView(rankingCard(index + 1, app), sectionCardLayoutParams())
        }
    }

    private fun addRangeControls() {
        val ids = mutableMapOf<Int, Range>()
        val group = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        Range.values().forEach { range ->
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId()
                text = if (range == Range.CUSTOM) "" else getString(range.labelRes)
                minWidth = 0
                minimumWidth = 0
                minHeight = dp(40)
                minimumHeight = dp(40)
                insetTop = 0
                insetBottom = 0
                setPadding(dp(4), 0, dp(4), 0)
                cornerRadius = dp(14)
                setAllCaps(false)
                isCheckable = true
                if (range == Range.CUSTOM) {
                    contentDescription = getString(R.string.activity_history_range_custom)
                    icon = ContextCompat.getDrawable(this@MostUsedAppsActivity, R.drawable.calendar_month_24)
                    iconPadding = 0
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(40))
                } else {
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
                }
            }
            ids[button.id] = range
            group.addView(button)
            styleRangeButton(button, range == currentRange)
            if (range == currentRange) group.check(button.id)
        }

        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selected = ids[checkedId] ?: return@addOnButtonCheckedListener
            if (selected == currentRange) return@addOnButtonCheckedListener
            if (selected == Range.CUSTOM) {
                showCustomRangePicker()
            } else {
                currentRange = selected
                load()
            }
        }

        content.addView(group)
        addCustomRangeSummary()
    }

    private fun addCustomRangeSummary() {
        if (currentRange != Range.CUSTOM) return
        val start = customRangeStartMillis ?: return
        val end = customRangeEndMillis ?: return
        val fmt = DateFormat.getDateInstance(DateFormat.SHORT)

        content.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(8))

            addView(TextView(this@MostUsedAppsActivity).apply {
                text = getString(
                    R.string.activity_history_range_custom_value,
                    fmt.format(Date(start)),
                    fmt.format(Date(end))
                )
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                alpha = 0.82f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(MaterialButton(this@MostUsedAppsActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.stats_range_clear)
                minWidth = 0
                minimumWidth = 0
                minHeight = dp(36)
                insetTop = 0
                insetBottom = 0
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    customRangeStartMillis = null
                    customRangeEndMillis = null
                    currentRange = Range.TODAY
                    load()
                }
            })
        })
    }

    private fun showCustomRangePicker() {
        if (customRangePickerShowing || supportFragmentManager.isStateSaved) return
        customRangePickerShowing = true
        val now = System.currentTimeMillis()
        val start = customRangeStartMillis ?: startOfTodayMillis()
        val end = customRangeEndMillis ?: now
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTheme(at.saltyy.switchly.theme.AccentColor.getDatePickerTheme(this))
            .setTitleText(R.string.activity_history_range_custom)
            .setSelection(Pair(localDayToDatePickerUtcMillis(start), localDayToDatePickerUtcMillis(end)))
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val startUtc = selection.first ?: return@addOnPositiveButtonClickListener
            val endUtc = selection.second ?: startUtc
            customRangeStartMillis = datePickerUtcMillisToLocalDayStart(minOf(startUtc, endUtc))
            customRangeEndMillis = datePickerUtcMillisToLocalDayEnd(maxOf(startUtc, endUtc))
            currentRange = Range.CUSTOM
            load()
        }
        picker.addOnDismissListener {
            customRangePickerShowing = false
            if (currentRange != Range.CUSTOM && !isFinishing && !isDestroyed) {
                load()
            }
        }
        runCatching { picker.show(supportFragmentManager, "most_used_apps_custom_range") }
            .onSuccess { UsageDatePickerAccentTint.apply(this, picker) }
            .onFailure { customRangePickerShowing = false }
    }

    private fun distributionCard(summary: UsageSummary, apps: List<AppUsage>): MaterialCardView {
        val card = baseCard()
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))

            addView(TextView(this@MostUsedAppsActivity).apply {
                text = getString(R.string.most_used_apps_total_title)
                textSize = 14f
                alpha = 0.74f
            })
            addView(TextView(this@MostUsedAppsActivity).apply {
                text = StatsFormat.prettyMsWithSeconds(summary.totalTimeMs)
                textSize = 30f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(AccentColor.getAccentColorInt(this@MostUsedAppsActivity))
            })

            addView(FrameLayout(this@MostUsedAppsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(252), dp(252)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(10)
                }
                addView(OnboardingUsageDonutView(this@MostUsedAppsActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    submitSegments(buildSegments(summary, apps))
                })
            })
        })
        return card
    }

    private fun buildSegments(summary: UsageSummary, apps: List<AppUsage>): List<OnboardingUsageDonutView.Segment> {
        val chartApps = apps.take(6)
        val accent = AccentColor.getAccentColorInt(this)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        val isDarkAccent = ColorUtils.calculateLuminance(accent) < 0.24
        val colors = IntArray(chartApps.size) { index ->
            if (index == 0) {
                accent
            } else {
                ColorUtils.HSLToColor(
                    floatArrayOf(
                        (hsl[0] + index * 45f) % 360f,
                        (hsl[1] * if (index % 2 == 0) 0.88f else 0.98f).coerceIn(0.44f, 0.94f),
                        (if (isDarkAccent) 0.54f else 0.42f + (index % 3) * 0.045f).coerceIn(0.34f, 0.72f)
                    )
                )
            }
        }

        val total = summary.totalTimeMs.coerceAtLeast(1L).toFloat()
        val segments = chartApps.mapIndexed { index, app ->
            val fraction = (app.timeMs / total).coerceIn(0f, 1f)
            OnboardingUsageDonutView.Segment(
                fraction = fraction,
                color = colors[index],
                icon = app.icon?.constantState?.newDrawable(resources)?.mutate() ?: app.icon,
                percentageLabel = formatPercent(fraction)
            )
        }.toMutableList()

        val shownFraction = segments.sumOf { it.fraction.toDouble() }.toFloat().coerceIn(0f, 1f)
        val otherFraction = (1f - shownFraction).coerceAtLeast(0f)
        if (otherFraction > 0.005f) {
            val otherColor = ColorUtils.blendARGB(
                MaterialColors.getColor(content, com.google.android.material.R.attr.colorOnSurface),
                MaterialColors.getColor(content, com.google.android.material.R.attr.colorSurface),
                0.72f
            )
            segments += OnboardingUsageDonutView.Segment(
                fraction = otherFraction,
                color = otherColor,
                label = getString(R.string.usage_chart_other_apps),
                percentageLabel = formatPercent(otherFraction)
            )
        }
        return segments
    }

    private fun rankingCard(rank: Int, app: AppUsage): MaterialCardView {
        val card = baseCard()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        row.addView(TextView(this).apply {
            text = String.format(Locale.getDefault(), "%d", rank)
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(AccentColor.getAccentColorInt(this@MostUsedAppsActivity))
        }, LinearLayout.LayoutParams(dp(28), dp(44)))

        row.addView(ImageView(this).apply {
            setImageDrawable(app.icon)
            contentDescription = app.label
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            marginStart = dp(4)
        })

        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MostUsedAppsActivity).apply {
                text = app.label
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 2
            })
            addView(TextView(this@MostUsedAppsActivity).apply {
                text = getString(
                    R.string.most_used_apps_row_summary,
                    StatsFormat.prettyMsWithSeconds(app.timeMs),
                    StatsFormat.prettyPercent(app.percent)
                )
                textSize = 13f
                alpha = 0.72f
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
        })

        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.keyboard_arrow_right_24)
            imageTintList = ColorStateList.valueOf(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
            )
            alpha = 0.72f
        }, LinearLayout.LayoutParams(dp(20), dp(20)))

        card.addView(row)
        card.isClickable = true
        card.isFocusable = true
        card.foreground = selectableForeground()
        card.setOnClickListener { openAppDetail(app) }
        return card
    }

    private fun openAppDetail(app: AppUsage) {
        val intent = Intent(this, AppUsageDetailActivity::class.java)
            .putExtra(AppUsageDetailActivity.EXTRA_PKG, app.packageName)
            .putExtra(AppUsageDetailActivity.EXTRA_LABEL, app.label)
            .putExtra(AppUsageDetailActivity.EXTRA_INITIAL_RANGE, currentRange.detailRange)
        if (currentRange == Range.CUSTOM) {
            customRangeStartMillis?.let { intent.putExtra(AppUsageDetailActivity.EXTRA_INITIAL_START_MS, it) }
            customRangeEndMillis?.let { intent.putExtra(AppUsageDetailActivity.EXTRA_INITIAL_END_MS, it) }
        }
        startActivity(intent)
    }

    private fun messageCard(message: String): MaterialCardView = baseCard().apply {
        addView(TextView(this@MostUsedAppsActivity).apply {
            text = message
            textSize = 15f
            alpha = 0.82f
            setPadding(dp(16), dp(16), dp(16), dp(16))
        })
    }

    private fun sectionTitle(textRes: Int): TextView = TextView(this).apply {
        text = getString(textRes)
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        alpha = 0.82f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(8)
        }
    }

    private fun baseCard(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(20).toFloat()
        cardElevation = dp(1).toFloat()
        strokeWidth = dp(1)
        strokeColor = ContextCompat.getColor(this@MostUsedAppsActivity, R.color.foqos_outline_variant)
        setCardBackgroundColor(ContextCompat.getColor(this@MostUsedAppsActivity, R.color.foqos_surface))
    }

    private fun sectionCardLayoutParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        bottomMargin = dp(10)
    }

    private fun styleRangeButton(button: MaterialButton, active: Boolean) {
        val activeBg = AccentColor.getAccentColorInt(this)
        val activeText = if (MaterialColors.isColorLight(activeBg)) Color.BLACK else Color.WHITE
        val inactiveBg = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, Color.TRANSPARENT)
        val inactiveText = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, inactiveText)
        button.backgroundTintList = ColorStateList.valueOf(if (active) activeBg else inactiveBg)
        button.setTextColor(if (active) activeText else inactiveText)
        button.iconTint = ColorStateList.valueOf(if (active) activeText else inactiveText)
        button.strokeColor = ColorStateList.valueOf(if (active) activeBg else outline)
        button.strokeWidth = resources.displayMetrics.density.toInt().coerceAtLeast(1)
        button.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(activeBg, 0x35))
    }

    private fun formatPercent(fraction: Float): String {
        val normalized = fraction.coerceIn(0f, 1f)
        val percent = (normalized * 100f).roundToInt()
        return if (normalized > 0f && percent == 0) "<1%" else "$percent%"
    }

    private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun localDayToDatePickerUtcMillis(localMillis: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = localMillis }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun datePickerUtcMillisToLocalDayStart(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun datePickerUtcMillisToLocalDayEnd(utcMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = datePickerUtcMillisToLocalDayStart(utcMillis)
        add(Calendar.DAY_OF_YEAR, 1)
        add(Calendar.MILLISECOND, -1)
    }.timeInMillis

    private fun actionBarSize(): Int {
        val typed = android.util.TypedValue()
        val ok = theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typed, true)
        return if (ok) android.util.TypedValue.complexToDimensionPixelSize(typed.data, resources.displayMetrics) else dp(56)
    }

    private fun toolbarIconColor(): Int {
        return if (MaterialColors.isColorLight(AccentColor.getToolbarColor(this))) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    private fun selectableForeground(): android.graphics.drawable.Drawable? {
        val typed = android.util.TypedValue()
        if (!theme.resolveAttribute(android.R.attr.selectableItemBackground, typed, true) || typed.resourceId == 0) return null
        return ContextCompat.getDrawable(this, typed.resourceId)
    }

    private fun resolveColor(attr: Int): Int {
        val typed = android.util.TypedValue()
        theme.resolveAttribute(attr, typed, true)
        return typed.data
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        fun intent(context: Context): Intent = Intent(context, MostUsedAppsActivity::class.java)
    }
}
