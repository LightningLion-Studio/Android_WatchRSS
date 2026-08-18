package com.lightningstudio.watchrss.ui.screen.rss

import android.annotation.SuppressLint
import android.os.Trace
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withLink
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import com.lightningstudio.watchrss.ui.util.RssInlineImageLoader
import com.lightningstudio.watchrss.ui.util.TextStyle as ContentTextStyle
import com.lightningstudio.watchrss.ui.reader.ReaderTextRole
import com.lightningstudio.watchrss.ui.reader.readerTextStyle
import kotlinx.coroutines.delay

internal data class DetailTextHighlightRange(
    val start: Int,
    val end: Int
)

@Composable
@SuppressLint("UnclosedTrace")
internal fun DetailTextBlock(
    text: String,
    style: ContentTextStyle,
    textColor: Color,
    fontSizeSp: TextUnit,
    topPadding: Dp,
    isScrolling: Boolean,
    inlineActionText: String? = null,
    inlineActionColor: Color = Color(0xFF87CEEB),
    onInlineActionClick: (() -> Unit)? = null,
    highlightRange: DetailTextHighlightRange? = null,
    highlightColor: Color = Color.Transparent,
    onTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onLongClick: ((Offset) -> Unit)? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null
) {
    if (isDetailTracingEnabled()) {
        Trace.beginSection("DetailTextBlock:${style.name}")
    }
    val presetStyle = readerTextStyle(
        when (style) {
            ContentTextStyle.TITLE -> ReaderTextRole.TITLE
            ContentTextStyle.SUBTITLE -> ReaderTextRole.SUBTITLE
            ContentTextStyle.QUOTE -> ReaderTextRole.QUOTE
            ContentTextStyle.CODE -> ReaderTextRole.CODE
            else -> ReaderTextRole.BODY
        }
    )
    val annotatedText = remember(
        text,
        inlineActionText,
        inlineActionColor,
        onInlineActionClick,
        highlightRange,
        highlightColor
    ) {
        buildDetailTextAnnotatedString(
            text = text,
            inlineActionText = inlineActionText,
            inlineActionColor = inlineActionColor,
            onInlineActionClick = onInlineActionClick,
            highlightRange = highlightRange,
            highlightColor = highlightColor
        )
    }
    val gestureModifier = if (onLongClick == null && onTap == null && onDoubleTap == null) {
        Modifier
    } else {
        Modifier.pointerInput(onTap, onDoubleTap, onLongClick) {
            detectTapGestures(
                onTap = { onTap?.invoke() },
                onDoubleTap = { onDoubleTap?.invoke() },
                onLongPress = { offset ->
                    onLongClick?.invoke(offset)
                }
            )
        }
    }
    Text(
        text = annotatedText,
        style = presetStyle,
        onTextLayout = { result -> onTextLayout?.invoke(result) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .then(gestureModifier)
            .scrollSemanticsDisabled(isScrolling)
            .debugTraceLayout("DetailTextBlock/layout")
            .debugTraceDraw("DetailTextBlock/draw")
    )
    if (isDetailTracingEnabled()) {
        Trace.endSection()
    }
}

private fun buildDetailTextAnnotatedString(
    text: String,
    inlineActionText: String?,
    inlineActionColor: Color,
    onInlineActionClick: (() -> Unit)?,
    highlightRange: DetailTextHighlightRange?,
    highlightColor: Color
) = buildAnnotatedString {
    if (inlineActionText.isNullOrEmpty() || onInlineActionClick == null) {
        append(text)
    } else {
        val linkStyle = TextLinkStyles(
            style = SpanStyle(
                color = inlineActionColor,
                textDecoration = TextDecoration.Underline
            )
        )
        var searchStart = 0
        while (searchStart < text.length) {
            val matchIndex = text.indexOf(inlineActionText, startIndex = searchStart)
            if (matchIndex < 0) {
                append(text.substring(searchStart))
                break
            }
            if (matchIndex > searchStart) {
                append(text.substring(searchStart, matchIndex))
            }
            withLink(
                LinkAnnotation.Clickable(
                    tag = inlineActionText,
                    styles = linkStyle,
                    linkInteractionListener = { onInlineActionClick() }
                )
            ) {
                append(inlineActionText)
            }
            searchStart = matchIndex + inlineActionText.length
        }
    }

    val start = highlightRange?.start?.coerceIn(0, text.length) ?: return@buildAnnotatedString
    val end = highlightRange.end.coerceIn(start, text.length)
    if (start < end && highlightColor.alpha > 0f) {
        addStyle(
            style = SpanStyle(background = highlightColor),
            start = start,
            end = end
        )
    }
}

internal fun buildHighlightedPlainText(
    text: String,
    highlightRange: DetailTextHighlightRange?,
    highlightColor: Color
) = buildAnnotatedString {
    append(text)
    val start = highlightRange?.start?.coerceIn(0, text.length) ?: return@buildAnnotatedString
    val end = highlightRange.end.coerceIn(start, text.length)
    if (start < end && highlightColor.alpha > 0f) {
        addStyle(
            style = SpanStyle(background = highlightColor),
            start = start,
            end = end
        )
    }
}

@Composable
@SuppressLint("UnclosedTrace")
internal fun DetailImageBlock(
    url: String,
    alt: String?,
    initialAspectRatio: Float?,
    maxWidthPx: Int,
    containerColor: Color,
    borderColor: Color,
    topPadding: Dp,
    isScrolling: Boolean,
    onClick: () -> Unit
) {
    if (isDetailTracingEnabled()) {
        Trace.beginSection("DetailImageBlock")
    }
    val context = LocalContext.current
    val imageLoader = remember(context) { RssInlineImageLoader.get(context) }
    var ratio by remember(url, initialAspectRatio) {
        mutableStateOf(RssImageLoader.getCachedAspectRatio(url) ?: initialAspectRatio)
    }
    val request = remember(url, maxWidthPx, context) {
        RssInlineImageLoader.buildRequest(context, url, maxWidthPx)
    }
    val painter = rememberAsyncImagePainter(
        model = request,
        imageLoader = imageLoader,
        onSuccess = { success ->
            RssInlineImageLoader.cacheAspectRatio(url, success.result)
            val width = success.result.drawable.intrinsicWidth
            val height = success.result.drawable.intrinsicHeight
            ratio = if (width > 0 && height > 0) {
                width.toFloat() / height.toFloat()
            } else {
                RssImageLoader.getCachedAspectRatio(url) ?: ratio
            }
        },
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None
    )
    val aspectRatio = ratio
    if (painter.state is AsyncImagePainter.State.Success && aspectRatio != null) {
        Image(
            painter = painter,
            contentDescription = alt,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .aspectRatio(aspectRatio)
                .clickableWithRipple(enabled = !isScrolling, onClick = onClick)
                .scrollSemanticsDisabled(isScrolling)
                .debugTraceLayout("DetailImageBlock/layout")
                .debugTraceDraw("DetailImageBlock/draw"),
            contentScale = ContentScale.Fit
        )
    } else {
        val placeholderModifier = if (aspectRatio != null && aspectRatio > 0f) {
            Modifier.aspectRatio(aspectRatio)
        } else {
            Modifier.height(watchDimensionResource(R.dimen.hey_card_large_height))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .then(placeholderModifier)
                .background(containerColor)
                .then(
                    if (borderColor.alpha > 0f) {
                        Modifier.border(
                            1.dp,
                            borderColor,
                            androidx.compose.foundation.shape.RoundedCornerShape(
                                WatchDimens.hey_card_normal_bg_radius
                            )
                        )
                    } else {
                        Modifier
                    }
                )
                .scrollSemanticsDisabled(isScrolling)
                .debugTraceLayout("DetailImageBlock/placeholder/layout")
                .debugTraceDraw("DetailImageBlock/placeholder/draw")
        )
    }
    if (isDetailTracingEnabled()) {
        Trace.endSection()
    }
}

@Composable
@SuppressLint("UnclosedTrace")
internal fun DetailVideoBlock(
    poster: String?,
    videoUrl: String,
    maxWidthPx: Int,
    containerColor: Color,
    borderColor: Color,
    topPadding: Dp,
    isScrolling: Boolean,
    onClick: () -> Unit
) {
    if (isDetailTracingEnabled()) {
        Trace.beginSection("DetailVideoBlock")
    }
    val context = LocalContext.current
    val coverState = remember(poster, videoUrl, maxWidthPx) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val ratioState = remember(poster, videoUrl) { mutableStateOf<Float?>(null) }

    LaunchedEffect(poster, videoUrl, maxWidthPx, isScrolling) {
        if (isScrolling || coverState.value != null) return@LaunchedEffect
        if (poster.isNullOrBlank() && !canExtractVideoFrame(videoUrl)) return@LaunchedEffect
        delay(MEDIA_LOAD_IDLE_DELAY_MS)
        decodeSemaphore.acquire()
        try {
            if (coverState.value == null) {
                coverState.value = when {
                    !poster.isNullOrBlank() -> RssImageLoader.loadBitmap(context, poster, maxWidthPx)
                    canExtractVideoFrame(videoUrl) -> loadCachedVideoFrame(context, videoUrl, maxWidthPx)
                    else -> null
                }
            }
        } finally {
            decodeSemaphore.release()
        }
    }
    LaunchedEffect(poster, videoUrl, maxWidthPx, isScrolling) {
        if (isScrolling || ratioState.value != null) return@LaunchedEffect
        delay(MEDIA_LOAD_IDLE_DELAY_MS)
        ratioState.value = when {
            !poster.isNullOrBlank() ->
                RssImageLoader.getCachedAspectRatio(poster)
                    ?: if (isLocalMedia(poster)) {
                        RssImageLoader.preloadAndCacheRatio(context, poster, maxWidthPx)
                    } else {
                        null
                    }
            canExtractVideoFrame(videoUrl) -> loadCachedVideoRatio(context, videoUrl)
            else -> null
        }
    }

    val coverRatio = coverState.value?.let { it.width.toFloat() / it.height.toFloat() }
        ?: poster?.let { RssImageLoader.getCachedAspectRatio(it) }
        ?: ratioState.value
    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(WatchDimens.hey_card_normal_bg_radius)
    val cardModifier = if (borderColor.alpha > 0f) {
        Modifier
            .clip(cardShape)
            .border(1.dp, borderColor, cardShape)
    } else {
        Modifier
    }
    val coverHeight = watchDimensionResource(R.dimen.hey_card_large_height)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .then(cardModifier)
            .background(containerColor)
            .clickableWithRipple(enabled = !isScrolling, onClick = onClick)
            .scrollSemanticsDisabled(isScrolling)
            .debugTraceLayout("DetailVideoBlock/layout")
            .debugTraceDraw("DetailVideoBlock/draw")
    ) {
        val safeCover = coverState.value
        val coverBitmap = remember(safeCover) { safeCover?.asImageBitmap() }
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = "视频封面",
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (coverRatio != null && coverRatio > 0f) {
                            Modifier.aspectRatio(coverRatio)
                        } else {
                            Modifier.height(coverHeight)
                        }
                    )
            )
        } else {
            val placeholderRatio = coverRatio ?: DEFAULT_VIDEO_ASPECT_RATIO
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (placeholderRatio > 0f) {
                            Modifier.aspectRatio(placeholderRatio)
                        } else {
                            Modifier.height(coverHeight)
                        }
                    )
            )
        }
        androidx.compose.material3.Icon(
            imageVector = Icons.Filled.PlayCircle,
            contentDescription = "播放",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.Center)
                .size(watchDimensionResource(R.dimen.hey_listitem_widget_size))
        )
    }
    if (isDetailTracingEnabled()) {
        Trace.endSection()
    }
}
