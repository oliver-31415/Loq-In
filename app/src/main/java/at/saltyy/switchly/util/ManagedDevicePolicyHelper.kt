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

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import at.saltyy.switchly.data.prefs.AppLogStore
import at.saltyy.switchly.security.AppLockStore
import at.saltyy.switchly.receiver.DPMReceiver
import java.util.concurrent.Executors

object ManagedDevicePolicyHelper {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SwitchlyManagedPolicy").apply { isDaemon = true }
    }

    fun syncSelfUninstallBlock(context: Context) {
        val appContext = context.applicationContext
        executor.execute { syncSelfUninstallBlockNow(appContext) }
    }

    // Returns null when Switchly is not a managed owner or Android cannot report the policy.
    fun isSelfUninstallBlocked(context: Context): Boolean? {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return null
        val hasManagedOwnership = dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
        if (!hasManagedOwnership) return null
        val admin = ComponentName(context, DPMReceiver::class.java)
        return runCatching { dpm.isUninstallBlocked(admin, context.packageName) }.getOrNull()
    }

    /**
     * Returns whether managed Android policy explicitly disables user control over Switchly.
     * This policy is available from Android 11 (API 30) and covers actions such as Force Stop and clearing app data. 
     * Older Android versions can still protect active Device Admin apps through the system Settings implementation, but there is no equivalent public policy API.
     */
    fun isSelfUserControlDisabled(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return null
        val hasManagedOwnership = dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
        if (!hasManagedOwnership) return null
        val admin = ComponentName(context, DPMReceiver::class.java)
        return runCatching {
            context.packageName in dpm.getUserControlDisabledPackages(admin).orEmpty()
        }.getOrNull()
    }

    private fun syncSelfUninstallBlockNow(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        val admin = ComponentName(context, DPMReceiver::class.java)
        val hasManagedOwnership = dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
        if (!hasManagedOwnership) return

        val shouldProtect = AppLockStore.isStrictProtectionEnabled(context)
        runCatching {
            dpm.setUninstallBlocked(admin, context.packageName, shouldProtect)
            AppLogStore.append(context, "ManagedPolicy", "setUninstallBlocked self=$shouldProtect")
        }.onFailure {
            AppLogStore.append(context, "ManagedPolicy", "setUninstallBlocked failed: ${it.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val current = dpm.getUserControlDisabledPackages(admin).orEmpty()
                val updated = LinkedHashSet(current)
                val changed = if (shouldProtect) {
                    updated.add(context.packageName)
                } else {
                    updated.remove(context.packageName)
                }
                if (changed) {
                    dpm.setUserControlDisabledPackages(admin, updated.toList())
                }
                AppLogStore.append(
                    context,
                    "ManagedPolicy",
                    "setUserControlDisabledPackages self=$shouldProtect changed=$changed",
                )
            }.onFailure {
                AppLogStore.append(context, "ManagedPolicy", "setUserControlDisabledPackages failed: ${it.message}")
            }
        }
    }
}
