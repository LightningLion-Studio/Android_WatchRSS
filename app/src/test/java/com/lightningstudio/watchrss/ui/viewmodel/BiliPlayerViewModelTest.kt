package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.sdk.bili.BiliDurl
import com.lightningstudio.watchrss.sdk.bili.BiliPlayUrl
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestBiliRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiliPlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadPlayUrl_prefersRemoteUrl_whenCachedPreviewExists() = runTest {
        val repo = TestBiliRepository().apply {
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
            cachedPreviewUriValue = "file:///tmp/bili-preview.mp4"
        }

        val viewModel = BiliPlayerViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "aid" to "12",
                    "bvid" to "BV12",
                    "cid" to "34"
                )
            ),
            repository = repo
        )
        advanceUntilIdle()

        assertEquals("https://example.com/full.mp4", viewModel.uiState.value.playUrl)
        assertEquals(repo.playHeaders, viewModel.uiState.value.headers)
        assertNull(viewModel.uiState.value.message)
        assertEquals(listOf("play:34:12:BV12:32"), repo.callLog)
    }

    @Test
    fun loadPlayUrl_fallsBackToCachedPreview_whenRemoteFetchFails() = runTest {
        val repo = TestBiliRepository().apply {
            playUrlResult = BiliResult(code = BiliErrorCodes.REQUEST_FAILED, message = "request_failed")
            cachedPreviewUriValue = "file:///tmp/bili-preview.mp4"
        }

        val viewModel = BiliPlayerViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "aid" to "12",
                    "bvid" to "BV12",
                    "cid" to "34"
                )
            ),
            repository = repo
        )
        advanceUntilIdle()

        assertEquals("file:///tmp/bili-preview.mp4", viewModel.uiState.value.playUrl)
        assertEquals(emptyMap<String, String>(), viewModel.uiState.value.headers)
        assertNull(viewModel.uiState.value.message)
        assertEquals(
            listOf(
                "play:34:12:BV12:32",
                "cached:12:BV12:34"
            ),
            repo.callLog
        )
    }
}
