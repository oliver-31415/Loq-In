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
import android.widget.FrameLayout
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
import com.oliver.loqin.ui.RuleTileStyle

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
        val outlineVariant = ContextCompat.getColor(ctx, R.color.foqos_outline_variant)
        val strong: Int = RuleTileStyle.strongAccent(ctx)
        val wash: Int = ColorUtils.compositeColors(ColorUtils.setAlphaComponent(strong, 0x2E), surface)
        val washText: Int = AccentColor.readableOn(strong, wash, onSurface, 4.5)
        val onTint: Int = ColorUtils.compositeColors(ColorUtils.setAlphaComponent(strong, 0x14), surface)
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
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(ctx, 4) }
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
        val card = newCard(ctx, p, vertical = false).apply {
            setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16))
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            foreground = ripple(ctx)
            contentDescription = "$title, $subtitle"
            ViewCompat.setStateDescription(this, stateDescription)
            asSwitch(this)
        }

        val roundel = FrameLayout(ctx).apply {
            background = oval(p.wash)
            setPadding(dp(ctx, 11), dp(ctx, 11), dp(ctx, 11), dp(ctx, 11))
            addView(glyph(ctx, R.drawable.play_arrow_24, p.strong), FrameLayout.LayoutParams(MATCH, MATCH))
        }
        card.addView(roundel, LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44)))

        val texts = box(ctx, vertical = true).apply {
            addView(label(ctx, title, 16f, p.onSurface, medium))
            addView(label(ctx, subtitle, 13.5f, p.onSurfaceVariant))
        }
        card.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(ctx, 14) })

        val thumb = if (state == MasterState.MIXED) R.drawable.remove_24 else R.drawable.loqin_switch_thumb_icon
        val sw = switchFor(ctx, checked = state == MasterState.ON, thumb = thumb).apply {
            if (locked) alpha = 0.45f
        }
        card.addView(sw, LinearLayout.LayoutParams(WRAP, WRAP))

        card.setOnClickListener { onClick() }
        return card
    }

    fun appCard(
        ctx: Context,
        model: AppCardModel,
        onRowClick: (key: String, row: View) -> Unit,
        onSeeWhere: () -> Unit,
        onExtraShorts: () -> Unit,
    ): View {
        val p = Palette(ctx)
        val card = newCard(ctx, p, vertical = true)

        card.addView(header(ctx, p, model))

        model.outcome?.let { outcomeText ->
            card.addView(outcomeRow(ctx, p, outcomeText, model, onSeeWhere))
        }

        for (row in model.rows) {
            card.addView(divider(ctx, p))
            card.addView(sectionRow(ctx, p, row, onRowClick))
        }

        model.extraShorts?.let { (title, subtitle) ->
            card.addView(extraShortsTile(ctx, p, title, subtitle, onExtraShorts))
        }

        model.footnote?.let { note ->
            card.addView(divider(ctx, p))
            card.addView(footnoteRow(ctx, p, note, model.footnoteWarning))
        }
        return card
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

        val texts = box(ctx, vertical = true).apply {
            addView(label(ctx, title, 15f, p.onSurface, medium))
            addView(label(ctx, subtitle, 13f, p.onSurfaceVariant))
        }

        val chevronRes = if (expanded) R.drawable.keyboard_arrow_up_24 else R.drawable.keyboard_arrow_down_24
        val rowView = newCard(ctx, p, vertical = false).apply {
            minimumHeight = dp(ctx, 64)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
            isClickable = true
            isFocusable = true
            foreground = ripple(ctx)
            ViewCompat.setStateDescription(this, expandedState)
            addView(stack, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(ctx, 12) })
            addView(glyph(ctx, chevronRes, p.onSurfaceVariant), LinearLayout.LayoutParams(WRAP, WRAP))
        }
        rowView.setOnClickListener { onClick() }
        return rowView
    }

    // ---------------------------------------------------------------- app card parts

    private fun header(ctx: Context, p: Palette, model: AppCardModel): View {
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

        return box(ctx).apply {
            minimumHeight = dp(ctx, 56)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 6))
            addView(iconView, LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)))
            addView(nameView, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(ctx, 12) })
            addView(pill, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(ctx, 8) })
        }
    }

    private fun outcomeRow(
        ctx: Context,
        p: Palette,
        outcomeText: String,
        model: AppCardModel,
        onSeeWhere: () -> Unit,
    ): View {
        val blended = ColorUtils.blendARGB(p.onSurfaceVariant, p.onSurface, 0.35f)
        return box(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(ctx, 64), 0, dp(ctx, 4), 0)
            addView(label(ctx, outcomeText, 13.5f, blended), LinearLayout.LayoutParams(0, WRAP, 1f))
            if (model.showSeeWhere) {
                val seeWhere = MaterialButton(ctx, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                    text = model.seeWhereLabel
                    isAllCaps = false
                    setTextColor(p.strong)
                    minHeight = dp(ctx, 48)
                    minWidth = 0
                    contentDescription = model.seeWhereCd
                    setOnClickListener { onSeeWhere() }
                }
                addView(seeWhere, LinearLayout.LayoutParams(WRAP, WRAP))
            }
        }
    }

    private fun sectionRow(ctx: Context, p: Palette, row: SectionRowModel, onRowClick: (String, View) -> Unit): View {
        val iconFrame = FrameLayout(ctx).apply {
            background = oval(p.wash)
            addView(
                glyph(ctx, row.iconRes, p.strong),
                FrameLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20), Gravity.CENTER),
            )
        }

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
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                }
                addView(summary, LinearLayout.LayoutParams(MATCH, WRAP))
            }
        }

        val sw = switchFor(ctx, checked = row.on, thumb = R.drawable.loqin_switch_thumb_icon).apply {
            if (row.pending) alpha = 0.6f
            if (row.lockedDisabled) alpha = 0.45f
        }

        val rowView = box(ctx).apply {
            minimumHeight = dp(ctx, 64)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
            if (row.on) setBackgroundColor(p.onTint)
            foreground = ripple(ctx)
            isClickable = true
            isFocusable = true
            contentDescription = listOfNotNull(row.title, row.description).joinToString(", ")
            ViewCompat.setStateDescription(this, row.stateDescription)
            asSwitch(this)
            addView(iconFrame, LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)))
            addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(ctx, 14) })
            addView(sw, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(ctx, 8) })
        }
        rowView.setOnClickListener { onRowClick(row.key, rowView) }
        return rowView
    }

    private fun extraShortsTile(
        ctx: Context,
        p: Palette,
        title: String,
        subtitle: String,
        onExtraShorts: () -> Unit,
    ): View {
        val texts = box(ctx, vertical = true).apply {
            addView(label(ctx, title, 14.5f, p.onSurface, medium))
            addView(label(ctx, subtitle, 12.5f, p.onSurfaceVariant))
        }
        val tile = box(ctx).apply {
            background = fill(p.surfaceVariant, dpF(ctx, 16))
            minimumHeight = dp(ctx, 56)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10))
            foreground = ripple(ctx)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 12))
            }
            addView(glyph(ctx, R.drawable.security_24, p.strong), LinearLayout.LayoutParams(dp(ctx, 22), dp(ctx, 22)))
            addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(ctx, 14) })
            addView(
                glyph(ctx, R.drawable.keyboard_arrow_right_24, p.onSurfaceVariant),
                LinearLayout.LayoutParams(WRAP, WRAP),
            )
        }
        tile.setOnClickListener { onExtraShorts() }
        return tile
    }

    private fun footnoteRow(ctx: Context, p: Palette, note: String, warning: Boolean): View {
        val ink = if (warning) ContextCompat.getColor(ctx, R.color.in_app_warn_fg) else p.onSurfaceVariant
        return box(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 14))
            if (warning) setBackgroundColor(ContextCompat.getColor(ctx, R.color.in_app_warn_bg))
            val iconRes = if (warning) R.drawable.info_24 else R.drawable.check_circle_24
            addView(glyph(ctx, iconRes, ink), LinearLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16)))
            addView(
                label(ctx, note, 12.5f, ink),
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(ctx, 8) },
            )
        }
    }

    // ---------------------------------------------------------------- building blocks

    private fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun dpF(ctx: Context, v: Int): Float = v * ctx.resources.displayMetrics.density

    /** Page card: surface fill, 24dp corners, rows clipped to its outline. */
    private fun newCard(ctx: Context, p: Palette, vertical: Boolean): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            background = fill(p.surface, dpF(ctx, 24))
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(ctx, 12) }
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

    private fun divider(ctx: Context, p: Palette): View =
        View(ctx).apply {
            setBackgroundColor(p.outlineVariant)
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(ctx, 1))
        }

    private fun fill(color: Int, radius: Float, stroke: Int = 0, strokeWidth: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun oval(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun ripple(ctx: Context): Drawable? {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return ContextCompat.getDrawable(ctx, tv.resourceId)
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
