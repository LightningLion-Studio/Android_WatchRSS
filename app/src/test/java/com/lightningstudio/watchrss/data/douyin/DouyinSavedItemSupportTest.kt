package com.lightningstudio.watchrss.data.douyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinSavedItemSupportTest {
    @Test
    fun parseDouyinAwemeId_extractsVideoIdFromDetailPage() {
        assertEquals(
            "7357000000000000001",
            parseDouyinAwemeId("https://www.douyin.com/video/7357000000000000001")
        )
    }

    @Test
    fun parseDouyinAwemeId_extractsVideoIdFromQueryParameter() {
        assertEquals(
            "7357000000000000001",
            parseDouyinAwemeId("https://www.iesdouyin.com/share/video?aweme_id=7357000000000000001")
        )
    }

    @Test
    fun douyinUrlPredicates_identifyDetailPageAndHost() {
        assertTrue(isDouyinWebUrl("https://www.douyin.com/video/7357000000000000001"))
        assertTrue(isDouyinDetailPageUrl("https://www.douyin.com/video/7357000000000000001"))
        assertFalse(isDouyinWebUrl("https://example.com/video/7357000000000000001"))
        assertFalse(isDouyinDetailPageUrl("https://example.com/video/7357000000000000001"))
    }
}
