package com.lightningstudio.watchrss.data.home

import org.junit.Assert.*
import org.junit.Test

class HomeOrderDraftTest {
    @Test
    fun moveSupportsHeadMiddleTailAndLeavesOriginalDraftUnchanged() {
        val original = listOf(0L, 1L, 2L, 3L)
        assertEquals(listOf(3L, 0L, 1L, 2L), moveHomeOrderItem(original, 3, 0))
        assertEquals(listOf(1L, 2L, 3L, 0L), moveHomeOrderItem(original, 0, 3))
        assertEquals(listOf(0L, 2L, 1L, 3L), moveHomeOrderItem(original, 2, 1))
        assertEquals(listOf(0L, 1L, 2L, 3L), original)
        assertEquals(original, moveHomeOrderItem(original, 99, 0))
    }

    @Test
    fun movementClampsAtBoundaries() {
        val ids = listOf(0L, 1L, 2L)
        assertEquals(ids, moveHomeOrderItem(ids, 0, -100))
        assertEquals(ids, moveHomeOrderItem(ids, 2, 100))
        assertEquals(emptyList<Long>(), moveHomeOrderItem(emptyList(), 0, 0))
    }

    @Test
    fun saveRequiresTheCurrentCompleteSetAndOneNotesEntry() {
        validateHomeOrder(listOf(2L, 0L, 1L), listOf(1L, 2L))
        validateHomeOrder(listOf(0L), emptyList())
        for (invalid in listOf(listOf(1L, 2L), listOf(0L, 1L, 1L), listOf(0L, 1L, 3L), listOf(0L, 1L))) {
            assertTrue(runCatching { validateHomeOrder(invalid, listOf(1L, 2L)) }.isFailure)
        }
        // A channel arriving while the user edits must not be silently dropped on save.
        assertTrue(runCatching { validateHomeOrder(listOf(0L, 1L), listOf(1L, 2L)) }.isFailure)
    }
}
