/*
 * Switchly
 * Copyright (C) 2025-2026 Saltyy
 * Copyright (C) 2026 Switchly Contributors
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

package at.saltyy.switchly.data.prefs

import android.content.Context
import androidx.core.content.edit
import java.util.Calendar
import java.util.Locale

/**
 * Per-profile caps for temporary pauses ("pause protection for X minutes").
 *
 * - max pauses per day (count, 0 = unlimited)
 * - max minutes per single pause (0 = unlimited)
 * - max total paused minutes per day (0 = unlimited)
 *
 * Usage counters reset on day rollover. All enforcement funnels through
 * [tryConsumePause], called from SwitchModeStore.setTemporarilyDisabled.
 */
object TempPauseStore {

    private const val PREFS = "switchly_temp_pause"
    const val PREFS_NAME = PREFS

    private const val KEY_MAX_COUNT = "max_count"
    private const val KEY_MAX_MIN_PER_PAUSE = "max_min_per_pause"
    private const val KEY_MAX_MIN_PER_DAY = "max_min_per_day"
    private const val KEY_DAY = "day"
    private const val KEY_USED_COUNT = "used_count"
    private const val KEY_USED_MIN = "used_min"

    data class Caps(val maxCountPerDay: Int, val maxMinutesPerPause: Int, val maxMinutesPerDay: Int)

    private fun resolveProfile(ctx: Context, profile: String): String {
        val trimmed = profile.trim()
        if (trimmed.isNotEmpty()) return trimmed
        return ProfileStore.getCurrent(ctx).orEmpty().trim().ifEmpty { "Default" }
    }

    private fun key(profile: String, suffix: String) = "temp_pause_" + profile.trim() + "_" + suffix

    private fun todayKey(): String {
        val cal = Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d%02d%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun readIntCompat(sp: android.content.SharedPreferences, k: String): Int {
        return when (val v = sp.all[k]) {
            is Int -> v
            is Long -> v.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            is Float -> v.toInt()
            is Double -> v.toInt()
            is String -> v.toIntOrNull() ?: 0
            else -> 0
        }.coerceAtLeast(0)
    }

    fun getCaps(ctx: Context, profile: String): Caps {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = resolveProfile(ctx, profile)
        return Caps(
            maxCountPerDay = readIntCompat(sp, key(p, KEY_MAX_COUNT)),
            maxMinutesPerPause = readIntCompat(sp, key(p, KEY_MAX_MIN_PER_PAUSE)),
            maxMinutesPerDay = readIntCompat(sp, key(p, KEY_MAX_MIN_PER_DAY))
        )
    }

    fun setCaps(ctx: Context, profile: String, caps: Caps) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = resolveProfile(ctx, profile)
        sp.edit {
            putInt(key(p, KEY_MAX_COUNT), caps.maxCountPerDay.coerceAtLeast(0))
            putInt(key(p, KEY_MAX_MIN_PER_PAUSE), caps.maxMinutesPerPause.coerceAtLeast(0))
            putInt(key(p, KEY_MAX_MIN_PER_DAY), caps.maxMinutesPerDay.coerceAtLeast(0))
        }
    }

    fun hasCaps(ctx: Context, profile: String): Boolean {
        val caps = getCaps(ctx, profile)
        return caps.maxCountPerDay > 0 || caps.maxMinutesPerPause > 0 || caps.maxMinutesPerDay > 0
    }

    private fun usageRaw(ctx: Context, profile: String): Pair<Int, Int> {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = resolveProfile(ctx, profile)
        if (sp.getString(key(p, KEY_DAY), null) != todayKey()) {
            return 0 to 0
        }
        return readIntCompat(sp, key(p, KEY_USED_COUNT)) to readIntCompat(sp, key(p, KEY_USED_MIN))
    }

    fun usedCountToday(ctx: Context, profile: String): Int = usageRaw(ctx, profile).first

    fun usedMinutesToday(ctx: Context, profile: String): Int = usageRaw(ctx, profile).second

    fun remainingPauses(ctx: Context, profile: String): Int {
        val caps = getCaps(ctx, profile)
        if (caps.maxCountPerDay <= 0) return Int.MAX_VALUE
        return (caps.maxCountPerDay - usedCountToday(ctx, profile)).coerceAtLeast(0)
    }

    fun remainingMinutes(ctx: Context, profile: String): Int {
        val caps = getCaps(ctx, profile)
        if (caps.maxMinutesPerDay <= 0) return Int.MAX_VALUE
        return (caps.maxMinutesPerDay - usedMinutesToday(ctx, profile)).coerceAtLeast(0)
    }

    /**
     * Calculates the maximum single pause duration in minutes allowed under the profile's caps,
     * considering both per-pause max and daily remaining minutes.
     * Returns [Int.MAX_VALUE] if unlimited, or 0 if limits are exhausted.
     */
    fun maxAllowedDurationMinutes(ctx: Context, profile: String): Int {
        val p = resolveProfile(ctx, profile)
        if (!hasCaps(ctx, p)) return Int.MAX_VALUE
        if (remainingPauses(ctx, p) <= 0 || remainingMinutes(ctx, p) <= 0) return 0
        val caps = getCaps(ctx, p)
        var max = if (caps.maxMinutesPerPause > 0) caps.maxMinutesPerPause else Int.MAX_VALUE
        if (caps.maxMinutesPerDay > 0) {
            max = minOf(max, remainingMinutes(ctx, p))
        }
        return max
    }

    fun onProfileRenamed(ctx: Context, oldProfile: String, newProfile: String) {
        val old = oldProfile.trim()
        val new = newProfile.trim()
        if (old.isEmpty() || new.isEmpty() || old == new) return
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val suffixes = listOf(KEY_MAX_COUNT, KEY_MAX_MIN_PER_PAUSE, KEY_MAX_MIN_PER_DAY, KEY_DAY, KEY_USED_COUNT, KEY_USED_MIN)
        sp.edit {
            for (suffix in suffixes) {
                val oldKey = key(old, suffix)
                if (!sp.contains(oldKey)) continue
                when (suffix) {
                    KEY_DAY -> putString(key(new, suffix), sp.getString(oldKey, null))
                    KEY_USED_COUNT, KEY_USED_MIN, KEY_MAX_COUNT, KEY_MAX_MIN_PER_PAUSE, KEY_MAX_MIN_PER_DAY -> {
                        when (val v = sp.all[oldKey]) {
                            is Int -> putInt(key(new, suffix), v)
                            is Long -> putLong(key(new, suffix), v)
                            is String -> putString(key(new, suffix), v)
                        }
                    }
                }
                remove(oldKey)
            }
        }
    }

    fun onProfileRemoved(ctx: Context, profile: String) {
        val p = profile.trim()
        if (p.isEmpty()) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove(key(p, KEY_MAX_COUNT))
            remove(key(p, KEY_MAX_MIN_PER_PAUSE))
            remove(key(p, KEY_MAX_MIN_PER_DAY))
            remove(key(p, KEY_DAY))
            remove(key(p, KEY_USED_COUNT))
            remove(key(p, KEY_USED_MIN))
        }
    }

    fun copyProfile(ctx: Context, fromProfile: String, toProfile: String) {
        val caps = getCaps(ctx, fromProfile)
        setCaps(ctx, toProfile, caps)
    }

    fun resetToday(ctx: Context, profile: String) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = profile.trim()
        sp.edit {
            putString(key(p, KEY_DAY), todayKey())
            putInt(key(p, KEY_USED_COUNT), 0)
            putInt(key(p, KEY_USED_MIN), 0)
        }
    }

    /**
     * Attempts to consume one pause of [requestedMs] for [profile].
     * Always tracks usage statistics. Enforces caps if configured.
     * @return granted milliseconds (clamped to caps), or 0 when denied.
     */
    fun tryConsumePause(ctx: Context, profile: String, requestedMs: Long): Long {
        val p = resolveProfile(ctx, profile)
        if (requestedMs <= 0L) return 0L
        val caps = getCaps(ctx, p)
        val hasLimits = hasCaps(ctx, p)

        val requestedMinutes = (requestedMs / 60_000L).toInt().coerceAtLeast(1)
        if (hasLimits) {
            if (remainingPauses(ctx, p) <= 0 || remainingMinutes(ctx, p) <= 0) {
                return 0L
            }
            if (caps.maxMinutesPerPause > 0 && requestedMinutes > caps.maxMinutesPerPause) {
                return 0L
            }
            if (caps.maxMinutesPerDay > 0 && requestedMinutes > remainingMinutes(ctx, p)) {
                return 0L
            }
        }
        val minutes = requestedMinutes

        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val (usedCount, usedMin) = usageRaw(ctx, p)
        sp.edit {
            putString(key(p, KEY_DAY), todayKey())
            putInt(key(p, KEY_USED_COUNT), usedCount + 1)
            putInt(key(p, KEY_USED_MIN), usedMin + minutes)
        }
        AppLogStore.append(
            ctx, "Profiles",
            "Temp pause consumed profile=$p minutes=$minutes " +
                "used=${usedCount + 1}/${usedMin + minutes}"
        )
        return minutes * 60_000L
    }
}
