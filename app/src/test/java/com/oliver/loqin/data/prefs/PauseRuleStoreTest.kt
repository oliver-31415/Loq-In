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

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseRuleStoreTest {

    @Test
    fun `first open waits the base time`() {
        assertEquals(10, PauseRuleStore.waitSeconds(10, 5, 0))
    }

    @Test
    fun `each earlier open today adds a step`() {
        assertEquals(25, PauseRuleStore.waitSeconds(10, 5, 3))
    }

    @Test
    fun `wait is capped`() {
        assertEquals(PauseRuleStore.MAX_SECONDS, PauseRuleStore.waitSeconds(10, 5, 1_000))
        assertEquals(PauseRuleStore.MAX_SECONDS, PauseRuleStore.waitSeconds(10, Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun `no step keeps a fixed wait`() {
        assertEquals(10, PauseRuleStore.waitSeconds(10, 0, 50))
    }

    @Test
    fun `invalid inputs are clamped`() {
        assertEquals(1, PauseRuleStore.waitSeconds(0, 0, -3))
    }

    @Test
    fun `weekly outcome needs a few pauses first`() {
        assertEquals(false, PauseRuleStore.shouldShowWeeklyOutcome(1, 1))
        assertEquals(true, PauseRuleStore.shouldShowWeeklyOutcome(0, 3))
        assertEquals(true, PauseRuleStore.shouldShowWeeklyOutcome(2, 1))
    }
}
