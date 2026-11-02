package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.home.*
import com.lightningstudio.watchrss.ui.components.*
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class HomeOrderRow(val id: Long, val title: String, val pinned: Boolean, val order: Long)

class HomeOrderActivity : BaseWatchActivity() {
    private var saving by mutableStateOf(false)
    override fun isSwipeBackEnabled(): Boolean = !saving
    override fun shouldUsePlatformSwipeDismissFeature(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        val repository = (application as WatchRssApplication).container.rssRepository
        val placements = HomeEntryPlacementStore(this)
        setContent {
            WatchRSSTheme {
                var rows by remember { mutableStateOf<List<HomeOrderRow>>(emptyList()) }
                var error by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                BackHandler(enabled = saving) { /* Complete the explicit save before leaving. */ }
                LaunchedEffect(Unit) {
                    val channels = repository.observeChannels().first()
                    val notes = placements.notesPlacement()
                    rows = (channels.map { HomeOrderRow(it.id, it.title, it.isPinned, it.sortOrder) } +
                        HomeOrderRow(NOTES_HOME_ORDER_ID, "备忘录", notes.isPinned, notes.sortOrder))
                        .sortedWith(compareByDescending<HomeOrderRow> { it.pinned }.thenByDescending { it.order })
                }
                val latestRows by rememberUpdatedState(rows)
                val latestSaving by rememberUpdatedState(saving)
                fun move(id: Long, target: Int) {
                    if (saving) return
                    val byId = rows.associateBy { it.id }
                    rows = moveHomeOrderItem(rows.map { it.id }, id, target).map(byId::getValue)
                }
                val moveLatest by rememberUpdatedState<(Long, Int) -> Unit>(::move)
                val listState = rememberLazyListState()
                InstallDigitalCrownLazyListHandler(listState)
                WatchSurface {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 26.dp),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 32.dp)
                    ) {
                        item {
                            Text("首页排序", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                            Text("拖动把手或点击箭头调整；保存后按此顺序展示，并取消已有置顶。", style = MaterialTheme.typography.bodySmall)
                            Text("RSS 顺序参与同步；备忘录与内置入口顺序保存在本机。", style = MaterialTheme.typography.bodySmall)
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Row {
                                WatchTextButton(enabled = !saving && rows.isNotEmpty(), onClick = {
                                    saving = true
                                    error = null
                                    scope.launch {
                                        try {
                                            val notesOrder = repository.reorderHomeEntries(rows.map { it.id })
                                            placements.setNotesOrder(notesOrder)
                                            finish()
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            error = failure.message ?: "排序保存失败，请重试"
                                        } finally { saving = false }
                                    }
                                }) { Text(if (saving) "保存中" else "保存") }
                                WatchTextButton(enabled = !saving, onClick = { finish() }) { Text("取消") }
                            }
                        }
                        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                            var height by remember(row.id) { mutableFloatStateOf(1f) }
                            var dragY by remember(row.id) { mutableFloatStateOf(0f) }
                            Row(Modifier.fillMaxWidth().onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }) {
                                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                    Text("${index + 1}. ${row.title}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Row {
                                        WatchIconButton(enabled = !saving && index > 0, onClick = { move(row.id, index - 1) }) {
                                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移 ${row.title}")
                                        }
                                        WatchIconButton(enabled = !saving && index < rows.lastIndex, onClick = { move(row.id, index + 1) }) {
                                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移 ${row.title}")
                                        }
                                    }
                                }
                                Icon(
                                    Icons.Filled.DragHandle, contentDescription = "拖动排序 ${row.title}",
                                    modifier = Modifier.size(44.dp).semantics { contentDescription = "拖动排序 ${row.title}" }
                                        .pointerInput(row.id) {
                                            var totalY = 0f
                                            detectDragGestures(
                                                onDragStart = { totalY = 0f; dragY = 0f },
                                                onDragCancel = { totalY = 0f; dragY = 0f },
                                                onDragEnd = {
                                                    dragY = 0f
                                                    if (!latestSaving) {
                                                        val from = latestRows.indexOfFirst { it.id == row.id }
                                                        moveLatest(row.id, from + (totalY / height).roundToInt())
                                                    }
                                                },
                                                onDrag = { change, delta ->
                                                    change.consume()
                                                    if (!latestSaving) { totalY += delta.y; dragY = totalY }
                                                }
                                            )
                                        }
                                        .graphicsLayer { translationY = dragY }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
