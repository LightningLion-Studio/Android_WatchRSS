package com.lightningstudio.watchrss.ui.screen.rss

import android.graphics.Paint
import android.text.TextPaint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.rememberWatchTitleLineLimitsPx
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.util.formatWatchTitleForWidthLimits
import com.lightningstudio.watchrss.ui.viewmodel.LlmSummaryUiState
import com.lightningstudio.watchrss.ui.viewmodel.SummaryStatus
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
internal fun AiSummaryBanner(
    summaryState: LlmSummaryUiState,
    textColor: Color,
    backgroundColor: Color,
    activeColor: Color,
    onClick: () -> Unit
) {
    val isGenerating = summaryState.status == SummaryStatus.Generating
    val hasText = summaryState.text.isNotBlank()
    val isError = summaryState.status is SummaryStatus.Error
    val bannerBg = activeColor.copy(alpha = 0.10f).compositeOver(backgroundColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WatchDimens.hey_card_normal_bg_radius))
            .background(bannerBg)
            .clickableWithoutRipple(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isGenerating && !hasText) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = activeColor
                )
            } else {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            val displayText = when {
                isError -> "总结失败，点击重试"
                hasText -> summaryState.firstLine
                isGenerating -> "正在生成总结..."
                else -> "AI 总结"
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) Color(0xFFFF8A80) else textColor.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.KeyboardArrowRight,
                contentDescription = "查看详情",
                tint = textColor.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
internal fun AiFloatingButton(
    activeColor: Color,
    containerColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = if (isPressed) {
        activeColor.copy(alpha = 0.18f).compositeOver(containerColor)
    } else {
        containerColor
    }

    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (borderColor.alpha > 0f) Modifier.border(1.dp, borderColor, CircleShape)
                else Modifier
            )
            .clickableWithoutRipple(interactionSource = interactionSource, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = "AI 总结",
            tint = activeColor,
            modifier = Modifier
                .size(38.dp)
                .padding(watchDimensionResource(R.dimen.hey_distance_6dp))
        )
    }
}

@Composable
internal fun DetailTitle(
    title: String,
    titlePadding: Dp,
    textColor: Color
) {
    val hintSize = textSize(R.dimen.hey_m_title)
    val titleStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = hintSize,
        lineHeight = max(
            MaterialTheme.typography.titleMedium.lineHeight.value,
            hintSize.value * 1.24f
        ).sp
    )
    val context = LocalContext.current
    val density = LocalDensity.current
    val titleSizePx = with(density) { watchDimensionResource(R.dimen.hey_m_title).toPx() }
    val typeface = remember(context) { ResourcesCompat.getFont(context, R.font.watch_sans) }
    val paint = remember(typeface, titleSizePx) {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = titleSizePx
            this.typeface = typeface
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = titlePadding)
    ) {
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val (firstLimitPx, secondLimitPx) = remember(availableWidthPx, density) {
            rememberWatchTitleLineLimitsPx(availableWidthPx, density)
        }
        val formattedTitle = remember(title, availableWidthPx, paint) {
            formatWatchTitleForWidthLimits(
                title = title,
                paint = paint,
                availableWidthPx = availableWidthPx,
                firstLimitPx = firstLimitPx,
                secondLimitPx = secondLimitPx
            )
        }
        Text(
            text = formattedTitle,
            style = titleStyle,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun DetailActionButton(
    text: String,
    fontSize: TextUnit,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(WatchDimens.hey_button_default_radius)
    val padding = PaddingValues(
        horizontal = WatchDimens.hey_content_horizontal_distance,
        vertical = watchDimensionResource(R.dimen.hey_distance_6dp)
    )
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.72f))
            .then(
                if (borderColor.alpha > 0f) {
                    Modifier.border(
                        1.dp,
                        if (enabled) borderColor else borderColor.copy(alpha = 0.72f),
                        shape
                    )
                } else {
                    Modifier
                }
            )
            .clickableWithoutRipple(enabled = enabled, onClick = onClick)
            .padding(padding)
    ) {
        Text(
            text = text,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.72f),
            fontSize = fontSize
        )
    }
}

@Composable
internal fun FavoriteButtonWithStars(
    isFavorite: Boolean,
    activeColor: Color,
    normalIconColor: Color,
    containerColor: Color,
    borderColor: Color,
    iconSize: Dp,
    iconPadding: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var triggerAnimation by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier.size(iconSize),
        contentAlignment = Alignment.Center
    ) {
        if (triggerAnimation > 0) {
            StarParticles(
                key = triggerAnimation,
                color = activeColor
            )
        }

        CircleIconButton(
            icon = Icons.Filled.Star,
            contentDescription = "收藏",
            tint = if (isFavorite) activeColor else normalIconColor,
            containerColor = containerColor,
            borderColor = borderColor,
            size = iconSize,
            padding = iconPadding,
            enabled = enabled,
            onClick = {
                onClick()
                triggerAnimation++
            }
        )
    }
}

@Composable
private fun StarParticles(
    key: Int,
    color: Color
) {
    val particleCount = 8
    val angles = remember { List(particleCount) { it * 360f / particleCount } }

    angles.forEach { angle ->
        StarParticle(
            key = key,
            angle = angle,
            color = color
        )
    }
}

@Composable
private fun StarParticle(
    key: Int,
    angle: Float,
    color: Color
) {
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(key) {
        progress.snapTo(0f)
        scale.snapTo(0f)
        rotation.snapTo(0f)
        alpha.snapTo(1f)

        launch {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            rotation.animateTo(
                targetValue = 720f,
                animationSpec = tween(durationMillis = 1200)
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1200, delayMillis = 200)
            )
        }
    }

    val distance = 60f * progress.value
    val radians = Math.toRadians(angle.toDouble())
    val offsetX = (distance * kotlin.math.cos(radians)).toFloat()
    val offsetY = (distance * kotlin.math.sin(radians)).toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha.value),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "★",
            color = color,
            fontSize = with(LocalDensity.current) { (16.dp * scale.value).toSp() },
            modifier = Modifier
                .offset(
                    x = with(LocalDensity.current) { offsetX.toDp() },
                    y = with(LocalDensity.current) { offsetY.toDp() }
                )
                .scale(scale.value)
                .rotate(rotation.value)
        )
    }
}

@Composable
internal fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    containerColor: Color,
    borderColor: Color,
    size: Dp,
    padding: Dp,
    iconOffsetX: Dp = 0.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = if (isPressed && enabled) {
        if (containerColor == Color.Transparent) {
            containerColor
        } else {
            tint.copy(
                alpha = if (containerColor.red + containerColor.green + containerColor.blue > 1.8f) {
                    0.08f
                } else {
                    0.14f
                }
            ).compositeOver(containerColor)
        }
    } else {
        containerColor
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (borderColor.alpha > 0f) Modifier.border(1.dp, borderColor, CircleShape)
                else Modifier
            )
            .clickableWithoutRipple(
                enabled = enabled,
                interactionSource = interactionSource,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .offset(x = iconOffsetX),
            tint = tint
        )
    }
}

@Composable
private fun textSize(id: Int): TextUnit {
    val density = LocalDensity.current
    return with(density) { watchDimensionResource(id).toSp() }
}

@Composable
internal fun Modifier.clickableWithoutRipple(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit
): Modifier {
    return clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}
