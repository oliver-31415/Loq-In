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
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.RuleTileStyle
import com.oliver.loqin.ui.SheetRows
import com.oliver.loqin.ui.dialog.padForNavigationBar

/**
 * "See where" sheet for an app's in-app rules: a small phone sketch with the app's
 * tab bar, struck through and badged where a section is off, and one sentence.
 */
object InAppWhereSheet {

    private class Tab(val labelRes: Int, val iconRes: Int, val surfaceKey: String?)

    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    /** Tab bar per app, in on-screen order. A null surfaceKey is a tab Loq In does not control. */
    private val tabMap: Map<String, List<Tab>> = mapOf(
        "com.google.android.youtube" to listOf(
            Tab(R.string.in_app_where_tab_home, R.drawable.loqin_home_nav_24, null),
            Tab(R.string.in_app_surface_shorts_label, R.drawable.play_arrow_24, "yt:shorts"),
            Tab(R.string.in_app_where_tab_create, R.drawable.add_24, null),
            Tab(R.string.in_app_surface_subscriptions_label, R.drawable.list_alt_24, "yt:subscriptions"),
            Tab(R.string.in_app_surface_you_label, R.drawable.account_box_24, "yt:you"),
        ),
        "com.instagram.android" to listOf(
            Tab(R.string.in_app_where_tab_home, R.drawable.loqin_home_nav_24, null),
            Tab(R.string.in_app_surface_search_label, R.drawable.search_24, "ig:search"),
            Tab(R.string.in_app_where_tab_create, R.drawable.add_24, null),
            Tab(R.string.in_app_surface_reels_label, R.drawable.play_arrow_24, "ig:reels"),
            Tab(R.string.in_app_where_tab_profile, R.drawable.account_box_24, null),
        ),
        "com.twitter.android" to listOf(
            Tab(R.string.in_app_surface_home_label, R.drawable.loqin_home_nav_24, "x:foryou"),
            Tab(R.string.in_app_surface_search_label, R.drawable.search_24, "x:search"),
            Tab(R.string.in_app_surface_grok_label, R.drawable.star_24, "x:grok"),
            Tab(R.string.in_app_surface_notifications_label, R.drawable.notifications_24, "x:notifications"),
            Tab(R.string.in_app_where_tab_messages, R.drawable.mail_24, null),
        ),
        "com.snapchat.android" to listOf(
            Tab(R.string.in_app_surface_map_label, R.drawable.location_on_24, "snap:map"),
            Tab(R.string.in_app_where_tab_chat, R.drawable.mail_24, null),
            Tab(R.string.in_app_where_tab_camera, R.drawable.photo_camera_24, null),
            Tab(R.string.in_app_surface_stories_label, R.drawable.dashboard_24, "snap:stories"),
            Tab(R.string.in_app_surface_spotlight_label, R.drawable.star_24, "snap:spotlight"),
        ),
        "com.facebook.katana" to listOf(
            Tab(R.string.in_app_where_tab_home, R.drawable.loqin_home_nav_24, null),
            Tab(R.string.in_app_surface_reels_label, R.drawable.play_arrow_24, "fb:reels"),
            Tab(R.string.in_app_where_tab_friends, R.drawable.account_box_24, null),
            Tab(R.string.in_app_surface_marketplace_label, R.drawable.list_alt_24, "fb:marketplace"),
            Tab(R.string.in_app_where_tab_menu, R.drawable.apps_24, null),
        ),
    )

    /** The feed placeholder that "Shorts/Reels in your feed" live in, for apps that have one. */
    private val feedShelfKey: Map<String, String> = mapOf(
        "com.google.android.youtube" to "yt:shorts",
        "com.instagram.android" to "ig:reels",
        "com.facebook.katana" to "fb:reels",
    )

    /** Surface key to its label. Order here is the order used when listing several surfaces. */
    private val labelRes: Map<String, Int> = mapOf(
        "yt:shorts" to R.string.in_app_surface_shorts_label,
        "yt:subscriptions" to R.string.in_app_surface_subscriptions_label,
        "yt:you" to R.string.in_app_surface_you_label,
        "yt:miniplayer" to R.string.in_app_surface_mini_player_label,
        "yt:pip" to R.string.in_app_surface_pip_label,
        "ig:reels" to R.string.in_app_surface_reels_label,
        "ig:search" to R.string.in_app_surface_search_label,
        "ig:stories" to R.string.in_app_surface_stories_label,
        "x:foryou" to R.string.in_app_surface_home_label,
        "x:search" to R.string.in_app_surface_search_label,
        "x:grok" to R.string.in_app_surface_grok_label,
        "x:notifications" to R.string.in_app_surface_notifications_label,
        "snap:spotlight" to R.string.in_app_surface_spotlight_label,
        "snap:stories" to R.string.in_app_surface_stories_label,
        "snap:map" to R.string.in_app_surface_map_label,
        "snap:following" to R.string.in_app_surface_following_label,
        "fb:reels" to R.string.in_app_surface_reels_label,
        "fb:marketplace" to R.string.in_app_surface_marketplace_label,
    )

    fun show(
        activity: AppCompatActivity,
        appName: String,
        appIcon: Drawable?,
        packageName: String,
        blockedSurfaceKeys: Set<String>,
        allowMode: Boolean,
        allowedSurfaceKeys: Set<String>,
    ) {
        val tabs = tabMap[packageName] ?: return
        val ctx: Context = activity

        fun dp(v: Int): Int = SheetRows.dp(ctx, v.toFloat())
        fun color(id: Int): Int = ContextCompat.getColor(ctx, id)

        // A surface is "off" when it is blocked (block mode), or not in the allowed set (allow mode).
        fun isOff(key: String?): Boolean {
            if (key == null) return false
            return if (allowMode) key !in allowedSurfaceKeys else key in blockedSurfaceKeys
        }

        fun labelsOf(keys: Collection<String>): List<Int> = keys.mapNotNull { labelRes[it] }
        fun names(labels: List<Int>): String = labels.joinToString(", ") { ctx.getString(it) }

        val tabSurfaceKeys = tabs.mapNotNullTo(mutableSetOf()) { it.surfaceKey }
        val offTabLabels = tabs.filter { isOff(it.surfaceKey) }.map { it.labelRes }
        val allowedLabels = labelsOf(labelRes.keys.filter { it in allowedSurfaceKeys })
        // Sections outside this app's tab bar that are still blocked.
        val alsoLabels = labelsOf(
            labelRes.keys.filter { key ->
                key !in tabSurfaceKeys && key in blockedSurfaceKeys &&
                    (!allowMode || key !in allowedSurfaceKeys)
            },
        )

        val mainText: String? = when {
            allowMode && allowedLabels.isEmpty() -> ctx.getString(R.string.in_app_where_allow_none)
            allowMode -> ctx.getString(R.string.in_app_where_allow_fmt, names(allowedLabels))
            offTabLabels.isNotEmpty() -> ctx.getString(R.string.in_app_where_block_fmt, names(offTabLabels))
            else -> null
        }
        val alsoText: String? = alsoLabels.takeIf { it.isNotEmpty() }
            ?.let { ctx.getString(R.string.in_app_where_also_fmt, names(it)) }

        val surface = color(R.color.foqos_surface)
        val surfaceVariant = color(R.color.foqos_surface_variant)
        val outline = color(R.color.foqos_outline)
        val onSurface = color(R.color.foqos_on_surface)
        val onSurfaceVariant = color(R.color.foqos_on_surface_variant)
        val accent = RuleTileStyle.strongAccent(ctx)

        fun block(fill: Int, radiusDp: Int, strokeColor: Int? = null, dashed: Boolean = false): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(radiusDp).toFloat()
                setColor(fill)
                if (strokeColor != null) {
                    if (dashed) {
                        setStroke(dp(2), strokeColor, dp(6).toFloat(), dp(4).toFloat())
                    } else {
                        setStroke(dp(2), strokeColor)
                    }
                }
            }

        val dialog = BottomSheetDialog(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(ctx, R.drawable.sheet_profile_bg)
            setPadding(dp(20), dp(10), dp(20), dp(34))
        }

        val header = SheetRows.header(ctx, root, ctx.getString(R.string.in_app_where_title_fmt, appName)) {
            dialog.dismiss()
        }
        if (appIcon != null) {
            (header.findViewById<TextView>(R.id.sheetTitle).parent as? LinearLayout)?.addView(
                ImageView(ctx).apply {
                    setImageDrawable(appIcon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                0,
                LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) },
            )
        }
        root.addView(header)

        // Phone sketch: 300dp wide, or the sheet width on narrow screens.
        val phoneWidth = minOf(dp(300), ctx.resources.displayMetrics.widthPixels - dp(40))
        val phone = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = block(surface, 28, outline)
            setPadding(dp(14), dp(14), dp(14), dp(10))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(
            phone,
            LinearLayout.LayoutParams(phoneWidth, WRAP).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(16)
            },
        )

        // Feed placeholder 1.
        phone.addView(
            View(ctx).apply { background = block(surfaceVariant, 14) },
            LinearLayout.LayoutParams(MATCH, dp(56)).apply { bottomMargin = dp(10) },
        )

        // Feed placeholder 2, which is the Shorts/Reels shelf when the app has one.
        val shelfOff = isOff(feedShelfKey[packageName])
        val shelf = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = if (shelfOff) {
                block(surfaceVariant, 14, accent, dashed = true)
            } else {
                block(surfaceVariant, 14)
            }
            if (shelfOff) {
                addView(
                    TextView(ctx).apply {
                        text = ctx.getString(R.string.in_app_where_shelf)
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setTextColor(AccentColor.readableOn(accent, surfaceVariant, onSurface, 4.5))
                    },
                    LinearLayout.LayoutParams(WRAP, WRAP),
                )
            }
        }
        phone.addView(shelf, LinearLayout.LayoutParams(MATCH, dp(84)).apply { bottomMargin = dp(12) })

        // Tab bar. Items keep their natural width and are spread by spacers so long labels fit.
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun addSpacer() = bar.addView(View(ctx), LinearLayout.LayoutParams(0, 0, 1f))
        addSpacer()
        tabs.forEach { tab ->
            val off = isOff(tab.surfaceKey)
            val iconBox = FrameLayout(ctx).apply {
                addView(
                    ImageView(ctx).apply {
                        setImageResource(tab.iconRes)
                        imageTintList = ColorStateList.valueOf(if (off) onSurfaceVariant else onSurface)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER),
                )
                if (off) {
                    addView(
                        ImageView(ctx).apply {
                            setImageResource(R.drawable.visibility_off_24)
                            imageTintList = ColorStateList.valueOf(Color.WHITE)
                            setPadding(dp(3), dp(3), dp(3), dp(3))
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(accent)
                            }
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            translationX = dp(2).toFloat()
                            translationY = -dp(2).toFloat()
                        },
                        FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP or Gravity.END),
                    )
                }
            }
            val label = TextView(ctx).apply {
                text = ctx.getString(tab.labelRes)
                textSize = 10.5f
                maxLines = 1
                gravity = Gravity.CENTER
                setTextColor(if (off) onSurfaceVariant else onSurface)
                if (off) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            }
            val item = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(iconBox, LinearLayout.LayoutParams(dp(40), dp(30)))
                addView(label, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(2) })
            }
            bar.addView(item, LinearLayout.LayoutParams(WRAP, WRAP))
            addSpacer()
        }
        phone.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        mainText?.let { line ->
            root.addView(
                TextView(ctx).apply {
                    text = line
                    textSize = 15f
                    setTextColor(onSurface)
                },
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) },
            )
        }
        alsoText?.let { line ->
            root.addView(
                TextView(ctx).apply {
                    text = line
                    textSize = 13f
                    setTextColor(onSurfaceVariant)
                },
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) },
            )
        }

        dialog.setContentView(root)
        dialog.padForNavigationBar()
        dialog.show()
    }
}
