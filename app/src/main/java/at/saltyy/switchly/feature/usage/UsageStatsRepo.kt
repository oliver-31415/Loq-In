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

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import at.saltyy.switchly.data.prefs.OpenCountStore
import at.saltyy.switchly.data.prefs.UsageStore
import at.saltyy.switchly.util.AppBlockSafety
import at.saltyy.switchly.util.PackageManagerApiCompat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

// Loads and transforms usage stats data.
object UsageStatsRepo {

    // UsageEvents.Event ACTIVITY_* numeric values.
    // Referencing these as local constants keeps minSdk 27 builds lint-clean without using newer SDK fields directly.
    private const val EVENT_ACTIVITY_RESUMED = 1
    private const val EVENT_ACTIVITY_PAUSED = 2
    private const val EVENT_SCREEN_NON_INTERACTIVE = 16
    private const val EVENT_KEYGUARD_SHOWN = 17

    private fun startOfDayLocal(timeMs: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = timeMs
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun startOfTodayLocal(): Long = startOfDayLocal(System.currentTimeMillis())

    private fun startOfPreviousLocalDay(timeMs: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = startOfDayLocal(timeMs)
        c.add(Calendar.DAY_OF_YEAR, -1)
        return c.timeInMillis
    }

    private fun startOfTomorrowLocal(): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = startOfTodayLocal()
        c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun isDayAligned(timeMs: Long): Boolean = startOfDayLocal(timeMs) == timeMs

    private fun isSingleLocalDayWindow(from: Long, to: Long): Boolean {
        if (!isDayAligned(from) || to <= from) {
            return false
        }
        val nextDay = Calendar.getInstance().apply {
            timeInMillis = from
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return to <= nextDay
    }

    private fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private fun buildTodayHourlyFallback(
        ctx: Context,
        packageName: String,
        now: Long = System.currentTimeMillis()
    ): List<Long> {
        val total = UsageStore.getUsageMsToday(ctx, packageName).coerceAtLeast(0L)
        if (total <= 0L) {
            return List(24) { 0L }
        }
        val buckets = LongArray(24)
        val dayStart = startOfDayLocal(now)
        val hourIndex = (((now - dayStart) / TimeUnit.HOURS.toMillis(1)).toInt()).coerceIn(0, 23)
        buckets[hourIndex] = total
        return buckets.map { it.coerceAtLeast(0L) }
    }

    fun hasUsageAccess(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getLast7DaysSummary(ctx: Context, topN: Int = 20): UsageSummary {
        return getLastNDaysSummary(ctx, days = 7, topN = topN)
    }

    fun getLastNDaysSummary(ctx: Context, days: Int, topN: Int = 20): UsageSummary {
        val now = System.currentTimeMillis()
        val from = now - TimeUnit.DAYS.toMillis(days.toLong().coerceAtLeast(1L))
        return getSummary(ctx, from, now, topN)
    }

    fun getThisMonthSummary(ctx: Context, topN: Int = 20): UsageSummary {
        // Trailing 30 days (today + previous 29), NOT the calendar month —
        // matches every other Month range in the app.
        return getLastNDaysSummary(ctx, 30, topN)
    }

    fun getThisYearSummary(ctx: Context, topN: Int = 20): UsageSummary {
        val c = Calendar.getInstance()
        c.set(Calendar.MONTH, Calendar.JANUARY)
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val from = c.timeInMillis
        val to = System.currentTimeMillis()
        return getSummary(ctx, from, to, topN)
    }

    fun getOverallSummary(ctx: Context, topN: Int = 20): UsageSummary {
        val now = System.currentTimeMillis()
        return getSummary(ctx, 0L, now, topN)
    }

    fun getTodaySummary(ctx: Context, topN: Int = 20): UsageSummary {
        // User-facing Today statistics should represent the whole device day, not only the time during which Switchly protection happened to be enabled.
        // When Usage Access is available and this query is already running off the main thread, prefer Android's live usage data.
        // getSingleDayUsageByPackage() also merges Switchly's local counter as a floor, which keeps the result useful on OEMs that publish UsageStats with a delay.
        // Important: profile/app-limit enforcement intentionally continues to use Switchly's own counters. 
        // This display-only path must not make a delayed or OEM-inflated UsageStats value trigger a limit early.
        if (!isMainThread() && hasUsageAccess(ctx)) {
            val systemSummary = getSummary(ctx, startOfTodayLocal(), System.currentTimeMillis(), topN)
            if (systemSummary.totalTimeMs > 0L || systemSummary.topApps.isNotEmpty()) {
                return systemSummary
            }
        }

        val byPkg = HashMap(UsageStore.getUsageMsMapToday(ctx))
        val it = byPkg.keys.iterator()
        while (it.hasNext()) {
            val pkg = it.next()
            if (shouldExcludePackage(ctx, pkg) || !isInstalled(ctx, pkg)) {
                it.remove()
            }
        }

        val total = byPkg.values.sum()
        val pm = ctx.packageManager
        val top = byPkg.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { (pkg, ms) ->
                val label = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Throwable) {
                    pkg
                }
                val icon = try {
                    pm.getApplicationIcon(pkg)
                } catch (_: Throwable) {
                    null
                }
                val percent = if (total > 0L) ms.toFloat() / total.toFloat() else 0f
                AppUsage(pkg, label, icon, ms, percent)
            }

        return UsageSummary(totalTimeMs = total, topApps = top)
    }

    fun getTodayPerHour(
        ctx: Context,
        packageName: String,
        now: Long = System.currentTimeMillis()
    ): List<Long> {
        if (packageName.isBlank()) {
            return List(24) { 0L }
        }
        if (isMainThread()) {
            return buildTodayHourlyFallback(ctx, packageName, now)
        }

        val safeNow = now.coerceAtMost(System.currentTimeMillis())
        val dayStart = startOfDayLocal(safeNow)
        val queryFrom = startOfPreviousLocalDay(dayStart)
        val buckets = LongArray(24)
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = try {
            // Look back one local day only to establish which package, if any, was already foreground at midnight.
            // addRangeToHourlyBuckets() clips all counted time to dayStart, so the previous day's time is never included.
            usm.queryEvents(queryFrom, safeNow)
        } catch (_: SecurityException) {
            null
        } catch (_: Throwable) {
            null
        }

        if (events != null) {
            var foregroundPackage: String? = null
            var foregroundStart = queryFrom

            fun closeForeground(atMs: Long) {
                if (foregroundPackage == packageName) {
                    addRangeToHourlyBuckets(
                        buckets,
                        dayStart,
                        maxOf(foregroundStart, dayStart),
                        minOf(atMs, safeNow)
                    )
                }
                foregroundPackage = null
            }

            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val eventAt = e.timeStamp.coerceIn(queryFrom, safeNow)
                when (e.eventType) {
                    EVENT_SCREEN_NON_INTERACTIVE,
                    EVENT_KEYGUARD_SHOWN -> closeForeground(eventAt)

                    EVENT_ACTIVITY_RESUMED -> {
                        val pkg = e.packageName ?: continue
                        if (foregroundPackage == pkg) continue
                        closeForeground(eventAt)
                        foregroundPackage = pkg
                        foregroundStart = eventAt
                    }

                    EVENT_ACTIVITY_PAUSED -> {
                        if (e.packageName == foregroundPackage) {
                            closeForeground(eventAt)
                        }
                    }
                }
            }

            if (foregroundPackage != null) {
                closeForeground(safeNow)
            }
        }

        val internalToday = UsageStore.getUsageMsToday(ctx, packageName).coerceAtLeast(0L)
        val bucketTotal = buckets.sum().coerceAtLeast(0L)
        if (internalToday > bucketTotal) {
            val hourIndex = (((safeNow - dayStart) / TimeUnit.HOURS.toMillis(1)).toInt()).coerceIn(0, 23)
            buckets[hourIndex] += (internalToday - bucketTotal)
        }

        return buckets.map { it.coerceAtLeast(0L) }
    }

    fun getSessionsToday(ctx: Context, packageName: String): Int {
        if (isMainThread()) {
            return OpenCountStore.getTodayAllProfiles(ctx, packageName)
        }
        val now = System.currentTimeMillis()
        val start = startOfTodayLocal()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(start, now)
        var sessions = 0
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName == packageName && e.eventType == EVENT_ACTIVITY_RESUMED) {
                sessions++
            }
        }
        return sessions
    }

    private fun getPackageUsageForWindowClamped(
        ctx: Context,
        from: Long,
        to: Long,
        packageName: String
    ): Long {
        val safeTo = to.coerceAtMost(System.currentTimeMillis())
        val windowMs = (safeTo - from).coerceAtLeast(0L)
        if (packageName.isBlank() || windowMs <= 0L) {
            return 0L
        }
        val reported = getSummary(ctx, from, safeTo, topN = 1, onlyPackage = packageName).totalTimeMs
        // A single package cannot physically be in the foreground longer than the
        // queried window itself. Some OEMs over-report package usage in bucketed
        // queries, especially for historical windows, so clamp to the window size.
        return reported.coerceIn(0L, windowMs)
    }

    fun getTotalMsForWindow(ctx: Context, from: Long, to: Long, packageName: String): Long {
        return getPackageUsageForWindowClamped(ctx, from, to, packageName)
    }

    fun getTodayMsForPackage(ctx: Context, packageName: String, now: Long = System.currentTimeMillis()): Long {
        return getPackageUsageForWindowClamped(ctx, startOfDayLocal(now), now, packageName)
    }

    fun getSessionsForWindow(ctx: Context, from: Long, to: Long, packageName: String): Int {
        val safeTo = to.coerceAtMost(System.currentTimeMillis())
        if (packageName.isBlank() || safeTo <= from) {
            return 0
        }
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = try {
            usm.queryEvents(from, safeTo)
        } catch (_: SecurityException) {
            return 0
        } catch (_: Throwable) {
            return 0
        }
        var sessions = 0
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName == packageName && e.eventType == EVENT_ACTIVITY_RESUMED) {
                sessions++
            }
        }
        return sessions
    }

    fun getSessionsForCurrentWeek(ctx: Context, packageName: String): Int {
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val diff = (7 + (get(Calendar.DAY_OF_WEEK) - firstDayOfWeek)) % 7
            add(Calendar.DAY_OF_YEAR, -diff)
        }
        return getSessionsForWindow(ctx, c.timeInMillis, now, packageName)
    }

    fun getSessionsForCurrentMonth(ctx: Context, packageName: String): Int {
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return getSessionsForWindow(ctx, c.timeInMillis, now, packageName)
    }

    fun getSessionsForCurrentYear(ctx: Context, packageName: String): Int {
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return getSessionsForWindow(ctx, c.timeInMillis, now, packageName)
    }

    fun getSessionsOverall(ctx: Context, packageName: String): Int {
        return getSessionsForWindow(ctx, 0L, System.currentTimeMillis(), packageName)
    }

    fun getLast7DaysPerDay(ctx: Context, packageName: String): List<Long> {
        val todayStart = startOfTodayLocal()
        val c = Calendar.getInstance()
        c.timeInMillis = todayStart

        val dayStarts = mutableListOf<Long>()
        for (i in 0 until 7) {
            dayStarts.add(c.timeInMillis)
            c.add(Calendar.DAY_OF_YEAR, -1)
        }
        dayStarts.reverse()

        return dayStarts.mapIndexed { i, start ->
            val end = if (i == dayStarts.lastIndex) startOfTomorrowLocal() else dayStarts[i + 1]
            getPackageUsageForWindowClamped(ctx, start, end, packageName)
        }
    }

    fun getLastNDaysPerDay(ctx: Context, packageName: String, days: Int): List<Long> {
        val n = days.coerceAtLeast(1).coerceAtMost(60)
        val todayStart = startOfTodayLocal()
        val c = Calendar.getInstance()
        c.timeInMillis = todayStart

        val dayStarts = mutableListOf<Long>()
        for (i in 0 until n) {
            dayStarts.add(c.timeInMillis)
            c.add(Calendar.DAY_OF_YEAR, -1)
        }
        dayStarts.reverse()

        return dayStarts.mapIndexed { i, start ->
            val end = if (i == dayStarts.lastIndex) startOfTomorrowLocal() else dayStarts[i + 1]
            getPackageUsageForWindowClamped(ctx, start, end, packageName)
        }
    }

    /**
     * Returns the earliest timestamp (ms) that the system actually reports usage for within the window.
     * Useful for showing a UI banner like: "Data available since ..." on devices that retain only ~1 week.
     */
    fun getSingleDayUsageMapForImport(ctx: Context, dayStart: Long, dayEnd: Long): Map<String, Long> {
        val safeEnd = dayEnd.coerceAtMost(System.currentTimeMillis())
        if (safeEnd <= dayStart) {
            return emptyMap()
        }
        val windowMs = (safeEnd - dayStart).coerceAtLeast(0L)

        // Import existing app-open data conservatively.
        // For the one-time migration we intentionally do NOT merge multiple system sources, because that can inflate historical values on some devices.
        // We only import the raw daily UsageStats values that Android already exposes for the requested window.
        val byPkg = queryDailyUsage(
            ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager,
            dayStart,
            safeEnd,
            onlyPackage = null
        )

        val out = linkedMapOf<String, Long>()
        for ((pkg, rawMs) in byPkg) {
            if (UsageInsightsAppFilter.shouldAlwaysHide(pkg)) continue
            if (!isInstalled(ctx, pkg)) continue
            val ms = rawMs.coerceIn(0L, windowMs)
            if (ms > 0L) out[pkg] = ms
        }
        return out
    }

    /**
     * Counts one launch whenever the foreground application actually changes.
     * Android can emit several ACTIVITY_RESUMED events while the user remains inside one app, for example during internal Activity changes, tab navigation or OEM window updates. 
     * Those events are not separate app launches and must not inflate the daily counter.
     */
    fun getSessionCountMapForWindow(ctx: Context, from: Long, to: Long): Map<String, Int> {
        if (isMainThread()) {
            return emptyMap()
        }
        val safeTo = to.coerceAtMost(System.currentTimeMillis())
        if (safeTo <= from) {
            return emptyMap()
        }

        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = try {
            usm.queryEvents(from, safeTo)
        } catch (_: SecurityException) {
            return emptyMap()
        } catch (_: Throwable) {
            return emptyMap()
        }

        val homePackages = getHomePackages(ctx)
        val inputMethodPackage = AppBlockSafety.getDefaultInputMethodPackage(ctx)
        val counts = linkedMapOf<String, Int>()
        val event = UsageEvents.Event()
        var foregroundPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                EVENT_SCREEN_NON_INTERACTIVE,
                EVENT_KEYGUARD_SHOWN -> foregroundPackage = null

                EVENT_ACTIVITY_RESUMED -> {
                    val packageName = event.packageName ?: continue
                    val normalized = packageName.lowercase(Locale.US)
                    val isHomeSurface = packageName in homePackages ||
                        normalized.contains("launcher") ||
                        normalized.contains("quickstep")

                    if (UsageInsightsAppFilter.isSwitchlyPackage(packageName) || isHomeSurface) {
                        foregroundPackage = null
                        continue
                    }
                    if (packageName == "com.android.systemui" ||
                        packageName == inputMethodPackage ||
                        UsageInsightsAppFilter.shouldAlwaysHide(packageName)
                    ) {
                        continue
                    }
                    if (UsageInsightsAppFilter.shouldHide(ctx, packageName) || !isInstalled(ctx, packageName)) {
                        continue
                    }
                    if (foregroundPackage == packageName) {
                        continue
                    }

                    foregroundPackage = packageName
                    counts[packageName] = (counts[packageName] ?: 0) + 1
                }
            }
        }
        return counts
    }

    fun getEarliestAvailableUsageMs(ctx: Context, from: Long, to: Long): Long? {
        if (isMainThread()) {
            return null
        }
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, to)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        if (stats.isNullOrEmpty()) {
            return null
        }
        return stats.minOfOrNull { it.firstTimeStamp }
    }

    private fun getHomePackages(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return try {
            PackageManagerApiCompat.queryIntentActivities(pm, intent)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private fun shouldExcludePackage(ctx: Context, pkg: String): Boolean {
        return UsageInsightsAppFilter.shouldHide(ctx, pkg)
    }

    private fun getSummary(
        ctx: Context,
        from: Long,
        to: Long,
        topN: Int,
        onlyPackage: String? = null
    ): UsageSummary {
        val safeTo = to.coerceAtMost(System.currentTimeMillis())
        if (safeTo <= from) {
            return UsageSummary(totalTimeMs = 0L, topApps = emptyList())
        }

        val byPkg = if (isSingleLocalDayWindow(from, safeTo)) {
            getSingleDayUsageByPackage(ctx, from, safeTo, onlyPackage)
        } else {
            getBucketedUsageByPackage(ctx, from, safeTo, onlyPackage)
        }

        if (onlyPackage == null) {
            val it = byPkg.keys.iterator()
            while (it.hasNext()) {
                val pkg = it.next()
                if (shouldExcludePackage(ctx, pkg)) {
                    it.remove()
                }
            }
        }

        run {
            val it = byPkg.keys.iterator()
            while (it.hasNext()) {
                val pkg = it.next()
                if (!isInstalled(ctx, pkg)) it.remove()
            }
        }

        val total = byPkg.values.sum()
        val pm = ctx.packageManager
        val top = byPkg.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { (pkg, ms) ->
                val label = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Throwable) {
                    pkg
                }
                val icon = try {
                    pm.getApplicationIcon(pkg)
                } catch (_: Throwable) {
                    null
                }
                val percent = if (total > 0L) ms.toFloat() / total.toFloat() else 0f
                AppUsage(pkg, label, icon, ms, percent)
            }

        return UsageSummary(totalTimeMs = total, topApps = top)
    }

    private fun getSingleDayUsageByPackage(
        ctx: Context,
        dayStart: Long,
        dayEnd: Long,
        onlyPackage: String?
    ): HashMap<String, Long> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val byPkg = HashMap<String, Long>()
        val isCurrentLocalDay = dayStart == startOfTodayLocal()

        if (isCurrentLocalDay) {
            // Android's DAILY/aggregate UsageStats buckets are not guaranteed to be clipped to the exact local-midnight window requested by the caller.
            // On some devices a bucket overlapping midnight contains foreground time from the previous evening, which can make "Today" keep growing before midnight and then carry that usage into the next day.
            //
            // UsageEvents are timestamped, so derive the live current-day value from events and clip every interval to [local midnight, now].
            // Start the event scan one local day earlier only to recover an app that was already foreground exactly at midnight; time before dayStart is never counted.
            mergeUsage(
                byPkg,
                queryEventDerivedUsage(
                    usm = usm,
                    from = dayStart,
                    to = dayEnd,
                    onlyPackage = onlyPackage,
                    eventQueryFrom = startOfPreviousLocalDay(dayStart)
                )
            )
        } else {
            // Keep the existing historical-day strategy.
            // Historical system data can be sparse on some OEMs, so the bucketed sources remain useful outside the live Today window.
            mergeUsage(byPkg, queryAggregateUsage(usm, dayStart, dayEnd, onlyPackage))
            mergeUsage(byPkg, queryDailyUsage(usm, dayStart, dayEnd, onlyPackage))
            mergeUsage(byPkg, queryEventDerivedUsage(usm, dayStart, dayEnd, onlyPackage))
        }

        // Only merge the live in-memory "today" usage for the actual current-day window.
        // Historical single-day queries (used by month/year/overall detail screens) must not pull in today's buffered value, otherwise today's usage gets duplicated into every historical day and long-range totals explode.
        if (dayStart == startOfTodayLocal()) {
            val internalToday = UsageStore.getUsageMsMapToday(ctx)
            if (onlyPackage != null) {
                val internal = internalToday[onlyPackage]?.coerceAtLeast(0L) ?: 0L
                if (internal > 0L) {
                    byPkg[onlyPackage] = maxOf(byPkg[onlyPackage] ?: 0L, internal)
                }
            } else {
                for ((pkg, ms) in internalToday) {
                    if (ms <= 0L) continue
                    byPkg[pkg] = maxOf(byPkg[pkg] ?: 0L, ms)
                }
            }
        }

        return byPkg
    }

    private fun mergeUsage(target: MutableMap<String, Long>, incoming: Map<String, Long>) {
        for ((pkg, ms) in incoming) {
            if (pkg.isBlank() || ms <= 0L) continue
            target[pkg] = maxOf(target[pkg] ?: 0L, ms)
        }
    }

    private fun queryAggregateUsage(
        usm: UsageStatsManager,
        from: Long,
        to: Long,
        onlyPackage: String?
    ): Map<String, Long> {
        if (isMainThread()) {
            return emptyMap()
        }
        val out = HashMap<String, Long>()
        val aggregated = try {
            usm.queryAndAggregateUsageStats(from, to)
        } catch (_: SecurityException) {
            emptyMap()
        } catch (_: Throwable) {
            emptyMap()
        }
        for ((pkg, st) in aggregated.orEmpty()) {
            if (onlyPackage != null && pkg != onlyPackage) continue
            val t = st.totalTimeInForeground.coerceAtLeast(0L)
            if (t > 0L) out[pkg] = maxOf(out[pkg] ?: 0L, t)
        }
        return out
    }

    private fun queryDailyUsage(
        usm: UsageStatsManager,
        from: Long,
        to: Long,
        onlyPackage: String?
    ): Map<String, Long> {
        if (isMainThread()) {
            return emptyMap()
        }
        val out = HashMap<String, Long>()
        val stats = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, to)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        for (st in stats.orEmpty()) {
            val pkg = st.packageName ?: continue
            if (onlyPackage != null && pkg != onlyPackage) continue
            val overlapsWindow = st.lastTimeStamp <= 0L || st.lastTimeStamp > from
            if (!overlapsWindow) continue
            val t = st.totalTimeInForeground.coerceAtLeast(0L)
            if (t > 0L) out[pkg] = maxOf(out[pkg] ?: 0L, t)
        }
        return out
    }

    private fun queryEventDerivedUsage(
        usm: UsageStatsManager,
        from: Long,
        to: Long,
        onlyPackage: String?,
        eventQueryFrom: Long = from
    ): Map<String, Long> {
        if (isMainThread() || to <= from) {
            return emptyMap()
        }
        val queryFrom = eventQueryFrom.coerceAtMost(from)
        val events = try {
            usm.queryEvents(queryFrom, to)
        } catch (_: SecurityException) {
            return emptyMap()
        } catch (_: Throwable) {
            return emptyMap()
        }

        val totals = HashMap<String, Long>()
        var foregroundPackage: String? = null
        var foregroundStart = queryFrom

        fun closeForeground(atMs: Long) {
            val pkg = foregroundPackage ?: return
            val startAt = maxOf(foregroundStart, from)
            val endAt = minOf(atMs, to)
            if (endAt > startAt && (onlyPackage == null || pkg == onlyPackage)) {
                totals[pkg] = (totals[pkg] ?: 0L) + (endAt - startAt)
            }
            foregroundPackage = null
        }

        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val eventAt = e.timeStamp.coerceIn(queryFrom, to)
            when (e.eventType) {
                EVENT_SCREEN_NON_INTERACTIVE,
                EVENT_KEYGUARD_SHOWN -> closeForeground(eventAt)

                EVENT_ACTIVITY_RESUMED -> {
                    val pkg = e.packageName ?: continue
                    if (foregroundPackage == pkg) {
                        // Activity changes inside one app can emit another RESUMED event.
                        // Keep the original package-level foreground start so those transitions do not split or inflate the app's usage.
                        continue
                    }
                    closeForeground(eventAt)
                    foregroundPackage = pkg
                    foregroundStart = eventAt
                }

                EVENT_ACTIVITY_PAUSED -> {
                    // Event type 2 is also MOVE_TO_BACKGROUND on pre-Android-10 devices.
                    // Only close when the paused/backgrounded package is still the package we currently consider foreground; later STOP events are intentionally ignored because they may belong to an older Activity after another Activity in the same package has already resumed.
                    if (e.packageName == foregroundPackage) {
                        closeForeground(eventAt)
                    }
                }
            }
        }

        if (foregroundPackage != null) {
            closeForeground(to)
        }
        return totals
    }

    private fun addRangeToHourlyBuckets(
        buckets: LongArray,
        dayStart: Long,
        startMs: Long,
        endMs: Long
    ) {
        val safeStart = startMs.coerceAtLeast(dayStart)
        val safeEnd = endMs.coerceAtLeast(safeStart)
        if (safeEnd <= safeStart) {
            return
        }

        val hourMs = TimeUnit.HOURS.toMillis(1)
        var cursor = safeStart
        while (cursor < safeEnd) {
            val hourIndex = (((cursor - dayStart) / hourMs).toInt()).coerceIn(0, 23)
            val hourEnd = minOf(dayStart + ((hourIndex + 1) * hourMs), safeEnd)
            if (hourEnd > cursor) buckets[hourIndex] += (hourEnd - cursor)
            cursor = hourEnd
        }
    }

    private fun getBucketedUsageByPackage(
        ctx: Context,
        from: Long,
        to: Long,
        onlyPackage: String?
    ): HashMap<String, Long> {
        if (isMainThread()) {
            return HashMap()
        }
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, to)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Throwable) {
            emptyList()
        }

        val byPkg = HashMap<String, Long>()
        for (st in stats.orEmpty()) {
            val pkg = st.packageName ?: continue
            if (onlyPackage != null && pkg != onlyPackage) continue
            val t = st.totalTimeInForeground.coerceAtLeast(0L)
            if (t > 0L) byPkg[pkg] = (byPkg[pkg] ?: 0L) + t
        }
        return byPkg
    }

    private fun isInstalled(ctx: Context, pkg: String): Boolean {
        return try {
            ctx.packageManager.getApplicationInfo(pkg, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
