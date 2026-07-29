package com.lightningstudio.watchrss.data.reader

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
