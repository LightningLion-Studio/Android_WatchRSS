package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.rss.SavedItem
import com.lightningstudio.watchrss.ui.util.formatTime
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SavedItemsScreen(
    title: String,
    hint: String,
    emptyMessage: String,
    items: List<SavedItem>,
    undoVisible: Boolean,
    showSyncButton: Boolean,
    onUndoClick: () -> Unit,
    onItemClick: (SavedItem) -> Unit,
    onItemRemove: (SavedItem) -> Unit,
    onSyncToPhone: () -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val itemSpacing = dimensionResource(R.dimen.hey_distance_6dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(safePadding)
        ) {
            item {
                SavedHeader(title = title, hint = hint)
            }
            if (showSyncButton) {
                item {
                    SyncButton(onClick = onSyncToPhone)
                }
            }
            if (items.isEmpty()) {
                item {
                    SavedEmpty(message = emptyMessage)
                }
            } else {
                items(items, key = { it.item.id }) { savedItem ->
                    SwipeToRemoveRow(
                        itemId = savedItem.item.id,
                        onRemove = { onItemRemove(savedItem) }
                    ) { swipeModifier ->
                        SavedItemRow(
                            title = savedItem.item.title,
                            summary = buildSavedSummary(savedItem),
                            modifier = swipeModifier.padding(bottom = itemSpacing),
                            onClick = { onItemClick(savedItem) }
                        )
                    }
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
    val padding = dimensionResource(R.dimen.hey_distance_4dp)
    val titleSize = textSize(R.dimen.settings_title_text_size)
    val hintSize = textSize(R.dimen.hey_s_desription)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = hintSize
        )
    }
}

@Composable
private fun SyncButton(onClick: () -> Unit) {
    val padding = dimensionResource(R.dimen.hey_distance_4dp)
    val shape = RoundedCornerShape(dimensionResource(R.dimen.hey_button_default_radius))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = padding, vertical = padding),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Button(
            onClick = onClick,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = shape,
            modifier = Modifier
                .width(dimensionResource(R.dimen.watch_action_button_width))
                .height(dimensionResource(R.dimen.watch_action_button_height))
        ) {
            Text(
                text = "同步到手机",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = textSize(R.dimen.hey_s_desription),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SavedEmpty(message: String) {
    val padding = dimensionResource(R.dimen.hey_content_horizontal_distance)
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
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isRound = remember(context) { context.resources.configuration.isScreenRound }
    val backgroundColor = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(dimensionResource(R.dimen.hey_card_normal_bg_radius))
    val paddingStart = dimensionResource(R.dimen.hey_content_horizontal_distance_6_0)
    val paddingEnd = dimensionResource(R.dimen.hey_listitem_padding_right)
    val verticalPadding = dimensionResource(R.dimen.hey_multiple_default_summary_alone_padding_vertical)
    val titleMargin = dimensionResource(R.dimen.hey_listitem_widget_padding_vertical)
    val summaryTop = dimensionResource(R.dimen.hey_alone_summary_margin_top)
    val summaryBottom = dimensionResource(R.dimen.hey_alone_summary_margin_bottom)
    val titleSize = textSize(R.dimen.hey_s_title)
    val summarySize = textSize(R.dimen.hey_m_desription)
    val summaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .clickableWithoutRipple(onClick)
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
    }
}

@Composable
private fun UndoFloatingButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val size = dimensionResource(R.dimen.hey_listitem_big_lefticon_height_width)
    val radius = dimensionResource(R.dimen.hey_card_normal_bg_radius)
    val background = colorResource(R.color.watch_pill_background)
    val iconSize = dimensionResource(R.dimen.hey_listitem_widget_size)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(background)
            .clickableWithoutRipple(onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_action_undo),
            contentDescription = "撤回",
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun SwipeToRemoveRow(
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
        modifier = Modifier.onSizeChanged { widthPx = it.width.toFloat() }
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
    return with(density) { dimensionResource(id).toSp() }
}

@Composable
private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier {
    return clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}
