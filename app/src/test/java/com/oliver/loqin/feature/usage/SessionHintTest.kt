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

import com.oliver.loqin.feature.usage.SessionHint.Hint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionHintTest {

    @Test
    fun noSessionsGivesNoHint() {
        assertNull(SessionHint.forBuckets(listOf(0, 0, 0, 0, 0)))
    }

    @Test
    fun shortRuleNeedsTenSessions() {
        // 100% short, but only 9 sessions.
        assertNull(SessionHint.forBuckets(listOf(5, 4, 0, 0, 0)))
    }

    @Test
    fun shortRuleFiresAtSeventyPercentWithTenSessions() {
        // 7 of 10 under 5 minutes.
        assertEquals(Hint.MOSTLY_SHORT, SessionHint.forBuckets(listOf(4, 3, 3, 0, 0)))
    }

    @Test
    fun shortRuleDoesNotFireBelowSeventyPercent() {
        // 6 of 10 under 5 minutes.
        assertNull(SessionHint.forBuckets(listOf(3, 3, 4, 0, 0)))
    }

    @Test
    fun longRuleFiresAtFortyPercent() {
        // 2 of 5 at 15 minutes or more.
        assertEquals(Hint.OFTEN_LONG, SessionHint.forBuckets(listOf(0, 0, 3, 1, 1)))
    }

    @Test
    fun longRuleDoesNotFireBelowFortyPercent() {
        // 3 of 9 at 15 minutes or more.
        assertNull(SessionHint.forBuckets(listOf(1, 2, 3, 2, 1)))
    }

    @Test
    fun negativeCountsAreTreatedAsZero() {
        assertEquals(Hint.OFTEN_LONG, SessionHint.forBuckets(listOf(-3, 0, 3, 1, 1)))
    }

    @Test
    fun wrongBucketCountIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionHint.forBuckets(listOf(1, 2, 3))
        }
    }

    @Test
    fun longRuleNeedsAFewSessions() {
        // Two long videos are not a pattern.
        assertNull(SessionHint.forBuckets(listOf(0, 0, 0, 1, 1)))
    }
}
