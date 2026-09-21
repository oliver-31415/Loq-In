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

package com.oliver.loqin.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingChangeQueueTest {

    private fun appSelectionChange(
        id: String,
        profile: String = "Default",
        allowMode: Boolean = false,
        remove: List<String> = listOf("com.example.app"),
        dueAt: Long = 1_000L,
    ) = PendingChange(
        id = id,
        type = PendingChangeType.APP_SELECTION,
        executeAtMs = dueAt,
        createdAtMs = 0L,
        data = JSONObject()
            .put("profile", profile)
            .put("allowMode", allowMode)
            .put("removePackages", org.json.JSONArray(remove)),
    )

    // ---------------------------------------------------------------------------------------------
    // Codec
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `codec round trips every change type`() {
        val changes = listOf(
            appSelectionChange("a"),
            PendingChange(
                "b",
                PendingChangeType.IN_APP_SELECTION,
                2_000L,
                1L,
                JSONObject().put("profile", "Default").put("baseKey", "shorts").put("selected", false),
            ),
            PendingChange(
                "c",
                PendingChangeType.WEBSITE_REMOVE,
                3_000L,
                1L,
                JSONObject().put("profile", "Default").put("rule", "example.com/path/*"),
            ),
            PendingChange(
                "d",
                PendingChangeType.WEBSITE_ENABLED,
                4_000L,
                1L,
                JSONObject().put("profile", "Default").put("rule", "example.com").put("enabled", false),
            ),
            PendingChange(
                "e",
                PendingChangeType.APP_LIMITS,
                5_000L,
                1L,
                JSONObject().put("profile", "Default").put("packageName", "com.example.app")
                    .put("fromTime", 10).put("toTime", 30),
            ),
        )

        val decoded = PendingChangeCodec.decode(PendingChangeCodec.encode(changes))

        assertEquals(changes.size, decoded.size)
        changes.zip(decoded).forEach { (expected, actual) ->
            assertEquals(expected.id, actual.id)
            assertEquals(expected.type, actual.type)
            assertEquals(expected.executeAtMs, actual.executeAtMs)
            assertEquals(expected.createdAtMs, actual.createdAtMs)
            assertEquals(expected.data.toString(), actual.data.toString())
        }
    }

    @Test
    fun `codec sorts by due time`() {
        val encoded = PendingChangeCodec.encode(
            listOf(
                appSelectionChange("late", dueAt = 5_000L),
                appSelectionChange("early", dueAt = 1_000L),
            ),
        )
        val decoded = PendingChangeCodec.decode(encoded)
        assertEquals(listOf("early", "late"), decoded.map { it.id })
    }

    @Test
    fun `codec skips malformed entries`() {
        val raw = """
            [
              {"id":"ok","type":"app_selection","executeAtMs":1000,"createdAtMs":1,"data":{}},
              {"type":"app_selection","executeAtMs":1000},
              {"id":"no-type","executeAtMs":1000},
              {"id":"no-due","type":"app_selection"},
              {"id":"zero-due","type":"app_selection","executeAtMs":0}
            ]
        """.trimIndent()

        val decoded = PendingChangeCodec.decode(raw)

        assertEquals(1, decoded.size)
        assertEquals("ok", decoded.first().id)
    }

    @Test
    fun `codec degrades to empty list for blank or invalid payloads`() {
        assertTrue(PendingChangeCodec.decode(null).isEmpty())
        assertTrue(PendingChangeCodec.decode("").isEmpty())
        assertTrue(PendingChangeCodec.decode("   ").isEmpty())
        assertTrue(PendingChangeCodec.decode("not json").isEmpty())
        assertTrue(PendingChangeCodec.decode("{}").isEmpty())
    }

    // ---------------------------------------------------------------------------------------------
    // Dedupe and list operations
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `dedupe keys are type specific`() {
        assertEquals(
            "app:Default:false:com.example.app",
            PendingChangeQueue.dedupeKey(appSelectionChange("a")),
        )
        assertEquals(
            "app:Default:true:com.example.app",
            PendingChangeQueue.dedupeKey(appSelectionChange("a", allowMode = true)),
        )
        assertEquals(
            "inapp:Default:shorts",
            PendingChangeQueue.dedupeKey(
                PendingChange(
                    "b",
                    PendingChangeType.IN_APP_SELECTION,
                    1L,
                    1L,
                    JSONObject().put("profile", "Default").put("baseKey", "shorts"),
                ),
            ),
        )
        assertEquals(
            "web-remove:Default:example.com",
            PendingChangeQueue.dedupeKey(
                PendingChange(
                    "c",
                    PendingChangeType.WEBSITE_REMOVE,
                    1L,
                    1L,
                    JSONObject().put("profile", "Default").put("rule", "example.com"),
                ),
            ),
        )
        assertEquals(
            "web-enabled:Default:example.com",
            PendingChangeQueue.dedupeKey(
                PendingChange(
                    "d",
                    PendingChangeType.WEBSITE_ENABLED,
                    1L,
                    1L,
                    JSONObject().put("profile", "Default").put("rule", "example.com"),
                ),
            ),
        )
        assertEquals(
            "auto-block:Default",
            PendingChangeQueue.dedupeKey(
                PendingChange(
                    "f",
                    PendingChangeType.AUTO_BLOCK_NEW_APPS,
                    1L,
                    1L,
                    JSONObject().put("profile", "Default").put("enabled", false),
                ),
            ),
        )
        assertEquals(
            "clear-app-data:Default:com.example.app",
            PendingChangeQueue.dedupeKey(
                PendingChange(
                    "g",
                    PendingChangeType.CLEAR_APP_DATA,
                    1L,
                    1L,
                    JSONObject().put("profile", "Default").put("packages", org.json.JSONArray().put("com.example.app")),
                ),
            ),
        )
        assertEquals(
            "app-limits:Default:com.example.app",
            PendingChangeQueue.dedupeKey(
                PendingChange(
                    "e",
                    PendingChangeType.APP_LIMITS,
                    1L,
                    1L,
                    JSONObject().put("profile", "Default").put("packageName", "com.example.app"),
                ),
            ),
        )
    }

    @Test
    fun `app selection entries for different packages coexist`() {
        val first = appSelectionChange("first", remove = listOf("com.example.one"), dueAt = 1_000L)
        val second = appSelectionChange("second", remove = listOf("com.example.two"), dueAt = 2_000L)

        assertFalse(PendingChangeQueue.dedupeKey(first) == PendingChangeQueue.dedupeKey(second))

        val result = PendingChangeQueue.upsert(listOf(first), second)

        assertEquals(2, result.size)
        assertEquals(listOf("first", "second"), result.map { it.id })
    }

    @Test
    fun `app selection entries for the same packages still dedupe`() {
        val first = appSelectionChange("first", remove = listOf("com.example.one"), dueAt = 1_000L)
        val second = appSelectionChange("second", remove = listOf("com.example.one"), dueAt = 5_000L)

        assertEquals(PendingChangeQueue.dedupeKey(first), PendingChangeQueue.dedupeKey(second))

        val result = PendingChangeQueue.upsert(listOf(first), second)

        assertEquals(1, result.size)
        assertEquals("second", result.first().id)
        assertEquals(1_000L, result.first().executeAtMs)
    }

    @Test
    fun `clear app data entries for different packages coexist`() {
        fun clear(id: String, packages: List<String>) = PendingChange(
            id,
            PendingChangeType.CLEAR_APP_DATA,
            1L,
            1L,
            JSONObject().put("profile", "Default").put("packages", org.json.JSONArray(packages)),
        )
        assertFalse(
            PendingChangeQueue.dedupeKey(clear("a", listOf("com.example.one"))) ==
                PendingChangeQueue.dedupeKey(clear("b", listOf("com.example.two"))),
        )
    }

    @Test
    fun `prune app selection removes a package from the pending entry`() {
        val entry = appSelectionChange("a", remove = listOf("com.example.one", "com.example.two"))

        val result = PendingChangeQueue.pruneAppSelection(
            existing = listOf(entry),
            profile = "Default",
            allowMode = false,
            packages = setOf("com.example.one"),
        )

        assertEquals(1, result.size)
        val remaining = result.first().data.optJSONArray("removePackages")
        assertEquals(listOf("com.example.two"), (0 until remaining!!.length()).map { remaining.optString(it) })
    }

    @Test
    fun `prune app selection drops the entry when all packages are stricter again`() {
        val entry = appSelectionChange("a", remove = listOf("com.example.one"))

        val result = PendingChangeQueue.pruneAppSelection(
            existing = listOf(entry),
            profile = "Default",
            allowMode = false,
            packages = setOf("com.example.one"),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `prune app selection leaves other profiles and modes alone`() {
        val entry = appSelectionChange("a", profile = "Second", remove = listOf("com.example.one"))

        val result = PendingChangeQueue.pruneAppSelection(
            existing = listOf(entry),
            profile = "Default",
            allowMode = false,
            packages = setOf("com.example.one"),
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `prune in-app and website enabled and auto-block entries`() {
        val inApp = PendingChange(
            "i", PendingChangeType.IN_APP_SELECTION, 1L, 1L,
            JSONObject().put("profile", "Default").put("baseKey", "shorts"),
        )
        val website = PendingChange(
            "w", PendingChangeType.WEBSITE_ENABLED, 1L, 1L,
            JSONObject().put("profile", "Default").put("rule", "example.com"),
        )
        val autoBlock = PendingChange(
            "b", PendingChangeType.AUTO_BLOCK_NEW_APPS, 1L, 1L,
            JSONObject().put("profile", "Default").put("enabled", false),
        )
        val all = listOf(inApp, website, autoBlock)

        assertTrue(PendingChangeQueue.pruneInAppSelections(all, "Default", setOf("shorts")).none { it.id == "i" })
        assertTrue(PendingChangeQueue.pruneWebsiteEnabled(all, "Default", setOf("example.com")).none { it.id == "w" })
        assertTrue(PendingChangeQueue.pruneAutoBlock(all, "Default").none { it.id == "b" })
    }

    @Test
    fun `upsert replaces the same target but keeps the original timer`() {
        val first = appSelectionChange("first", dueAt = 1_000L)
        val replacement = appSelectionChange("second", dueAt = 5_000L)

        val result = PendingChangeQueue.upsert(listOf(first), replacement)

        assertEquals(1, result.size)
        assertEquals("second", result.first().id)
        assertEquals(1_000L, result.first().executeAtMs)
        assertEquals(0L, result.first().createdAtMs)
    }

    @Test
    fun `upsert keeps different targets and sorts by due time`() {
        val a = appSelectionChange("a", profile = "Default", dueAt = 5_000L)
        val b = appSelectionChange("b", profile = "Second", dueAt = 1_000L)

        val result = PendingChangeQueue.upsert(listOf(a), b)

        assertEquals(listOf("b", "a"), result.map { it.id })
    }

    @Test
    fun `removeById removes only the matching entry`() {
        val a = appSelectionChange("a")
        val b = appSelectionChange("b", profile = "Second")

        val result = PendingChangeQueue.removeById(listOf(a, b), "a")

        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun `partitionDue splits due and remaining entries`() {
        val due = appSelectionChange("due", dueAt = 1_000L)
        val later = appSelectionChange("later", profile = "Second", dueAt = 5_000L)

        val (applied, remaining) = PendingChangeQueue.partitionDue(listOf(due, later), nowMs = 2_000L)

        assertEquals(listOf("due"), applied.map { it.id })
        assertEquals(listOf("later"), remaining.map { it.id })
    }

    @Test
    fun `partitionDue treats the due time as inclusive`() {
        val exact = appSelectionChange("exact", dueAt = 2_000L)
        val (applied, remaining) = PendingChangeQueue.partitionDue(listOf(exact), nowMs = 2_000L)
        assertEquals(1, applied.size)
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun `earliest due time is null for an empty queue`() {
        assertNull(PendingChangeQueue.earliestDueAtMs(emptyList()))
        assertEquals(1_000L, PendingChangeQueue.earliestDueAtMs(listOf(appSelectionChange("a", dueAt = 1_000L))))
    }

    @Test
    fun `newChange computes the due time from the delay`() {
        val change = PendingChangeQueue.newChange(
            id = "id",
            type = PendingChangeType.WEBSITE_REMOVE,
            nowMs = 1_000_000L,
            delayMinutes = 15,
            data = JSONObject().put("rule", "example.com"),
        )

        assertEquals(1_000_000L + 15 * 60_000L, change.executeAtMs)
        assertEquals(1_000_000L, change.createdAtMs)
    }

    @Test
    fun `newChange never queues with a zero delay`() {
        val change = PendingChangeQueue.newChange("id", PendingChangeType.WEBSITE_REMOVE, 0L, 0, JSONObject())
        assertEquals(60_000L, change.executeAtMs)
    }

    // ---------------------------------------------------------------------------------------------
    // Apply-time guards
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `numeric guard only applies when the current value still matches`() {
        assertTrue(PendingChangeQueue.shouldApplyNumeric(currentValue = 10, fromValue = 10))
        assertFalse(PendingChangeQueue.shouldApplyNumeric(currentValue = 5, fromValue = 10))
        assertFalse(PendingChangeQueue.shouldApplyNumeric(currentValue = 0, fromValue = 10))
    }

    @Test
    fun `reset mode guard only applies when the current value still matches`() {
        assertTrue(PendingChangeQueue.shouldApplyResetMode("day", "day"))
        assertFalse(PendingChangeQueue.shouldApplyResetMode("session", "day"))
    }
}
