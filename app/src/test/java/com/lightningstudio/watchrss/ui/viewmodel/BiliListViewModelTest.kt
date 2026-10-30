package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.sdk.bili.BiliFavoriteFolder
import com.lightningstudio.watchrss.sdk.bili.BiliFavoriteMedia
import com.lightningstudio.watchrss.sdk.bili.BiliFavoritePage
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiliListViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Repository : BiliRepositoryContract {
        var folders = BiliResult(code = 0, data = listOf(
            BiliFavoriteFolder(id = 1, title = "收藏一"), BiliFavoriteFolder(fid = 2, title = "收藏二")
        ))
        val requests = mutableListOf<Pair<Long, Int>>()
        var page: suspend (Long, Int) -> BiliResult<BiliFavoritePage> = { id, pn -> success(id, pn) }
        override suspend fun fetchFavoriteFolders() = folders
        override suspend fun fetchFavoriteItems(mediaId: Long, pn: Int, ps: Int): BiliResult<BiliFavoritePage> {
            requests += mediaId to pn
            return page(mediaId, pn)
        }
    }

    @Test fun foldersRetainTheirOwnPagesAndSwitchBackWithoutRefetching() = runTest {
        val repo = Repository()
        val vm = mainDispatcherRule.track(BiliListViewModel(SavedStateHandle(mapOf("type" to "favorite")), repo))
        try {
            advanceUntilIdle()
            assertEquals(listOf("收藏一", "收藏二"), vm.uiState.value.favoriteFolders.map { it.title })
            vm.loadMore()
            vm.loadMore() // Repeated near-end events must not start another page request.
            advanceUntilIdle()
            vm.selectFavoriteFolder(2)
            advanceUntilIdle()
            assertEquals(listOf("2:1"), vm.uiState.value.items.map { it.title })
            vm.selectFavoriteFolder(1)
            assertEquals(listOf("1:1", "1:2"), vm.uiState.value.items.map { it.title })
            vm.loadMore()
            advanceUntilIdle()
            assertEquals(listOf(1L to 1, 1L to 2, 2L to 1, 1L to 3), repo.requests)
        } finally { vm.viewModelScope.cancel() }
    }

    @Test fun aLateResultFromAnotherFolderCannotOverwriteTheSelectedFolderOrItsCache() = runTest {
        val repo = Repository()
        var pending: Continuation<BiliResult<BiliFavoritePage>>? = null
        repo.page = { id, pn ->
            if (id == 2L && pending == null) suspendCoroutine { pending = it } else success(id, pn)
        }
        val vm = mainDispatcherRule.track(BiliListViewModel(SavedStateHandle(mapOf("type" to "favorite")), repo))
        try {
            advanceUntilIdle()
            vm.selectFavoriteFolder(2)
            runCurrent()
            vm.selectFavoriteFolder(1)
            pending!!.resume(success(2, 1))
            advanceUntilIdle()
            assertEquals(1L, vm.uiState.value.selectedFavoriteFolderId)
            assertEquals(listOf("1:1"), vm.uiState.value.items.map { it.title })
            vm.selectFavoriteFolder(2)
            advanceUntilIdle()
            assertEquals(2, repo.requests.count { it.first == 2L })
            assertEquals(listOf("2:1"), vm.uiState.value.items.map { it.title })
        } finally { vm.viewModelScope.cancel() }
    }

    @Test fun aFailedPageIsRetriedAtTheSamePageAndRemainsVisibleAfterToastDismissal() = runTest {
        val repo = Repository()
        var fail = true
        repo.page = { id, pn -> if (pn == 2 && fail) BiliResult(code = -403) else success(id, pn) }
        val vm = mainDispatcherRule.track(BiliListViewModel(SavedStateHandle(mapOf("type" to "favorite")), repo))
        try {
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()
            vm.clearMessage()
            assertNotNull(vm.uiState.value.favoriteError)
            assertEquals(listOf("1:1"), vm.uiState.value.items.map { it.title })
            fail = false
            vm.loadMore()
            advanceUntilIdle()
            assertEquals(listOf(1L to 1, 1L to 2, 1L to 2), repo.requests)
            assertNull(vm.uiState.value.favoriteError)
            assertFalse(vm.uiState.value.isLoadingMore)
        } finally { vm.viewModelScope.cancel() }
    }

    @Test fun refreshingRemovedFoldersDropsOldSelectionAndDistinguishesAnEmptyDirectory() = runTest {
        val repo = Repository()
        val vm = mainDispatcherRule.track(BiliListViewModel(SavedStateHandle(mapOf("type" to "favorite")), repo))
        try {
            advanceUntilIdle()
            vm.selectFavoriteFolder(2)
            advanceUntilIdle()
            repo.folders = BiliResult(code = 0, data = emptyList())
            vm.refresh()
            advanceUntilIdle()
            assertNull(vm.uiState.value.selectedFavoriteFolderId)
            assertTrue(vm.uiState.value.items.isEmpty())
            assertFalse(vm.uiState.value.isRefreshing)
            assertNull(vm.uiState.value.favoriteError)
            vm.selectFavoriteFolder(999)
            assertNull(vm.uiState.value.selectedFavoriteFolderId)
        } finally { vm.viewModelScope.cancel() }
    }

    companion object {
        private fun success(id: Long, pn: Int) = BiliResult(code = 0, data = BiliFavoritePage(
            mediaId = id, hasMore = true,
            medias = listOf(BiliFavoriteMedia(bvid = "BV$id-$pn", title = "$id:$pn"))
        ))
    }
}
