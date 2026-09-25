package com.lightningstudio.watchrss.data.rss

import org.junit.Assert.*
import org.junit.Test

class NovelSourcePolicyTest {
    @Test fun novelSourcesOptIn() {
        assertTrue(ImportedContentIds.isNovelSourceUrl(ImportedContentIds.ROOT_SOURCE_URL))
        assertTrue(ImportedContentIds.isNovelSourceUrl("${ImportedContentIds.EPUB_SOURCE_ROOT_URL}/book-1"))
        assertTrue(ImportedContentIds.isNovelSourceUrl("  ${ImportedContentIds.EPUB_SOURCE_ROOT_URL.uppercase()}  "))
    }

    @Test fun otherModulesStayOutsideNovelPolicy() {
        assertFalse(ImportedContentIds.isNovelSourceUrl(null))
        assertFalse(ImportedContentIds.isNovelSourceUrl("https://example.org/feed.xml"))
        assertFalse(ImportedContentIds.isNovelSourceUrl(ImportedContentIds.PHONE_IMPORT_CHANNEL_URL))
        assertFalse(ImportedContentIds.isNovelSourceUrl("${ImportedContentIds.EPUB_SOURCE_ROOT_URL}-other"))
    }
}
