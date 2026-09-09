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

package com.oliver.loqin.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.oliver.loqin.BuildConfig
import com.oliver.loqin.data.prefs.ActivityHistoryLogStore
import com.oliver.loqin.data.prefs.ActiveDurationStore
import com.oliver.loqin.data.prefs.ProfileUsageStore
import com.oliver.loqin.data.prefs.SurfaceUsageStore
import com.oliver.loqin.data.prefs.LoqInRuntimeStore
import com.oliver.loqin.data.prefs.UsageStore
import com.oliver.loqin.data.prefs.WebUsageStore
import com.oliver.loqin.data.prefs.AppLogStore
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.data.statistics.StatsBackupCodec
import com.oliver.loqin.data.statistics.StatsPersistence
import com.oliver.loqin.feature.usage.StatsArchiveSync

/**
 * Builds and applies the versioned local backup payload shared by file backup/restore.
 * (Extracted from the former CloudSyncRuntime; the payload format is unchanged.)
 */
object LocalBackupPayload {

    private const val TAG = "LocalBackupPayload"

    private const val FIELD_PREFS = "prefs"
    private const val FIELD_LOQIN_PREFS = "loqin_prefs"
    private const val FIELD_SCHEDULES_PREFS = "schedules_prefs"
    private const val FIELD_UI_HINTS_PREFS = "ui_hints_prefs"
    private const val FIELD_CREATED_AT = "created_at"
    private const val FIELD_STATS = "stats"

    private const val SCHEDULES_PREFS_NAME = "loqin_prefs_schedules"
    private const val SCHEDULES_KEY_ITEMS = "items" // JSON list stored by ScheduleStore
    private const val UI_HINTS_PREFS_NAME = "loqin_ui_hints"
    private const val TEMP_PAUSE_PREFS_NAME = "loqin_temp_pause"

    private val backupExcludedExactKeys = setOf(
        "switch_mode_active_since_ms",
        "loqin_runtime_running_since",
        "stats_archive_last_sync_ms",
    )

    private val backupExcludedKeyMarkers = listOf(
        "access_token",
        "app_lock",
        "auth_token",
        "billing",
        "emergency_pin",
        "entitlement",
        "firebase",
        "id_token",
        "password",
        "pin_hash",
        "pin_salt",
        "premium",
        "purchase",
        "refresh_token",
        "subscription",
        "unlock_pin"
    )

    private fun isBackupExcludedKey(key: String): Boolean {
        val normalized = key.trim().lowercase()
        if (normalized.isBlank()) {
            return true
        }
        if (normalized in backupExcludedExactKeys) {
            return true
        }
        return backupExcludedKeyMarkers.any { marker -> normalized.contains(marker) }
    }
    private fun normalizePrefsMap(src: Map<String, *>): Map<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        for ((rawKey, value) in src) {
            if (isBackupExcludedKey(rawKey)) continue

            val v: Any? = when (value) {
                is Set<*> -> value.filterNotNull().toList()
                is Collection<*> -> value.filterNotNull().toList()
                else -> value
            }
            out[rawKey] = v
        }
        return out
    }

    /**
     * Stats keys (usage_day_*, blocked_*, runtime_*, etc.) are stored as many single entries in "loqin_prefs".
     * For cloud backup we compress them into structured lists to keep the remote document smaller and cleaner.
     * Restore expands them back into the original SharedPreferences keys.
     */
    private fun extractStatsFromInternalPrefs(
        src: Map<String, Any?>
    ): Pair<Map<String, Any?>, Map<String, Any?>> {
        val prefsOut = src.toMutableMap()
        val statsOut = mutableMapOf<String, Any?>()

        fun takeRegex(
            listKey: String,
            regex: Regex,
            buildItem: (MatchResult) -> Map<String, Any?>
        ) {
            val items = mutableListOf<Map<String, Any?>>()
            val toRemove = mutableListOf<String>()

            for ((k, v) in prefsOut) {
                val m = regex.matchEntire(k) ?: continue
                val numericValue = when (v) {
                    is Number -> v.toLong()
                    is String -> v.toLongOrNull()
                    else -> null
                } ?: continue
                items += buildItem(m) + mapOf("v" to numericValue)
                toRemove += k
            }

            if (items.isNotEmpty()) {
                statsOut[listKey] = items
                toRemove.forEach { prefsOut.remove(it) }
            }
        }

        // Per-app per-day
        takeRegex(
            listKey = "usage_day",
            regex = Regex("usage_day_(\\d{8})_(.+)")
        ) { m -> mapOf("d" to m.groupValues[1], "p" to m.groupValues[2]) }

        takeRegex(
            listKey = "blocked_ms",
            regex = Regex("blocked_ms_(\\d{8})_(.+)")
        ) { m -> mapOf("d" to m.groupValues[1], "p" to m.groupValues[2]) }

        takeRegex(
            listKey = "blocked_count",
            regex = Regex("blocked_count_(\\d{8})_(.+)")
        ) { m -> mapOf("d" to m.groupValues[1], "p" to m.groupValues[2]) }

        takeRegex(
            listKey = "blocked_attempt",
            regex = Regex("blocked_attempt_(\\d{8})_(.+)")
        ) { m -> mapOf("d" to m.groupValues[1], "p" to m.groupValues[2]) }

        // Per-day (no pkg)
        takeRegex(
            listKey = "runtime_ms",
            regex = Regex("loqin_runtime_ms_(\\d{8})")
        ) { m -> mapOf("d" to m.groupValues[1]) }

        takeRegex(
            listKey = "emergency_unlock_count",
            regex = Regex("emergency_unlock_count_(\\d{8})")
        ) { m -> mapOf("d" to m.groupValues[1]) }

        takeRegex(
            listKey = "nfc_scan_count",
            regex = Regex("nfc_scan_count_(\\d{8})")
        ) { m -> mapOf("d" to m.groupValues[1]) }

        takeRegex(
            listKey = "schedule_exec_count",
            regex = Regex("schedule_exec_count_(\\d{8})")
        ) { m -> mapOf("d" to m.groupValues[1]) }

        return prefsOut to statsOut
    }

    private fun applyStatsToInternalPrefs(ctx: Context, stats: Any?): Int {
        val map = stats as? Map<*, *> ?: return 0
        var restoredValues = 0

        val restoredActivityDays = (map["activity_history_days"] as? Map<*, *>)
            ?.mapNotNull { (day, encoded) ->
                val dayKey = day as? String ?: return@mapNotNull null
                val value = encoded as? String ?: return@mapNotNull null
                dayKey to value
            }
            ?.toMap()
        when {
            restoredActivityDays != null -> ActivityHistoryLogStore.replaceDays(ctx, restoredActivityDays)
            map.containsKey("activity_history_logs") -> {
                val restoredActivityLogs = (map["activity_history_logs"] as? List<*>)
                    ?.filterIsInstance<String>()
                    .orEmpty()
                ActivityHistoryLogStore.replaceLines(ctx, restoredActivityLogs)
            }
        }

        fun stringValue(value: Any?): String? = when (value) {
            is String -> value.trim().takeIf(String::isNotEmpty)
            is Number -> value.toLong().toString()
            else -> null
        }

        fun dayValue(value: Any?): String? {
            val day = stringValue(value) ?: return null
            return day.takeIf { candidate -> candidate.length == 8 && candidate.all(Char::isDigit) }
        }

        fun longValue(value: Any?): Long? = when (value) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        }

        fun booleanValue(value: Any?): Boolean? = when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> when (value.trim().lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> null
            }
            else -> null
        }

        fun buildKeyWithPkg(prefix: String, item: Map<*, *>): String? {
            val day = dayValue(item["d"]) ?: return null
            val packageName = stringValue(item["p"]) ?: return null
            return "$prefix${day}_$packageName"
        }

        fun buildKeyNoPkg(prefix: String, item: Map<*, *>): String? {
            val day = dayValue(item["d"]) ?: return null
            return prefix + day
        }

        val internalPrefs = ctx.getSharedPreferences("loqin_prefs", Context.MODE_PRIVATE)
        internalPrefs.edit(commit = true) {
            fun applyListLong(listKey: String, keyBuilder: (Map<*, *>) -> String?) {
                val items = map[listKey] as? Collection<*> ?: return
                for (rawItem in items) {
                    val item = rawItem as? Map<*, *> ?: continue
                    val value = longValue(item["v"]) ?: continue
                    val key = keyBuilder(item) ?: continue
                    putLong(key, value)
                    restoredValues++
                }
            }

            fun applyListInt(listKey: String, keyBuilder: (Map<*, *>) -> String?) {
                val items = map[listKey] as? Collection<*> ?: return
                for (rawItem in items) {
                    val item = rawItem as? Map<*, *> ?: continue
                    val value = longValue(item["v"])
                        ?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                        ?.toInt()
                        ?: continue
                    val key = keyBuilder(item) ?: continue
                    putInt(key, value)
                    restoredValues++
                }
            }

            fun applyListBoolean(listKey: String, keyBuilder: (Map<*, *>) -> String?) {
                val items = map[listKey] as? Collection<*> ?: return
                for (rawItem in items) {
                    val item = rawItem as? Map<*, *> ?: continue
                    val value = booleanValue(item["v"]) ?: continue
                    val key = keyBuilder(item) ?: continue
                    putBoolean(key, value)
                    restoredValues++
                }
            }

            applyListLong("usage_day") { item -> buildKeyWithPkg("usage_day_", item) }
            applyListLong("blocked_ms") { item -> buildKeyWithPkg("blocked_ms_", item) }
            applyListLong("blocked_count") { item -> buildKeyWithPkg("blocked_count_", item) }
            applyListLong("blocked_attempt") { item -> buildKeyWithPkg("blocked_attempt_", item) }
            applyListLong("app_launch_count") { item -> buildKeyWithPkg("app_launch_count_", item) }

            applyListInt("open_count") { item ->
                val day = dayValue(item["d"]) ?: return@applyListInt null
                val packageName = stringValue(item["p"]) ?: return@applyListInt null
                val profile = stringValue(item["pr"])
                if (profile == null) {
                    "open_count_${day}_$packageName"
                } else {
                    "open_count_${day}__${profile}__$packageName"
                }
            }

            applyListLong("runtime_ms") { item -> buildKeyNoPkg("loqin_runtime_ms_", item) }
            applyListLong("emergency_unlock_count") { item ->
                buildKeyNoPkg("emergency_unlock_count_", item)
            }
            applyListLong("nfc_scan_count") { item -> buildKeyNoPkg("nfc_scan_count_", item) }
            applyListLong("qr_scan_count") { item -> buildKeyNoPkg("qr_scan_count_", item) }
            applyListLong("barcode_scan_count") { item ->
                buildKeyNoPkg("barcode_scan_count_", item)
            }
            applyListLong("temp_enable_count") { item -> buildKeyNoPkg("temp_enable_count_", item) }
            applyListLong("schedule_exec_count") { item ->
                buildKeyNoPkg("schedule_exec_count_", item)
            }

            applyListLong("switch_action_count") { item ->
                val action = stringValue(item["a"] ?: item["action"]) ?: return@applyListLong null
                val day = dayValue(item["d"]) ?: return@applyListLong null
                "switch_action_count_${action}_$day"
            }

            applyListBoolean("usage_limit_ever") { item ->
                val packageName = stringValue(item["p"]) ?: return@applyListBoolean null
                "usage_limit_ever__$packageName"
            }
            applyListInt("usage_limit_min") { item ->
                val profile = stringValue(item["pr"]) ?: return@applyListInt null
                val packageName = stringValue(item["p"]) ?: return@applyListInt null
                "usage_limit_min__${profile}__$packageName"
            }
        }

        fun restoreRawStatistics(raw: Map<*, *>) {
            val internalWrites = linkedMapOf<String, Any?>()
            val defaultWrites = linkedMapOf<String, Any?>()
            val uiHintsWrites = linkedMapOf<String, Any?>()

            raw.forEach { (rawKey, value) ->
                val key = rawKey as? String ?: return@forEach
                when {
                    StatsPersistence.isArchivedInternalKey(key) -> internalWrites[key] = value
                    StatsPersistence.isArchivedDefaultKey(key) -> defaultWrites[key] = value
                    StatsPersistence.isArchivedUiHintsKey(key) -> uiHintsWrites[key] = value
                }
            }

            fun writeValues(preferences: SharedPreferences, values: Map<String, Any?>) {
                if (values.isEmpty()) {
                    return
                }
                preferences.edit(commit = true) {
                    values.forEach { (key, value) ->
                        putSupportedPreferenceValue(this, key, value)
                        restoredValues++
                    }
                }
            }

            writeValues(internalPrefs, internalWrites)
            writeValues(PreferenceManager.getDefaultSharedPreferences(ctx), defaultWrites)
            writeValues(ctx.getSharedPreferences(UI_HINTS_PREFS_NAME, Context.MODE_PRIVATE), uiHintsWrites)
        }

        // Some pre-database backups kept raw statistic keys instead of compact lists.
        restoreRawStatistics(map)
        (map["values"] as? Map<*, *>)?.let(::restoreRawStatistics)

        return restoredValues
    }

    fun createLocalBackupPayload(ctx: Context): Map<String, Any?> =
        createLocalBackupPayload(ctx, BackupSelection.full())

    fun createLocalBackupPayload(ctx: Context, selection: BackupSelection): Map<String, Any?> {
        val now = System.currentTimeMillis()

        if (selection.includes(BackupCategory.STATISTICS)) {
            runCatching { StatsArchiveSync.sync(ctx, force = true) }
        }

        // Persist all buffered/live statistic deltas before reading SharedPreferences.
        UsageStore.flush(ctx)
        ProfileUsageStore.flush(ctx)
        SurfaceUsageStore.flush(ctx)
        WebUsageStore.flush(ctx)
        ActiveDurationStore.checkpointForBackup(ctx)
        LoqInRuntimeStore.checkpointForBackup(ctx)
        val statsDatabase = if (selection.includes(BackupCategory.STATISTICS)) {
            StatsBackupCodec.encode(StatsPersistence.snapshotForBackup(ctx))
        } else {
            null
        }

        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(ctx).all
        val internalPrefs = ctx.getSharedPreferences("loqin_prefs", Context.MODE_PRIVATE).all
        val schedulesPrefs = ctx.getSharedPreferences(SCHEDULES_PREFS_NAME, Context.MODE_PRIVATE).all
        val uiHintsPrefs = ctx.getSharedPreferences(UI_HINTS_PREFS_NAME, Context.MODE_PRIVATE).all
        val tempPausePrefs = ctx.getSharedPreferences(TEMP_PAUSE_PREFS_NAME, Context.MODE_PRIVATE).all

        val all = BackupCategoryFilter.filterDefaultPrefs(normalizePrefsMap(defaultPrefs), selection)
        val internalAllRaw = BackupCategoryFilter.filterInternalPrefs(normalizePrefsMap(internalPrefs), selection)
        val schedulesAll = BackupCategoryFilter.filterSchedulesPrefs(normalizePrefsMap(schedulesPrefs), selection)
        val uiHintsAll = BackupCategoryFilter.filterUiHintsPrefs(normalizePrefsMap(uiHintsPrefs), selection)
        val tempPauseAll = BackupCategoryFilter.filterTempPausePrefs(normalizePrefsMap(tempPausePrefs), selection)

        val (internalAll, statsMapRaw) = extractStatsFromInternalPrefs(internalAllRaw)
        val statsMapWithLogs = statsMapRaw.toMutableMap()
        ActivityHistoryLogStore.ensureMigrated(ctx, AppLogStore.latestLines(ctx, 1000))
        statsMapWithLogs["activity_history_days"] = ActivityHistoryLogStore.exportDays(ctx)
        val statsMap = BackupCategoryFilter.filterStats(statsMapWithLogs, selection)

        return mapOf(
            BackupCategoryFilter.FIELD_BACKUP_SCHEMA_VERSION to BACKUP_SCHEMA_VERSION,
            BackupCategoryFilter.FIELD_CREATED_WITH_VERSION to BuildConfig.VERSION_NAME,
            BackupCategoryFilter.FIELD_CREATED_WITH_VERSION_CODE to BuildConfig.VERSION_CODE,
            FIELD_PREFS to all,
            FIELD_LOQIN_PREFS to internalAll,
            FIELD_STATS to statsMap,
            BackupCategoryFilter.FIELD_STATS_DATABASE to statsDatabase,
            FIELD_SCHEDULES_PREFS to schedulesAll,
            FIELD_UI_HINTS_PREFS to uiHintsAll,
            BackupCategoryFilter.FIELD_TEMP_PAUSE_PREFS to tempPauseAll,
            BackupCategoryFilter.FIELD_INCLUDED_CATEGORIES to selection.categoryIds.toList().sorted(),
            BackupCategoryFilter.FIELD_IS_PARTIAL_BACKUP to !selection.isFull,
            FIELD_CREATED_AT to now
        )
    }

    @JvmStatic
    fun applyBackupPayload(ctx: Context, payload: Map<*, *>) {
        if (!hasBackupPayload(payload)) {
            throw IllegalArgumentException("No backup data found in payload")
        }

        val prefsMap = payload[FIELD_PREFS] as? Map<*, *> ?: emptyMap<Any, Any>()
        val internalMap = payload[FIELD_LOQIN_PREFS] as? Map<*, *> ?: emptyMap<Any, Any>()
        val schedulesMap = payload[FIELD_SCHEDULES_PREFS] as? Map<*, *> ?: emptyMap<Any, Any>()
        val uiHintsMap = payload[FIELD_UI_HINTS_PREFS] as? Map<*, *> ?: emptyMap<Any, Any>()
        val tempPauseMap = payload[BackupCategoryFilter.FIELD_TEMP_PAUSE_PREFS] as? Map<*, *> ?: emptyMap<Any, Any>()
        val stats = payload[FIELD_STATS]
        val statsDatabase = payload[BackupCategoryFilter.FIELD_STATS_DATABASE] as? Map<*, *>
        val partialBackup = BackupCategoryFilter.isPartialBackup(payload)

        val legacyStatisticsBackup = statsDatabase == null && stats is Map<*, *>
        var restoredCompactValues = 0

        StatsPersistence.beginRestore(ctx)
        try {
            applyPrefsMapToLocal(ctx, prefsMap, isInternal = false, isSchedules = false, clearBeforeApply = !partialBackup)
            applyPrefsMapToLocal(ctx, internalMap, isInternal = true, isSchedules = false, clearBeforeApply = !partialBackup)
            applyPrefsMapToLocal(ctx, schedulesMap, isInternal = false, isSchedules = true, clearBeforeApply = !partialBackup)
            applyPrefsMapToLocal(ctx, uiHintsMap, prefsName = UI_HINTS_PREFS_NAME, clearBeforeApply = !partialBackup)
            applyPrefsMapToLocal(ctx, tempPauseMap, prefsName = TEMP_PAUSE_PREFS_NAME, clearBeforeApply = !partialBackup)

            // Expand compact statistics from 2.1.x/2.2.x backups before Room is restored.
            restoredCompactValues = applyStatsToInternalPrefs(ctx, stats)
            normalizeLegacyStatisticsPreferences(ctx)

            // Safety: restored schedules should not immediately fire
            if (schedulesMap.isNotEmpty()) {
                forceDisableAllSchedules(ctx)
            }

            // Safety: after restore, keep LoqIn base state OFF so users don't get locked out
            forceDisableLoqInAfterRestore(ctx)
        } finally {
            StatsPersistence.finishRestore(
                context = ctx,
                databasePayload = statsDatabase,
                replaceDatabase = !partialBackup,
            )

            // Re-apply compact values after the database phase so an older or incomplete database archive cannot overwrite counters restored from legacy backups.
            if (restoredCompactValues > 0) {
                restoredCompactValues = applyStatsToInternalPrefs(ctx, stats)
            }
            normalizeLegacyStatisticsPreferences(ctx)
            StatsPersistence.flushBlocking(ctx)

            if (legacyStatisticsBackup && restoredCompactValues > 0) {
                AppLogStore.append(
                    ctx,
                    TAG,
                    "Legacy statistics migration restored $restoredCompactValues preference values",
                )
            }
        }
    }

    private fun hasBackupPayload(payload: Map<*, *>): Boolean {
        return payload.containsKey(FIELD_PREFS) ||
            payload.containsKey(FIELD_LOQIN_PREFS) ||
            payload.containsKey(FIELD_SCHEDULES_PREFS) ||
            payload.containsKey(FIELD_UI_HINTS_PREFS) ||
            payload.containsKey(BackupCategoryFilter.FIELD_TEMP_PAUSE_PREFS) ||
            payload.containsKey(FIELD_STATS) ||
            payload.containsKey(BackupCategoryFilter.FIELD_STATS_DATABASE)
    }

    private fun shouldStoreAsInt(key: String): Boolean {
        return key == "onboarding_version" ||
            key == "primary_toggle_tap_count" ||
            key.startsWith("usage_limit_min__") ||
            key.startsWith("session_limit_min__") ||
            key.startsWith("attempt_limit__") ||
            key.startsWith("inapp_limit_min__") ||
            key.startsWith("surf_rule__") ||
            key.startsWith("domain_limit_min_") ||
            key.startsWith("scan_code_daily_limit_") ||
            key.startsWith("scan_code_cooldown_") ||
            key.startsWith("scan_code_count_") ||
            key.startsWith("qr_temp_count_") ||
            key.startsWith("nfc_td_count_") ||
            key.startsWith("nfc_td_cfg_daily_") ||
            key.startsWith("nfc_td_cfg_cooldown_") ||
            key.startsWith("nfc_tag_read_only_duration_") ||
            key.startsWith("temp_pause_") ||
            key.startsWith("open_count_")
    }

    private fun isArchivedStatisticsKey(key: String): Boolean {
        return StatsPersistence.isArchivedInternalKey(key) ||
            StatsPersistence.isArchivedDefaultKey(key) ||
            StatsPersistence.isArchivedUiHintsKey(key)
    }

    private fun putSupportedPreferenceValue(
        editor: SharedPreferences.Editor,
        key: String,
        value: Any?,
    ) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Number -> {
                val numericValue = value.toLong()
                when {
                    shouldStoreAsInt(key) && numericValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() -> {
                        editor.putInt(key, numericValue.toInt())
                    }
                    isArchivedStatisticsKey(key) -> editor.putLong(key, numericValue)
                    value is Float -> editor.putFloat(key, value)
                    value is Double && value % 1.0 != 0.0 -> editor.putFloat(key, value.toFloat())
                    value is Int -> editor.putInt(key, value)
                    else -> editor.putLong(key, numericValue)
                }
            }
            is String -> {
                val numericValue = value.toLongOrNull()
                when {
                    numericValue != null && shouldStoreAsInt(key) &&
                        numericValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() -> {
                        editor.putInt(key, numericValue.toInt())
                    }
                    numericValue != null && isArchivedStatisticsKey(key) -> {
                        editor.putLong(key, numericValue)
                    }
                    else -> editor.putString(key, value)
                }
            }
            is Collection<*> -> {
                if (value.all { item -> item is String }) {
                    editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }
    }

    // Older backups could store statistic keys in the wrong SharedPreferences file or with JSON Int values.
    // Put every archived key back into its canonical file and normalize numeric types before Room mirrors it.
    private fun normalizeLegacyStatisticsPreferences(ctx: Context) {
        val internalPrefs = ctx.getSharedPreferences("loqin_prefs", Context.MODE_PRIVATE)
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val uiHintsPrefs = ctx.getSharedPreferences(UI_HINTS_PREFS_NAME, Context.MODE_PRIVATE)
        val sources = listOf(internalPrefs, defaultPrefs, uiHintsPrefs)
        val snapshots = sources.associateWith { prefs -> prefs.all }
        val writes = sources.associateWith { linkedMapOf<String, Any?>() }.toMutableMap()
        val removals = sources.associateWith { linkedSetOf<String>() }.toMutableMap()

        val keys = snapshots.values.flatMap { values -> values.keys }.toSet()
        for (key in keys) {
            val target = when {
                StatsPersistence.isArchivedInternalKey(key) -> internalPrefs
                StatsPersistence.isArchivedDefaultKey(key) -> defaultPrefs
                StatsPersistence.isArchivedUiHintsKey(key) -> uiHintsPrefs
                else -> null
            } ?: continue

            val value = snapshots[target]?.get(key)
                ?: sources.firstNotNullOfOrNull { source -> snapshots[source]?.get(key) }
                ?: continue
            writes.getValue(target)[key] = value
            sources.filter { source -> source !== target }.forEach { source ->
                if (snapshots[source]?.containsKey(key) == true) {
                    removals.getValue(source).add(key)
                }
            }
        }

        sources.forEach { prefs ->
            val values = writes.getValue(prefs)
            val keysToRemove = removals.getValue(prefs)
            if (values.isEmpty() && keysToRemove.isEmpty()) {
                return@forEach
            }
            prefs.edit(commit = true) {
                keysToRemove.forEach(::remove)
                values.forEach { (key, value) ->
                    putSupportedPreferenceValue(this, key, value)
                }
            }
        }
    }

    // Applies a Firestore-loaded map to local SharedPreferences.
    private fun applyPrefsMapToLocal(
        ctx: Context,
        map: Map<*, *>,
        isInternal: Boolean = false,
        isSchedules: Boolean = false,
        prefsName: String? = null,
        clearBeforeApply: Boolean = true
    ) {
        val prefs = when {
            prefsName != null -> ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            isSchedules -> ctx.getSharedPreferences(SCHEDULES_PREFS_NAME, Context.MODE_PRIVATE)
            isInternal -> ctx.getSharedPreferences("loqin_prefs", Context.MODE_PRIVATE)
            else -> PreferenceManager.getDefaultSharedPreferences(ctx)
        }
        val preservedEntitlementPrefs = prefs.all.filterKeys { key -> isBackupExcludedKey(key) }

        prefs.edit(commit = true) {
            if (clearBeforeApply) clear()

            for ((rawKey, value) in map) {
                val key = rawKey as? String ?: continue
                if (isBackupExcludedKey(key)) continue
                putSupportedPreferenceValue(this, key, value)
            }

            for ((key, value) in preservedEntitlementPrefs) {
                putSupportedPreferenceValue(this, key, value)
            }
        }
    }

    // After restoring schedules, force-disable them for safety.
    private fun forceDisableAllSchedules(ctx: Context) {
        try {
            val sp = ctx.getSharedPreferences(SCHEDULES_PREFS_NAME, Context.MODE_PRIVATE)
            val raw = sp.getString(SCHEDULES_KEY_ITEMS, null) ?: return

            // Best-effort JSON patch: enabled:true -> enabled:false
            val patched = raw
                .replace("\"enabled\":true", "\"enabled\":false")
                .replace("\"enabled\" : true", "\"enabled\":false")
                .replace("\"enabled\"  :  true", "\"enabled\":false")
                .replace("\"enabled\": true", "\"enabled\":false")
                .replace("\"enabled\" :true", "\"enabled\":false")

            if (patched != raw) {
                sp.edit { putString(SCHEDULES_KEY_ITEMS, patched) }
                if (BuildConfig.DEBUG) Log.d(TAG, "forceDisableAllSchedules: patched schedules enabled->false")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "forceDisableAllSchedules failed: ${t.message}")
        }
    }

    private fun forceDisableLoqInAfterRestore(ctx: Context) {
        try {
            val sp = ctx.getSharedPreferences("loqin_prefs", Context.MODE_PRIVATE)
            sp.edit(commit = true) {
                putBoolean("switch_mode_enabled", false)
                putLong("switch_mode_temp_disable_until", 0L)
                putLong("switch_mode_temp_enable_until", 0L)
                remove("switch_mode_base_before_temp_enable")
            }

            // Keep runtime flow/state in sync with prefs and bypass NFC lock for this forced safety off.
            runCatching { SwitchModeStore.setEnabled(ctx, false, allowNfcBypass = true) }
                .onFailure { Log.w(TAG, "forceDisableLoqInAfterRestore runtime sync failed", it) }
        } catch (t: Throwable) {
            Log.w(TAG, "forceDisableLoqInAfterRestore failed: ${t.message}")
        }
    }

}
