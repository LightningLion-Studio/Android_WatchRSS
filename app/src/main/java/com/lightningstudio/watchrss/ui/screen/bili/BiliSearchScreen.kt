package com.lightningstudio.watchrss.ui.screen.bili

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.SearchInputBar
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallRotaryLazyListHandler
import com.lightningstudio.watchrss.ui.viewmodel.BiliSearchSubmitAction
import com.lightningstudio.watchrss.ui.viewmodel.BiliSearchViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BiliSearchScreen(
    viewModel: BiliSearchViewModel,
    onNavigateBack: () -> Unit,
    onSearch: (BiliSearchSubmitAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchText by viewModel.draftQuery.collectAsState()
    val hotSearchWords by viewModel.hotSearchWords.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val titleSize = textSize(R.dimen.hey_s_title)
    val listSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_6dp
    val listState = rememberLazyListState()

    BackHandler(onBack = onNavigateBack)
    InstallRotaryLazyListHandler(listState)

    WatchSurface {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = safePadding,
                top = safePadding,
                end = safePadding,
                bottom = safePadding + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(listSpacing)
        ) {
            item {
                Text(
                    text = "搜索",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = titleSize,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                SearchInputBar(
                    keyword = searchText,
                    onKeywordChange = viewModel::updateDraftQuery,
                    onSearch = { query ->
                        onSearch(viewModel.submitSearch(query))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Search History
            if (searchHistory.isNotEmpty()) {
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "搜索历史",
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(onClick = { viewModel.clearSearchHistory() }) {
                                Text("清空")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            searchHistory.forEach { keyword ->
                                SuggestionChip(
                                    onClick = {
                                        onSearch(viewModel.submitSearch(keyword))
                                    },
                                    label = { Text(keyword) }
                                )
                            }
                        }
                    }
                }
            }

            // Hot Search
            if (hotSearchWords.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "热门搜索",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            hotSearchWords.forEach { word ->
                                SuggestionChip(
                                    onClick = {
                                        onSearch(viewModel.submitSearch(word.keyword))
                                    },
                                    label = { Text(word.showName ?: word.keyword) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun textSize(id: Int): TextUnit {
    return androidx.compose.ui.platform.LocalDensity.current.run {
        dimensionResource(id).toSp()
    }
}
