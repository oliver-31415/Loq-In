/*
 * Loq In
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

package com.oliver.loqin.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import com.oliver.loqin.R
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.util.FrameworkApi34Compat

object ThemeUtils {

    /**
     * Single source of truth for the display-mode preference.
     *
     * The UI (Appearance + Settings) stores the choice under [PREF_THEME_MODE].
     * Very old installs (pre-2.0) stored it under [PREF_THEME_LEGACY]; that key
     * is only read as a fallback and migrated forward so a stale legacy value
     * can never override the current choice after a process restart (e.g.
     * overnight, when Android kills the app and [applySavedNightMode] runs
     * again on cold start).
     */
    const val PREF_THEME_MODE = "pref_theme_mode"
    const val PREF_THEME_LEGACY = "pref_theme"

    fun getSavedThemeMode(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.getString(PREF_THEME_MODE, null)?.let { return it }
        // One-time migration for pre-2.0 installs that only have the legacy key.
        val legacy = prefs.getString(PREF_THEME_LEGACY, null)
        if (legacy != null) {
            runCatching {
                prefs.edit { putString(PREF_THEME_MODE, legacy) }
            }
            return legacy
        }
        return "system"
    }

    fun applyNightMode(mode: String) {
        when (mode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun applySavedNightMode(context: Context) {
        applyNightMode(getSavedThemeMode(context))
    }

    /**
     * Apply the user-selected accent theme variant to the activity before super.onCreate().
     */
    fun applyAccentTheme(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val accent = prefs.getString("pref_accent", "default") ?: "default"

        val themeRes = when (accent) {
            "default" -> R.style.Theme_LoqIn
            "green"  -> R.style.Theme_LoqIn_Accent_Green
            "blue"   -> R.style.Theme_LoqIn_Accent_Blue
            "orange" -> R.style.Theme_LoqIn_Accent_Orange
            "purple" -> R.style.Theme_LoqIn_Accent_Purple
            "pink"   -> R.style.Theme_LoqIn_Accent_Pink
            "teal"   -> R.style.Theme_LoqIn_Accent_Teal
            "red"    -> R.style.Theme_LoqIn_Accent_Red
            "amber"  -> R.style.Theme_LoqIn_Accent_Amber
            "gray"   -> R.style.Theme_LoqIn_Accent_Gray
            "custom" -> R.style.Theme_LoqIn
            else     -> R.style.Theme_LoqIn
        }

        activity.setTheme(themeRes)

        // A small number of API-34 system images report SDK 34 while missing finalized framework members used by current AndroidX. 
        // Apply the defensive overlay only there.
        FrameworkApi34Compat.applyThemeWorkaround(activity)

        // Run one shared late UI pass after inflation.
        // Accent retinting replaces any remaining compile-time theme green; the consistency pass then normalizes late-bound widget states.
        activity.window?.decorView?.post {
            if (CustomAccentApplier.isCustomAccentEnabled(activity)) {
                runCatching { CustomAccentApplier.applyIfNeeded(activity) }
            }
            runCatching { retintUnthemedPrimaryIcons(activity) }
            runCatching { UiConsistency.apply(activity) }
        }
    }

    /**
     * Safety net for views whose `?attr/colorPrimary` fails to pick up the accent
     * theme variant (observed on Settings rows and the Schedules empty state):
     * anything still carrying the compile-time default green gets the live
     * accent at runtime. Neutral tints (chevrons, white icons) are left alone —
     * only the exact default green matches. Covers icons, buttons (text, icon,
     * stroke, filled backgrounds), FABs, and checkables.
     */
    private fun retintUnthemedPrimaryIcons(activity: Activity) {
        val accent = AccentColor.getAccentColorInt(activity)
        val fallback = activity.getColor(R.color.accent_default_blue) and 0x00FFFFFF
        val root = activity.findViewById<View>(android.R.id.content) ?: return

        fun matchesGreen(color: Int): Boolean {
            return (color and 0x00FFFFFF) == fallback
        }

        fun walk(v: View) {
            when (v) {
                is ViewGroup -> for (i in 0 until v.childCount) walk(v.getChildAt(i))
                is ImageView -> v.imageTintList?.let { list ->
                    if (matchesGreen(list.defaultColor)) {
                        v.imageTintList = ColorStateList.valueOf(accent)
                    }
                }
                is CompoundButton -> {
                    v.buttonTintList?.let { list ->
                        if (matchesGreen(list.defaultColor)) {
                            v.buttonTintList = CustomAccentApplier.buildCheckableTint(activity, accent)
                        }
                    }
                }
                is com.google.android.material.button.MaterialButton -> {
                    v.strokeColor?.let { stroke ->
                        if (matchesGreen(stroke.defaultColor)) {
                            v.strokeColor = ColorStateList.valueOf(accent)
                        }
                    }
                    if (matchesGreen(v.currentTextColor)) {
                        v.setTextColor(accent)
                    }
                    val iconTint = v.iconTint
                    if (iconTint != null && matchesGreen(iconTint.defaultColor)) {
                        v.iconTint = ColorStateList.valueOf(accent)
                    } else if (iconTint == null && (matchesGreen(v.currentTextColor) || matchesGreen(v.strokeColor?.defaultColor ?: 0))) {
                        v.iconTint = ColorStateList.valueOf(accent)
                    }
                    v.backgroundTintList?.let { bg ->
                        if (matchesGreen(bg.defaultColor) && android.graphics.Color.alpha(bg.defaultColor) > 24) {
                            v.backgroundTintList = ColorStateList.valueOf(accent)
                            val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.5) {
                                android.graphics.Color.BLACK
                            } else {
                                android.graphics.Color.WHITE
                            }
                            v.setTextColor(onAccent)
                            v.iconTint = ColorStateList.valueOf(onAccent)
                        }
                    }
                }
                is com.google.android.material.floatingactionbutton.FloatingActionButton -> {
                    v.backgroundTintList?.let { bg ->
                        if (matchesGreen(bg.defaultColor)) {
                            v.backgroundTintList = ColorStateList.valueOf(accent)
                        }
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
