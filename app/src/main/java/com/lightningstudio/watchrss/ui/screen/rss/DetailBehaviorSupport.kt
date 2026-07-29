package com.lightningstudio.watchrss.ui.screen.rss

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.Trace
import android.view.View
import android.widget.Toast
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.ImagePreviewActivity
import com.lightningstudio.watchrss.RssPlayerActivity
import com.lightningstudio.watchrss.ShareQrActivity
import com.lightningstudio.watchrss.WebViewActivity
import com.lightningstudio.watchrss.data.douyin.parseDouyinAwemeId
import com.lightningstudio.watchrss.ui.util.showAppToast
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

private const val DETAIL_SHARE_QR_WIDTH_RATIO = 0.7f

@SuppressLint("UnclosedTrace")
internal fun calculateReadingProgress(listState: androidx.compose.foundation.lazy.LazyListState): Float {
    val tracingEnabled = isDetailTracingEnabled()
    if (tracingEnabled) {
        Trace.beginSection("ReadingProgress")
    }
    return try {
        val layoutInfo = listState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems == 0) {
            return 1f
        }
        if (layoutInfo.visibleItemsInfo.isNotEmpty() && !listState.canScrollForward) {
            return 1f
        }
        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        val firstSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
        val offsetProgress = if (firstSize > 0) firstOffset.toFloat() / firstSize.toFloat() else 0f
        val denominator = (totalItems - 1).coerceAtLeast(1)
        val rawProgress = (firstIndex + offsetProgress) / denominator.toFloat()
        rawProgress.coerceIn(0f, 1f)
    } finally {
        if (tracingEnabled) {
            Trace.endSection()
        }
    }
}

internal fun calculateImportedTextReadingProgress(
    listState: androidx.compose.foundation.lazy.LazyListState,
    firstChunkItemIndex: Int,
    chunkCount: Int
): Float {
    if (chunkCount <= 0) return calculateReadingProgress(listState)
    val firstVisibleChunkIndex = listState.firstVisibleItemIndex - firstChunkItemIndex
    if (firstVisibleChunkIndex < 0) return 0f
    if (listState.layoutInfo.visibleItemsInfo.isNotEmpty() && !listState.canScrollForward) return 1f

    val firstSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
    val offsetProgress = if (firstSize > 0) {
        listState.firstVisibleItemScrollOffset.toFloat() / firstSize.toFloat()
    } else {
        0f
    }
    return calculateImportedTextReadingProgressFromPosition(
        firstVisibleChunkIndex = firstVisibleChunkIndex,
        firstVisibleItemScrollOffsetProgress = offsetProgress,
        chunkCount = chunkCount
    )
}

internal fun calculateImportedTextReadingProgressFromPosition(
    firstVisibleChunkIndex: Int,
    firstVisibleItemScrollOffsetProgress: Float,
    chunkCount: Int
): Float {
    if (chunkCount <= 0) return 1f
    if (firstVisibleChunkIndex < 0) return 0f
    if (firstVisibleChunkIndex >= chunkCount) return 1f
    val denominator = (chunkCount - 1).coerceAtLeast(1)
    return ((firstVisibleChunkIndex + firstVisibleItemScrollOffsetProgress) / denominator.toFloat()).coerceIn(0f, 1f)
}

internal data class ImportedTextRestoreTarget(
    val itemIndex: Int,
    val itemScrollOffsetProgress: Float
)

internal data class ImportedTextByteRestoreTarget(
    val itemIndex: Int,
    val chunkIndex: Int,
    val byteOffsetInChunk: Int
)

internal fun importedTextRestoreTarget(
    progress: Float,
    firstChunkItemIndex: Int,
    chunkCount: Int
): ImportedTextRestoreTarget {
    if (chunkCount <= 0) {
        return ImportedTextRestoreTarget(firstChunkItemIndex.coerceAtLeast(0), 0f)
    }
    val denominator = (chunkCount - 1).coerceAtLeast(1)
    val scaled = denominator * progress.coerceIn(0f, 1f)
    val chunkIndex = floor(scaled.toDouble()).toInt()
        .coerceIn(0, chunkCount - 1)
    val offsetProgress = if (chunkIndex >= chunkCount - 1) {
        0f
    } else {
        (scaled - chunkIndex).coerceIn(0f, 1f)
    }
    return ImportedTextRestoreTarget(
        itemIndex = firstChunkItemIndex + chunkIndex,
        itemScrollOffsetProgress = offsetProgress
    )
}

internal fun importedTextByteRestoreTarget(
    progress: Float,
    firstChunkItemIndex: Int,
    byteLength: Long,
    chunkCount: Int,
    chunkBytes: Int
): ImportedTextByteRestoreTarget {
    if (byteLength <= 0L || chunkCount <= 0 || chunkBytes <= 0) {
        return ImportedTextByteRestoreTarget(
            itemIndex = firstChunkItemIndex.coerceAtLeast(0),
            chunkIndex = 0,
            byteOffsetInChunk = 0
        )
    }
    val maxByte = (byteLength - 1L).coerceAtLeast(0L)
    val absoluteByte = (byteLength.toDouble() * progress.coerceIn(0f, 1f).toDouble())
        .roundToLong()
        .coerceIn(0L, maxByte)
    val chunkIndex = (absoluteByte / chunkBytes.toLong())
        .toInt()
        .coerceIn(0, chunkCount - 1)
    val byteOffsetInChunk = (absoluteByte - chunkIndex.toLong() * chunkBytes.toLong())
        .toInt()
        .coerceAtLeast(0)
    return ImportedTextByteRestoreTarget(
        itemIndex = firstChunkItemIndex + chunkIndex,
        chunkIndex = chunkIndex,
        byteOffsetInChunk = byteOffsetInChunk
    )
}

internal fun Modifier.debugTraceLayout(name: String): Modifier {
    if (!isDetailTracingEnabled()) return this
    return this.layout { measurable, constraints ->
        Trace.beginSection(name)
        try {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        } finally {
            Trace.endSection()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.scrollSemanticsDisabled(isScrolling: Boolean): Modifier {
    if (!isScrolling) return this
    return this.semantics { invisibleToUser() }
}

internal fun Modifier.debugTraceDraw(name: String): Modifier {
    if (!isDetailTracingEnabled()) return this
    return this.drawWithContent {
        Trace.beginSection(name)
        try {
            drawContent()
        } finally {
            Trace.endSection()
        }
    }
}

internal fun View.captureAccessibilityDelegate(): View.AccessibilityDelegate? {
    return runCatching {
        val field = View::class.java.getDeclaredField("mAccessibilityDelegate")
        field.isAccessible = true
        field.get(this) as? View.AccessibilityDelegate
    }.getOrNull()
}

internal fun isDetailTracingEnabled(): Boolean {
    return BuildConfig.DEBUG &&
        Trace.isEnabled()
}

internal fun isReachedBottom(
    listState: androidx.compose.foundation.lazy.LazyListState,
    thresholdPx: Float
): Boolean {
    val layoutInfo = listState.layoutInfo
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return false
    val bottom = lastVisible.offset + lastVisible.size
    return lastVisible.index >= layoutInfo.totalItemsCount - 1 &&
        bottom >= layoutInfo.viewportEndOffset - thresholdPx
}

internal suspend fun maybeSaveReadingProgress(
    readingProgress: Float,
    force: Boolean,
    lastSavedProgress: () -> Float,
    lastProgressSavedAt: () -> Long,
    updateLastSavedProgress: (Float) -> Unit,
    updateLastProgressSavedAt: (Long) -> Unit,
    onSave: suspend (Float) -> Unit
) {
    val clamped = readingProgress.coerceIn(0f, 1f)
    val now = SystemClock.elapsedRealtime()
    if (!force && lastSavedProgress() >= 0f) {
        val diff = abs(clamped - lastSavedProgress())
        if (diff < 0.02f && now - lastProgressSavedAt() < 1500L) return
    }
    updateLastSavedProgress(clamped)
    updateLastProgressSavedAt(now)
    onSave(clamped)
}

internal fun openLinkInApp(context: Context, link: String) {
    val trimmed = link.trim()
    if (trimmed.isEmpty()) return
    WebViewActivity.open(context, trimmed)
}

internal fun shareCurrent(context: Context, title: String, link: String?) {
    if (title.isBlank()) return
    val text = if (!link.isNullOrBlank()) "$title\n$link" else title
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

internal fun showShareQr(context: Context, title: String, link: String?) {
    val trimmed = link?.trim().orEmpty()
    if (trimmed.isEmpty()) {
        showAppToast(context, "暂无可分享链接", Toast.LENGTH_SHORT)
        return
    }
    context.startActivity(
        ShareQrActivity.createIntent(
            context = context,
            title = title,
            link = trimmed,
            qrWidthRatio = DETAIL_SHARE_QR_WIDTH_RATIO
        )
    )
}

internal fun openExternalLink(context: Context, link: String) {
    val trimmed = link.trim()
    if (trimmed.isEmpty()) return
    val uri = if (trimmed.startsWith("/")) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(trimmed))
    } else {
        trimmed.toUri()
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(intent)
}

internal fun openImagePreview(context: Context, url: String, alt: String?) {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return
    context.startActivity(ImagePreviewActivity.createIntent(context, trimmed, alt))
}

internal fun openRssVideo(context: Context, playUrl: String, webUrl: String?, channelId: Long = 0L) {
    val trimmed = playUrl.trim()
    if (trimmed.isEmpty()) return
    val trimmedWebUrl = webUrl?.trim()?.takeIf { it.isNotEmpty() }
    val awemeId = parseDouyinAwemeId(trimmedWebUrl) ?: parseDouyinAwemeId(trimmed)
    context.startActivity(
        RssPlayerActivity.createIntent(
            context = context,
            playUrl = trimmed,
            webUrl = trimmedWebUrl,
            awemeId = awemeId,
            channelId = channelId
        )
    )
}
