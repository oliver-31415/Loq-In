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

package com.oliver.loqin.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupExclusionTest {

    @Test
    fun `pending protection queue is excluded from backups`() {
        assertTrue(LocalBackupPayload.isBackupExcludedKey("protection_pending_changes_json"))
    }

    @Test
    fun `protection change delay is not excluded`() {
        assertFalse(LocalBackupPayload.isBackupExcludedKey("protection_change_delay_minutes"))
    }

    @Test
    fun `existing exclusions still hold`() {
        assertTrue(LocalBackupPayload.isBackupExcludedKey("switch_mode_active_since_ms"))
        assertTrue(LocalBackupPayload.isBackupExcludedKey("pref_app_lock_pin_hash"))
        assertTrue(LocalBackupPayload.isBackupExcludedKey("emergency_pin"))
        assertFalse(LocalBackupPayload.isBackupExcludedKey("blocked_apps_Default"))
    }
}
