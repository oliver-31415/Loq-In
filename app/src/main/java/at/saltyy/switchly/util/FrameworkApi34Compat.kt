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
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import at.saltyy.switchly.R

/**
 * Defensive runtime guard for API-34 system images that report SDK 34+ but are missing framework members that AndroidX Core legitimately expects to exist on API 34.
 * Crashlytics has seen both of these missing on the same Android 14 / Pixel 8 Pro image:
 *  - AccessibilityAction.ACTION_SCROLL_IN_DIRECTION
 *  - TextView.setLineHeight(int, float)
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
    ) {
        val complete: Boolean get() = scrollInDirection && unitLineHeight
    }

    private val api34Surface: Surface by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (Build.VERSION.SDK_INT < 34) {
            Surface(scrollInDirection = true, unitLineHeight = true)
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

            Surface(
                scrollInDirection = scrollActionPresent,
                unitLineHeight = lineHeightPresent,
            )
        }
    }

    // True only for a framework that claims API 34+ while missing finalized API-34 members.
    fun needsCrashShield(): Boolean = Build.VERSION.SDK_INT >= 34 && !api34Surface.complete

    /**
     * Must run after the activity chose its accent theme and before setContentView().
     * Material's text-appearance line-height pass otherwise reaches TextViewCompat's API-34 overload on broken framework images.
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
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                suppressAccessibilityTree(activity)
            }

            override fun onActivityStarted(activity: Activity) = suppressAccessibilityTree(activity)
            override fun onActivityResumed(activity: Activity) = suppressAccessibilityTree(activity)
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
                "sdk=${Build.VERSION.SDK_INT}",
        )
    }
}
