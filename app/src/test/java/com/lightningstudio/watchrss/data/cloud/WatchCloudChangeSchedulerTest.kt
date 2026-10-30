package com.lightningstudio.watchrss.data.cloud

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchCloudChangeSchedulerTest {
    @Test fun optOutNeverTransfersAndOptInChecksPendingRevisionImmediately() = runTest {
        val enabled = MutableStateFlow(false)
        val revisions = MutableStateFlow<Any>(40L)
        var calls = 0
        val scheduler = WatchCloudChangeScheduler(enabled, revisions, backgroundScope, { calls++ }, {}, 100)
        scheduler.start(); runCurrent(); assertEquals(0, calls)
        enabled.value = true; runCurrent(); assertEquals(1, calls)
        revisions.value = 41L; runCurrent(); advanceTimeBy(50); revisions.value = 42L; runCurrent()
        advanceTimeBy(100); runCurrent(); assertEquals(2, calls)
        scheduler.stop()
    }
    @Test fun disablingCancelsActualTransferAndReenableChecksAgain() = runTest {
        val enabled = MutableStateFlow(true)
        var cancelled = false
        var calls = 0
        val scheduler = WatchCloudChangeScheduler(enabled, MutableStateFlow<Any>(0), backgroundScope, {
            calls++; try { CompletableDeferred<Unit>().await() } finally { cancelled = true }
        }, {}, 100)
        scheduler.start(); runCurrent(); assertEquals(1, calls)
        enabled.value = false; runCurrent(); assertTrue(cancelled)
        enabled.value = true; runCurrent(); assertEquals(2, calls)
        scheduler.stop()
    }
    @Test fun retriesAreBoundedAndChangesDuringTransferQueueInsteadOfCancelling() = runTest {
        val changes = MutableStateFlow<Any>(0)
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val scheduler = WatchCloudChangeScheduler(MutableStateFlow(true), changes, backgroundScope, {
            calls++; if (calls == 1) release.await() else throw IOException("offline")
        }, {}, 100, listOf(10, 20))
        scheduler.start(); runCurrent(); changes.value = 1; runCurrent(); advanceTimeBy(100); runCurrent()
        assertEquals(1, calls)
        release.complete(Unit); runCurrent(); advanceTimeBy(30); runCurrent()
        assertEquals(4, calls)
        advanceTimeBy(1000); runCurrent(); assertEquals(4, calls)
        scheduler.stop()
    }
}
