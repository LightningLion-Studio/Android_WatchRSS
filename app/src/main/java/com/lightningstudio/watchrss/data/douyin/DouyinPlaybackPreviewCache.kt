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
import kotlin.math.max
import kotlin.math.min
import okhttp3.OkHttpClient
import okhttp3.Request

internal const val DOUYIN_PLAYBACK_PREVIEW_ENTRY_LIMIT = 5

private const val DOUYIN_PLAYBACK_SNAPSHOT_COUNT = 2
private const val DOUYIN_PLAYBACK_PREFETCH_COUNT = 3
private const val DOUYIN_PLAYBACK_BACKWARD_REGISTRATION_COUNT = 1
private const val PREVIEW_DURATION_MS = 30_000L
private const val PREFETCH_DURATION_MS = 8_000L
private const val MIN_ENTRY_BYTES = 512 * 1024
private const val DEFAULT_UNKNOWN_DURATION_BYTES = 8 * 1024 * 1024
private const val MAX_ENTRY_BYTES = 12 * 1024 * 1024
private const val MAX_PREFETCH_ENTRY_BYTES = 2 * 1024 * 1024
private const val MAX_TOTAL_PREVIEW_BYTES = 48L * 1024L * 1024L
private const val MAX_TOTAL_POSTER_BYTES = 4L * 1024L * 1024L
private const val DEFAULT_ESTIMATED_BYTES_PER_SECOND = 256 * 1024L

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
    val captureEnabled: Boolean
)

private data class DouyinPlaybackPreviewEntry(
    val key: DouyinPlaybackPreviewKey,
    val mediaUri: String,
    val bytes: ByteArray,
    val budgetBytes: Int
)

internal data class DouyinPlaybackPreviewReadOutcome(
    val bytes: ByteArray,
    val error: Throwable?
)

internal fun readDouyinPreviewBytes(
    input: InputStream,
    budgetBytes: Int
): DouyinPlaybackPreviewReadOutcome {
    if (budgetBytes <= 0) {
        return DouyinPlaybackPreviewReadOutcome(bytes = ByteArray(0), error = null)
    }
    val buffer = ByteArray(budgetBytes)
    var offset = 0
    try {
        while (offset < budgetBytes) {
            val read = input.read(buffer, offset, budgetBytes - offset)
            if (read <= 0) break
            offset += read
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

    fun configure(context: Context) {
        manager.configure(File(context.applicationContext.cacheDir, SNAPSHOT_DIR_NAME))
    }

    fun buildPlaybackDataSourceFactory(
        upstreamFactory: DataSource.Factory
    ): DataSource.Factory {
        return DataSource.Factory {
            DouyinPlaybackPreviewDataSource(
                manager = manager,
                upstream = upstreamFactory.createDataSource()
            )
        }
    }

    fun updatePlaybackWindow(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?,
        headers: Map<String, String>,
        reason: String
    ) {
        manager.updatePlaybackWindow(items, anchorIndex, headers, reason)
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
        expectedGeneration: Long
    ) {
        manager.persistExitSnapshotsIfCurrent(items, anchorIndex, expectedGeneration)
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

    fun clearSession() {
        manager.clearSession()
    }

    fun captureSessionGeneration(): Long {
        return manager.captureSessionGeneration()
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

    private const val SNAPSHOT_DIR_NAME = "douyin_preview_snapshots"
}

private class DouyinPlaybackPreviewManager {
    private val lock = Any()
    private val entries = LinkedHashMap<DouyinPlaybackPreviewKey, DouyinPlaybackPreviewEntry>(16, 0.75f, true)
    private val posterEntries = LinkedHashMap<DouyinPlaybackPreviewKey, ByteArray>(16, 0.75f, true)
    private val registrationsByUri = linkedMapOf<String, DouyinPlaybackPreviewRegistration>()
    private val keyByUri = linkedMapOf<String, DouyinPlaybackPreviewKey>()
    private val prefetchJobs = linkedMapOf<DouyinPlaybackPreviewKey, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefetchDispatcher =
        Dispatchers.IO.limitedParallelism(DOUYIN_PLAYBACK_PREFETCH_COUNT)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private var totalBytes = 0L
    private var totalPosterBytes = 0L
    private var snapshotDir: File? = null
    private var sessionGeneration = 0L

    fun configure(snapshotDir: File?) {
        synchronized(lock) {
            this.snapshotDir = snapshotDir?.apply { mkdirs() }
        }
    }

    fun updatePlaybackWindow(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?,
        headers: Map<String, String>,
        reason: String
    ) {
        bumpSessionGeneration()
        val targets = buildPlaybackTargets(items, anchorIndex)
        updateRegistrations(targets)
        schedulePrefetch(
            targets = targets.drop(1).take(DOUYIN_PLAYBACK_PREFETCH_COUNT),
            headers = headers,
            reason = reason
        )
        AppLogger.d(
            TAG,
            "update playback window reason=$reason anchorIndex=$anchorIndex ids=${
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

    fun persistExitSnapshots(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?
    ) {
        val directory = synchronized(lock) { snapshotDir } ?: return clearSession()
        directory.mkdirs()
        clearSnapshotDirectory(directory)
        val nextItems = resolveExitSnapshotItems(items, anchorIndex)
        nextItems.forEachIndexed { index, item ->
            val bytes = peekBytes(item.playUrl)
                ?.takeIf { it.isNotEmpty() }
                ?: return@forEachIndexed
            writeSnapshot(
                directory = directory,
                slotIndex = index,
                item = item,
                bytes = bytes,
                posterBytes = readPosterBytes(item)
            )
            AppLogger.d(
                TAG,
                "SNAPSHOT_WRITE awemeId=${item.awemeId} bytes=${bytes.size} slot=$index"
            )
        }
        clearSession()
    }

    fun persistExitSnapshotsIfCurrent(
        items: List<DouyinStreamItem>,
        anchorIndex: Int?,
        expectedGeneration: Long
    ) {
        if (!isCurrentGeneration(expectedGeneration)) return
        val directory = synchronized(lock) { snapshotDir }
        if (directory == null) {
            if (isCurrentGeneration(expectedGeneration)) {
                clearSession()
            }
            return
        }
        if (!isCurrentGeneration(expectedGeneration)) return
        directory.mkdirs()
        clearSnapshotDirectory(directory)
        if (!isCurrentGeneration(expectedGeneration)) return
        val nextItems = resolveExitSnapshotItems(items, anchorIndex)
        nextItems.forEachIndexed { index, item ->
            if (!isCurrentGeneration(expectedGeneration)) return
            val bytes = peekBytes(item.playUrl)
                ?.takeIf { it.isNotEmpty() }
                ?: return@forEachIndexed
            writeSnapshot(
                directory = directory,
                slotIndex = index,
                item = item,
                bytes = bytes,
                posterBytes = readPosterBytes(item)
            )
            AppLogger.d(
                TAG,
                "SNAPSHOT_WRITE awemeId=${item.awemeId} bytes=${bytes.size} slot=$index"
            )
        }
        if (isCurrentGeneration(expectedGeneration)) {
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
        val jobs = synchronized(lock) {
            sessionGeneration += 1
            val runningJobs = prefetchJobs.values.toList()
            prefetchJobs.clear()
            entries.clear()
            posterEntries.clear()
            registrationsByUri.clear()
            keyByUri.clear()
            totalBytes = 0L
            totalPosterBytes = 0L
            runningJobs
        }
        jobs.forEach { job ->
            if (job.isActive) {
                job.cancel()
            }
        }
    }

    fun clearAll() {
        clearSession()
        synchronized(lock) { snapshotDir }?.let(::clearSnapshotDirectory)
    }

    fun captureSessionGeneration(): Long {
        return synchronized(lock) { sessionGeneration }
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

    fun peekBytes(mediaUri: String?): ByteArray? {
        val normalizedUri = mediaUri?.trim().orEmpty()
        if (normalizedUri.isEmpty()) return null
        return synchronized(lock) {
            val key = keyByUri[normalizedUri] ?: return@synchronized null
            entries[key]?.bytes
        }
    }

    private fun bumpSessionGeneration() {
        synchronized(lock) {
            sessionGeneration += 1
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
                DouyinPlaybackPreviewRegistration(
                    key = target.key(),
                    mediaUri = target.mediaUri,
                    durationMs = target.durationMs,
                    budgetBytes = estimatePreviewBytes(
                        contentLength = null,
                        durationMs = target.durationMs
                    ),
                    captureEnabled = true
                )
            }
        )
        synchronized(lock) {
            registrationsByUri.clear()
            registrationsByUri.putAll(registrations)
            val allowedKeys = registrations.values.mapTo(linkedSetOf()) { it.key }
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next().value
                if (!allowedKeys.contains(entry.key)) {
                    totalBytes -= entry.bytes.size.toLong()
                    keyByUri.remove(entry.mediaUri)
                    iterator.remove()
                }
            }
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
        val normalizedTargets = targets.filter { it.mediaUri.startsWith("http", ignoreCase = true) }
        val targetKeys = normalizedTargets.mapTo(linkedSetOf()) { it.key() }
        val jobsToCancel = synchronized(lock) {
            val removed = prefetchJobs
                .filterKeys { !targetKeys.contains(it) }
                .values
                .toList()
            prefetchJobs.entries.removeAll { !targetKeys.contains(it.key) }
            removed
        }
        jobsToCancel.forEach { job ->
            if (job.isActive) {
                scope.launch {
                    runCatching { job.cancelAndJoin() }
                }
            }
        }
        normalizedTargets.forEachIndexed { index, target ->
            val key = target.key()
            val existingBytes = synchronized(lock) { entries[key]?.bytes?.size ?: 0 }
            val requiredBytes = estimatePrefetchBytes(
                contentLength = null,
                durationMs = target.durationMs,
                prefetchOrder = index
            )
            if (existingBytes >= requiredBytes) return@forEachIndexed
            val shouldLaunch = synchronized(lock) {
                val running = prefetchJobs[key]
                if (running?.isActive == true) {
                    false
                } else {
                    prefetchJobs[key] = scope.launch(prefetchDispatcher) {
                        try {
                            prefetchTarget(
                                target = target,
                                headers = headers,
                                reason = reason,
                                budgetBytes = requiredBytes
                            )
                        } finally {
                            synchronized(lock) {
                                prefetchJobs.remove(key)
                            }
                        }
                    }
                    true
                }
            }
            if (shouldLaunch) {
                AppLogger.d(
                    TAG,
                    "queue prefetch awemeId=${target.awemeId} bytes=$requiredBytes order=$index reason=$reason"
                )
            }
        }
    }

    private suspend fun prefetchTarget(
        target: DouyinPlaybackPreviewWarmTarget,
        headers: Map<String, String>,
        reason: String,
        budgetBytes: Int
    ) {
        if (budgetBytes <= 0) return
        val requestBuilder = Request.Builder()
            .url(target.mediaUri)
            .header("Range", "bytes=0-${budgetBytes - 1}")
        headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                requestBuilder.header(key, value)
            }
        }
        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body ?: return
                val outcome = body.byteStream().use { input ->
                    readDouyinPreviewBytes(input, budgetBytes)
                }
                val bytes = outcome.bytes
                if (bytes.isEmpty()) return
                putPreviewBytes(target, bytes)
                when (val error = outcome.error) {
                    null -> {
                        AppLogger.d(
                            TAG,
                            "RAM_PREFETCH awemeId=${target.awemeId} bytes=${bytes.size} reason=$reason"
                        )
                    }
                    is CancellationException -> {
                        AppLogger.d(
                            TAG,
                            "RAM_PREFETCH_PARTIAL awemeId=${target.awemeId} bytes=${bytes.size} reason=$reason"
                        )
                        throw error
                    }
                    else -> {
                        AppLogger.w(
                            TAG,
                            "prefetch failed awemeId=${target.awemeId} bytes=${bytes.size}",
                            error
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AppLogger.w(TAG, "prefetch failed awemeId=${target.awemeId}", error)
        }
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
        budgetBytes: Int
    ) {
        val existing = entries[key]
        if (existing != null && existing.bytes.size >= bytes.size) {
            keyByUri[existing.mediaUri] = key
            return
        }
        existing?.let { totalBytes -= it.bytes.size.toLong() }
        val cappedBytes = if (bytes.size > budgetBytes) bytes.copyOf(budgetBytes) else bytes
        entries[key] = DouyinPlaybackPreviewEntry(
            key = key,
            mediaUri = mediaUri,
            bytes = cappedBytes,
            budgetBytes = max(budgetBytes, cappedBytes.size)
        )
        keyByUri[mediaUri] = key
        totalBytes += cappedBytes.size.toLong()
        trimToBudgetLocked()
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
        val iterator = entries.entries.iterator()
        while (
            entries.size > DOUYIN_PLAYBACK_PREVIEW_ENTRY_LIMIT ||
            totalBytes > MAX_TOTAL_PREVIEW_BYTES
        ) {
            if (!iterator.hasNext()) break
            val entry = iterator.next().value
            totalBytes -= entry.bytes.size.toLong()
            keyByUri.remove(entry.mediaUri)
            iterator.remove()
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
            items.getOrNull(center + 1)?.let(::add)
            items.getOrNull(center + 2)?.let(::add)
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
    val divisor = when (prefetchOrder.coerceAtLeast(0)) {
        0 -> 1
        1 -> 2
        else -> 4
    }
    return max(MIN_ENTRY_BYTES, boundedBase / divisor)
}

private class DouyinPlaybackPreviewDataSource(
    private val manager: DouyinPlaybackPreviewManager,
    private val upstream: DataSource
) : BaseDataSource(false) {
    private var currentUriString: String? = null
    private var currentUri: Uri? = null
    private var currentPreviewBytes: ByteArray? = null
    private var previewReadPosition = 0
    private var previewBytesRemaining = 0
    private var upstreamOpened = false
    private var pendingUpstreamDataSpec: DataSpec? = null
    private var opened = false
    private var responseHeaders: Map<String, List<String>> = emptyMap()
    private var upstreamReadPosition = 0L
    private var currentRegistration: DouyinPlaybackPreviewRegistration? = null

    override fun open(dataSpec: DataSpec): Long {
        if (opened || upstreamOpened || currentUriString != null || currentPreviewBytes != null) {
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
        val previewBytes = manager.peekBytes(currentUriString)
        val requestedPosition = dataSpec.position.coerceAtLeast(0L)
        currentPreviewBytes = previewBytes
        currentRegistration = manager.registrationFor(currentUriString)
        val previewSize = previewBytes?.size ?: 0
        val previewAvailable = (previewSize.toLong() - requestedPosition).coerceAtLeast(0L)
        val requestedLength = dataSpec.length
        previewBytesRemaining = if (previewAvailable > 0L) {
            val length = if (requestedLength == C.LENGTH_UNSET.toLong()) {
                previewAvailable
            } else {
                min(requestedLength, previewAvailable)
            }
            length.toInt()
        } else {
            0
        }
        previewReadPosition = requestedPosition.toInt()
        if (previewBytesRemaining > 0) {
            AppLogger.d(
                DATA_SOURCE_TAG,
                "RAM_HIT uri=${dataSpec.uri} bytes=$previewBytesRemaining position=$requestedPosition"
            )
        }

        val previewServedLength = previewBytesRemaining.toLong()
        val needsUpstream = requestedLength == C.LENGTH_UNSET.toLong() || previewServedLength < requestedLength
        var resolvedLength = previewServedLength
        if (needsUpstream) {
            val upstreamPosition = if (previewServedLength > 0L) {
                previewSize.toLong()
            } else {
                requestedPosition
            }
            val upstreamLength = if (requestedLength == C.LENGTH_UNSET.toLong()) {
                C.LENGTH_UNSET.toLong()
            } else {
                requestedLength - previewServedLength
            }
            val upstreamDataSpec = dataSpec.buildUpon()
                .setPosition(upstreamPosition)
                .setLength(upstreamLength)
                .build()
            pendingUpstreamDataSpec = upstreamDataSpec
            resolvedLength = when {
                requestedLength != C.LENGTH_UNSET.toLong() && previewServedLength >= requestedLength ->
                    requestedLength
                previewServedLength > 0L -> C.LENGTH_UNSET.toLong()
                else -> C.LENGTH_UNSET.toLong()
            }
            if (previewServedLength <= 0L) {
                openPendingUpstream(previewSize.toLong(), requestedPosition, requestedLength)
                resolvedLength = when {
                    requestedLength != C.LENGTH_UNSET.toLong() -> requestedLength
                    else -> C.LENGTH_UNSET.toLong()
                }
            }
        }

        opened = true
        transferStarted(dataSpec)
        return resolvedLength
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        var totalRead = 0
        val previewBytes = currentPreviewBytes
        if (previewBytes != null && previewBytesRemaining > 0) {
            val bytesToCopy = min(length, previewBytesRemaining)
            System.arraycopy(previewBytes, previewReadPosition, buffer, offset, bytesToCopy)
            previewReadPosition += bytesToCopy
            previewBytesRemaining -= bytesToCopy
            totalRead += bytesToCopy
            bytesTransferred(bytesToCopy)
            if (totalRead == length) {
                return totalRead
            }
        }

        if (!upstreamOpened) {
            if (previewBytesRemaining <= 0 && pendingUpstreamDataSpec != null) {
                openPendingUpstream(
                    previewSize = currentPreviewBytes?.size?.toLong() ?: 0L,
                    requestedPosition = upstreamReadPosition,
                    requestedLength = C.LENGTH_UNSET.toLong()
                )
            }
        }

        if (!upstreamOpened) {
            return if (totalRead > 0) totalRead else C.RESULT_END_OF_INPUT
        }

        val upstreamRead = upstream.read(buffer, offset + totalRead, length - totalRead)
        if (upstreamRead == C.RESULT_END_OF_INPUT) {
            return if (totalRead > 0) totalRead else C.RESULT_END_OF_INPUT
        }
        manager.captureFromUpstream(
            mediaUri = currentUriString,
            absolutePosition = upstreamReadPosition,
            buffer = buffer,
            offset = offset + totalRead,
            length = upstreamRead
        )
        upstreamReadPosition += upstreamRead
        bytesTransferred(upstreamRead)
        return totalRead + upstreamRead
    }

    override fun getUri(): Uri? = currentUri ?: upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders

    override fun close() {
        val wasOpened = opened
        opened = false
        currentUriString = null
        currentUri = null
        currentPreviewBytes = null
        previewReadPosition = 0
        previewBytesRemaining = 0
        pendingUpstreamDataSpec = null
        currentRegistration = null
        responseHeaders = emptyMap()
        upstreamReadPosition = 0L
        if (upstreamOpened) {
            upstreamOpened = false
            runCatching { upstream.close() }
                .getOrElse { throw IOException("failed to close upstream", it) }
        }
        if (wasOpened) {
            transferEnded()
        }
    }

    private fun cleanupAfterOpenFailure() {
        currentUriString = null
        currentUri = null
        currentPreviewBytes = null
        previewReadPosition = 0
        previewBytesRemaining = 0
        pendingUpstreamDataSpec = null
        currentRegistration = null
        responseHeaders = emptyMap()
        upstreamReadPosition = 0L
        upstreamOpened = false
        runCatching { upstream.close() }
    }

    private fun openPendingUpstream(
        previewSize: Long,
        requestedPosition: Long,
        requestedLength: Long
    ) {
        val upstreamDataSpec = pendingUpstreamDataSpec ?: return
        try {
            val upstreamResolvedLength = upstream.open(upstreamDataSpec)
            upstreamOpened = true
            upstreamReadPosition = upstreamDataSpec.position
            responseHeaders = upstream.responseHeaders
            pendingUpstreamDataSpec = null
            val totalContentLength = when {
                upstreamResolvedLength == C.LENGTH_UNSET.toLong() -> null
                previewSize > 0L -> previewSize + upstreamResolvedLength
                requestedPosition == 0L -> upstreamResolvedLength
                else -> null
            }
            totalContentLength?.let { manager.adjustBudgetFromContentLength(currentUriString, it) }
            if (requestedLength != C.LENGTH_UNSET.toLong()) {
                AppLogger.d(
                    DATA_SOURCE_TAG,
                    "deferred upstream open uri=$currentUriString position=${upstreamDataSpec.position} length=${upstreamDataSpec.length}"
                )
            }
        } catch (error: Throwable) {
            cleanupAfterOpenFailure()
            throw error
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
