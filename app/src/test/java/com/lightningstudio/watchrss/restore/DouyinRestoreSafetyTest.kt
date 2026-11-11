package com.lightningstudio.watchrss.restore

import com.lightningstudio.watchrss.data.douyin.*
import com.lightningstudio.watchrss.sdk.douyin.*
import com.lightningstudio.watchrss.testutil.*
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Request
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DouyinRestoreSafetyTest {
    @get:Rule val main = MainDispatcherRule()
    @After fun clearCache() = DouyinPlaybackPreviewCache.resetForTests()

    private class Transport : DouyinPlaybackTransportContract {
        val registered = mutableListOf<List<DouyinStreamItem>>()
        override fun proxyUrisFor(items: List<DouyinStreamItem>, headers: Map<String, String>, reason: String): Map<String, String> {
            registered += items
            return items.associate { it.awemeId to "watchrss-douyin-cache://play/${it.awemeId}" }
        }
        override fun primeStartupWindow(items: List<DouyinStreamItem>, headers: Map<String, String>, reason: String) = Unit
        override fun stop() = Unit
    }

    private fun model(items: List<DouyinStreamItem>, repository: DouyinRepositoryContract,
                      transport: Transport = Transport(), gate: DouyinTrafficGate = DouyinTrafficGate(),
                      anchor: String = items.first().awemeId): DouyinFeedViewModel = main.track(DouyinFeedViewModel(
        repository, TestDouyinPreloadManager(), playbackTransport = transport,
        watchHistoryStore = TestDouyinWatchHistoryStore(), feedCacheStore = TestDouyinFeedCacheStore(items),
        recentWindowStore = TestDouyinRecentWindowStore(DouyinRecentWindowSnapshot(items.takeLast(3), null, 0L)),
        storageDispatcher = main.dispatcher, resumeToVideoFlowOnEntry = true,
        resumeAwemeIdOnEntry = anchor, trafficGate = gate, stockReader = { emptyList() }
    ))

    @Test fun eighteenExpiredRestoredUrlsNeverReachPlaybackWhileReplacementFeedIsPending() = runTest {
        val items = (0..17).map { sampleDouyinStreamItem(awemeId = "restored-$it", playUrlResolvedAtMs = 0) }
        val release = CompletableDeferred<Unit>()
        var detailCalls = 0
        val backing = TestDouyinRepository(true)
        val repo = object : DouyinRepositoryContract by backing {
            override suspend fun fetchVideo(awemeId: String): DouyinResult<DouyinContent> {
                detailCalls++
                return DouyinResult(DouyinErrorCodes.REQUEST_FAILED, "detail must not be used")
            }
            override suspend fun fetchFeedPage(cursor: String?, count: Int): DouyinResult<DouyinFeedPage> {
                release.await()
                return backing.fetchFeedPage(cursor, count)
            }
        }
        val transport = Transport()
        val vm = model(items, repo, transport)
        runCurrent()
        assertEquals(items.map { it.awemeId }, vm.uiState.value.items.map { it.awemeId })
        assertEquals(1, vm.uiState.value.currentPage)
        assertTrue(vm.uiState.value.proxyPlayUris.isEmpty())
        assertTrue(transport.registered.flatten().isEmpty())
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, detailCalls)
        assertTrue(vm.uiState.value.items.none { it.awemeId.startsWith("restored-") })
        assertTrue(transport.registered.flatten().all { !isDouyinPlayUrlExpired(it) })
    }

    @Test fun eighteenExpiredItemsAndForbiddenNeighboursStopAtThreeWithoutAdvancingOrLoggingOut() = runTest {
        val gate = DouyinTrafficGate()
        var calls = 0
        val backing = TestDouyinRepository(true)
        val repo = object : DouyinRepositoryContract by backing {
            override suspend fun fetchVideo(awemeId: String): DouyinResult<DouyinContent> {
                gate.checkAllowed()
                calls++
                gate.recordStatus(403)
                return DouyinResult(403, "fixture")
            }
            override suspend fun fetchFeedPage(cursor: String?, count: Int): DouyinResult<DouyinFeedPage> {
                gate.checkAllowed()
                calls++
                gate.recordStatus(403)
                return DouyinResult(403, "fixture")
            }
        }
        val items = (0..17).map { sampleDouyinStreamItem(awemeId = "restored-$it", playUrlResolvedAtMs = 0) }
        val vm = model(items, repo, gate = gate)
        advanceUntilIdle()
        vm.enterVideoFlow(items.first().awemeId)
        items.drop(1).forEach { item ->
            repeat(2) {
                vm.refreshPlaybackSource(item.awemeId, DouyinPlaybackSourceKind.REMOTE, DouyinPlaybackRefreshTrigger.PREFETCH_HTTP_403)
                advanceUntilIdle()
            }
        }
        vm.refreshPlaybackSource(items.first().awemeId, DouyinPlaybackSourceKind.REMOTE, DouyinPlaybackRefreshTrigger.FOREGROUND_HTTP_403)
        vm.loadMoreForList()
        vm.loadInitial()
        advanceUntilIdle()
        assertEquals(3, calls)
        assertTrue(gate.stopped.value)
        assertTrue(vm.uiState.value.trafficStopped)
        assertEquals(items.map { it.awemeId }, vm.uiState.value.items.map { it.awemeId })
        assertEquals(1, vm.uiState.value.currentPage)
        assertFalse(vm.uiState.value.showTitlePage)
        assertTrue(vm.uiState.value.proxyPlayUris.isEmpty())
        // The stale-queue replacement feed load may report its own failure; it never logs the user out.
        assertTrue(vm.uiState.value.isLoggedIn)
        assertEquals(0, backing.logoutCalls)
        assertEquals(0, backing.clearCookieCalls)
        assertTrue(backing.fetchVideoCalls.isEmpty())
    }

    @Test fun freshRestorePublishesSameItemImmediatelyWithoutARefresh() = runTest {
        val items = (0..17).map { sampleDouyinStreamItem(awemeId = "fresh-$it") }
        val repo = TestDouyinRepository(true)
        val vm = model(items, repo, anchor = "fresh-7")
        advanceUntilIdle()
        vm.enterVideoFlow()
        assertEquals("fresh-7", vm.uiState.value.items[vm.uiState.value.currentPage - 1].awemeId)
        assertTrue(vm.uiState.value.proxyPlayUris.containsKey("fresh-7"))
        assertTrue(repo.fetchFeedPageCursors.isEmpty())
        assertTrue(repo.fetchVideoCalls.isEmpty())
    }

    @Test fun discardingNeighboursOnEitherSideKeepsVisibleIdentityAndCurrentFailureStaysInline() = runTest {
        val items = (0..17).map { sampleDouyinStreamItem(awemeId = "item-$it") }
        val vm = model(items, TestDouyinRepository(true))
        advanceUntilIdle()
        vm.enterVideoFlow()
        vm.onPageSettled(8)
        fun visibleId() = vm.uiState.value.items[vm.uiState.value.currentPage - 1].awemeId
        assertEquals("item-7", visibleId())
        vm.discardPlaybackItem("item-9")
        assertEquals(8, vm.uiState.value.currentPage)
        assertEquals("item-7", visibleId())
        vm.discardPlaybackItem("item-2")
        assertEquals(7, vm.uiState.value.currentPage)
        assertEquals("item-7", visibleId())
        repeat(18) { vm.discardPlaybackItem("item-7") }
        assertEquals("item-7", visibleId())
        assertEquals(setOf("item-7"), vm.uiState.value.unavailablePlaybackIds)
        assertNull(vm.uiState.value.message)
    }

    @Test fun foreground403RefreshIsOncePerSessionItemEvenAfterTheUrlChanges() = runTest {
        val items = (0..17).map { sampleDouyinStreamItem(awemeId = "item-$it") }
        val repo = TestDouyinRepository(true).apply {
            feedPageResults = ArrayDeque(listOf(DouyinResult(DouyinErrorCodes.OK,
                data = DouyinFeedPage(listOf(sampleDouyinVideo(awemeId = "item-0")), null, false))))
        }
        val vm = model(items, repo)
        advanceUntilIdle()
        repeat(18) {
            vm.refreshPlaybackSource("item-0", DouyinPlaybackSourceKind.REMOTE, DouyinPlaybackRefreshTrigger.FOREGROUND_HTTP_403)
            advanceUntilIdle()
        }
        assertEquals(1, repo.fetchFeedPageCursors.size)
        assertEquals(1, vm.uiState.value.currentPage)
        assertEquals("item-0", vm.uiState.value.items.first().awemeId)
        assertNull(vm.uiState.value.message)
    }

    @Test fun stoppedSessionReentryRestoresCachedPageWithoutRequestsOrLoginChange() = runTest {
        val gate = DouyinTrafficGate()
        repeat(3) { gate.recordStatus(403) }
        val repo = TestDouyinRepository(true)
        val items = (0..17).map { sampleDouyinStreamItem(awemeId = "item-$it") }
        val vm = model(items, repo, gate = gate)
        advanceUntilIdle()
        vm.enterVideoFlow()
        assertEquals(items.map { it.awemeId }, vm.uiState.value.items.map { it.awemeId })
        assertTrue(vm.uiState.value.isLoggedIn)
        assertTrue(vm.uiState.value.trafficStopped)
        assertEquals(1, vm.uiState.value.currentPage)
        assertTrue(repo.fetchFeedPageCursors.isEmpty())
        assertTrue(repo.fetchVideoCalls.isEmpty())
        assertTrue(vm.uiState.value.proxyPlayUris.isEmpty())
    }

    @Test fun legacySnapshotsWithUnknownUrlAgeDoNotBorrowTheSaveTimestamp() {
        val now = System.currentTimeMillis()
        val raw = """{"savedAtMs":$now,"items":[{"awemeId":"legacy","playUrl":"https://fixture.invalid/old"}]}"""
        val feed = parseDouyinFeedCache(raw, 18).items.single()
        val recent = parseDouyinRecentWindow(raw, 18).items.single()
        assertEquals(0L, feed.playUrlResolvedAtMs)
        assertEquals(0L, recent.playUrlResolvedAtMs)
        assertTrue(isDouyinPlayUrlExpired(feed, now))
        assertTrue(isDouyinPlayUrlExpired(recent, now))
    }

    @Test fun explicitUrlExpiryUnknownAgeAndClockRollbackAreStale() {
        val now = 1_800_000_000_000L
        val fresh = sampleDouyinStreamItem(playUrlResolvedAtMs = now)
        assertFalse(isDouyinPlayUrlExpired(fresh, now))
        assertTrue(isDouyinPlayUrlExpired(fresh.copy(playUrlResolvedAtMs = 0), now))
        assertTrue(isDouyinPlayUrlExpired(fresh.copy(playUrlResolvedAtMs = now + 1), now))
        assertTrue(isDouyinPlayUrlExpired(fresh.copy(playUrlResolvedAtMs = now - DOUYIN_PLAY_URL_TTL_MS), now))
        assertTrue(isDouyinPlayUrlExpired(fresh.copy(playUrl = "https://cdn.invalid/v?x-expires=1799999999"), now))
        assertFalse(isDouyinPlayUrlExpired(fresh.copy(playUrl = "https://cdn.invalid/v?x-expires=1800000001"), now))
    }

    @Test fun apiAndMediaClientsShareFuseCancelActiveBodiesAndNeverSendAfterTrip() {
        val gate = DouyinTrafficGate()
        var sent = 0
        fun client(status: Int) = OkHttpClient.Builder().addInterceptor(DouyinTrafficInterceptor(gate))
            .addInterceptor { chain ->
                sent++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status)
                    .message("offline fixture").body("fixture".toResponseBody()).build()
            }.build()
        val request = Request.Builder().url("https://fixture.invalid/video").build()
        val streamingCall = client(200).newCall(request)
        val openBody = streamingCall.execute()
        val forbidden = client(403)
        repeat(3) {
            try { forbidden.newCall(request).execute().close() }
            catch (_: IOException) { assertTrue(gate.stopped.value) }
        }
        assertTrue(gate.stopped.value)
        assertTrue(streamingCall.isCanceled())
        assertEquals(4, sent)
        for (channel in listOf(client(200), forbidden)) {
            try { channel.newCall(request).execute().close(); fail("traffic after fuse trip") }
            catch (_: IOException) { }
        }
        assertEquals(4, sent)
        openBody.close()
    }

    @Test fun failuresOutsideThirtySecondWindowDoNotAccumulateButTripNeverResets() {
        var now = 0L
        val gate = DouyinTrafficGate { now }
        repeat(2) { gate.recordStatus(403) }
        now = 30_001
        gate.recordStatus(403)
        assertFalse(gate.stopped.value)
        gate.recordStatus(200) // An unrelated feed response must not hide CDN failures.
        repeat(2) { gate.recordStatus(403) }
        assertTrue(gate.stopped.value)
        now += 1_000_000
        gate.recordStatus(200)
        assertTrue(gate.stopped.value)
    }
}
