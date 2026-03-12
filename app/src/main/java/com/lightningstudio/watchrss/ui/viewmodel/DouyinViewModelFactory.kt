package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStore
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManager
import com.lightningstudio.watchrss.data.douyin.DouyinRepository
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStore

class DouyinViewModelFactory(
    private val repository: DouyinRepository,
    private val preloadManager: DouyinPreloadManager,
    private val watchHistoryStore: DouyinWatchHistoryStore,
    private val feedCacheStore: DouyinFeedCacheStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DouyinFeedViewModel::class.java) -> {
                DouyinFeedViewModel(repository, preloadManager, watchHistoryStore, feedCacheStore)
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
