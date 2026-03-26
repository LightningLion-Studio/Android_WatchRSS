package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.data.bili.BiliInteractionState
import com.lightningstudio.watchrss.sdk.bili.BiliPage
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestBiliRepository
import com.lightningstudio.watchrss.testutil.TestRssRepository
import com.lightningstudio.watchrss.testutil.sampleBiliItem
import com.lightningstudio.watchrss.testutil.sampleBiliVideoDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiliDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadDetail_startsWarmup_forSelectedCid() = runTest {
        val repo = TestBiliRepository(initialLoggedIn = true).apply {
            val item = sampleBiliItem(aid = 12L, bvid = "BV12", cid = 101L)
            videoDetailResult = BiliResult(
                code = 0,
                data = sampleBiliVideoDetail(
                    item = item,
                    pages = listOf(
                        BiliPage(cid = 101L, page = 1, part = "P1", duration = 30),
                        BiliPage(cid = 202L, page = 2, part = "P2", duration = 40)
                    )
                )
            )
        }

        val viewModel = BiliDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "aid" to "12",
                    "bvid" to "BV12",
                    "cid" to "202"
                )
            ),
            repository = repo,
            rssRepository = TestRssRepository()
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.selectedPageIndex)
        assertEquals(listOf(Triple(12L, "BV12", 202L)), repo.warmupDetailRequests)
    }

    @Test
    fun init_startsWarmupImmediately_fromSavedTargetBeforeDetailSucceeds() = runTest {
        val repo = TestBiliRepository(initialLoggedIn = true).apply {
            videoDetailResult = BiliResult(code = BiliErrorCodes.REQUEST_FAILED)
        }

        BiliDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "aid" to "13",
                    "bvid" to "BV13",
                    "cid" to "303"
                )
            ),
            repository = repo,
            rssRepository = TestRssRepository()
        )
        advanceUntilIdle()

        assertEquals(listOf(Triple(13L, "BV13", 303L)), repo.warmupDetailRequests)
    }

    @Test
    fun selectPage_restartsWarmup_forNewCid() = runTest {
        val repo = TestBiliRepository(initialLoggedIn = true).apply {
            val item = sampleBiliItem(aid = 22L, bvid = "BV22", cid = 301L)
            videoDetailResult = BiliResult(
                code = 0,
                data = sampleBiliVideoDetail(
                    item = item,
                    pages = listOf(
                        BiliPage(cid = 301L, page = 1, part = "P1", duration = 30),
                        BiliPage(cid = 302L, page = 2, part = "P2", duration = 40)
                    )
                )
            )
        }
        val viewModel = BiliDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("aid" to "22", "bvid" to "BV22")),
            repository = repo,
            rssRepository = TestRssRepository()
        )
        advanceUntilIdle()

        viewModel.selectPage(1)
        advanceUntilIdle()

        assertEquals(
            listOf(
                Triple(22L, "BV22", 301L),
                Triple(22L, "BV22", 302L)
            ),
            repo.warmupDetailRequests
        )
    }

    @Test
    fun loadDetail_restoresPersistedLikeAndCoinState() = runTest {
        val repo = TestBiliRepository(initialLoggedIn = true).apply {
            val item = sampleBiliItem(aid = 31L, bvid = "BV31", cid = 401L)
            videoDetailResult = BiliResult(code = 0, data = sampleBiliVideoDetail(item))
            localInteractionStates["bv:BV31"] = BiliInteractionState(
                isLiked = true,
                isCoined = true
            )
        }

        val viewModel = BiliDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("aid" to "31", "bvid" to "BV31")),
            repository = repo,
            rssRepository = TestRssRepository()
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLiked)
        assertTrue(viewModel.uiState.value.isCoined)
    }

    @Test
    fun like_positive_updatesUiImmediately_and_runsActionWithoutWarmupGate() = runTest {
        val repo = TestBiliRepository(initialLoggedIn = true).apply {
            val item = sampleBiliItem(aid = 55L, bvid = "BV55", cid = 77L)
            videoDetailResult = BiliResult(code = 0, data = sampleBiliVideoDetail(item))
        }
        val viewModel = BiliDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("aid" to "55", "bvid" to "BV55", "cid" to "77")),
            repository = repo,
            rssRepository = TestRssRepository()
        )
        advanceUntilIdle()

        viewModel.like()

        assertTrue(viewModel.uiState.value.isLiked)
        assertEquals("已点赞", viewModel.uiState.value.message)

        advanceUntilIdle()

        assertEquals(listOf(Pair(55L, true)), repo.likeRequests)
        assertTrue(repo.ensureInteractionRequests.isEmpty())
        assertEquals(
            BiliInteractionState(isLiked = true, isCoined = false),
            repo.localInteractionStates["bv:BV55"]
        )
        assertEquals(
            listOf(
                "warmup:55:BV55:77",
                "like:55:true"
            ),
            repo.callLog
        )
    }

    @Test
    fun coin_failure_keepsOptimisticState_and_stillShowsSuccessMessage() = runTest {
        val repo = TestBiliRepository(initialLoggedIn = true).apply {
            val item = sampleBiliItem(aid = 88L, bvid = "BV88", cid = 99L)
            videoDetailResult = BiliResult(code = 0, data = sampleBiliVideoDetail(item))
            coinResult = BiliResult(code = BiliErrorCodes.REQUEST_FAILED)
        }
        val viewModel = BiliDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("aid" to "88", "bvid" to "BV88", "cid" to "99")),
            repository = repo,
            rssRepository = TestRssRepository()
        )
        advanceUntilIdle()

        viewModel.coin()

        assertTrue(viewModel.uiState.value.isCoined)
        assertEquals("已投币", viewModel.uiState.value.message)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCoined)
        assertEquals("已投币", viewModel.uiState.value.message)
        assertTrue(repo.ensureInteractionRequests.isEmpty())
        assertEquals(listOf(Triple(88L, 1, false)), repo.coinRequests)
    }

    @Test
    fun coin_success_persistsCoinState_forFutureOpen() = runTest {
        val repo = TestBiliRepository(initialLoggedIn = true).apply {
            val item = sampleBiliItem(aid = 91L, bvid = "BV91", cid = 191L)
            videoDetailResult = BiliResult(code = 0, data = sampleBiliVideoDetail(item))
            coinResult = BiliResult(code = 0, data = false)
        }
        val viewModel = BiliDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("aid" to "91", "bvid" to "BV91", "cid" to "191")),
            repository = repo,
            rssRepository = TestRssRepository()
        )
        advanceUntilIdle()

        viewModel.coin()
        advanceUntilIdle()

        assertEquals(
            BiliInteractionState(isLiked = false, isCoined = true),
            repo.localInteractionStates["bv:BV91"]
        )
    }

    @Test
    fun like_failure_keepsOptimisticState_and_stillShowsSuccessMessage() = runTest {
        val repo = TestBiliRepository(initialLoggedIn = true).apply {
            val item = sampleBiliItem(aid = 77L, bvid = "BV77", cid = 177L)
            videoDetailResult = BiliResult(code = 0, data = sampleBiliVideoDetail(item))
            likeResult = BiliResult(code = BiliErrorCodes.REQUEST_FAILED)
        }
        val viewModel = BiliDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("aid" to "77", "bvid" to "BV77", "cid" to "177")),
            repository = repo,
            rssRepository = TestRssRepository()
        )
        advanceUntilIdle()

        viewModel.like()

        assertTrue(viewModel.uiState.value.isLiked)
        assertEquals("已点赞", viewModel.uiState.value.message)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLiked)
        assertEquals("已点赞", viewModel.uiState.value.message)
        assertTrue(repo.ensureInteractionRequests.isEmpty())
        assertEquals(listOf(Pair(77L, true)), repo.likeRequests)
    }

    @Test
    fun unlike_staysOptimistic_and_doesNotRequireWarmup() = runTest {
        val repo = TestBiliRepository(initialLoggedIn = true).apply {
            val item = sampleBiliItem(aid = 66L, bvid = "BV66", cid = 166L)
            videoDetailResult = BiliResult(code = 0, data = sampleBiliVideoDetail(item))
        }
        val viewModel = BiliDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("aid" to "66", "bvid" to "BV66", "cid" to "166")),
            repository = repo,
            rssRepository = TestRssRepository()
        )
        advanceUntilIdle()

        viewModel.like()
        advanceUntilIdle()
        repo.ensureInteractionRequests.clear()
        repo.likeRequests.clear()
        repo.callLog.clear()
        repo.likeResult = BiliResult(code = BiliErrorCodes.REQUEST_FAILED)

        viewModel.like()
        advanceUntilIdle()

        assertEquals(listOf(Pair(66L, false)), repo.likeRequests)
        assertTrue(repo.ensureInteractionRequests.isEmpty())
        assertEquals("已取消点赞", viewModel.uiState.value.message)
        assertEquals(listOf("like:66:false"), repo.callLog)
        assertTrue(!viewModel.uiState.value.isLiked)
    }

    @Test
    fun unlike_success_clearsPersistedLikeState() = runTest {
        val repo = TestBiliRepository(initialLoggedIn = true).apply {
            val item = sampleBiliItem(aid = 67L, bvid = "BV67", cid = 167L)
            videoDetailResult = BiliResult(code = 0, data = sampleBiliVideoDetail(item))
        }
        val viewModel = BiliDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("aid" to "67", "bvid" to "BV67", "cid" to "167")),
            repository = repo,
            rssRepository = TestRssRepository()
        )
        advanceUntilIdle()

        viewModel.like()
        advanceUntilIdle()
        assertEquals(
            BiliInteractionState(isLiked = true, isCoined = false),
            repo.localInteractionStates["bv:BV67"]
        )

        viewModel.like()
        advanceUntilIdle()

        assertNull(repo.localInteractionStates["bv:BV67"])
    }
}
