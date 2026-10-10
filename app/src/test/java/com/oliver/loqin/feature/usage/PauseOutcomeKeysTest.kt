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

import com.oliver.loqin.data.prefs.PauseRuleStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseOutcomeKeysTest {

    @Test
    fun overallKeysKeepExistingFormat() {
        assertEquals("pause_outcome_left_20261011", PauseRuleStore.outcomeKey(PauseRuleStore.Outcome.LEFT, 20261011))
        assertEquals(
            "pause_outcome_continued_20261011",
            PauseRuleStore.outcomeKey(PauseRuleStore.Outcome.CONTINUED, 20261011),
        )
    }

    @Test
    fun appKeysAppendPackageAfterDoubleUnderscore() {
        assertEquals(
            "pause_outcome_left_20261011__com.example.app",
            PauseRuleStore.appOutcomeKey(PauseRuleStore.Outcome.LEFT, 20261011, "com.example.app"),
        )
    }

    @Test
    fun appKeysNeverCollideWithOverallKeys() {
        val overall = PauseRuleStore.outcomeKey(PauseRuleStore.Outcome.LEFT, 20261011)
        val perApp = PauseRuleStore.appOutcomeKey(PauseRuleStore.Outcome.LEFT, 20261011, "com.example.app")
        assertNotEquals(overall, perApp)
        assertTrue(perApp.startsWith(overall))
    }
}
