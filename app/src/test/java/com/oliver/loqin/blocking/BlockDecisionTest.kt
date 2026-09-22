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

package com.oliver.loqin.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDecisionTest {

    private fun decide(
        pkg: String = "com.example.app",
        blocked: Set<String> = setOf(pkg),
        limitMinutes: Int = 0,
        attemptLimit: Int = 0,
        opensExceeded: Boolean = false,
        usageMs: Long = 0L,
        perVisitLimitMinutes: Int = 0,
        perVisitUsageMs: Long = 0L,
        force: Boolean = false,
        allowMode: Boolean = false,
        allowModeListed: Boolean = false,
        essentialAllowed: Boolean = false,
    ): AppBlockDecision = resolveAppBlockDecision(
        pkg = pkg,
        blockedPackages = blocked,
        limitMinutes = limitMinutes,
        attemptLimit = attemptLimit,
        opensExceeded = opensExceeded,
        effectiveUsageMsToday = usageMs,
        perVisitLimitMinutes = perVisitLimitMinutes,
        perVisitUsageMs = perVisitUsageMs,
        lockActive = false,
        highRisk = false,
        force = force,
        allowMode = allowMode,
        essentialAllowed = essentialAllowed,
        allowModeListed = allowModeListed,
    )

    @Test
    fun `unselected app without limits is allowed`() {
        val decision = decide(blocked = emptySet())
        assertFalse(decision.shouldBlock)
    }

    @Test
    fun `selected app without limits is hard blocked immediately`() {
        val decision = decide()
        assertTrue(decision.shouldBlock)
        assertTrue(decision.immediate)
    }

    @Test
    fun `daily time limit blocks only once reached`() {
        assertFalse(decide(limitMinutes = 30, usageMs = 29 * 60_000L).shouldBlock)
        val reached = decide(limitMinutes = 30, usageMs = 30 * 60_000L)
        assertTrue(reached.shouldBlock)
        assertTrue(reached.immediate)
    }

    @Test
    fun `daily limit alone does not hard block its app`() {
        // An app that only has a limit must stay usable until the limit is reached.
        assertFalse(decide(limitMinutes = 30, usageMs = 0L).shouldBlock)
    }

    @Test
    fun `attempt limit blocks once opens exceeded`() {
        assertFalse(decide(attemptLimit = 4, opensExceeded = false).shouldBlock)
        val exceeded = decide(attemptLimit = 4, opensExceeded = true)
        assertTrue(exceeded.shouldBlock)
        assertTrue(exceeded.immediate)
    }

    @Test
    fun `per visit limit blocks only once the visit is exhausted`() {
        assertFalse(
            decide(perVisitLimitMinutes = 5, perVisitUsageMs = 4 * 60_000L + 59_000L).shouldBlock
        )
        val reached = decide(perVisitLimitMinutes = 5, perVisitUsageMs = 5 * 60_000L)
        assertTrue(reached.shouldBlock)
        assertTrue(reached.immediate)
    }

    @Test
    fun `per visit limit alone does not hard block its app`() {
        // The visit limit must not turn a selected app into an immediate hard block.
        val below = decide(perVisitLimitMinutes = 5, perVisitUsageMs = 1_000L)
        assertFalse(below.shouldBlock)
        val justReached = decide(perVisitLimitMinutes = 5, perVisitUsageMs = 5 * 60_000L)
        assertTrue(justReached.shouldBlock)
    }

    @Test
    fun `per visit and daily limits combine`() {
        // Daily limit reached while the visit still has time left.
        val dailyReached = decide(
            limitMinutes = 30,
            usageMs = 30 * 60_000L,
            perVisitLimitMinutes = 10,
            perVisitUsageMs = 60_000L,
        )
        assertTrue(dailyReached.shouldBlock)
    }

    @Test
    fun `force does not block an unlimited unselected app`() {
        val decision = decide(blocked = emptySet(), force = true)
        assertFalse(decision.shouldBlock)
    }

    @Test
    fun `allow mode blocks listed unselected app without limits`() {
        val decision = decide(
            blocked = emptySet(),
            force = true,
            allowMode = true,
            allowModeListed = true,
        )
        assertTrue(decision.shouldBlock)
        assertTrue(decision.immediate)
    }

    @Test
    fun `allow mode keep listed selected app allowed`() {
        val decision = decide(
            blocked = setOf("com.example.app"),
            allowMode = true,
            allowModeListed = true,
        )
        assertFalse(decision.shouldBlock)
    }
}
