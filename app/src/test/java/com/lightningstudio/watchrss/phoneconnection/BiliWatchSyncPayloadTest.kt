package com.lightningstudio.watchrss.phoneconnection

import com.lightningstudio.watchrss.data.bili.BiliPlaybackProgress
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryCursor
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryEntry
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryItem
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryPage
import org.junit.Assert.assertEquals
import org.junit.Test

class BiliWatchSyncPayloadTest {
    @Test
    fun buildHistoryPayload_includesCursorAndResolvedLink() {
        val payload = BiliWatchSyncPayload.buildHistoryPayload(
            page = BiliHistoryPage(
                cursor = BiliHistoryCursor(max = 123L, viewAt = 456L, business = "archive", ps = 20),
                items = listOf(
                    BiliHistoryItem(
                        title = "测试视频",
                        cover = "https://example.com/cover.jpg",
                        viewAt = 1_700_000_000L,
                        duration = 321L,
                        progress = 111L,
                        authorName = "测试UP",
                        authorMid = 7788L,
                        history = BiliHistoryEntry(
                            oid = 9527L,
                            bvid = "BV1xx411c7mD",
                            cid = 2468L,
                            page = 2,
                            part = "P2",
                            business = "archive"
                        )
                    )
                )
            )
        ) { bvid, aid, cid ->
            "https://www.bilibili.com/video/${bvid ?: "av$aid"}?cid=$cid"
        }

        val cursor = payload.cursor
        assertEquals(123L, cursor?.max)
        assertEquals(456L, cursor?.viewAt)
        assertEquals("archive", cursor?.business)
        assertEquals(20, cursor?.ps)

        assertEquals(1, payload.items.size)
        val item = payload.items.first()
        assertEquals(9527L, item.aid)
        assertEquals("BV1xx411c7mD", item.bvid)
        assertEquals(2468L, item.cid)
        assertEquals("测试视频", item.title)
        assertEquals(321L, item.durationSeconds)
        assertEquals(111L, item.progressSeconds)
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD?cid=2468", item.link)
    }

    @Test
    fun buildPlaybackProgressPayload_keepsMillisFieldsAndLink() {
        val payload = BiliWatchSyncPayload.buildPlaybackProgressPayload(
            records = listOf(
                BiliPlaybackProgress(
                    aid = 1001L,
                    bvid = "BV1ab411c7mD",
                    cid = 3003L,
                    positionMs = 4_000L,
                    durationMs = 60_000L,
                    updatedAtMillis = 9_999L
                )
            )
        ) { bvid, aid, cid ->
            "https://www.bilibili.com/video/${bvid ?: "av$aid"}?cid=$cid"
        }

        assertEquals(1, payload.size)
        val item = payload.first()
        assertEquals(1001L, item.aid)
        assertEquals("BV1ab411c7mD", item.bvid)
        assertEquals(3003L, item.cid)
        assertEquals(4_000L, item.positionMs)
        assertEquals(60_000L, item.durationMs)
        assertEquals(9_999L, item.updatedAtMillis)
        assertEquals("https://www.bilibili.com/video/BV1ab411c7mD?cid=3003", item.link)
    }

    @Test
    fun buildHistoryPayload_returnsNullCursorWhenCursorMissing() {
        val payload = BiliWatchSyncPayload.buildHistoryPayload(
            page = BiliHistoryPage(
                cursor = null,
                items = emptyList()
            )
        ) { _, _, _ -> null }

        assertEquals(null, payload.cursor)
        assertEquals(0, payload.items.size)
    }
}
