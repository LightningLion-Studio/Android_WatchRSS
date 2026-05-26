package com.lightningstudio.watchrss.data.rss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedContentIdsTest {
    @Test
    fun isImportedContentUrl_matchesOnlyReservedImportPrefix() {
        assertTrue(ImportedContentIds.isImportedContentUrl("https://watchrss.local/import-content"))
        assertTrue(ImportedContentIds.isImportedContentUrl("https://watchrss.local/import-content/epub/book"))
        assertFalse(ImportedContentIds.isImportedContentUrl("https://example.com/feed.xml"))
        assertFalse(ImportedContentIds.isImportedContentUrl("watchrss://phone-imports"))
    }
}
