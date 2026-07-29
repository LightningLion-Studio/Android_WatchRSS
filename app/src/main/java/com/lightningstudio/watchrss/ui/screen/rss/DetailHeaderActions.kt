package com.lightningstudio.watchrss.ui.screen.rss

import android.graphics.Paint
import android.text.TextPaint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
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
            .clickableWithRipple(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isGenerating && !hasText) {
                WatchCircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = activeColor,
                    strokeWidth = 1.5.dp
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
        summaryState.status == SummaryStatus.WaitingForContent -> "等待原文加载..."
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
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
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
            .clickableWithRipple(interactionSource = interactionSource, onClick = onClick),
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
    textColor: Color,
    highlightRange: DetailTextHighlightRange? = null,
    highlightColor: Color = Color.Transparent,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null
) {
    val presetTitleStyle = com.lightningstudio.watchrss.ui.reader.readerTextStyle(
        com.lightningstudio.watchrss.ui.reader.ReaderTextRole.TITLE
    )
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
        val highlightedTitle = remember(formattedTitle, highlightRange, highlightColor) {
            buildHighlightedPlainText(
                text = formattedTitle,
                highlightRange = highlightRange,
                highlightColor = highlightColor
            )
        }
        Text(
            text = highlightedTitle,
            style = presetTitleStyle,
            onTextLayout = { result -> onTextLayout?.invoke(result) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DetailActionButton(
    text: String,
    fontSize: TextUnit,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(WatchDimens.hey_button_default_radius)
    val padding = PaddingValues(
        horizontal = WatchDimens.hey_content_horizontal_distance,
        vertical = watchDimensionResource(R.dimen.hey_distance_6dp)
    )
    val interactionSource = remember { MutableInteractionSource() }
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
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
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
    var starBurstKey by remember { mutableStateOf(0) }
    var buttonPulseKey by remember { mutableStateOf(0) }
    var pulseColor by remember { mutableStateOf(activeColor) }

    val buttonScale = remember { Animatable(1f) }
    val buttonAlpha = remember { Animatable(1f) }
    val buttonBlur = remember { Animatable(0f) }
    val buttonBrightness = remember { Animatable(0f) }
    val auraScale = remember { Animatable(0.82f) }
    val auraAlpha = remember { Animatable(0f) }
    val auraBlur = remember { Animatable(0f) }
    val auraBrightness = remember { Animatable(0f) }

    LaunchedEffect(buttonPulseKey) {
        if (buttonPulseKey == 0) return@LaunchedEffect

        buttonScale.snapTo(0.96f)
        buttonAlpha.snapTo(0.94f)
        buttonBlur.snapTo(0f)
        buttonBrightness.snapTo(0.08f)
        auraScale.snapTo(0.82f)
        auraAlpha.snapTo(0f)
        auraBlur.snapTo(0f)
        auraBrightness.snapTo(0.1f)

        launch {
            buttonScale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 1260
                    1.09f at 180 using FastOutSlowInEasing
                    1.03f at 520 using LinearOutSlowInEasing
                    0.995f at 920 using LinearOutSlowInEasing
                    1f at 1260
                }
            )
        }
        launch {
            buttonAlpha.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 1260
                    1f at 160 using FastOutSlowInEasing
                    0.98f at 420 using LinearOutSlowInEasing
                    1f at 1260
                }
            )
        }
        launch {
            buttonBlur.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 1260
                    2.4f at 180 using FastOutSlowInEasing
                    0.7f at 540 using LinearOutSlowInEasing
                    0f at 1260
                }
            )
        }
        launch {
            buttonBrightness.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 1260
                    0.48f at 170 using FastOutSlowInEasing
                    0.22f at 520 using LinearOutSlowInEasing
                    0f at 1260
                }
            )
        }
        launch {
            auraScale.animateTo(
                targetValue = 1.48f,
                animationSpec = tween(
                    durationMillis = 1380,
                    easing = LinearOutSlowInEasing
                )
            )
        }
        launch {
            auraAlpha.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 1380
                    0.34f at 150 using FastOutSlowInEasing
                    0.18f at 520 using LinearOutSlowInEasing
                    0f at 1380
                }
            )
        }
        launch {
            auraBlur.animateTo(
                targetValue = 10f,
                animationSpec = tween(
                    durationMillis = 1380,
                    easing = LinearOutSlowInEasing
                )
            )
        }
        launch {
            auraBrightness.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 1380
                    0.42f at 180 using FastOutSlowInEasing
                    0.12f at 560 using LinearOutSlowInEasing
                    0f at 1380
                }
            )
        }
    }

    Box(
        modifier = Modifier.size(iconSize),
        contentAlignment = Alignment.Center
    ) {
        if (buttonPulseKey > 0) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = auraScale.value
                        scaleY = auraScale.value
                        alpha = auraAlpha.value
                    }
                    .then(if (auraBlur.value > 0.1f) Modifier.blur(auraBlur.value.dp) else Modifier)
                    .clip(CircleShape)
                    .background(pulseColor.copy(alpha = 0.24f))
                    .favoriteBrightnessOverlay(
                        amount = auraBrightness.value,
                        tintColor = pulseColor
                    )
            )
        }

        if (starBurstKey > 0) {
            Box(
                modifier = Modifier.requiredSize(iconSize + 84.dp),
                contentAlignment = Alignment.Center
            ) {
                StarParticles(
                    key = starBurstKey,
                    color = activeColor
                )
            }
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = buttonScale.value
                    scaleY = buttonScale.value
                    alpha = buttonAlpha.value
                }
                .then(if (buttonBlur.value > 0.1f) Modifier.blur(buttonBlur.value.dp) else Modifier)
                .favoriteBrightnessOverlay(
                    amount = buttonBrightness.value,
                    tintColor = pulseColor
                )
        ) {
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
                    pulseColor = if (isFavorite) normalIconColor else activeColor
                    buttonPulseKey++
                    if (!isFavorite) {
                        starBurstKey++
                    }
                    onClick()
                }
            )
        }
    }
}

@Composable
private fun StarParticles(
    key: Int,
    color: Color
) {
    val particles = remember {
        listOf(
            StarParticleSpec(angle = -92f, distance = 34f, size = 8.dp, delayMillis = 0, rotationTarget = 220f),
            StarParticleSpec(angle = -56f, distance = 42f, size = 10.dp, delayMillis = 36, rotationTarget = -260f),
            StarParticleSpec(angle = -18f, distance = 36f, size = 8.dp, delayMillis = 72, rotationTarget = 180f),
            StarParticleSpec(angle = 18f, distance = 46f, size = 11.dp, delayMillis = 118, rotationTarget = -300f),
            StarParticleSpec(angle = 54f, distance = 38f, size = 9.dp, delayMillis = 164, rotationTarget = 240f),
            StarParticleSpec(angle = 98f, distance = 30f, size = 7.dp, delayMillis = 92, rotationTarget = -180f),
            StarParticleSpec(angle = 134f, distance = 40f, size = 9.dp, delayMillis = 146, rotationTarget = 260f),
            StarParticleSpec(angle = 170f, distance = 32f, size = 8.dp, delayMillis = 56, rotationTarget = -220f),
            StarParticleSpec(angle = 214f, distance = 36f, size = 8.dp, delayMillis = 126, rotationTarget = 200f),
            StarParticleSpec(angle = 252f, distance = 30f, size = 7.dp, delayMillis = 182, rotationTarget = -210f)
        )
    }

    particles.forEach { particle ->
        StarParticle(
            key = key,
            spec = particle,
            color = color
        )
    }
}

private data class StarParticleSpec(
    val angle: Float,
    val distance: Float,
    val size: Dp,
    val delayMillis: Int,
    val rotationTarget: Float
)

@Composable
private fun StarParticle(
    key: Int,
    spec: StarParticleSpec,
    color: Color
) {
    val progress = remember { Animatable(0f) }
    val starScale = remember { Animatable(0.4f) }
    val glowScale = remember { Animatable(0.75f) }
    val rotation = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(1f) }
    val blur = remember { Animatable(5f) }
    val brightness = remember { Animatable(0.35f) }

    LaunchedEffect(key) {
        progress.snapTo(0f)
        starScale.snapTo(0.4f)
        glowScale.snapTo(0.75f)
        rotation.snapTo(0f)
        alphaAnim.snapTo(0f)
        blur.snapTo(5f)
        brightness.snapTo(0.35f)

        launch {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 1680,
                    delayMillis = spec.delayMillis,
                    easing = LinearOutSlowInEasing
                )
            )
        }
        launch {
            starScale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 1680
                    delayMillis = spec.delayMillis
                    1.28f at 240 using FastOutSlowInEasing
                    1.04f at 620 using LinearOutSlowInEasing
                    0.86f at 1240 using LinearOutSlowInEasing
                    0.72f at 1680
                }
            )
        }
        launch {
            glowScale.animateTo(
                targetValue = 1.52f,
                animationSpec = tween(
                    durationMillis = 1680,
                    delayMillis = spec.delayMillis,
                    easing = LinearOutSlowInEasing
                )
            )
        }
        launch {
            rotation.animateTo(
                targetValue = spec.rotationTarget,
                animationSpec = tween(
                    durationMillis = 1680,
                    delayMillis = spec.delayMillis,
                    easing = LinearOutSlowInEasing
                )
            )
        }
        launch {
            alphaAnim.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 1680
                    delayMillis = spec.delayMillis
                    0.96f at 200 using FastOutSlowInEasing
                    0.72f at 820 using LinearOutSlowInEasing
                    0.14f at 1380 using LinearOutSlowInEasing
                    0f at 1680
                }
            )
        }
        launch {
            blur.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 1680
                    delayMillis = spec.delayMillis
                    3.4f at 240 using FastOutSlowInEasing
                    1.2f at 860 using LinearOutSlowInEasing
                    0f at 1680
                }
            )
        }
        launch {
            brightness.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 1680
                    delayMillis = spec.delayMillis
                    0.56f at 220 using FastOutSlowInEasing
                    0.22f at 840 using LinearOutSlowInEasing
                    0f at 1680
                }
            )
        }
    }

    val easedProgress = FastOutSlowInEasing.transform(progress.value.coerceIn(0f, 1f))
    val distance = spec.distance * easedProgress
    val radians = Math.toRadians(spec.angle.toDouble())
    val offsetX = (distance * kotlin.math.cos(radians)).toFloat()
    val offsetY = (distance * kotlin.math.sin(radians)).toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "★",
            color = color.copy(alpha = 0.72f),
            fontSize = with(LocalDensity.current) { spec.size.toSp() },
            modifier = Modifier
                .offset(
                    x = with(LocalDensity.current) { offsetX.toDp() },
                    y = with(LocalDensity.current) { offsetY.toDp() }
                )
                .graphicsLayer {
                    scaleX = glowScale.value
                    scaleY = glowScale.value
                    alpha = alphaAnim.value * 0.78f
                }
                .then(if (blur.value > 0.1f) Modifier.blur(blur.value.dp) else Modifier)
                .favoriteBrightnessOverlay(
                    amount = brightness.value,
                    tintColor = color
                )
        )
        Text(
            text = "★",
            color = color,
            fontSize = with(LocalDensity.current) { spec.size.toSp() },
            modifier = Modifier
                .offset(
                    x = with(LocalDensity.current) { offsetX.toDp() },
                    y = with(LocalDensity.current) { offsetY.toDp() }
                )
                .graphicsLayer {
                    scaleX = starScale.value
                    scaleY = starScale.value
                    alpha = alphaAnim.value
                }
                .rotate(rotation.value)
        )
    }
}

private fun Modifier.favoriteBrightnessOverlay(
    amount: Float,
    tintColor: Color
): Modifier {
    if (amount <= 0f) return this
    return drawWithContent {
        drawContent()
        drawRect(
            color = tintColor.copy(alpha = amount.coerceIn(0f, 1f) * 0.28f)
        )
        drawRect(
            color = Color.White.copy(alpha = amount.coerceIn(0f, 1f) * 0.14f)
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
            .clickableWithRipple(
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
internal fun Modifier.clickableWithRipple(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit
): Modifier {
    return clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = onClick
    )
}
