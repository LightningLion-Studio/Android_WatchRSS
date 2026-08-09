package com.lightningstudio.watchrss.ui.screen.home

import com.lightningstudio.watchrss.data.home.NotesHomePlacement
import com.lightningstudio.watchrss.data.rss.RssChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeEntryOrderingTest {
    @Test
    fun notesEntry_usesSamePinnedAndSortOrderRulesAsChannels() {
        val pinnedChannel = channel(id = 1L, sortOrder = 100L, pinned = true)
        val recentChannel = channel(id = 2L, sortOrder = 300L, pinned = false)

        val keys = buildMovableHomeEntries(
            channels = listOf(pinnedChannel, recentChannel),
            notesPlacement = NotesHomePlacement(sortOrder = 200L, isPinned = false)
        ).map { it.key }

        assertEquals(listOf("channel_1", "channel_2", "notes"), keys)
    }

    @Test
    fun notesEntry_moveToTop_placesItAboveUnpinnedChannels() {
        val keys = buildMovableHomeEntries(
            channels = listOf(channel(id = 2L, sortOrder = 300L, pinned = false)),
            notesPlacement = NotesHomePlacement(sortOrder = 400L, isPinned = false)
        ).map { it.key }

        assertEquals(listOf("notes", "channel_2"), keys)
    }

    private fun channel(id: Long, sortOrder: Long, pinned: Boolean) = RssChannel(
        id = id,
        url = "https://example.com/$id.xml",
        title = "频道$id",
        description = null,
        imageUrl = null,
        lastFetchedAt = null,
        sortOrder = sortOrder,
        isPinned = pinned,
        useOriginalContent = false,
        unreadCount = 0
    )
}
