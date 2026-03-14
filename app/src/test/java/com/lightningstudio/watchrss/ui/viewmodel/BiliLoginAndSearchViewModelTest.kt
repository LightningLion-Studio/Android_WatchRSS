package com.lightningstudio.watchrss.ui.viewmodel

import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
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
    fun startLogin_successfullyPollsQrState() = runTest {
        val repo = TestBiliRepository().apply {
            qrPollResult = QrPollResult(status = QrPollStatus.SUCCESS, rawCode = 0)
        }
        val viewModel = BiliLoginViewModel(repo)

        viewModel.startLogin()
        advanceUntilIdle()

        assertEquals("test-key", viewModel.uiState.value.qrKey)

        advanceTimeBy(2_100)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isSuccess)
        assertEquals("登录成功", viewModel.uiState.value.message)
    }

    @Test
    fun applyCookies_failure_showsCookieError() = runTest {
        val repo = TestBiliRepository().apply {
            applyCookieResult = Result.failure(IllegalArgumentException("bad cookie"))
        }
        val viewModel = BiliLoginViewModel(repo)

        viewModel.applyCookies("SESSDATA=bad")
        advanceUntilIdle()

        assertEquals("RSS解析失败(-9005)", viewModel.uiState.value.message)
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
}
