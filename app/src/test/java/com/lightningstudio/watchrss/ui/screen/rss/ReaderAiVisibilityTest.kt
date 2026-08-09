package com.lightningstudio.watchrss.ui.screen.rss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAiVisibilityTest {
    @Test
    fun aiSummaryEntryIsOnlyVisibleInEligibleDebugReaders() {
        assertTrue(
            isReaderAiSummaryEntryVisible(
                isDebugBuild = true,
                llmEnabled = true,
                isNovelContent = false
            )
        )
        assertFalse(
            isReaderAiSummaryEntryVisible(
                isDebugBuild = false,
                llmEnabled = true,
                isNovelContent = false
            )
        )
        assertFalse(
            isReaderAiSummaryEntryVisible(
                isDebugBuild = true,
                llmEnabled = false,
                isNovelContent = false
            )
        )
        assertFalse(
            isReaderAiSummaryEntryVisible(
                isDebugBuild = true,
                llmEnabled = true,
                isNovelContent = true
            )
        )
    }
}
