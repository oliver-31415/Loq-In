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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainBlockStoreTest {

    @Test
    fun `normalize keeps host rules host-only`() {
        assertEquals("youtube.com", DomainBlockStore.normalize("https://www.YouTube.com"))
        assertEquals("youtube.com", DomainBlockStore.normalize("*.youtube.com"))
        assertEquals("youtube.com", DomainBlockStore.normalize("youtube.com:8443/"))
        assertEquals("example.com", DomainBlockStore.normalize("user:pass@example.com"))
        assertEquals("example.com", DomainBlockStore.normalize("example.com."))
    }

    @Test
    fun `normalize preserves path rules`() {
        assertEquals(
            "youtube.com/shorts",
            DomainBlockStore.normalize("https://www.youtube.com/shorts")
        )
        assertEquals(
            "youtube.com/shorts/*",
            DomainBlockStore.normalize("youtube.com//shorts//*")
        )
        assertEquals(
            "youtube.com/feed?",
            DomainBlockStore.normalize("youtube.com/feed?")
        )
    }

    @Test
    fun `normalize rejects invalid inputs`() {
        assertNull(DomainBlockStore.normalize(""))
        assertNull(DomainBlockStore.normalize("localhost"))
        assertNull(DomainBlockStore.normalize(".example.com"))
        // Whitespace tails are dropped, so a stray space truncates instead of rejecting.
        assertEquals("example.com/path", DomainBlockStore.normalize("example.com/path with space"))
    }

    @Test
    fun `host and path parts split correctly`() {
        assertEquals("youtube.com", DomainBlockStore.hostPart("youtube.com/shorts/*"))
        assertEquals("/shorts/*", DomainBlockStore.pathPart("youtube.com/shorts/*"))
        assertNull(DomainBlockStore.pathPart("youtube.com"))
        assertTrue(DomainBlockStore.isPathRule("youtube.com/shorts/*"))
        assertFalse(DomainBlockStore.isPathRule("youtube.com"))
    }

    @Test
    fun `host rules match the host and its subdomains`() {
        assertTrue(DomainBlockStore.matches("youtube.com", "youtube.com"))
        assertTrue(DomainBlockStore.matches("m.youtube.com", "youtube.com"))
        assertTrue(DomainBlockStore.matches("m.youtube.com/shorts/x", "youtube.com"))
        assertFalse(DomainBlockStore.matches("notyoutube.com", "youtube.com"))
        assertFalse(DomainBlockStore.matches("youtube.com.evil.example", "youtube.com"))
    }

    @Test
    fun `path rules require the path to match`() {
        assertTrue(DomainBlockStore.matches("youtube.com/shorts/x", "youtube.com/shorts/*"))
        assertTrue(DomainBlockStore.matches("youtube.com/shorts/", "youtube.com/shorts/*"))
        assertFalse(DomainBlockStore.matches("youtube.com", "youtube.com/shorts/*"))
        assertFalse(DomainBlockStore.matches("youtube.com/watch?v=1", "youtube.com/shorts/*"))
        // Browsers trim the trailing slash in the displayed URL (Firefox shows "/shorts/" as
        // "/shorts"), so the trimmed target must still match the rule written for the real URL.
        assertTrue(DomainBlockStore.matches("youtube.com/shorts", "youtube.com/shorts/*"))
    }

    @Test
    fun `path wildcards support star and question mark`() {
        assertTrue(DomainBlockStore.matches("example.com/feed/all", "example.com/feed/*"))
        assertTrue(DomainBlockStore.matches("example.com/ab", "example.com/a?"))
        assertFalse(DomainBlockStore.matches("example.com/abc", "example.com/a?"))
    }

    @Test
    fun `path rules match subdomains too`() {
        assertTrue(DomainBlockStore.matches("m.youtube.com/shorts/x", "youtube.com/shorts/*"))
    }
}
