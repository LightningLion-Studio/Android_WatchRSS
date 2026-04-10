package com.lightningstudio.watchrss.data.douyin

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito
import java.io.IOException
import java.io.InputStream

class DouyinPlaybackPreviewCacheTest {

    @After
    fun tearDown() {
        DouyinPlaybackPreviewCache.resetForTests()
    }

    @Test
    fun playbackDataSource_closesDirtyUpstreamBeforeReopen() {
        val upstream = RecordingDataSource()
        val factory = DouyinPlaybackPreviewCache.buildPlaybackDataSourceFactory(
            upstreamFactory = DataSource.Factory { upstream }
        )
        val dataSource = factory.createDataSource()
        val uri = Mockito.mock(Uri::class.java).also { mockUri ->
            Mockito.`when`(mockUri.toString()).thenReturn("https://example.com/video.mp4")
        }
        val dataSpec = DataSpec.Builder()
            .setUri(uri)
            .build()

        assertEquals(androidx.media3.common.C.LENGTH_UNSET.toLong(), dataSource.open(dataSpec))
        assertEquals(androidx.media3.common.C.LENGTH_UNSET.toLong(), dataSource.open(dataSpec))

        assertEquals(2, upstream.openCount)
        assertEquals(1, upstream.closeCount)

        dataSource.close()
        assertEquals(2, upstream.closeCount)
    }

    @Test
    fun playbackDataSource_closesPartiallyOpenedUpstreamAfterOpenFailure() {
        val upstream = FailingOpenThenReadableDataSource()
        val factory = DouyinPlaybackPreviewCache.buildPlaybackDataSourceFactory(
            upstreamFactory = DataSource.Factory { upstream }
        )
        val dataSource = factory.createDataSource()
        val uri = Mockito.mock(Uri::class.java).also { mockUri ->
            Mockito.`when`(mockUri.toString()).thenReturn("https://example.com/video.mp4")
        }
        val dataSpec = DataSpec.Builder()
            .setUri(uri)
            .build()

        try {
            dataSource.open(dataSpec)
        } catch (_: IOException) {
        }

        assertEquals(1, upstream.closeCount)
        assertEquals(androidx.media3.common.C.LENGTH_UNSET.toLong(), dataSource.open(dataSpec))

        dataSource.close()
        assertEquals(2, upstream.closeCount)
    }

    @Test
    fun estimatePrefetchBytes_capsWarmupBudgetBelowFullPreviewBudget() {
        val previewBudget = estimatePreviewBytes(contentLength = null, durationMs = 30_000L)
        val firstPrefetchBudget = estimatePrefetchBytes(
            contentLength = null,
            durationMs = 30_000L,
            prefetchOrder = 0
        )
        val secondPrefetchBudget = estimatePrefetchBytes(
            contentLength = null,
            durationMs = 30_000L,
            prefetchOrder = 1
        )
        val thirdPrefetchBudget = estimatePrefetchBytes(
            contentLength = null,
            durationMs = 30_000L,
            prefetchOrder = 2
        )
        val fifthPrefetchBudget = estimatePrefetchBytes(
            contentLength = null,
            durationMs = 30_000L,
            prefetchOrder = 4
        )

        assertTrue(firstPrefetchBudget < previewBudget)
        assertEquals(4 * 1024 * 1024, firstPrefetchBudget)
        assertEquals(3 * 1024 * 1024, secondPrefetchBudget)
        assertEquals(2 * 1024 * 1024, thirdPrefetchBudget)
        assertEquals(1024 * 1024, fifthPrefetchBudget)
    }

    @Test
    fun readPreviewBytes_keepsPartialPrefixOnReadFailure() {
        val prefix = byteArrayOf(1, 2, 3, 4, 5, 6)
        val input = FailingAfterPrefixInputStream(prefix = prefix, failAfterBytes = 4)

        val outcome = readDouyinPreviewBytes(input, budgetBytes = 16)

        assertArrayEquals(prefix.copyOf(4), outcome.bytes)
        assertTrue(outcome.error is IOException)
    }

    @Test
    fun playbackDataSource_defersUpstreamOpenUntilPreviewBytesAreConsumed() {
        val snapshotDir = createTempDir(prefix = "douyin-preview-cache-test")
        DouyinPlaybackPreviewCache.configureForTests(snapshotDir)
        val item = DouyinStreamItem(
            awemeId = "1001",
            playUrl = "https://example.com/video.mp4",
            coverUrl = null,
            title = "title",
            author = "author",
            likeCount = 0L,
            playUrlResolvedAtMs = 123L,
            sourceOrigin = DouyinSourceOrigin.NETWORK_FEED,
            durationMs = 30_000L
        )
        val previewBytes = byteArrayOf(9, 8, 7, 6)
        DouyinPlaybackPreviewCache.writeSnapshotForTests(0, item, previewBytes)
        DouyinPlaybackPreviewCache.restorePinnedItems()

        val upstream = RecordingDataSource()
        val factory = DouyinPlaybackPreviewCache.buildPlaybackDataSourceFactory(
            upstreamFactory = DataSource.Factory { upstream }
        )
        val dataSource = factory.createDataSource()
        val uri = Mockito.mock(Uri::class.java).also { mockUri ->
            Mockito.`when`(mockUri.toString()).thenReturn(item.playUrl)
        }
        val dataSpec = DataSpec.Builder()
            .setUri(uri)
            .build()

        assertEquals(androidx.media3.common.C.LENGTH_UNSET.toLong(), dataSource.open(dataSpec))
        assertEquals(0, upstream.openCount)

        val firstBuffer = ByteArray(previewBytes.size)
        assertEquals(previewBytes.size, dataSource.read(firstBuffer, 0, firstBuffer.size))
        assertArrayEquals(previewBytes, firstBuffer)
        assertEquals(0, upstream.openCount)

        val nextBuffer = ByteArray(1)
        assertEquals(1, dataSource.read(nextBuffer, 0, 1))
        assertEquals(1, upstream.openCount)

        dataSource.close()
        assertTrue(upstream.closeCount >= 1)
        snapshotDir.deleteRecursively()
    }

    @Test
    fun primeStartupWindow_restoresPinnedSnapshotsIntoRamBeforeWarmupTargets() {
        val snapshotDir = createTempDir(prefix = "douyin-preview-startup")
        DouyinPlaybackPreviewCache.configureForTests(snapshotDir)
        val pinnedItem = DouyinStreamItem(
            awemeId = "pinned-aweme",
            playUrl = "file:///tmp/pinned-aweme.mp4",
            coverUrl = null,
            title = "pinned",
            author = "author",
            likeCount = 0L,
            playUrlResolvedAtMs = 456L,
            sourceOrigin = DouyinSourceOrigin.NETWORK_FEED,
            durationMs = 30_000L
        )
        val startupItem = DouyinStreamItem(
            awemeId = "startup-aweme",
            playUrl = "file:///tmp/startup-aweme.mp4",
            coverUrl = null,
            title = "startup",
            author = "author",
            likeCount = 0L,
            playUrlResolvedAtMs = 789L,
            sourceOrigin = DouyinSourceOrigin.NETWORK_FEED,
            durationMs = 30_000L
        )
        val previewBytes = byteArrayOf(4, 3, 2, 1)
        DouyinPlaybackPreviewCache.writeSnapshotForTests(0, pinnedItem, previewBytes)

        DouyinPlaybackPreviewCache.primeStartupWindow(
            items = listOf(startupItem),
            headers = emptyMap(),
            reason = "unit_test"
        )

        val upstream = RecordingDataSource()
        val factory = DouyinPlaybackPreviewCache.buildPlaybackDataSourceFactory(
            upstreamFactory = DataSource.Factory { upstream }
        )
        val dataSource = factory.createDataSource()
        val uri = Mockito.mock(Uri::class.java).also { mockUri ->
            Mockito.`when`(mockUri.toString()).thenReturn(pinnedItem.playUrl)
        }
        val dataSpec = DataSpec.Builder()
            .setUri(uri)
            .build()

        assertEquals(androidx.media3.common.C.LENGTH_UNSET.toLong(), dataSource.open(dataSpec))
        assertEquals(0, upstream.openCount)

        val firstBuffer = ByteArray(previewBytes.size)
        assertEquals(previewBytes.size, dataSource.read(firstBuffer, 0, firstBuffer.size))
        assertArrayEquals(previewBytes, firstBuffer)
        assertEquals(0, upstream.openCount)

        dataSource.close()
        snapshotDir.deleteRecursively()
    }

    @Test
    fun restorePinnedItems_restoresPersistedPosterBytesIntoMemory() {
        val snapshotDir = createTempDir(prefix = "douyin-preview-poster")
        DouyinPlaybackPreviewCache.configureForTests(snapshotDir)
        val item = DouyinStreamItem(
            awemeId = "poster-aweme",
            playUrl = "file:///tmp/poster-aweme.mp4",
            coverUrl = "https://example.com/cover.jpg",
            title = "poster",
            author = "author",
            likeCount = 0L,
            playUrlResolvedAtMs = 987L,
            sourceOrigin = DouyinSourceOrigin.NETWORK_FEED,
            durationMs = 30_000L
        )
        val previewBytes = byteArrayOf(1, 2, 3, 4)
        val posterBytes = byteArrayOf(9, 8, 7, 6)

        DouyinPlaybackPreviewCache.writeSnapshotForTests(0, item, previewBytes)
        snapshotDir.resolve("snapshot_0.jpg").writeBytes(posterBytes)

        DouyinPlaybackPreviewCache.restorePinnedItems()

        assertArrayEquals(posterBytes, DouyinPlaybackPreviewCache.readPosterBytes(item))

        DouyinPlaybackPreviewCache.clearSession()
        assertNull(DouyinPlaybackPreviewCache.readPosterBytes(item))
        snapshotDir.deleteRecursively()
    }

    @Test
    fun updatePlaybackWindow_registersCurrentNextSevenAndPreviousOne() {
        val items = (0..9).map { index ->
            DouyinStreamItem(
                awemeId = "aweme-$index",
                playUrl = "file:///tmp/aweme-$index.mp4",
                coverUrl = null,
                title = "title-$index",
                author = "author",
                likeCount = 0L,
                playUrlResolvedAtMs = 100L + index,
                sourceOrigin = DouyinSourceOrigin.NETWORK_FEED,
                durationMs = 30_000L
            )
        }

        DouyinPlaybackPreviewCache.updatePlaybackWindow(
            items = items,
            anchorIndex = 2,
            headers = emptyMap(),
            reason = "unit_test"
        )

        assertTrue(DouyinPlaybackPreviewCache.hasRegistrationForTests(items[2].playUrl))
        assertTrue(DouyinPlaybackPreviewCache.hasRegistrationForTests(items[3].playUrl))
        assertTrue(DouyinPlaybackPreviewCache.hasRegistrationForTests(items[4].playUrl))
        assertTrue(DouyinPlaybackPreviewCache.hasRegistrationForTests(items[5].playUrl))
        assertTrue(DouyinPlaybackPreviewCache.hasRegistrationForTests(items[6].playUrl))
        assertTrue(DouyinPlaybackPreviewCache.hasRegistrationForTests(items[7].playUrl))
        assertTrue(DouyinPlaybackPreviewCache.hasRegistrationForTests(items[8].playUrl))
        assertTrue(DouyinPlaybackPreviewCache.hasRegistrationForTests(items[9].playUrl))
        assertTrue(DouyinPlaybackPreviewCache.hasRegistrationForTests(items[1].playUrl))
        assertTrue(!DouyinPlaybackPreviewCache.hasRegistrationForTests(items[0].playUrl))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun emitPrefetchHttpFailureForTests_publishesFailureEvent() = runTest {
        val awaited = async(start = CoroutineStart.UNDISPATCHED) {
            DouyinPlaybackPreviewCache.prefetchHttpFailures.first()
        }

        DouyinPlaybackPreviewCache.emitPrefetchHttpFailureForTests(
            DouyinPlaybackPrefetchHttpFailure(
                awemeId = "aweme-http-fail",
                mediaUri = "https://example.com/fail.mp4",
                httpStatusCode = 403,
                reason = "unit_test",
                occurredAtMs = 123L
            )
        )

        val failure = awaited.await()
        assertEquals("aweme-http-fail", failure.awemeId)
        assertEquals(403, failure.httpStatusCode)
        assertEquals("unit_test", failure.reason)
    }

    @Test
    fun debugSnapshot_exposesRegistrationWindowAndMemoryEntries() {
        val snapshotDir = createTempDir(prefix = "douyin-preview-debug")
        DouyinPlaybackPreviewCache.configureForTests(snapshotDir)
        val items = (0..9).map { index ->
            DouyinStreamItem(
                awemeId = "aweme-$index",
                playUrl = "file:///tmp/aweme-$index.mp4",
                coverUrl = null,
                title = "title-$index",
                author = "author",
                likeCount = 0L,
                playUrlResolvedAtMs = 200L + index,
                sourceOrigin = DouyinSourceOrigin.NETWORK_FEED,
                durationMs = 30_000L
            )
        }

        DouyinPlaybackPreviewCache.updatePlaybackWindow(
            items = items,
            anchorIndex = 2,
            headers = emptyMap(),
            reason = "unit_test"
        )

        var debugSnapshot = DouyinPlaybackPreviewCache.debugSnapshot()
        assertEquals(9, debugSnapshot.registrations.size)
        assertEquals("aweme-2", debugSnapshot.registrations.first().awemeId)
        assertEquals("aweme-9", debugSnapshot.registrations[7].awemeId)
        assertEquals("aweme-1", debugSnapshot.registrations.last().awemeId)
        assertEquals(0, debugSnapshot.activePrefetches.size)

        val previewBytes = byteArrayOf(7, 8, 9, 10)
        DouyinPlaybackPreviewCache.writeSnapshotForTests(0, items[3], previewBytes)
        DouyinPlaybackPreviewCache.restorePinnedItems()

        debugSnapshot = DouyinPlaybackPreviewCache.debugSnapshot()
        assertEquals(1, debugSnapshot.memoryEntries.size)
        assertEquals("aweme-3", debugSnapshot.memoryEntries.first().awemeId)
        assertEquals(previewBytes.size, debugSnapshot.memoryEntries.first().cachedBytes)

        snapshotDir.deleteRecursively()
    }
}

private class RecordingDataSource : DataSource {
    val payload = ByteArray(16) { index -> index.toByte() }
    var openCount = 0
    var closeCount = 0
    private var opened = false
    private var readPosition = 0
    private var currentUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        check(!opened) { "upstream opened twice without close" }
        opened = true
        readPosition = dataSpec.position.toInt().coerceAtMost(payload.size)
        currentUri = dataSpec.uri
        openCount += 1
        return (payload.size - readPosition).toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!opened) return 0
        if (readPosition >= payload.size) return androidx.media3.common.C.RESULT_END_OF_INPUT
        val bytesToCopy = minOf(length, payload.size - readPosition)
        System.arraycopy(payload, readPosition, buffer, offset, bytesToCopy)
        readPosition += bytesToCopy
        return bytesToCopy
    }

    override fun getUri(): Uri? = currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        if (opened) {
            closeCount += 1
        }
        opened = false
        readPosition = 0
        currentUri = null
    }
}

private class FailingOpenThenReadableDataSource : DataSource {
    val payload = ByteArray(16) { index -> index.toByte() }
    var openCount = 0
    var closeCount = 0
    private var opened = false
    private var readPosition = 0
    private var currentUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        check(!opened) { "already open" }
        opened = true
        currentUri = dataSpec.uri
        readPosition = dataSpec.position.toInt().coerceAtMost(payload.size)
        openCount += 1
        if (openCount == 1) {
            throw IOException("boom after open")
        }
        return (payload.size - readPosition).toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!opened) return 0
        if (readPosition >= payload.size) return androidx.media3.common.C.RESULT_END_OF_INPUT
        val bytesToCopy = minOf(length, payload.size - readPosition)
        System.arraycopy(payload, readPosition, buffer, offset, bytesToCopy)
        readPosition += bytesToCopy
        return bytesToCopy
    }

    override fun getUri(): Uri? = currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        if (opened) {
            closeCount += 1
        }
        opened = false
        readPosition = 0
        currentUri = null
    }
}

private class FailingAfterPrefixInputStream(
    private val prefix: ByteArray,
    private val failAfterBytes: Int
) : InputStream() {
    private var position = 0

    override fun read(): Int {
        val oneByte = ByteArray(1)
        return if (read(oneByte, 0, 1) == 1) {
            oneByte[0].toInt() and 0xFF
        } else {
            -1
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= failAfterBytes) {
            throw IOException("forced read failure after prefix")
        }
        if (position >= prefix.size) {
            return -1
        }
        val bytesToCopy = minOf(length, prefix.size - position, failAfterBytes - position)
        if (bytesToCopy <= 0) {
            throw IOException("forced read failure after prefix")
        }
        System.arraycopy(prefix, position, buffer, offset, bytesToCopy)
        position += bytesToCopy
        return bytesToCopy
    }
}
