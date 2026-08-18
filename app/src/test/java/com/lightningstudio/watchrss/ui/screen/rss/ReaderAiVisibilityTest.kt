package com.lightningstudio.watchrss.ui.screen.rss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAiVisibilityTest {
    @Test
    fun aiSummaryEntryRequiresEnabledPaidAuthorization() {
        assertTrue(
            isReaderAiSummaryEntryVisible(
                llmEnabled = true,
                hasPaidAuthorization = true
            )
        )
        assertFalse(
            isReaderAiSummaryEntryVisible(
                llmEnabled = false,
                hasPaidAuthorization = true
            )
        )
        assertFalse(
            isReaderAiSummaryEntryVisible(
                llmEnabled = true,
                hasPaidAuthorization = false
            )
        )
    }
}
