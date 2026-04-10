package com.lightningstudio.watchrss

import com.lightningstudio.watchrss.testutil.sampleDouyinStreamItem
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinEntryResumeSupportTest {
    @Test
    fun shouldResumeDouyinVideoFlow_requiresActiveLoggedInPlaybackState() {
        assertFalse(shouldResumeDouyinVideoFlow(DouyinFeedUiState()))
        assertFalse(
            shouldResumeDouyinVideoFlow(
                DouyinFeedUiState(
                    isLoggedIn = true,
                    showTitlePage = true,
                    currentPage = 1,
                    items = listOf(sampleDouyinStreamItem())
                )
            )
        )
        assertFalse(
            shouldResumeDouyinVideoFlow(
                DouyinFeedUiState(
                    isLoggedIn = true,
                    showTitlePage = false,
                    currentPage = 0,
                    items = listOf(sampleDouyinStreamItem())
                )
            )
        )
        assertTrue(
            shouldResumeDouyinVideoFlow(
                DouyinFeedUiState(
                    isLoggedIn = true,
                    showTitlePage = false,
                    currentPage = 2,
                    items = listOf(
                        sampleDouyinStreamItem(awemeId = "aweme-a"),
                        sampleDouyinStreamItem(awemeId = "aweme-b")
                    )
                )
            )
        )
    }

    @Test
    fun resolveResumeDouyinAwemeId_returnsCurrentVideoOnlyForActivePlaybackState() {
        assertNull(resolveResumeDouyinAwemeId(DouyinFeedUiState()))
        assertNull(
            resolveResumeDouyinAwemeId(
                DouyinFeedUiState(
                    isLoggedIn = true,
                    showTitlePage = true,
                    currentPage = 1,
                    items = listOf(sampleDouyinStreamItem(awemeId = "aweme-a"))
                )
            )
        )
        assertEquals(
            "aweme-b",
            resolveResumeDouyinAwemeId(
                DouyinFeedUiState(
                    isLoggedIn = true,
                    showTitlePage = false,
                    currentPage = 2,
                    items = listOf(
                        sampleDouyinStreamItem(awemeId = "aweme-a"),
                        sampleDouyinStreamItem(awemeId = "aweme-b")
                    )
                )
            )
        )
    }
}
