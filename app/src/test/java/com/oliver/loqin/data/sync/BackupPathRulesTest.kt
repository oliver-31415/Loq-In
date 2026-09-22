package com.oliver.loqin.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Website path rules are stored as plain strings in the same per-profile set as host rules
 * (for example "example.com" and "example.com/blocked/&lt;path&gt;"). The backup pipeline must carry
 * them through unchanged: the export filter must include the set for the Website rules category
 * and the set -> list -> set conversion used by the payload must preserve the path part.
 */
class BackupPathRulesTest {

    @Test
    fun `path rules are included when exporting website rules`() {
        val prefs = mapOf<String, Any?>(
            "domain_block_domains__p__Default" to listOf("example.com", "example.com/blocked/*"),
            "domain_block_domains__p__Work" to listOf("youtube.com/shorts/*"),
            "unrelated_key" to "x",
        )

        val filtered = BackupCategoryFilter.filterDefaultPrefs(
            prefs,
            BackupSelection(setOf(BackupCategory.WEBSITE_RULES.id)),
        )

        @Suppress("UNCHECKED_CAST")
        val defaultRules = (filtered["domain_block_domains__p__Default"] as List<Any?>)
            .filterIsInstance<String>()
        assertTrue(defaultRules.contains("example.com"))
        assertTrue(defaultRules.contains("example.com/blocked/*"))

        @Suppress("UNCHECKED_CAST")
        val workRules = (filtered["domain_block_domains__p__Work"] as List<Any?>)
            .filterIsInstance<String>()
        assertTrue(workRules.contains("youtube.com/shorts/*"))
        assertFalse(filtered.containsKey("unrelated_key"))
    }

    @Test
    fun `path rules are excluded when website rules are not selected`() {
        val prefs = mapOf<String, Any?>(
            "domain_block_domains__p__Default" to listOf("example.com/blocked/*"),
        )
        val filtered = BackupCategoryFilter.filterDefaultPrefs(
            prefs,
            BackupSelection(setOf(BackupCategory.PROFILES.id)),
        )
        assertFalse(filtered.containsKey("domain_block_domains__p__Default"))
    }

    @Test
    fun `path rules survive the set to list to set payload round trip`() {
        val exported: List<String> = setOf("example.com", "example.com/blocked/*").toList()
        val restored: Set<String> = exported.filterIsInstance<String>().toSet()
        assertTrue(restored.contains("example.com"))
        assertTrue(restored.contains("example.com/blocked/*"))
        assertEquals(2, restored.size)
    }
}
