package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import com.lightningstudio.watchrss.ui.viewmodel.BiliFeedUiState
import kotlinx.coroutines.flow.distinctUntilChanged

/** Lazy precomposition is not an impression. Only rows intersecting the active viewport count. */
@Composable
internal fun RememberBiliShownRecommendations(
    listState: LazyListState,
    uiState: BiliFeedUiState,
    onItemsShown: (Long, List<BiliItem>) -> Unit
) {
    val owner = LocalLifecycleOwner.current
    val state = rememberUpdatedState(uiState)
    val callback = rememberUpdatedState(onItemsShown)
    val itemsByKey = rememberUpdatedState(remember(uiState.items) {
        uiState.items.associateBy { it.bvid ?: it.aid ?: it.title.orEmpty() }
    })
    LaunchedEffect(listState, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                val feed = state.value
                val layout = listState.layoutInfo
                val visible = if (!feed.isLoggedIn) emptyList() else layout.visibleItemsInfo
                    .filter { it.size > 0 && it.offset < layout.viewportEndOffset &&
                        it.offset + it.size > layout.viewportStartOffset }
                    .mapNotNull { itemsByKey.value[it.key] }
                feed.accountGeneration to visible
            }.distinctUntilChanged().collect { (generation, items) ->
                if (items.isNotEmpty()) callback.value(generation, items)
            }
        }
    }
}
