package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.data.bili.formatBiliError
import com.lightningstudio.watchrss.sdk.bili.BiliDurl
import com.lightningstudio.watchrss.sdk.bili.BiliPlayUrl
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestBiliRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiliPlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadPlayUrl_emitsPreviewThenRemoteUpgrade_whenExactPreviewExists() = runTest {
        val repo = TestBiliRepository().apply {
            cachedPreviewUriValue = "file:///tmp/bili-preview.mp4"
            playUrlResult = BiliResult(
                code = 0,
                data = BiliPlayUrl(
                    durl = listOf(BiliDurl(url = "https://example.com/full.mp4"))
                )
            )
            playHeaders = mapOf(
                "User-Agent" to "TestUA",
                "Referer" to "https://www.bilibili.com"
            )
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            BiliPlaybackSource(
                url = "file:///tmp/bili-preview.mp4",
                kind = BiliPlaybackSourceKind.PREVIEW
            ),
            state.initialSource
        )
        assertEquals(
            BiliPlaybackSource(
                url = "https://example.com/full.mp4",
                headers = repo.playHeaders,
                kind = BiliPlaybackSourceKind.REMOTE
            ),
            state.upgradeSource
        )
        assertFalse(state.isLoading)
        assertFalse(state.isUpgradeLoading)
        assertNull(state.message)
        assertNull(state.upgradeErrorMessage)
        assertEquals(
            listOf(
                "cached:12:BV12:34",
                "play:34:12:BV12:32"
            ),
            repo.callLog
        )
        assertNoAnyPreviewFallback(repo)
    }

    @Test
    fun loadPlayUrl_usesRemoteOnly_whenPreviewCacheMisses() = runTest {
        val repo = TestBiliRepository().apply {
            playUrlResult = BiliResult(
                code = 0,
                data = BiliPlayUrl(
                    durl = listOf(BiliDurl(url = "https://example.com/full.mp4"))
                )
            )
            playHeaders = mapOf("User-Agent" to "TestUA")
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            BiliPlaybackSource(
                url = "https://example.com/full.mp4",
                headers = repo.playHeaders,
                kind = BiliPlaybackSourceKind.REMOTE
            ),
            state.initialSource
        )
        assertNull(state.upgradeSource)
        assertFalse(state.isLoading)
        assertFalse(state.isUpgradeLoading)
        assertNull(state.message)
        assertNull(state.upgradeErrorMessage)
        assertEquals(
            listOf(
                "cached:12:BV12:34",
                "play:34:12:BV12:32"
            ),
            repo.callLog
        )
        assertNoAnyPreviewFallback(repo)
    }

    @Test
    fun loadPlayUrl_keepsPreview_whenRemoteFetchFails() = runTest {
        val repo = TestBiliRepository().apply {
            cachedPreviewUriValue = "file:///tmp/bili-preview.mp4"
            playUrlResult = BiliResult(code = BiliErrorCodes.REQUEST_FAILED, message = "request_failed")
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            BiliPlaybackSource(
                url = "file:///tmp/bili-preview.mp4",
                kind = BiliPlaybackSourceKind.PREVIEW
            ),
            state.initialSource
        )
        assertNull(state.upgradeSource)
        assertFalse(state.isLoading)
        assertFalse(state.isUpgradeLoading)
        assertNull(state.message)
        assertEquals(
            formatBiliError(BiliErrorCodes.REQUEST_FAILED, "request_failed"),
            state.upgradeErrorMessage
        )
        assertEquals(
            listOf(
                "cached:12:BV12:34",
                "play:34:12:BV12:32"
            ),
            repo.callLog
        )
        assertNoAnyPreviewFallback(repo)
    }

    @Test
    fun onPreviewPlaybackFailed_clearsExactPreviewAndPromotesReadyRemote() = runTest {
        val repo = TestBiliRepository().apply {
            cachedPreviewUriValue = "file:///tmp/bili-preview.mp4"
            playUrlResult = BiliResult(
                code = 0,
                data = BiliPlayUrl(
                    durl = listOf(BiliDurl(url = "https://example.com/full.mp4"))
                )
            )
            playHeaders = mapOf("User-Agent" to "TestUA")
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        viewModel.onPreviewPlaybackFailed()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            BiliPlaybackSource(
                url = "https://example.com/full.mp4",
                headers = repo.playHeaders,
                kind = BiliPlaybackSourceKind.REMOTE
            ),
            state.initialSource
        )
        assertNull(state.upgradeSource)
        assertFalse(state.isLoading)
        assertFalse(state.isUpgradeLoading)
        assertNull(state.message)
        assertNull(state.upgradeErrorMessage)
        assertEquals(
            listOf(
                "cached:12:BV12:34",
                "play:34:12:BV12:32",
                "clearcache:12:BV12:34"
            ),
            repo.callLog
        )
        assertNoAnyPreviewFallback(repo)
    }

    @Test
    fun onPreviewPlaybackFailed_clearsExactPreviewAndRefetchesRemote_whenUpgradeUnavailable() = runTest {
        val repo = TestBiliRepository().apply {
            cachedPreviewUriValue = "file:///tmp/bili-preview.mp4"
            playUrlResult = BiliResult(code = BiliErrorCodes.REQUEST_FAILED, message = "request_failed")
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        repo.playUrlResult = BiliResult(
            code = 0,
            data = BiliPlayUrl(
                durl = listOf(BiliDurl(url = "https://example.com/recovered.mp4"))
            )
        )
        repo.playHeaders = mapOf("User-Agent" to "RecoveredUA")

        viewModel.onPreviewPlaybackFailed()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            BiliPlaybackSource(
                url = "https://example.com/recovered.mp4",
                headers = repo.playHeaders,
                kind = BiliPlaybackSourceKind.REMOTE
            ),
            state.initialSource
        )
        assertNull(state.upgradeSource)
        assertFalse(state.isLoading)
        assertFalse(state.isUpgradeLoading)
        assertNull(state.message)
        assertNull(state.upgradeErrorMessage)
        assertEquals(
            listOf(
                "cached:12:BV12:34",
                "play:34:12:BV12:32",
                "clearcache:12:BV12:34",
                "play:34:12:BV12:32"
            ),
            repo.callLog
        )
        assertNoAnyPreviewFallback(repo)
    }

    private fun createViewModel(repository: TestBiliRepository): BiliPlayerViewModel {
        return BiliPlayerViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "aid" to "12",
                    "bvid" to "BV12",
                    "cid" to "34"
                )
            ),
            repository = repository
        )
    }

    private fun assertNoAnyPreviewFallback(repository: TestBiliRepository) {
        assertFalse(repository.callLog.any { it.startsWith("cachedAny:") })
    }
}
