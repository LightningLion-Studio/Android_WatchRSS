package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.lightningstudio.watchrss.data.douyin.DouyinErrorCodes
import com.lightningstudio.watchrss.data.douyin.DouyinResult
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestDouyinRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RssPlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadPlayUrl_usesRawUrl_forNonDouyinSource() = runTest {
        val repository = TestDouyinRepository()
        val viewModel = createViewModel(
            repository = repository,
            playUrl = "https://example.com/video.mp4",
            webUrl = "https://example.com/detail.html",
            awemeId = null
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            BiliPlaybackSource(
                url = "https://example.com/video.mp4",
                headers = emptyMap(),
                kind = BiliPlaybackSourceKind.REMOTE,
                cacheKey = null
            ),
            state.initialSource
        )
        assertNull(state.message)
        assertTrue(repository.fetchVideoCalls.isEmpty())
    }

    @Test
    fun loadPlayUrl_refreshesDouyinSource_beforePlayback() = runTest {
        val repository = TestDouyinRepository().apply {
            headers = mapOf(
                "User-Agent" to "DouyinUA",
                "Referer" to "https://www.douyin.com/",
                "Cookie" to "passport_csrf_token=token"
            )
            videoResult = DouyinResult(
                code = DouyinErrorCodes.OK,
                data = DouyinContent.Video(
                    awemeId = "7357000000000000001",
                    desc = "刷新后",
                    authorName = "作者",
                    diggCount = 1L,
                    playUrl = "https://cdn.example.com/refreshed.mp4",
                    coverUrl = "https://cdn.example.com/refreshed.jpg"
                )
            )
        }

        val viewModel = createViewModel(
            repository = repository,
            playUrl = "https://www.douyin.com/video/7357000000000000001",
            webUrl = "https://www.douyin.com/video/7357000000000000001",
            awemeId = "7357000000000000001"
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("7357000000000000001"), repository.fetchVideoCalls)
        assertEquals("https://cdn.example.com/refreshed.mp4", state.initialSource?.url)
        assertEquals(repository.headers, state.initialSource?.headers)
        assertTrue(state.initialSource?.cacheKey?.startsWith("7357000000000000001:") == true)
        assertEquals(
            "https://www.douyin.com/video/7357000000000000001",
            viewModel.webUrl()
        )
        assertNull(state.message)
    }

    @Test
    fun loadPlayUrl_fallsBackToSavedDouyinUrl_whenRefreshFails() = runTest {
        val repository = TestDouyinRepository().apply {
            headers = mapOf(
                "User-Agent" to "DouyinUA",
                "Referer" to "https://www.douyin.com/"
            )
            videoResult = DouyinResult(
                code = DouyinErrorCodes.REQUEST_FAILED,
                message = "network_failed"
            )
        }

        val viewModel = createViewModel(
            repository = repository,
            playUrl = "https://cdn.example.com/saved.mp4",
            webUrl = "https://www.douyin.com/video/7357000000000000001",
            awemeId = "7357000000000000001"
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("https://cdn.example.com/saved.mp4", state.initialSource?.url)
        assertEquals(repository.headers, state.initialSource?.headers)
        assertNull(state.message)
    }

    @Test
    fun recoverFromPlaybackError_refreshesDouyinSource_again() = runTest {
        val repository = TestDouyinRepository().apply {
            headers = mapOf(
                "User-Agent" to "DouyinUA",
                "Referer" to "https://www.douyin.com/"
            )
            videoResult = DouyinResult(
                code = DouyinErrorCodes.REQUEST_FAILED,
                message = "network_failed"
            )
        }

        val viewModel = createViewModel(
            repository = repository,
            playUrl = "https://cdn.example.com/saved.mp4",
            webUrl = "https://www.douyin.com/video/7357000000000000001",
            awemeId = "7357000000000000001"
        )
        advanceUntilIdle()

        repository.videoResult = DouyinResult(
            code = DouyinErrorCodes.OK,
            data = DouyinContent.Video(
                awemeId = "7357000000000000001",
                desc = "二次刷新后",
                authorName = "作者",
                diggCount = 2L,
                playUrl = "https://cdn.example.com/recovered.mp4",
                coverUrl = "https://cdn.example.com/recovered.jpg"
            )
        )

        assertTrue(viewModel.recoverFromPlaybackError())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            listOf("7357000000000000001", "7357000000000000001"),
            repository.fetchVideoCalls
        )
        assertEquals("https://cdn.example.com/recovered.mp4", state.initialSource?.url)
        assertNull(state.message)
    }

    private fun createViewModel(
        repository: TestDouyinRepository,
        playUrl: String,
        webUrl: String,
        awemeId: String?
    ): RssPlayerViewModel {
        return RssPlayerViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    RssPlayerViewModel.KEY_PLAY_URL to playUrl,
                    RssPlayerViewModel.KEY_WEB_URL to webUrl,
                    RssPlayerViewModel.KEY_AWEME_ID to awemeId
                )
            ),
            douyinRepository = repository
        )
    }
}
