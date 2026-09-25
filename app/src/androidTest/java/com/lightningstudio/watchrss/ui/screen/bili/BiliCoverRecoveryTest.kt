package com.lightningstudio.watchrss.ui.screen.bili

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Isolated HTTP server and injected connectivity; no platform account writes. */
class BiliCoverRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val network = MutableStateFlow(BiliCoverNetwork("wifi-A", true))
    private var server: CoverServer? = null
    private val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    @After fun stopServer() { server?.stop() }

    @Test fun realHttpFailure_manualRetryRecovers_sameUrl_withoutOpeningVideo() {
        val http = CoverServer(bitmap).also { it.start(); server = it }
        var videoOpens = 0
        compose.setContent {
            WatchRSSTheme {
                CompositionLocalProvider(LocalBiliCoverNetwork provides network) {
                    BiliFeedCard("测试视频", "", http.url, { videoOpens++ }, coverRecoveryEnabled = true)
                }
            }
        }
        waitForText("封面加载失败，点此重试")
        assertEquals(1, http.requests.get())
        http.succeed = true
        compose.onNodeWithText("封面加载失败，点此重试").performTouchInput { click() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("测试视频").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { assertEquals(0, videoOpens) }
        assertEquals(2, http.requests.get())
        network.value = BiliCoverNetwork(null, false)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("测试视频").assertIsDisplayed()
        compose.onNodeWithText("网络不可用，点此重试").assertDoesNotExist()
        assertEquals(2, http.requests.get())
    }

    @Test fun realHttpTimeout_exposesRetry_andThenRecovers() {
        val http = CoverServer(bitmap).also { it.responseDelayMillis = 11_000; it.start(); server = it }
        compose.setContent {
            CompositionLocalProvider(LocalBiliCoverNetwork provides network) {
                val state = rememberBiliCover(http.url, 100)
                BiliCoverFeedback(state)
                if (state.bitmap != null) Text("timeout-recovered")
            }
        }
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("封面加载失败，点此重试").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, http.requests.get())
        http.responseDelayMillis = 0
        http.succeed = true
        compose.onNodeWithText("封面加载失败，点此重试").performClick()
        waitForText("timeout-recovered")
        assertEquals(2, http.requests.get())
    }

    @Test fun offlineThenRecovery_reloadsFailedCoverAutomatically() {
        network.value = BiliCoverNetwork(null, false)
        val calls = AtomicInteger()
        val loader = BiliCoverLoader { _, _, _ ->
            calls.incrementAndGet()
            if (network.value.online == true) bitmap else null
        }
        setProbe(loader)
        waitForText("网络不可用，点此重试")
        assertEquals(1, calls.get())
        network.value = BiliCoverNetwork("wifi-A", true)
        waitForText("ready")
        assertEquals(2, calls.get())
    }

    @Test fun switchingBetweenOnlineNetworks_retriesOnceWithoutPolling() {
        val calls = AtomicInteger()
        setProbe(BiliCoverLoader { _, _, _ -> if (calls.incrementAndGet() == 1) null else bitmap })
        waitForText("封面加载失败，点此重试")
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals(1, calls.get())
        network.value = BiliCoverNetwork("wifi-B", true)
        waitForText("ready")
        assertEquals(2, calls.get())
        network.value = BiliCoverNetwork("cellular", true)
        compose.waitForIdle()
        assertEquals(2, calls.get()) // A successful cover survives network changes.
    }

    @Test fun scrollingDefersLoading_andEmptyUrlDoesNotRequest() {
        val enabled = mutableStateOf(false)
        val url = mutableStateOf<String?>(null)
        val calls = AtomicInteger()
        compose.setContent {
            CompositionLocalProvider(LocalBiliCoverNetwork provides network,
                LocalBiliCoverLoader provides BiliCoverLoader { _, _, _ -> calls.incrementAndGet(); bitmap }) {
                val state = rememberBiliCover(url.value, 100, enabled.value)
                BiliCoverFeedback(state)
                if (state.bitmap != null) Text("ready")
            }
        }
        compose.onNodeWithText("暂无封面").assertIsDisplayed()
        compose.runOnIdle { url.value = "deferred" }
        compose.waitForIdle()
        assertEquals(0, calls.get())
        compose.runOnIdle { enabled.value = true }
        waitForText("ready")
        assertEquals(1, calls.get())
    }

    @Test fun changingUrl_cancelsStaleResult_andShowsLoading() {
        val first = CompletableDeferred<Bitmap?>()
        val url = mutableStateOf("first")
        compose.setContent {
            CompositionLocalProvider(LocalBiliCoverNetwork provides network,
                LocalBiliCoverLoader provides BiliCoverLoader { _, request, _ ->
                    if (request == "first") first.await() else bitmap
                }) {
                val state = rememberBiliCover(url.value, 100)
                Box(Modifier.fillMaxSize()) {
                    BiliCoverFeedback(state)
                    if (state.bitmap === bitmap) Text("current-image")
                }
            }
        }
        waitForText("封面加载中")
        compose.runOnIdle { url.value = "second" }
        waitForText("current-image")
        first.complete(null)
        compose.waitForIdle()
        compose.onNodeWithText("current-image").assertIsDisplayed()
    }

    @Test fun sharedCardRecoveryIsOptIn_otherModulesKeepTheirLoader() {
        val calls = AtomicInteger()
        val http = CoverServer(bitmap).also { it.succeed = true; it.start(); server = it }
        compose.setContent {
            WatchRSSTheme {
                CompositionLocalProvider(LocalBiliCoverNetwork provides network,
                    LocalBiliCoverLoader provides BiliCoverLoader { _, _, _ -> calls.incrementAndGet(); null }) {
                    BiliFeedCard("旧模块", "", http.url, {}) // Default stays legacy.
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("旧模块").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, calls.get())
        assertEquals(1, http.requests.get())
        network.value = BiliCoverNetwork("wifi-B", true)
        compose.waitForIdle()
        assertEquals(1, http.requests.get())
    }

    @Test fun simultaneousSameUrlLoads_shareCacheWithoutRacingTemporaryFile() {
        val http = CoverServer(bitmap).also { it.succeed = true; it.start(); server = it }
        compose.setContent {
            CompositionLocalProvider(LocalBiliCoverNetwork provides network) {
                val first = rememberBiliCover(http.url, 100)
                val second = rememberBiliCover(http.url, 100)
                if (first.bitmap != null && second.bitmap != null) Text("both-ready")
            }
        }
        waitForText("both-ready")
        assertEquals(1, http.requests.get())
    }

    private fun setProbe(loader: BiliCoverLoader) {
        compose.setContent {
            CompositionLocalProvider(LocalBiliCoverNetwork provides network, LocalBiliCoverLoader provides loader) {
                val state = rememberBiliCover("test", 100)
                BiliCoverFeedback(state)
                if (state.bitmap != null) Text("ready")
            }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private class CoverServer(bitmap: Bitmap) : NanoHTTPD("127.0.0.1", 0) {
        @Volatile var succeed = false
        @Volatile var responseDelayMillis = 0L
        val requests = AtomicInteger()
        private val image = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        private val name = UUID.randomUUID().toString()
        val url get() = "http://127.0.0.1:$listeningPort/$name.png"
        override fun serve(session: IHTTPSession): Response {
            requests.incrementAndGet()
            Thread.sleep(responseDelayMillis)
            return if (succeed) newFixedLengthResponse(Response.Status.OK, "image/png", image.inputStream(), image.size.toLong())
            else newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "injected failure")
        }
    }
}
