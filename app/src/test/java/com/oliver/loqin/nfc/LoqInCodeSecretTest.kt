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

package com.oliver.loqin.nfc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoqInCodeSecretTest {

    @Test
    fun `matching secret is accepted`() {
        assertTrue(LoqInCodeSecret.matches("abc123_-", "abc123_-"))
    }

    @Test
    fun `different secret is rejected`() {
        assertFalse(LoqInCodeSecret.matches("abc123_-", "abc123_x"))
        assertFalse(LoqInCodeSecret.matches("abc123_-", "abc123_-x"))
    }

    @Test
    fun `missing secret never matches`() {
        assertFalse(LoqInCodeSecret.matches(null, null))
        assertFalse(LoqInCodeSecret.matches(null, "abc"))
        assertFalse(LoqInCodeSecret.matches("abc", null))
        assertFalse(LoqInCodeSecret.matches("", ""))
        assertFalse(LoqInCodeSecret.matches("  ", "  "))
    }
}
