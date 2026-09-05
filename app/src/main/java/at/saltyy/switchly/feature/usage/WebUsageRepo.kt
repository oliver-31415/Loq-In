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

package at.saltyy.switchly.feature.usage

import android.content.Context
import androidx.core.content.ContextCompat
import at.saltyy.switchly.R
import at.saltyy.switchly.data.prefs.WebUsageStore

// Loads and transforms web usage data.
object WebUsageRepo {

    fun getLast7DaysSummary(ctx: Context, topN: Int = 20): UsageSummary {
        return getLastNDaysSummary(ctx, days = 7, topN = topN)
    }

    fun getLastNDaysSummary(ctx: Context, days: Int, topN: Int = 20): UsageSummary {
        return getSummary(ctx, days = days, topN = topN)
    }

    fun getThisMonthSummary(ctx: Context, topN: Int = 20): UsageSummary {
        // Trailing 30 days, NOT the calendar month.
        return getSummary(ctx, days = 30, topN = topN)
    }

    fun getThisYearSummary(ctx: Context, topN: Int = 20): UsageSummary {
        val days = java.util.Calendar.getInstance()
            .get(java.util.Calendar.DAY_OF_YEAR)
            .coerceAtLeast(1)
            .coerceAtMost(366)
        return getSummary(ctx, days = days, topN = topN)
    }

    fun getDateRangeSummary(ctx: Context, startMs: Long, endMs: Long, topN: Int = 20): UsageSummary {
        val totalsByDomain = WebUsageStore.getUsageMsMapForDateRange(ctx, startMs, endMs)
        if (totalsByDomain.isEmpty()) {
            return UsageSummary(0L, emptyList())
        }

        val totalAll = totalsByDomain.values.sum().coerceAtLeast(1L)
        val icon = ContextCompat.getDrawable(ctx, R.drawable.language_24)
        val top = totalsByDomain.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { (domain, ms) ->
                AppUsage(
                    packageName = domain,
                    label = domain,
                    icon = icon,
                    timeMs = ms,
                    percent = (ms.toFloat() / totalAll.toFloat()).coerceIn(0f, 1f)
                )
            }

        return UsageSummary(totalTimeMs = totalsByDomain.values.sum(), topApps = top)
    }

    fun getOverallSummary(ctx: Context, topN: Int = 20): UsageSummary {
        WebUsageStore.flush(ctx)
        val totalsByDomain = HashMap<String, Long>()
        for (domain in WebUsageStore.getDomains(ctx)) {
            val ms = WebUsageStore.getUsageMsAllTime(ctx, domain)
            if (domain.isNotBlank() && ms > 0L) {
                totalsByDomain[domain] = ms
            }
        }

        if (totalsByDomain.isEmpty()) {
            return UsageSummary(0L, emptyList())
        }

        val totalAll = totalsByDomain.values.sum().coerceAtLeast(1L)
        val icon = ContextCompat.getDrawable(ctx, R.drawable.language_24)

        val top = totalsByDomain.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { (domain, ms) ->
                AppUsage(
                    packageName = domain,
                    label = domain,
                    icon = icon,
                    timeMs = ms,
                    percent = (ms.toFloat()/totalAll.toFloat()).coerceIn(0f, 1f)
                )
            }

        return UsageSummary(totalTimeMs = totalsByDomain.values.sum(), topApps = top)
    }

    fun getTodaySummary(ctx: Context, topN: Int = 20): UsageSummary {
        return getSummary(ctx, days = 1, topN = topN)
    }

    private fun getSummary(ctx: Context, days: Int, topN: Int): UsageSummary {
        // Include buffered increments from the Accessibility service.
        WebUsageStore.flush(ctx)
        val domains = WebUsageStore.getDomains(ctx)
        if (domains.isEmpty()) {
            return UsageSummary(0L, emptyList())
        }

        val totals = ArrayList<Pair<String, Long>>(domains.size)
        for (d in domains) {
            val sum = WebUsageStore.getUsageMsForLastNDays(ctx, d, days).sum()
            if (sum > 0L) totals.add(d to sum)
        }
        val totalAll = totals.sumOf { it.second }.coerceAtLeast(1L)
        val icon = ContextCompat.getDrawable(ctx, R.drawable.language_24)

        val top = totals
            .sortedByDescending { it.second }
            .take(topN)
            .map { (domain, ms) ->
                AppUsage(
                    packageName = domain,
                    label = domain,
                    icon = icon,
                    timeMs = ms,
                    percent = (ms.toFloat()/totalAll.toFloat()).coerceIn(0f, 1f)
                )
            }

        return UsageSummary(totalTimeMs = totals.sumOf { it.second }, topApps = top)
    }
}
