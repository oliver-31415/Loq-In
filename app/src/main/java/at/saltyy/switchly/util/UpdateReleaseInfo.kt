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

package at.saltyy.switchly.util

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal enum class UpdateImpact {
    MAINTENANCE,
    FEATURE,
    MAJOR,
}

internal data class UpdateReleaseDetails(
    val targetVersion: String,
    val impact: UpdateImpact,
    val breaking: Boolean,
    val highlights: List<String>,
    val allChanges: List<String>,
)

/**
 * Optional release metadata for the Play update prompt.
 * The public Realm of Salt release timeline is an enhancement only: if the request fails, the regular Play update prompt still works.
 * A last-known-good copy is cached locally so a temporary website outage does not remove useful release context.
 */
internal object UpdateReleaseInfo {
    private const val RELEASE_FEED_URL = "https://release.saltyy.at/data/releases.json"
    private const val CACHE_PREFS = "switchly_release_feed_cache"
    private const val CACHE_KEY_JSON = "json"

    suspend fun resolve(
        context: Context,
        currentVersionName: String,
        targetVersionCode: Long,
    ): UpdateReleaseDetails? = withContext(Dispatchers.IO) {
        val raw = fetchFeed(context) ?: return@withContext null
        val entries = parseSwitchlyEntries(raw)
        if (entries.isEmpty()) return@withContext null

        val current = ParsedVersion.parse(currentVersionName) ?: return@withContext null
        val targetEntry = findTarget(entries, targetVersionCode) ?: return@withContext null
        val target = targetEntry.parsedVersion

        val relevant = entries
            .filter { it.parsedVersion > current && it.parsedVersion <= target }
            .groupBy { it.parsedVersion.coreKey }
            .values
            .mapNotNull(::preferPublicEntry)
            .sortedBy { it.parsedVersion }

        val allChanges = relevant
            .asReversed()
            .flatMap(::entryHighlights)
            .distinctBy(::normalizeLine)
            .take(24)

        val targetHighlights = entryHighlights(targetEntry)
        val highlights = (targetHighlights + allChanges)
            .distinctBy(::normalizeLine)
            .take(5)

        val explicitImpact = targetEntry.updateType?.let(::parseImpact)
        val impact = explicitImpact ?: when {
            target.major > current.major -> UpdateImpact.MAJOR
            target.minor > current.minor -> UpdateImpact.FEATURE
            else -> UpdateImpact.MAINTENANCE
        }

        UpdateReleaseDetails(
            targetVersion = target.display,
            impact = impact,
            breaking = targetEntry.breaking,
            highlights = highlights,
            allChanges = allChanges,
        )
    }

    private fun fetchFeed(context: Context): String? {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val remote = runCatching {
            val connection = (URL(RELEASE_FEED_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3_000
                readTimeout = 3_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Switchly-Android")
                useCaches = true
            }

            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }

        if (remote != null) {
            prefs.edit { putString(CACHE_KEY_JSON, remote) }
            return remote
        }

        return prefs.getString(CACHE_KEY_JSON, null)?.takeIf { it.isNotBlank() }
    }

    private fun parseSwitchlyEntries(raw: String): List<ReleaseEntry> {
        val root = raw.trim()
        val array = when {
            root.startsWith("[") -> JSONArray(root)
            root.startsWith("{") -> JSONObject(root).optJSONArray("releases") ?: JSONArray()
            else -> JSONArray()
        }

        val out = mutableListOf<ReleaseEntry>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            val type = item.optString("type").trim()
            val subtitle = item.optString("subtitle").trim()
            val versionRaw = item.optString("version").trim()
            val parsed = ParsedVersion.parse(versionRaw) ?: continue

            if (!title.equals("Switchly", ignoreCase = true)) continue
            if (type.isNotBlank() && !type.equals("app", ignoreCase = true)) continue
            if (subtitle.isNotBlank() && !subtitle.contains("Android", ignoreCase = true)) continue

            val explicitHighlights = mutableListOf<String>()
            item.optJSONArray("highlights")?.let { highlights ->
                for (j in 0 until highlights.length()) {
                    highlights.optString(j)
                        .trim()
                        .removePrefix("•")
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let(explicitHighlights::add)
                }
            }

            out += ReleaseEntry(
                version = versionRaw,
                parsedVersion = parsed,
                description = item.optString("desc").trim(),
                explicitHighlights = explicitHighlights,
                versionCode = if (item.has("versionCode")) item.optLong("versionCode", -1L).takeIf { it >= 0L } else null,
                updateType = item.optString("updateType").trim().takeIf { it.isNotBlank() },
                breaking = item.optBoolean("breaking", false),
            )
        }
        return out
    }

    private fun findTarget(
        entries: List<ReleaseEntry>,
        targetVersionCode: Long,
    ): ReleaseEntry? {
        val exactCode = entries
            .filter { it.versionCode == targetVersionCode }
            .maxWithOrNull(releasePreferenceComparator())
        if (exactCode != null) return exactCode

        val inferredCode = entries
            .filter { it.parsedVersion.versionCodeHint == targetVersionCode }
            .maxWithOrNull(releasePreferenceComparator())
        if (inferredCode != null) return inferredCode

        // Do not guess a different target release when the Play version code cannot be matched.
        // The generic Play prompt is safer than showing notes for a release the user is not actually being offered.
        return null
    }

    private fun releasePreferenceComparator(): Comparator<ReleaseEntry> =
        compareBy<ReleaseEntry> { it.parsedVersion.isPreRelease }
            .reversed()
            .thenBy { it.explicitHighlights.size }

    private fun preferPublicEntry(entries: List<ReleaseEntry>): ReleaseEntry? {
        if (entries.isEmpty()) return null
        return entries.firstOrNull { !it.parsedVersion.isPreRelease }
            ?: entries.maxByOrNull { it.explicitHighlights.size }
            ?: entries.first()
    }

    private fun entryHighlights(entry: ReleaseEntry): List<String> {
        if (entry.explicitHighlights.isNotEmpty()) {
            return entry.explicitHighlights.map(::cleanHighlight).filter { it.isNotBlank() }
        }
        if (entry.description.isBlank()) return emptyList()

        val sentenceParts = entry.description
            .replace("\n", " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map(::cleanHighlight)
            .filter { it.length >= 18 }

        if (sentenceParts.size >= 2) {
            return sentenceParts.take(5)
        }

        // Older release.saltyy.at entries are often intentionally written as one long sentence. 
        // Split those into readable feature clauses.
        return entry.description
            .replace("\n", " ")
            .split(Regex(",\\s+(?=(?:and\\s+)?(?:added|adds|improved|improves|fixed|fixes|new|better|clearer|updated|updates|reworked|redesigned|more|safer|stronger|expanded|expands|refined|refines))", RegexOption.IGNORE_CASE))
            .map(::cleanHighlight)
            .filter { it.length >= 18 }
            .take(5)
            .ifEmpty { listOf(cleanHighlight(entry.description)) }
    }

    private fun cleanHighlight(raw: String): String {
        val clean = raw
            .replace(Regex("\\s+"), " ")
            .removePrefix("•")
            .trim()
            .trimEnd('.', ',', ';', ':')
        return if (clean.length <= 155) clean else {
            val cut = clean.take(155)
            val safe = cut.substringBeforeLast(' ').takeIf { it.length >= 110 } ?: cut
            safe.trimEnd('.', ',', ';', ':') + "…"
        }
    }

    private fun normalizeLine(line: String): String = line
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun parseImpact(raw: String): UpdateImpact? = when (raw.trim().lowercase(Locale.US)) {
        "patch", "maintenance", "hotfix" -> UpdateImpact.MAINTENANCE
        "minor", "feature" -> UpdateImpact.FEATURE
        "major" -> UpdateImpact.MAJOR
        else -> null
    }

    private data class ReleaseEntry(
        val version: String,
        val parsedVersion: ParsedVersion,
        val description: String,
        val explicitHighlights: List<String>,
        val versionCode: Long?,
        val updateType: String?,
        val breaking: Boolean,
    )

    private data class ParsedVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val isPreRelease: Boolean,
    ) : Comparable<ParsedVersion> {
        val display: String = "$major.$minor.$patch"
        val coreKey: String = display
        val versionCodeHint: Long? = if (minor in 0..9 && patch in 0..9) {
            major * 100L + minor * 10L + patch
        } else {
            null
        }

        override fun compareTo(other: ParsedVersion): Int {
            compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
            compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
            compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
            return when {
                isPreRelease == other.isPreRelease -> 0
                isPreRelease -> -1
                else -> 1
            }
        }

        companion object {
            private val VERSION_REGEX = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$", RegexOption.IGNORE_CASE)

            fun parse(raw: String): ParsedVersion? {
                val value = raw.trim()
                val match = VERSION_REGEX.matchEntire(value) ?: return null
                val major = match.groupValues[1].toIntOrNull() ?: return null
                val minor = match.groupValues[2].toIntOrNull() ?: return null
                val patch = match.groupValues[3].toIntOrNull() ?: return null
                val suffixStart = value.indexOf('-')
                val isPreRelease = suffixStart >= 0
                return ParsedVersion(major, minor, patch, isPreRelease)
            }
        }
    }
}
