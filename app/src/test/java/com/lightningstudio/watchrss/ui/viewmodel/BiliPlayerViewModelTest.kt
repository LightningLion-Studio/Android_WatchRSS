package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.data.bili.BiliPlaybackCheckpointTrigger
import com.lightningstudio.watchrss.data.bili.BiliPlaybackProgress
import com.lightningstudio.watchrss.data.bili.BiliResolvedPlaybackSource
import com.lightningstudio.watchrss.data.bili.formatBiliError
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import com.lightningstudio.watchrss.sdk.bili.BiliOwner
import com.lightningstudio.watchrss.sdk.bili.BiliPage
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.sdk.bili.BiliVideoDetail
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestBiliRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiliPlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadPlayUrl_usesPreparedPlaybackSource_whenWarmSourceExists() = runTest {
        val repo = TestBiliRepository().apply {
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(BiliPage(cid = 34L, part = "P1"))
                )
            )
            resolvedPlaybackSourceValue = BiliResolvedPlaybackSource(
                cid = 34,
                url = "https://example.com/full.mp4",
                headers = mapOf(
                    "User-Agent" to "WarmUA",
                    "Referer" to "https://www.bilibili.com"
                ),
                cacheKey = "bili:bv:BV12:34:q64",
                quality = 64
            )
            playbackProgressRecords += BiliPlaybackProgress(
                aid = 12L,
                bvid = "BV12",
                cid = 34L,
                positionMs = 12_345L,
                durationMs = 60_000L,
                updatedAtMillis = 10L
            )
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            BiliPlaybackSource(
                url = "https://example.com/full.mp4",
                headers = repo.resolvedPlaybackSourceValue!!.headers,
                kind = BiliPlaybackSourceKind.REMOTE,
                cacheKey = "bili:bv:BV12:34:q64"
            ),
            state.initialSource
        )
        assertNull(state.upgradeSource)
        assertFalse(state.isLoading)
        assertFalse(state.isUpgradeLoading)
        assertEquals(12_345, state.resumePositionMs)
        assertNull(state.message)
        assertNull(state.upgradeErrorMessage)
        assertEquals(listOf("resolve:34:12:BV12:32"), repo.callLog)
        assertNoPreviewCalls(repo)
        assertEquals(listOf(Triple(12L, "BV12", 34L)), repo.exactPlaybackProgressReadRequests)
    }

    @Test
    fun loadPlayUrl_setsMessage_whenPlayUrlRequestFails() = runTest {
        val repo = TestBiliRepository().apply {
            playUrlResult = BiliResult(code = BiliErrorCodes.REQUEST_FAILED, message = "request_failed")
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(
                        BiliPage(cid = 34L, part = "P1"),
                        BiliPage(cid = 56L, part = "P2")
                    )
                )
            )
            playbackProgressRecords += BiliPlaybackProgress(
                aid = 12L,
                bvid = "BV12",
                cid = 56L,
                positionMs = 7_000L,
                durationMs = 60_000L,
                updatedAtMillis = 20L
            )
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.initialSource)
        assertNull(state.upgradeSource)
        assertFalse(state.isLoading)
        assertEquals(
            formatBiliError(BiliErrorCodes.REQUEST_FAILED, "request_failed"),
            state.message
        )
        assertNull(state.upgradeErrorMessage)
        assertEquals(listOf("resolve:34:12:BV12:32", "resolve:34:12:BV12:32"), repo.callLog)
        assertNoPreviewCalls(repo)
    }

    @Test
    fun loadPlayUrl_retriesSameTargetOnce_whenInitialRequestFails() = runTest {
        val repo = TestBiliRepository().apply {
            resolvedPlaybackSourceResultQueueByCid[34L] = mutableListOf(
                BiliResult(
                    code = BiliErrorCodes.REQUEST_FAILED,
                    message = "request_failed"
                ),
                BiliResult(
                    code = 0,
                    data = BiliResolvedPlaybackSource(
                        cid = 34L,
                        url = "https://example.com/retry-success.mp4",
                        headers = playHeaders,
                        cacheKey = "bili:bv:BV12:34:q32",
                        quality = 32
                    )
                )
            )
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(
                        BiliPage(cid = 34L, part = "P1"),
                        BiliPage(cid = 56L, part = "P2")
                    )
                )
            )
        }

        val viewModel = createViewModel(repository = repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            BiliPlaybackSource(
                url = "https://example.com/retry-success.mp4",
                headers = repo.playHeaders,
                kind = BiliPlaybackSourceKind.REMOTE,
                cacheKey = "bili:bv:BV12:34:q32"
            ),
            state.initialSource
        )
        assertFalse(state.isLoading)
        assertNull(state.message)
        assertEquals(listOf("resolve:34:12:BV12:32", "resolve:34:12:BV12:32"), repo.callLog)
    }

    @Test
    fun loadPlayUrl_recoversWithCorrectedCid_whenInitialCidFails() = runTest {
        val repo = TestBiliRepository().apply {
            resolvedPlaybackSourceResultsByCid[34L] = BiliResult(
                code = 0,
                data = BiliResolvedPlaybackSource(
                    cid = 34L,
                    url = "https://example.com/recovered.mp4",
                    headers = playHeaders,
                    cacheKey = "bili:bv:BV12:34:q32",
                    quality = 32
                )
            )
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(
                        BiliPage(cid = 34L, part = "P1"),
                        BiliPage(cid = 56L, part = "P2")
                    )
                )
            )
        }

        val viewModel = createViewModel(repository = repo, cid = "999")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            BiliPlaybackSource(
                url = "https://example.com/recovered.mp4",
                headers = repo.playHeaders,
                kind = BiliPlaybackSourceKind.REMOTE,
                cacheKey = "bili:bv:BV12:34:q32"
            ),
            state.initialSource
        )
        assertEquals("标题", state.title)
        assertEquals("UP主", state.owner)
        assertEquals("P1", state.pageTitle)
        assertEquals(listOf("resolve:34:12:BV12:32"), repo.callLog)
        assertEquals(listOf(12L to "BV12"), repo.latestPlaybackProgressReadRequests)
        assertEquals(listOf(Triple(12L, "BV12", 34L)), repo.exactPlaybackProgressReadRequests)
        assertNull(state.message)
    }

    @Test
    fun loadPlayUrl_recoveryPrefersLatestPlaybackCid_whenAvailable() = runTest {
        val repo = TestBiliRepository().apply {
            resolvedPlaybackSourceResultsByCid[56L] = BiliResult(
                code = 0,
                data = BiliResolvedPlaybackSource(
                    cid = 56L,
                    url = "https://example.com/resume.mp4",
                    headers = playHeaders,
                    cacheKey = "bili:bv:BV12:56:q32",
                    quality = 32
                )
            )
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(
                        BiliPage(cid = 34L, part = "P1"),
                        BiliPage(cid = 56L, part = "P2")
                    )
                )
            )
            playbackProgressRecords += BiliPlaybackProgress(
                aid = 12L,
                bvid = "BV12",
                cid = 56L,
                positionMs = 4_321L,
                durationMs = 60_000L,
                updatedAtMillis = 20L
            )
        }

        val viewModel = createViewModel(repository = repo, cid = "999")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("P2", state.pageTitle)
        assertEquals(4_321, state.resumePositionMs)
        assertEquals(listOf("resolve:56:12:BV12:32"), repo.callLog)
        assertEquals(listOf(Triple(12L, "BV12", 56L)), repo.exactPlaybackProgressReadRequests)
        assertNull(state.message)
    }

    @Test
    fun loadPlayUrl_keepsLoadingWithoutMessage_whileRecoveryIsInFlight() = runTest {
        val repo = TestBiliRepository().apply {
            resolvedPlaybackSourceResultsByCid[34L] = BiliResult(
                code = 0,
                data = BiliResolvedPlaybackSource(
                    cid = 34L,
                    url = "https://example.com/recovered.mp4",
                    headers = playHeaders,
                    cacheKey = "bili:bv:BV12:34:q32",
                    quality = 32
                )
            )
            videoDetailDelayMs = 1L
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(BiliPage(cid = 34L, part = "P1"))
                )
            )
        }

        val viewModel = createViewModel(repository = repo, cid = "999")
        runCurrent()

        val loadingState = viewModel.uiState.value
        assertTrue(loadingState.isLoading)
        assertNull(loadingState.message)
        assertNull(loadingState.initialSource)
        assertTrue(repo.callLog.isEmpty())

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.message)
        assertEquals(listOf("resolve:34:12:BV12:32"), repo.callLog)
    }

    @Test
    fun loadPlayUrl_keepsLoadingWithoutMessage_whileAutoRetryRunsForRequestFailure() = runTest {
        val repo = TestBiliRepository().apply {
            resolvedPlaybackSourceResultQueueByCid[34L] = mutableListOf(
                BiliResult(
                    code = BiliErrorCodes.REQUEST_FAILED,
                    message = "request_failed"
                ),
                BiliResult(
                    code = 0,
                    data = BiliResolvedPlaybackSource(
                        cid = 34L,
                        url = "https://example.com/retry-success.mp4",
                        headers = playHeaders,
                        cacheKey = "bili:bv:BV12:34:q32",
                        quality = 32
                    )
                )
            )
            videoDetailDelayMs = 1L
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(BiliPage(cid = 34L, part = "P1"))
                )
            )
        }

        val viewModel = createViewModel(repository = repo)
        runCurrent()

        val loadingState = viewModel.uiState.value
        assertTrue(loadingState.isLoading)
        assertNull(loadingState.message)
        assertNull(loadingState.initialSource)
        assertTrue(repo.callLog.isEmpty())

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.message)
        assertEquals(listOf("resolve:34:12:BV12:32", "resolve:34:12:BV12:32"), repo.callLog)
    }

    @Test
    fun onPlaybackError_recoversWithCorrectedCid_whenCurrentCidIsInvalid() = runTest {
        val repo = TestBiliRepository().apply {
            resolvedPlaybackSourceResultsByCid[999L] = BiliResult(
                code = 0,
                data = BiliResolvedPlaybackSource(
                    cid = 999L,
                    url = "https://example.com/bad.mp4",
                    headers = playHeaders,
                    cacheKey = "bili:bv:BV12:999:q32",
                    quality = 32
                )
            )
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 999L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(BiliPage(cid = 999L, part = "旧P"))
                )
            )
        }

        val viewModel = createViewModel(repository = repo, cid = "999")
        advanceUntilIdle()

        repo.videoDetailResult = BiliResult(
            code = 0,
            data = BiliVideoDetail(
                item = BiliItem(
                    aid = 12L,
                    bvid = "BV12",
                    cid = 34L,
                    title = "标题",
                    owner = BiliOwner(name = "UP主")
                ),
                pages = listOf(
                    BiliPage(cid = 34L, part = "P1"),
                    BiliPage(cid = 56L, part = "P2")
                )
            )
        )
        repo.resolvedPlaybackSourceResultsByCid[34L] = BiliResult(
            code = 0,
            data = BiliResolvedPlaybackSource(
                cid = 34L,
                url = "https://example.com/recovered.mp4",
                headers = repo.playHeaders,
                cacheKey = "bili:bv:BV12:34:q32",
                quality = 32
            )
        )

        assertTrue(viewModel.onPlaybackError())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            BiliPlaybackSource(
                url = "https://example.com/recovered.mp4",
                headers = repo.playHeaders,
                kind = BiliPlaybackSourceKind.REMOTE,
                cacheKey = "bili:bv:BV12:34:q32"
            ),
            state.initialSource
        )
        assertEquals("P1", state.pageTitle)
        assertNull(state.message)
        assertEquals(listOf("resolve:999:12:BV12:32", "resolve:34:12:BV12:32"), repo.callLog)
    }

    @Test
    fun onPlaybackError_setsMessage_whenCurrentCidIsAlreadyValid() = runTest {
        val repo = TestBiliRepository().apply {
            resolvedPlaybackSourceResultsByCid[34L] = BiliResult(
                code = 0,
                data = BiliResolvedPlaybackSource(
                    cid = 34L,
                    url = "https://example.com/current.mp4",
                    headers = playHeaders,
                    cacheKey = "bili:bv:BV12:34:q32",
                    quality = 32
                )
            )
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(
                        BiliPage(cid = 34L, part = "P1"),
                        BiliPage(cid = 56L, part = "P2")
                    )
                )
            )
        }

        val viewModel = createViewModel(repository = repo)
        advanceUntilIdle()

        assertTrue(viewModel.onPlaybackError())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.initialSource)
        assertFalse(state.isLoading)
        assertEquals("播放失败", state.message)
        assertEquals(listOf("resolve:34:12:BV12:32"), repo.callLog)
    }

    @Test
    fun onPlaybackError_keepsLoadingWithoutMessage_whileAutoRecoveryRuns() = runTest {
        val repo = TestBiliRepository().apply {
            resolvedPlaybackSourceResultsByCid[999L] = BiliResult(
                code = 0,
                data = BiliResolvedPlaybackSource(
                    cid = 999L,
                    url = "https://example.com/bad.mp4",
                    headers = playHeaders,
                    cacheKey = "bili:bv:BV12:999:q32",
                    quality = 32
                )
            )
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 999L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(BiliPage(cid = 999L, part = "旧P"))
                )
            )
        }

        val viewModel = createViewModel(repository = repo, cid = "999")
        advanceUntilIdle()

        repo.videoDetailDelayMs = 1L
        repo.videoDetailResult = BiliResult(
            code = 0,
            data = BiliVideoDetail(
                item = BiliItem(
                    aid = 12L,
                    bvid = "BV12",
                    cid = 34L,
                    title = "标题",
                    owner = BiliOwner(name = "UP主")
                ),
                pages = listOf(BiliPage(cid = 34L, part = "P1"))
            )
        )
        repo.resolvedPlaybackSourceResultsByCid[34L] = BiliResult(
            code = 0,
            data = BiliResolvedPlaybackSource(
                cid = 34L,
                url = "https://example.com/recovered.mp4",
                headers = repo.playHeaders,
                cacheKey = "bili:bv:BV12:34:q32",
                quality = 32
            )
        )

        assertTrue(viewModel.onPlaybackError())
        runCurrent()

        val loadingState = viewModel.uiState.value
        assertTrue(loadingState.isLoading)
        assertNull(loadingState.message)
        assertNull(loadingState.initialSource)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.message)
        assertEquals(listOf("resolve:999:12:BV12:32", "resolve:34:12:BV12:32"), repo.callLog)
    }

    @Test
    fun loadPlayUrl_resolvesCidAndUsesResolvedIdsForCacheKey() = runTest {
        val repo = TestBiliRepository().apply {
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 77L,
                        bvid = "BV77",
                        cid = 88L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(
                        BiliPage(cid = 88L, part = "P1")
                    )
                )
            )
            resolvedPlaybackSourceValue = BiliResolvedPlaybackSource(
                cid = 88L,
                url = "https://example.com/resolved.mp4",
                headers = playHeaders,
                cacheKey = "bili:bv:BV77:88:q32",
                quality = 32
            )
        }

        val viewModel = createViewModel(
            repository = repo,
            aid = "77",
            bvid = "BV77",
            cid = ""
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            BiliPlaybackSource(
                url = "https://example.com/resolved.mp4",
                headers = repo.playHeaders,
                kind = BiliPlaybackSourceKind.REMOTE,
                cacheKey = "bili:bv:BV77:88:q32"
            ),
            state.initialSource
        )
        assertEquals("标题", state.title)
        assertEquals("UP主", state.owner)
        assertEquals("P1", state.pageTitle)
        assertEquals(listOf("resolve:88:77:BV77:32"), repo.callLog)
        assertNoPreviewCalls(repo)
    }

    @Test
    fun onPlaybackProgress_throttlesPeriodicWrites_andForcePersistsImmediately() = runTest {
        val repo = TestBiliRepository().apply {
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(BiliPage(cid = 34L, part = "P1"))
                )
            )
            resolvedPlaybackSourceValue = BiliResolvedPlaybackSource(
                cid = 34L,
                url = "https://example.com/full.mp4",
                headers = playHeaders,
                cacheKey = "bili:bv:BV12:34:q32",
                quality = 32
            )
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        viewModel.onPlaybackProgress(positionMs = 4_000, durationMs = 60_000, force = false)
        viewModel.onPlaybackProgress(positionMs = 6_000, durationMs = 60_000, force = false)
        viewModel.onPlaybackProgress(positionMs = 6_500, durationMs = 60_000, force = false)
        viewModel.onPlaybackProgress(positionMs = 6_500, durationMs = 60_000, force = true)
        advanceUntilIdle()

        assertEquals(listOf(6_000L, 6_500L), repo.playbackProgressWrites.map { it.positionMs })
        assertEquals(listOf(34L, 34L), repo.playbackProgressWrites.map { it.cid })
    }

    @Test
    fun onPlaybackReady_reportsHistoryImmediately() = runTest {
        val repo = TestBiliRepository().apply {
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(BiliPage(cid = 34L, part = "P1"))
                )
            )
            resolvedPlaybackSourceValue = BiliResolvedPlaybackSource(
                cid = 34L,
                url = "https://example.com/full.mp4",
                headers = playHeaders,
                cacheKey = "bili:bv:BV12:34:q32",
                quality = 32
            )
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        viewModel.onPlaybackReady(positionMs = 0, durationMs = 60_000)
        advanceUntilIdle()

        assertEquals(1, repo.reportPlaybackHistoryRequests.size)
        assertEquals(BiliPlaybackCheckpointTrigger.READY, repo.reportPlaybackHistoryRequests.single().trigger)
        assertEquals(0L, repo.reportPlaybackHistoryRequests.single().positionMs)
        assertEquals(60_000L, repo.reportPlaybackHistoryRequests.single().durationMs)
    }

    @Test
    fun onPlaybackProgress_reportsRemotelyEveryTwoMinutes() = runTest {
        val repo = TestBiliRepository().apply {
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(BiliPage(cid = 34L, part = "P1"))
                )
            )
            resolvedPlaybackSourceValue = BiliResolvedPlaybackSource(
                cid = 34L,
                url = "https://example.com/full.mp4",
                headers = playHeaders,
                cacheKey = "bili:bv:BV12:34:q32",
                quality = 32
            )
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        viewModel.onPlaybackReady(positionMs = 0, durationMs = 600_000)
        advanceUntilIdle()

        viewModel.onPlaybackProgress(positionMs = 119_000, durationMs = 600_000, force = false)
        advanceUntilIdle()
        assertEquals(listOf(0L), repo.reportPlaybackHistoryRequests.map { it.positionMs })

        viewModel.onPlaybackProgress(positionMs = 120_000, durationMs = 600_000, force = false)
        advanceUntilIdle()
        viewModel.onPlaybackProgress(positionMs = 240_000, durationMs = 600_000, force = false)
        advanceUntilIdle()

        assertEquals(
            listOf(0L, 120_000L, 240_000L),
            repo.reportPlaybackHistoryRequests.map { it.positionMs }
        )
        assertEquals(
            listOf(
                BiliPlaybackCheckpointTrigger.READY,
                BiliPlaybackCheckpointTrigger.TICK,
                BiliPlaybackCheckpointTrigger.TICK
            ),
            repo.reportPlaybackHistoryRequests.map { it.trigger }
        )
    }

    @Test
    fun onPlaybackPauseOrExit_reportsLatestProgressBeforeTwoMinuteBoundary() = runTest {
        val repo = TestBiliRepository().apply {
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(BiliPage(cid = 34L, part = "P1"))
                )
            )
            resolvedPlaybackSourceValue = BiliResolvedPlaybackSource(
                cid = 34L,
                url = "https://example.com/full.mp4",
                headers = playHeaders,
                cacheKey = "bili:bv:BV12:34:q32",
                quality = 32
            )
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        viewModel.onPlaybackReady(positionMs = 0, durationMs = 600_000)
        advanceUntilIdle()
        viewModel.onPlaybackPauseOrExit(positionMs = 30_000, durationMs = 600_000)
        advanceUntilIdle()

        assertEquals(
            listOf(0L, 30_000L),
            repo.reportPlaybackHistoryRequests.map { it.positionMs }
        )
        assertEquals(
            listOf(
                BiliPlaybackCheckpointTrigger.READY,
                BiliPlaybackCheckpointTrigger.PAUSE_OR_EXIT
            ),
            repo.reportPlaybackHistoryRequests.map { it.trigger }
        )
    }

    @Test
    fun onCleared_drainsPendingRemoteHistoryReport() = runTest {
        val repo = TestBiliRepository().apply {
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(BiliPage(cid = 34L, part = "P1"))
                )
            )
            resolvedPlaybackSourceValue = BiliResolvedPlaybackSource(
                cid = 34L,
                url = "https://example.com/full.mp4",
                headers = playHeaders,
                cacheKey = "bili:bv:BV12:34:q32",
                quality = 32
            )
            reportPlaybackHistoryDelayMs = 1_000L
        }
        val remoteReportScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

        val viewModel = createViewModel(repo, remoteReportScope = remoteReportScope)
        advanceUntilIdle()

        viewModel.onPlaybackReady(positionMs = 0, durationMs = 600_000)
        runCurrent()
        viewModel.onPlaybackPauseOrExit(positionMs = 30_000, durationMs = 600_000)
        runCurrent()
        clearViewModel(viewModel)
        advanceUntilIdle()
        remoteReportScope.cancel()

        assertEquals(
            listOf(
                BiliPlaybackCheckpointTrigger.READY,
                BiliPlaybackCheckpointTrigger.PAUSE_OR_EXIT
            ),
            repo.reportPlaybackHistoryRequests.map { it.trigger }
        )
        assertEquals(
            listOf(0L, 30_000L),
            repo.reportPlaybackHistoryRequests.map { it.positionMs }
        )
    }

    @Test
    fun onPlaybackEnded_reportsFinalProgressAfterReadySync() = runTest {
        val repo = TestBiliRepository().apply {
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(BiliPage(cid = 34L, part = "P1"))
                )
            )
            resolvedPlaybackSourceValue = BiliResolvedPlaybackSource(
                cid = 34L,
                url = "https://example.com/full.mp4",
                headers = playHeaders,
                cacheKey = "bili:bv:BV12:34:q32",
                quality = 32
            )
            reportPlaybackHistoryResult = BiliResult(code = 0, data = Unit)
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        viewModel.onPlaybackReady(positionMs = 0, durationMs = 60_000)
        runCurrent()

        viewModel.onPlaybackEnded(positionMs = 59_000, durationMs = 60_000)
        advanceUntilIdle()

        assertEquals(
            listOf(0L, 60_000L),
            repo.reportPlaybackHistoryRequests.map { it.positionMs }
        )
        assertEquals(
            listOf(
                BiliPlaybackCheckpointTrigger.READY,
                BiliPlaybackCheckpointTrigger.ENDED
            ),
            repo.reportPlaybackHistoryRequests.map { it.trigger }
        )
    }

    @Test
    fun onPlaybackEnded_clearsExactProgress_andResetsResumePosition() = runTest {
        val repo = TestBiliRepository().apply {
            videoDetailResult = BiliResult(
                code = 0,
                data = BiliVideoDetail(
                    item = BiliItem(
                        aid = 12L,
                        bvid = "BV12",
                        cid = 34L,
                        title = "标题",
                        owner = BiliOwner(name = "UP主")
                    ),
                    pages = listOf(BiliPage(cid = 34L, part = "P1"))
                )
            )
            resolvedPlaybackSourceValue = BiliResolvedPlaybackSource(
                cid = 34L,
                url = "https://example.com/full.mp4",
                headers = playHeaders,
                cacheKey = "bili:bv:BV12:34:q32",
                quality = 32
            )
            playbackProgressRecords += BiliPlaybackProgress(
                aid = 12L,
                bvid = "BV12",
                cid = 34L,
                positionMs = 9_000L,
                durationMs = 60_000L,
                updatedAtMillis = 10L
            )
        }

        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        viewModel.onPlaybackEnded(positionMs = 59_000, durationMs = 60_000)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.resumePositionMs)
        assertEquals(listOf(Triple(12L, "BV12", 34L)), repo.clearedPlaybackProgressRequests)
        assertEquals(listOf(BiliPlaybackCheckpointTrigger.ENDED), repo.reportPlaybackHistoryRequests.map { it.trigger })
    }

    private fun createViewModel(
        repository: TestBiliRepository,
        aid: String = "12",
        bvid: String = "BV12",
        cid: String = "34",
        remoteReportScope: CoroutineScope? = null
    ): BiliPlayerViewModel {
        return BiliPlayerViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "aid" to aid,
                    "bvid" to bvid,
                    "cid" to cid
                )
            ),
            repository = repository,
            detachedRemoteReportScope = remoteReportScope
        )
    }

    private fun clearViewModel(viewModel: BiliPlayerViewModel) {
        val clearMethod = (ViewModel::class.java.declaredMethods + ViewModel::class.java.methods)
            .firstOrNull { method ->
                method.parameterCount == 0 && method.name.contains("clear")
            }
            ?: throw NoSuchMethodException("ViewModel clear method not found")
        clearMethod.isAccessible = true
        clearMethod.invoke(viewModel)
    }

    private fun assertNoPreviewCalls(repository: TestBiliRepository) {
        assertFalse(repository.callLog.any { it.startsWith("cached:") })
        assertFalse(repository.callLog.any { it.startsWith("cachedAny:") })
        assertFalse(repository.callLog.any { it.startsWith("clearcache:") })
    }
}
