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

package at.saltyy.switchly.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.preference.PreferenceManager
import at.saltyy.switchly.R
import at.saltyy.switchly.theme.AccentColor
import at.saltyy.switchly.theme.CustomAccentApplier
import at.saltyy.switchly.util.FrameworkApi34Compat

object ThemeUtils {

    fun applyAccentTheme(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val accent = prefs.getString("pref_accent", "default") ?: "default"

        val themeRes = when (accent) {
            "blue"   -> R.style.Theme_Switchly_Accent_Blue
            "orange" -> R.style.Theme_Switchly_Accent_Orange
            "purple" -> R.style.Theme_Switchly_Accent_Purple
            "pink"   -> R.style.Theme_Switchly_Accent_Pink
            "teal"   -> R.style.Theme_Switchly_Accent_Teal
            "red"    -> R.style.Theme_Switchly_Accent_Red
            "amber"  -> R.style.Theme_Switchly_Accent_Amber
            "gray"   -> R.style.Theme_Switchly_Accent_Gray
            "custom" -> R.style.Theme_Switchly
            else     -> R.style.Theme_Switchly
        }

        activity.setTheme(themeRes)

        // A small number of API-34 system images report SDK 34 while missing finalized framework members used by current AndroidX. 
        // Apply the defensive overlay only there.
        FrameworkApi34Compat.applyThemeWorkaround(activity)

        // Run one shared late UI pass after inflation.
        // Custom accents first replace any remaining compile-time theme green; the consistency pass then normalizes late-bound widget states.
        activity.window?.decorView?.post {
            if (accent == "custom") {
                runCatching { CustomAccentApplier.applyIfNeeded(activity) }
            }
            runCatching { retintUnthemedPrimaryIcons(activity) }
            runCatching { UiConsistency.apply(activity) }
        }
    }

    /**
     * Safety net for icons whose `?attr/colorPrimary` fails to pick up the accent
     * theme variant (observed on Settings rows): anything still carrying the
     * compile-time default green gets the live accent at runtime. Neutral tints
     * (chevrons, white icons) are left alone — only the exact default green matches.
     */
    private fun retintUnthemedPrimaryIcons(activity: Activity) {
        val accent = AccentColor.getAccentColorInt(activity)
        val fallback = activity.getColor(R.color.accent_default_green) and 0x00FFFFFF
        val root = activity.findViewById<View>(android.R.id.content) ?: return

        fun walk(v: View) {
            when (v) {
                is ViewGroup -> for (i in 0 until v.childCount) walk(v.getChildAt(i))
                is ImageView -> v.imageTintList?.let { list ->
                    if ((list.defaultColor and 0x00FFFFFF) == fallback) {
                        v.imageTintList = ColorStateList.valueOf(accent)
                    }
                }
            }
        }

        walk(root)
        // Late passes for rows bound after the first layout.
        longArrayOf(200L, 600L).forEach { delay ->
            root.postDelayed({ runCatching { walk(root) } }, delay)
        }
    }
}
