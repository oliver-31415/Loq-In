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

package com.oliver.loqin.feature.usage

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.oliver.loqin.data.prefs.AppLaunchCountStore
import com.oliver.loqin.data.prefs.BlockAttemptStore
import com.oliver.loqin.data.prefs.UsageStore
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Reads the local stores for the last [UsageCsvExport.DAYS] days and writes the CSV. Call off the main thread. */
object UsageCsvExporter {

    /** Returns false if the document could not be opened or written. */
    fun writeLastDays(context: Context, uri: Uri): Boolean {
        val pm = context.packageManager
        val labels = HashMap<String, String>()
        fun labelFor(packageName: String): String = labels.getOrPut(packageName) {
            try {
                pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val day = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(UsageCsvExport.DAYS - 1))
        }

        return try {
            val writer = context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)
                ?: return false
            writer.use { out ->
                out.write(UsageCsvExport.HEADER)
                out.write("\n")
                repeat(UsageCsvExport.DAYS) {
                    val startMs = day.timeInMillis
                    val nextDay = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                    val endMs = nextDay.timeInMillis - 1L
                    val dateLabel = dateFormat.format(day.time)
                    val ymd = day.get(Calendar.YEAR) * 10000 +
                        (day.get(Calendar.MONTH) + 1) * 100 +
                        day.get(Calendar.DAY_OF_MONTH)

                    val usageMs = UsageStore.getUsageMsMapForDay(context, ymd)
                    val opens = AppLaunchCountStore.getMapForDateRange(context, startMs, endMs)
                    val blocks = BlockAttemptStore.getMapForDateRange(context, startMs, endMs)

                    for (packageName in (usageMs.keys + opens.keys + blocks.keys).sorted()) {
                        val ms = usageMs[packageName] ?: 0L
                        val openCount = opens[packageName] ?: 0
                        val blockCount = blocks[packageName] ?: 0
                        if (!UsageCsvExport.hasData(ms, openCount, blockCount)) continue
                        // Same app set as the insights screen (no System UI, launchers, hidden apps).
                        if (UsageInsightsAppFilter.shouldHide(context, packageName)) continue
                        out.write(
                            UsageCsvExport.row(
                                date = dateLabel,
                                packageName = packageName,
                                appName = labelFor(packageName),
                                screenTimeMs = ms,
                                opens = openCount,
                                blocks = blockCount
                            )
                        )
                        out.write("\n")
                    }
                    day.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            true
        } catch (e: IOException) {
            false
        }
    }
}
