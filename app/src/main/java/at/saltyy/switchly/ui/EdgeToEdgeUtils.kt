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

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import at.saltyy.switchly.util.FrameworkApi34Compat

/**
 * System-bars setup for classic Views/XML screens.
 * Switchly's UI is mostly "classic" (Toolbar + content below).
 * Edge-to-edge (decorFitsSystemWindows=false) made the toolbar look like a
 * "double topbar" (status bar area + toolbar area) and caused unreadable titles on some screens.
 * We run edge-to-edge (decorFitsSystemWindows=false) but apply insets in a consistent way:
 * - Toolbar gets status-bar top inset as padding (so title/menu are clickable).
 * - BottomNav gets navigation-bar bottom inset.
 * - Content root gets left/right + bottom system-bar insets.
 */
object EdgeToEdgeUtils {

    /**
     * Classic mode (no drawing behind system bars).
     * Use this for screens where you want the status bar to keep the system look (no accent color “bleeding” into it) and where BottomNavigationView should sit naturally above the nav bar without extra padding.
     */
    fun setupClassic(
        activity: androidx.appcompat.app.AppCompatActivity,
        toolbar: View? = null,
        bottomNav: View? = null
    ) {
        // A few malformed Android 14 images report SDK 34 while missing the API-34 WindowInsets.Type.systemOverlays() method.
        // Any AndroidX WindowInsetsCompat listener crashes before our callback on those images.
        // Android 14 does not enforce edge-to-edge, so keep the platform's normal decor-fitting behavior.
        if (FrameworkApi34Compat.needsWindowInsetsCrashShield()) {
            FrameworkApi34Compat.applyWindowInsetsWorkaround(activity)
            return
        }

        // Android 15+ enforces edge-to-edge for targetSdk 35+.
        // Do not try to opt back out with decorFitsSystemWindows=true; use the compatibility helper and apply the system-bar insets required by Switchly's classic XML screens explicitly.
        WindowCompat.enableEdgeToEdge(activity.window)

        // Every current classic Toolbar lives in an AppBarLayout with fitsSystemWindows=true.
        // Do not add the status-bar inset a second time to the Toolbar itself.
        toolbar?.let { ViewCompat.setOnApplyWindowInsetsListener(it, null) }

        bottomNav?.let { bn ->
            val initialBottom = bn.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(bn) { v, insets ->
                val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom
                v.updatePadding(bottom = maxOf(initialBottom, nav, gestures))
                insets
            }
        }

        // Keep classic screens clear of side cutouts and the bottom system bar. When a
        // BottomNavigationView is present it owns the bottom inset to avoid double-padding.
        activity.findViewById<View>(android.R.id.content)?.let { content ->
            val initialLeft = content.paddingLeft
            val initialRight = content.paddingRight
            val initialBottom = content.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(
                    left = initialLeft + bars.left,
                    right = initialRight + bars.right,
                    bottom = if (bottomNav == null) initialBottom + bars.bottom else initialBottom
                )
                insets
            }
        }

        ViewCompat.requestApplyInsets(activity.window.decorView)
    }

    /**
     * Adds a small, consistent "nice" spacing for BottomNavigationView on gesture navigation.
     * Some devices report 0 navigationBars() inset in classic mode (decorFitsSystemWindows=true), but still have a gesture area.
     * Using systemGestures() makes the bottom items sit higher, matching the look of the Schedules screen.
     */
    fun applyBottomNavGestureInset(bottomNav: View) {
        if (FrameworkApi34Compat.needsWindowInsetsCrashShield()) {
            return
        }
        val initialBottom = bottomNav.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val gest = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom
            val target = maxOf(nav, gest)
            v.updatePadding(bottom = maxOf(initialBottom, target))
            insets
        }
        ViewCompat.requestApplyInsets(bottomNav)
    }

    fun setup(
        activity: androidx.appcompat.app.AppCompatActivity,
        toolbar: View? = null,
        bottomNav: View? = null,
        contentRoot: View? = null
    ) {
        if (FrameworkApi34Compat.needsWindowInsetsCrashShield()) {
            FrameworkApi34Compat.applyWindowInsetsWorkaround(activity)
            return
        }
        WindowCompat.enableEdgeToEdge(activity.window)

        // Edge-to-edge is enforced on Android 15+; apply insets manually.

        toolbar?.let { tb ->
            val initialTop = tb.paddingTop
            ViewCompat.setOnApplyWindowInsetsListener(tb) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                // Preserve initial padding; add status-bar height on top.
                v.updatePadding(top = initialTop + bars.top)
                insets
            }
        }

        // Bottom nav: some OEMs need a little help with gesture navigation.
        bottomNav?.let { bn ->
            val initialBottom = bn.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(bn) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                // Avoid double-padding (OEMs/Material may already include some bottom padding)
                v.updatePadding(bottom = maxOf(initialBottom, bars.bottom))
                insets
            }
        }

        // Optional: content root gets left/right + bottom if you want. (Often you don't want top here because toolbar already handles it.)
        contentRoot?.let { root ->
            val initialBottom = root.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(left = bars.left, right = bars.right, bottom = initialBottom + bars.bottom)
                insets
            }
        }

        // Make sure insets are applied now (some devices need an explicit request).
        ViewCompat.requestApplyInsets(activity.window.decorView)
    }
}
