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

/**
 * "Pause before opening" rules: a profile can make chosen apps show a short countdown screen
 * instead of a hard block. The wait can grow with every open today (soft friction, see
 * docs/feature-research.md). Keys use the `pause_rule_` prefix (backed up with blocked apps);
 * per-day outcomes use `pause_outcome_` (statistics).
 */
object PauseRuleStore {
    private const val PREFS = "loqin_prefs"
    private const val KEY_APPS = "pause_rule_apps__"
    private const val KEY_BASE_SECONDS = "pause_rule_base_sec__"
    private const val KEY_STEP_SECONDS = "pause_rule_step_sec__"
    private const val KEY_OUTCOME = "pause_outcome_"
    private const val APP_OUTCOME_SEPARATOR = "__"

    const val DEFAULT_BASE_SECONDS = 10
    const val DEFAULT_STEP_SECONDS = 5
    const val MAX_SECONDS = 60
    private const val MIN_PAUSES_FOR_SUMMARY = 3

    enum class Outcome(val key: String) { CONTINUED("continued"), LEFT("left") }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getPausedApps(context: Context, profile: String): Set<String> =
        prefs(context).getStringSet(KEY_APPS + profile, emptySet())?.toSet() ?: emptySet()

    fun isPaused(context: Context, profile: String, pkg: String): Boolean = pkg in getPausedApps(context, profile)

    fun setPaused(context: Context, profile: String, pkg: String, paused: Boolean) {
        val next = getPausedApps(context, profile).toMutableSet()
        if (paused) next += pkg else next -= pkg
        prefs(context).edit { putStringSet(KEY_APPS + profile, next) }
    }

    fun getBaseSeconds(context: Context, profile: String): Int =
        prefs(context).getInt(KEY_BASE_SECONDS + profile, DEFAULT_BASE_SECONDS)

    fun getStepSeconds(context: Context, profile: String): Int =
        prefs(context).getInt(KEY_STEP_SECONDS + profile, DEFAULT_STEP_SECONDS)

    fun setTiming(context: Context, profile: String, baseSeconds: Int, stepSeconds: Int) {
        prefs(context).edit {
            putInt(KEY_BASE_SECONDS + profile, baseSeconds.coerceIn(1, MAX_SECONDS))
            putInt(KEY_STEP_SECONDS + profile, stepSeconds.coerceIn(0, MAX_SECONDS))
        }
    }

    /** Wait for the next open: base + step per earlier open today, capped. */
    fun waitSeconds(baseSeconds: Int, stepSeconds: Int, opensBeforeToday: Int): Int {
        val base = baseSeconds.coerceIn(1, MAX_SECONDS)
        val step = stepSeconds.coerceIn(0, MAX_SECONDS)
        val earlier = opensBeforeToday.coerceAtLeast(0)
        val total = base.toLong() + step.toLong() * earlier
        return total.coerceAtMost(MAX_SECONDS.toLong()).toInt()
    }

    /**
     * Records one outcome for today under the overall key and, when [pkg] is set, the per-app key
     * so the app detail page can show how pauses went for that app.
     */
    fun recordOutcome(context: Context, outcome: Outcome, pkg: String) {
        val day = todayYmd()
        val keys = buildList {
            add(outcomeKey(outcome, day))
            if (pkg.isNotBlank()) add(appOutcomeKey(outcome, day, pkg))
        }
        val sp = prefs(context)
        sp.edit {
            for (key in keys) putInt(key, sp.getInt(key, 0) + 1)
        }
    }

    fun outcomesToday(context: Context, outcome: Outcome): Int =
        prefs(context).getInt(outcomeKey(outcome, todayYmd()), 0)

    /** Sum of [outcome] over the last [days] days, today included. */
    fun outcomesForLastDays(context: Context, outcome: Outcome, days: Int = 7): Int {
        val sp = prefs(context)
        val c = Calendar.getInstance()
        var total = 0
        repeat(days.coerceAtLeast(1)) {
            total += sp.getInt(outcomeKey(outcome, ymd(c)), 0)
            c.add(Calendar.DAY_OF_YEAR, -1)
        }
        return total
    }

    /** Per-day counts of [outcome] for [pkg], one entry per day in [ymds] and in the same order. */
    fun appOutcomesForDays(context: Context, pkg: String, outcome: Outcome, ymds: List<Int>): List<Int> {
        if (pkg.isBlank()) return ymds.map { 0 }
        val sp = prefs(context)
        return ymds.map { ymd -> sp.getInt(appOutcomeKey(outcome, ymd, pkg), 0) }
    }

    /** Overall outcome key, e.g. `pause_outcome_left_20261011`. */
    fun outcomeKey(outcome: Outcome, ymd: Int): String = KEY_OUTCOME + outcome.key + "_" + ymd

    /** Per-app outcome key, e.g. `pause_outcome_left_20261011__com.example.app`. */
    fun appOutcomeKey(outcome: Outcome, ymd: Int, pkg: String): String =
        outcomeKey(outcome, ymd) + APP_OUTCOME_SEPARATOR + pkg

    /** Whether there are enough pauses this week for "you left N of M times" to mean something. */
    fun shouldShowWeeklyOutcome(left: Int, continued: Int): Boolean = left + continued >= MIN_PAUSES_FOR_SUMMARY

    fun onProfileRenamed(context: Context, old: String, new: String) {
        if (old == new) return
        val sp = prefs(context)
        sp.edit {
            for (prefix in listOf(KEY_APPS, KEY_BASE_SECONDS, KEY_STEP_SECONDS)) {
                val value = sp.all[prefix + old] ?: continue
                remove(prefix + old)
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is Int -> putInt(prefix + new, value)
                    is Set<*> -> putStringSet(prefix + new, value as Set<String>)
                }
            }
        }
    }

    fun onProfileRemoved(context: Context, profile: String) {
        prefs(context).edit {
            remove(KEY_APPS + profile)
            remove(KEY_BASE_SECONDS + profile)
            remove(KEY_STEP_SECONDS + profile)
        }
    }

    private fun todayYmd(): Int = ymd(Calendar.getInstance())

    private fun ymd(c: Calendar): Int =
        c.get(Calendar.YEAR) * 10_000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}
