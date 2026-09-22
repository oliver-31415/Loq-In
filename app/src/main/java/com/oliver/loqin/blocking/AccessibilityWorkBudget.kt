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
import androidx.core.content.edit

/**
 * Lightweight performance telemetry for synchronous Accessibility work.
 * It never stores node text. Counters are buffered in memory and persisted only periodically
 * (or immediately for a slow operation), so diagnostics do not become new main-thread work.
 */
object AccessibilityWorkBudget {
    private const val PREFS = "loqin_accessibility_budget"
    private const val KEY_SCANS = "scans"
    private const val KEY_SCAN_OVERRUNS = "scan_overruns"
    private const val KEY_NODE_LIMIT_HITS = "node_limit_hits"
    private const val KEY_ROOT_LOOKUPS = "root_lookups"
    private const val KEY_ROOT_SLOW = "root_slow"
    private const val KEY_LAST_SCAN = "last_scan"
    private const val KEY_LAST_SCAN_MS = "last_scan_ms"
    private const val KEY_LAST_SCAN_NODES = "last_scan_nodes"
    private const val KEY_LAST_ROOT_REASON = "last_root_reason"
    private const val KEY_LAST_ROOT_MS = "last_root_ms"
    private const val SCAN_WARN_MS = 20L
    private const val ROOT_WARN_MS = 12L
    private const val PERSIST_EVERY_OPERATIONS = 25

    data class Snapshot(
        val scans: Int,
        val scanOverruns: Int,
        val nodeLimitHits: Int,
        val rootLookups: Int,
        val slowRootLookups: Int,
        val lastScan: String,
        val lastScanMs: Long,
        val lastScanNodes: Int,
        val lastRootReason: String,
        val lastRootMs: Long,
    )

    private var initialized = false
    private var operationCountSincePersist = 0
    private var scans = 0
    private var scanOverruns = 0
    private var nodeLimitHits = 0
    private var rootLookups = 0
    private var slowRootLookups = 0
    private var lastScan = ""
    private var lastScanMs = 0L
    private var lastScanNodes = 0
    private var lastRootReason = ""
    private var lastRootMs = 0L

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (initialized) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        scans = p.getInt(KEY_SCANS, 0)
        scanOverruns = p.getInt(KEY_SCAN_OVERRUNS, 0)
        nodeLimitHits = p.getInt(KEY_NODE_LIMIT_HITS, 0)
        rootLookups = p.getInt(KEY_ROOT_LOOKUPS, 0)
        slowRootLookups = p.getInt(KEY_ROOT_SLOW, 0)
        lastScan = p.getString(KEY_LAST_SCAN, null).orEmpty()
        lastScanMs = p.getLong(KEY_LAST_SCAN_MS, 0L)
        lastScanNodes = p.getInt(KEY_LAST_SCAN_NODES, 0)
        lastRootReason = p.getString(KEY_LAST_ROOT_REASON, null).orEmpty()
        lastRootMs = p.getLong(KEY_LAST_ROOT_MS, 0L)
        initialized = true
    }

    @Synchronized
    fun recordScan(context: Context, name: String, visitedNodes: Int, nodeLimit: Int, durationMs: Long) {
        ensureLoaded(context)
        scans += 1
        if (durationMs >= SCAN_WARN_MS) scanOverruns += 1
        if (visitedNodes >= nodeLimit) nodeLimitHits += 1
        lastScan = name.take(80)
        lastScanMs = durationMs
        lastScanNodes = visitedNodes
        operationCountSincePersist += 1
        persistIfNeeded(context, urgent = durationMs >= SCAN_WARN_MS || visitedNodes >= nodeLimit)
    }

    @Synchronized
    fun recordRootLookup(context: Context, reason: String, durationMs: Long) {
        ensureLoaded(context)
        rootLookups += 1
        if (durationMs >= ROOT_WARN_MS) slowRootLookups += 1
        lastRootReason = reason.take(100)
        lastRootMs = durationMs
        operationCountSincePersist += 1
        persistIfNeeded(context, urgent = durationMs >= ROOT_WARN_MS)
    }

    @Synchronized
    fun snapshot(context: Context): Snapshot {
        ensureLoaded(context)
        return Snapshot(
            scans = scans,
            scanOverruns = scanOverruns,
            nodeLimitHits = nodeLimitHits,
            rootLookups = rootLookups,
            slowRootLookups = slowRootLookups,
            lastScan = lastScan,
            lastScanMs = lastScanMs,
            lastScanNodes = lastScanNodes,
            lastRootReason = lastRootReason,
            lastRootMs = lastRootMs,
        )
    }

    private fun persistIfNeeded(context: Context, urgent: Boolean) {
        if (!urgent && operationCountSincePersist < PERSIST_EVERY_OPERATIONS) return
        operationCountSincePersist = 0
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_SCANS, scans)
            putInt(KEY_SCAN_OVERRUNS, scanOverruns)
            putInt(KEY_NODE_LIMIT_HITS, nodeLimitHits)
            putInt(KEY_ROOT_LOOKUPS, rootLookups)
            putInt(KEY_ROOT_SLOW, slowRootLookups)
            putString(KEY_LAST_SCAN, lastScan)
            putLong(KEY_LAST_SCAN_MS, lastScanMs)
            putInt(KEY_LAST_SCAN_NODES, lastScanNodes)
            putString(KEY_LAST_ROOT_REASON, lastRootReason)
            putLong(KEY_LAST_ROOT_MS, lastRootMs)
        }
    }
}
