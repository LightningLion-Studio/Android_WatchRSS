package com.lightningstudio.watchrss.port.readerrelease

import com.lightningstudio.watchrss.ui.reader.awaitReaderAutoPage
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderAutoPageTest {
    @Test fun intervalExpiresOnlyAfterConfiguredPageSeconds() = runTest {
        var turns = 0
        val job = launch { awaitReaderAutoPage(15); turns++ }
        runCurrent()
        advanceTimeBy(14_999)
        assertEquals(0, turns)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, turns)
        job.join()
    }

    @Test fun cancellingForBackgroundChapterOrMenuDiscardsElapsedTime() = runTest {
        var turns = 0
        repeat(3) {
            val old = launch { awaitReaderAutoPage(10); turns++ }
            runCurrent()
            advanceTimeBy(9_000)
            old.cancelAndJoin()
        }
        assertEquals(0, turns)
        val resumed = launch { awaitReaderAutoPage(10); turns++ }
        runCurrent()
        advanceTimeBy(9_999)
        assertEquals(0, turns)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, turns)
        resumed.join()
    }
}
