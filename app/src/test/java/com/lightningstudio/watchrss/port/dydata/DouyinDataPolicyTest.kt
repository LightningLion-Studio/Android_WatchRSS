package com.lightningstudio.watchrss.port.dydata

import com.lightningstudio.watchrss.data.douyin.*
import com.lightningstudio.watchrss.sdk.douyin.*
import com.lightningstudio.watchrss.testutil.sampleDouyinStreamItem
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DouyinDataPolicyTest {
    @Test fun parserAndPersistenceRetainEveryRenditionCandidate() {
        val json = """{"aweme_list":[{"aweme_id":"x","video":{"duration":30000,"bit_rate":[{"bit_rate":500000,"gear_name":"h264_480","play_addr":{"width":480,"height":854,"url_list":["","https://a/video","https://b/video","https://a/video"]}}]}}]}"""
        val video = DouyinUnifiedParser().parseFeed(json).single()
        assertEquals(listOf("https://a/video", "https://b/video"), video.variants.single().playUrls)
        val variants = decodeDouyinVariants(encodeDouyinVariants(video.variants))
        assertEquals(video.variants, variants)
    }

    @Test fun selectorUsesSmallestSourceCovering466AndLowerBitrate() {
        fun variant(size: Int, rate: Long) = DouyinVideoVariant("https://cdn/$size/$rate", DouyinVideoCodec.H264, rate, size, size * 2)
        val small = variant(360, 100_000)
        val cover = variant(480, 200_000)
        assertEquals(cover, selectPreferredVariant(listOf(variant(720, 100_000), small, variant(480, 400_000), cover)))
        assertEquals(small, selectPreferredVariant(listOf(variant(240, 50_000), small)))
        assertNull(selectPreferredVariant(listOf(cover.copy(codec = DouyinVideoCodec.H265))))
        assertNotNull(selectPreferredVariant(listOf(cover.copy(codec = DouyinVideoCodec.H265)), hevcSupported = true))
    }

    @Test fun prefixUses1500MillisecondsPlusHeaderNotVideoDuration() {
        val expected = 96_000 + 128 * 1024
        assertEquals(expected, estimatePreviewBytes(6_400_000, 100_000))
        assertEquals(expected, estimatePrefetchBytes(6_400_000, 100_000))
        assertEquals(expected, estimatePreviewBytes(null, 100_000, 512_000))
        assertEquals(50, estimatePreviewBytes(50, 1))
        assertTrue(estimatePreviewBytes(null, 0) < 256 * 1024)
        assertEquals(4 * 1024 * 1024, estimatePreviewBytes(null, 100, Long.MAX_VALUE))
    }

    @Test fun candidateRetryIsDistinctBoundedAndRefillOncePerGeneration() {
        val urls = (1..6).map { "https://cdn$it/video" }
        val variant = DouyinVideoVariant(urls.first(), DouyinVideoCodec.H264, 300_000, 480, 854, playUrls = urls)
        val item = sampleDouyinStreamItem().copy(playUrl = urls.first(), variants = listOf(variant))
        val policy = DouyinCandidateRecovery()
        val second = policy.next(item)!!
        val third = policy.next(second)!!
        assertEquals(urls[1], second.playUrl)
        assertEquals(urls[2], third.playUrl)
        assertNull(policy.next(third))
        assertTrue(policy.allowFeedRefresh(third))
        assertFalse(policy.allowFeedRefresh(third))
        assertTrue(policy.allowFeedRefresh(third.copy(playUrlResolvedAtMs = third.playUrlResolvedAtMs + 1)))
    }

    @Test fun challengeProbeCoalescesAndCooldownIsEndpointScoped() = runTest {
        var now = 0L
        var calls = 0
        val policy = DouyinRequestPolicy(now = { now })
        val first = async { policy.challenged("feed") { calls++; DouyinLoginProbeResult.UNAUTHENTICATED } }
        val second = async { policy.challenged("feed") { calls++; DouyinLoginProbeResult.AUTHENTICATED } }
        assertEquals(DouyinLoginProbeResult.UNAUTHENTICATED, first.await())
        assertEquals(DouyinLoginProbeResult.UNKNOWN, second.await())
        assertEquals(1, calls)
        assertTrue(policy.coolingDown("feed"))
        assertFalse(policy.coolingDown("detail"))
        now = 30_001
        assertFalse(policy.coolingDown("feed"))
        policy.challenged("feed") { DouyinLoginProbeResult.UNKNOWN }
        now += 30_001
        assertTrue(policy.coolingDown("feed"))
        policy.successful("feed")
        assertFalse(policy.coolingDown("feed"))
    }

    @Test fun cooldownSurvivesRestartAndRejectsUnboundedPersistedExpiry() = runTest {
        var saved = emptyMap<String, DouyinCooldown>()
        val original = DouyinRequestPolicy(now = { 1_000 }, persist = { saved = it })
        original.challenged("feed") { DouyinLoginProbeResult.UNKNOWN }
        assertTrue(DouyinRequestPolicy(now = { 2_000 }, initial = saved).coolingDown("feed"))
        assertFalse(DouyinRequestPolicy(now = { 32_000 }, initial = saved).coolingDown("feed"))
        val clamped = DouyinRequestPolicy(now = { 0 }, initial = mapOf("feed" to DouyinCooldown(Long.MAX_VALUE, 1)))
        assertTrue(clamped.coolingDown("feed"))
    }

    @Test fun challengeBodiesNeverBecomeCookieInvalidErrors() {
        for (body in listOf("", "blocked", "<html>challenge</html>", "{\"verify_center\":{}}")) {
            try { validateDouyinResponse(body); fail("challenge accepted") }
            catch (expected: DouyinChallengeException) { assertFalse(expected.message.orEmpty().contains("Cookie无效")) }
        }
        assertEquals("{\"status_code\":0}", validateDouyinResponse("{\"status_code\":0}"))
    }

    @Test fun requestConfigRejectsHeaderInjectionAndKeepsSessionValuesOutOfToString() {
        try { DouyinRequestConfig(userAgent = "browser\r\nCookie:bad"); fail("invalid UA") }
        catch (_: IllegalArgumentException) { }
        try { DouyinRequestConfig(sessionParameters = mapOf("untrusted" to "x")); fail("invalid param") }
        catch (_: IllegalArgumentException) { }
        val config = DouyinRequestConfig(sessionParameters = mapOf("verifyFp" to "private-test-value"))
        val params = config.parameters(BaseRequestModel().toMap())
        assertEquals("155.0.0.0", params["browser_version"])
        assertEquals(params["verifyFp"], params["fp"])
        assertFalse(config.toString().contains("private-test-value"))
    }

    @Test fun previewCacheSeparatesRenditionsAtTheSameTimestamp() {
        DouyinPlaybackPreviewCache.resetForTests()
        try {
            val first = sampleDouyinStreamItem().copy(playUrl = "https://cdn/low.mp4")
            val second = first.copy(playUrl = "https://cdn/high.mp4")
            DouyinPlaybackPreviewCache.putPreviewBytesForTests(first, byteArrayOf(1, 2))
            DouyinPlaybackPreviewCache.putPreviewBytesForTests(second, byteArrayOf(3, 4))
            assertArrayEquals(byteArrayOf(1, 2), DouyinPlaybackPreviewCache.cachedPrefixBytes(first.playUrl))
            assertArrayEquals(byteArrayOf(3, 4), DouyinPlaybackPreviewCache.cachedPrefixBytes(second.playUrl))
        } finally { DouyinPlaybackPreviewCache.resetForTests() }
    }

    @Test fun existingDecodedPrefixIsPlayableWithoutEnteringUnwatchedInventory() {
        val dir = Files.createTempDirectory("dy-stock-playback").toFile()
        try {
            val now = 1_000_000L
            val item = sampleDouyinStreamItem().copy(playUrlResolvedAtMs = now)
            val stock = DouyinStockStore(dir, { true }, { now })
            assertTrue(stock.admitPlaybackPrefix(item, byteArrayOf(1, 2)))
            assertNotNull(stock.prefix(item))
            assertEquals(0, stock.count())
            assertTrue(DouyinStockStore(dir, { true }, { now }).restore(emptySet()).isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun stockRestartsWithThreeDecodedUnwatchedItemsAndConsumesDurably() {
        val dir = Files.createTempDirectory("dy-stock").toFile()
        try {
            val now = 1_000_000L
            fun item(id: String) = sampleDouyinStreamItem(awemeId = id).copy(playUrlResolvedAtMs = now)
            val stock = DouyinStockStore(dir, { it.readBytes().contentEquals(byteArrayOf(1, 2, 3)) }, { now })
            for (id in listOf("a", "b", "c")) assertTrue(stock.add(item(id), byteArrayOf(1, 2, 3), emptySet()))
            assertFalse(stock.add(item("d"), byteArrayOf(1, 2, 3), emptySet()))
            assertEquals(listOf("a", "b", "c"), DouyinStockStore(dir, { true }, { now }).restore(emptySet()).map { it.awemeId })
            stock.consume("a")
            assertNotNull(stock.prefix(item("a")))
            assertEquals(listOf("b"), DouyinStockStore(dir, { true }, { now }).restore(setOf("c")).map { it.awemeId })
        } finally { dir.deleteRecursively() }
    }

    @Test fun stockRejectsUndecodableExpiredWatchedAndMismatchedRendition() {
        val dir = Files.createTempDirectory("dy-stock-reject").toFile()
        try {
            val now = 1_000_000L
            val item = sampleDouyinStreamItem().copy(playUrlResolvedAtMs = now)
            assertFalse(DouyinStockStore(dir, { false }, { now }).add(item, byteArrayOf(1), emptySet()))
            val stock = DouyinStockStore(dir, { true }, { now })
            assertFalse(stock.add(item.copy(playUrlResolvedAtMs = 0), byteArrayOf(1), emptySet()))
            assertFalse(stock.add(item, byteArrayOf(1), setOf(item.awemeId)))
            assertTrue(stock.add(item, byteArrayOf(1), emptySet()))
            assertNull(stock.prefix(item.copy(durationMs = item.durationMs + 1)))
            assertNotEquals(douyinStockContentKey(item), douyinStockContentKey(item.copy(durationMs = 1)))
            // A new process must obtain fresh local decode proof before publishing readiness.
            assertTrue(DouyinStockStore(dir, { false }, { now }).restore(emptySet()).isEmpty())
        } finally { dir.deleteRecursively() }
    }
}
