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
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileLimitKeysTest {

    @Test
    fun `limit keys move to the new profile name`() {
        assertEquals(
            "usage_limit_min__Focus__com.reddit.frontpage",
            ProfileLimitKeys.renamedKey("usage_limit_min__Work__com.reddit.frontpage", "Work", "Focus", emptySet()),
        )
        assertEquals(
            "attempt_limit__Focus__com.example",
            ProfileLimitKeys.renamedKey("attempt_limit__Work__com.example", "Work", "Focus", emptySet()),
        )
    }

    @Test
    fun `a longer profile name keeps its own limit keys`() {
        assertNull(
            ProfileLimitKeys.renamedKey("usage_limit_min__Work__Home__com.example", "Work", "Focus", setOf("Work__Home")),
        )
    }

    @Test
    fun `day keys move with doubled underscores`() {
        assertEquals(
            "profile_usage_day_20261016_Deep__focus_com.my__app",
            ProfileLimitKeys.renamedKey("profile_usage_day_20261016_Work_com.my__app", "Work", "Deep_focus", emptySet()),
        )
        assertEquals(
            "limit_reached_20261016_Focus_com.example",
            ProfileLimitKeys.renamedKey("limit_reached_20261016_my__work_com.example", "my_work", "Focus", emptySet()),
        )
    }

    @Test
    fun `day keys of a profile that only starts with the name are left alone`() {
        assertNull(
            ProfileLimitKeys.renamedKey("profile_usage_day_20261016_Work__x_com.example", "Work", "Focus", emptySet()),
        )
        assertNull(ProfileLimitKeys.renamedKey("profile_usage_day_garbage", "Work", "Focus", emptySet()))
        assertNull(ProfileLimitKeys.renamedKey("unrelated_key", "Work", "Focus", emptySet()))
    }
}
