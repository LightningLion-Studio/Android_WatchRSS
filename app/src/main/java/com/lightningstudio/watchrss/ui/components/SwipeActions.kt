package com.lightningstudio.watchrss.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import com.lightningstudio.watchrss.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeActionButton(
    text: String,
    width: Dp,
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
            .clickableWithoutRipple(onClick),
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
                modifier = Modifier.padding(horizontal = textPadding)
            )
        }
    }
}

@Composable
fun SwipeActionRow(
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
