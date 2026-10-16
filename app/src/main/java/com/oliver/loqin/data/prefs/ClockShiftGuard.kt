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
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import com.oliver.loqin.util.ProtectionChangeGate
import com.oliver.loqin.util.getLongCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Keeps protection windows honest when the device clock is changed.
 *
 * Breaks, Emergency Unlock, the undo window, temporary allows and queued protection changes store
 * wall-clock deadlines. Without this, setting the clock forward applied a queued weakening change
 * at once, and setting it back made a break last longer. When the wall clock moves against the
 * monotonic clock, every stored deadline moves by the same amount, so each window keeps its real
 * length.
 *
 * While automatic time is off, manual jumps also add up to an offset, so "once a day" limits
 * (Emergency Unlock, break caps) follow the real date rather than the one the user set.
 */
object ClockShiftGuard {

    private const val PREFS = "loqin_prefs"
    private const val KEY_ANCHOR_WALL = "clock_guard_anchor_wall_ms"
    private const val KEY_ANCHOR_ELAPSED = "clock_guard_anchor_elapsed_ms"
    private const val KEY_ANCHOR_BOOT = "clock_guard_anchor_boot"
    private const val KEY_OFFSET = "clock_guard_offset_ms"

    // Network time corrections and alarm latency stay well inside this.
    private const val TOLERANCE_MS = 90_000L

    private val SHIFTED_DEADLINE_KEYS = listOf(
        "switch_mode_temp_disable_until",
        "switch_mode_temp_enable_until",
        "emergency_bypass_until",
        "enable_undo_until_ms",
    )
    private const val TEMP_ALLOW_PREFIX = "temp_allow_"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Compares the wall clock with the monotonic clock since the last check and shifts the stored
     * deadlines when they disagree. Returns the shift applied (0 when none).
     */
    @Synchronized
    fun reconcile(context: Context): Long {
        val ctx = context.applicationContext
        val sp = prefs(ctx)
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val boot = bootCount(ctx)

        val anchorWall = sp.getLongCompat(KEY_ANCHOR_WALL, 0L)
        val anchorElapsed = sp.getLongCompat(KEY_ANCHOR_ELAPSED, 0L)
        val anchorBoot = sp.getLongCompat(KEY_ANCHOR_BOOT, -1L)

        var delta = 0L
        if (anchorWall > 0L) {
            val sameBoot = boot >= 0 && boot == anchorBoot && elapsed >= anchorElapsed
            delta = if (sameBoot) {
                wall - (anchorWall + (elapsed - anchorElapsed))
            } else {
                // Across a reboot only a clock set behind the last check can be told apart.
                (wall - anchorWall).coerceAtMost(0L)
            }
            if (abs(delta) <= TOLERANCE_MS) delta = 0L
        }

        // Anchor and shifted deadlines go in one commit, so a crash can't record the new
        // anchor while leaving the deadlines unshifted.
        val all = sp.all
        val autoTime = isAutoTimeOn(ctx)
        sp.edit(commit = true) {
            putLong(KEY_ANCHOR_WALL, wall)
            putLong(KEY_ANCHOR_ELAPSED, elapsed)
            putLong(KEY_ANCHOR_BOOT, boot)
            if (delta != 0L) {
                for (key in SHIFTED_DEADLINE_KEYS) {
                    val until = (all[key] as? Long) ?: continue
                    if (until > 0L) putLong(key, until + delta)
                }
                for ((key, value) in all) {
                    if (!key.startsWith(TEMP_ALLOW_PREFIX)) continue
                    val until = value as? Long ?: continue
                    if (until > 0L) putLong(key, until + delta)
                }
            }
            if (autoTime) {
                putLong(KEY_OFFSET, 0L)
            } else if (delta != 0L) {
                putLong(KEY_OFFSET, sp.getLongCompat(KEY_OFFSET, 0L) + delta)
            }
        }
        if (delta != 0L) {
            runCatching { ProtectionChangeGate.shiftPending(ctx, delta) }
            runCatching {
                DiagnosticsTimelineStore.record(
                    ctx, "Protection", "Clock changed",
                    "shift=${delta / 1000}s offset=${sp.getLongCompat(KEY_OFFSET, 0L) / 1000}s",
                )
            }
        }
        return delta
    }

    /** Now on the real calendar: the wall clock minus manual jumps made while automatic time is off. */
    fun trustedToday(context: Context, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val ctx = context.applicationContext
        // With automatic time on, the wall clock is network time.
        val offset = if (isAutoTimeOn(ctx)) 0L else prefs(ctx).getLongCompat(KEY_OFFSET, 0L)
        return Instant.ofEpochMilli(System.currentTimeMillis() - offset).atZone(zone).toLocalDate()
    }

    private fun isAutoTimeOn(ctx: Context): Boolean =
        runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.AUTO_TIME, 1) == 1 }
            .getOrDefault(true)

    private fun bootCount(ctx: Context): Long =
        runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT).toLong() }
            .getOrDefault(-1L)
}
