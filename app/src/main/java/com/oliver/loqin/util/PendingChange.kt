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
        PendingChangeType.APP_SELECTION ->
            "app:${change.data.optString("profile")}:${change.data.optBoolean("allowMode")}"

        PendingChangeType.IN_APP_SELECTION ->
            "inapp:${change.data.optString("profile")}:${change.data.optString("baseKey")}"

        PendingChangeType.WEBSITE_REMOVE ->
            "web-remove:${change.data.optString("profile")}:${change.data.optString("rule")}"

        PendingChangeType.WEBSITE_ENABLED ->
            "web-enabled:${change.data.optString("profile")}:${change.data.optString("rule")}"

        PendingChangeType.APP_LIMITS ->
            "app-limits:${change.data.optString("profile")}:${change.data.optString("packageName")}"

        PendingChangeType.AUTO_BLOCK_NEW_APPS ->
            "auto-block:${change.data.optString("profile")}"

        PendingChangeType.CLEAR_APP_DATA ->
            "clear-app-data:${change.data.optString("profile")}"

        else -> change.id
    }

    /** Queueing the same target again replaces the previous entry and restarts its timer. */
    fun upsert(existing: List<PendingChange>, change: PendingChange): List<PendingChange> {
        val key = dedupeKey(change)
        val remaining = existing.filterNot { dedupeKey(it) == key }
        return (remaining + change).sortedBy { it.executeAtMs }
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

    fun shouldApplyNumeric(currentValue: Int, fromValue: Int): Boolean = currentValue == fromValue

    fun shouldApplyResetMode(currentValue: String, fromValue: String): Boolean = currentValue == fromValue
}
