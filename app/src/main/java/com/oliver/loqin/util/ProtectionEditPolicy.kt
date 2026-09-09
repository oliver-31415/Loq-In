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
import com.oliver.loqin.data.prefs.ProfileStore

/**
 * Allows rule edits while protection is active only when the requested change can make the currently active profile stricter.
 * This intentionally does not cover limits yet; limit changes keep their existing lock until they can be compared safely across all limit types.
 */
object ProtectionEditPolicy {
    fun canTightenActiveProfile(context: Context, profile: String?): Boolean =
        canTightenProfileWhileLocked(context, profile, allowInactiveProfile = false)

    private fun canTightenProfileWhileLocked(
        context: Context,
        profile: String?,
        allowInactiveProfile: Boolean,
    ): Boolean {
        if (!EditingLockGuard.isLocked(context)) return true
        if (profile.isNullOrBlank()) return false
        return allowInactiveProfile || ProfileStore.getCurrent(context) == profile
    }

    fun canChangeSelection(
        context: Context,
        profile: String?,
        allowMode: Boolean,
        currentlySelected: Boolean,
        requestedSelected: Boolean,
        allowInactiveProfile: Boolean = false,
    ): Boolean {
        if (!EditingLockGuard.isLocked(context)) return true
        if (!canTightenProfileWhileLocked(context, profile, allowInactiveProfile)) return false
        if (currentlySelected == requestedSelected) return true

        // Block selected: selecting another item increases protection.
        // Allow selected: removing an allowed exception increases protection.
        return if (allowMode) {
            currentlySelected && !requestedSelected
        } else {
            !currentlySelected && requestedSelected
        }
    }

    fun canSaveSelection(
        context: Context,
        profile: String?,
        allowMode: Boolean,
        original: Set<String>,
        requested: Set<String>,
        allowInactiveProfile: Boolean = false,
    ): Boolean {
        if (!EditingLockGuard.isLocked(context)) return true
        if (!canTightenProfileWhileLocked(context, profile, allowInactiveProfile)) return false

        return if (allowMode) {
            requested.all { it in original }
        } else {
            original.all { it in requested }
        }
    }

    fun canAddBlockedWebsite(context: Context, profile: String?, allowMode: Boolean): Boolean {
        if (!EditingLockGuard.isLocked(context)) return true
        return canTightenActiveProfile(context, profile) && !allowMode
    }
}
