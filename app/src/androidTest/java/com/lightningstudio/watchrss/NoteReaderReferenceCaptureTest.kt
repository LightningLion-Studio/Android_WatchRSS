package com.lightningstudio.watchrss

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.data.reader.ReaderPreset
import com.lightningstudio.watchrss.ui.reader.LocalReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.ReaderBackgroundSurface
import com.lightningstudio.watchrss.ui.reader.ReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.ReaderTextRole
import com.lightningstudio.watchrss.ui.reader.readerTextStyle
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

/** Same static content as PodJS's isolated note reader fixture; writes only to the test APK. */
@RunWith(AndroidJUnit4::class)
class NoteReaderReferenceCaptureTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun captureMissingImageLink() {
        val markup = "![示例](assets/missing-image.png)"
        val preset = ReaderPreset.lightDefault(id = "note-image-failure", name = "Light", fontSizeSp = 20f)
        val listState = LazyListState()
        composeRule.setContent {
            WatchRSSTheme {
                CompositionLocalProvider(LocalReaderPresetRuntime provides ReaderPresetRuntime(preset)) {
                    ReaderBackgroundSurface(Modifier.fillMaxSize()) {
                        WatchNoteRichTextLazyColumn(
                            markup = markup,
                            blocks = prepareWatchNotePreviewBlocks(markup),
                            emptyText = "这条备忘录还没有正文。",
                            title = "缺失图片",
                            metadata = "0分钟前 · 7 字",
                            listState = listState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        Thread.sleep(1500)
        composeRule.waitForIdle()
        composeRule.runOnIdle { runBlocking { listState.scrollBy(180f) } }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.openFileOutput("native-note-image-failure-scrolled.png", Context.MODE_PRIVATE).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    @Test fun captureTableInlineCode() {
        val markup = "| 名称 | 示例 |\n| --- | --- |\n| **原文** | 前`码`后 |"
        val preset = ReaderPreset.lightDefault(id = "note-table-code", name = "Light", fontSizeSp = 20f)
        composeRule.setContent {
            WatchRSSTheme {
                CompositionLocalProvider(LocalReaderPresetRuntime provides ReaderPresetRuntime(preset)) {
                    ReaderBackgroundSurface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            WatchNoteRichTextLazyColumn(
                                markup = markup,
                                blocks = prepareWatchNotePreviewBlocks(markup),
                                emptyText = "这条备忘录还没有正文。",
                                title = "代码表格",
                                metadata = "0分钟前 · 24 字",
                                listState = LazyListState(),
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val bodyStyle = readerTextStyle(ReaderTextRole.BODY)
                                Box(
                                    Modifier.height(32.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(bodyStyle.color.copy(alpha = 0.14f))
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("编辑", color = bodyStyle.color, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.openFileOutput("native-note-table-code.png", Context.MODE_PRIVATE).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    @Test fun captureTable() {
        val markup = "| 甲 | 乙 |\n| --- | --- |\n| 丙 | 丁 |"
        val preset = ReaderPreset.lightDefault(id = "note-table", name = "Light", fontSizeSp = 20f)
        composeRule.setContent {
            WatchRSSTheme {
                CompositionLocalProvider(LocalReaderPresetRuntime provides ReaderPresetRuntime(preset)) {
                    ReaderBackgroundSurface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            WatchNoteRichTextLazyColumn(
                                markup = markup,
                                blocks = prepareWatchNotePreviewBlocks(markup),
                                emptyText = "这条备忘录还没有正文。",
                                title = "表格测试",
                                metadata = "0分钟前 · 19 字",
                                listState = LazyListState(),
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val bodyStyle = readerTextStyle(ReaderTextRole.BODY)
                                Box(
                                    Modifier.height(32.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(bodyStyle.color.copy(alpha = 0.14f))
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("编辑", color = bodyStyle.color, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.openFileOutput("native-note-table-light-20sp.png", Context.MODE_PRIVATE).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    @Test fun captureCodeOnly() {
        val markup = "前文\n```text\n代码\n```\n后文"
        val preset = ReaderPreset.lightDefault(id = "note-code", name = "Light", fontSizeSp = 20f)
        composeRule.setContent {
            WatchRSSTheme {
                CompositionLocalProvider(LocalReaderPresetRuntime provides ReaderPresetRuntime(preset)) {
                    ReaderBackgroundSurface(Modifier.fillMaxSize()) {
                        WatchNoteRichTextLazyColumn(
                            markup = markup,
                            blocks = prepareWatchNotePreviewBlocks(markup),
                            emptyText = "这条备忘录还没有正文。",
                            title = "代码测试",
                            metadata = null,
                            listState = LazyListState(),
                            onTextBlockLayout = { block ->
                                val lines = (0 until block.layout.lineCount).joinToString { index ->
                                    "${block.layout.getLineStart(index)}-${block.layout.getLineEnd(index)}" +
                                        "@${block.layout.getLineTop(index)}:${block.layout.getLineBottom(index)}"
                                }
                                Log.i("NoteCodeReference", "rendered=${block.renderedText} lines=$lines")
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.openFileOutput("native-note-code-only.png", Context.MODE_PRIVATE).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    @Test fun captureMultilineCode() {
        val markup = "前文\n```text\n代码甲\n代码乙\n```\n后文"
        val preset = ReaderPreset.lightDefault(id = "note-code-many", name = "Light", fontSizeSp = 20f)
        composeRule.setContent {
            WatchRSSTheme {
                CompositionLocalProvider(LocalReaderPresetRuntime provides ReaderPresetRuntime(preset)) {
                    ReaderBackgroundSurface(Modifier.fillMaxSize()) {
                        WatchNoteRichTextLazyColumn(
                            markup = markup,
                            blocks = prepareWatchNotePreviewBlocks(markup),
                            emptyText = "这条备忘录还没有正文。",
                            title = "代码测试",
                            metadata = null,
                            listState = LazyListState(),
                            onTextBlockLayout = { block ->
                                val lines = (0 until block.layout.lineCount).joinToString { index ->
                                    "${block.layout.getLineStart(index)}-${block.layout.getLineEnd(index)}" +
                                        "@${block.layout.getLineTop(index)}:${block.layout.getLineBottom(index)}"
                                }
                                Log.i("NoteCodeManyReference", "rendered=${block.renderedText} lines=$lines")
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.openFileOutput("native-note-code-many.png", Context.MODE_PRIVATE).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    @Test fun captureLight20Sp() {
        val markup = "正文应该使用深色文字。\n> 引用也需要可读。\n```\n代码\n```"
        val preset = ReaderPreset.lightDefault(id = "note-light", name = "Light", fontSizeSp = 20f)
        val blocks = prepareWatchNotePreviewBlocks(markup)
        val listState = LazyListState()
        val showEdit = mutableStateOf(true)
        composeRule.setContent {
            WatchRSSTheme {
                CompositionLocalProvider(LocalReaderPresetRuntime provides ReaderPresetRuntime(preset)) {
                    ReaderBackgroundSurface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            WatchNoteRichTextLazyColumn(
                                markup = markup,
                                blocks = blocks,
                                emptyText = "这条备忘录还没有正文。",
                                title = "浅色备忘录",
                                metadata = "0分钟前 · 21 字",
                                listState = listState,
                                onTextBlockLayout = { block ->
                                    val lines = (0 until block.layout.lineCount).joinToString { index ->
                                        "${block.layout.getLineStart(index)}-${block.layout.getLineEnd(index)}" +
                                            "@${block.layout.getLineTop(index)}:${block.layout.getLineBottom(index)}" +
                                            ":${block.layout.getLineBaseline(index)}"
                                    }
                                    Log.i("NoteReaderReference", "key=${block.key} top=${block.topInRoot} left=${block.leftInRoot} text=${block.renderedText} lines=$lines")
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (showEdit.value) Box(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val bodyStyle = readerTextStyle(ReaderTextRole.BODY)
                                Box(
                                    Modifier.height(32.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(bodyStyle.color.copy(alpha = 0.14f))
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("编辑", color = bodyStyle.color, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot(useUnmergedTree = true).printToLog("NoteReaderTree")
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.filesDir.isDirectory || context.filesDir.mkdirs())
        context.openFileOutput("native-note-light-20sp.png", Context.MODE_PRIVATE).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        val consumed = runBlocking { listState.scrollBy(180f) }
        composeRule.runOnIdle { showEdit.value = false }
        composeRule.waitForIdle()
        Log.i("NoteReaderReference", "scrolled=$consumed index=${listState.firstVisibleItemIndex} offset=${listState.firstVisibleItemScrollOffset}")
        val scrolled = composeRule.onRoot().captureToImage().asAndroidBitmap()
        context.openFileOutput("native-note-light-20sp-scrolled.png", Context.MODE_PRIVATE).use { output ->
            check(scrolled.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
