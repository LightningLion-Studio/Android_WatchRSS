package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.data.rss.RssRepository

class BiliViewModelFactory(
    private val repository: BiliRepository,
    private val rssRepository: RssRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val savedStateHandle = extras.createSavedStateHandle()
        return when {
            modelClass.isAssignableFrom(BiliFeedViewModel::class.java) -> {
                BiliFeedViewModel(repository, rssRepository)
            }
            modelClass.isAssignableFrom(BiliLoginViewModel::class.java) -> {
                BiliLoginViewModel(repository)
            }
            modelClass.isAssignableFrom(BiliDetailViewModel::class.java) -> {
                val rss = rssRepository ?: throw IllegalStateException("Missing RssRepository")
                BiliDetailViewModel(savedStateHandle, repository, rss)
            }
            modelClass.isAssignableFrom(BiliPlayerViewModel::class.java) -> {
                BiliPlayerViewModel(savedStateHandle, repository)
            }
            modelClass.isAssignableFrom(BiliListViewModel::class.java) -> {
                BiliListViewModel(savedStateHandle, repository)
            }
            modelClass.isAssignableFrom(BiliSettingsViewModel::class.java) -> {
                val rss = rssRepository ?: throw IllegalStateException("Missing RssRepository")
                BiliSettingsViewModel(repository, rss)
            }
            modelClass.isAssignableFrom(BiliSearchViewModel::class.java) -> {
                BiliSearchViewModel(repository)
            }
            modelClass.isAssignableFrom(BiliSearchResultViewModel::class.java) -> {
                BiliSearchResultViewModel(savedStateHandle, repository)
            }
            modelClass.isAssignableFrom(BiliCommentViewModel::class.java) -> {
                BiliCommentViewModel(savedStateHandle, repository)
            }
            modelClass.isAssignableFrom(BiliReplyViewModel::class.java) -> {
                BiliReplyViewModel(savedStateHandle, repository)
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return create(modelClass, CreationExtras.Empty)
    }
}
