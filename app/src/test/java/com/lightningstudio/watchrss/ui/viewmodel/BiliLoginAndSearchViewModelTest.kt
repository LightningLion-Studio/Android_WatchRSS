package com.lightningstudio.watchrss.ui.viewmodel

import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.data.bili.formatBiliError
import com.lightningstudio.watchrss.sdk.bili.QrPollResult
import com.lightningstudio.watchrss.sdk.bili.QrPollStatus
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestBiliRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiliLoginAndSearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun startLogin_requestsWebQr_and_successfullyPollsQrState() = runTest {
        val repo = TestBiliRepository().apply {
            webQrPollResult = QrPollResult(status = QrPollStatus.SUCCESS, rawCode = 0)
        }
        val viewModel = BiliLoginViewModel(repo)

        viewModel.startLogin()
        advanceUntilIdle()

        assertEquals("web-test-key", viewModel.uiState.value.pollToken)
        assertEquals(1, repo.requestWebQrCodeCalls)

        advanceTimeBy(2_100)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isSuccess)
        assertEquals("登录成功", viewModel.uiState.value.message)
        assertEquals("web-test-key", repo.lastWebPollToken)
    }

    @Test
    fun startLogin_showsError_whenWebQrUnavailable() = runTest {
        val repo = TestBiliRepository().apply {
            webQrCode = null
        }
        val viewModel = BiliLoginViewModel(repo)

        viewModel.startLogin()
        advanceUntilIdle()

        assertEquals(1, repo.requestWebQrCodeCalls)
        assertEquals(formatBiliError(BiliErrorCodes.QR_REQUEST_FAILED), viewModel.uiState.value.message)
    }

    @Test
    fun applyCookies_failure_showsCookieError() = runTest {
        val repo = TestBiliRepository().apply {
            applyCookieResult = Result.failure(IllegalArgumentException("missing_cookie:bili_jct"))
        }
        val viewModel = BiliLoginViewModel(repo)

        viewModel.applyCookies("SESSDATA=bad")
        advanceUntilIdle()

        assertEquals("Cookie 缺少 bili_jct", viewModel.uiState.value.message)
    }

    @Test
    fun searchViewModel_loadsAndMutatesSearchHistory() = runTest {
        val repo = TestBiliRepository()
        val viewModel = BiliSearchViewModel(repo)
        advanceUntilIdle()

        assertEquals(listOf("Compose"), viewModel.searchHistory.value)
        assertEquals(listOf("Compose"), viewModel.hotSearchWords.value.map { it.keyword })

        viewModel.addSearchHistory("Kotlin")
        advanceUntilIdle()
        assertEquals(listOf("Kotlin", "Compose"), viewModel.searchHistory.value)

        viewModel.clearSearchHistory()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), viewModel.searchHistory.value)
    }

    @Test
    fun searchViewModel_submitSearch_normalizesQuery_updatesActiveQuery_and_persistsHistory() = runTest {
        val repo = TestBiliRepository()
        val viewModel = BiliSearchViewModel(repo)
        advanceUntilIdle()

        viewModel.updateDraftQuery("  Kotlin  ")
        val action = viewModel.submitSearch()
        advanceUntilIdle()

        assertEquals(BiliSearchSubmitAction.OpenResults, action)
        assertEquals("Kotlin", viewModel.draftQuery.value)
        assertEquals("Kotlin", viewModel.activeQuery.value)
        assertEquals(listOf("Kotlin", "Compose"), viewModel.searchHistory.value)
    }

    @Test
    fun searchViewModel_submitSearch_collapsesInternalWhitespace_beforeSearching() = runTest {
        val repo = TestBiliRepository()
        val viewModel = BiliSearchViewModel(repo)
        advanceUntilIdle()

        viewModel.updateDraftQuery("  hello\u3000 world   compose  ")
        val action = viewModel.submitSearch()
        advanceUntilIdle()

        assertEquals(BiliSearchSubmitAction.OpenResults, action)
        assertEquals("hello world compose", viewModel.draftQuery.value)
        assertEquals("hello world compose", viewModel.activeQuery.value)
        assertEquals(listOf("hello world compose", "Compose"), viewModel.searchHistory.value)
    }

    @Test
    fun searchViewModel_submitSearch_usesExplicitQuery_whenInputStateHasNotSyncedYet() = runTest {
        val repo = TestBiliRepository()
        val viewModel = BiliSearchViewModel(repo)
        advanceUntilIdle()

        viewModel.updateDraftQuery("world")
        val action = viewModel.submitSearch("hello world")
        advanceUntilIdle()

        assertEquals(BiliSearchSubmitAction.OpenResults, action)
        assertEquals("hello world", viewModel.draftQuery.value)
        assertEquals("hello world", viewModel.activeQuery.value)
        assertEquals(listOf("hello world", "Compose"), viewModel.searchHistory.value)
    }

    @Test
    fun searchViewModel_submitSearch_videoId_returnsOpenVideo_withoutChangingActiveQuery() = runTest {
        val repo = TestBiliRepository()
        val viewModel = BiliSearchViewModel(repo)
        advanceUntilIdle()

        viewModel.updateDraftQuery("  bv1xx411c7md  ")
        val action = viewModel.submitSearch()
        advanceUntilIdle()

        assertEquals(
            BiliSearchSubmitAction.OpenVideo(aid = null, bvid = "BV1xx411c7md"),
            action
        )
        assertEquals("BV1xx411c7md", viewModel.draftQuery.value)
        assertEquals("", viewModel.activeQuery.value)
        assertEquals(listOf("Compose"), viewModel.searchHistory.value)
    }

    @Test
    fun searchViewModel_resetSearchSession_clearsDraftAndActiveQuery() = runTest {
        val repo = TestBiliRepository()
        val viewModel = BiliSearchViewModel(repo)
        advanceUntilIdle()

        viewModel.submitSearch("  Kotlin Compose  ")
        advanceUntilIdle()

        viewModel.resetSearchSession()

        assertEquals("", viewModel.draftQuery.value)
        assertEquals("", viewModel.activeQuery.value)
        assertEquals(listOf("Kotlin Compose", "Compose"), viewModel.searchHistory.value)
    }
}
