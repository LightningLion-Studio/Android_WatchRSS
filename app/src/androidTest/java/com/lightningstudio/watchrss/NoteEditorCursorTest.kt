package com.lightningstudio.watchrss

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.lightningstudio.watchrss.data.db.WatchRssDatabase
import com.lightningstudio.watchrss.data.note.WatchNoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import java.util.UUID
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic notes in a dedicated disposable database; never modifies the user database. */
@OptIn(ExperimentalTestApi::class)
class NoteEditorCursorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var database: WatchRssDatabase? = null
    private val databaseName = "note-cursor-test-${UUID.randomUUID()}"

    @After fun removeOnlyTestDatabase() {
        database?.close()
        compose.activity.deleteDatabase(databaseName)
    }

    private fun openDatabase(): WatchRssDatabase = Room.databaseBuilder(
        compose.activity, WatchRssDatabase::class.java, databaseName
    ).build().also { database = it }

    @Test fun plainText_middleInsertionPersistsAcrossReopen() {
        checkMiddleInsertion("abcdef", "abcXdef")
    }

    @Test fun imageReference_middleInsertionKeepsRawMarkdownAcrossReopen() {
        checkMiddleInsertion("abcdef\n![image](https://example.invalid/image.png)",
            "abcXdef\n![image](https://example.invalid/image.png)")
    }

    private fun checkMiddleInsertion(original: String, expected: String) {
        var repository = WatchNoteRepository(openDatabase().watchNoteDao())
        var saved = runBlocking {
            repository.saveRawMarkdown(null, original, "instrumentation", "Cursor test")
        }
        val generation = mutableStateOf(0)
        var closed = false
        compose.setContent {
            WatchRSSTheme {
                key(generation.value) {
                    WatchNoteRawEditor(
                        note = saved,
                        safeInset = 8.dp,
                        scrollState = ScrollState(0),
                        initialAnchorOffset = 0,
                        saveDraft = { _, title, markdown ->
                            repository.saveRawMarkdown(saved.noteId, markdown, "instrumentation", title)
                        },
                        onClose = { _, _ -> closed = true }
                    )
                }
            }
        }
        val body = compose.onNodeWithTag("note-editor-body")
        // Tap the unfocused text overlay, not the semantics focus shortcut.
        body.performTouchInput { click(Offset(12f, 12f)) }
        compose.waitForIdle()
        // The activation callback may restore the initial anchor once. Subsequent IME
        // selection-only callbacks must not be pinned, even without a physical touch.
        body.performTextInputSelection(TextRange(1))
        compose.waitForIdle()
        body.performTextInputSelection(TextRange(3))
        compose.waitForIdle()
        assertEquals(TextRange(3), body.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        body.performTextInput("X")
        body.assertTextEquals(expected)
        compose.onNodeWithContentDescription("关闭编辑，", substring = true).performClick()
        compose.waitUntil(5_000) { closed }
        compose.waitForIdle()
        database!!.close()
        repository = WatchNoteRepository(openDatabase().watchNoteDao())
        saved = runBlocking { repository.all().single() }
        assertEquals(expected, saved.markdown)
        compose.runOnIdle { generation.value++ }
        body.assertTextEquals(expected)
    }
}
