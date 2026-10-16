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
 * App limits and today's per-profile usage live in `loqin_prefs` under keys that embed the
 * profile name. Their stores have no rename hooks of their own, so a renamed profile used to drop
 * its limits (and today's usage), and a recreated name inherited a deleted profile's limits.
 */
internal object ProfileLimitKeys {

    private const val PREFS = "loqin_prefs"

    // "<prefix><profile>__<pkg>", profile name as is.
    private val LIMIT_PREFIXES = listOf(
        "usage_limit_min__",
        "session_limit_min__",
        "attempt_limit__",
        "usage_limit_reset__",
    )

    // "open_count_<yyyymmdd>__<profile>__<pkg>", profile name as is.
    private const val OPEN_COUNT_PREFIX = "open_count_"

    // "<prefix><yyyymmdd>_<profile>_<pkg>", underscores inside each part doubled.
    private val DAY_PREFIXES = listOf(
        "profile_usage_day_",
        "limit_reached_",
    )

    private const val NFC_TAG_PROFILE_PREFIX = "nfc_tag_action_profile_"

    fun onProfileRenamed(context: Context, old: String, new: String) {
        if (old == new || old.isBlank() || new.isBlank()) return
        ProfileUsageStore.flush(context)
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val others = ProfileStore.getProfiles(context) - old - new
        val all = sp.all
        sp.edit {
            for ((key, value) in all) {
                val renamed = renamedKey(key, old, new, others) ?: continue
                remove(key)
                put(renamed, value)
            }
            // Paired tags that switch to this profile follow the new name.
            for ((key, value) in all) {
                if (key.startsWith(NFC_TAG_PROFILE_PREFIX) && value == old) putString(key, new)
            }
        }
    }

    fun onProfileRemoved(context: Context, profile: String) {
        if (profile.isBlank()) return
        ProfileUsageStore.flush(context)
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val others = ProfileStore.getProfiles(context) - profile
        val all = sp.all
        val today = todayYmd()
        sp.edit {
            for (key in all.keys) {
                if (renamedKey(key, profile, profile, others) == null) continue
                // Older days are history; only today's counters would carry over to a new
                // profile with the same name.
                val day = dayOf(key)
                if (day == null || day == today) remove(key)
            }
        }
    }

    /** Copies the limit settings (not usage) to a duplicated profile. */
    fun copyProfile(context: Context, from: String, to: String) {
        if (from == to || from.isBlank() || to.isBlank()) return
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val others = ProfileStore.getProfiles(context) - from - to
        val all = sp.all
        sp.edit {
            for ((key, value) in all) {
                if (LIMIT_PREFIXES.none { key.startsWith(it) }) continue
                val copied = renamedKey(key, from, to, others) ?: continue
                put(copied, value)
            }
        }
    }

    /** The key with [old] replaced by [new] when [key] belongs to profile [old], else null. */
    internal fun renamedKey(key: String, old: String, new: String, otherProfiles: Set<String>): String? {
        for (prefix in LIMIT_PREFIXES) {
            val head = "$prefix${old}__"
            if (!key.startsWith(head)) continue
            // "Work" must not claim the keys of a profile called "Work__Home".
            if (otherProfiles.any { it.length > old.length && key.startsWith("$prefix${it}__") }) return null
            return "$prefix${new}__" + key.removePrefix(head)
        }
        if (key.startsWith(OPEN_COUNT_PREFIX)) {
            val rest = key.removePrefix(OPEN_COUNT_PREFIX)
            if (rest.length > 10 && rest.substring(0, 8).all(Char::isDigit) && rest.startsWith("__", 8)) {
                val day = OPEN_COUNT_PREFIX + rest.substring(0, 8) + "__"
                val head = "$day${old}__"
                if (key.startsWith(head) &&
                    otherProfiles.none { it.length > old.length && key.startsWith("$day${it}__") }
                ) {
                    return "$day${new}__" + key.removePrefix(head)
                }
            }
            return null
        }
        for (prefix in DAY_PREFIXES) {
            if (!key.startsWith(prefix)) continue
            val rest = key.removePrefix(prefix)
            if (rest.length < 10 || rest[8] != '_' || !rest.substring(0, 8).all(Char::isDigit)) continue
            val ymd = rest.substring(0, 8)
            val owner = "${safe(old)}_"
            val tail = rest.substring(9)
            // A doubled underscore after the owner means a longer profile name ("old_x").
            if (!tail.startsWith(owner) || tail.getOrNull(owner.length) == '_') continue
            return "$prefix${ymd}_${safe(new)}_" + tail.removePrefix(owner)
        }
        return null
    }

    /** The yyyymmdd of a per-day key, or null for limit settings. */
    private fun dayOf(key: String): String? {
        for (prefix in DAY_PREFIXES + OPEN_COUNT_PREFIX) {
            if (!key.startsWith(prefix)) continue
            return key.removePrefix(prefix).take(8).takeIf { it.length == 8 && it.all(Char::isDigit) }
        }
        return null
    }

    private fun todayYmd(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d%02d%02d".format(
            java.util.Locale.US,
            c.get(java.util.Calendar.YEAR),
            c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

    private fun android.content.SharedPreferences.Editor.put(key: String, value: Any?) {
        @Suppress("UNCHECKED_CAST")
        when (value) {
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Boolean -> putBoolean(key, value)
            is Float -> putFloat(key, value)
            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value as Set<String>)
        }
    }

    private fun safe(part: String): String = part.replace("_", "__")
}
