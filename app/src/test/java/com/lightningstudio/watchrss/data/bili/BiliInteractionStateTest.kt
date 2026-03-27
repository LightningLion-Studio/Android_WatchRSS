package com.lightningstudio.watchrss.data.bili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliInteractionStateTest {
    @Test
    fun upsertBiliInteractionState_keepsMostRecent50Records() {
        var records = emptyList<BiliInteractionRecord>()

        repeat(55) { index ->
            records = upsertBiliInteractionState(
                records = records,
                aid = index.toLong() + 1L,
                bvid = "BV$index",
                state = BiliInteractionState(isLiked = true),
                updatedAtMillis = index.toLong()
            )
        }

        assertEquals(BILI_INTERACTION_STATE_LIMIT, records.size)
        assertEquals("BV54", records.first().bvid)
        assertEquals("BV5", records.last().bvid)
    }

    @Test
    fun upsertBiliInteractionState_removesRecordWhenNoStateRemains() {
        val persisted = upsertBiliInteractionState(
            records = emptyList(),
            aid = 1L,
            bvid = "BV1",
            state = BiliInteractionState(isLiked = true),
            updatedAtMillis = 1L
        )

        val cleared = upsertBiliInteractionState(
            records = persisted,
            aid = 1L,
            bvid = "BV1",
            state = BiliInteractionState(),
            updatedAtMillis = 2L
        )

        assertTrue(cleared.isEmpty())
    }

    @Test
    fun hasAnyInteraction_includesFavoriteState() {
        assertTrue(BiliInteractionState(isFavorited = true).hasAnyInteraction)
    }
}
