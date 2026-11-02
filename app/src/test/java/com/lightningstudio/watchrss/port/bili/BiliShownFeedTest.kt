package com.lightningstudio.watchrss.port.bili

import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.bili.rememberBiliRecommendations
import com.lightningstudio.watchrss.sdk.bili.BiliFeedPage
import com.lightningstudio.watchrss.sdk.bili.BiliFeedSource
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.ui.viewmodel.BiliFeedViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiliShownFeedTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun fetchedAndCachedItems_areNotShownUntilVisibleCallback() = runTest {
        val repo = FeedRepository()
        val model = main.track(BiliFeedViewModel(repo))
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), model.ids())
        assertTrue(repo.history.isEmpty())
        model.rememberShownItems(model.uiState.value.accountGeneration, listOf(item(2)))
        advanceUntilIdle()
        assertEquals(listOf("aid:2"), repo.history["account:1"])
        model.refresh()
        advanceUntilIdle()
        assertEquals(listOf(1L, 3L), model.ids())
        assertEquals(listOf("aid:2"), repo.history["account:1"])
    }

    @Test
    fun cacheAndPaging_filterShownHistory_andDeduplicate() = runTest {
        val repo = FeedRepository().apply {
            history[scope] = listOf("aid:1")
            caches[scope] = listOf(item(1), item(2))
            response = page(1, 2, 3, 3)
        }
        val model = main.track(BiliFeedViewModel(repo))
        advanceUntilIdle()
        assertEquals(listOf(2L, 3L), model.ids())
        repo.response = page(1, 3, 4, 4)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(2L, 3L, 4L), model.ids())
        model.loadMore()
        advanceUntilIdle()
        assertFalse(model.uiState.value.canLoadMore)
        assertEquals(listOf("aid:1"), repo.history["account:1"])
    }

    @Test
    fun newViewModel_filtersPersistentHistory_andKeepsUnshownItems() = runTest {
        val repo = FeedRepository()
        val first = main.track(BiliFeedViewModel(repo))
        advanceUntilIdle()
        first.rememberShownItems(first.uiState.value.accountGeneration, listOf(item(1)))
        advanceUntilIdle()
        val reopened = main.track(BiliFeedViewModel(repo))
        advanceUntilIdle()
        assertEquals(listOf(2L, 3L), reopened.ids())
    }

    @Test
    fun accountSwitch_clearsRows_andRejectsOldVisibilityCallbacks() = runTest {
        val repo = FeedRepository()
        val model = main.track(BiliFeedViewModel(repo))
        advanceUntilIdle()
        val oldGeneration = model.uiState.value.accountGeneration
        model.rememberShownItems(oldGeneration, listOf(item(1)))
        advanceUntilIdle()
        repo.scope = "account:2"
        model.refreshLoginState()
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), model.ids())
        model.rememberShownItems(oldGeneration, listOf(item(2)))
        model.rememberShownItems(model.uiState.value.accountGeneration, listOf(item(999)))
        advanceUntilIdle()
        assertEquals(null, repo.history["account:2"])
        repo.scope = "account:1"
        model.refreshLoginState()
        advanceUntilIdle()
        assertEquals(listOf(2L, 3L), model.ids())
    }

    @Test
    fun inFlightOldAccountResponse_cannotReplaceNewAccountFeedOrCache() = runTest {
        val repo = FeedRepository()
        val model = main.track(BiliFeedViewModel(repo))
        advanceUntilIdle()
        val pending = CompletableDeferred<BiliResult<BiliFeedPage>>()
        repo.pending = pending
        model.loadMore()
        runCurrent()
        repo.scope = "account:2"
        repo.response = page(20, 21)
        model.refreshLoginState()
        runCurrent()
        pending.complete(page(999))
        advanceUntilIdle()
        assertEquals(listOf(20L, 21L), model.ids())
        assertEquals(listOf(20L, 21L), repo.caches["account:2"]?.map { it.aid })
    }

    @Test
    fun logout_clearsRows_andPreservesAccountHistoryForRelogin() = runTest {
        val repo = FeedRepository()
        val model = main.track(BiliFeedViewModel(repo))
        advanceUntilIdle()
        model.rememberShownItems(model.uiState.value.accountGeneration, listOf(item(1)))
        advanceUntilIdle()
        repo.loggedIn = false
        model.refreshLoginState()
        advanceUntilIdle()
        assertTrue(model.uiState.value.items.isEmpty())
        assertFalse(model.uiState.value.isLoggedIn)
        repo.loggedIn = true
        model.refreshLoginState()
        advanceUntilIdle()
        assertEquals(listOf(2L, 3L), model.ids())
    }

    @Test
    fun repeatedOnlyPage_doesNotReinsertShownItems_orAdvanceRecency() = runTest {
        val repo = FeedRepository().apply { history[scope] = listOf("aid:1", "aid:2", "aid:3") }
        val model = main.track(BiliFeedViewModel(repo))
        advanceUntilIdle()
        assertTrue(model.uiState.value.items.isEmpty())
        assertFalse(model.uiState.value.canLoadMore)
        assertEquals(listOf("aid:1", "aid:2", "aid:3"), repo.history[repo.scope])
    }

    @Test
    fun feedFailure_keepsUnseenCache_withoutWritingShownHistory() = runTest {
        val repo = FeedRepository().apply {
            caches[scope] = listOf(item(1), item(2))
            history[scope] = listOf("aid:1")
            response = BiliResult(code = -1, message = "fixture failure")
        }
        val model = main.track(BiliFeedViewModel(repo))
        advanceUntilIdle()
        assertEquals(listOf(2L), model.ids())
        assertEquals(listOf("aid:1"), repo.history[repo.scope])
    }

    private class FeedRepository : BiliRepositoryContract {
        var loggedIn = true
        var scope = "account:1"
        var response = page(1, 2, 3)
        var pending: CompletableDeferred<BiliResult<BiliFeedPage>>? = null
        val history = mutableMapOf<String?, List<String>>()
        val caches = mutableMapOf<String?, List<BiliItem>>()
        override suspend fun isLoggedIn() = loggedIn
        override suspend fun feedAccountScope() = if (loggedIn) scope else null
        override suspend fun readShownRecommendations(scope: String?) = history[scope].orEmpty()
        override suspend fun rememberShownRecommendations(scope: String?, items: List<BiliItem>) {
            history[scope] = rememberBiliRecommendations(history[scope].orEmpty(), items)
        }
        override suspend fun readFeedCacheForAccount(scope: String?) = caches[scope].orEmpty()
        override suspend fun writeFeedCacheForAccount(scope: String?, items: List<BiliItem>) { caches[scope] = items }
        override suspend fun fetchFeed(): BiliResult<BiliFeedPage> {
            val wait = pending
            pending = null
            return wait?.await() ?: response
        }
    }

    private fun BiliFeedViewModel.ids() = uiState.value.items.map { it.aid }

    companion object {
        private fun item(aid: Long) = BiliItem(aid = aid, bvid = "BV$aid")
        private fun page(vararg ids: Long) = BiliResult(
            code = 0, data = BiliFeedPage(ids.map(::item), BiliFeedSource.WEB)
        )
    }
}
