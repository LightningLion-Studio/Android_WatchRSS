package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.testutil.TestDouyinPreloadManager
import com.lightningstudio.watchrss.testutil.sampleDouyinStreamItem
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DouyinRecentWindowCacheCoordinatorTest {
    @Test
    fun enqueueWindow_prioritizesWindowAroundAnchor_andCachesWholeRecentWindow() = runTest {
        val preloadManager = TestDouyinPreloadManager()
        val coordinatorScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val coordinator = DouyinRecentWindowCacheCoordinator(
            appScope = coordinatorScope,
            preloadManager = preloadManager
        )

        coordinator.enqueueWindow(
            items = listOf(
                sampleDouyinStreamItem(awemeId = "aweme-a"),
                sampleDouyinStreamItem(awemeId = "aweme-b"),
                sampleDouyinStreamItem(awemeId = "aweme-c"),
                sampleDouyinStreamItem(awemeId = "aweme-d")
            ),
            anchorAwemeId = "aweme-b",
            headers = mapOf("User-Agent" to "Test"),
            reason = "unit_test"
        )
        advanceUntilIdle()

        assertEquals(1, preloadManager.ensurePlaybackWindowCalls)
        assertEquals(4, preloadManager.playbackWindowPrefixCounts.single())
        assertEquals(
            listOf("aweme-b", "aweme-c", "aweme-d", "aweme-a"),
            preloadManager.playbackWindowSnapshots.single()
        )
        coordinatorScope.cancel()
    }
}
