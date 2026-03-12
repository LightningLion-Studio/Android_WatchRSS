package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.lightningstudio.watchrss.data.AppContainer

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val savedStateHandle = extras.createSavedStateHandle()
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(container.rssRepository)
            }
            modelClass.isAssignableFrom(AddRssViewModel::class.java) -> {
                AddRssViewModel(container.rssRepository)
            }
            modelClass.isAssignableFrom(FeedViewModel::class.java) -> {
                FeedViewModel(savedStateHandle, container.rssRepository)
            }
            modelClass.isAssignableFrom(ChannelActionsViewModel::class.java) -> {
                ChannelActionsViewModel(savedStateHandle, container.rssRepository)
            }
            modelClass.isAssignableFrom(ItemActionsViewModel::class.java) -> {
                ItemActionsViewModel(savedStateHandle, container.rssRepository)
            }
            modelClass.isAssignableFrom(DetailViewModel::class.java) -> {
                DetailViewModel(savedStateHandle, container.rssRepository, container.settingsRepository)
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(container.settingsRepository, container.rssRepository)
            }
            modelClass.isAssignableFrom(OobeViewModel::class.java) -> {
                OobeViewModel(container.settingsRepository)
            }
            modelClass.isAssignableFrom(ChannelDetailViewModel::class.java) -> {
                ChannelDetailViewModel(savedStateHandle, container.rssRepository)
            }
            modelClass.isAssignableFrom(SavedItemsViewModel::class.java) -> {
                SavedItemsViewModel(savedStateHandle, container.rssRepository)
            }
            modelClass.isAssignableFrom(RssSearchViewModel::class.java) -> {
                RssSearchViewModel(savedStateHandle, container.rssRepository)
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return create(modelClass, CreationExtras.Empty)
    }
}
