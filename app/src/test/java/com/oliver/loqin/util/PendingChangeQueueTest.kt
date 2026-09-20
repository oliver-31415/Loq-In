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
            .put("removePackages", remove),
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
        assertEquals("app:Default:false", PendingChangeQueue.dedupeKey(appSelectionChange("a")))
        assertEquals("app:Default:true", PendingChangeQueue.dedupeKey(appSelectionChange("a", allowMode = true)))
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
    fun `upsert replaces the same target and restarts its timer`() {
        val first = appSelectionChange("first", dueAt = 1_000L)
        val replacement = appSelectionChange("second", dueAt = 2_000L)

        val result = PendingChangeQueue.upsert(listOf(first), replacement)

        assertEquals(1, result.size)
        assertEquals("second", result.first().id)
        assertEquals(2_000L, result.first().executeAtMs)
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
