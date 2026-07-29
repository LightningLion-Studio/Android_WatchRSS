package com.lightningstudio.watchrss

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightningstudio.watchrss.data.reader.ReaderPresetRepository
import com.lightningstudio.watchrss.ui.reader.LocalReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.ReaderBackgroundSurface
import com.lightningstudio.watchrss.ui.reader.ReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.ReaderTextRole
import com.lightningstudio.watchrss.ui.reader.readerTextStyle
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
    androidx.compose.runtime.CompositionLocalProvider(
        LocalReaderPresetRuntime provides ReaderPresetRuntime(
            preset = preview.preset,
            fontFile = repository::fontFile,
            backgroundFile = repository::backgroundFile
        )
    ) {
        ReaderBackgroundSurface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "手机实时预览",
                    color = Color(preview.preset.accentColorArgb),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(
                            Color(preview.preset.accentColorArgb).copy(alpha = 0.14f),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
                Text("阅读，让时间慢下来", style = readerTextStyle(ReaderTextRole.TITLE))
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
            }
        }
    }
}
