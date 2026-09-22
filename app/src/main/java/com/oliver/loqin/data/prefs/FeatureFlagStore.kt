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

package com.oliver.loqin.data.prefs

import android.content.Context
import androidx.core.content.edit

/**
 * Feature switches for staged functionality.
 * Experimental behavior can ship disabled without branching the release build.
 */
object FeatureFlagStore {

    private const val PREFS = "loqin_feature_flags"

    enum class Flag(val key: String, val defaultEnabled: Boolean) {
        DIAGNOSTIC_TIMELINE("diagnostic_timeline", true),
        AUTOMATION_SAVE_PREVIEW("automation_save_preview", true),
        AUTOMATION_ENGINE_V2("automation_engine_v2", false),
        CONTINUOUS_USAGE_TRIGGER("continuous_usage_trigger", false),
    }

    fun isEnabled(context: Context, flag: Flag): Boolean =
        prefs(context).getBoolean(flag.key, flag.defaultEnabled)

    fun setEnabled(context: Context, flag: Flag, enabled: Boolean) {
        prefs(context).edit { putBoolean(flag.key, enabled) }
        runCatching {
            DiagnosticsTimelineStore.record(context, "FeatureFlag", flag.key, "enabled=$enabled")
        }
    }

    fun snapshot(context: Context): Map<Flag, Boolean> =
        Flag.entries.associateWith { isEnabled(context, it) }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
