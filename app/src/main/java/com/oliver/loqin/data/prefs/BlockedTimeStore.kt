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
import java.util.Calendar

// Persists and retrieves blocked time state.
object BlockedTimeStore {
    private const val PREFS = "loqin_prefs"
    // blocked_ms_yyyymmdd_pkg  (ymd = Int like 20251223)
    private const val PREFIX_DAY = "blocked_ms_" // + yyyymmdd + "_" + pkg
    // protection_ms_yyyymmdd — total ms per day while LoqIn was enabled & enforcing
    // (the Home heatmap's "actual blocking time", independent of which app was foreground)
    private const val PREFIX_PROT = "protection_ms_" // + yyyymmdd

    // Buffer frequent increments to avoid high-frequency SharedPreferences writes.
    private const val FLUSH_INTERVAL_MS = 10_000L
    private const val MAX_PENDING_KEYS = 32

    private val lock = Any()
    private val pending = HashMap<String, Long>()
    @Volatile private var lastFlushAtMs: Long = 0L

    private fun dayKey(ymd: Int, pkg: String): String = PREFIX_DAY + ymd.toString() + "_" + pkg

    fun addBlockedMsToday(ctx: Context, pkg: String, deltaMs: Long) {
        if (deltaMs <= 0L || pkg.isBlank()) {
            return
        }
        addToPending(dayKey(todayYmdInt(), pkg), deltaMs)
        maybeFlush(ctx)
    }

    /** Adds [deltaMs] of enabled-and-enforcing time to today's protection total. */
    fun addProtectionMsToday(ctx: Context, deltaMs: Long) {
        if (deltaMs <= 0L) {
            return
        }
        addToPending(PREFIX_PROT + todayYmdInt(), deltaMs)
        maybeFlush(ctx)
    }

    /** Today's persisted + buffered protection total. */
    fun getProtectionTodayMs(ctx: Context): Long {
        val key = PREFIX_PROT + todayYmdInt()
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val persisted = sp.getLong(key, 0L)
        val buffered = synchronized(lock) { pending[key] ?: 0L }
        return (persisted + buffered).coerceAtMost(86_400_000L)
    }

    /**
     * Lifts today's protection total to at least [minMs] without ever reducing it —
     * reconciles time the tick accrual missed (app reinstalls, paused accrual) so the
     * running session is always reflected and later sessions accumulate on top.
     */
    fun ensureProtectionTodayAtLeast(ctx: Context, minMs: Long) {
        if (minMs <= 0L) {
            return
        }
        val key = PREFIX_PROT + todayYmdInt()
        val delta = minMs - getProtectionTodayMs(ctx)
        if (delta > 0L) {
            addToPending(key, delta)
        }
    }

    private fun addToPending(key: String, deltaMs: Long) {
        synchronized(lock) {
            pending[key] = (pending[key] ?: 0L) + deltaMs
        }
    }

    private fun maybeFlush(ctx: Context) {
        val now = System.currentTimeMillis()
        var shouldFlush = false
        synchronized(lock) {
            shouldFlush = (now - lastFlushAtMs) >= FLUSH_INTERVAL_MS || pending.size >= MAX_PENDING_KEYS
        }
        if (shouldFlush) flush(ctx)
    }

    fun getBlockedMsToday(ctx: Context, pkg: String): Long {
        if (pkg.isBlank()) {
            return 0L
        }
        val ymd = todayYmdInt()
        val key = dayKey(ymd, pkg)
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val persisted = sp.getLong(key, 0L)
        val buffered = synchronized(lock) { pending[key] ?: 0L }
        return persisted + buffered
    }

    fun getBlockedMsForLastNDays(ctx: Context, pkg: String, days: Int): Long {
        if (pkg.isBlank() || days <= 0) {
            return 0L
        }

        flush(ctx)

        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cal = Calendar.getInstance()

        var sum = 0L
        for (i in 0 until days) {
            val ymd = ymdInt(cal)
            sum += sp.getLong(dayKey(ymd, pkg), 0L)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return sum
    }

    fun getBlockedMsForMonth(ctx: Context, pkg: String, year: Int, month1Based: Int): Long {
        if (pkg.isBlank()) {
            return 0L
        }

        flush(ctx)

        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, (month1Based - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12) // avoid DST weirdness
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val targetMonth = cal.get(Calendar.MONTH)
        var sum = 0L
        while (cal.get(Calendar.MONTH) == targetMonth) {
            val ymd = ymdInt(cal)
            sum += sp.getLong(dayKey(ymd, pkg), 0L)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return sum
    }

    fun getBlockedMsForYear(ctx: Context, pkg: String, year: Int): Long {
        if (pkg.isBlank()) {
            return 0L
        }

        flush(ctx)

        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var sum = 0L
        while (cal.get(Calendar.YEAR) == year) {
            val ymd = ymdInt(cal)
            sum += sp.getLong(dayKey(ymd, pkg), 0L)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return sum
    }

    /**
     * Per-day blocked-time totals for the last N days (inclusive of today),
     * ordered oldest -> today. Entry index [days - 1] is always today.
     * Used by the Foqos-style activity heatmap on Home.
     */
    fun getDayTotalsMs(ctx: Context, days: Int): LongArray {
        val result = LongArray(days.coerceAtLeast(1))
        if (days <= 0) {
            return result
        }

        flush(ctx)
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Map ymd -> index in result.
        val indexByYmd = HashMap<Int, Int>(days)
        val calWalk = cal.clone() as Calendar
        calWalk.add(Calendar.DAY_OF_YEAR, -(days - 1))
        for (i in 0 until days) {
            indexByYmd[ymdInt(calWalk)] = i
            calWalk.add(Calendar.DAY_OF_YEAR, 1)
        }

        for ((k, vAny) in sp.all) {
            if (!k.startsWith(PREFIX_DAY)) continue
            // Key: blocked_ms_yyyymmdd_pkg
            val ymdPart = k.removePrefix(PREFIX_DAY).substringBefore('_')
            val idx = indexByYmd[ymdPart.toIntOrNull() ?: continue] ?: continue
            val v = when (vAny) {
                is Long -> vAny
                is Int -> vAny.toLong()
                is Number -> vAny.toLong()
                else -> 0L
            }
            if (v > 0L) result[idx] += v
        }
        return result
    }

    /**
     * Per-day totals for the Home heatmap: how long LoqIn was ACTUALLY blocking
     * (protection time). Days without protection records (older than the counter)
     * fall back to the legacy per-app blocked-time totals — per day we take the
     * larger of the two so mixed-semantics days never double-count.
     */
    fun getFocusDayTotalsMs(ctx: Context, days: Int): LongArray {
        val size = days.coerceAtLeast(1)
        val prot = LongArray(size)
        val legacy = LongArray(size)
        flush(ctx)
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val indexByYmd = HashMap<Int, Int>(size)
        val calWalk = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        calWalk.add(Calendar.DAY_OF_YEAR, -(size - 1))
        for (i in 0 until size) {
            indexByYmd[ymdInt(calWalk)] = i
            calWalk.add(Calendar.DAY_OF_YEAR, 1)
        }

        fun valueOf(vAny: Any?): Long = when (vAny) {
            is Long -> vAny
            is Int -> vAny.toLong()
            is Number -> vAny.toLong()
            else -> 0L
        }

        for ((k, vAny) in sp.all) {
            val v = valueOf(vAny)
            if (v <= 0L) continue
            when {
                k.startsWith(PREFIX_PROT) -> {
                    val idx = indexByYmd[k.removePrefix(PREFIX_PROT).toIntOrNull() ?: continue] ?: continue
                    prot[idx] += v
                }
                k.startsWith(PREFIX_DAY) -> {
                    val ymdPart = k.removePrefix(PREFIX_DAY).substringBefore('_')
                    val idx = indexByYmd[ymdPart.toIntOrNull() ?: continue] ?: continue
                    legacy[idx] += v
                }
            }
        }

        val maxDayMs = 86_400_000L // 24 hours — hard ceiling per calendar day
        val result = LongArray(size)
        val todayIdx = size - 1
        for (i in 0 until size) {
            val dayVal = if (i == todayIdx) {
                getProtectionTodayMs(ctx)
            } else if (prot[i] > 0L) {
                prot[i]
            } else {
                legacy[i]
            }
            result[i] = dayVal.coerceAtMost(maxDayMs)
        }
        return result
    }

    /**
     * Sums all persisted blocked_ms entries for the given package across *all* days.
     * This is used for the "Overall" stats range.
     */
    fun getBlockedMsOverall(ctx: Context, pkg: String): Long {
        if (pkg.isBlank()) {
            return 0L
        }
        flush(ctx)
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val suffix = "_" + pkg

        var sum = 0L
        for ((k, vAny) in sp.all) {
            if (!k.startsWith(PREFIX_DAY) || !k.endsWith(suffix)) continue
            val v = when (vAny) {
                is Long -> vAny
                is Int -> vAny.toLong()
                is Number -> vAny.toLong()
                else -> 0L
            }
            if (v > 0L) sum += v
        }
        return sum
    }

    /**
     * Forces a flush of buffered deltas to SharedPreferences.
     * Safe to call frequently; does nothing if no pending values exist.
     */
    fun flush(ctx: Context) {
        val toWrite: Map<String, Long>
        synchronized(lock) {
            if (pending.isEmpty()) {
                return
            }
            toWrite = HashMap(pending)
            pending.clear()
            lastFlushAtMs = System.currentTimeMillis()
        }

        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val merged = HashMap<String, Long>(toWrite.size)
        for ((k, delta) in toWrite) {
            val cur = sp.getLong(k, 0L)
            merged[k] = cur + delta
        }

        sp.edit {
            for ((k, v) in merged) {
                putLong(k, v)
            }
        }
    }

    // helpers
    private fun todayYmdInt(): Int = ymdInt(Calendar.getInstance())

    private fun ymdInt(cal: Calendar): Int {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return (y * 10000) + (m * 100) + d
    }
}
