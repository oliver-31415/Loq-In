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

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.PageUi
import com.oliver.loqin.ui.RuleTileStyle
import com.oliver.loqin.ui.widgets.ListGroupLayout

enum class MasterState { ON, OFF, MIXED }

data class SectionRowModel(
    val key: String,            // surfaceKey, e.g. "yt:shorts"
    val iconRes: Int,
    val title: String,
    val description: String?,   // neutral, may be null
    val on: Boolean,            // shown state (switch checked)
    val tag: String?,           // "Blocked" / "Allowed" / "Pending" / null
    val tagAccent: Boolean,     // true = accent wash tag, false = neutral tag
    val pending: Boolean,
    val lockedDisabled: Boolean, // can't change now (protection on); dim switch, still clickable
    val stateDescription: String, // spoken state, e.g. "Blocked" or "Off · locked while Loq In is on"
)

data class AppCardModel(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val pillText: String,       // "1 of 5 blocked" / "Not installed" / "Disabled"
    val pillAccent: Boolean,
    val outcome: String?,       // null = no outcome line
    val showSeeWhere: Boolean,
    val seeWhereLabel: String,  // "See where"
    val seeWhereCd: String,     // "See where in YouTube"
    val rows: List<SectionRowModel>,
    val extraShorts: Pair<String, String>?, // (title, subtitle) or null
    val footnote: String?,      // null = none
    val footnoteWarning: Boolean,
)

object InAppRuleViews {

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /** Resolved colours for one build pass. */
    private class Palette(ctx: Context) {
        val surface = ContextCompat.getColor(ctx, R.color.foqos_surface)
        val surfaceVariant = ContextCompat.getColor(ctx, R.color.foqos_surface_variant)
        val onSurface = ContextCompat.getColor(ctx, R.color.foqos_on_surface)
        val onSurfaceVariant = ContextCompat.getColor(ctx, R.color.foqos_on_surface_variant)
        val strong: Int = RuleTileStyle.strongAccent(ctx)
        val wash: Int = ColorUtils.compositeColors(ColorUtils.setAlphaComponent(strong, 0x2E), surface)
        val washText: Int = AccentColor.readableOn(strong, wash, onSurface, 4.5)
    }

    // ---------------------------------------------------------------- public

    fun lockBanner(ctx: Context, text: String): View {
        val p = Palette(ctx)
        return box(ctx).apply {
            minimumHeight = dp(ctx, 48)
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            gravity = Gravity.CENTER_VERTICAL
            background = fill(p.surfaceVariant, dpF(ctx, 16))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = ctx.resources.getDimensionPixelSize(R.dimen.block_gap)
            }
            addView(glyph(ctx, R.drawable.lock_24, p.onSurface), LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)))
            addView(
                label(ctx, text, 14f, p.onSurface),
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(ctx, 10) },
            )
        }
    }

    fun masterCard(
        ctx: Context,
        title: String,
        subtitle: String,
        state: MasterState,
        stateDescription: String,
        locked: Boolean,
        onClick: () -> Unit,
    ): View {
        val p = Palette(ctx)
        val row = listRow(ctx).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "$title, $subtitle"
            ViewCompat.setStateDescription(this, stateDescription)
            asSwitch(this)
            addView(rowIcon(ctx, R.drawable.play_arrow_24, p.strong), rowIconParams(ctx))
            addView(rowTexts(ctx, title, subtitle), rowTextParams(ctx))
            val thumb = if (state == MasterState.MIXED) R.drawable.remove_24 else R.drawable.loqin_switch_thumb_icon
            val sw = switchFor(ctx, checked = state == MasterState.ON, thumb = thumb).apply {
                if (locked) alpha = 0.45f
            }
            addView(sw, rowSwitchParams(ctx))
        }
        row.setOnClickListener { onClick() }
        return newGroup(ctx).apply { addView(row) }
    }

    fun appCard(
        ctx: Context,
        model: AppCardModel,
        onRowClick: (key: String, row: View) -> Unit,
        onSeeWhere: () -> Unit,
        onExtraShorts: () -> Unit,
    ): View {
        val p = Palette(ctx)
        // One grouped list per app: header (with its outcome line), a switch row per section,
        // then the optional Extra Shorts row and footnote as further segments.
        val group = newGroup(ctx)

        group.addView(header(ctx, p, model, onSeeWhere))

        for (row in model.rows) {
            group.addView(sectionRow(ctx, p, row, onRowClick))
        }

        model.extraShorts?.let { (title, subtitle) ->
            group.addView(extraShortsRow(ctx, p, title, subtitle, onExtraShorts))
        }

        model.footnote?.let { note ->
            group.addView(footnoteRow(ctx, p, note, model.footnoteWarning))
        }
        return group
    }

    fun notInstalledRow(
        ctx: Context,
        letters: List<String>,
        title: String,
        subtitle: String,
        expanded: Boolean,
        expandedState: String,
        onClick: () -> Unit,
    ): View {
        val p = Palette(ctx)

        val stack = box(ctx).apply {
            letters.take(3).forEachIndexed { i, letter ->
                val square = TextView(ctx).apply {
                    text = letter
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(p.onSurfaceVariant)
                    background = fill(p.surfaceVariant, dpF(ctx, 8), p.surface, dp(ctx, 2))
                }
                addView(
                    square,
                    LinearLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28)).apply {
                        if (i > 0) marginStart = -dp(ctx, 6)
                    },
                )
            }
        }

        val chevronRes = if (expanded) R.drawable.keyboard_arrow_up_24 else R.drawable.keyboard_arrow_down_24
        val rowView = listRow(ctx).apply {
            isClickable = true
            isFocusable = true
            ViewCompat.setStateDescription(this, expandedState)
            addView(stack, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(rowTexts(ctx, title, subtitle), rowTextParams(ctx))
            addView(rowChevron(ctx, chevronRes, p.onSurfaceVariant), rowChevronParams(ctx))
        }
        rowView.setOnClickListener { onClick() }
        return newGroup(ctx).apply { addView(rowView) }
    }

    // ---------------------------------------------------------------- app card parts

    // Header segment: app icon, name and count pill; the outcome line and "See where" sit under
    // the name. A 28dp icon plus a 12dp gap puts the name on the row text column (24dp + 16dp).
    private fun header(ctx: Context, p: Palette, model: AppCardModel, onSeeWhere: () -> Unit): View {
        val nameView = label(ctx, model.appName, 17f, p.onSurface, medium).apply {
            maxLines = 2
            ViewCompat.setAccessibilityHeading(this, true)
            contentDescription = "${model.appName}, ${model.pillText}"
        }

        val pill = if (model.pillAccent) {
            label(ctx, model.pillText, 12.5f, p.washText, medium)
        } else {
            label(ctx, model.pillText, 12.5f, p.onSurfaceVariant)
        }.apply {
            setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4))
            background = fill(if (model.pillAccent) p.wash else p.surfaceVariant, dpF(ctx, 99))
        }

        val iconView: View = model.icon?.let { drawable ->
            ImageView(ctx).apply { setImageDrawable(drawable) }
        } ?: glyph(ctx, R.drawable.apps_24, p.onSurfaceVariant)

        val titleLine = box(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(iconView, LinearLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28)))
            addView(nameView, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(ctx, 12) })
            addView(pill, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(ctx, 8) })
        }

        val res = ctx.resources
        return box(ctx, vertical = true).apply {
            minimumHeight = res.getDimensionPixelSize(R.dimen.row_min_height_one_line)
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(
                res.getDimensionPixelSize(R.dimen.row_padding_start),
                res.getDimensionPixelSize(R.dimen.row_padding_vertical),
                res.getDimensionPixelSize(R.dimen.row_padding_end),
                res.getDimensionPixelSize(R.dimen.row_padding_vertical),
            )
            addView(titleLine, LinearLayout.LayoutParams(MATCH, WRAP))
            model.outcome?.let { outcomeText ->
                addView(outcomeLine(ctx, p, outcomeText, model, onSeeWhere), LinearLayout.LayoutParams(MATCH, WRAP))
            }
        }
    }

    private fun outcomeLine(
        ctx: Context,
        p: Palette,
        outcomeText: String,
        model: AppCardModel,
        onSeeWhere: () -> Unit,
    ): View {
        return box(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(ctx, 28 + 12), dp(ctx, 4), 0, 0)
            val outcome = TextView(ctx).apply {
                TextViewCompat.setTextAppearance(this, R.style.TextAppearance_LoqIn_RowSummary)
                text = outcomeText
            }
            addView(outcome, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (model.showSeeWhere) {
                val seeWhere = MaterialButton(ctx, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                    TextViewCompat.setTextAppearance(this, R.style.TextAppearance_LoqIn_Button)
                    text = model.seeWhereLabel
                    isAllCaps = false
                    setTextColor(p.strong)
                    minHeight = ctx.resources.getDimensionPixelSize(R.dimen.touch_target)
                    minWidth = 0
                    contentDescription = model.seeWhereCd
                    setOnClickListener { onSeeWhere() }
                }
                addView(seeWhere, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(ctx, 8) })
            }
        }
    }

    private fun sectionRow(ctx: Context, p: Palette, row: SectionRowModel, onRowClick: (String, View) -> Unit): View {
        val titleView = TextView(ctx).apply {
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_LoqIn_RowTitle)
            text = row.title
            maxLines = 2
        }

        val tagText = row.tag
        val firstLine = box(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(titleView, LinearLayout.LayoutParams(WRAP, WRAP))
            if (tagText != null) {
                val tag = TextView(ctx).apply {
                    text = tagText
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(ctx, 7), dp(ctx, 1), dp(ctx, 7), dp(ctx, 1))
                    background = fill(if (row.tagAccent) p.wash else p.surfaceVariant, dpF(ctx, 8))
                    setTextColor(if (row.tagAccent) p.washText else p.onSurface)
                }
                addView(tag, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(ctx, 6) })
            }
        }

        val texts = box(ctx, vertical = true).apply {
            addView(firstLine, LinearLayout.LayoutParams(MATCH, WRAP))
            row.description?.let { desc ->
                val summary = TextView(ctx).apply {
                    TextViewCompat.setTextAppearance(this, R.style.TextAppearance_LoqIn_RowSummary)
                    text = desc
                }
                addView(summary, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(ctx, 2) })
            }
        }

        val sw = switchFor(ctx, checked = row.on, thumb = R.drawable.loqin_switch_thumb_icon).apply {
            if (row.pending) alpha = 0.6f
            if (row.lockedDisabled) alpha = 0.45f
        }

        val rowView = listRow(ctx).apply {
            isClickable = true
            isFocusable = true
            contentDescription = listOfNotNull(row.title, row.description).joinToString(", ")
            ViewCompat.setStateDescription(this, row.stateDescription)
            asSwitch(this)
            addView(rowIcon(ctx, row.iconRes, p.strong), rowIconParams(ctx))
            addView(texts, rowTextParams(ctx))
            addView(sw, rowSwitchParams(ctx))
        }
        rowView.setOnClickListener { onRowClick(row.key, rowView) }
        return rowView
    }

    private fun extraShortsRow(
        ctx: Context,
        p: Palette,
        title: String,
        subtitle: String,
        onExtraShorts: () -> Unit,
    ): View {
        val row = listRow(ctx).apply {
            isClickable = true
            isFocusable = true
            addView(rowIcon(ctx, R.drawable.security_24, p.strong), rowIconParams(ctx))
            addView(rowTexts(ctx, title, subtitle), rowTextParams(ctx))
            addView(rowChevron(ctx, R.drawable.keyboard_arrow_right_24, p.onSurfaceVariant), rowChevronParams(ctx))
        }
        row.setOnClickListener { onExtraShorts() }
        return row
    }

    // Footnote segment: a status line (LoqIn.RowStatus) on the row text column.
    private fun footnoteRow(ctx: Context, p: Palette, note: String, warning: Boolean): View {
        val status = if (warning) PageUi.Status.WARNING else PageUi.Status.NEUTRAL
        val ink = if (warning) ContextCompat.getColor(ctx, R.color.status_warning) else p.onSurfaceVariant
        val res = ctx.resources
        return box(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(
                res.getDimensionPixelSize(R.dimen.row_padding_start),
                dp(ctx, 12),
                res.getDimensionPixelSize(R.dimen.row_padding_end),
                dp(ctx, 12),
            )
            val iconRes = if (warning) R.drawable.info_24 else R.drawable.check_circle_24
            addView(rowIcon(ctx, iconRes, ink), rowIconParams(ctx))
            val text = TextView(ctx).apply {
                TextViewCompat.setTextAppearance(this, status.styleRes)
                this.text = note
            }
            addView(text, rowTextParams(ctx))
        }
    }

    // ---------------------------------------------------------------- building blocks

    private fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun dpF(ctx: Context, v: Int): Float = v * ctx.resources.displayMetrics.density

    /** A grouped list (LoqIn.ListGroup look) with the block gap above it. */
    private fun newGroup(ctx: Context): ListGroupLayout =
        ListGroupLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = ctx.resources.getDimensionPixelSize(R.dimen.block_gap)
            }
        }

    /** A row with the LoqIn.ListRow paddings and minimum height. */
    private fun listRow(ctx: Context): LinearLayout {
        val res = ctx.resources
        return box(ctx).apply {
            minimumHeight = res.getDimensionPixelSize(R.dimen.row_min_height)
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(
                res.getDimensionPixelSize(R.dimen.row_padding_start),
                res.getDimensionPixelSize(R.dimen.row_padding_vertical),
                res.getDimensionPixelSize(R.dimen.row_padding_end),
                res.getDimensionPixelSize(R.dimen.row_padding_vertical),
            )
        }
    }

    private fun rowIcon(ctx: Context, res: Int, tint: Int): ImageView =
        glyph(ctx, res, tint).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }

    private fun rowIconParams(ctx: Context): LinearLayout.LayoutParams {
        val size = ctx.resources.getDimensionPixelSize(R.dimen.row_icon_size)
        return LinearLayout.LayoutParams(size, size)
    }

    private fun rowTexts(ctx: Context, title: String, subtitle: String): LinearLayout =
        box(ctx, vertical = true).apply {
            addView(TextView(ctx).apply {
                TextViewCompat.setTextAppearance(this, R.style.TextAppearance_LoqIn_RowTitle)
                text = title
            })
            addView(
                TextView(ctx).apply {
                    TextViewCompat.setTextAppearance(this, R.style.TextAppearance_LoqIn_RowSummary)
                    text = subtitle
                },
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(ctx, 2) },
            )
        }

    private fun rowTextParams(ctx: Context): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, WRAP, 1f).apply {
            marginStart = ctx.resources.getDimensionPixelSize(R.dimen.row_icon_gap)
        }

    private fun rowChevron(ctx: Context, res: Int, tint: Int): ImageView = rowIcon(ctx, res, tint)

    private fun rowChevronParams(ctx: Context): LinearLayout.LayoutParams =
        rowIconParams(ctx).apply { marginStart = dp(ctx, 8) }

    private fun rowSwitchParams(ctx: Context): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(WRAP, WRAP).apply {
            marginStart = ctx.resources.getDimensionPixelSize(R.dimen.row_trailing_gap)
        }

    private fun box(ctx: Context, vertical: Boolean = false): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }

    private fun label(ctx: Context, value: String, sp: Float, color: Int, face: Typeface? = null): TextView =
        TextView(ctx).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (face != null) typeface = face
        }

    private fun glyph(ctx: Context, res: Int, tint: Int): ImageView =
        ImageView(ctx).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(tint)
        }

    private fun fill(color: Int, radius: Float, stroke: Int = 0, strokeWidth: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun switchFor(ctx: Context, checked: Boolean, thumb: Int): MaterialSwitch =
        MaterialSwitch(ctx).apply {
            isChecked = checked
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            thumbIconDrawable = ContextCompat.getDrawable(ctx, thumb)
            CustomAccentApplier.tintSwitch(this)
        }

    /** Makes TalkBack announce the view as a switch. */
    private fun asSwitch(view: View) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Switch"
            }
        })
    }
}
