package com.lightningstudio.watchrss.data.reader

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchReaderPresetPreviewSessionTest {
    @Test
    fun previewIsMemoryOnlyAndRejectsOutOfOrderUpdates() = runTest {
        val session = WatchReaderPresetPreviewSession(this, timeoutMs = 5_000L)

        assertTrue(session.update("phone", 2L, ReaderPreset.darkDefault(name = "第二版")))
        assertFalse(session.update("phone", 1L, ReaderPreset.lightDefault(name = "旧版")))

        assertEquals("第二版", session.state.value?.preset?.name)
        assertTrue(session.stop("phone"))
        assertNull(session.state.value)
    }

    @Test
    fun newerSessionReplacesOldAndExpiresWithoutHeartbeat() = runTest {
        val session = WatchReaderPresetPreviewSession(this, timeoutMs = 1_000L)
        session.update("old", 9L, ReaderPreset.darkDefault(name = "旧会话"))
        session.update("new", 0L, ReaderPreset.lightDefault(name = "新会话"))

        assertFalse(session.stop("old"))
        assertEquals("新会话", session.state.value?.preset?.name)

        advanceTimeBy(1_000L)
        runCurrent()
        assertNull(session.state.value)
    }

    @Test
    fun resourceHandoffKeepsPreviewAliveAcrossSessionReplacement() = runTest {
        val session = WatchReaderPresetPreviewSession(this, timeoutMs = 1_000L)
        session.update("old", 9L, ReaderPreset.darkDefault(name = "旧字体"))

        assertTrue(
            session.update(
                sessionId = "new",
                sequence = 0L,
                preset = ReaderPreset.lightDefault(name = "新字体"),
                resourceTransferInProgress = true
            )
        )
        assertFalse(session.stop("old"))
        assertEquals("新字体", session.state.value?.preset?.name)
        assertTrue(session.state.value?.resourceTransferInProgress == true)

        assertTrue(
            session.update(
                sessionId = "new",
                sequence = 1L,
                preset = ReaderPreset.lightDefault(name = "新字体"),
                resourceTransferInProgress = false
            )
        )
        assertFalse(session.state.value?.resourceTransferInProgress == true)
        assertEquals("新字体", session.state.value?.preset?.name)
    }

    @Test
    fun rapidDeltasPublishOnlyTheLatestPendingState() = runTest {
        val session = WatchReaderPresetPreviewSession(
            scope = this,
            timeoutMs = 5_000L,
            publishIntervalMs = 100L
        )
        val initial = ReaderPreset.darkDefault(name = "预览")
        session.update("phone", 0L, initial)

        val first = initial.copy(body = initial.body.copy(fontSizeSp = 20f))
        val second = initial.copy(body = initial.body.copy(fontSizeSp = 24f))
        assertTrue(
            session.updateDelta(
                sessionId = "phone",
                sequence = 1L,
                changes = JSONObject().put(
                    "body",
                    JSONObject(ReaderPresetCodec.encode(first)).getJSONObject("body")
                )
            )
        )
        assertTrue(
            session.updateDelta(
                sessionId = "phone",
                sequence = 2L,
                changes = JSONObject().put(
                    "body",
                    JSONObject(ReaderPresetCodec.encode(second)).getJSONObject("body")
                )
            )
        )

        assertEquals(0L, session.state.value?.sequence)
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(2L, session.state.value?.sequence)
        assertEquals(24f, session.state.value?.preset?.body?.fontSizeSp)
    }
}
