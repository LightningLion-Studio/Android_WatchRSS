package com.lightningstudio.watchrss.ui.screen

import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.WebViewScaleMode
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.widget.ProgressRingView
import com.lightningstudio.watchrss.ui.util.getWebViewUnavailableMessage
import com.lightningstudio.watchrss.util.AppLogger
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PanoramaFishEye
import kotlin.math.sqrt

@Composable
fun WebViewScreen(
    backgroundColor: Color,
    errorMessage: String?,
    scaleMode: WebViewScaleMode,
    onToggleScaleMode: () -> Unit,
    onWebViewReady: (WebView) -> Unit,
    onWebViewInitFailed: (String) -> Unit,
    onProgressRingReady: (ProgressRingView) -> Unit
) {
    val safePadding = WatchDimens.watch_safe_padding
    val safeVerticalPadding = WatchDimens.watch_safe_vertical_padding
    val controlSize = watchDimensionResource(R.dimen.hey_button_height)
    val iconSize = watchDimensionResource(R.dimen.hey_listitem_widget_size)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        val viewportWidth = (maxWidth - safePadding * 2).coerceAtLeast(maxWidth * 0.1f)
        val viewportHeight = (maxHeight - safeVerticalPadding * 2).coerceAtLeast(maxHeight * 0.1f)
        val expandedScale = (maxWidth / viewportWidth).coerceAtLeast(1f)
        val shrunkScale = run {
            val diagonal = sqrt(
                viewportWidth.value * viewportWidth.value +
                    viewportHeight.value * viewportHeight.value
            )
            if (diagonal > 0f) {
                (maxWidth.value / diagonal).coerceAtMost(1f)
            } else {
                1f
            }
        }
        val viewportScale = when (scaleMode) {
            WebViewScaleMode.Standard -> 1f
            WebViewScaleMode.Expanded -> expandedScale
            WebViewScaleMode.Shrunk -> shrunkScale
        }
        val targetWidth = viewportWidth * viewportScale
        val targetHeight = viewportHeight * viewportScale
        val targetWidthPx = with(density) { targetWidth.roundToPx() }
        val targetHeightPx = with(density) { targetHeight.roundToPx() }

        if (!errorMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = safePadding,
                        top = safeVerticalPadding,
                        end = safePadding,
                        bottom = safeVerticalPadding
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            AndroidView(
                factory = { context ->
                    try {
                        WebView(context).also(onWebViewReady)
                    } catch (throwable: Throwable) {
                        AppLogger.e("WebViewScreen", "Failed to initialize WebView", throwable)
                        val message = getWebViewUnavailableMessage(context)
                            ?: "当前设备无法初始化 WebView，无法打开此页面"
                        FrameLayout(context).apply {
                            post { onWebViewInitFailed(message) }
                        }
                    }
                },
                update = { view ->
                    val existing = view.layoutParams
                    val needsUpdate = existing == null ||
                        existing.width != targetWidthPx ||
                        existing.height != targetHeightPx
                    if (needsUpdate) {
                        view.layoutParams = FrameLayout.LayoutParams(targetWidthPx, targetHeightPx)
                        view.requestLayout()
                        view.forceLayout()
                        view.invalidate()
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(targetWidth, targetHeight)
            )
            AndroidView(
                factory = { context ->
                    ProgressRingView(context).also(onProgressRingReady)
                },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = safePadding, vertical = safeVerticalPadding)
            ) {
                val scaleToggleAction = scaleMode.toggleAction()
                WebViewIconButton(
                    icon = scaleToggleAction.icon,
                    contentDescription = scaleToggleAction.contentDescription,
                    size = controlSize,
                    iconSize = iconSize,
                    modifier = Modifier.align(Alignment.Center),
                    onClick = onToggleScaleMode
                )
            }
        }
    }
}

private data class WebViewScaleToggleAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: String
)

private fun WebViewScaleMode.toggleAction(): WebViewScaleToggleAction {
    return when (this) {
        WebViewScaleMode.Standard -> WebViewScaleToggleAction(
            icon = Icons.Filled.Fullscreen,
            contentDescription = "放大"
        )
        WebViewScaleMode.Expanded -> WebViewScaleToggleAction(
            icon = Icons.Filled.PanoramaFishEye,
            contentDescription = "缩小"
        )
        WebViewScaleMode.Shrunk -> WebViewScaleToggleAction(
            icon = Icons.Filled.FullscreenExit,
            contentDescription = "标准"
        )
    }
}

@Composable
private fun WebViewIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize)
        )
    }
}
