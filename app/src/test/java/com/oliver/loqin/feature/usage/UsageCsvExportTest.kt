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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageCsvExportTest {

    @Test
    fun headerListsColumnsInOrder() {
        assertEquals("date,package,app_name,screen_time_minutes,opens,blocks", UsageCsvExport.HEADER)
    }

    @Test
    fun plainFieldIsUnchanged() {
        assertEquals("Maps", UsageCsvExport.escape("Maps"))
    }

    @Test
    fun fieldWithCommaIsQuoted() {
        assertEquals("\"Notes, Pro\"", UsageCsvExport.escape("Notes, Pro"))
    }

    @Test
    fun fieldWithQuoteIsQuotedAndDoubled() {
        assertEquals("\"say \"\"hi\"\"\"", UsageCsvExport.escape("say \"hi\""))
    }

    @Test
    fun fieldWithNewlineIsQuoted() {
        assertEquals("\"line1\nline2\"", UsageCsvExport.escape("line1\nline2"))
    }

    @Test
    fun fieldWithCarriageReturnIsQuoted() {
        assertEquals("\"a\rb\"", UsageCsvExport.escape("a\rb"))
    }

    @Test
    fun rowEscapesAppNameAndKeepsNumericColumns() {
        val line = UsageCsvExport.row(
            date = "2026-10-01",
            packageName = "com.example.app",
            appName = "Tom's \"Best\", App",
            screenTimeMs = 5 * 60_000L + 30_000L,
            opens = 3,
            blocks = 0
        )
        assertEquals("2026-10-01,com.example.app,\"Tom's \"\"Best\"\", App\",5,3,0", line)
    }

    @Test
    fun screenTimeIsTruncatedToWholeMinutes() {
        val line = UsageCsvExport.row("2026-10-01", "p", "P", 40_000L, 0, 1)
        assertEquals("2026-10-01,p,P,0,0,1", line)
    }

    @Test
    fun hasDataFalseWhenEverythingIsZero() {
        assertFalse(UsageCsvExport.hasData(0L, 0, 0))
    }

    @Test
    fun hasDataTrueForAnySingleNonZeroMeasure() {
        assertTrue(UsageCsvExport.hasData(1L, 0, 0))
        assertTrue(UsageCsvExport.hasData(0L, 1, 0))
        assertTrue(UsageCsvExport.hasData(0L, 0, 1))
    }
}
