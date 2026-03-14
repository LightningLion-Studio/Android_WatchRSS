package com.lightningstudio.watchrss.ui.viewmodel

import com.lightningstudio.watchrss.data.rss.AddRssPreview
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestRssRepository
import com.lightningstudio.watchrss.testutil.sampleChannelPreview
import com.lightningstudio.watchrss.testutil.sampleRssChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddRssViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun submit_withBlankUrl_showsValidationError() = runTest {
        val viewModel = AddRssViewModel(TestRssRepository())

        viewModel.submit()

        assertEquals("请输入 RSS 地址", viewModel.uiState.value.errorMessage)
        assertEquals(AddRssStep.INPUT, viewModel.uiState.value.step)
    }

    @Test
    fun submit_readyPreview_thenConfirmAdd_setsCreatedChannel() = runTest {
        val repo = TestRssRepository()
        val preview = sampleChannelPreview()
        repo.previewChannelResult = Result.success(AddRssPreview.Ready(preview))
        repo.confirmAddChannelResult = Result.success(sampleRssChannel(id = 88L, title = "已添加"))
        val viewModel = AddRssViewModel(repo)

        viewModel.updateUrl("https://example.com/feed.xml")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(AddRssStep.PREVIEW, viewModel.uiState.value.step)
        assertEquals(preview.title, viewModel.uiState.value.preview?.title)
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.confirmAdd()
        advanceUntilIdle()

        assertEquals(88L, viewModel.uiState.value.createdChannelId)

        viewModel.consumeCreatedChannel()
        assertNull(viewModel.uiState.value.createdChannelId)
    }

    @Test
    fun submit_existingPreview_and_showQrCode_updateFlow() = runTest {
        val repo = TestRssRepository()
        repo.previewChannelResult = Result.success(
            AddRssPreview.Existing(sampleRssChannel(id = 9L, title = "已存在频道"))
        )
        val viewModel = AddRssViewModel(repo)

        viewModel.updateUrl("https://example.com/feed.xml")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(AddRssStep.EXISTING, viewModel.uiState.value.step)
        assertEquals(9L, viewModel.uiState.value.existingChannel?.id)

        viewModel.backToInput()
        assertEquals(AddRssStep.INPUT, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.existingChannel)

        viewModel.showQrCode("192.168.0.10:8080")
        assertEquals(AddRssStep.QR_CODE, viewModel.uiState.value.step)
        assertEquals("192.168.0.10:8080", viewModel.uiState.value.serverAddress)
    }
}
