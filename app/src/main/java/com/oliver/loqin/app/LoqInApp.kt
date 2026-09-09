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

package com.oliver.loqin.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.oliver.loqin.blocking.BlockingRuntime
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.data.prefs.UsageStore
import com.oliver.loqin.data.statistics.StatsPersistence
import com.oliver.loqin.feature.entry.QuickShortcutRegistrar
import com.oliver.loqin.platform.receiver.bluetooth.BluetoothTriggerMonitor
import com.oliver.loqin.platform.receiver.location.LocationTriggerMonitor
import com.oliver.loqin.platform.receiver.wifi.WifiTriggerMonitor
import com.oliver.loqin.security.AppLockManager
import com.oliver.loqin.util.LocaleHelper
import com.oliver.loqin.util.AdvancedProtectionCompat
import com.oliver.loqin.util.FrameworkApi34Compat
import com.oliver.loqin.util.ManagedDevicePolicyHelper
import com.oliver.loqin.util.PersistentStatusNotifier
import java.util.concurrent.Executors

class LoqInApp : Application() {
    private val startupExecutor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LoqInStartup").apply { isDaemon = true }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Install the API-34 compatibility shield before any activity is created.
        // It is a no-op on conforming Android framework builds.
        FrameworkApi34Compat.installActivityCrashShield(this)

        // language
        LocaleHelper.setLanguage(this, LocaleHelper.getSavedLanguage(this))

        // When the accent/theme/language changes (e.g. in Appearance), background
        // activities keep their old theme. Recreate each activity once when it is
        // shown again so the whole back stack picks up the new look in place.
        registerActivityLifecycleCallbacks(ThemeRefreshCallbacks)

        // theme
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        when (prefs.getString("pref_theme", "system")) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark"  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else    -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        AppLockManager.register(this)

        // ShortcutManagerCompat uses ShortcutService binder calls.
        // Keep it off the main startup path and only refresh when the app/shortcut spec changed.
        QuickShortcutRegistrar.refreshAsync(this)

        val appContext = applicationContext

        // Android 16 Advanced Protection can change while LoqIn is running.
        // Reconcile the limited UsageEvents fallback whenever the public AAPM state changes.
        AdvancedProtectionCompat.registerProcessObserver(appContext) {
            startupExecutor.execute {
                if (SwitchModeStore.isEnabled(appContext)) {
                    BlockingRuntime.ensureRunning(appContext)
                } else {
                    BlockingRuntime.stop(appContext)
                }
            }
        }

        // Startup work below can touch system services, Google Play services or disk.
        // Do it after Application.onCreate() returns so Android/Samsung cold starts do not get stuck in finishAttachApplication or slow binder calls.
        startupExecutor.execute {
            // Initialize the durable statistics archive before monitors can emit new counters.
            runCatching { StatsPersistence.initialize(appContext) }

            // One-time sanity cleanup for old inflated usage imports.
            runCatching { UsageStore.sanitizeImpossibleDailyTotals(appContext) }

            // Start/stop trigger monitors based on active rules.
            // These may register receivers/services/geofences and should not run on the main thread.
            runCatching { WifiTriggerMonitor.ensureStarted(appContext) }
            runCatching { BluetoothTriggerMonitor.ensureStarted(appContext) }
            runCatching { LocationTriggerMonitor.ensureStarted(appContext) }

            runCatching { ManagedDevicePolicyHelper.syncSelfUninstallBlock(appContext) }

            // Reconcile the full Accessibility runtime health and, on Android 16 Advanced Protection devices, the limited UsageEvents fallback when needed.
            if (SwitchModeStore.isEnabled(appContext)) {
                BlockingRuntime.ensureRunning(appContext)
            }
            PersistentStatusNotifier.refresh(appContext)
        }
    }

    private object ThemeRefreshCallbacks : ActivityLifecycleCallbacks {
        private val appliedSnapshot = mutableMapOf<Activity, String>()

        private fun snapshotOf(ctx: Context): String {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
            return listOf(
                "pref_accent",
                "pref_accent_custom",
                "pref_theme_mode",
                "pref_theme",
                "pref_language"
            ).joinToString("|") { prefs.getString(it, "") ?: "" }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            appliedSnapshot[activity] = snapshotOf(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            val current = snapshotOf(activity)
            val last = appliedSnapshot[activity]
            if (last == null) {
                appliedSnapshot[activity] = current
                return
            }
            if (last != current && !activity.isFinishing && !activity.isDestroyed) {
                appliedSnapshot[activity] = current
                activity.window?.decorView?.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        activity.recreate()
                    }
                }
            }
        }

        override fun onActivityDestroyed(activity: Activity) {
            appliedSnapshot.remove(activity)
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
