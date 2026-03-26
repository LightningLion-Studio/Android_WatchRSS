package com.lightningstudio.watchrss.data.bili

import org.junit.Assert.assertEquals
import org.junit.Test

class BiliPlaybackProgressTest {
    @Test
    fun findLatestBiliPlaybackProgress_returnsMostRecentCidForVideo() {
        val records = listOf(
            BiliPlaybackProgress(aid = 1L, bvid = "BV1", cid = 11L, updatedAtMillis = 1L),
            BiliPlaybackProgress(aid = 1L, bvid = "BV1", cid = 22L, updatedAtMillis = 3L),
            BiliPlaybackProgress(aid = 2L, bvid = "BV2", cid = 33L, updatedAtMillis = 2L)
        )

        val latest = findLatestBiliPlaybackProgress(records, aid = 1L, bvid = "BV1")

        assertEquals(22L, latest?.cid)
    }

    @Test
    fun upsertBiliPlaybackProgress_replacesExistingRecordForSameCid() {
        val persisted = upsertBiliPlaybackProgress(
            records = emptyList(),
            progress = BiliPlaybackProgress(aid = 1L, bvid = "BV1", cid = 11L, positionMs = 4_000L),
            updatedAtMillis = 1L
        )

        val updated = upsertBiliPlaybackProgress(
            records = persisted,
            progress = BiliPlaybackProgress(aid = 1L, bvid = "BV1", cid = 11L, positionMs = 9_000L),
            updatedAtMillis = 2L
        )

        assertEquals(1, updated.size)
        assertEquals(9_000L, updated.first().positionMs)
    }

    @Test
    fun removeBiliPlaybackProgress_removesOnlyTargetCid() {
        val records = listOf(
            BiliPlaybackProgress(aid = 1L, bvid = "BV1", cid = 11L, updatedAtMillis = 1L),
            BiliPlaybackProgress(aid = 1L, bvid = "BV1", cid = 22L, updatedAtMillis = 2L)
        )

        val remaining = removeBiliPlaybackProgress(records, aid = 1L, bvid = "BV1", cid = 11L)

        assertEquals(listOf(22L), remaining.map { it.cid })
    }
}
