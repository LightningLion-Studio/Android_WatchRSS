package com.lightningstudio.watchrss.ui.screen.rss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAiVisibilityTest {
    @Test
    fun aiSummaryEntryRemainsVisibleWithoutPaidAuthorization() {
        assertTrue(
            isReaderAiSummaryEntryVisible(
                llmEnabled = true
            )
        )
        assertFalse(
            isReaderAiSummaryEntryVisible(
                llmEnabled = false
            )
        )
    }
}
