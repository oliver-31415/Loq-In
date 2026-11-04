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

package com.oliver.loqin.feature.profiles

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.shape.RelativeCornerSize
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.EmergencyPinStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.TempPauseStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.RuleTileStyle
import com.oliver.loqin.ui.SheetRows
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.EmergencyPinDialog
import com.oliver.loqin.ui.dialog.LoqInInfoRow
import com.oliver.loqin.ui.dialog.showLoqInInfoDialog
import com.oliver.loqin.ui.dialog.showLoqInNumberInput
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.ui.widgets.ListGroupLayout
import com.oliver.loqin.util.EditingLockGuard

private const val MAX_COUNT = 100
private const val MAX_MINUTES = 1440

/** More breaks than this and the per-break segments get too thin; a plain bar takes over. */
private const val MAX_SEGMENTS = 10

/**
 * Per-profile break limits. The Today card says what's left (big number, bars that empty as
 * breaks are used); below it each limit picks a value from preset chips, with "Other" for any
 * amount. While protection is on everything stays readable but nothing can be changed.
 */
class BreaksActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PROFILE_NAME = "extra_profile_name"
    }

    private enum class Cap(
        @StringRes val labelRes: Int,
        @StringRes val subRes: Int,
        @DrawableRes val iconRes: Int,
        @StringRes val hintRes: Int,
        @StringRes val errorRes: Int,
        @StringRes val infoRes: Int,
        val max: Int,
        val presets: List<Int>,
        val minutes: Boolean,
    ) {
        COUNT(
            R.string.temp_pause_section_count, R.string.breaks_limit_count_sub, R.drawable.calendar_month_24,
            R.string.temp_pause_section_count, R.string.temp_pause_error_count_fmt,
            R.string.temp_pause_section_count_subtitle, MAX_COUNT, listOf(1, 2, 3), minutes = false,
        ),
        PER_PAUSE(
            R.string.temp_pause_section_length, R.string.breaks_limit_length_sub, R.drawable.timer_24,
            R.string.minutes_hint, R.string.temp_pause_error_minutes_fmt,
            R.string.temp_pause_section_length_subtitle, MAX_MINUTES, listOf(10, 15, 30), minutes = true,
        ),
        PER_DAY(
            R.string.temp_pause_section_total, R.string.breaks_limit_total_sub, R.drawable.schedule_24,
            R.string.minutes_hint, R.string.temp_pause_error_minutes_fmt,
            R.string.temp_pause_section_total_subtitle, MAX_MINUTES, listOf(15, 30, 60), minutes = true,
        ),
    }

    private lateinit var todayHero: View
    private lateinit var todayBig: TextView
    private lateinit var todaySuffix: TextView
    private lateinit var todayTitle: TextView
    private lateinit var todaySegments: LinearLayout
    private lateinit var todayMinutes: TextView
    private lateinit var todayMinutesBar: LinearProgressIndicator
    private lateinit var todayNext: TextView
    private lateinit var btnReset: MaterialButton
    private lateinit var lockNote: View
    private lateinit var groupLimits: ListGroupLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_breaks)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        todayHero = findViewById(R.id.todayHero)
        todayBig = findViewById(R.id.todayBig)
        todaySuffix = findViewById(R.id.todaySuffix)
        todayTitle = findViewById(R.id.todayTitle)
        todaySegments = findViewById(R.id.todaySegments)
        todayMinutes = findViewById(R.id.todayMinutes)
        todayMinutesBar = findViewById(R.id.todayMinutesBar)
        todayNext = findViewById(R.id.todayNext)
        btnReset = findViewById(R.id.btnResetToday)
        lockNote = findViewById(R.id.lockNote)
        groupLimits = findViewById(R.id.groupLimits)
        findViewById<View>(R.id.headerLimits).findViewById<TextView>(R.id.tvHeader).text =
            getString(R.string.breaks_page_limits_header)
        btnReset.setOnClickListener { resetToday(it) }

        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        toolbar.setBackgroundColor(AccentColor.getToolbarBackgroundColor(this))
        toolbar.subtitle = getString(R.string.app_rules_profile_subtitle, currentProfile())
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        render()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, R.id.action_breaks_info, Menu.NONE, R.string.profile_sheet_pauses)
            .setIcon(R.drawable.info_24)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == R.id.action_breaks_info) {
            showLoqInInfoDialog(
                title = getString(R.string.profile_sheet_pauses),
                rows = Cap.entries.map { cap ->
                    LoqInInfoRow(label = getString(cap.labelRes), value = getString(cap.infoRes))
                },
            )
            true
        } else {
            super.onOptionsItemSelected(item)
        }

    override fun onResume() {
        super.onResume()
        if (::groupLimits.isInitialized) render()
    }

    private fun currentProfile(): String =
        intent.getStringExtra(EXTRA_PROFILE_NAME)?.trim()?.takeIf { it.isNotBlank() }
            ?: ProfileStore.getCurrent(this) ?: "default"

    private fun surface(): Int = ContextCompat.getColor(this, R.color.foqos_surface)
    private fun ink(): Int = MaterialColors.getColor(todayBig, com.google.android.material.R.attr.colorOnSurface)

    /** Accent strong enough to carry meaning (bars, selected chips) on the card surface. */
    private fun strongAccent(): Int =
        RuleTileStyle.strongAccent(this)

    private fun render() {
        CustomAccentApplier.applyIfNeeded(this)
        val profile = currentProfile()
        val locked = EditingLockGuard.isLocked(this)
        val caps = TempPauseStore.getCaps(this, profile)
        val usedCount = TempPauseStore.usedCountToday(this, profile)
        val usedMinutes = TempPauseStore.usedMinutesToday(this, profile)

        renderToday(profile, caps, usedCount, usedMinutes, locked)

        lockNote.isVisible = locked
        groupLimits.removeAllViews()
        limitSection(Cap.COUNT, caps.maxCountPerDay, locked)
        limitSection(Cap.PER_PAUSE, caps.maxMinutesPerPause, locked)
        limitSection(Cap.PER_DAY, caps.maxMinutesPerDay, locked)
        groupLimits.refreshSegments()
    }

    private fun renderToday(profile: String, caps: TempPauseStore.Caps, usedCount: Int, usedMinutes: Int, locked: Boolean) {
        val nextMax = TempPauseStore.maxAllowedDurationMinutes(this, profile)
        val countLeft = TempPauseStore.remainingPauses(this, profile)
        val minutesLeft = TempPauseStore.remainingMinutes(this, profile)
        val hasCaps = TempPauseStore.hasCaps(this, profile)

        // Hero: breaks left if counted, else minutes left; a plain title when nothing is capped
        // or today is used up.
        val hero: Pair<Int, String>? = when {
            !hasCaps || nextMax <= 0 -> null
            caps.maxCountPerDay > 0 -> countLeft to resources.getQuantityString(
                R.plurals.breaks_hero_count_suffix, caps.maxCountPerDay, caps.maxCountPerDay,
            )
            caps.maxMinutesPerDay > 0 ->
                minutesLeft to getString(R.string.breaks_hero_minutes_suffix_fmt, caps.maxMinutesPerDay)
            else -> null
        }
        todayHero.isVisible = hero != null
        todayTitle.isVisible = hero == null
        if (hero != null) {
            todayBig.text = hero.first.toString()
            todaySuffix.text = hero.second
            todayHero.contentDescription = "${hero.first} ${hero.second}"
        } else {
            todayTitle.text = getString(
                if (hasCaps) R.string.breaks_today_none_left else R.string.breaks_page_summary_unlimited
            )
        }

        val strong = strongAccent()
        val track = ColorUtils.compositeColors(ColorUtils.setAlphaComponent(strong, 0x2E), surface())

        val segments = caps.maxCountPerDay.takeIf { it in 1..MAX_SEGMENTS }
        todaySegments.isVisible = segments != null
        if (segments != null) {
            todaySegments.removeAllViews()
            repeat(segments) { i ->
                todaySegments.addView(View(this).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = SheetRows.dp(this@BreaksActivity, 4f).toFloat()
                        setColor(if (i < countLeft.coerceAtMost(segments)) strong else track)
                    }
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        if (i > 0) marginStart = SheetRows.dp(this@BreaksActivity, 6f)
                    }
                })
            }
        }

        val dailyCap = caps.maxMinutesPerDay
        // With minutes as the hero (no count limit) the line would repeat it, so it only shows
        // alongside a count hero, or as "used" when minutes aren't capped.
        val minutesIsHero = hero != null && caps.maxCountPerDay <= 0
        todayMinutes.isVisible = !minutesIsHero && (dailyCap > 0 || usedMinutes > 0)
        todayMinutes.text = if (dailyCap > 0) {
            getString(R.string.breaks_minutes_left_fmt, minutesLeft, dailyCap)
        } else {
            getString(R.string.breaks_minutes_used_fmt, usedMinutes)
        }
        todayMinutesBar.isVisible = dailyCap > 0
        if (dailyCap > 0) {
            todayMinutesBar.setIndicatorColor(strong)
            todayMinutesBar.trackColor = track
            todayMinutesBar.max = dailyCap
            todayMinutesBar.setProgressCompat(minutesLeft.coerceIn(0, dailyCap), false)
        }

        todayNext.isVisible = nextMax in 1 until Int.MAX_VALUE
        if (todayNext.isVisible) todayNext.text = getString(R.string.breaks_today_next_fmt, nextMax)

        btnReset.isVisible = usedCount > 0 || usedMinutes > 0
        // Locked: still shown (so the card doesn't jump), faded, and a tap explains why.
        btnReset.alpha = if (locked) 0.45f else 1f
    }

    private fun Cap.chipText(value: Int): String = value.toString()

    private fun Cap.spoken(value: Int): String =
        if (minutes) getString(R.string.breaks_page_minutes_fmt, value) else value.toString()

    private fun limitSection(cap: Cap, value: Int, locked: Boolean) {
        val ctx = this
        val section = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val padH = SheetRows.dp(ctx, 18f)
            setPadding(padH, SheetRows.dp(ctx, 16f), SheetRows.dp(ctx, 14f), SheetRows.dp(ctx, 14f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val strong = strongAccent()
        section.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(ImageView(ctx).apply {
                setImageResource(cap.iconRes)
                imageTintList = ColorStateList.valueOf(strong)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(SheetRows.dp(ctx, 24f), SheetRows.dp(ctx, 24f)).apply {
                    marginEnd = SheetRows.dp(ctx, 16f)
                }
            })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(ctx).apply {
                    text = getString(cap.labelRes)
                    androidx.core.widget.TextViewCompat.setTextAppearance(this, R.style.TextAppearance_LoqIn_RowTitle)
                    ViewCompat.setAccessibilityHeading(this, true)
                })
                addView(TextView(ctx).apply {
                    text = getString(cap.subRes)
                    androidx.core.widget.TextViewCompat.setTextAppearance(this, R.style.TextAppearance_LoqIn_RowSummary)
                })
            })
        })

        val chips = ChipGroup(ctx).apply {
            chipSpacingHorizontal = SheetRows.dp(ctx, 8f)
            chipSpacingVertical = 0
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = SheetRows.dp(ctx, 6f) }
        }
        val custom = value > 0 && value !in cap.presets
        chips.addView(limitChip(getString(R.string.breaks_page_no_limit), null, value <= 0, locked) {
            writeCap(cap, 0)
        })
        cap.presets.forEach { preset ->
            chips.addView(limitChip(cap.chipText(preset), cap.spoken(preset), value == preset, locked) {
                writeCap(cap, preset)
            })
        }
        // A custom amount takes the Other chip's place, with its unit (no preset beside it to
        // explain the number) and a pencil.
        val otherChip = if (custom) {
            limitChip(cap.spoken(value), getString(R.string.breaks_chip_custom_cd, cap.spoken(value)), true, locked, edit = true) {
                pickCap(cap, value)
            }
        } else {
            limitChip(getString(R.string.breaks_chip_other), null, false, locked) { pickCap(cap, value) }
        }
        chips.addView(otherChip)
        section.addView(chips)
        groupLimits.addView(section)
    }

    /**
     * One limit choice. Not a checkable Chip: the selected look is drawn here so a locked page
     * can keep the chosen value fully readable (a lock in place of the check) while the rest fade.
     */
    private fun limitChip(
        label: String,
        spoken: String?,
        selected: Boolean,
        locked: Boolean,
        edit: Boolean = false,
        onPick: () -> Unit,
    ): Chip {
        val ctx = this
        val strong = strongAccent()
        val ink = ink()
        val outline = ContextCompat.getColor(ctx, R.color.foqos_outline)
        val muted = MaterialColors.getColor(todayBig, android.R.attr.textColorSecondary)
        return Chip(ctx).apply {
            text = label
            isCheckable = false
            chipMinHeight = SheetRows.dp(ctx, 40f).toFloat()
            textStartPadding = SheetRows.dp(ctx, 3f).toFloat()
            textEndPadding = SheetRows.dp(ctx, 3f).toFloat()
            shapeAppearanceModel = shapeAppearanceModel.withCornerSize(RelativeCornerSize(0.5f))
            setTextAppearanceResource(R.style.TextAppearance_LoqIn_RowSummary)
            textSize = 14f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = Gravity.CENTER
            minWidth = SheetRows.dp(ctx, 48f)
            when {
                selected && locked -> {
                    val fill = ContextCompat.getColor(ctx, R.color.foqos_surface_variant)
                    chipBackgroundColor = ColorStateList.valueOf(fill)
                    chipStrokeWidth = 0f
                    setTextColor(ink)
                    chipIcon = ContextCompat.getDrawable(ctx, R.drawable.lock_24)
                    chipIconTint = ColorStateList.valueOf(muted)
                }
                selected -> {
                    val fill = ColorUtils.compositeColors(ColorUtils.setAlphaComponent(strong, 0x2E), surface())
                    val onFill = AccentColor.readableOn(strong, fill, ink, 4.5)
                    chipBackgroundColor = ColorStateList.valueOf(fill)
                    chipStrokeWidth = 0f
                    setTextColor(onFill)
                    chipIcon = ContextCompat.getDrawable(ctx, R.drawable.check_circle_24)
                    chipIconTint = ColorStateList.valueOf(onFill)
                }
                else -> {
                    chipBackgroundColor = ColorStateList.valueOf(surface())
                    chipStrokeWidth = SheetRows.dp(ctx, 1f).toFloat()
                    chipStrokeColor = ColorStateList.valueOf(
                        if (locked) outline else ColorUtils.blendARGB(outline, ink, 0.15f)
                    )
                    setTextColor(if (locked) ColorUtils.setAlphaComponent(ink, 0x73) else ink)
                }
            }
            isChipIconVisible = selected
            chipIconSize = SheetRows.dp(ctx, 18f).toFloat()
            iconStartPadding = if (selected) SheetRows.dp(ctx, 6f).toFloat() else 0f
            if (edit && !locked) {
                closeIcon = ContextCompat.getDrawable(ctx, R.drawable.edit_24)
                closeIconTint = chipIconTint
                closeIconSize = SheetRows.dp(ctx, 16f).toFloat()
                isCloseIconVisible = true
                setOnCloseIconClickListener { onPick() }
                closeIconContentDescription = spoken
            }
            contentDescription = spoken ?: label
            ViewCompat.setStateDescription(this, if (selected) getString(R.string.state_on) else null)
            setOnClickListener {
                if (locked) it.showWarnPill(R.string.edit_locked_manage_temp_pauses) else onPick()
            }
        }
    }

    private fun pickCap(cap: Cap, current: Int) {
        showLoqInNumberInput(
            title = getString(cap.labelRes),
            hint = getString(cap.hintRes),
            confirmText = getString(R.string.save),
            range = 1..cap.max,
            invalidMessage = getString(cap.errorRes, cap.max),
            initialValue = current.takeIf { it > 0 },
        ) { writeCap(cap, it) }
    }

    /** Saves one cap and keeps the other two as they are. */
    private fun writeCap(cap: Cap, value: Int) {
        if (EditingLockGuard.isLocked(this)) {
            findViewById<View>(R.id.root).showWarnPill(R.string.edit_locked_manage_temp_pauses)
            return
        }
        val profile = currentProfile()
        val caps = TempPauseStore.getCaps(this, profile)
        TempPauseStore.setCaps(
            this,
            profile,
            TempPauseStore.Caps(
                maxCountPerDay = if (cap == Cap.COUNT) value else caps.maxCountPerDay,
                maxMinutesPerPause = if (cap == Cap.PER_PAUSE) value else caps.maxMinutesPerPause,
                maxMinutesPerDay = if (cap == Cap.PER_DAY) value else caps.maxMinutesPerDay,
            )
        )
        render()
    }

    private fun resetToday(anchor: View) {
        if (EditingLockGuard.isLocked(this)) {
            anchor.showWarnPill(R.string.edit_locked_manage_temp_pauses)
            return
        }

        fun doReset() {
            TempPauseStore.resetToday(this, currentProfile())
            anchor.showWarnPill(R.string.temp_pause_reset_success)
            render()
        }

        if (EmergencyPinStore.hasPin(this)) {
            EmergencyPinDialog.showEnterPin(this, onSuccess = { doReset() })
        } else {
            EmergencyPinDialog.showSetPin(this) { doReset() }
        }
    }
}
