package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.BlurFadeVisibility
import com.lightningstudio.watchrss.data.rss.SavedItem
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.util.formatTime
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SavedItemsScreen(
    title: String,
    hint: String,
    emptyMessage: String,
    hasLoadedItems: Boolean,
    items: List<SavedItem>,
    undoVisible: Boolean,
    onUndoClick: () -> Unit,
    onItemClick: (SavedItem) -> Unit,
    onItemRemove: (SavedItem) -> Unit,
    onItemsReordered: (List<Long>) -> Unit
) {
    val safePadding = WatchDimens.watch_safe_padding
    val extraBottomPadding = 40.dp
    val itemSpacing = watchDimensionResource(R.dimen.hey_distance_6dp)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val displayItems = remember { mutableStateListOf<SavedItem>() }
    var draggingItemId by remember { mutableStateOf<Long?>(null) }
    var draggingItemOffset by remember { mutableStateOf(0f) }
    var dragStartItems by remember { mutableStateOf(emptyList<SavedItem>()) }
    var pendingOrderIds by remember { mutableStateOf<List<Long>?>(null) }

    InstallDigitalCrownLazyListHandler(listState)

    LaunchedEffect(items, draggingItemId, pendingOrderIds) {
        if (draggingItemId != null) return@LaunchedEffect
        val incomingIds = items.map { it.item.id }
        val pendingIds = pendingOrderIds
        if (pendingIds != null) {
            if (incomingIds == pendingIds) {
                pendingOrderIds = null
            } else if (incomingIds.toSet() == pendingIds.toSet()) {
                return@LaunchedEffect
            } else {
                pendingOrderIds = null
            }
        }
        if (displayItems != items) {
            displayItems.clear()
            displayItems.addAll(items)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics {
                contentDescription = "${title}页面"
                stateDescription = when {
                    !hasLoadedItems -> "加载中"
                    items.isEmpty() -> "无保存项目"
                    else -> "共 ${items.size} 个保存项目"
                }
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = safePadding,
                top = safePadding,
                end = safePadding,
                bottom = safePadding + extraBottomPadding
            )
        ) {
            item(key = "saved_header") {
                SavedHeader(title = title, hint = hint)
            }
            if (hasLoadedItems && displayItems.isEmpty()) {
                item(key = "saved_empty") {
                    SavedEmpty(message = emptyMessage)
                }
            } else {
                items(displayItems, key = { it.item.id }) { savedItem ->
                    val itemId = savedItem.item.id
                    val isDragging = draggingItemId == itemId
                    val dragModifier = Modifier.pointerInput(itemId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingItemId = itemId
                                draggingItemOffset = 0f
                                dragStartItems = displayItems.toList()
                            },
                            onDragEnd = {
                                if (draggingItemId != itemId) return@detectDragGesturesAfterLongPress
                                val orderedIds = displayItems.map { it.item.id }
                                val startIds = dragStartItems.map { it.item.id }
                                draggingItemId = null
                                draggingItemOffset = 0f
                                dragStartItems = emptyList()
                                if (orderedIds != startIds) {
                                    pendingOrderIds = orderedIds
                                    onItemsReordered(orderedIds)
                                }
                            },
                            onDragCancel = {
                                if (draggingItemId == itemId) {
                                    displayItems.replaceAll(dragStartItems)
                                    draggingItemId = null
                                    draggingItemOffset = 0f
                                    dragStartItems = emptyList()
                                }
                            }
                        ) { change, dragAmount ->
                            if (draggingItemId != itemId) return@detectDragGesturesAfterLongPress
                            change.consume()
                            draggingItemOffset += dragAmount.y

                            val currentInfo = listState.visibleItemInfo(itemId) ?: return@detectDragGesturesAfterLongPress
                            val currentCenter = currentInfo.offset + draggingItemOffset + currentInfo.size / 2f
                            val targetInfo = listState.layoutInfo.visibleItemsInfo
                                .filter { info -> info.key is Long && info.key != itemId }
                                .firstOrNull { info ->
                                    currentCenter in info.offset.toFloat()..(info.offset + info.size).toFloat()
                                }

                            if (targetInfo != null) {
                                val fromIndex = displayItems.indexOfFirst { it.item.id == itemId }
                                val toIndex = displayItems.indexOfFirst { it.item.id == targetInfo.key }
                                if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                                    draggingItemOffset -= (targetInfo.offset - currentInfo.offset).toFloat()
                                    displayItems.move(fromIndex, toIndex)
                                }
                            }

                            val updatedInfo = listState.visibleItemInfo(itemId) ?: return@detectDragGesturesAfterLongPress
                            val overscroll = listState.dragOverscroll(updatedInfo, draggingItemOffset)
                            if (overscroll != 0f) {
                                scope.launch {
                                    val consumed = listState.scrollBy(overscroll.coerceIn(-36f, 36f))
                                    draggingItemOffset += consumed
                                }
                            }
                        }
                    }

                    SwipeToRemoveRow(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = if (isDragging) draggingItemOffset.roundToInt() else 0
                                )
                            }
                            .then(dragModifier),
                        itemId = savedItem.item.id,
                        onRemove = { onItemRemove(savedItem) }
                    ) { swipeModifier ->
                        SavedItemRow(
                            title = savedItem.item.title,
                            summary = buildSavedSummary(savedItem),
                            modifier = swipeModifier.padding(bottom = itemSpacing),
                            isDragging = isDragging,
                            onClick = { onItemClick(savedItem) }
                        )
                    }
                }
            }
        }

        BlurFadeVisibility(
            visible = !hasLoadedItems,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false,
                contentPadding = PaddingValues(
                    start = safePadding,
                    top = safePadding,
                    end = safePadding,
                    bottom = safePadding + extraBottomPadding
                )
            ) {
                item(key = "saved_loading_header") {
                    SavedHeader(title = title, hint = hint)
                }
                item(key = "saved_loading_skeleton") {
                    SavedItemsSkeleton(itemSpacing = itemSpacing)
                }
            }
        }

        if (undoVisible) {
            UndoFloatingButton(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = safePadding),
                onClick = onUndoClick
            )
        }
    }
}

private fun buildSavedSummary(savedItem: SavedItem): String {
    val summary = savedItem.item.summary ?: "暂无摘要"
    val meta = "${savedItem.channelTitle} · ${formatTime(savedItem.savedAt)}"
    return "$meta\n$summary"
}

@Composable
private fun SavedHeader(title: String, hint: String) {
    val padding = watchDimensionResource(R.dimen.hey_distance_4dp)
    val titleSize = textSize(R.dimen.settings_title_text_size)
    val hintSize = textSize(R.dimen.hey_s_desription)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding)
            .semantics { heading() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { contentDescription = "页面标题：$title" }
        )
        Text(
            text = hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = hintSize,
            modifier = Modifier.semantics { contentDescription = "页面说明：$hint" }
        )
    }
}

@Composable
private fun SavedEmpty(message: String) {
    val padding = watchDimensionResource(R.dimen.hey_content_horizontal_distance)
    val textSize = textSize(R.dimen.hey_m_desription)

    Text(
        text = message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = textSize,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding)
    )
}

@Composable
private fun SavedItemRow(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onClick: () -> Unit
) {
    val backgroundColor = if (isDragging) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    val shape = RoundedCornerShape(WatchDimens.hey_card_normal_bg_radius)
    val paddingStart = WatchDimens.hey_content_horizontal_distance_6_0
    val paddingEnd = watchDimensionResource(R.dimen.hey_listitem_padding_right)
    val verticalPadding = watchDimensionResource(R.dimen.hey_multiple_default_summary_alone_padding_vertical)
    val titleSize = textSize(R.dimen.hey_s_title)
    val summarySize = textSize(R.dimen.hey_m_desription)
    val summaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .clickableWithRipple(onClick)
            .padding(
                start = paddingStart,
                end = paddingEnd,
                top = verticalPadding,
                bottom = verticalPadding
            )
            .semantics {
                contentDescription = "文章：$title，$summary"
                stateDescription = if (isDragging) "正在拖动排序" else "长按可拖动排序"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = titleSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                color = summaryColor,
                fontSize = summarySize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SavedItemsSkeleton(itemSpacing: Dp) {
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(4) { index ->
            SavedItemSkeletonRow(
                titleWidths = if (index % 2 == 0) {
                    listOf(0.78f)
                } else {
                    listOf(0.66f)
                },
                summaryWidths = when (index % 3) {
                    0 -> listOf(0.72f, 0.88f)
                    1 -> listOf(0.64f, 0.82f)
                    else -> listOf(0.81f, 0.58f)
                }
            )
            if (index != 3) {
                Box(modifier = Modifier.height(itemSpacing))
            }
        }
    }
}

@Composable
private fun SavedItemSkeletonRow(
    titleWidths: List<Float>,
    summaryWidths: List<Float>
) {
    val shape = RoundedCornerShape(WatchDimens.hey_card_normal_bg_radius)
    val paddingStart = WatchDimens.hey_content_horizontal_distance_6_0
    val paddingEnd = watchDimensionResource(R.dimen.hey_listitem_padding_right)
    val verticalPadding = watchDimensionResource(R.dimen.hey_multiple_default_summary_alone_padding_vertical)
    val titleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)
    val summaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(
                start = paddingStart,
                end = paddingEnd,
                top = verticalPadding,
                bottom = verticalPadding
            )
    ) {
        SavedSkeletonParagraph(
            widths = titleWidths,
            lineHeight = 10.dp,
            spacing = 6.dp,
            color = titleColor
        )
        Box(modifier = Modifier.height(10.dp))
        SavedSkeletonParagraph(
            widths = summaryWidths,
            lineHeight = 8.dp,
            spacing = 5.dp,
            color = summaryColor
        )
    }
}

@Composable
private fun SavedSkeletonParagraph(
    widths: List<Float>,
    lineHeight: Dp,
    spacing: Dp,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        widths.forEachIndexed { index, width ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(width)
                    .height(lineHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .savedSkeletonPlaceholder(
                        baseColor = color,
                        highlightColor = Color.White.copy(alpha = 0.22f),
                        cornerRadius = 6.dp
                    )
            )
            if (index != widths.lastIndex) {
                Box(modifier = Modifier.height(spacing))
            }
        }
    }
}

@Composable
private fun Modifier.savedSkeletonPlaceholder(
    baseColor: Color,
    highlightColor: Color,
    cornerRadius: Dp
): Modifier {
    val transition = rememberInfiniteTransition(label = "SavedItemsSkeleton")
    val shimmerProgress by transition.animateFloat(
        initialValue = -1.1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SavedItemsSkeletonShimmer"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SavedItemsSkeletonPulse"
    )

    return this
        .background(baseColor)
        .drawWithCache {
            val radiusPx = cornerRadius.toPx()
            val widthPx = size.width.coerceAtLeast(1f)
            val heightPx = size.height.coerceAtLeast(1f)
            val startX = widthPx * shimmerProgress
            val brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    highlightColor,
                    Color.Transparent
                ),
                start = Offset(startX - widthPx * 0.8f, 0f),
                end = Offset(startX + widthPx * 0.25f, heightPx)
            )
            onDrawWithContent {
                drawContent()
                drawRoundRect(
                    brush = brush,
                    alpha = pulseAlpha,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx)
                )
            }
        }
}

@Composable
private fun UndoFloatingButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val size = watchDimensionResource(R.dimen.hey_listitem_big_lefticon_height_width)
    val radius = WatchDimens.hey_card_normal_bg_radius
    val background = watchColorResource(R.color.watch_pill_background)
    val iconSize = watchDimensionResource(R.dimen.hey_listitem_widget_size)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(background)
            .clickableWithRipple(onClick)
            .semantics {
                contentDescription = "撤回按钮"
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Undo,
            contentDescription = "撤回",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun SwipeToRemoveRow(
    modifier: Modifier = Modifier,
    itemId: Long,
    onRemove: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(itemId) { Animatable(0f) }
    var widthPx by remember { mutableStateOf(0f) }

    val dragModifier = Modifier.pointerInput(itemId, widthPx) {
        if (widthPx <= 0f) return@pointerInput
        detectHorizontalDragGestures(
            onDragEnd = {
                val shouldRemove = offsetX.value <= -widthPx * 0.35f
                if (shouldRemove) {
                    onRemove()
                }
                scope.launch {
                    offsetX.animateTo(0f, animationSpec = tween(durationMillis = 180))
                }
            },
            onDragCancel = {
                scope.launch {
                    offsetX.animateTo(0f, animationSpec = tween(durationMillis = 180))
                }
            }
        ) { change, dragAmount ->
            change.consume()
            val newOffset = (offsetX.value + dragAmount).coerceIn(-widthPx, 0f)
            scope.launch {
                offsetX.snapTo(newOffset)
            }
        }
    }

    Box(
        modifier = modifier.onSizeChanged { widthPx = it.width.toFloat() }
    ) {
        content(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(dragModifier)
        )
    }
}

@Composable
private fun textSize(id: Int): TextUnit {
    val density = LocalDensity.current
    return with(density) { watchDimensionResource(id).toSp() }
}

@Composable
private fun Modifier.clickableWithRipple(onClick: () -> Unit): Modifier {
    return clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = LocalIndication.current,
        onClick = onClick
    )
}

private fun LazyListState.visibleItemInfo(key: Long): LazyListItemInfo? {
    return layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
}

private fun LazyListState.dragOverscroll(itemInfo: LazyListItemInfo, itemOffset: Float): Float {
    val itemStart = itemInfo.offset + itemOffset
    val itemEnd = itemStart + itemInfo.size
    return when {
        itemEnd > layoutInfo.viewportEndOffset -> itemEnd - layoutInfo.viewportEndOffset
        itemStart < layoutInfo.viewportStartOffset -> itemStart - layoutInfo.viewportStartOffset
        else -> 0f
    }
}

private fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return
    val item = removeAt(fromIndex)
    add(if (toIndex > fromIndex) toIndex else toIndex, item)
}

private fun <T> MutableList<T>.replaceAll(items: List<T>) {
    clear()
    addAll(items)
}
