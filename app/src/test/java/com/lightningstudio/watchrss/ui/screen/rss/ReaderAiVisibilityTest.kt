package com.lightningstudio.watchrss.ui.screen.rss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAiVisibilityTest {
    @Test
    fun aiSummaryEntryIsVisibleInEligibleProductionReaders() {
        assertTrue(
            isReaderAiSummaryEntryVisible(
                llmEnabled = true,
                isNovelContent = false
            )
        )
        assertFalse(
            isReaderAiSummaryEntryVisible(
                llmEnabled = false,
                isNovelContent = false
            )
        )
        assertFalse(
            isReaderAiSummaryEntryVisible(
                llmEnabled = true,
                isNovelContent = true
            )
        )
    }
}
