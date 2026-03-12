package com.lightningstudio.watchrss.ui.screen.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.ui.util.formatTime
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HomeComposeScreen(
    channels: List<RssChannel>,
    openSwipeId: Long?,
    onOpenSwipe: (Long) -> Unit,
    onCloseSwipe: () -> Unit,
    draggingSwipeId: Long?,
    onDragStart: (Long) -> Unit,
    onDragEnd: () -> Unit,
    onProfileClick: () -> Unit,
    onRecommendClick: () -> Unit,
    onChannelClick: (RssChannel) -> Unit,
    onChannelLongClick: (RssChannel) -> Unit,
    onAddRssClick: () -> Unit,
    onMoveTopClick: (RssChannel) -> Unit,
    onMarkReadClick: (RssChannel) -> Unit,
    onBeianClick: () -> Unit
) {
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
        val entries = remember(channels) { buildHomeEntries(channels) }
        val safePadding = dimensionResource(R.dimen.watch_safe_padding)
        val itemSpacing = dimensionResource(R.dimen.hey_distance_6dp)
        val listState = rememberLazyListState()
        // 目的：解决纵向滚动与卡片横向滑动（HomeSwipeRow）之间的手势冲突。
        // 滚动时将 HomeSwipeRow 替换为普通 Box，彻底移除横向手势，避免误触左滑操作。
        // 代价：isScrollInProgress 在滚动过程中高频变化，会导致列表所有可见卡片频繁重组。
        // 手表场景下列表条目有限，重组开销可接受。
        val isScrolling by remember(listState) {
            derivedStateOf { listState.isScrollInProgress }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = safePadding),
                state = listState,
                contentPadding = PaddingValues(
                    top = safePadding,
                    bottom = safePadding + itemSpacing
                ),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                items(
                    entries,
                    key = { it.key },
                    contentType = { it::class }
                ) { entry ->
                    when (entry) {
                        HomeEntry.Profile -> {
                            HomeProfileEntry(onProfileClick = onProfileClick)
                        }
                        HomeEntry.Empty -> {
                            HomeDefaultItem(
                                title = "还没有 RSS 频道",
                                summary = "点击下方添加你的第一个订阅源",
                                backgroundColor = MaterialTheme.colorScheme.surface,
                                showIndicator = false
                            )
                        }
                        HomeEntry.Recommend -> {
                            HomeDefaultItem(
                                title = "RSS推荐",
                                summary = "一键加入官方支持频道",
                                backgroundColor = MaterialTheme.colorScheme.surface,
                                showIndicator = false,
                                onClick = onRecommendClick
                            )
                        }
                        HomeEntry.AddRss -> {
                            HomeAddEntry(onAddRssClick = onAddRssClick)
                        }
                        HomeEntry.Beian -> {
                            HomeBeianEntry(onBeianClick = onBeianClick)
                        }
                        is HomeEntry.Channel -> {
                            HomeChannelEntry(
                                channel = entry.channel,
                                isScrolling = isScrolling,
                                openSwipeId = openSwipeId,
                                onOpenSwipe = onOpenSwipe,
                                onCloseSwipe = onCloseSwipe,
                                draggingSwipeId = draggingSwipeId,
                                onDragStart = onDragStart,
                                onDragEnd = onDragEnd,
                                onChannelClick = { onChannelClick(entry.channel) },
                                onChannelLongClick = { onChannelLongClick(entry.channel) },
                                onMoveTopClick = { onMoveTopClick(entry.channel) },
                                onMarkReadClick = { onMarkReadClick(entry.channel) }
                            )
                        }
                }
            }
        }
    }
    }
}

private sealed class HomeEntry(val key: String) {
    data object Profile : HomeEntry("profile")
    data class Channel(val channel: RssChannel) : HomeEntry("channel_${channel.id}")
    data object Empty : HomeEntry("empty")
    data object Recommend : HomeEntry("recommend")
    data object AddRss : HomeEntry("add_rss")
    data object Beian : HomeEntry("beian")
}

private fun buildHomeEntries(channels: List<RssChannel>): List<HomeEntry> {
    val entries = mutableListOf<HomeEntry>()
    entries.add(HomeEntry.Profile)
    if (channels.isEmpty()) {
        entries.add(HomeEntry.Empty)
    } else {
        entries.addAll(channels.map { HomeEntry.Channel(it) })
    }
    entries.add(HomeEntry.Recommend)
    entries.add(HomeEntry.AddRss)
    entries.add(HomeEntry.Beian)
    return entries
}

@Composable
private fun HomeProfileEntry(onProfileClick: () -> Unit) {
    val avatarSize = dimensionResource(R.dimen.hey_listitem_big_lefticon_height_width)
    val padding = dimensionResource(R.dimen.hey_distance_4dp)
    val hintSize = textSize(R.dimen.hey_caption)
    val avatarTextSize = textSize(R.dimen.hey_m_desription)
    val strokeWidth = dimensionResource(R.dimen.hey_dotStrokeWidth)
    val accentColor = MaterialTheme.colorScheme.primary
    val cardColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding)
            .clickableWithoutRipple(onClick = onProfileClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .border(strokeWidth, accentColor, CircleShape)
                .clip(CircleShape)
                .background(cardColor)
                .semantics { contentDescription = "个人中心" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "我",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = avatarTextSize
            )
        }
        Spacer(modifier = Modifier.height(padding))
        Text(
            text = "点击进入我的",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = hintSize
        )
    }
}

@Composable
private fun HomeAddEntry(onAddRssClick: () -> Unit) {
    val buttonSize = dimensionResource(R.dimen.hey_button_height)
    val padding = dimensionResource(R.dimen.hey_distance_4dp)
    val radius = dimensionResource(R.dimen.hey_button_default_radius)
    val pressState = rememberPressScaleState()
    val pressScale = pressState.scale
    val scaleModifier = if (pressScale != 1f) {
        Modifier.graphicsLayer(
            scaleX = pressScale,
            scaleY = pressScale
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = padding),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .then(scaleModifier)
                .clip(RoundedCornerShape(radius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickableWithoutRipple(
                    onClick = onAddRssClick,
                    interactionSource = pressState.interactionSource
                )
                .semantics { contentDescription = "添加RSS" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = textSize(R.dimen.hey_s_title)
            )
        }
    }
}

@Composable
private fun HomeChannelEntry(
    channel: RssChannel,
    isScrolling: Boolean,
    openSwipeId: Long?,
    onOpenSwipe: (Long) -> Unit,
    onCloseSwipe: () -> Unit,
    draggingSwipeId: Long?,
    onDragStart: (Long) -> Unit,
    onDragEnd: () -> Unit,
    onChannelClick: () -> Unit,
    onChannelLongClick: () -> Unit,
    onMoveTopClick: () -> Unit,
    onMarkReadClick: () -> Unit
) {
    val actionPadding = dimensionResource(R.dimen.hey_distance_4dp)
    val actionWidth = dimensionResource(R.dimen.watch_swipe_action_button_width)
    val fallbackActionsWidthPx = with(LocalDensity.current) {
        (actionWidth * 2 + actionPadding * 2).toPx()
    }
    val revealGapPx = with(LocalDensity.current) { (actionPadding * 2).toPx() }
    var actionsWidthPx by remember { mutableStateOf(fallbackActionsWidthPx) }
    var cardHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val cardHeightModifier = if (cardHeightPx > 0) {
        val height = with(density) { cardHeightPx.toDp() }
        Modifier.height(height)
    } else {
        Modifier
    }
    val pressState = rememberPressScaleState(enabled = !isScrolling)
    val pressScale = pressState.scale
    val cardScaleModifier = if (pressScale != 1f) {
        Modifier.graphicsLayer(
            scaleX = pressScale,
            scaleY = pressScale
        )
    } else {
        Modifier
    }
    val backgroundScaleModifier = if (pressScale != 1f) {
        Modifier.graphicsLayer(
            scaleX = pressScale,
            scaleY = 1f
        )
    } else {
        Modifier
    }
    val summary = remember(
        channel.id,
        channel.description,
        channel.url,
        channel.isPinned,
        channel.unreadCount,
        channel.lastFetchedAt
    ) {
        buildChannelSummary(channel)
    }

    val cardContent: @Composable (Modifier) -> Unit = { offsetModifier ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(offsetModifier)
                .onSizeChanged { size ->
                    if (!isScrolling) {
                        cardHeightPx = size.height
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(backgroundScaleModifier)
            )
            HomeDefaultItem(
                title = channel.title,
                summary = summary,
                backgroundColor = if (channel.isPinned) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
                showIndicator = channel.unreadCount > 0,
                modifier = Modifier
                    .then(cardScaleModifier)
                    .clickableWithoutRipple(
                        enabled = !isScrolling,
                        onClick = onChannelClick,
                        onLongClick = onChannelLongClick,
                        interactionSource = pressState.interactionSource
                    )
            )
        }
    }

    if (isScrolling) {
        Box(modifier = Modifier.fillMaxWidth()) {
            cardContent(Modifier)
        }
    } else {
        HomeSwipeRow(
            itemId = channel.id,
            enabled = true,
            openSwipeId = openSwipeId,
            onOpenSwipe = onOpenSwipe,
            onCloseSwipe = onCloseSwipe,
            draggingSwipeId = draggingSwipeId,
            onDragStart = onDragStart,
            onDragEnd = onDragEnd,
            actionsWidthPx = actionsWidthPx,
            revealGapPx = revealGapPx
        ) { offsetModifier ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .then(cardHeightModifier)
                        .padding(start = 0.dp, end = actionPadding)
                        .onSizeChanged { size ->
                            actionsWidthPx = size.width.toFloat()
                        },
                    horizontalArrangement = Arrangement.spacedBy(actionPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeSwipeActionButton(
                        text = "移到顶",
                        width = actionWidth,
                        onClick = {
                            onCloseSwipe()
                            onMoveTopClick()
                        },
                        iconRes = R.drawable.ic_action_move_top
                    )
                    val isBuiltin = BuiltinChannelType.fromUrl(channel.url) != null
                    val canMarkRead = channel.unreadCount > 0 && !isBuiltin
                    HomeSwipeActionButton(
                        text = "标记已读",
                        width = actionWidth,
                        alpha = if (canMarkRead) 1f else 0.5f,
                        onClick = {
                            onCloseSwipe()
                            if (canMarkRead) {
                                onMarkReadClick()
                            }
                        },
                        iconRes = R.drawable.ic_action_mark_read
                    )
                }
                cardContent(offsetModifier)
            }
        }
    }
}

@Composable
private fun HomeSwipeActionButton(
    text: String,
    width: Dp,
    alpha: Float = 1f,
    onClick: () -> Unit,
    @DrawableRes iconRes: Int? = null
) {
    val radius = dimensionResource(R.dimen.hey_card_normal_bg_radius)
    val textSize = textSize(R.dimen.feed_card_action_text_size)
    val textPadding = dimensionResource(R.dimen.hey_distance_8dp)
    val iconSize = dimensionResource(R.dimen.hey_distance_16dp)
    val iconSpacing = dimensionResource(R.dimen.hey_distance_4dp)
    val dangerColor = colorResource(R.color.danger_red)
    val actionColor = if (text.contains("删除")) dangerColor else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickableWithoutRipple(onClick = onClick)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = textPadding)
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = text,
                    tint = actionColor,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.height(iconSpacing))
                Text(
                    text = text,
                    color = actionColor,
                    fontSize = textSize,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = text,
                color = actionColor,
                fontSize = textSize,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = textPadding)
            )
        }
    }
}

@Composable
private fun HomeSwipeRow(
    itemId: Long,
    enabled: Boolean = true,
    openSwipeId: Long?,
    onOpenSwipe: (Long) -> Unit,
    onCloseSwipe: () -> Unit,
    draggingSwipeId: Long?,
    onDragStart: (Long) -> Unit,
    onDragEnd: () -> Unit,
    actionsWidthPx: Float,
    revealGapPx: Float,
    content: @Composable (Modifier) -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val revealWidth = (actionsWidthPx + revealGapPx).coerceAtLeast(0f)
    val dragThreshold = revealWidth * 0.5f
    val openSwipeIdState = rememberUpdatedState(openSwipeId)

    LaunchedEffect(openSwipeId, actionsWidthPx, revealGapPx, draggingSwipeId, enabled) {
        if (!enabled) {
            offsetX.snapTo(0f)
            return@LaunchedEffect
        }
        if (draggingSwipeId != itemId && openSwipeId != itemId && offsetX.value != 0f) {
            offsetX.animateTo(0f, animationSpec = tween(durationMillis = 180))
        }
        if (revealWidth > 0f && offsetX.value < -revealWidth) {
            offsetX.snapTo(-revealWidth)
        }
    }

    val dragModifier = if (!enabled || revealWidth <= 0f) {
        Modifier
    } else {
        Modifier.pointerInput(itemId, actionsWidthPx, revealGapPx) {
            if (revealWidth <= 0f) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = {
                    onDragStart(itemId)
                    if (openSwipeIdState.value != null && openSwipeIdState.value != itemId) {
                        onCloseSwipe()
                    }
                },
                onDragEnd = {
                    val shouldOpen = offsetX.value <= -dragThreshold
                    val target = if (shouldOpen) -revealWidth else 0f
                    scope.launch {
                        offsetX.animateTo(target, animationSpec = tween(durationMillis = 180))
                    }
                    if (shouldOpen) {
                        onOpenSwipe(itemId)
                    } else if (openSwipeIdState.value == itemId) {
                        onCloseSwipe()
                    }
                    onDragEnd()
                },
                onDragCancel = {
                    scope.launch {
                        offsetX.animateTo(0f, animationSpec = tween(durationMillis = 180))
                    }
                    if (openSwipeIdState.value == itemId) {
                        onCloseSwipe()
                    }
                    onDragEnd()
                }
            ) { change, dragAmount ->
                change.consume()
                val newOffset = (offsetX.value + dragAmount).coerceIn(-revealWidth, 0f)
                scope.launch {
                    offsetX.snapTo(newOffset)
                }
            }
        }
    }

    val offsetModifier = Modifier
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .then(dragModifier)

    content(offsetModifier)
}

@Composable
private fun HomeDefaultItem(
    title: String,
    summary: String,
    backgroundColor: Color,
    showIndicator: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isRound = remember(context) { context.resources.configuration.isScreenRound }
    val paddingStart = dimensionResource(R.dimen.hey_content_horizontal_distance_6_0)
    val paddingEnd = dimensionResource(R.dimen.hey_listitem_padding_right)
    val verticalPadding = dimensionResource(R.dimen.hey_multiple_default_summary_alone_padding_vertical)
    val titleMargin = dimensionResource(R.dimen.hey_listitem_widget_padding_vertical)
    val summaryTop = dimensionResource(R.dimen.hey_alone_summary_margin_top)
    val summaryBottom = dimensionResource(R.dimen.hey_alone_summary_margin_bottom)
    val titleSize = textSize(R.dimen.hey_s_title)
    val summarySize = textSize(R.dimen.hey_m_desription)
    val summaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val arrowMargin = dimensionResource(R.dimen.hey_listitem_widget_margin_left)
    val minorMarginRight = dimensionResource(R.dimen.hey_listitem_widget_minor_margin_right)
    val indicatorSize = dimensionResource(R.dimen.hey_distance_6dp)
    val shape = RoundedCornerShape(dimensionResource(R.dimen.hey_card_normal_bg_radius))
    val clickModifier = if (onClick != null) {
        Modifier.clickableWithoutRipple(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .clip(shape)
            .background(backgroundColor)
            .padding(
                start = paddingStart,
                end = paddingEnd,
                top = if (isRound) 0.dp else verticalPadding,
                bottom = if (isRound) 0.dp else verticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = titleSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isRound) {
                    Modifier.padding(top = titleMargin, bottom = titleMargin)
                } else {
                    Modifier
                }
            )
            Text(
                text = summary,
                color = summaryColor,
                fontSize = summarySize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isRound) {
                    Modifier.padding(top = summaryTop, bottom = summaryBottom)
                } else {
                    Modifier
                }
            )
        }
        if (showIndicator) {
            Box(
                modifier = Modifier
                    .padding(start = arrowMargin)
                    .offset(x = minorMarginRight)
                    .size(indicatorSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

private fun buildChannelSummary(channel: RssChannel): String {
    val summary = channel.description?.takeIf { it.isNotBlank() } ?: channel.url
    val pinLabel = if (channel.isPinned) "置顶 · " else ""
    val unreadLabel = if (channel.unreadCount > 0) "未读 ${channel.unreadCount} · " else ""
    val timeText = formatTime(channel.lastFetchedAt)
    return "$pinLabel$summary\n${unreadLabel}更新: $timeText"
}

@Composable
private fun HomeBeianEntry(onBeianClick: () -> Unit) {
    val padding = dimensionResource(R.dimen.hey_distance_4dp)
    val textSize = textSize(R.dimen.hey_caption)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "浙ICP备2024111886号-5A",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = textSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickableWithoutRipple(onClick = onBeianClick)
        )
    }
}

@Stable
private data class PressScaleState(
    val scale: Float,
    val interactionSource: MutableInteractionSource
)

@Composable
private fun rememberPressScaleState(enabled: Boolean = true): PressScaleState {
    val interactionSource = remember { MutableInteractionSource() }
    if (!enabled) {
        return PressScaleState(1f, interactionSource)
    }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 240 else 360),
        label = "pressScale"
    )
    return PressScaleState(scale, interactionSource)
}

@Composable
private fun textSize(id: Int): TextUnit {
    val density = LocalDensity.current
    return with(density) { dimensionResource(id).toSp() }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.clickableWithoutRipple(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = MutableInteractionSource()
): Modifier {
    return if (onLongClick != null) {
        combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
    }
}
