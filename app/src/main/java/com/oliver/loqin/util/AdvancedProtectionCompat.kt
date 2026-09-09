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

package com.oliver.loqin.util

import android.content.Context
import android.os.Build
import android.security.advancedprotection.AdvancedProtectionManager
import androidx.annotation.RequiresApi

/**
 * Small compatibility wrapper around Android 16 Advanced Protection Mode (AAPM).
 * Android exposes the public status API from API 36 onward.
 * Older Android versions simply report false and never load the API-36 implementation object.
 */
object AdvancedProtectionCompat {

    fun isEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) {
            return false
        }
        return Api36.isEnabled(context.applicationContext)
    }

    fun registerProcessObserver(context: Context, onChanged: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 36) {
            return
        }
        Api36.register(context.applicationContext, onChanged)
    }

    @RequiresApi(36)
    private object Api36 {
        private var callback: AdvancedProtectionManager.Callback? = null

        fun isEnabled(context: Context): Boolean {
            val resolved = runCatching {
                context.getSystemService(AdvancedProtectionManager::class.java)
            }.getOrNull() ?: return false

            return runCatching { resolved.isAdvancedProtectionEnabled }.getOrDefault(false)
        }

        @Synchronized
        fun register(context: Context, onChanged: (Boolean) -> Unit) {
            if (callback != null) {
                return
            }

            val resolved = runCatching {
                context.getSystemService(AdvancedProtectionManager::class.java)
            }.getOrNull() ?: return

            val nextCallback = AdvancedProtectionManager.Callback { enabled ->
                onChanged(enabled)
            }

            val registered = runCatching {
                resolved.registerAdvancedProtectionCallback(context.mainExecutor, nextCallback)
            }.isSuccess

            if (registered) {
                callback = nextCallback
            }
        }
    }
}
