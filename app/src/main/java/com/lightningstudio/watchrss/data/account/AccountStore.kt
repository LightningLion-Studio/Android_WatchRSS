package com.lightningstudio.watchrss.data.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface AccountStore {
    fun read(): WatchAccountState?
    val state: StateFlow<WatchAccountState?>
    fun save(state: WatchAccountState)
    fun clear()
}
