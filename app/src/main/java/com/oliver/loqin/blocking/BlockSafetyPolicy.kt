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

import android.content.Context
import com.oliver.loqin.util.AppBlockSafety

internal fun isHighRiskBlockTarget(context: Context, pkg: String): Boolean {
    return when (AppBlockSafety.matchRiskRule(context, pkg)?.action) {
        AppBlockSafety.PolicyAction.PROTECTED,
        AppBlockSafety.PolicyAction.STRICT_MODE_ONLY,
        AppBlockSafety.PolicyAction.WARN_ONLY -> true
        else -> false
    }
}

internal fun shouldUseLoopSafetyMode(context: Context, pkg: String): Boolean {
    return when (AppBlockSafety.matchRiskRule(context, pkg)?.action) {
        AppBlockSafety.PolicyAction.PROTECTED,
        AppBlockSafety.PolicyAction.STRICT_MODE_ONLY -> true
        else -> false
    }
}
