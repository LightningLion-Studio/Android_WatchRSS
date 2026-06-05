package com.lightningstudio.watchrss.data.bili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliSavedItemSupportTest {
    @Test
    fun parseBiliVideoTarget_extractsBvidAndCidFromDetailPage() {
        val target = parseBiliVideoTarget("https://www.bilibili.com/video/BV1bDDEBZEp8?cid=37347986442")

        assertEquals(null, target?.aid)
        assertEquals("BV1bDDEBZEp8", target?.bvid)
        assertEquals(37347986442L, target?.cid)
    }

    @Test
    fun parseBiliVideoTarget_extractsAidFromAvDetailPage() {
        val target = parseBiliVideoTarget("https://www.bilibili.com/video/av12345")

        assertEquals(12345L, target?.aid)
        assertEquals(null, target?.bvid)
    }

    @Test
    fun biliUrlPredicates_identifyDetailPageAndHost() {
        assertTrue(isBiliWebUrl("https://www.bilibili.com/video/BV1bDDEBZEp8"))
        assertTrue(isBiliDetailPageUrl("https://m.bilibili.com/video/BV1bDDEBZEp8"))
        assertFalse(isBiliWebUrl("https://example.com/video/BV1bDDEBZEp8"))
        assertFalse(isBiliDetailPageUrl("https://example.com/video/BV1bDDEBZEp8"))
        assertFalse(isBiliDetailPageUrl("https://www.bilibili.com/read/cv123"))
    }

    @Test
    fun buildBiliPlaybackWebUrl_rejectsNonVideoBiliFallback() {
        val url = buildBiliPlaybackWebUrl(
            aid = null,
            bvid = null,
            cid = null,
            fallbackUrl = "https://www.bilibili.com/read/cv123"
        )

        assertEquals(null, url)
    }

    @Test
    fun buildBiliExternalSavedItem_setsVideoUrlToPlayableWebTarget() {
        val item = buildBiliExternalSavedItem(
            aid = null,
            bvid = "BV1bDDEBZEp8",
            cid = 37347986442L,
            title = "标题",
            owner = "UP",
            coverUrl = "https://example.com/cover.jpg"
        ).item

        assertEquals("https://www.bilibili.com/video/BV1bDDEBZEp8?cid=37347986442", item.link)
        assertEquals(item.link, item.videoUrl)
    }
}
