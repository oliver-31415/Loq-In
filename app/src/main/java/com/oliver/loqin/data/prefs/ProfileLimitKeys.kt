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

    // "<prefix><yyyymmdd>_<profile>_<pkg>", underscores inside each part doubled.
    private val DAY_PREFIXES = listOf(
        "profile_usage_day_",
        "limit_reached_",
    )

    private const val SESSION_RUNTIME_PREFIX = "usage_limit_session_runtime__"
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
            removeSessionRuntime(all.keys, old)
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
        sp.edit {
            for (key in all.keys) {
                if (renamedKey(key, profile, profile, others) != null) remove(key)
            }
            removeSessionRuntime(all.keys, profile)
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

    private fun android.content.SharedPreferences.Editor.removeSessionRuntime(keys: Set<String>, profile: String) {
        val head = SESSION_RUNTIME_PREFIX + safe(profile) + "__"
        keys.filter { it.startsWith(head) }.forEach { remove(it) }
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
