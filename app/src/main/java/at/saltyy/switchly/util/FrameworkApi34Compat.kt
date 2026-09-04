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

package at.saltyy.switchly.util

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import at.saltyy.switchly.R

/**
 * Defensive runtime guard for API-34 system images that report SDK 34+ but are missing framework members that AndroidX Core legitimately expects to exist on API 34.
 * Crashlytics has seen these finalized API-34 members missing on the same Android 14 / Pixel 8 Pro image:
 *  - AccessibilityAction.ACTION_SCROLL_IN_DIRECTION
 *  - TextView.setLineHeight(int, float)
 *  - WindowInsets.Type.systemOverlays()
 *
 * AndroidX Core checks SDK_INT and links those members directly.
 * If the system image is internally inconsistent, that direct link throws NoSuchFieldError / NoSuchMethodError.
 * We detect the broken framework surface without touching AndroidX accessibility classes.
 */
object FrameworkApi34Compat {
    private const val TAG = "SwitchlyApi34Compat"

    private data class Surface(
        val scrollInDirection: Boolean,
        val unitLineHeight: Boolean,
        val systemOverlays: Boolean,
    ) {
        val complete: Boolean get() = scrollInDirection && unitLineHeight && systemOverlays
    }

    private val api34Surface: Surface by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (Build.VERSION.SDK_INT < 34) {
            Surface(scrollInDirection = true, unitLineHeight = true, systemOverlays = true)
        } else {
            val scrollActionPresent = runCatching {
                AccessibilityNodeInfo.AccessibilityAction::class.java
                    .getField("ACTION_SCROLL_IN_DIRECTION")
            }.isSuccess

            val lineHeightPresent = runCatching {
                TextView::class.java.getMethod(
                    "setLineHeight",
                    Int::class.javaPrimitiveType!!,
                    Float::class.javaPrimitiveType!!,
                )
            }.isSuccess

            val systemOverlaysPresent = runCatching {
                WindowInsets.Type::class.java.getMethod("systemOverlays")
            }.isSuccess

            Surface(
                scrollInDirection = scrollActionPresent,
                unitLineHeight = lineHeightPresent,
                systemOverlays = systemOverlaysPresent,
            )
        }
    }

    // True only for a framework that claims API 34+ while missing finalized API-34 members.
    fun needsCrashShield(): Boolean = Build.VERSION.SDK_INT >= 34 && !api34Surface.complete

    /**
     * AndroidX Core 1.16+ maps compat inset masks through the API-34 WindowInsets.Type.systemOverlays() method.
     * A malformed system image that reports SDK 34 while missing that method will crash before an OnApplyWindowInsetsListener is invoked.
     * On those devices Switchly keeps Android 14's normal decor-fitting behavior instead of installing AndroidX edge-to-edge/insets listeners.
     */
    fun needsWindowInsetsCrashShield(): Boolean =
        Build.VERSION.SDK_INT >= 34 && !api34Surface.systemOverlays

    /**
     * Keep malformed API-34 images away from AndroidX's Impl34 inset conversion entirely.
     * The platform can still fit the content window normally on Android 14.
     * Consuming the dispatch at android.R.id.content prevents Material/AppCompat child views from invoking their own WindowInsetsCompat listeners, which would link systemOverlays() before the app callback gets a chance to recover.
     */
    fun applyWindowInsetsWorkaround(activity: Activity) {
        if (!needsWindowInsetsCrashShield()) return
        // needsWindowInsetsCrashShield() is only true on API 34+, but keep the platform API boundary explicit so lint can verify the minSdk 27 path.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Api30.applyWindowInsetsWorkaround(activity)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private object Api30 {
        fun applyWindowInsetsWorkaround(activity: Activity) {
            runCatching { WindowCompat.setDecorFitsSystemWindows(activity.window, true) }
            val content = activity.findViewById<View>(android.R.id.content) ?: return
            content.setOnApplyWindowInsetsListener { _, _ -> WindowInsets.CONSUMED }
        }
    }

    /**
     * Must run after the activity chose its accent theme and before setContentView().
     * AppCompat 1.7+ and Material text appearances can otherwise reach TextViewCompat's API-34 unit-aware line-height overload while XML is still inflating.
     * The overlay redirects those appearances to line-height-free AppCompat typography only on the detected inconsistent framework image.
     */
    fun applyThemeWorkaround(activity: Activity) {
        if (!needsCrashShield()) return
        activity.theme.applyStyle(R.style.SwitchlyApi34FrameworkCompatOverlay, true)
        suppressAccessibilityTree(activity)
        logOnce()
    }

    /**
     * Hide the app's view tree from accessibility traversal only on the inconsistent framework.
     * This prevents Material/AndroidX accessibility delegates from linking the missing API-34 action while keeping accessibility fully enabled on conforming Android builds.
     */
    fun suppressAccessibilityTree(activity: Activity) {
        if (!needsCrashShield()) return
        activity.window?.decorView?.importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    /**
     * Backup guard for activities that do not pass through ThemeUtils.
     * ThemeUtils reapplies the theme overlay after setTheme(), while these callbacks keep the accessibility shield active.
     */
    fun installActivityCrashShield(application: Application) {
        if (!needsCrashShield()) return

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                applyThemeWorkaround(activity)
                applyWindowInsetsWorkaround(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                suppressAccessibilityTree(activity)
                applyWindowInsetsWorkaround(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                suppressAccessibilityTree(activity)
                applyWindowInsetsWorkaround(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                suppressAccessibilityTree(activity)
                applyWindowInsetsWorkaround(activity)
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        logOnce()
    }

    private var logged = false

    @Synchronized
    private fun logOnce() {
        if (logged || !needsCrashShield()) return
        logged = true
        Log.w(
            TAG,
            "Enabled API-34 framework crash shield: " +
                "ACTION_SCROLL_IN_DIRECTION=${api34Surface.scrollInDirection}, " +
                "TextView#setLineHeight(unit)=${api34Surface.unitLineHeight}, " +
                "WindowInsets.Type#systemOverlays=${api34Surface.systemOverlays}, " +
                "sdk=${Build.VERSION.SDK_INT}",
        )
    }
}
