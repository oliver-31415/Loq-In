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

import com.oliver.loqin.data.prefs.UsageLimitResetStore
import com.oliver.loqin.util.ProtectionChangePolicy.Decision
import com.oliver.loqin.util.ProtectionChangePolicy.Direction
import com.oliver.loqin.util.ProtectionChangePolicy.SelectionAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionChangePolicyTest {

    // ---------------------------------------------------------------------------------------------
    // Delay
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `delay validation accepts presets and custom range`() {
        assertEquals(listOf(0, 15, 60, 360, 1440), ProtectionChangePolicy.delayOptionsMinutes)
        assertTrue(ProtectionChangePolicy.isPresetDelayMinutes(0))
        assertTrue(ProtectionChangePolicy.isPresetDelayMinutes(1440))
        assertFalse(ProtectionChangePolicy.isPresetDelayMinutes(1))
        assertTrue(ProtectionChangePolicy.isValidDelayMinutes(0))
        assertTrue(ProtectionChangePolicy.isValidDelayMinutes(1))
        assertTrue(ProtectionChangePolicy.isValidDelayMinutes(10_080))
        assertFalse(ProtectionChangePolicy.isValidDelayMinutes(-1))
        assertFalse(ProtectionChangePolicy.isValidDelayMinutes(10_081))
    }

    @Test
    fun `delay can be shortened only while protection is off`() {
        assertTrue(ProtectionChangePolicy.canSetDelayMinutes(locked = false, currentMinutes = 60, requestedMinutes = 0))
        assertTrue(ProtectionChangePolicy.canSetDelayMinutes(locked = false, currentMinutes = 0, requestedMinutes = 1440))
        assertTrue(ProtectionChangePolicy.canSetDelayMinutes(locked = true, currentMinutes = 60, requestedMinutes = 60))
        assertTrue(ProtectionChangePolicy.canSetDelayMinutes(locked = true, currentMinutes = 60, requestedMinutes = 360))
        assertFalse(ProtectionChangePolicy.canSetDelayMinutes(locked = true, currentMinutes = 60, requestedMinutes = 15))
        assertFalse(ProtectionChangePolicy.canSetDelayMinutes(locked = true, currentMinutes = 60, requestedMinutes = 10_081))
    }

    // ---------------------------------------------------------------------------------------------
    // Decision matrix
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `decision matrix matches upstream`() {
        assertEquals(
            Decision.APPLY_NOW,
            ProtectionChangePolicy.decision(locked = false, delayMinutes = 0, direction = Direction.WEAKER),
        )
        assertEquals(
            Decision.APPLY_NOW,
            ProtectionChangePolicy.decision(locked = true, delayMinutes = 0, direction = Direction.STRICTER),
        )
        assertEquals(
            Decision.APPLY_NOW,
            ProtectionChangePolicy.decision(locked = true, delayMinutes = 60, direction = Direction.NEUTRAL),
        )
        assertEquals(
            Decision.DENY,
            ProtectionChangePolicy.decision(locked = true, delayMinutes = 0, direction = Direction.WEAKER),
        )
        assertEquals(
            Decision.QUEUE_DELAYED,
            ProtectionChangePolicy.decision(locked = true, delayMinutes = 15, direction = Direction.WEAKER),
        )
        assertEquals(
            Decision.DENY,
            ProtectionChangePolicy.decision(locked = true, delayMinutes = 0, direction = Direction.PROTECTED_STRUCTURAL),
        )
        assertEquals(
            Decision.DENY,
            ProtectionChangePolicy.decision(locked = true, delayMinutes = 1440, direction = Direction.PROTECTED_STRUCTURAL),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // App selection
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `block mode selection directions`() {
        assertEquals(
            Direction.STRICTER,
            ProtectionChangePolicy.appSelectionDirection(false, setOf("a"), setOf("a", "b")),
        )
        assertEquals(
            Direction.WEAKER,
            ProtectionChangePolicy.appSelectionDirection(false, setOf("a", "b"), setOf("a")),
        )
        assertEquals(
            Direction.NEUTRAL,
            ProtectionChangePolicy.appSelectionDirection(false, setOf("a"), setOf("a")),
        )
        assertEquals(
            Direction.WEAKER,
            ProtectionChangePolicy.appSelectionDirection(false, setOf("a", "b"), setOf("a", "c")),
        )
    }

    @Test
    fun `allow mode selection directions`() {
        assertEquals(
            Direction.STRICTER,
            ProtectionChangePolicy.appSelectionDirection(true, setOf("a", "b"), setOf("a")),
        )
        assertEquals(
            Direction.WEAKER,
            ProtectionChangePolicy.appSelectionDirection(true, setOf("a"), setOf("a", "b")),
        )
        assertEquals(
            Direction.NEUTRAL,
            ProtectionChangePolicy.appSelectionDirection(true, setOf("a"), setOf("a")),
        )
    }

    @Test
    fun `block mode mixed selection applies additions and queues removals`() {
        val plan = ProtectionChangePolicy.planAppSelection(
            allowMode = false,
            original = setOf("a", "b"),
            requested = setOf("a", "c"),
        )
        assertEquals(Direction.WEAKER, plan.direction)
        assertEquals(setOf("c"), plan.applyNow)
        assertEquals(setOf("b"), plan.queued)
        assertEquals(SelectionAction.REMOVE, plan.action)
        assertTrue(plan.hasImmediate)
        assertTrue(plan.hasQueued)
    }

    @Test
    fun `block mode stricter selection has nothing to queue`() {
        val plan = ProtectionChangePolicy.planAppSelection(
            allowMode = false,
            original = setOf("a"),
            requested = setOf("a", "b"),
        )
        assertEquals(Direction.STRICTER, plan.direction)
        assertEquals(setOf("b"), plan.applyNow)
        assertTrue(plan.queued.isEmpty())
        assertNull(plan.action)
    }

    @Test
    fun `allow mode mixed selection applies removals and queues additions`() {
        val plan = ProtectionChangePolicy.planAppSelection(
            allowMode = true,
            original = setOf("a", "b"),
            requested = setOf("a", "c"),
        )
        assertEquals(Direction.WEAKER, plan.direction)
        assertEquals(setOf("b"), plan.applyNow)
        assertEquals(setOf("c"), plan.queued)
        assertEquals(SelectionAction.ADD, plan.action)
    }

    @Test
    fun `unchanged selection is neutral and empty`() {
        val plan = ProtectionChangePolicy.planAppSelection(
            allowMode = false,
            original = setOf("a"),
            requested = setOf("a"),
        )
        assertEquals(Direction.NEUTRAL, plan.direction)
        assertFalse(plan.hasImmediate)
        assertFalse(plan.hasQueued)
        assertNull(plan.action)
    }

    // ---------------------------------------------------------------------------------------------
    // In-app and website rules
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `in-app rule directions`() {
        assertEquals(Direction.STRICTER, ProtectionChangePolicy.inAppDirection(false, false, true))
        assertEquals(Direction.WEAKER, ProtectionChangePolicy.inAppDirection(false, true, false))
        assertEquals(Direction.STRICTER, ProtectionChangePolicy.inAppDirection(true, true, false))
        assertEquals(Direction.WEAKER, ProtectionChangePolicy.inAppDirection(true, false, true))
        assertEquals(Direction.NEUTRAL, ProtectionChangePolicy.inAppDirection(false, true, true))
    }

    @Test
    fun `website removal and enable directions`() {
        assertEquals(Direction.WEAKER, ProtectionChangePolicy.websiteRemovalDirection(allowMode = false))
        assertEquals(Direction.STRICTER, ProtectionChangePolicy.websiteRemovalDirection(allowMode = true))
        assertEquals(Direction.STRICTER, ProtectionChangePolicy.websiteEnabledDirection(false, requestedEnabled = true))
        assertEquals(Direction.WEAKER, ProtectionChangePolicy.websiteEnabledDirection(false, requestedEnabled = false))
        assertEquals(Direction.WEAKER, ProtectionChangePolicy.websiteEnabledDirection(true, requestedEnabled = true))
        assertEquals(Direction.STRICTER, ProtectionChangePolicy.websiteEnabledDirection(true, requestedEnabled = false))
    }

    // ---------------------------------------------------------------------------------------------
    // Limits
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `numeric limit directions`() {
        assertEquals(Direction.STRICTER, ProtectionChangePolicy.numericLimitDirection(0, 10))
        assertEquals(Direction.WEAKER, ProtectionChangePolicy.numericLimitDirection(10, 0))
        assertEquals(Direction.STRICTER, ProtectionChangePolicy.numericLimitDirection(10, 5))
        assertEquals(Direction.WEAKER, ProtectionChangePolicy.numericLimitDirection(5, 10))
        assertEquals(Direction.NEUTRAL, ProtectionChangePolicy.numericLimitDirection(10, 10))
        assertEquals(Direction.NEUTRAL, ProtectionChangePolicy.numericLimitDirection(-5, -5))
        assertEquals(Direction.NEUTRAL, ProtectionChangePolicy.numericLimitDirection(-5, 0))
        assertEquals(Direction.STRICTER, ProtectionChangePolicy.numericLimitDirection(-5, 5))
    }

    @Test
    fun `reset mode directions`() {
        assertEquals(
            Direction.NEUTRAL,
            ProtectionChangePolicy.resetModeDirection(
                UsageLimitResetStore.MODE_DAY,
                UsageLimitResetStore.MODE_SESSION,
                timeLimitExists = false,
            ),
        )
        assertEquals(
            Direction.NEUTRAL,
            ProtectionChangePolicy.resetModeDirection(
                UsageLimitResetStore.MODE_DAY,
                UsageLimitResetStore.MODE_DAY,
                timeLimitExists = true,
            ),
        )
        assertEquals(
            Direction.STRICTER,
            ProtectionChangePolicy.resetModeDirection(
                UsageLimitResetStore.MODE_SESSION,
                UsageLimitResetStore.MODE_DAY,
                timeLimitExists = true,
            ),
        )
        assertEquals(
            Direction.WEAKER,
            ProtectionChangePolicy.resetModeDirection(
                UsageLimitResetStore.MODE_DAY,
                UsageLimitResetStore.MODE_SESSION,
                timeLimitExists = true,
            ),
        )
    }

    @Test
    fun `limits with no change are neutral`() {
        val plan = ProtectionChangePolicy.planAppLimits(30, 30, 5, 5, 10, 10, "day", "day")
        assertEquals(Direction.NEUTRAL, plan.direction)
        assertFalse(plan.hasImmediate)
        assertFalse(plan.hasQueued)
    }

    @Test
    fun `lowering a limit applies immediately`() {
        val plan = ProtectionChangePolicy.planAppLimits(30, 20, 0, 0, 0, 0, "day", "day")
        assertEquals(Direction.STRICTER, plan.direction)
        assertEquals(20, plan.applyNowTime)
        assertTrue(plan.hasImmediate)
        assertFalse(plan.hasQueued)
    }

    @Test
    fun `raising a limit is queued with its previous value`() {
        val plan = ProtectionChangePolicy.planAppLimits(10, 30, 0, 0, 0, 0, "day", "day")
        assertEquals(Direction.WEAKER, plan.direction)
        assertEquals(ProtectionChangePolicy.LimitChange(10, 30), plan.queuedTime)
        assertFalse(plan.hasImmediate)
        assertTrue(plan.hasQueued)
    }

    @Test
    fun `removing a limit is queued with its previous value`() {
        val plan = ProtectionChangePolicy.planAppLimits(10, 0, 0, 0, 0, 0, "day", "day")
        assertEquals(Direction.WEAKER, plan.direction)
        assertEquals(ProtectionChangePolicy.LimitChange(10, 0), plan.queuedTime)
    }

    @Test
    fun `mixed limits apply the stricter component and queue the weaker one`() {
        val plan = ProtectionChangePolicy.planAppLimits(
            currentTimeMinutes = 10,
            requestedTimeMinutes = 30,
            currentAttempts = 5,
            requestedAttempts = 2,
            currentPerVisitMinutes = 0,
            requestedPerVisitMinutes = 0,
            currentResetMode = "day",
            requestedResetMode = "day",
        )
        assertEquals(Direction.WEAKER, plan.direction)
        assertEquals(2, plan.applyNowAttempts)
        assertEquals(ProtectionChangePolicy.LimitChange(10, 30), plan.queuedTime)
        assertTrue(plan.hasImmediate)
        assertTrue(plan.hasQueued)
    }

    @Test
    fun `stricter reset mode applies immediately`() {
        val plan = ProtectionChangePolicy.planAppLimits(
            currentTimeMinutes = 30,
            requestedTimeMinutes = 30,
            currentAttempts = 0,
            requestedAttempts = 0,
            currentPerVisitMinutes = 0,
            requestedPerVisitMinutes = 0,
            currentResetMode = UsageLimitResetStore.MODE_SESSION,
            requestedResetMode = UsageLimitResetStore.MODE_DAY,
        )
        assertEquals(Direction.STRICTER, plan.direction)
        assertEquals(UsageLimitResetStore.MODE_DAY, plan.applyNowResetMode)
        assertFalse(plan.hasQueued)
    }

    @Test
    fun `weaker reset mode is queued when a time limit exists`() {
        val plan = ProtectionChangePolicy.planAppLimits(
            currentTimeMinutes = 30,
            requestedTimeMinutes = 30,
            currentAttempts = 0,
            requestedAttempts = 0,
            currentPerVisitMinutes = 0,
            requestedPerVisitMinutes = 0,
            currentResetMode = UsageLimitResetStore.MODE_DAY,
            requestedResetMode = UsageLimitResetStore.MODE_SESSION,
        )
        assertEquals(Direction.WEAKER, plan.direction)
        assertEquals(
            ProtectionChangePolicy.ResetModeChange(
                UsageLimitResetStore.MODE_DAY,
                UsageLimitResetStore.MODE_SESSION,
            ),
            plan.queuedResetMode,
        )
    }

    @Test
    fun `adding a time limit applies reset mode immediately`() {
        val plan = ProtectionChangePolicy.planAppLimits(
            currentTimeMinutes = 0,
            requestedTimeMinutes = 30,
            currentAttempts = 0,
            requestedAttempts = 0,
            currentPerVisitMinutes = 0,
            requestedPerVisitMinutes = 0,
            currentResetMode = UsageLimitResetStore.MODE_DAY,
            requestedResetMode = UsageLimitResetStore.MODE_SESSION,
        )
        assertEquals(Direction.STRICTER, plan.direction)
        assertEquals(30, plan.applyNowTime)
        assertEquals(UsageLimitResetStore.MODE_SESSION, plan.applyNowResetMode)
        assertFalse(plan.hasQueued)
    }

    @Test
    fun `stricter time with weaker reset splits both ways`() {
        val plan = ProtectionChangePolicy.planAppLimits(
            currentTimeMinutes = 30,
            requestedTimeMinutes = 20,
            currentAttempts = 0,
            requestedAttempts = 0,
            currentPerVisitMinutes = 0,
            requestedPerVisitMinutes = 0,
            currentResetMode = UsageLimitResetStore.MODE_DAY,
            requestedResetMode = UsageLimitResetStore.MODE_SESSION,
        )
        assertEquals(Direction.WEAKER, plan.direction)
        assertEquals(20, plan.applyNowTime)
        assertNull(plan.applyNowResetMode)
        assertEquals(
            ProtectionChangePolicy.ResetModeChange(
                UsageLimitResetStore.MODE_DAY,
                UsageLimitResetStore.MODE_SESSION,
            ),
            plan.queuedResetMode,
        )
    }

    @Test
    fun `per-visit limits follow the same rules`() {
        val stricter = ProtectionChangePolicy.planAppLimits(0, 0, 0, 0, 10, 5, "day", "day")
        assertEquals(Direction.STRICTER, stricter.direction)
        assertEquals(5, stricter.applyNowPerVisit)

        val weaker = ProtectionChangePolicy.planAppLimits(0, 0, 0, 0, 5, 15, "day", "day")
        assertEquals(Direction.WEAKER, weaker.direction)
        assertEquals(ProtectionChangePolicy.LimitChange(5, 15), weaker.queuedPerVisit)
    }
}
