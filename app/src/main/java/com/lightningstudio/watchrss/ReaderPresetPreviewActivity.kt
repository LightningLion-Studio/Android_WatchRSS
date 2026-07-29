package com.lightningstudio.watchrss

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightningstudio.watchrss.data.reader.ReaderPresetRepository
import com.lightningstudio.watchrss.ui.reader.LocalReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.ReaderBackgroundSurface
import com.lightningstudio.watchrss.ui.reader.ReaderPageLayout
import com.lightningstudio.watchrss.ui.reader.ReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.ReaderTextRole
import com.lightningstudio.watchrss.ui.reader.readerTextStyle
import com.lightningstudio.watchrss.ui.screen.rss.DetailTitle
import com.lightningstudio.watchrss.ui.screen.rss.CircleIconButton
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class ReaderPresetPreviewActivity : BaseWatchActivity() {
    private val app by lazy { application as WatchRssApplication }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            WatchRSSTheme {
                WatchReaderPresetLivePreview(
                    repository = app.container.readerPresetRepository,
                    application = app,
                    onExpired = ::finish
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
    }

    override fun onPause() {
        isVisible = false
        super.onPause()
    }

    companion object {
        @Volatile
        var isVisible: Boolean = false
            private set
    }
}

@Composable
private fun WatchReaderPresetLivePreview(
    repository: ReaderPresetRepository,
    application: WatchRssApplication,
    onExpired: () -> Unit
) {
    val state = application.readerPresetPreviewSession.state.collectAsStateWithLifecycle().value
    LaunchedEffect(state) {
        if (state == null) onExpired()
    }
    val preview = state ?: return
    val context = LocalContext.current
    val showUnsupportedAction = {
        Toast.makeText(context, "预览页面不支持收藏/分享", Toast.LENGTH_SHORT).show()
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalReaderPresetRuntime provides ReaderPresetRuntime(
            preset = preview.preset,
            fontFile = repository::fontFile,
            backgroundFile = repository::backgroundFile
        )
    ) {
        ReaderBackgroundSurface(modifier = Modifier.fillMaxSize()) {
            Text(
                text = if (preview.resourceTransferInProgress) {
                    "资源文件传输中"
                } else {
                    "预设实时预览"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            )
            val pagePadding = ReaderPageLayout.horizontalPadding
            val blockSpacing = ReaderPageLayout.blockSpacing
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = pagePadding,
                        top = ReaderPageLayout.titleTopPadding,
                        end = pagePadding,
                        bottom = ReaderPageLayout.bottomPadding(hasFloatingAction = false)
                    ),
                verticalArrangement = Arrangement.spacedBy(blockSpacing)
            ) {
                DetailTitle(
                    title = "阅读，让时间慢下来",
                    titlePadding = ReaderPageLayout.titleHorizontalPadding,
                    textColor = androidx.compose.ui.graphics.Color(preview.preset.body.colorArgb)
                )
                Text("标题、副标题与正文会分别使用分类样式", style = readerTextStyle(ReaderTextRole.SUBTITLE))
                Text(
                    "这是手表端的实时正文预览。你在手机上调整字体、字号、颜色、排版或背景后，这里会自动更新。",
                    style = readerTextStyle(ReaderTextRole.BODY)
                )
                Text("“好的排版让文字安静地抵达读者。”", style = readerTextStyle(ReaderTextRole.QUOTE))
                Text("val preview = ReaderPreset.live", style = readerTextStyle(ReaderTextRole.CODE))
                Text(
                    "链接样式预览 · watchrss.app",
                    style = readerTextStyle(ReaderTextRole.LINK),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "继续向下阅读时，可以观察正文在圆形屏幕不同高度上的换行和边缘留白。预览内容会保留足够长度，方便检查滚动过程。",
                    style = readerTextStyle(ReaderTextRole.BODY)
                )
                Text(
                    "较长文章往往包含多个段落。这里再加入一段常规文字，用来确认段间距、首行缩进和两端对齐在连续内容中的实际效果。",
                    style = readerTextStyle(ReaderTextRole.BODY)
                )
                Text(
                    "到达文章结尾前，还应留出收藏与分享操作的位置，使最后一段文字能够完整滚动到圆屏中央，而不是停留在底部曲面区域。",
                    style = readerTextStyle(ReaderTextRole.BODY)
                )
                Spacer(modifier = Modifier.height(15.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircleIconButton(
                            icon = Icons.Outlined.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = MaterialTheme.colorScheme.onSurface,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            borderColor = Color.Transparent,
                            size = 32.dp,
                            padding = 6.dp,
                            onClick = showUnsupportedAction
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        CircleIconButton(
                            icon = Icons.Outlined.Share,
                            contentDescription = "分享",
                            tint = MaterialTheme.colorScheme.onSurface,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            borderColor = Color.Transparent,
                            size = 32.dp,
                            padding = 6.dp,
                            iconOffsetX = (-1).dp,
                            onClick = showUnsupportedAction
                        )
                    }
                }
            }
        }
    }
}
