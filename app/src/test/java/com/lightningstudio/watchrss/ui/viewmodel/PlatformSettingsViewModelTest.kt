package com.lightningstudio.watchrss.ui.viewmodel

import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestBiliRepository
import com.lightningstudio.watchrss.testutil.TestDouyinRepository
import com.lightningstudio.watchrss.testutil.TestRssRepository
import com.lightningstudio.watchrss.testutil.sampleRssChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlatformSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun biliSettings_observesChannel_and_handlesLogoutDelete() = runTest {
        val rssRepo = TestRssRepository(
            initialChannels = listOf(
                sampleRssChannel(id = 77L, url = BuiltinChannelType.BILI.url, useOriginalContent = false)
            )
        )
        val biliRepo = TestBiliRepository(initialLoggedIn = true)
        val viewModel = BiliSettingsViewModel(biliRepo, rssRepo)
        advanceUntilIdle()

        viewModel.refreshLoginState()
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.isLoggedIn)
        assertEquals(77L, viewModel.uiState.value.channelId)

        viewModel.toggleOriginalContent()
        advanceUntilIdle()
        assertEquals(listOf(77L to true), rssRepo.setOriginalContentRequests)
        assertEquals(listOf(77L to true), rssRepo.refreshBackgroundRequests)

        viewModel.logout()
        advanceUntilIdle()
        assertEquals(1, biliRepo.logoutCalls)
        assertEquals("已退出登录", viewModel.uiState.value.message)

        viewModel.deleteChannel()
        advanceUntilIdle()
        assertEquals(listOf(77L), rssRepo.deletedChannelIds)
    }

    @Test
    fun douyinSettings_observesChannel_and_clearsCookieOnDelete() = runTest {
        val rssRepo = TestRssRepository(
            initialChannels = listOf(
                sampleRssChannel(id = 88L, url = BuiltinChannelType.DOUYIN.url, useOriginalContent = true)
            )
        )
        val douyinRepo = TestDouyinRepository(initialLoggedIn = true)
        val viewModel = DouyinSettingsViewModel(douyinRepo, rssRepo)
        advanceUntilIdle()

        viewModel.refreshLoginState()
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.isLoggedIn)
        assertEquals(88L, viewModel.uiState.value.channelId)
        assertEquals(true, viewModel.uiState.value.originalContentEnabled)

        viewModel.toggleOriginalContent()
        advanceUntilIdle()
        assertEquals(listOf(88L to false), rssRepo.setOriginalContentRequests)
        assertEquals(listOf(88L to true), rssRepo.refreshBackgroundRequests)

        viewModel.logout()
        advanceUntilIdle()
        assertEquals(1, douyinRepo.logoutCalls)
        assertEquals("已退出登录", viewModel.uiState.value.message)

        viewModel.deleteChannel()
        advanceUntilIdle()
        assertEquals(2, douyinRepo.clearCookieCalls)
        assertEquals(listOf(88L), rssRepo.deletedChannelIds)
    }
}
