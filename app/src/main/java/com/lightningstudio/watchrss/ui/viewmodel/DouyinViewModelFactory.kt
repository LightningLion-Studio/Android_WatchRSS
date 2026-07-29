package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackTransportContract
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceCoordinatorContract
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManagerContract
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowCacheCoordinatorContract
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStoreContract

class DouyinViewModelFactory(
    private val repository: DouyinRepositoryContract,
    private val preloadManager: DouyinPreloadManagerContract,
    private val playbackTransport: DouyinPlaybackTransportContract,
    private val playbackSourceCoordinator: DouyinPlaybackSourceCoordinatorContract,
    private val watchHistoryStore: DouyinWatchHistoryStoreContract,
    private val feedCacheStore: DouyinFeedCacheStoreContract,
    private val recentWindowStore: DouyinRecentWindowStoreContract,
    private val recentWindowCacheCoordinator: DouyinRecentWindowCacheCoordinatorContract,
    private val resumeToVideoFlowOnEntry: Boolean = false,
    private val resumeAwemeIdOnEntry: String? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DouyinFeedViewModel::class.java) -> {
                DouyinFeedViewModel(
                    repository = repository,
                    preloadManager = preloadManager,
                    playbackTransport = playbackTransport,
                    playbackSourceCoordinator = playbackSourceCoordinator,
                    watchHistoryStore = watchHistoryStore,
                    feedCacheStore = feedCacheStore,
                    recentWindowStore = recentWindowStore,
                    recentWindowCacheCoordinator = recentWindowCacheCoordinator,
                    resumeToVideoFlowOnEntry = resumeToVideoFlowOnEntry,
                    resumeAwemeIdOnEntry = resumeAwemeIdOnEntry
                )
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
