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

package com.oliver.loqin.feature.usage

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import com.oliver.loqin.R
import com.oliver.loqin.ui.dialog.InputSheet
import com.oliver.loqin.data.prefs.ActivityHistoryLogStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.PageUi
import com.oliver.loqin.ui.SegmentedToggleUi
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.LoqInDropdownAdapter
import com.oliver.loqin.util.LocaleHelper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class ActivityHistoryActivity : AppCompatActivity() {

    private lateinit var filtersContainer: LinearLayout
    private lateinit var entriesContainer: LinearLayout

    private var rangeFilter = RangeFilter.TODAY
    private var customRangeStartMillis: Long? = null
    private var customRangeEndMillis: Long? = null
    private var typeFilter = TypeFilter.ALL
    private var sortOrder = SortOrder.NEWEST
    private var customRangePickerShowing = false
    private val activityHistoryChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (ActivityHistoryLogStore.isHistoryChangeKey(key)) {
            runOnUiThread { refreshContent() }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resolveColor(android.R.attr.colorBackground))
        }

        val toolbar = MaterialToolbar(this).apply {
            minimumHeight = actionBarSize()
            title = getString(R.string.active_time_activity_history_title)
            setNavigationIcon(R.drawable.keyboard_arrow_left_24)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            menu.add(Menu.NONE, MENU_INFO, Menu.NONE, R.string.activity_history_info_title).apply {
                setIcon(R.drawable.info_24)
                icon?.mutate()?.setTint(toolbarIconColor())
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            menu.add(Menu.NONE, MENU_SORT_FILTER, Menu.NONE, R.string.stats_sort_filter).apply {
                setIcon(R.drawable.tune_24)
                icon?.mutate()?.setTint(toolbarIconColor())
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_INFO -> {
                        showInfoDialog()
                        true
                    }
                    MENU_SORT_FILTER -> {
                        showSortFilterDialog()
                        true
                    }
                    else -> false
                }
            }
        }

        root.addView(AppBarLayout(this).apply {
            fitsSystemWindows = true
            addView(
                toolbar,
                AppBarLayout.LayoutParams(
                    AppBarLayout.LayoutParams.MATCH_PARENT,
                    AppBarLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        PageUi.applyPageContentPadding(content)
        scroll.addView(
            content,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        filtersContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(filtersContainer)

        entriesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(entriesContainer)

        val coordinator = CoordinatorLayout(this).apply {
            setBackgroundColor(resolveColor(android.R.attr.colorBackground))
        }
        coordinator.addView(root, CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(coordinator)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        refreshContent()
    }

    override fun onStart() {
        super.onStart()
        ActivityHistoryLogStore.registerChangeListener(this, activityHistoryChangeListener)
        refreshContent()
    }

    override fun onResume() {
        super.onResume()
        refreshContent()
    }

    override fun onStop() {
        ActivityHistoryLogStore.unregisterChangeListener(this, activityHistoryChangeListener)
        super.onStop()
    }

    private fun refreshContent() {
        renderFilters()
        renderEntries()
    }

    private fun renderFilters() {
        filtersContainer.removeAllViews()

        val rangeGroup = StatsPageUi.rangeBar(this)

        var checkedRangeId = View.NO_ID
        val buttons = RangeFilter.values().map { option ->
            val button = rangeButton(option)
            if (option == rangeFilter) checkedRangeId = button.id
            rangeGroup.addView(button)
            button
        }
        if (checkedRangeId != View.NO_ID) rangeGroup.check(checkedRangeId)
        // Style once the segments are in the group, so the group gets the shared pill track too.
        SegmentedToggleUi.apply(this, buttons, checkedRangeId)

        filtersContainer.addView(rangeGroup)

        customRangeSummaryView()?.let { summary ->
            filtersContainer.addView(summary)
        }
    }

    private fun renderEntries() {
        entriesContainer.removeAllViews()

        val window = selectedTimeWindow()
        val entries = ActivityHistoryRepository
            .recentEntries(this, days = historyLoadDays(window.first), limit = 500)
            .filter { it.timeMillis in window.first..window.second }
            .filter { typeFilter.matches(it) }
            .let { filtered ->
                when (sortOrder) {
                    SortOrder.NEWEST -> filtered.sortedByDescending { it.timeMillis }
                    SortOrder.OLDEST -> filtered.sortedBy { it.timeMillis }
                }
            }

        // The result count heads the list, like a section header.
        StatsPageUi.addSectionHeader(
            entriesContainer,
            resources.getQuantityString(R.plurals.activity_history_result_count, entries.size, entries.size),
            first = true
        )

        if (entries.isEmpty()) {
            entriesContainer.addView(
                StatsPageUi.emptyState(this, getString(R.string.activity_history_recent_empty), R.drawable.list_alt_24)
            )
            return
        }

        val dateFmt = DateFormat.getDateInstance(DateFormat.SHORT)
        val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        val group = StatsPageUi.listGroup(entriesContainer)
        entries.forEach { entry ->
            val date = Date(entry.timeMillis)
            group.addView(StatsPageUi.row(
                parent = group,
                title = entry.title,
                summary = entry.summary,
                value = "${dateFmt.format(date)}\n${timeFmt.format(date)}",
                iconRes = entry.iconRes,
            ) { showEntryDetailDialog(entry) }.apply {
                findViewById<TextView>(R.id.statsRowValue).gravity = Gravity.END
            })
        }
        entriesContainer.addView(group)
    }

    private fun showSortFilterDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_statistics_dropdown_sort_filter, FrameLayout(this), false)
        val typeDropdown = view.findViewById<MaterialAutoCompleteTextView>(R.id.dropdownStatsPrimary)
        val sortDropdown = view.findViewById<MaterialAutoCompleteTextView>(R.id.dropdownStatsSort)
        val extraFilter = view.findViewById<View>(R.id.cbStatsExtraFilter)

        view.findViewById<TextView>(R.id.tvStatsDropdownPrimaryLabel).text =
            getString(R.string.activity_history_filter_action)

        val typeOptions = TypeFilter.values().toList()
        val typeLabels = typeOptions.map { getString(it.labelRes) }
        val sortOptions = listOf(
            SortOrder.NEWEST to getString(R.string.activity_history_sort_newest),
            SortOrder.OLDEST to getString(R.string.activity_history_sort_oldest)
        )
        var selectedTypeFilter = typeFilter
        var selectedSortOrder = sortOrder

        typeDropdown.setAdapter(LoqInDropdownAdapter(this, typeLabels))
        typeDropdown.setText(getString(typeFilter.labelRes), false)
        typeDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedTypeFilter = typeOptions.getOrElse(position) { typeFilter }
        }
        typeDropdown.setOnClickListener { typeDropdown.showDropDown() }

        sortDropdown.setAdapter(LoqInDropdownAdapter(this, sortOptions.map { it.second }))
        sortDropdown.setText(sortOptions.first { it.first == sortOrder }.second, false)
        sortDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedSortOrder = sortOptions.getOrElse(position) { sortOptions.first() }.first
        }
        sortDropdown.setOnClickListener { sortDropdown.showDropDown() }
        extraFilter.visibility = View.GONE

        val sheet = InputSheet(this, getString(R.string.stats_sort_filter_title))
        sheet.content(view)
        sheet.primary(getString(R.string.stats_apply)) {
            typeFilter = selectedTypeFilter
            sortOrder = selectedSortOrder
            refreshContent()
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun rangeButton(option: RangeFilter): MaterialButton {
        val active = option == rangeFilter
        val button = if (option == RangeFilter.CUSTOM) {
            StatsPageUi.calendarSegment(this)
        } else {
            StatsPageUi.rangeSegment(this, rangeChipText(option))
        }
        return button.apply {
            isChecked = active
            setOnClickListener {
                if (!ensureRangeAllowed(option)) {
                    refreshContent()
                    return@setOnClickListener
                }
                if (option == RangeFilter.CUSTOM) {
                    showCustomRangePicker()
                } else {
                    rangeFilter = option
                    refreshContent()
                }
            }
        }
    }

    private fun ensureRangeAllowed(option: RangeFilter): Boolean {
        // Extended stat ranges are available to everyone.
        return true
    }

    private fun rangeChipText(option: RangeFilter): String = getString(option.labelRes)

    private fun customRangeSummaryView(): View? {
        if (rangeFilter != RangeFilter.CUSTOM) {
            return null
        }
        val start = customRangeStartMillis ?: return null
        val end = customRangeEndMillis ?: return null
        val fmt = DateFormat.getDateInstance(DateFormat.SHORT)
        val value = getString(
            R.string.activity_history_range_custom_value,
            fmt.format(Date(start)),
            fmt.format(Date(end))
        )

        val row = StatsPageUi.customRangeRow(filtersContainer) {
            customRangeStartMillis = null
            customRangeEndMillis = null
            rangeFilter = RangeFilter.TODAY
            refreshContent()
        }
        row.findViewById<TextView>(R.id.statsCustomRangeValue).apply {
            text = value
            // Tapping the dates reopens the picker, as the old range card did.
            setOnClickListener { showCustomRangePicker() }
        }
        return row
    }

    private fun showCustomRangePicker() {
        if (!ensureRangeAllowed(RangeFilter.CUSTOM)) {
            rangeFilter = RangeFilter.TODAY
            refreshContent()
            return
        }
        if (customRangePickerShowing || supportFragmentManager.isStateSaved) {
            return
        }

        val currentStart = customRangeStartMillis
        val currentEnd = customRangeEndMillis
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTheme(com.oliver.loqin.theme.AccentColor.getDatePickerTheme(this))
            .setTitleText(R.string.activity_history_range_custom_title)

        if (currentStart != null && currentEnd != null && currentStart <= currentEnd) {
            builder.setSelection(
                androidx.core.util.Pair(
                    localDayToDatePickerUtcMillis(currentStart),
                    localDayToDatePickerUtcMillis(currentEnd)
                )
            )
        }

        val picker = runCatching { builder.build() }.getOrElse {
            customRangePickerShowing = false
            return
        }

        customRangePickerShowing = true
        var datePickerLayoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null
        var datePickerDecor: View? = null

        picker.addOnDismissListener {
            customRangePickerShowing = false
            datePickerDecor?.let { decor ->
                datePickerLayoutListener?.let { listener ->
                    runCatching {
                        if (decor.viewTreeObserver.isAlive) {
                            decor.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                        }
                    }
                }
            }
            refreshContent()
        }
        picker.addOnPositiveButtonClickListener { selection ->
            val start = selection.first ?: return@addOnPositiveButtonClickListener
            val end = selection.second ?: return@addOnPositiveButtonClickListener
            customRangeStartMillis = datePickerUtcMillisToLocalDayStart(minOf(start, end))
            customRangeEndMillis = datePickerUtcMillisToLocalDayEnd(maxOf(start, end))
            rangeFilter = RangeFilter.CUSTOM
            refreshContent()
        }

        val shown = runCatching {
            picker.show(
                supportFragmentManager,
                "loqin_activity_history_range"
            )
        }.isSuccess

        if (!shown) {
            customRangePickerShowing = false
            return
        }

        UsageDatePickerAccentTint.apply(this, picker)

        if (CustomAccentApplier.isCustomAccentEnabled(this)) {
            window.decorView.post {
                val decor = picker.dialog?.window?.decorView ?: return@post
                datePickerDecor = decor
                runCatching { applyCustomAccentToDatePicker(decor) }

                datePickerLayoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    runCatching { applyCustomAccentToDatePicker(decor) }
                }
                datePickerLayoutListener?.let { listener ->
                    runCatching {
                        decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
                    }
                }

                longArrayOf(80L, 180L, 360L, 720L).forEach { delay ->
                    decor.postDelayed(
                        { runCatching { applyCustomAccentToDatePicker(decor) } },
                        delay
                    )
                }
            }
        }
    }

    private fun applyCustomAccentToDatePicker(root: View) {
        if (!CustomAccentApplier.isCustomAccentEnabled(this)) {
            return
        }

        val accent = AccentColor.getAccentColorInt(this)
        val defaultAccent = ContextCompat.getColor(this, R.color.accent_default_blue)
        val onAccent = readableAccentTextColor(accent)
        val subtleAccent = ColorUtils.setAlphaComponent(accent, 0x22)

        fun sweep(view: View) {
            val idName = runCatching {
                if (view.id != View.NO_ID) view.resources.getResourceEntryName(view.id) else ""
            }.getOrDefault("")
            val className = view.javaClass.name

            retintDatePickerDrawable(view.background, defaultAccent, accent, subtleAccent)
            ViewCompat.getBackgroundTintList(view)?.let { tint ->
                if (matchesDatePickerAccent(tint.defaultColor, defaultAccent)) {
                    ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(accent))
                }
            }

            if (idName.contains("confirm", ignoreCase = true) ||
                idName.contains("cancel", ignoreCase = true) ||
                className.contains("MaterialButton", ignoreCase = true)
            ) {
                styleDatePickerActionButton(view, accent)
            } else if (view.isSelected || view.isActivated) {
                view.backgroundTintList = ColorStateList.valueOf(accent)
                (view as? TextView)?.setTextColor(onAccent)
            } else if (idName.contains("range", ignoreCase = true)) {
                view.backgroundTintList = ColorStateList.valueOf(subtleAccent)
            }

            if (view is TextView && view !is MaterialButton && matchesDatePickerAccent(view.currentTextColor, defaultAccent)) {
                view.setTextColor(accent)
            }

            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    sweep(view.getChildAt(i))
                }
            }
        }

        sweep(root)
    }

    private fun styleDatePickerActionButton(view: View, accent: Int) {
        val text = view as? TextView ?: return
        text.setTextColor(accent)
        text.alpha = if (view.isEnabled) 1f else 0.48f

        if (view is MaterialButton) {
            // MaterialDatePicker action buttons can inherit a filled accent background in CUSTOM mode.
            // Keep them as transparent text buttons so Save/Cancel never become accent-on-accent.
            view.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            view.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
            view.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x28))
            view.iconTint = ColorStateList.valueOf(accent)
        }
    }

    private fun retintDatePickerDrawable(drawable: Drawable?, defaultAccent: Int, accent: Int, subtleAccent: Int) {
        drawable ?: return
        runCatching {
            when (drawable) {
                is ColorDrawable -> {
                    if (matchesDatePickerAccent(drawable.color, defaultAccent)) {
                        drawable.color = accent
                    }
                }
                is GradientDrawable -> {
                    val color = drawable.color?.defaultColor
                    if (color != null && matchesDatePickerAccent(color, defaultAccent)) {
                        drawable.setColor(accent)
                    }
                }
                is MaterialShapeDrawable -> {
                    drawable.fillColor?.defaultColor?.let { color ->
                        if (matchesDatePickerAccent(color, defaultAccent)) {
                            drawable.fillColor = ColorStateList.valueOf(accent)
                        }
                    }
                    drawable.strokeColor?.defaultColor?.let { color ->
                        if (matchesDatePickerAccent(color, defaultAccent)) {
                            drawable.strokeColor = ColorStateList.valueOf(accent)
                        }
                    }
                }
                is InsetDrawable -> {
                    retintDatePickerDrawable(drawable.drawable, defaultAccent, accent, subtleAccent)
                }
                is LayerDrawable -> {
                    for (i in 0 until drawable.numberOfLayers) {
                        retintDatePickerDrawable(drawable.getDrawable(i), defaultAccent, accent, subtleAccent)
                    }
                }
                is RippleDrawable -> {
                    drawable.setColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x28)))
                    for (i in 0 until drawable.numberOfLayers) {
                        retintDatePickerDrawable(drawable.getDrawable(i), defaultAccent, accent, subtleAccent)
                    }
                }
                is StateListDrawable -> {
                    val count = runCatching {
                        StateListDrawable::class.java.getMethod("getStateCount").invoke(drawable) as Int
                    }.getOrDefault(0)
                    val getter = runCatching {
                        StateListDrawable::class.java.getMethod("getStateDrawable", Int::class.javaPrimitiveType!!)
                    }.getOrNull()
                    if (getter != null) {
                        for (i in 0 until count) {
                            retintDatePickerDrawable(getter.invoke(drawable, i) as? Drawable, defaultAccent, accent, subtleAccent)
                        }
                    } else {
                        DrawableCompat.setTint(DrawableCompat.wrap(drawable.mutate()), accent)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun matchesDatePickerAccent(color: Int, defaultAccent: Int): Boolean {
        if (Color.alpha(color) == 0) {
            return false
        }
        if (color == defaultAccent) {
            return true
        }
        val dr = kotlin.math.abs(Color.red(color) - Color.red(defaultAccent))
        val dg = kotlin.math.abs(Color.green(color) - Color.green(defaultAccent))
        val db = kotlin.math.abs(Color.blue(color) - Color.blue(defaultAccent))
        return dr + dg + db < 48
    }

    private fun selectedTimeWindow(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        if (rangeFilter == RangeFilter.CUSTOM) {
            val start = customRangeStartMillis
            val end = customRangeEndMillis
            if (start != null && end != null) {
                return Pair(start, end)
            }
        }
        return when (rangeFilter) {
            RangeFilter.TODAY -> Pair(startOfToday(), now)
            RangeFilter.WEEK -> Pair(now - 7L * DAY_MILLIS, now)
            RangeFilter.MONTH -> Pair(startOfCurrentMonth(), now)
            RangeFilter.YEAR -> Pair(startOfCurrentYear(), now)
            RangeFilter.CUSTOM -> Pair(startOfToday(), now)
        }
    }

    private fun startOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun startOfCurrentMonth(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun startOfCurrentYear(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun historyLoadDays(startMillis: Long): Int {
        val ageMillis = (System.currentTimeMillis() - startMillis).coerceAtLeast(DAY_MILLIS)
        return ((ageMillis + DAY_MILLIS - 1) / DAY_MILLIS).toInt().coerceAtLeast(1)
    }

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

    private fun datePickerUtcMillisToLocalDayEnd(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    private fun showInfoDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.activity_history_info_title)
            .setMessage(R.string.activity_history_info_message)
            .setPositiveButton(android.R.string.ok, null)
            .showAccented()
    }

    private fun showEntryDetailDialog(entry: ActivityHistoryRepository.Entry) {
        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val body = buildString {
            append(getString(R.string.activity_history_detail_time_line, fmt.format(Date(entry.timeMillis)))).append("\n")
            append(getString(R.string.activity_history_detail_source_line, entry.source.name.lowercase().replaceFirstChar { it.uppercase() })).append("\n")
            append(getString(R.string.activity_history_detail_action_line, entry.action.name.lowercase().replaceFirstChar { it.uppercase() })).append("\n")
            if (entry.summary.isNotBlank()) {
                append("\n").append(entry.summary)
            }
            if (entry.rawMessage.isNotBlank()) {
                append("\n\n").append(getString(R.string.activity_history_detail_raw_line, entry.rawMessage))
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .showAccented()
    }

    private fun resolveColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun toolbarIconColor(): Int {
        val bg = AccentColor.getToolbarColor(this)
        return if (MaterialColors.isColorLight(bg)) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    private fun readableAccentTextColor(accent: Int): Int {
        return if (MaterialColors.isColorLight(accent)) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    private fun actionBarSize(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
        return android.util.TypedValue.complexToDimensionPixelSize(
            typedValue.data,
            resources.displayMetrics
        )
    }

    private enum class RangeFilter(@param:StringRes val labelRes: Int, val days: Int) {
        TODAY(R.string.stats_range_today, 1),
        WEEK(R.string.stats_range_week, 7),
        MONTH(R.string.stats_range_month, 31),
        YEAR(R.string.stats_range_year, 366),
        CUSTOM(R.string.activity_history_range_custom, 30)
    }

    private enum class TypeFilter(@param:StringRes val labelRes: Int) {
        ALL(R.string.activity_history_type_all),
        PROFILES(R.string.activity_history_type_profiles),
        SCHEDULES(R.string.activity_history_type_schedules),
        LOCATION(R.string.activity_history_type_location),
        WIFI_BLUETOOTH(R.string.activity_history_type_wifi_bluetooth),
        SCANS(R.string.activity_history_type_scans),
        TEMPORARY(R.string.activity_history_type_temporary),
        BLOCKED(R.string.activity_history_type_blocked),
        MANUAL(R.string.activity_history_type_manual),
        EMERGENCY(R.string.activity_history_type_emergency);

        fun matches(entry: ActivityHistoryRepository.Entry): Boolean {
            return when (this) {
                ALL -> true
                PROFILES -> entry.source == ActivityHistoryRepository.Source.PROFILE
                SCHEDULES -> entry.source in setOf(
                    ActivityHistoryRepository.Source.SCHEDULE,
                    ActivityHistoryRepository.Source.LOCATION,
                    ActivityHistoryRepository.Source.WIFI,
                    ActivityHistoryRepository.Source.BLUETOOTH
                )
                LOCATION -> entry.source == ActivityHistoryRepository.Source.LOCATION
                WIFI_BLUETOOTH -> entry.source == ActivityHistoryRepository.Source.WIFI ||
                    entry.source == ActivityHistoryRepository.Source.BLUETOOTH
                SCANS -> entry.source in setOf(
                    ActivityHistoryRepository.Source.NFC,
                    ActivityHistoryRepository.Source.QR,
                    ActivityHistoryRepository.Source.BARCODE
                )
                TEMPORARY -> entry.action == ActivityHistoryRepository.Action.TEMPORARY
                BLOCKED -> entry.action == ActivityHistoryRepository.Action.BLOCKED
                MANUAL -> entry.source == ActivityHistoryRepository.Source.MANUAL
                EMERGENCY -> entry.source == ActivityHistoryRepository.Source.EMERGENCY
            }
        }
    }

    private enum class SortOrder(@param:StringRes val labelRes: Int) {
        NEWEST(R.string.activity_history_sort_newest),
        OLDEST(R.string.activity_history_sort_oldest)
    }

    companion object {
        private const val MENU_INFO = 1
        private const val MENU_SORT_FILTER = 2
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

        fun intent(context: Context): Intent = Intent(context, ActivityHistoryActivity::class.java)
    }
}
