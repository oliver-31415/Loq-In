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
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/**
 * Per-domain visit limits, scoped to the active profile.
 * - session limit: minutes per visit (resets when the user leaves the site)
 * - visit limit: visits (opens) allowed per day
 */
object DomainVisitLimitStore {

    private const val PREFIX_SESSION_MIN = "domain_visit_min_"
    private const val PREFIX_VISIT_COUNT = "domain_visit_count_"
    private const val PROFILE_SEGMENT = "__p__"

    private fun prefs(ctx: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(ctx)

    private fun sanitizeProfile(profile: String): String =
        profile.trim().ifBlank { "default" }
            .replace("_", "__")
            .replace(":", "_")

    private fun currentProfile(ctx: Context): String =
        sanitizeProfile(ProfileStore.getCurrent(ctx) ?: "default")

    private fun sessionKey(profile: String, domain: String): String =
        PREFIX_SESSION_MIN + PROFILE_SEGMENT + profile + "__" + domain

    private fun visitKey(profile: String, domain: String): String =
        PREFIX_VISIT_COUNT + PROFILE_SEGMENT + profile + "__" + domain

    private fun readInt(p: SharedPreferences, key: String): Int {
        if (!p.contains(key)) return 0
        return try {
            p.getInt(key, 0)
        } catch (_: ClassCastException) {
            val v = runCatching { p.getLong(key, 0L).toInt() }.getOrDefault(0)
            p.edit { putInt(key, v.coerceAtLeast(0)) }
            v
        }.coerceAtLeast(0)
    }

    fun getSessionLimitMinutes(ctx: Context, domain: String): Int =
        getSessionLimitMinutesForProfile(ctx, currentProfile(ctx), domain)

    fun getSessionLimitMinutesForProfile(ctx: Context, profile: String, domain: String): Int {
        val d = DomainBlockStore.normalize(domain) ?: return 0
        return readInt(prefs(ctx), sessionKey(sanitizeProfile(profile), d))
    }

    fun setSessionLimitMinutes(ctx: Context, domain: String, minutes: Int) =
        setSessionLimitMinutesForProfile(ctx, currentProfile(ctx), domain, minutes)

    fun setSessionLimitMinutesForProfile(ctx: Context, profile: String, domain: String, minutes: Int) {
        val d = DomainBlockStore.normalize(domain) ?: return
        val m = minutes.coerceAtLeast(0)
        prefs(ctx).edit {
            if (m <= 0) remove(sessionKey(sanitizeProfile(profile), d))
            else putInt(sessionKey(sanitizeProfile(profile), d), m)
        }
    }

    fun getVisitLimitCount(ctx: Context, domain: String): Int =
        getVisitLimitCountForProfile(ctx, currentProfile(ctx), domain)

    fun getVisitLimitCountForProfile(ctx: Context, profile: String, domain: String): Int {
        val d = DomainBlockStore.normalize(domain) ?: return 0
        return readInt(prefs(ctx), visitKey(sanitizeProfile(profile), d))
    }

    fun setVisitLimitCount(ctx: Context, domain: String, count: Int) =
        setVisitLimitCountForProfile(ctx, currentProfile(ctx), domain, count)

    fun setVisitLimitCountForProfile(ctx: Context, profile: String, domain: String, count: Int) {
        val d = DomainBlockStore.normalize(domain) ?: return
        val c = count.coerceAtLeast(0)
        prefs(ctx).edit {
            if (c <= 0) remove(visitKey(sanitizeProfile(profile), d))
            else putInt(visitKey(sanitizeProfile(profile), d), c)
        }
    }

    fun clear(ctx: Context, domain: String) =
        clearForProfile(ctx, currentProfile(ctx), domain)

    fun clearForProfile(ctx: Context, profile: String, domain: String) {
        val d = DomainBlockStore.normalize(domain) ?: return
        prefs(ctx).edit {
            remove(sessionKey(sanitizeProfile(profile), d))
            remove(visitKey(sanitizeProfile(profile), d))
        }
    }

    fun hasAnyLimitsForProfile(ctx: Context, profile: String): Boolean =
        getDomainsWithLimitsForProfile(ctx, profile).isNotEmpty()

    fun getDomainsWithLimitsForProfile(ctx: Context, profile: String): Set<String> {
        val p = prefs(ctx)
        val safe = sanitizeProfile(profile)
        val sessionPrefix = PREFIX_SESSION_MIN + PROFILE_SEGMENT + safe + "__"
        val visitPrefix = PREFIX_VISIT_COUNT + PROFILE_SEGMENT + safe + "__"
        val fromSession = p.all.keys.asSequence()
            .filter { it.startsWith(sessionPrefix) }
            .map { it.removePrefix(sessionPrefix) }
        val fromVisits = p.all.keys.asSequence()
            .filter { it.startsWith(visitPrefix) }
            .map { it.removePrefix(visitPrefix) }
        return (fromSession + fromVisits)
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun onProfileRenamed(ctx: Context, oldProfile: String, newProfile: String) {
        val p = prefs(ctx)
        val oldSafe = sanitizeProfile(oldProfile)
        val newSafe = sanitizeProfile(newProfile)
        for (prefix in listOf(PREFIX_SESSION_MIN, PREFIX_VISIT_COUNT)) {
            val oldPrefix = prefix + PROFILE_SEGMENT + oldSafe + "__"
            val newPrefix = prefix + PROFILE_SEGMENT + newSafe + "__"
            val entries = p.all.filterKeys { it.startsWith(oldPrefix) }
            if (entries.isEmpty()) continue
            p.edit {
                for ((key, value) in entries) {
                    val domain = key.removePrefix(oldPrefix)
                    if (domain.isBlank()) continue
                    val v = when (value) {
                        is Int -> value
                        is Long -> value.toInt()
                        else -> 0
                    }
                    if (v > 0) putInt(newPrefix + domain, v)
                    remove(key)
                }
            }
        }
    }

    fun onProfileRemoved(ctx: Context, profile: String) {
        val p = prefs(ctx)
        val safe = sanitizeProfile(profile)
        val keys = p.all.keys.filter {
            it.startsWith(PREFIX_SESSION_MIN + PROFILE_SEGMENT + safe + "__") ||
                it.startsWith(PREFIX_VISIT_COUNT + PROFILE_SEGMENT + safe + "__")
        }
        if (keys.isEmpty()) return
        p.edit { keys.forEach { remove(it) } }
    }
}
