package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.sdk.bili.BiliPage
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import com.lightningstudio.watchrss.ui.viewmodel.BiliDetailUiState

@Composable
fun BiliDetailScreen(
    uiState: BiliDetailUiState,
    onPlayClick: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onLike: () -> Unit,
    onCoin: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onCommentClick: () -> Unit,
    showCommentEntry: Boolean
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val spacing = dimensionResource(R.dimen.hey_distance_6dp)
    val extraBottomPadding = 32.dp
    val listState = rememberLazyListState()
    val detail = uiState.detail

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = safePadding),
            contentPadding = PaddingValues(
                top = safePadding,
                bottom = safePadding + spacing + extraBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            item {
                BiliCoverCard(
                    coverUrl = detail?.item?.cover,
                    onClick = onPlayClick
                )
            }
            item {
                BiliMetaCard(
                    title = detail?.item?.title ?: "加载中...",
                    owner = detail?.item?.owner?.name,
                    viewCount = detail?.item?.stat?.view,
                    likeCount = detail?.item?.stat?.like,
                    danmakuCount = detail?.item?.stat?.danmaku
                )
            }
            item {
    BiliActionSection(
        isLiked = uiState.isLiked,
        isCoined = uiState.isCoined,
        isFavorited = uiState.isFavorited,
        onLike = onLike,
        onCoin = onCoin,
                    onFavorite = onFavorite,
                    onShare = onShare
                )
            }
            if (!detail?.pages.isNullOrEmpty()) {
                item {
                    BiliSectionTitle(
                        title = "分P",
                        trailing = "${detail.pages.size} 个"
                    )
                }
                itemsIndexed(detail!!.pages) { index, page ->
                    BiliPageEntry(
                        page = page,
                        selected = uiState.selectedPageIndex == index,
                        onClick = { onSelectPage(index) }
                    )
                }
            }
            if (!detail?.desc.isNullOrBlank()) {
                item {
                    BiliSectionTitle(title = "简介")
                }
                item {
                    BiliDescriptionCard(text = detail?.desc.orEmpty())
                }
            }
            if (showCommentEntry) {
                item {
                    BiliSectionTitle(title = "评论")
                }
                item {
                    BiliCommentEntryCard(onClick = onCommentClick)
                }
            }
            if (!uiState.message.isNullOrBlank()) {
                item {
                    BiliMessageCard(message = uiState.message.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun BiliCoverCard(
    coverUrl: String?,
    onClick: () -> Unit
) {
    val radius = dimensionResource(R.dimen.hey_card_normal_bg_radius)
    val coverHeight = dimensionResource(R.dimen.hey_card_large_height)
    val playIconSize = dimensionResource(R.dimen.hey_listitem_widget_size)
    val overlay = Brush.verticalGradient(
        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    val maxWidthPx = remember(context) { context.resources.displayMetrics.widthPixels.coerceAtLeast(1) }
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, coverUrl, maxWidthPx) {
        value = if (coverUrl.isNullOrBlank()) null else {
            RssImageLoader.loadBitmap(context, coverUrl, maxWidthPx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(coverHeight)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surface)
            .clickableWithoutRipple(onClick),
        contentAlignment = Alignment.Center
    ) {
        val safeBitmap = bitmap
        if (safeBitmap != null) {
            Image(
                bitmap = safeBitmap.asImageBitmap(),
                contentDescription = "视频封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(overlay)
        )
        Box(
            modifier = Modifier
                .size(playIconSize + 12.dp)
                .clip(RoundedCornerShape(100))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_play_circle),
                contentDescription = "播放",
                modifier = Modifier.size(playIconSize)
            )
        }
    }
}

@Composable
private fun BiliMetaCard(
    title: String,
    owner: String?,
    viewCount: Long?,
    likeCount: Long?,
    danmakuCount: Long?
) {
    val radius = dimensionResource(R.dimen.hey_card_normal_bg_radius)
    val spacing = dimensionResource(R.dimen.hey_distance_6dp)
    val cardColor = MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(cardColor)
            .padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = textSize(R.dimen.hey_m_title),
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (!owner.isNullOrBlank()) {
            Text(
                text = "UP 主：$owner",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = textSize(R.dimen.hey_caption)
            )
        }
        BiliStatChips(
            viewCount = viewCount,
            likeCount = likeCount,
            danmakuCount = danmakuCount
        )
    }
}

@Composable
private fun BiliStatChips(
    viewCount: Long?,
    likeCount: Long?,
    danmakuCount: Long?
) {
    val parts = buildList {
        viewCount?.let { add("播放 ${formatBiliCount(it)}") }
        likeCount?.let { add("点赞 ${formatBiliCount(it)}") }
        danmakuCount?.let { add("弹幕 ${formatBiliCount(it)}") }
    }
    if (parts.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.hey_distance_4dp))) {
        parts.forEach { text -> BiliStatChip(text = text) }
    }
}

@Composable
private fun BiliStatChip(text: String) {
    val radius = dimensionResource(R.dimen.hey_button_default_radius)
    val padding = dimensionResource(R.dimen.hey_distance_4dp)
    val background = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radius))
            .background(background)
            .padding(horizontal = padding, vertical = padding / 2)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = textSize(R.dimen.hey_caption),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BiliActionSection(
    isLiked: Boolean,
    isCoined: Boolean,
    isFavorited: Boolean,
    onLike: () -> Unit,
    onCoin: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BiliActionCircleButton(
            iconRes = R.drawable.ic_action_like,
            contentDescription = "点赞",
            selected = isLiked,
            onClick = onLike
        )
        BiliActionCircleButton(
            iconRes = R.drawable.ic_action_coin,
            contentDescription = "投币",
            selected = isCoined,
            enabled = !isCoined,
            onClick = onCoin
        )
        BiliActionCircleButton(
            iconRes = R.drawable.ic_action_favorite,
            contentDescription = "收藏",
            selected = isFavorited,
            onClick = onFavorite
        )
        BiliActionCircleButton(
            iconRes = R.drawable.ic_action_share,
            contentDescription = "转发",
            onClick = onShare
        )
    }
}

@Composable
private fun BiliActionCircleButton(
    iconRes: Int,
    contentDescription: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val size = dimensionResource(R.dimen.hey_button_height)
    val iconSize = dimensionResource(R.dimen.hey_listitem_widget_size)
    val background = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = dimensionResource(R.dimen.hey_distance_2dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun BiliSectionTitle(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = textSize(R.dimen.hey_s_title)
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = textSize(R.dimen.hey_caption)
            )
        }
    }
}

@Composable
private fun BiliDescriptionCard(text: String) {
    val radius = dimensionResource(R.dimen.hey_card_normal_bg_radius)
    val padding = dimensionResource(R.dimen.hey_distance_6dp)
    val background = MaterialTheme.colorScheme.surface
    val textSize = textSize(R.dimen.hey_s_title)
    val lineHeight = textSize * 1.1f
    val density = androidx.compose.ui.platform.LocalDensity.current
    val arrowWidth = remember(textSize, density) { with(density) { textSize.toDp() } }
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var canExpand by remember(text) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(background)
            .clickableWithoutRipple(
                onClick = {
                    if (canExpand || expanded) {
                        expanded = !expanded
                    }
                }
            )
            .padding(padding)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = textSize,
            lineHeight = lineHeight,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = if (!expanded && canExpand) {
                Modifier.padding(end = arrowWidth)
            } else {
                Modifier
            },
            onTextLayout = { result ->
                if (!expanded) {
                    canExpand = result.hasVisualOverflow
                }
            }
        )
        if (!expanded && canExpand) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "▼",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = textSize,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
    }
}

@Composable
private fun BiliCommentEntryCard(onClick: () -> Unit) {
    val radius = dimensionResource(R.dimen.hey_card_normal_bg_radius)
    val padding = dimensionResource(R.dimen.hey_distance_6dp)
    val background = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(background)
            .clickableWithoutRipple(onClick)
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "查看评论",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = textSize(R.dimen.hey_s_title),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BiliMessageCard(message: String) {
    val radius = dimensionResource(R.dimen.hey_card_normal_bg_radius)
    val padding = dimensionResource(R.dimen.hey_distance_6dp)
    val background = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(background)
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BiliPageEntry(
    page: BiliPage,
    selected: Boolean,
    onClick: () -> Unit
) {
    val radius = dimensionResource(R.dimen.hey_card_normal_bg_radius)
    val accent = MaterialTheme.colorScheme.primary
    val background = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val borderColor = if (selected) accent else Color.Transparent
    val padding = dimensionResource(R.dimen.hey_distance_6dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(radius))
            .clickableWithoutRipple(onClick)
            .padding(padding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = page.part?.ifBlank { null } ?: "第${page.page ?: 1}集",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = textSize(R.dimen.hey_s_title),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val duration = formatDuration(page.duration)
            if (duration != null) {
                Spacer(modifier = Modifier.size(padding))
                Text(
                    text = duration,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = textSize(R.dimen.hey_caption)
                )
            }
        }
    }
}

@Composable
private fun textSize(id: Int): androidx.compose.ui.unit.TextUnit {
    return androidx.compose.ui.platform.LocalDensity.current.run {
        dimensionResource(id).toSp()
    }
}

@Composable
private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier {
    return clickable(
        interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

private fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val minutes = seconds / 60
    val remain = seconds % 60
    return String.format("%02d:%02d", minutes, remain)
}
