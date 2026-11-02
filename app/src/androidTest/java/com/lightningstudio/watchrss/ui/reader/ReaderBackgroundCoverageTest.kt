package com.lightningstudio.watchrss.ui.reader

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.lightningstudio.watchrss.ui.theme.LocalRubberBandOverscrollOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lightningstudio.watchrss.data.reader.ReaderBackground
import com.lightningstudio.watchrss.data.reader.ReaderPreset
import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.settings.RssInlineImagePrefetchMode
import com.lightningstudio.watchrss.ui.screen.rss.DetailContent
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.util.ContentBlock
import com.lightningstudio.watchrss.ui.util.TextStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Real DetailContent, synthetic article, pixels sampled while the boundary drag is held. */
class ReaderBackgroundCoverageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun shortLightRss_bottomBackgroundRemainsCovered() = checkCoverage(false, false, 14, 0xFFF0E7D8)
    @Test fun longLightNovel_largeFont_bottomBackgroundRemainsCovered() = checkCoverage(true, true, 24, 0xFFF0E7D8)
    @Test fun shortDarkNovel_bottomBackgroundRemainsCovered() = checkCoverage(false, true, 18, 0xFF24303B)
    @Test fun longDarkRss_bottomBackgroundRemainsCovered() = checkCoverage(true, false, 24, 0xFF24303B)

    @Test fun resolvedThemeChange_keepsNewBackgroundAtBottom() =
        checkCoverage(false, false, 18, 0xFFF0E7D8, nextBackground = 0xFF24303B)

    @Test fun lightReaderTopEdge_isAlsoCovered() =
        checkCoverage(false, true, 14, 0xFFF0E7D8, topEdge = true)

    @Test fun regularChildScrollingStillWorksInsideReaderBoundary() {
        lateinit var scrollingState: LazyListState
        compose.setContent {
            WatchRSSTheme {
                ReaderBackgroundSurface(Modifier.fillMaxSize().readerViewportBoundary()) {
                    val list = rememberLazyListState()
                    SideEffect { scrollingState = list }
                    LazyColumn(modifier = Modifier.fillMaxSize(), state = list) { items(100) { Text("正常滚动 $it") } }
                }
            }
        }
        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(scrollingState.firstVisibleItemIndex > 0) }
    }

    @Test fun otherModulesRetainTheirGlobalRubberBandBehavior() {
        var offset = 0f
        compose.setContent {
            WatchRSSTheme {
                val currentOffset = LocalRubberBandOverscrollOffset.current?.value ?: 0f
                SideEffect { offset = currentOffset }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Text("其他模块") }
            }
        }
        val root = compose.onRoot()
        root.performTouchInput {
            down(Offset(width / 2f, height * .2f))
            moveBy(Offset(0f, height * .5f), 200)
        }
        try {
            compose.waitForIdle()
            assertTrue("Non-reader rubber band must stay enabled", abs(offset) > 1f)
        } finally { root.performTouchInput { up() } }
    }

    private fun checkCoverage(long: Boolean, novel: Boolean, font: Int, background: Long,
                              nextBackground: Long? = null, topEdge: Boolean = false) {
        val text = if (long) (1..20).joinToString("\n") { "第${it}行 阅读背景测试正文。" } else "短正文。"
        val item = RssItem(9_000_006, 9_000_006, "背景测试", null, text, null,
            if (novel) "${ImportedContentIds.EPUB_SOURCE_ROOT_URL}/test/chapter" else "https://example.invalid/article",
            null, null, null, null, null, null, true, false, 0f, 0L)
        val preset = ReaderPreset(name = "测试主题", background = ReaderBackground(colorArgb = background))
            .let { it.copy(body = it.body.copy(fontSizeSp = font.toFloat(), colorArgb = if (background == 0xFFF0E7D8) 0xFF101010 else 0xFFFFFFFF)) }
        val selected = mutableStateOf(preset)
        compose.setContent {
            WatchRSSTheme {
                CompositionLocalProvider(LocalReaderPresetRuntime provides ReaderPresetRuntime(selected.value)) {
                    DetailContent(
                        item = item, showOriginalLoadingNotice = false,
                        contentBlocks = listOf(ContentBlock.Text(text, TextStyle.BODY)),
                        offlineMedia = emptyMap(), hasOfflineFailures = false, isRetryingOfflineMedia = false,
                        isFavorite = false, isWatchLater = false, originalContentEnabled = false,
                        readingFontSizeSp = font, shareUseSystem = false,
                        rssInlineImagePrefetchMode = RssInlineImagePrefetchMode.OFF,
                        llmEnabled = false, onToggleFavorite = {}, onToggleOriginalContent = {},
                        onRetryOfflineMedia = {}, onSaveReadingProgress = {}, onBack = { _, _, _ -> }
                    )
                }
            }
        }
        compose.waitForIdle()
        val root = compose.onRoot()
        if (!topEdge) repeat(if (long) 14 else 7) { root.performTouchInput { swipeUp(durationMillis = 120) } }
        if (nextBackground != null) {
            compose.runOnIdle { selected.value = preset.copy(background = ReaderBackground(colorArgb = nextBackground)) }
            compose.waitForIdle()
        }
        root.performTouchInput {
            down(Offset(width / 2f, height * if (topEdge) .15f else .85f))
            val direction = if (topEdge) 1f else -1f
            moveBy(Offset(0f, direction * height * .35f), 200)
            moveBy(Offset(0f, direction * height * .15f), 100)
        }
        try {
            val image = root.captureToImage()
            val pixels = image.toPixelMap()
            val actual = pixels[pixels.width / 2, if (topEdge) 6 else pixels.height - 6]
            val expected = Color(nextBackground ?: background)
            val directory = File(compose.activity.getExternalFilesDir(null), "bug006").apply { mkdirs() }
            File(directory, "${if (topEdge) "top" else if (nextBackground != null) "theme-change" else if (long) "long" else "short"}-${if (novel) "novel" else "rss"}-${background.toString(16)}.png")
                .outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("Bottom must retain paper background while overscrolling: actual=$actual expected=$expected",
                abs(actual.red - expected.red) < .04f && abs(actual.green - expected.green) < .04f && abs(actual.blue - expected.blue) < .04f)
        } finally {
            root.performTouchInput { up() }
        }
    }
}
