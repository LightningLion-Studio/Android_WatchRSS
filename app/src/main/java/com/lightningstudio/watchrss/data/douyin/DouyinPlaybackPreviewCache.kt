package com.lightningstudio.watchrss.data.douyin

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoCodec
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoVariant
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.io.StringWriter
import java.io.ByteArrayOutputStream
import java.util.LinkedHashMap
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher as OkHttpDispatcher
import okhttp3.OkHttpClient
import okhttp3.Request

internal const val DOUYIN_PLAYBACK_PREVIEW_ENTRY_LIMIT = 16

private const val DOUYIN_PLAYBACK_SNAPSHOT_COUNT = 6
internal const val DOUYIN_PLAYBACK_PREFETCH_COUNT = 2
private const val DOUYIN_PLAYBACK_PREFETCH_CONCURRENCY = DOUYIN_PLAYBACK_PREVIEW_ENTRY_LIMIT
private const val DOUYIN_PLAYBACK_BACKWARD_REGISTRATION_COUNT = 5
private const val PREVIEW_DURATION_MS = 30_000L
private const val PREFETCH_DURATION_MS = 16_000L
private const val MIN_ENTRY_BYTES = 512 * 1024
private const val PREFETCH_PROGRESS_COMMIT_BYTES = 512 * 1024
private const val PREFETCH_READ_CHUNK_BYTES = 64 * 1024
private const val DEFAULT_UNKNOWN_DURATION_BYTES = 8 * 1024 * 1024
private const val MAX_ENTRY_BYTES = 12 * 1024 * 1024
private const val MAX_PREFETCH_ENTRY_BYTES = 4 * 1024 * 1024
private const val MAX_TOTAL_PREVIEW_BYTES = 128L * 1024L * 1024L
private const val MAX_TOTAL_POSTER_BYTES = 4L * 1024L * 1024L
private const val DEFAULT_ESTIMATED_BYTES_PER_SECOND = 256 * 1024L
private const val FOREGROUND_BANDWIDTH_RESERVE_NUMERATOR = 13L
private const val FOREGROUND_BANDWIDTH_RESERVE_DENOMINATOR = 80L
private const val PREFETCH_BANDWIDTH_WINDOW_MS = 1_000L
private const val PREFETCH_BANDWIDTH_WAIT_MS = 250L
private const val BANDWIDTH_ESTIMATE_MIN_SAMPLE_MS = 500L
private const val BANDWIDTH_ESTIMATE_MIN_SAMPLE_BYTES = 32 * 1024L
private const val CACHE_READ_WAIT_MS = 50L

data class DouyinPlaybackPreviewWarmTarget(
    val awemeId: String,
    val mediaUri: String,
    val playUrlResolvedAtMs: Long,
    val durationMs: Long
)

internal data class DouyinPlaybackPreviewKey(
    val awemeId: String,
    val playUrlResolvedAtMs: Long
)

private data class DouyinPlaybackPreviewRegistration(
    val key: DouyinPlaybackPreviewKey,
    val mediaUri: String,
    val durationMs: Long,
    val budgetBytes: Int,
    val captureEnabled: Boolean,
    val remoteUri: String?,
    val headers: Map<String, String>
)

private data class DouyinPlaybackPreviewEntry(
    val key: DouyinPlaybackPreviewKey,
    val mediaUri: String,
    val bytes: ByteArray,
    val budgetBytes: Int,
    val isComplete: Boolean = false,
    val failureMessage: String? = null
)

private data class DouyinPlaybackRemoteTarget(
    val remoteUri: String,
    val headers: Map<String, String>
)

private data class DouyinPlaybackDownloadPlan(
    val target: DouyinPlaybackPreviewWarmTarget,
    val remoteUri: String,
    val headers: Map<String, String>,
    val requiredBytes: Int,
    val initialBytes: Int,
    val prefetchOrder: Int,
    val completeOnEnd: Boolean
)

private data class DouyinPlaybackDownloadControl(
    val requiredBytes: Int,
    val prefetchOrder: Int,
    val completeOnEnd: Boolean,
    val reason: String
)

private data class DouyinPlaybackExitSnapshotRecord(
    val item: DouyinStreamItem,
    val bytes: ByteArray,
    val posterBytes: ByteArray?
)

internal data class DouyinPlaybackPrefetchDebugEntry(
    val awemeId: String,
    val mediaUri: String,
    val downloadedBytes: Int,
    val budgetBytes: Int,
    val prefetchOrder: Int,
    val reason: String,
    val startedAtMs: Long
)

internal data class DouyinPlaybackPreviewRegistrationDebugEntry(
    val awemeId: String,
    val mediaUri: String,
    val cachedBytes: Int,
    val budgetBytes: Int,
    val captureEnabled: Boolean,
    val isPrefetching: Boolean
)

internal data class DouyinPlaybackPreviewMemoryDebugEntry(
    val awemeId: String,
    val mediaUri: String,
    val cachedBytes: Int,
    val budgetBytes: Int
)

internal data class DouyinPlaybackPreviewDebugSnapshot(
    val sessionGeneration: Long,
    val totalPreviewBytes: Long,
    val totalPosterBytes: Long,
    val activePrefetches: List<DouyinPlaybackPrefetchDebugEntry>,
    val registrations: List<DouyinPlaybackPreviewRegistrationDebugEntry>,
    val memoryEntries: List<DouyinPlaybackPreviewMemoryDebugEntry>
)

internal data class DouyinPlaybackPrefetchHttpFailure(
    val awemeId: String,
    val mediaUri: String,
    val httpStatusCode: Int,
    val reason: String,
    val occurredAtMs: Long
)

private data class DouyinPlaybackPrefetchProgress(
    val key: DouyinPlaybackPreviewKey,
    val awemeId: String,
    val mediaUri: String,
    val downloadedBytes: Int,
    val budgetBytes: Int,
    val prefetchOrder: Int,
    val reason: String,
    val startedAtMs: Long
)

internal data class DouyinPlaybackPreviewReadOutcome(
    val bytes: ByteArray,
    val error: Throwable?
)

internal fun readDouyinPreviewBytes(
    input: InputStream,
    budgetBytes: Int,
    onBytesRead: (ByteArray, Int) -> Unit = { _, _ -> }
): DouyinPlaybackPreviewReadOutcome {
    if (budgetBytes <= 0) {
        return DouyinPlaybackPreviewReadOutcome(bytes = ByteArray(0), error = null)
    }
    val buffer = ByteArray(budgetBytes)
    var offset = 0
    try {
        while (offset < budgetBytes) {
            val read = input.read(buffer, offset, min(PREFETCH_READ_CHUNK_BYTES, budgetBytes - offset))
            if (read <= 0) break
            offset += read
            onBytesRead(buffer, offset)
        }
        return DouyinPlaybackPreviewReadOutcome(
            bytes = buffer.copyOf(offset),
            error = null
        )
    } catch (error: Throwable) {
        return DouyinPlaybackPreviewReadOutcome(
            bytes = buffer.copyOf(offset),
            error = error
        )
    }
}

private suspend fun readDouyinPreviewBytesThrottled(
    input: InputStream,
    budgetBytes: Int,
    beforeRead: suspend (Int) -> Unit,
    onBytesRead: (ByteArray, Int) -> Unit = { _, _ -> }
): DouyinPlaybackPreviewReadOutcome {
    if (budgetBytes <= 0) {
        return DouyinPlaybackPreviewReadOutcome(bytes = ByteArray(0), error = null)
    }
    val buffer = ByteArray(budgetBytes)
    var offset = 0
    try {
        while (offset < budgetBytes) {
            val bytesToRead = min(PREFETCH_READ_CHUNK_BYTES, budgetBytes - offset)
            beforeRead(bytesToRead)
            val read = input.read(buffer, offset, bytesToRead)
            if (read <= 0) break
            offset += read
            onBytesRead(buffer, offset)
        }
        return DouyinPlaybackPreviewReadOutcome(
            bytes = buffer.copyOf(offset),
            error = null
        )
    } catch (error: Throwable) {
        return DouyinPlaybackPreviewReadOutcome(
            bytes = buffer.copyOf(offset),
            error = error
        )
    }
}

object DouyinPlaybackPreviewCache {
    private val manager = DouyinPlaybackPreviewManager()
    private val _prefetchHttpFailures =
        MutableSharedFlow<DouyinPlaybackPrefetchHttpFailure>(extraBufferCapacity = 32)

    internal val prefetchHttpFailures: SharedFlow<DouyinPlaybackPrefetchHttpFailure> =
        _prefetchHttpFailures.asSharedFlow()

    fun configure(context: Context) {
        manager.configure(File(context.applicationContext.cacheDir, SNAPSHOT_DIR_NAME))
    }

    fun buildPlaybackDataSourceFactory(
        upstreamFactory: DataSource.Factory
    ): DataSource.Factory {
        return DataSource.Factory {
            DouyinPlaybackPreviewDataSource(
                manager = manager
            )
        }
    }

    fun updatePlaybackWindow(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?,
        headers: Map<String, String>,
        reason: String,
        foregroundBitrateBitsPerSecond: Long? = null,
        totalBandwidthBytesPerSecond: Long? = null
    ) {
        manager.updatePlaybackWindow(
            items = items,
            anchorIndex = anchorIndex,
            headers = headers,
            reason = reason,
            foregroundBitrateBitsPerSecond = foregroundBitrateBitsPerSecond,
            totalBandwidthBytesPerSecond = totalBandwidthBytesPerSecond
        )
    }

    fun primeStartupWindow(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        reason: String
    ) {
        manager.primeStartupWindow(items, headers, reason)
    }

    fun restorePinnedItems(
        limit: Int = DOUYIN_PLAYBACK_SNAPSHOT_COUNT
    ): List<DouyinStreamItem> {
        return manager.restorePinnedItems(limit)
    }

    fun persistExitSnapshots(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?
    ) {
        manager.persistExitSnapshots(items, anchorIndex)
    }

    fun persistExitSnapshotsIfCurrent(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?,
        expectedGeneration: Long,
        clearAfterPersist: Boolean = true
    ) {
        manager.persistExitSnapshotsIfCurrent(
            items = items,
            anchorIndex = anchorIndex,
            expectedGeneration = expectedGeneration,
            clearAfterPersist = clearAfterPersist
        )
    }

    fun persistExitSnapshotsIfCurrent(
        items: List<DouyinStreamItem>,
        playbackUrisByAwemeId: Map<String, String>,
        anchorIndex: Int?,
        expectedGeneration: Long,
        clearAfterPersist: Boolean = true
    ) {
        manager.persistExitSnapshotsIfCurrent(
            items = items,
            playbackUrisByAwemeId = playbackUrisByAwemeId,
            anchorIndex = anchorIndex,
            expectedGeneration = expectedGeneration,
            clearAfterPersist = clearAfterPersist
        )
    }

    fun storePoster(
        item: DouyinStreamItem,
        bitmap: Bitmap
    ): Boolean {
        return manager.storePoster(item, bitmap)
    }

    fun readPosterBytes(item: DouyinStreamItem?): ByteArray? {
        return manager.readPosterBytes(item)
    }

    internal fun aliasPreviewBytes(
        sourceUri: String?,
        targetItem: DouyinStreamItem
    ): Boolean {
        return manager.aliasPreviewBytes(sourceUri, targetItem)
    }

    internal fun registerRemotePlaybackTarget(
        targetItem: DouyinStreamItem,
        remoteUri: String,
        headers: Map<String, String>
    ): Boolean {
        return manager.registerRemotePlaybackTarget(
            targetItem = targetItem,
            remoteUri = remoteUri,
            headers = headers
        )
    }

    internal fun prefetchTargetsForTests(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?
    ): List<DouyinPlaybackPreviewWarmTarget> {
        return manager.buildPlaybackPrefetchTargetsForTests(items, anchorIndex)
    }

    fun clearSession() {
        manager.clearSession()
    }

    fun captureSessionGeneration(): Long {
        return manager.captureSessionGeneration()
    }

    internal fun debugSnapshot(): DouyinPlaybackPreviewDebugSnapshot {
        return manager.debugSnapshot()
    }

    fun clearAll() {
        manager.clearAll()
    }

    internal fun configureForTests(snapshotDir: File?) {
        manager.configure(snapshotDir)
    }

    internal fun resetForTests() {
        manager.clearAll()
        manager.configure(null)
    }

    internal fun emitPrefetchHttpFailureForTests(
        failure: DouyinPlaybackPrefetchHttpFailure
    ) {
        reportPrefetchHttpFailure(failure)
    }

    internal fun hasRegistrationForTests(
        mediaUri: String?
    ): Boolean {
        return manager.registrationFor(mediaUri) != null
    }

    internal fun writeSnapshotForTests(
        slotIndex: Int,
        item: DouyinStreamItem,
        bytes: ByteArray
    ) {
        manager.writeSnapshotForTests(slotIndex, item, bytes)
    }

    internal fun putPreviewBytesForTests(
        item: DouyinStreamItem,
        bytes: ByteArray,
        isComplete: Boolean = false
    ) {
        manager.putPreviewBytesForTests(item, bytes, isComplete)
    }

    internal fun markDownloadFailureForTests(
        item: DouyinStreamItem,
        message: String = "unit test failure"
    ) {
        manager.markDownloadFailureForTests(item, message)
    }

    internal fun reportPrefetchHttpFailure(
        failure: DouyinPlaybackPrefetchHttpFailure
    ) {
        _prefetchHttpFailures.tryEmit(failure)
    }

    private const val SNAPSHOT_DIR_NAME = "douyin_preview_snapshots"
}

private class DouyinPlaybackPreviewManager {
    private val lock = Any()
    private val entries = LinkedHashMap<DouyinPlaybackPreviewKey, DouyinPlaybackPreviewEntry>(16, 0.75f, true)
    private val posterEntries = LinkedHashMap<DouyinPlaybackPreviewKey, ByteArray>(16, 0.75f, true)
    private val registrationsByUri = linkedMapOf<String, DouyinPlaybackPreviewRegistration>()
    private val keyByUri = linkedMapOf<String, DouyinPlaybackPreviewKey>()
    private val remoteTargetsByUri = linkedMapOf<String, DouyinPlaybackRemoteTarget>()
    private val protectedPreviewKeys = linkedSetOf<DouyinPlaybackPreviewKey>()
    private val prefetchJobs = linkedMapOf<DouyinPlaybackPreviewKey, Job>()
    private val prefetchCallsByKey = linkedMapOf<DouyinPlaybackPreviewKey, Call>()
    private val prefetchProgressByKey = linkedMapOf<DouyinPlaybackPreviewKey, DouyinPlaybackPrefetchProgress>()
    private val downloadControlsByKey = linkedMapOf<DouyinPlaybackPreviewKey, DouyinPlaybackDownloadControl>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefetchDispatcher =
        Dispatchers.IO.limitedParallelism(DOUYIN_PLAYBACK_PREFETCH_CONCURRENCY)
    private val httpDispatcher = OkHttpDispatcher().apply {
        maxRequests = DOUYIN_PLAYBACK_PREFETCH_CONCURRENCY
        maxRequestsPerHost = DOUYIN_PLAYBACK_PREFETCH_CONCURRENCY
    }
    private val connectionPool = ConnectionPool(
        maxIdleConnections = 16,
        keepAliveDuration = 10,
        timeUnit = TimeUnit.MINUTES
    )
    private val httpClient = OkHttpClient.Builder()
        .dispatcher(httpDispatcher)
        .connectionPool(connectionPool)
        .retryOnConnectionFailure(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    private var totalBytes = 0L
    private var totalPosterBytes = 0L
    private var snapshotDir: File? = null
    private var sessionGeneration = 0L
    private var foregroundBitrateBitsPerSecond: Long? = null
    private var networkBandwidthEstimateBytesPerSecond: Long? = null
    private var runtimeBandwidthEstimateBytesPerSecond: Long? = null
    private var bandwidthSampleStartedAtMs = 0L
    private var bandwidthSampleBytes = 0L
    private val prefetchBandwidthLock = Any()
    private var prefetchBandwidthWindowStartedAtMs = 0L
    private var prefetchBandwidthWindowBytes = 0L

    fun configure(snapshotDir: File?) {
        synchronized(lock) {
            this.snapshotDir = snapshotDir?.apply { mkdirs() }
        }
    }

    fun updatePlaybackWindow(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?,
        headers: Map<String, String>,
        reason: String,
        foregroundBitrateBitsPerSecond: Long?,
        totalBandwidthBytesPerSecond: Long?
    ) {
        bumpSessionGeneration()
        setBandwidthContext(
            foregroundBitrateBitsPerSecond = foregroundBitrateBitsPerSecond,
            totalBandwidthBytesPerSecond = totalBandwidthBytesPerSecond
        )
        val targets = buildPlaybackTargets(items, anchorIndex)
        updateRegistrations(targets)
        schedulePrefetch(
            targets = targets,
            headers = headers,
            reason = reason
        )
        AppLogger.d(
            TAG,
            "update playback window reason=$reason anchorIndex=$anchorIndex foregroundBitrate=${foregroundBitrateBitsPerSecond ?: 0} " +
                "totalBandwidth=${formatBandwidthForLog(currentTotalBandwidthBytesPerSecond())} " +
                "prefetchBandwidth=${formatBandwidthForLog(currentPrefetchBandwidthBytesPerSecond())} ids=${
                targets.joinToString(",") { it.awemeId }
            }"
        )
    }

    fun primeStartupWindow(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        reason: String
    ) {
        bumpSessionGeneration()
        setBandwidthContext(
            foregroundBitrateBitsPerSecond = null,
            totalBandwidthBytesPerSecond = null
        )
        val restoredPinnedItems = restorePinnedItems(limit = DOUYIN_PLAYBACK_SNAPSHOT_COUNT)
        val targets = normalizeTargets(
            restoredPinnedItems + items.take(1 + DOUYIN_PLAYBACK_PREFETCH_COUNT)
        )
        updateRegistrations(targets)
        schedulePrefetch(
            targets = normalizeTargets(items.take(1 + DOUYIN_PLAYBACK_PREFETCH_COUNT)),
            headers = headers,
            reason = reason
        )
        AppLogger.d(
            TAG,
            "prime startup window reason=$reason pinned=${restoredPinnedItems.size} ids=${targets.joinToString(",") { it.awemeId }}"
        )
    }

    fun restorePinnedItems(limit: Int): List<DouyinStreamItem> {
        val directory = synchronized(lock) { snapshotDir } ?: return emptyList()
        if (!directory.isDirectory) return emptyList()
        val restored = buildList {
            repeat(limit.coerceAtMost(DOUYIN_PLAYBACK_SNAPSHOT_COUNT)) { index ->
                val metadataFile = File(directory, snapshotMetadataName(index))
                val dataFile = File(directory, snapshotDataName(index))
                val item = parseSnapshotItem(metadataFile.readTextSafely()) ?: return@repeat
                val bytes = dataFile.readBytesSafely().takeIf { it.isNotEmpty() } ?: return@repeat
                val target = normalizeTarget(item) ?: return@repeat
                putPreviewBytes(target, bytes)
                File(directory, snapshotPosterName(index))
                    .readBytesSafely()
                    .takeIf { it.isNotEmpty() }
                    ?.let { posterBytes ->
                        putPosterBytes(target.key(), posterBytes)
                    }
                add(item)
                AppLogger.d(
                    TAG,
                    "SNAPSHOT_RESTORE awemeId=${item.awemeId} bytes=${bytes.size} slot=$index"
                )
            }
        }
        if (restored.isNotEmpty()) {
            AppLogger.d(
                TAG,
                "PINNED_STARTUP_ITEMS ids=${restored.joinToString(",") { it.awemeId }}"
            )
        }
        return restored
    }

    fun buildPlaybackPrefetchTargetsForTests(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?
    ): List<DouyinPlaybackPreviewWarmTarget> {
        return buildPlaybackTargets(items, anchorIndex)
    }

    fun persistExitSnapshots(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?
    ) {
        val directory = synchronized(lock) { snapshotDir } ?: return clearSession()
        directory.mkdirs()
        val snapshotRecords = buildExitSnapshotRecords(
            items = items,
            playbackUrisByAwemeId = emptyMap(),
            anchorIndex = anchorIndex
        )
        if (snapshotRecords.isEmpty()) {
            AppLogger.d(TAG, "SNAPSHOT_SKIP reason=no_cached_bytes")
            clearSnapshotDirectory(directory)
            return
        }
        clearSnapshotDirectory(directory)
        snapshotRecords.forEachIndexed { index, record ->
            writeSnapshot(
                directory = directory,
                slotIndex = index,
                item = record.item,
                bytes = record.bytes,
                posterBytes = record.posterBytes
            )
            AppLogger.d(
                TAG,
                "SNAPSHOT_WRITE awemeId=${record.item.awemeId} bytes=${record.bytes.size} slot=$index"
            )
        }
        clearSession()
    }

    fun persistExitSnapshotsIfCurrent(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?,
        expectedGeneration: Long,
        clearAfterPersist: Boolean = true
    ) {
        persistExitSnapshotsIfCurrent(
            items = items,
            playbackUrisByAwemeId = emptyMap(),
            anchorIndex = anchorIndex,
            expectedGeneration = expectedGeneration,
            clearAfterPersist = clearAfterPersist
        )
    }

    fun persistExitSnapshotsIfCurrent(
        items: List<DouyinStreamItem>,
        playbackUrisByAwemeId: Map<String, String>,
        anchorIndex: Int?,
        expectedGeneration: Long,
        clearAfterPersist: Boolean = true
    ) {
        if (!isCurrentGeneration(expectedGeneration)) return
        val directory = synchronized(lock) { snapshotDir }
        if (directory == null) {
            if (clearAfterPersist && isCurrentGeneration(expectedGeneration)) {
                clearSession()
            }
            return
        }
        if (!isCurrentGeneration(expectedGeneration)) return
        directory.mkdirs()
        val snapshotRecords = buildExitSnapshotRecords(
            items = items,
            playbackUrisByAwemeId = playbackUrisByAwemeId,
            anchorIndex = anchorIndex
        )
        if (snapshotRecords.isEmpty()) {
            AppLogger.d(TAG, "SNAPSHOT_SKIP reason=no_cached_bytes")
            clearSnapshotDirectory(directory)
            if (clearAfterPersist && isCurrentGeneration(expectedGeneration)) {
                clearSession()
            }
            return
        }
        clearSnapshotDirectory(directory)
        if (!isCurrentGeneration(expectedGeneration)) return
        snapshotRecords.forEachIndexed { index, record ->
            if (!isCurrentGeneration(expectedGeneration)) return
            writeSnapshot(
                directory = directory,
                slotIndex = index,
                item = record.item,
                bytes = record.bytes,
                posterBytes = record.posterBytes
            )
            AppLogger.d(
                TAG,
                "SNAPSHOT_WRITE awemeId=${record.item.awemeId} bytes=${record.bytes.size} slot=$index"
            )
        }
        if (clearAfterPersist && isCurrentGeneration(expectedGeneration)) {
            clearSession()
        }
    }

    fun storePoster(
        item: DouyinStreamItem,
        bitmap: Bitmap
    ): Boolean {
        val target = normalizeTarget(item) ?: return false
        val posterBytes = encodePosterBitmap(bitmap) ?: return false
        return synchronized(lock) {
            val existing = posterEntries[target.key()]
            if (existing != null && existing.contentEquals(posterBytes)) {
                false
            } else {
                putPosterBytesLocked(target.key(), posterBytes)
                true
            }
        }
    }

    fun readPosterBytes(item: DouyinStreamItem?): ByteArray? {
        val key = item
            ?.let(::normalizeTarget)
            ?.key()
            ?: return null
        return synchronized(lock) { posterEntries[key] }
    }

    fun clearSession() {
        val running = synchronized(lock) {
            sessionGeneration += 1
            val runningJobs = prefetchJobs.values.toList()
            val runningCalls = prefetchCallsByKey.values.toList()
            prefetchJobs.clear()
            prefetchCallsByKey.clear()
            prefetchProgressByKey.clear()
            downloadControlsByKey.clear()
            entries.clear()
            posterEntries.clear()
            registrationsByUri.clear()
            keyByUri.clear()
            remoteTargetsByUri.clear()
            protectedPreviewKeys.clear()
            totalBytes = 0L
            totalPosterBytes = 0L
            foregroundBitrateBitsPerSecond = null
            resetPrefetchBandwidthLimiterLocked()
            runningJobs to runningCalls
        }
        running.first.forEach { job ->
            if (job.isActive) {
                job.cancel()
            }
        }
        running.second.forEach { call ->
            call.cancel()
        }
    }

    fun clearAll() {
        clearSession()
        synchronized(lock) {
            resetBandwidthEstimatesLocked()
        }
        synchronized(lock) { snapshotDir }?.let(::clearSnapshotDirectory)
    }

    fun captureSessionGeneration(): Long {
        return synchronized(lock) { sessionGeneration }
    }

    fun debugSnapshot(): DouyinPlaybackPreviewDebugSnapshot {
        return synchronized(lock) {
            DouyinPlaybackPreviewDebugSnapshot(
                sessionGeneration = sessionGeneration,
                totalPreviewBytes = totalBytes,
                totalPosterBytes = totalPosterBytes,
                activePrefetches = prefetchProgressByKey.values
                    .sortedBy { it.prefetchOrder }
                    .map { progress ->
                        DouyinPlaybackPrefetchDebugEntry(
                            awemeId = progress.awemeId,
                            mediaUri = progress.mediaUri,
                            downloadedBytes = progress.downloadedBytes,
                            budgetBytes = progress.budgetBytes,
                            prefetchOrder = progress.prefetchOrder,
                            reason = progress.reason,
                            startedAtMs = progress.startedAtMs
                        )
                    },
                registrations = registrationsByUri.values.map { registration ->
                    val cachedBytes = entries[registration.key]?.bytes?.size ?: 0
                    DouyinPlaybackPreviewRegistrationDebugEntry(
                        awemeId = registration.key.awemeId,
                        mediaUri = registration.mediaUri,
                        cachedBytes = cachedBytes,
                        budgetBytes = registration.budgetBytes,
                        captureEnabled = registration.captureEnabled,
                        isPrefetching = prefetchProgressByKey.containsKey(registration.key)
                    )
                },
                memoryEntries = entries.values.toList().asReversed().map { entry ->
                    DouyinPlaybackPreviewMemoryDebugEntry(
                        awemeId = entry.key.awemeId,
                        mediaUri = entry.mediaUri,
                        cachedBytes = entry.bytes.size,
                        budgetBytes = entry.budgetBytes
                    )
                }
            )
        }
    }

    fun writeSnapshotForTests(slotIndex: Int, item: DouyinStreamItem, bytes: ByteArray) {
        val directory = synchronized(lock) { snapshotDir } ?: return
        directory.mkdirs()
        writeSnapshot(
            directory = directory,
            slotIndex = slotIndex,
            item = item,
            bytes = bytes,
            posterBytes = null
        )
    }

    fun putPreviewBytesForTests(
        item: DouyinStreamItem,
        bytes: ByteArray,
        isComplete: Boolean
    ) {
        val target = normalizeTarget(item) ?: return
        synchronized(lock) {
            registrationsByUri[target.mediaUri] = DouyinPlaybackPreviewRegistration(
                key = target.key(),
                mediaUri = target.mediaUri,
                durationMs = target.durationMs,
                budgetBytes = max(bytes.size, estimatePreviewBytes(null, target.durationMs)),
                captureEnabled = true,
                remoteUri = target.mediaUri.takeIf { it.startsWith("http", ignoreCase = true) },
                headers = emptyMap()
            )
            keyByUri[target.mediaUri] = target.key()
            putPreviewBytesLocked(
                key = target.key(),
                mediaUri = target.mediaUri,
                bytes = bytes,
                budgetBytes = max(bytes.size, estimatePreviewBytes(null, target.durationMs)),
                isComplete = isComplete,
                failureMessage = null
            )
        }
    }

    fun markDownloadFailureForTests(item: DouyinStreamItem, message: String) {
        val target = normalizeTarget(item) ?: return
        markDownloadFailure(
            target = target,
            budgetBytes = estimatePreviewBytes(null, target.durationMs),
            message = message
        )
    }

    fun peekBytes(mediaUri: String?): ByteArray? {
        val normalizedUri = mediaUri?.trim().orEmpty()
        if (normalizedUri.isEmpty()) return null
        return synchronized(lock) {
            val key = keyByUri[normalizedUri] ?: return@synchronized null
            entries[key]?.bytes
        }
    }

    fun aliasPreviewBytes(
        sourceUri: String?,
        targetItem: DouyinStreamItem
    ): Boolean {
        val normalizedSourceUri = sourceUri?.trim().orEmpty()
        val target = normalizeTarget(targetItem) ?: return false
        if (normalizedSourceUri.isEmpty() || normalizedSourceUri == target.mediaUri) return false
        return synchronized(lock) {
            val sourceKey = keyByUri[normalizedSourceUri] ?: return@synchronized false
            val sourceEntry = entries[sourceKey] ?: return@synchronized false
            putPreviewBytesLocked(
                key = target.key(),
                mediaUri = target.mediaUri,
                bytes = sourceEntry.bytes,
                budgetBytes = max(sourceEntry.budgetBytes, estimatePreviewBytes(null, target.durationMs))
            )
            true
        }
    }

    fun registerRemotePlaybackTarget(
        targetItem: DouyinStreamItem,
        remoteUri: String,
        headers: Map<String, String>
    ): Boolean {
        val target = normalizeTarget(targetItem) ?: return false
        val normalizedRemoteUri = remoteUri.trim()
        if (!normalizedRemoteUri.startsWith("http", ignoreCase = true)) return false
        val cleanHeaders = headers
            .filterKeys { it.isNotBlank() }
            .filterValues { it.isNotBlank() }
        synchronized(lock) {
            val remoteTarget = DouyinPlaybackRemoteTarget(
                remoteUri = normalizedRemoteUri,
                headers = cleanHeaders
            )
            remoteTargetsByUri[target.mediaUri] = remoteTarget
            keyByUri[target.mediaUri] = target.key()
            registrationsByUri[target.mediaUri]?.let { existing ->
                registrationsByUri[target.mediaUri] = existing.copy(
                    remoteUri = normalizedRemoteUri,
                    headers = cleanHeaders
                )
            }
        }
        return true
    }

    private fun bumpSessionGeneration() {
        synchronized(lock) {
            sessionGeneration += 1
        }
    }

    private fun setBandwidthContext(
        foregroundBitrateBitsPerSecond: Long?,
        totalBandwidthBytesPerSecond: Long?
    ) {
        synchronized(lock) {
            this.foregroundBitrateBitsPerSecond = foregroundBitrateBitsPerSecond?.takeIf { it > 0L }
            networkBandwidthEstimateBytesPerSecond = totalBandwidthBytesPerSecond?.takeIf { it > 0L }
        }
    }

    private fun isCurrentGeneration(expectedGeneration: Long): Boolean {
        return synchronized(lock) { sessionGeneration == expectedGeneration }
    }

    fun registrationFor(mediaUri: String?): DouyinPlaybackPreviewRegistration? {
        val normalizedUri = mediaUri?.trim().orEmpty()
        if (normalizedUri.isEmpty()) return null
        return synchronized(lock) { registrationsByUri[normalizedUri] }
    }

    fun adjustBudgetFromContentLength(mediaUri: String?, contentLength: Long) {
        val normalizedUri = mediaUri?.trim().orEmpty()
        if (normalizedUri.isEmpty() || contentLength <= 0L) return
        synchronized(lock) {
            val registration = registrationsByUri[normalizedUri] ?: return
            val adjustedBudget = estimatePreviewBytes(
                contentLength = contentLength,
                durationMs = registration.durationMs
            )
            if (adjustedBudget <= registration.budgetBytes) return
            registrationsByUri[normalizedUri] = registration.copy(budgetBytes = adjustedBudget)
            val entry = entries[registration.key] ?: return
            entries[registration.key] = entry.copy(budgetBytes = adjustedBudget)
        }
    }

    fun recordNetworkTransferBytes(byteCount: Int) {
        if (byteCount <= 0) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (bandwidthSampleStartedAtMs <= 0L) {
                bandwidthSampleStartedAtMs = now
                bandwidthSampleBytes = 0L
            }
            bandwidthSampleBytes += byteCount.toLong()
            val elapsedMs = now - bandwidthSampleStartedAtMs
            if (
                elapsedMs >= BANDWIDTH_ESTIMATE_MIN_SAMPLE_MS &&
                bandwidthSampleBytes >= BANDWIDTH_ESTIMATE_MIN_SAMPLE_BYTES
            ) {
                val sampleBytesPerSecond = bandwidthSampleBytes * 1_000L / elapsedMs.coerceAtLeast(1L)
                runtimeBandwidthEstimateBytesPerSecond = runtimeBandwidthEstimateBytesPerSecond
                    ?.let { previous -> (previous * 3L + sampleBytesPerSecond) / 4L }
                    ?: sampleBytesPerSecond
                bandwidthSampleStartedAtMs = now
                bandwidthSampleBytes = 0L
            }
        }
    }

    fun captureFromUpstream(
        mediaUri: String?,
        absolutePosition: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ) {
        val normalizedUri = mediaUri?.trim().orEmpty()
        if (normalizedUri.isEmpty() || length <= 0 || absolutePosition < 0L) return
        synchronized(lock) {
            val registration = registrationsByUri[normalizedUri]
                ?.takeIf { it.captureEnabled }
                ?: return
            val entry = entries[registration.key]
            val existingBytes = entry?.bytes ?: ByteArray(0)
            if (absolutePosition != existingBytes.size.toLong()) {
                return
            }
            val budgetBytes = registration.budgetBytes
            val remainingBytes = budgetBytes - existingBytes.size
            if (remainingBytes <= 0) return
            val bytesToCopy = min(length, remainingBytes)
            val merged = existingBytes.copyOf(existingBytes.size + bytesToCopy)
            System.arraycopy(buffer, offset, merged, existingBytes.size, bytesToCopy)
            putPreviewBytesLocked(
                key = registration.key,
                mediaUri = normalizedUri,
                bytes = merged,
                budgetBytes = budgetBytes
            )
            if (existingBytes.isEmpty() || merged.size == budgetBytes) {
                AppLogger.d(
                    TAG,
                    "RAM_CAPTURE awemeId=${registration.key.awemeId} bytes=${merged.size} budget=$budgetBytes"
                )
            }
        }
    }

    private fun buildPlaybackTargets(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?
    ): List<DouyinPlaybackPreviewWarmTarget> {
        if (items.isEmpty()) return emptyList()
        val center = anchorIndex?.coerceIn(0, items.lastIndex) ?: return emptyList()
        val orderedItems = buildList {
            add(items[center])
            for (offset in 1..DOUYIN_PLAYBACK_PREFETCH_COUNT) {
                items.getOrNull(center + offset)?.let(::add)
            }
            for (offset in 1..DOUYIN_PLAYBACK_BACKWARD_REGISTRATION_COUNT) {
                items.getOrNull(center - offset)?.let(::add)
            }
        }
        return normalizeTargets(orderedItems)
    }

    private fun buildPlaybackPrefetchTargets(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?
    ): List<DouyinPlaybackPreviewWarmTarget> {
        if (items.isEmpty()) return emptyList()
        val center = anchorIndex?.coerceIn(0, items.lastIndex) ?: return emptyList()
        val orderedItems = buildList {
            for (offset in 1..DOUYIN_PLAYBACK_PREFETCH_COUNT) {
                items.getOrNull(center + offset)?.let(::add)
            }
            for (offset in 1..DOUYIN_PLAYBACK_BACKWARD_REGISTRATION_COUNT) {
                items.getOrNull(center - offset)?.let(::add)
            }
        }
        return normalizeTargets(orderedItems)
    }

    private fun normalizeTargets(items: List<DouyinStreamItem>): List<DouyinPlaybackPreviewWarmTarget> {
        val result = linkedMapOf<DouyinPlaybackPreviewKey, DouyinPlaybackPreviewWarmTarget>()
        items.forEach { item ->
            val target = normalizeTarget(item) ?: return@forEach
            result.putIfAbsent(
                target.key(),
                target
            )
        }
        return result.values.take(DOUYIN_PLAYBACK_PREVIEW_ENTRY_LIMIT)
    }

    private fun normalizeTarget(item: DouyinStreamItem): DouyinPlaybackPreviewWarmTarget? {
        val awemeId = item.awemeId.trim()
        val mediaUri = item.playUrl.trim()
        if (awemeId.isEmpty() || mediaUri.isEmpty()) return null
        return DouyinPlaybackPreviewWarmTarget(
            awemeId = awemeId,
            mediaUri = mediaUri,
            playUrlResolvedAtMs = item.playUrlResolvedAtMs,
            durationMs = item.durationMs.coerceAtLeast(0L)
        )
    }

    private fun updateRegistrations(targets: List<DouyinPlaybackPreviewWarmTarget>) {
        val registrations = targets.associateBy(
            keySelector = { it.mediaUri },
            valueTransform = { target ->
                val remoteTarget = synchronized(lock) { remoteTargetsByUri[target.mediaUri] }
                DouyinPlaybackPreviewRegistration(
                    key = target.key(),
                    mediaUri = target.mediaUri,
                    durationMs = target.durationMs,
                    budgetBytes = estimatePreviewBytes(
                        contentLength = null,
                        durationMs = target.durationMs
                    ),
                    captureEnabled = true,
                    remoteUri = remoteTarget?.remoteUri
                        ?: target.mediaUri.takeIf { it.startsWith("http", ignoreCase = true) },
                    headers = remoteTarget?.headers.orEmpty()
                )
            }
        )
        synchronized(lock) {
            registrationsByUri.clear()
            registrationsByUri.putAll(registrations)
            protectedPreviewKeys.clear()
            targets.firstOrNull()?.key()?.let(protectedPreviewKeys::add)
            registrations.values.forEach { registration ->
                keyByUri[registration.mediaUri] = registration.key
                val existing = entries[registration.key]
                if (existing != null && registration.budgetBytes > existing.budgetBytes) {
                    entries[registration.key] = existing.copy(budgetBytes = registration.budgetBytes)
                }
            }
            trimToBudgetLocked()
        }
    }

    private fun schedulePrefetch(
        targets: List<DouyinPlaybackPreviewWarmTarget>,
        headers: Map<String, String>,
        reason: String
    ) {
        val cleanFallbackHeaders = headers.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() }
        val downloadPlans = targets.mapIndexedNotNull { index, target ->
            val registration = synchronized(lock) { registrationsByUri[target.mediaUri] }
            val remoteUri = registration?.remoteUri
                ?: target.mediaUri.takeIf { it.startsWith("http", ignoreCase = true) }
                ?: return@mapIndexedNotNull null
            val requiredBytes = if (index == 0) {
                MAX_TOTAL_PREVIEW_BYTES.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            } else {
                estimatePrefetchBytes(
                    contentLength = null,
                    durationMs = target.durationMs,
                    prefetchOrder = index
                )
            }
            val existingBytes = synchronized(lock) { entries[target.key()]?.bytes?.size ?: 0 }
            if (existingBytes >= requiredBytes) return@mapIndexedNotNull null
            DouyinPlaybackDownloadPlan(
                target = target,
                remoteUri = remoteUri,
                headers = registration?.headers?.takeIf { it.isNotEmpty() } ?: cleanFallbackHeaders,
                requiredBytes = requiredBytes,
                initialBytes = existingBytes.coerceAtMost(requiredBytes),
                prefetchOrder = index,
                completeOnEnd = index == 0
            )
        }
        val targetKeys = downloadPlans.mapTo(linkedSetOf()) { it.target.key() }
        val tasksToCancel = synchronized(lock) {
            val removed = prefetchJobs
                .filterKeys { !targetKeys.contains(it) }
                .values
                .toList()
            val removedCalls = prefetchCallsByKey
                .filterKeys { !targetKeys.contains(it) }
                .values
                .toList()
            prefetchJobs.entries.removeAll { !targetKeys.contains(it.key) }
            prefetchCallsByKey.entries.removeAll { !targetKeys.contains(it.key) }
            downloadControlsByKey.keys.removeAll { !targetKeys.contains(it) }
            removed to removedCalls
        }
        tasksToCancel.first.forEach { job ->
            if (job.isActive) {
                job.cancel()
            }
        }
        tasksToCancel.second.forEach { call ->
            call.cancel()
        }
        tasksToCancel.first.forEach { job ->
            if (job.isActive) {
                scope.launch {
                    runCatching { job.cancelAndJoin() }
                }
            }
        }
        downloadPlans.forEach { plan ->
            val target = plan.target
            val key = target.key()
            val shouldLaunch = synchronized(lock) {
                downloadControlsByKey[key] = plan.toDownloadControl(reason)
                val running = prefetchJobs[key]
                val runningProgress = prefetchProgressByKey[key]
                if (running?.isActive == true) {
                    prefetchProgressByKey[key] = DouyinPlaybackPrefetchProgress(
                        key = key,
                        awemeId = target.awemeId,
                        mediaUri = target.mediaUri,
                        downloadedBytes = plan.initialBytes,
                        budgetBytes = max(runningProgress?.budgetBytes ?: 0, plan.requiredBytes),
                        prefetchOrder = plan.prefetchOrder,
                        reason = reason,
                        startedAtMs = runningProgress?.startedAtMs ?: System.currentTimeMillis()
                    )
                    false
                } else {
                    prefetchProgressByKey[key] = DouyinPlaybackPrefetchProgress(
                        key = key,
                        awemeId = target.awemeId,
                        mediaUri = target.mediaUri,
                        downloadedBytes = plan.initialBytes,
                        budgetBytes = plan.requiredBytes,
                        prefetchOrder = plan.prefetchOrder,
                        reason = reason,
                        startedAtMs = System.currentTimeMillis()
                    )
                    prefetchJobs[key] = scope.launch(prefetchDispatcher) {
                        try {
                            prefetchTarget(
                                plan = plan,
                                reason = reason,
                            )
                        } finally {
                            synchronized(lock) {
                                if (prefetchJobs[key] === coroutineContext[Job]) {
                                    prefetchJobs.remove(key)
                                    prefetchProgressByKey.remove(key)
                                    downloadControlsByKey.remove(key)
                                }
                            }
                        }
                    }
                    true
                }
            }
            if (shouldLaunch) {
                AppLogger.d(
                    TAG,
                    "queue prefetch awemeId=${target.awemeId} bytes=${plan.requiredBytes} order=${plan.prefetchOrder} " +
                        "complete=${plan.completeOnEnd} reason=$reason"
                )
            }
        }
    }

    private fun DouyinPlaybackDownloadPlan.toDownloadControl(
        reason: String
    ): DouyinPlaybackDownloadControl {
        return DouyinPlaybackDownloadControl(
            requiredBytes = requiredBytes,
            prefetchOrder = prefetchOrder,
            completeOnEnd = completeOnEnd,
            reason = reason
        )
    }

    private suspend fun prefetchTarget(
        plan: DouyinPlaybackDownloadPlan,
        reason: String
    ) {
        val target = plan.target
        val budgetBytes = plan.requiredBytes
        var initialBytes = plan.initialBytes
        if (budgetBytes <= 0) return
        updatePrefetchProgress(
            key = target.key(),
            awemeId = target.awemeId,
            mediaUri = target.mediaUri,
            downloadedBytes = initialBytes,
            budgetBytes = budgetBytes,
            prefetchOrder = plan.prefetchOrder,
            reason = reason
        )
        val requestBuilder = Request.Builder()
            .url(plan.remoteUri)
        requestBuilder.header("Range", "bytes=$initialBytes-")
        plan.headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank() && !key.equals("Range", ignoreCase = true)) {
                requestBuilder.header(key, value)
            }
        }
        val call = httpClient.newCall(requestBuilder.build())
        synchronized(lock) {
            prefetchCallsByKey[target.key()] = call
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(
                        TAG,
                        "prefetch http failure awemeId=${target.awemeId} code=${response.code} reason=$reason"
                    )
                    markDownloadFailure(
                        target = target,
                        budgetBytes = budgetBytes,
                        message = "HTTP ${response.code}"
                    )
                    DouyinPlaybackPreviewCache.reportPrefetchHttpFailure(
                        DouyinPlaybackPrefetchHttpFailure(
                            awemeId = target.awemeId,
                            mediaUri = target.mediaUri,
                            httpStatusCode = response.code,
                            reason = reason,
                            occurredAtMs = System.currentTimeMillis()
                        )
                    )
                    return
                }
                val body = response.body ?: return
                if (response.code == 200 && initialBytes > 0) {
                    resetPreviewBytes(target)
                    initialBytes = 0
                }
                var downloadedBytes = initialBytes
                var lastLoggedBytes = initialBytes
                var reachedStreamEnd = false
                val readBuffer = ByteArray(PREFETCH_READ_CHUNK_BYTES)
                body.byteStream().use { input ->
                    while (true) {
                        val control = downloadControlFor(target.key()) ?: plan.toDownloadControl(reason)
                        if (downloadedBytes >= control.requiredBytes) break
                        val bytesToRead = min(
                            PREFETCH_READ_CHUNK_BYTES,
                            control.requiredBytes - downloadedBytes
                        )
                        if (!control.completeOnEnd) {
                            throttlePrefetchBandwidth(
                                requestedBytes = bytesToRead,
                                shouldThrottle = {
                                    downloadControlFor(target.key())?.completeOnEnd != true
                                }
                            )
                        }
                        val read = input.read(readBuffer, 0, bytesToRead)
                        if (read <= 0) {
                            reachedStreamEnd = true
                            break
                        }
                        val appended = appendPreviewBytes(
                            target = target,
                            absolutePosition = downloadedBytes,
                            buffer = readBuffer,
                            length = read,
                            budgetBytes = control.requiredBytes
                        )
                        if (!appended) {
                            AppLogger.d(
                                TAG,
                                "prefetch append skipped awemeId=${target.awemeId} position=$downloadedBytes reason=$reason"
                            )
                            return
                        }
                        downloadedBytes += read
                        recordNetworkTransferBytes(read)
                        updatePrefetchProgress(
                            key = target.key(),
                            awemeId = target.awemeId,
                            mediaUri = target.mediaUri,
                            downloadedBytes = downloadedBytes,
                            budgetBytes = control.requiredBytes,
                            prefetchOrder = control.prefetchOrder,
                            reason = control.reason
                        )
                        if (
                            downloadedBytes >= MIN_ENTRY_BYTES &&
                            downloadedBytes - lastLoggedBytes >= PREFETCH_PROGRESS_COMMIT_BYTES
                        ) {
                            lastLoggedBytes = downloadedBytes
                            AppLogger.d(
                                TAG,
                                "RAM_PREFETCH_PROGRESS awemeId=${target.awemeId} bytes=$downloadedBytes reason=$reason"
                            )
                        }
                    }
                }
                val finalControl = downloadControlFor(target.key()) ?: plan.toDownloadControl(reason)
                if (reachedStreamEnd) {
                    markDownloadComplete(target)
                } else if (finalControl.completeOnEnd && downloadedBytes >= finalControl.requiredBytes) {
                    markDownloadFailure(
                        target = target,
                        budgetBytes = finalControl.requiredBytes,
                        message = "RAM budget exceeded"
                    )
                }
                if (downloadedBytes > initialBytes) {
                    if (finalControl.completeOnEnd) {
                        AppLogger.d(
                            TAG,
                            "RAM_DOWNLOAD awemeId=${target.awemeId} bytes=$downloadedBytes complete=${finalControl.completeOnEnd} reason=${finalControl.reason}"
                        )
                    } else {
                        AppLogger.d(
                            TAG,
                            "RAM_PREFETCH_PARTIAL awemeId=${target.awemeId} bytes=$downloadedBytes reason=${finalControl.reason}"
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (!coroutineContext.isActive) return
            val control = downloadControlFor(target.key()) ?: plan.toDownloadControl(reason)
            markDownloadFailure(
                target = target,
                budgetBytes = control.requiredBytes,
                message = error.message ?: error::class.java.simpleName
            )
            AppLogger.w(TAG, "prefetch failed awemeId=${target.awemeId}", error)
        } finally {
            synchronized(lock) {
                if (prefetchCallsByKey[target.key()] === call) {
                    prefetchCallsByKey.remove(target.key())
                }
            }
        }
    }

    private suspend fun waitForPrefetchBandwidthAvailability(
        awemeId: String,
        reason: String
    ) {
        var logged = false
        while (currentPrefetchBandwidthBytesPerSecond() <= 0L) {
            if (!logged) {
                AppLogger.d(
                    TAG,
                    "pause prefetch awemeId=$awemeId reason=$reason foreground_reserve_exhausted"
                )
                logged = true
            }
            delay(PREFETCH_BANDWIDTH_WAIT_MS)
        }
    }

    private fun downloadControlFor(
        key: DouyinPlaybackPreviewKey
    ): DouyinPlaybackDownloadControl? {
        return synchronized(lock) { downloadControlsByKey[key] }
    }

    private suspend fun throttlePrefetchBandwidth(
        requestedBytes: Int,
        shouldThrottle: () -> Boolean = { true }
    ) {
        if (requestedBytes <= 0) return
        while (shouldThrottle()) {
            val limitBytesPerSecond = currentPrefetchBandwidthBytesPerSecond()
            val delayMs = if (limitBytesPerSecond <= 0L) {
                PREFETCH_BANDWIDTH_WAIT_MS
            } else {
                synchronized(prefetchBandwidthLock) {
                    val now = System.currentTimeMillis()
                    if (
                        prefetchBandwidthWindowStartedAtMs <= 0L ||
                        now - prefetchBandwidthWindowStartedAtMs >= PREFETCH_BANDWIDTH_WINDOW_MS
                    ) {
                        prefetchBandwidthWindowStartedAtMs = now
                        prefetchBandwidthWindowBytes = 0L
                    }
                    if (prefetchBandwidthWindowBytes + requestedBytes <= limitBytesPerSecond) {
                        prefetchBandwidthWindowBytes += requestedBytes.toLong()
                        0L
                    } else {
                        (PREFETCH_BANDWIDTH_WINDOW_MS - (now - prefetchBandwidthWindowStartedAtMs))
                            .coerceAtLeast(1L)
                    }
                }
            }
            if (delayMs <= 0L) return
            delay(delayMs)
        }
    }

    private fun currentPrefetchBandwidthBytesPerSecond(): Long {
        val bandwidthState = synchronized(lock) {
            foregroundBitrateBitsPerSecond to currentTotalBandwidthBytesPerSecondLocked()
        }
        return estimateDouyinPrefetchBandwidthBytesPerSecond(
            foregroundBitrateBitsPerSecond = bandwidthState.first,
            totalBandwidthBytesPerSecond = bandwidthState.second
        )
    }

    private fun currentTotalBandwidthBytesPerSecond(): Long? {
        return synchronized(lock) { currentTotalBandwidthBytesPerSecondLocked() }
    }

    private fun currentTotalBandwidthBytesPerSecondLocked(): Long? {
        return runtimeBandwidthEstimateBytesPerSecond
            ?: if (foregroundBitrateBitsPerSecond != null) {
                null
            } else {
                networkBandwidthEstimateBytesPerSecond
            }
    }

    private fun resetPrefetchBandwidthLimiterLocked() {
        synchronized(prefetchBandwidthLock) {
            prefetchBandwidthWindowStartedAtMs = 0L
            prefetchBandwidthWindowBytes = 0L
        }
    }

    private fun resetBandwidthEstimatesLocked() {
        foregroundBitrateBitsPerSecond = null
        networkBandwidthEstimateBytesPerSecond = null
        runtimeBandwidthEstimateBytesPerSecond = null
        bandwidthSampleStartedAtMs = 0L
        bandwidthSampleBytes = 0L
        resetPrefetchBandwidthLimiterLocked()
    }

    private fun putPreviewBytes(
        target: DouyinPlaybackPreviewWarmTarget,
        bytes: ByteArray
    ) {
        synchronized(lock) {
            putPreviewBytesLocked(
                key = target.key(),
                mediaUri = target.mediaUri,
                bytes = bytes,
                budgetBytes = estimatePreviewBytes(contentLength = null, durationMs = target.durationMs)
            )
        }
    }

    fun readCacheBytesBlocking(
        mediaUri: String?,
        absolutePosition: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
        shouldContinue: () -> Boolean = { true }
    ): Int {
        val normalizedUri = mediaUri?.trim().orEmpty()
        if (normalizedUri.isEmpty()) {
            throw IOException("empty Douyin playback cache URI")
        }
        if (length <= 0) return 0
        var loggedWait = false
        while (true) {
            if (!shouldContinue()) {
                return C.RESULT_END_OF_INPUT
            }
            synchronized(lock) {
                val key = keyByUri[normalizedUri]
                    ?: registrationsByUri[normalizedUri]?.key
                    ?: throw IOException("unregistered Douyin playback cache URI: $normalizedUri")
                val entry = entries[key]
                if (entry != null) {
                    entry.failureMessage?.let { message ->
                        if (absolutePosition >= entry.bytes.size.toLong()) {
                            throw IOException("Douyin download failed for $normalizedUri: $message")
                        }
                    }
                    val availableBytes = entry.bytes.size.toLong() - absolutePosition
                    if (availableBytes > 0L) {
                        val bytesToCopy = min(length.toLong(), availableBytes).toInt()
                        System.arraycopy(
                            entry.bytes,
                            absolutePosition.toInt(),
                            buffer,
                            offset,
                            bytesToCopy
                        )
                        return bytesToCopy
                    }
                    if (entry.isComplete) {
                        return C.RESULT_END_OF_INPUT
                    }
                }
            }
            if (!loggedWait) {
                AppLogger.d(
                    TAG,
                    "RAM_WAIT uri=$normalizedUri position=$absolutePosition requested=$length"
                )
                loggedWait = true
            }
            try {
                Thread.sleep(CACHE_READ_WAIT_MS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("interrupted while waiting for Douyin RAM cache", interrupted)
            }
        }
    }

    private fun appendPreviewBytes(
        target: DouyinPlaybackPreviewWarmTarget,
        absolutePosition: Int,
        buffer: ByteArray,
        length: Int,
        budgetBytes: Int
    ): Boolean {
        if (length <= 0 || absolutePosition < 0) return false
        return synchronized(lock) {
            val key = target.key()
            val existing = entries[key]
            val existingBytes = existing?.bytes ?: ByteArray(0)
            if (absolutePosition != existingBytes.size) {
                return@synchronized false
            }
            val remainingBytes = budgetBytes - existingBytes.size
            if (remainingBytes <= 0) return@synchronized false
            val bytesToCopy = min(length, remainingBytes)
            val merged = existingBytes.copyOf(existingBytes.size + bytesToCopy)
            System.arraycopy(buffer, 0, merged, existingBytes.size, bytesToCopy)
            putPreviewBytesLocked(
                key = key,
                mediaUri = target.mediaUri,
                bytes = merged,
                budgetBytes = max(budgetBytes, existing?.budgetBytes ?: 0),
                isComplete = false,
                failureMessage = null
            )
            true
        }
    }

    private fun resetPreviewBytes(target: DouyinPlaybackPreviewWarmTarget) {
        synchronized(lock) {
            val key = target.key()
            entries.remove(key)?.let { existing ->
                totalBytes -= existing.bytes.size.toLong()
                keyByUri.remove(existing.mediaUri)
            }
            keyByUri[target.mediaUri] = key
        }
    }

    private fun markDownloadComplete(target: DouyinPlaybackPreviewWarmTarget) {
        synchronized(lock) {
            val key = target.key()
            val existing = entries[key]
            if (existing != null) {
                entries[key] = existing.copy(isComplete = true, failureMessage = null)
            } else {
                entries[key] = DouyinPlaybackPreviewEntry(
                    key = key,
                    mediaUri = target.mediaUri,
                    bytes = ByteArray(0),
                    budgetBytes = estimatePreviewBytes(contentLength = null, durationMs = target.durationMs),
                    isComplete = true,
                    failureMessage = null
                )
                keyByUri[target.mediaUri] = key
            }
        }
    }

    private fun markDownloadFailure(
        target: DouyinPlaybackPreviewWarmTarget,
        budgetBytes: Int,
        message: String
    ) {
        synchronized(lock) {
            val key = target.key()
            val existing = entries[key]
            if (existing != null) {
                entries[key] = existing.copy(failureMessage = message)
            } else {
                entries[key] = DouyinPlaybackPreviewEntry(
                    key = key,
                    mediaUri = target.mediaUri,
                    bytes = ByteArray(0),
                    budgetBytes = budgetBytes,
                    isComplete = false,
                    failureMessage = message
                )
                keyByUri[target.mediaUri] = key
            }
        }
    }

    private fun putPosterBytes(
        key: DouyinPlaybackPreviewKey,
        bytes: ByteArray
    ) {
        synchronized(lock) {
            putPosterBytesLocked(key, bytes)
        }
    }

    private fun putPreviewBytesLocked(
        key: DouyinPlaybackPreviewKey,
        mediaUri: String,
        bytes: ByteArray,
        budgetBytes: Int,
        isComplete: Boolean = false,
        failureMessage: String? = null
    ) {
        val existing = entries[key]
        if (existing != null && existing.bytes.size >= bytes.size) {
            keyByUri[existing.mediaUri] = key
            keyByUri[mediaUri] = key
            entries[key] = existing.copy(
                budgetBytes = max(existing.budgetBytes, budgetBytes),
                isComplete = existing.isComplete || isComplete,
                failureMessage = failureMessage
            )
            return
        }
        existing?.let { totalBytes -= it.bytes.size.toLong() }
        val cappedBytes = if (bytes.size > budgetBytes) bytes.copyOf(budgetBytes) else bytes
        entries[key] = DouyinPlaybackPreviewEntry(
            key = key,
            mediaUri = mediaUri,
            bytes = cappedBytes,
            budgetBytes = max(budgetBytes, cappedBytes.size),
            isComplete = isComplete,
            failureMessage = failureMessage
        )
        keyByUri[mediaUri] = key
        totalBytes += cappedBytes.size.toLong()
        trimToBudgetLocked()
    }

    private fun updatePrefetchProgress(
        key: DouyinPlaybackPreviewKey,
        awemeId: String,
        mediaUri: String,
        downloadedBytes: Int,
        budgetBytes: Int,
        prefetchOrder: Int,
        reason: String
    ) {
        synchronized(lock) {
            val existing = prefetchProgressByKey[key]
            prefetchProgressByKey[key] = DouyinPlaybackPrefetchProgress(
                key = key,
                awemeId = awemeId,
                mediaUri = mediaUri,
                downloadedBytes = downloadedBytes.coerceAtLeast(0).coerceAtMost(budgetBytes),
                budgetBytes = budgetBytes,
                prefetchOrder = prefetchOrder,
                reason = reason,
                startedAtMs = existing?.startedAtMs ?: System.currentTimeMillis()
            )
        }
    }

    private fun putPosterBytesLocked(
        key: DouyinPlaybackPreviewKey,
        bytes: ByteArray
    ) {
        val existing = posterEntries[key]
        existing?.let { totalPosterBytes -= it.size.toLong() }
        posterEntries[key] = bytes
        totalPosterBytes += bytes.size.toLong()
        trimPosterBudgetLocked()
    }

    private fun trimToBudgetLocked() {
        while (
            entries.size > DOUYIN_PLAYBACK_PREVIEW_ENTRY_LIMIT ||
            totalBytes > MAX_TOTAL_PREVIEW_BYTES
        ) {
            val entry = entries.entries.firstOrNull { candidate ->
                !protectedPreviewKeys.contains(candidate.key)
            } ?: break
            totalBytes -= entry.value.bytes.size.toLong()
            keyByUri.remove(entry.value.mediaUri)
            entries.remove(entry.key)
        }
    }

    private fun trimPosterBudgetLocked() {
        val iterator = posterEntries.entries.iterator()
        while (
            posterEntries.size > DOUYIN_PLAYBACK_PREVIEW_ENTRY_LIMIT ||
            totalPosterBytes > MAX_TOTAL_POSTER_BYTES
        ) {
            if (!iterator.hasNext()) break
            val entry = iterator.next()
            totalPosterBytes -= entry.value.size.toLong()
            iterator.remove()
        }
    }

    private fun resolveExitSnapshotItems(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?
    ): List<DouyinStreamItem> {
        val center = anchorIndex?.coerceIn(0, items.lastIndex) ?: return emptyList()
        return buildList {
            repeat(DOUYIN_PLAYBACK_SNAPSHOT_COUNT) { offset ->
                items.getOrNull(center + offset)?.let(::add)
            }
        }
    }

    private fun buildExitSnapshotRecords(
        items: List<DouyinStreamItem>,
        playbackUrisByAwemeId: Map<String, String>,
        anchorIndex: Int?
    ): List<DouyinPlaybackExitSnapshotRecord> {
        return resolveExitSnapshotItems(items, anchorIndex).mapNotNull { item ->
            val playbackUri = playbackUrisByAwemeId[item.awemeId]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val bytes = peekBytes(playbackUri)
                ?.takeIf { it.isNotEmpty() }
                ?: peekBytes(item.playUrl)
                    ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            DouyinPlaybackExitSnapshotRecord(
                item = item,
                bytes = bytes,
                posterBytes = readPosterBytes(item)
            )
        }
    }

    private fun writeSnapshot(
        directory: File,
        slotIndex: Int,
        item: DouyinStreamItem,
        bytes: ByteArray,
        posterBytes: ByteArray?
    ) {
        val metadataFile = File(directory, snapshotMetadataName(slotIndex))
        val dataFile = File(directory, snapshotDataName(slotIndex))
        val posterFile = File(directory, snapshotPosterName(slotIndex))
        val tempMetadataFile = File(directory, "${snapshotMetadataName(slotIndex)}.tmp")
        val tempDataFile = File(directory, "${snapshotDataName(slotIndex)}.tmp")
        val tempPosterFile = File(directory, "${snapshotPosterName(slotIndex)}.tmp")
        val hasPosterBytes = posterBytes != null && posterBytes.isNotEmpty()
        tempMetadataFile.writeText(encodeSnapshotItem(item))
        tempDataFile.writeBytes(bytes)
        if (!hasPosterBytes) {
            posterFile.delete()
            tempPosterFile.delete()
        } else {
            tempPosterFile.writeBytes(posterBytes)
        }
        tempMetadataFile.renameTo(metadataFile)
        tempDataFile.renameTo(dataFile)
        if (hasPosterBytes) {
            tempPosterFile.renameTo(posterFile)
        }
    }

    private fun clearSnapshotDirectory(directory: File) {
        repeat(DOUYIN_PLAYBACK_SNAPSHOT_COUNT) { index ->
            File(directory, snapshotMetadataName(index)).delete()
            File(directory, snapshotDataName(index)).delete()
            File(directory, snapshotPosterName(index)).delete()
            File(directory, "${snapshotMetadataName(index)}.tmp").delete()
            File(directory, "${snapshotDataName(index)}.tmp").delete()
            File(directory, "${snapshotPosterName(index)}.tmp").delete()
        }
    }

    private fun snapshotMetadataName(index: Int): String = "snapshot_$index.json"

    private fun snapshotDataName(index: Int): String = "snapshot_$index.bin"

    private fun snapshotPosterName(index: Int): String = "snapshot_$index.jpg"

    private fun encodeSnapshotItem(item: DouyinStreamItem): String {
        return Properties().apply {
            setProperty("awemeId", item.awemeId)
            setProperty("playUrl", item.playUrl)
            setProperty("coverUrl", item.coverUrl.orEmpty())
            setProperty("title", item.title.orEmpty())
            setProperty("author", item.author.orEmpty())
            setProperty("likeCount", item.likeCount.toString())
            setProperty("playUrlResolvedAtMs", item.playUrlResolvedAtMs.toString())
            setProperty("durationMs", item.durationMs.toString())
            setProperty("sourceOrigin", item.sourceOrigin.name)
            encodeSnapshotVariants(item.variants)
        }.let { properties ->
            StringWriter().use { writer ->
                properties.store(writer, null)
                writer.toString()
            }
        }
    }

    private fun parseSnapshotItem(raw: String?): DouyinStreamItem? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val properties = Properties().apply {
                StringReader(raw).use(::load)
            }
            val awemeId = properties.getProperty("awemeId").orEmpty().trim()
            val playUrl = properties.getProperty("playUrl").orEmpty().trim()
            if (awemeId.isEmpty() || playUrl.isEmpty()) {
                null
            } else {
                DouyinStreamItem(
                    awemeId = awemeId,
                    playUrl = playUrl,
                    coverUrl = properties.getProperty("coverUrl").takeIf { !it.isNullOrBlank() },
                    title = properties.getProperty("title").takeIf { !it.isNullOrBlank() },
                    author = properties.getProperty("author").takeIf { !it.isNullOrBlank() },
                    likeCount = properties.getProperty("likeCount")?.toLongOrNull() ?: 0L,
                    playUrlResolvedAtMs = properties.getProperty("playUrlResolvedAtMs")?.toLongOrNull() ?: 0L,
                    sourceOrigin = DouyinSourceOrigin.fromPersistedValue(
                        properties.getProperty("sourceOrigin")?.takeIf { it.isNotBlank() }
                    ),
                    durationMs = (properties.getProperty("durationMs")?.toLongOrNull() ?: 0L).coerceAtLeast(0L),
                    variants = properties.decodeSnapshotVariants()
                )
            }
        }.getOrNull()
    }

    private fun Properties.encodeSnapshotVariants(
        variants: List<DouyinVideoVariant>
    ) {
        setProperty("variants.count", variants.size.toString())
        variants.forEachIndexed { index, variant ->
            val prefix = "variants.$index"
            setProperty("$prefix.playUrl", variant.playUrl)
            setProperty("$prefix.codec", variant.codec.name)
            setProperty("$prefix.bitrate", variant.bitrate.toString())
            setProperty("$prefix.width", variant.width.toString())
            setProperty("$prefix.height", variant.height.toString())
            setProperty("$prefix.definition", variant.definition.orEmpty())
            setProperty("$prefix.quality", variant.quality.orEmpty())
            setProperty("$prefix.gearName", variant.gearName.orEmpty())
        }
    }

    private fun Properties.decodeSnapshotVariants(): List<DouyinVideoVariant> {
        val count = getProperty("variants.count")?.toIntOrNull()
        if (count == null) {
            return decodeDouyinVariants(getProperty("variants"))
        }
        return buildList(count.coerceAtLeast(0)) {
            repeat(count.coerceAtLeast(0)) { index ->
                val prefix = "variants.$index"
                val playUrl = getProperty("$prefix.playUrl").orEmpty().trim()
                if (playUrl.isEmpty()) return@repeat
                add(
                    DouyinVideoVariant(
                        playUrl = playUrl,
                        codec = DouyinVideoCodec.entries.firstOrNull {
                            it.name == getProperty("$prefix.codec").orEmpty().trim()
                        } ?: DouyinVideoCodec.UNKNOWN,
                        bitrate = getProperty("$prefix.bitrate")?.toLongOrNull() ?: 0L,
                        width = getProperty("$prefix.width")?.toIntOrNull() ?: 0,
                        height = getProperty("$prefix.height")?.toIntOrNull() ?: 0,
                        definition = getProperty("$prefix.definition")?.takeIf { it.isNotBlank() },
                        quality = getProperty("$prefix.quality")?.takeIf { it.isNotBlank() },
                        gearName = getProperty("$prefix.gearName")?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    companion object {
        private const val TAG = "DouyinPreviewCache"
    }
}

private fun DouyinPlaybackPreviewWarmTarget.key(): DouyinPlaybackPreviewKey {
    return DouyinPlaybackPreviewKey(
        awemeId = awemeId,
        playUrlResolvedAtMs = playUrlResolvedAtMs
    )
}

internal fun estimatePreviewBytes(
    contentLength: Long?,
    durationMs: Long
): Int {
    val boundedLength = contentLength?.coerceAtMost(Int.MAX_VALUE.toLong())
    if (boundedLength != null && boundedLength > 0L && durationMs > 0L) {
        val targetDurationMs = min(durationMs, PREVIEW_DURATION_MS)
        val estimated = max(
            MIN_ENTRY_BYTES.toLong(),
            boundedLength * targetDurationMs / durationMs
        )
        return estimated.coerceAtMost(min(boundedLength, MAX_ENTRY_BYTES.toLong())).toInt()
    }
    if (durationMs > 0L) {
        val estimated = max(
            MIN_ENTRY_BYTES.toLong(),
            DEFAULT_ESTIMATED_BYTES_PER_SECOND * min(durationMs, PREVIEW_DURATION_MS) / 1_000L
        )
        return estimated.coerceAtMost(MAX_ENTRY_BYTES.toLong()).toInt()
    }
    return DEFAULT_UNKNOWN_DURATION_BYTES.coerceAtMost(MAX_ENTRY_BYTES)
}

internal fun estimatePrefetchBytes(
    contentLength: Long?,
    durationMs: Long,
    prefetchOrder: Int = 0
): Int {
    val cappedDuration = min(durationMs.coerceAtLeast(0L), PREFETCH_DURATION_MS)
    val base = estimatePreviewBytes(
        contentLength = contentLength,
        durationMs = cappedDuration
    )
    val boundedBase = base.coerceAtMost(MAX_PREFETCH_ENTRY_BYTES)
    val normalizedOrder = prefetchOrder.coerceAtLeast(0)
    val targetBytes = when (normalizedOrder) {
        0 -> boundedBase
        1 -> boundedBase * 3 / 4
        2, 3 -> boundedBase / 2
        else -> boundedBase / 4
    }
    return max(MIN_ENTRY_BYTES, targetBytes)
}

internal fun estimateDouyinPrefetchBandwidthBytesPerSecond(
    foregroundBitrateBitsPerSecond: Long?,
    totalBandwidthBytesPerSecond: Long?
): Long {
    val totalBytesPerSecond = totalBandwidthBytesPerSecond?.coerceAtLeast(0L)
    val foregroundBitrate = foregroundBitrateBitsPerSecond?.takeIf { it > 0L }
        ?: return totalBytesPerSecond ?: Long.MAX_VALUE
    val knownTotalBytesPerSecond = totalBytesPerSecond?.takeIf { it > 0L } ?: return 0L
    val foregroundReserveBytesPerSecond = (
        foregroundBitrate * FOREGROUND_BANDWIDTH_RESERVE_NUMERATOR +
            FOREGROUND_BANDWIDTH_RESERVE_DENOMINATOR - 1L
        ) / FOREGROUND_BANDWIDTH_RESERVE_DENOMINATOR
    return (knownTotalBytesPerSecond - foregroundReserveBytesPerSecond).coerceAtLeast(0L)
}

private fun formatBandwidthForLog(bytesPerSecond: Long?): String {
    return when (bytesPerSecond) {
        null -> "unknown"
        Long.MAX_VALUE -> "unlimited"
        else -> bytesPerSecond.toString()
    }
}

private class DouyinPlaybackPreviewDataSource(
    private val manager: DouyinPlaybackPreviewManager
) : BaseDataSource(false) {
    private var currentUriString: String? = null
    private var currentUri: Uri? = null
    private var opened = false
    private var responseHeaders: Map<String, List<String>> = emptyMap()
    private var readPosition = 0L
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    @Volatile
    private var closed = true

    override fun open(dataSpec: DataSpec): Long {
        if (opened || currentUriString != null) {
            AppLogger.w(
                DATA_SOURCE_TAG,
                "dirty reopen uri=$currentUriString -> ${dataSpec.uri}"
            )
            runCatching { close() }
                .onFailure { error ->
                    AppLogger.w(DATA_SOURCE_TAG, "failed to reset preview data source before reopen", error)
                }
        }
        transferInitializing(dataSpec)
        currentUri = dataSpec.uri
        currentUriString = dataSpec.uri.toString()
        responseHeaders = emptyMap()
        readPosition = dataSpec.position.coerceAtLeast(0L)
        bytesRemaining = dataSpec.length
        closed = false
        opened = true
        transferStarted(dataSpec)
        AppLogger.d(
            DATA_SOURCE_TAG,
            "RAM_ONLY_OPEN uri=${dataSpec.uri} position=$readPosition length=${dataSpec.length}"
        )
        return dataSpec.length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (!opened) return C.RESULT_END_OF_INPUT
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val uriString = currentUriString ?: return C.RESULT_END_OF_INPUT
        val readLength = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            min(length.toLong(), bytesRemaining).toInt()
        }
        val read = manager.readCacheBytesBlocking(
            mediaUri = uriString,
            absolutePosition = readPosition,
            buffer = buffer,
            offset = offset,
            length = readLength,
            shouldContinue = { !closed }
        )
        if (read == C.RESULT_END_OF_INPUT) return C.RESULT_END_OF_INPUT
        readPosition += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining = (bytesRemaining - read).coerceAtLeast(0L)
        }
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders

    override fun close() {
        val wasOpened = opened
        closed = true
        opened = false
        currentUriString = null
        currentUri = null
        responseHeaders = emptyMap()
        readPosition = 0L
        bytesRemaining = C.LENGTH_UNSET.toLong()
        if (wasOpened) {
            transferEnded()
        }
    }

    companion object {
        private const val DATA_SOURCE_TAG = "DouyinPreviewDataSource"
    }
}

private fun encodePosterBitmap(bitmap: Bitmap): ByteArray? {
    return ByteArrayOutputStream().use { output ->
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
            null
        } else {
            output.toByteArray().takeIf { it.isNotEmpty() }
        }
    }
}

private fun String?.readTextSafely(): String? {
    return this
}

private fun File.readTextSafely(): String? {
    return runCatching {
        if (!isFile) null else readText()
    }.getOrNull()
}

private fun File.readBytesSafely(): ByteArray {
    return runCatching {
        if (!isFile) ByteArray(0) else readBytes()
    }.getOrDefault(ByteArray(0))
}
