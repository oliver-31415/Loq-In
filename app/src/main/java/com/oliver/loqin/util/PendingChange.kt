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

import org.json.JSONArray
import org.json.JSONObject

/**
 * One protection-weakening edit waiting for its delay to expire.
 *
 * Pure data model: persistence, alarms and store writes live in the Android shell
 * ([ProtectionChangeGate]). The `data` payload is type-specific and always stores deltas
 * (never full snapshots), so stricter edits made while a change is pending are never lost.
 */
data class PendingChange(
    val id: String,
    val type: String,
    val executeAtMs: Long,
    val createdAtMs: Long,
    val data: JSONObject,
)

object PendingChangeType {
    const val APP_SELECTION = "app_selection"
    const val IN_APP_SELECTION = "in_app_selection"
    const val WEBSITE_REMOVE = "website_remove"
    const val WEBSITE_ENABLED = "website_enabled"
    const val APP_LIMITS = "app_limits"
    const val WEBSITE_LIMIT = "website_limit"
    const val AUTO_BLOCK_NEW_APPS = "auto_block_new_apps"
    const val CLEAR_APP_DATA = "clear_app_data"
}

object PendingChangeCodec {

    fun encode(changes: List<PendingChange>): String {
        val array = JSONArray()
        changes.sortedBy { it.executeAtMs }.forEach { change ->
            array.put(
                JSONObject()
                    .put("id", change.id)
                    .put("type", change.type)
                    .put("executeAtMs", change.executeAtMs)
                    .put("createdAtMs", change.createdAtMs)
                    .put("data", change.data),
            )
        }
        return array.toString()
    }

    /** Malformed entries are skipped; a corrupt payload degrades to an empty queue instead of crashing. */
    fun decode(raw: String?): List<PendingChange> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<PendingChange>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val type = item.optString("type").takeIf { it.isNotBlank() } ?: continue
            val executeAt = item.optLong("executeAtMs", 0L)
            if (executeAt <= 0L) continue
            out += PendingChange(
                id = id,
                type = type,
                executeAtMs = executeAt,
                createdAtMs = item.optLong("createdAtMs", 0L),
                data = item.optJSONObject("data") ?: JSONObject(),
            )
        }
        return out.sortedBy { it.executeAtMs }
    }
}

object PendingChangeQueue {

    fun newChange(
        id: String,
        type: String,
        nowMs: Long,
        delayMinutes: Int,
        data: JSONObject,
    ): PendingChange {
        val delayMs = delayMinutes.coerceAtLeast(1).toLong() * 60_000L
        return PendingChange(
            id = id,
            type = type,
            executeAtMs = nowMs + delayMs,
            createdAtMs = nowMs,
            data = data,
        )
    }

    fun dedupeKey(change: PendingChange): String = when (change.type) {
        PendingChangeType.APP_SELECTION -> {
            val profile = change.data.optString("profile")
            val allowMode = change.data.optBoolean("allowMode")
            val packages = packageList(change.data, "removePackages") +
                packageList(change.data, "addPackages")
            "app:$profile:$allowMode:${packages.sorted().joinToString(",")}"
        }

        PendingChangeType.IN_APP_SELECTION ->
            "inapp:${change.data.optString("profile")}:${change.data.optString("baseKey")}"

        PendingChangeType.WEBSITE_REMOVE ->
            "web-remove:${change.data.optString("profile")}:${change.data.optString("rule")}"

        PendingChangeType.WEBSITE_ENABLED ->
            "web-enabled:${change.data.optString("profile")}:${change.data.optString("rule")}"

        PendingChangeType.APP_LIMITS ->
            "app-limits:${change.data.optString("profile")}:${change.data.optString("packageName")}"

        PendingChangeType.WEBSITE_LIMIT ->
            "web-limit:${change.data.optString("profile")}:${change.data.optString("rule")}"

        PendingChangeType.AUTO_BLOCK_NEW_APPS ->
            "auto-block:${change.data.optString("profile")}"

        PendingChangeType.CLEAR_APP_DATA -> {
            val profile = change.data.optString("profile")
            val packages = packageList(change.data, "packages")
            "clear-app-data:$profile:${packages.sorted().joinToString(",")}"
        }

        else -> change.id
    }

    /**
     * Queueing the same target again replaces the payload but keeps the original timer, so an
     * accidental repeat cannot extend (or shorten) the delay the user already agreed to.
     */
    fun upsert(existing: List<PendingChange>, change: PendingChange): List<PendingChange> {
        val key = dedupeKey(change)
        val previous = existing.firstOrNull { dedupeKey(it) == key }
        val merged = if (previous == null) {
            change
        } else {
            change.copy(executeAtMs = previous.executeAtMs, createdAtMs = previous.createdAtMs)
        }
        val remaining = existing.filterNot { dedupeKey(it) == key }
        return (remaining + merged).sortedBy { it.executeAtMs }
    }

    fun removeById(existing: List<PendingChange>, id: String): List<PendingChange> =
        existing.filterNot { it.id == id }

    /** Returns (due, remaining). */
    fun partitionDue(existing: List<PendingChange>, nowMs: Long): Pair<List<PendingChange>, List<PendingChange>> {
        val due = existing.filter { it.executeAtMs <= nowMs }
        val remaining = existing.filter { it.executeAtMs > nowMs }
        return due to remaining
    }

    fun earliestDueAtMs(existing: List<PendingChange>): Long? =
        existing.minOfOrNull { it.executeAtMs }

    private fun packageList(data: JSONObject, key: String): List<String> {
        val array = data.optJSONArray(key) ?: return emptyList()
        val out = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotBlank() }?.let(out::add)
        }
        return out
    }

    private fun removePackagesFromEntry(change: PendingChange, key: String, packages: Set<String>): PendingChange? {
        val remaining = packageList(change.data, key).filterNot { it in packages }
        if (remaining.isEmpty()) return null
        val data = JSONObject(change.data.toString())
        data.put(key, JSONArray(remaining))
        return change.copy(data = data)
    }

    /**
     * A stricter app-selection edit (blocking an app again / removing an allowed exception) cancels
     * the matching pending weakening change, so the queued removal/addition cannot undo it later.
     */
    fun pruneAppSelection(
        existing: List<PendingChange>,
        profile: String,
        allowMode: Boolean,
        packages: Set<String>,
    ): List<PendingChange> {
        if (packages.isEmpty()) return existing
        val weakeningKey = if (allowMode) "addPackages" else "removePackages"
        return existing.mapNotNull { change ->
            if (change.type != PendingChangeType.APP_SELECTION) return@mapNotNull change
            if (change.data.optString("profile") != profile) return@mapNotNull change
            if (change.data.optBoolean("allowMode") != allowMode) return@mapNotNull change
            removePackagesFromEntry(change, weakeningKey, packages)
        }
    }

    /** A stricter in-app rule edit cancels the pending weakening change for the same rule. */
    fun pruneInAppSelections(
        existing: List<PendingChange>,
        profile: String,
        baseKeys: Set<String>,
    ): List<PendingChange> {
        if (baseKeys.isEmpty()) return existing
        return existing.filterNot { change ->
            change.type == PendingChangeType.IN_APP_SELECTION &&
                change.data.optString("profile") == profile &&
                change.data.optString("baseKey") in baseKeys
        }
    }

    /** Re-enabling/removing a website rule cancels the pending weakening change for that rule. */
    fun pruneWebsiteEnabled(
        existing: List<PendingChange>,
        profile: String,
        rules: Set<String>,
    ): List<PendingChange> {
        if (rules.isEmpty()) return existing
        return existing.filterNot { change ->
            change.type == PendingChangeType.WEBSITE_ENABLED &&
                change.data.optString("profile") == profile &&
                change.data.optString("rule") in rules
        }
    }

    /** Cancels pending allow-additions for packages the user no longer wants allowed. */
    fun pruneAllowAdditionsNotIn(
        existing: List<PendingChange>,
        profile: String,
        allowedPackages: Set<String>,
    ): List<PendingChange> = existing.mapNotNull { change ->
        if (change.type != PendingChangeType.APP_SELECTION) return@mapNotNull change
        if (change.data.optString("profile") != profile) return@mapNotNull change
        if (!change.data.optBoolean("allowMode")) return@mapNotNull change
        val requested = packageList(change.data, "addPackages")
        val keep = requested.filter { it in allowedPackages }
        if (keep.isEmpty()) return@mapNotNull null
        if (keep.size == requested.size) return@mapNotNull change
        val data = JSONObject(change.data.toString())
        data.put("addPackages", JSONArray(keep))
        change.copy(data = data)
    }

    /** Re-enabling auto-block cancels the pending disable. */
    fun pruneAutoBlock(existing: List<PendingChange>, profile: String): List<PendingChange> =
        existing.filterNot { change ->
            change.type == PendingChangeType.AUTO_BLOCK_NEW_APPS &&
                change.data.optString("profile") == profile
        }

    fun shouldApplyNumeric(currentValue: Int, fromValue: Int): Boolean = currentValue == fromValue

    fun shouldApplyResetMode(currentValue: String, fromValue: String): Boolean = currentValue == fromValue
}
