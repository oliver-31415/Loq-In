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

package com.oliver.loqin.blocking

import android.os.SystemClock

/**
 * Which app the user chose to open from the pause screen. A grant covers the current visit only:
 * the accessibility service revokes it when the foreground moves to another app, so the next
 * open pauses again. Process-wide so it survives the service being re-created.
 */
object PauseGrants {
    // Hard upper bound in case a visit-end signal is missed (e.g. screen off inside the app).
    private const val MAX_GRANT_MS = 2 * 60 * 60 * 1000L

    @Volatile private var grantedPkg: String? = null
    @Volatile private var grantedAt: Long = 0L
    @Volatile private var lastPauseShownPkg: String? = null
    @Volatile private var lastPauseShownAt: Long = 0L

    fun grant(pkg: String) {
        grantedPkg = pkg
        grantedAt = SystemClock.elapsedRealtime()
    }

    fun isGranted(pkg: String): Boolean =
        grantedPkg == pkg && SystemClock.elapsedRealtime() - grantedAt < MAX_GRANT_MS

    /** Called when the foreground visit moves to [pkg]; ends any grant for a different app. */
    fun onVisitChanged(pkg: String) {
        if (grantedPkg != null && grantedPkg != pkg) grantedPkg = null
    }

    /** Debounces repeated events so one open shows one pause screen. */
    fun shouldShowPause(pkg: String, windowMs: Long = 2_000L): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastPauseShownPkg == pkg && now - lastPauseShownAt < windowMs) return false
        lastPauseShownPkg = pkg
        lastPauseShownAt = now
        return true
    }
}
