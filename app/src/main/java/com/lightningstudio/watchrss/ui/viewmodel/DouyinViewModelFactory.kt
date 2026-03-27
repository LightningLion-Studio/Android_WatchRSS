package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManagerContract
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStoreContract

class DouyinViewModelFactory(
    private val repository: DouyinRepositoryContract,
    private val preloadManager: DouyinPreloadManagerContract,
    private val watchHistoryStore: DouyinWatchHistoryStoreContract,
    private val feedCacheStore: DouyinFeedCacheStoreContract
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
