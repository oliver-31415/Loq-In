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
import com.oliver.loqin.data.prefs.UsageStore

/**
 * Single source of truth for app usage "today" when enforcement/timers are involved.
 * This intentionally uses LoqIn's internal per-day counter.
 * User-facing Statistics may use Android Usage Access for whole-device screen-time reporting, but enforcement stays local because some devices/OEMs publish delayed or stale UsageStats values that could otherwise trigger a limit early.
 */
object AppUsageToday {
    fun getUsageMsToday(
        ctx: Context,
        pkg: String,
        now: Long = System.currentTimeMillis()
    ): Long {
        return runCatching { UsageStore.getUsageMsToday(ctx, pkg) }.getOrDefault(0L)
    }
}
