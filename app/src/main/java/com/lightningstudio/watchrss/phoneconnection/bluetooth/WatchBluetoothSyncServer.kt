package com.lightningstudio.watchrss.phoneconnection.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.data.rss.SyncedArticleBodyRequest
import com.lightningstudio.watchrss.data.rss.SyncedChunkedArticle
import com.lightningstudio.watchrss.data.rss.SyncedSavedArticleMergeStats
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.phoneconnection.SavedItemsSyncPayload
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

data class BluetoothSyncResult(
    val remoteName: String,
    val remoteAddress: String,
    val request: JSONObject,
    val response: JSONObject
)

private data class PendingLibrarySyncSuccess(
    val remoteDeviceId: String,
    val localSeqToInclusive: Long,
    val remoteSeqToInclusive: Long,
    val remoteProtocolVersion: Int,
    val fullSnapshot: Boolean,
    val localArticleCount: Int,
    val remoteArticleCount: Int
)

private data class LibraryManifestExchangeResult(
    val response: JSONObject,
    val pendingSyncSuccess: PendingLibrarySyncSuccess?
)

private data class ArticleRequestBatchStats(
    var frameCount: Int = 0,
    var totalBytes: Int = 0,
    var totalWireBytes: Long = 0L,
    var maxFrameBytes: Int = 0,
    var articleCount: Int = 0
) {
    fun add(frame: JSONObject) {
        frameCount += 1
        val bytes = runCatching { BluetoothSyncProtocol.encodedSize(frame) }.getOrDefault(0)
        totalBytes += bytes
        totalWireBytes += runCatching { BluetoothSyncProtocol.wireSize(frame) }.getOrDefault(0L)
        maxFrameBytes = maxOf(maxFrameBytes, bytes)
        articleCount += frame.optJSONArray("articles")?.length() ?: 0
    }

    fun fields(prefix: String): Map<String, Any?> {
        return mapOf(
            "${prefix}FrameCount" to frameCount,
            "${prefix}TotalBytes" to totalBytes,
            "${prefix}TotalWireBytes" to totalWireBytes,
            "${prefix}MaxFrameBytes" to maxFrameBytes,
            "${prefix}ArticleCount" to articleCount
        )
    }
}

private interface WatchSyncTransport : Closeable {
    val inputStream: InputStream
    val outputStream: OutputStream
}

private class BluetoothWatchSyncTransport(
    private val socket: BluetoothSocket
) : WatchSyncTransport {
    override val inputStream: InputStream
        get() = socket.inputStream
    override val outputStream: OutputStream
        get() = socket.outputStream
    override fun close() = socket.close()
}

private class StreamWatchSyncTransport(
    override val inputStream: InputStream,
    override val outputStream: OutputStream,
    private val closeTransport: () -> Unit
) : WatchSyncTransport {
    override fun close() = closeTransport()
}

internal suspend fun <T> withTransportReadTimeout(
    timeoutMs: Long,
    closeTransport: () -> Unit,
    read: () -> T
): T = coroutineScope {
    val timeoutJob = launch(Dispatchers.IO) {
        delay(timeoutMs)
        closeTransport()
    }
    try {
        withContext(Dispatchers.IO) { read() }
    } finally {
        timeoutJob.cancel()
    }
}

private data class ChunkedArticleRequestFrames(
    val bodyRequests: List<SyncedArticleBodyRequest>,
    val stats: ArticleRequestBatchStats,
    val receivedArticles: Int,
    val mergeStats: SyncedSavedArticleMergeStats
)

private class ReaderPreviewStreamStats {
    private var windowStartedAt = SystemClock.elapsedRealtime()
    private var frames = 0
    private var updateFrames = 0
    private var acknowledgements = 0
    private var handleElapsedMs = 0L
    private var maxHandleElapsedMs = 0L
    private var readElapsedMs = 0L

    fun recordFrame(phase: String, handleMs: Long, readMs: Long, acknowledged: Boolean) {
        frames += 1
        if (phase == ReaderPresetPreviewPayload.PHASE_UPDATE) updateFrames += 1
        if (acknowledged) acknowledgements += 1
        handleElapsedMs += handleMs
        maxHandleElapsedMs = maxOf(maxHandleElapsedMs, handleMs)
        readElapsedMs += readMs
    }

    fun snapshotIfDue(): Map<String, Any?>? {
        val now = SystemClock.elapsedRealtime()
        val durationMs = now - windowStartedAt
        if (durationMs < REPORT_INTERVAL_MS) return null
        val snapshot = mapOf(
            "durationMs" to durationMs,
            "frames" to frames,
            "framesPerSecond" to rate(frames, durationMs),
            "updateFrames" to updateFrames,
            "acknowledgements" to acknowledgements,
            "averageHandleMs" to average(handleElapsedMs, frames),
            "maxHandleMs" to maxHandleElapsedMs,
            "averageReadMs" to average(readElapsedMs, frames)
        )
        windowStartedAt = now
        frames = 0
        updateFrames = 0
        acknowledgements = 0
        handleElapsedMs = 0L
        maxHandleElapsedMs = 0L
        readElapsedMs = 0L
        return snapshot
    }

    private fun rate(count: Int, durationMs: Long): Double =
        if (durationMs <= 0L) 0.0 else count * 1_000.0 / durationMs

    private fun average(total: Long, count: Int): Double =
        if (count <= 0) 0.0 else total.toDouble() / count

    private companion object {
        const val REPORT_INTERVAL_MS = 1_000L
    }
}

class WatchBluetoothSyncServer(
    private val context: Context,
    private val expectedAbility: PhoneConnectionAbility? = null,
    private val allowedActions: Set<String>? = null,
    private val onClientAccepted: (() -> Unit)? = null,
    private val onRequestReceived: (() -> Unit)? = null,
    private val onActionCompleted: ((BluetoothSyncResult) -> Unit)? = null,
    private val responseAckTimeoutMs: Long = RESPONSE_ACK_TIMEOUT_MS
) {
    @SuppressLint("MissingPermission")
    suspend fun acceptOnce(timeoutMs: Long = DEFAULT_TIMEOUT_MS): BluetoothSyncResult {
        val sessionId = WatchBluetoothDebugLog.newSessionId("watchSync")
        val totalStartedAt = SystemClock.elapsedRealtime()
        WatchBluetoothDebugLog.event(
            sessionId = sessionId,
            event = "server.acceptOnce.start",
            fields = mapOf(
                "timeoutMs" to timeoutMs,
                "uuid" to BluetoothSyncProtocol.SERVICE_UUID,
                "expectedAbility" to expectedAbility?.name.orEmpty(),
                "allowedActions" to allowedActions.orEmpty().joinToString("|")
            )
        )
        return runCatching {
            requireBluetoothConnectPermission()
            val adapter = context.getSystemService(BluetoothManager::class.java)
                ?.adapter
                ?: error("此设备没有蓝牙适配器")
            require(adapter.isEnabled) { "蓝牙未开启" }

            val listenStartedAt = SystemClock.elapsedRealtime()
            val serverSocket = adapter.listenUsingRfcommWithServiceRecord(
                BluetoothSyncProtocol.SERVICE_NAME,
                BluetoothSyncProtocol.SERVICE_UUID
            )
            Log.i(TAG, "listening uuid=${BluetoothSyncProtocol.SERVICE_UUID} timeoutMs=$timeoutMs")
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "server.listen.success",
                fields = mapOf("elapsedMs" to elapsedSince(listenStartedAt))
            )
            serverSocket.use { socket ->
                val acceptStartedAt = SystemClock.elapsedRealtime()
                WatchBluetoothDebugLog.event(
                    sessionId = sessionId,
                    event = "server.accept.start",
                    fields = mapOf("timeoutMs" to timeoutMs)
                )
                val bluetoothSocket = socket.acceptWithTimeout(timeoutMs)
                WatchBluetoothDebugLog.event(
                    sessionId = sessionId,
                    event = "server.accept.success",
                    fields = mapOf("elapsedMs" to elapsedSince(acceptStartedAt))
                )
                bluetoothSocket.use { socketClient ->
                    serveTransport(
                        client = BluetoothWatchSyncTransport(socketClient),
                        remoteName = socketClient.remoteDevice?.name.orEmpty(),
                        remoteAddress = socketClient.remoteDevice?.address.orEmpty(),
                        sessionId = sessionId,
                        totalStartedAt = totalStartedAt,
                        initialRequestTimeoutMs = INITIAL_REQUEST_READ_TIMEOUT_MS
                    )
                }
            }
        }.onFailure { throwable ->
            WatchBluetoothDebugLog.error(
                sessionId = sessionId,
                event = "server.acceptOnce.failed",
                fields = mapOf(
                    "elapsedMs" to elapsedSince(totalStartedAt),
                    "errorClass" to throwable::class.java.name,
                    "message" to throwable.message.orEmpty()
                ),
                throwable = throwable
            )
        }.getOrThrow()
    }

    suspend fun serveStreams(
        inputStream: InputStream,
        outputStream: OutputStream,
        remoteName: String,
        remoteAddress: String,
        closeTransport: () -> Unit = {},
        initialRequestTimeoutMs: Long = INITIAL_REQUEST_READ_TIMEOUT_MS
    ): BluetoothSyncResult {
        val sessionId = WatchBluetoothDebugLog.newSessionId("watchIpSync")
        val startedAt = SystemClock.elapsedRealtime()
        return runCatching {
            serveTransport(
                client = StreamWatchSyncTransport(inputStream, outputStream, closeTransport),
                remoteName = remoteName,
                remoteAddress = remoteAddress,
                sessionId = sessionId,
                totalStartedAt = startedAt,
                initialRequestTimeoutMs = initialRequestTimeoutMs
            )
        }.onFailure { throwable ->
            WatchBluetoothDebugLog.error(
                sessionId = sessionId,
                event = "server.ip.failed",
                fields = mapOf(
                    "elapsedMs" to elapsedSince(startedAt),
                    "errorClass" to throwable::class.java.name,
                    "message" to throwable.message.orEmpty()
                ),
                throwable = throwable
            )
        }.getOrThrow()
    }

    private suspend fun serveTransport(
        client: WatchSyncTransport,
        remoteName: String,
        remoteAddress: String,
        sessionId: String,
        totalStartedAt: Long,
        initialRequestTimeoutMs: Long
    ): BluetoothSyncResult {
        Log.i(TAG, "accepted from name=$remoteName address=$remoteAddress")
        WatchBluetoothDebugLog.event(
            sessionId = sessionId,
            event = "server.client.accepted",
            fields = remoteFields(remoteName, remoteAddress)
        )
        onClientAccepted?.invoke()
        var persistentSession = false
        var requestTimeoutMs = initialRequestTimeoutMs
        var completedActions = 0
        while (true) {
            var request = readFrameLoggedWithTimeout(
                client = client,
                sessionId = sessionId,
                label = if (completedActions == 0) "initialRequest" else "sessionRequest",
                timeoutMs = requestTimeoutMs
            )
            onRequestReceived?.invoke()
            val acceptsPersistentSession =
                BluetoothSyncProtocol.requestsPersistentSession(request)
            val persistentSessionAccepted = !persistentSession && acceptsPersistentSession
            if (persistentSessionAccepted) {
                persistentSession = true
                requestTimeoutMs = BluetoothSyncProtocol.PERSISTENT_SESSION_IDLE_TIMEOUT_MS
                WatchBluetoothDebugLog.event(
                    sessionId = sessionId,
                    event = "server.session.accepted",
                    fields = remoteFields(remoteName, remoteAddress)
                )
            }

            BluetoothSyncProtocol.sessionControlPhase(request)?.let { phase ->
                require(persistentSession) { "当前连接不是持久同步会话" }
                val response = BluetoothSyncProtocol.buildSessionControlResponse(
                    version = LibrarySyncPayload.PROTOCOL_VERSION,
                    phase = phase
                )
                writeFrameLogged(client, sessionId, "sessionResponse", response)
                waitForResponseAck(client, sessionId)
                val result = BluetoothSyncResult(remoteName, remoteAddress, request, response)
                WatchBluetoothDebugLog.event(
                    sessionId = sessionId,
                    event = "server.session.complete",
                    fields = mapOf(
                        "phase" to phase,
                        "completedActions" to completedActions,
                        "elapsedMs" to elapsedSince(totalStartedAt)
                    )
                )
                return result
            }

            if (
                request.optString("action") == BluetoothSyncProtocol.ACTION_PREVIEW_READER &&
                request.optBoolean("stream")
            ) {
                val response = handleReaderPreviewStream(client, request, sessionId)
                return BluetoothSyncResult(remoteName, remoteAddress, request, response)
            }
            var unsupportedPhoneProtocolResponse =
                LibrarySyncPayload.buildUnsupportedPhoneProtocolResponse(request)
            if (unsupportedPhoneProtocolResponse == null && request.isCursorLibrarySync()) {
                val cursorResponse = buildLibraryCursorResponse(request, sessionId).apply {
                    if (persistentSessionAccepted) putPersistentSessionAccepted()
                }
                writeFrameLogged(client, sessionId, "cursorResponse", cursorResponse)
                request = readFrameLoggedWithTimeout(
                    client = client,
                    sessionId = sessionId,
                    label = "manifestRequest",
                    timeoutMs = requestTimeoutMs
                )
                unsupportedPhoneProtocolResponse =
                    LibrarySyncPayload.buildUnsupportedPhoneProtocolResponse(request)
            }
            if (unsupportedPhoneProtocolResponse == null && request.isManifestLibrarySync()) {
                request = readManifestRequestFrames(client, sessionId, request)
            }
            val libraryExchange = if (
                unsupportedPhoneProtocolResponse == null && request.isManifestLibrarySync()
            ) {
                handleLibraryManifestExchange(
                    client = client,
                    request = request,
                    sessionId = sessionId,
                    persistentSessionAccepted = persistentSessionAccepted
                )
            } else {
                null
            }
            val response = libraryExchange?.response ?: run {
                (unsupportedPhoneProtocolResponse ?: handleRequest(request, sessionId)).also { payload ->
                    if (persistentSessionAccepted) payload.putPersistentSessionAccepted()
                    writeFrameLogged(client, sessionId, "response", payload)
                }
            }
            val ack = waitForResponseAck(client, sessionId)
            libraryExchange?.pendingSyncSuccess?.let { pending ->
                markLibrarySyncSuccessAfterAck(pending, ack, sessionId)
            }
            completedActions += 1
            val result = BluetoothSyncResult(remoteName, remoteAddress, request, response)
            onActionCompleted?.invoke(result)
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "server.exchange.complete",
                fields = remoteFields(remoteName, remoteAddress) +
                    payloadFields("request", request) + payloadFields("response", response) +
                    mapOf(
                        "persistentSession" to persistentSession,
                        "completedActions" to completedActions,
                        "elapsedMs" to elapsedSince(totalStartedAt)
                    )
            )
            if (!persistentSession) return result
        }
    }

    private suspend fun buildLibraryCursorResponse(
        request: JSONObject,
        sessionId: String
    ): JSONObject {
        if (allowedActions != null && BluetoothSyncProtocol.ACTION_SYNC_LIBRARY !in allowedActions) {
            error("当前前台自动同步不支持此操作，请在手表上打开对应连接页面")
        }
        val app = context.applicationContext as WatchRssApplication
        val localDeviceId = WatchDeviceIdentity(context).deviceId
        val remoteDeviceId = request.optString("deviceId").trim().ifBlank { "phone" }
        val cursor = app.container.rssRepository.getLibrarySyncCursor(remoteDeviceId)
        WatchBluetoothDebugLog.event(
            sessionId = sessionId,
            event = "library.cursor.exchanged",
            fields = mapOf(
                "remoteDeviceId" to remoteDeviceId,
                "localMaxSeq" to cursor.localMaxSeq,
                "lastRemoteSeqApplied" to cursor.lastRemoteSeqApplied,
                "lastLocalSeqAckedByPeer" to cursor.lastLocalSeqAckedByPeer
            )
        )
        return LibrarySyncPayload.buildCursorResponse(
            deviceId = localDeviceId,
            cursor = LibrarySyncCursor(
                localMaxSeq = cursor.localMaxSeq,
                lastRemoteSeqApplied = cursor.lastRemoteSeqApplied,
                lastLocalSeqAckedByPeer = cursor.lastLocalSeqAckedByPeer
            )
        )
    }

    private suspend fun handleLibraryManifestExchange(
        client: WatchSyncTransport,
        request: JSONObject,
        sessionId: String,
        persistentSessionAccepted: Boolean = false
    ): LibraryManifestExchangeResult {
        val startedAt = SystemClock.elapsedRealtime()
        WatchBluetoothDebugLog.event(
            sessionId = sessionId,
            event = "library.manifest.handle.start",
            fields = payloadFields("manifestRequest", request)
        )
        return runCatching {
            if (allowedActions != null && BluetoothSyncProtocol.ACTION_SYNC_LIBRARY !in allowedActions) {
                error("当前前台自动同步不支持此操作，请在手表上打开对应连接页面")
            }
            val app = context.applicationContext as WatchRssApplication
            val localDeviceId = WatchDeviceIdentity(context).deviceId
            val remoteDeviceId = request.optString("deviceId").ifBlank { "phone" }
            val remoteCursor = LibrarySyncPayload.parseCursor(request)
            val remoteSupportsArticleBatches = request.optBoolean("supportsArticleBatches", false)
            val remoteChangeSequence = LibrarySyncPayload.parseChangeSequence(request)
            val remoteManifest = LibrarySyncPayload.parseArticleManifest(request)
            val incomingSources = LibrarySyncPayload.parseRssSources(request)
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.manifest.parsed",
                fields = mapOf(
                    "remoteManifest" to remoteManifest.size,
                    "incomingSources" to incomingSources.size,
                    "remoteSeqToInclusive" to remoteChangeSequence.toSeqInclusive,
                    "remoteFullSnapshot" to remoteChangeSequence.fullSnapshot
                )
            )
            val sourceStats = app.container.rssRepository.mergeSyncedRssSources(
                sources = incomingSources,
                remoteDeviceId = remoteDeviceId,
                localDeviceId = localDeviceId
            )
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.sources.merged",
                fields = mapOf(
                    "incomingSources" to incomingSources.size,
                    "sourcesApplied" to sourceStats.applied
                )
            )
            val supportsChunkedBodies = request.optBoolean("supportsChunkedBodies", false) &&
                request.optInt("version") > LibrarySyncPayload.LEGACY_PROTOCOL_VERSION
            val supportsMetadataOnlyArticles = LibrarySyncPayload.supportsMetadataOnlyArticles(request)
            val preparedWindow = app.container.rssRepository.prepareLibrarySyncWindow(
                peerDeviceId = remoteDeviceId,
                localDeviceId = localDeviceId,
                peerAppliedLocalSeq = remoteCursor.lastRemoteSeqApplied
            )
            val outgoingWindow = if (LibrarySyncPayload.supportsChangeSequences(request)) {
                preparedWindow
            } else {
                preparedWindow.copy(
                    articleManifest = preparedWindow.fullArticleManifest,
                    rssSources = app.container.rssRepository.exportSyncedRssSources(localDeviceId),
                    fullSnapshot = true,
                    fromSeqExclusive = 0L,
                    fallbackReason = "peerProtocol"
                )
            }
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.local.exported",
                fields = mapOf(
                    "outgoingArticles" to outgoingWindow.articleManifest.size,
                    "fullArticles" to outgoingWindow.fullArticleManifest.size,
                    "outgoingSources" to outgoingWindow.rssSources.size,
                    "chunked" to supportsChunkedBodies,
                    "localSeqMax" to outgoingWindow.toSeqInclusive,
                    "peerAckedSeq" to outgoingWindow.peerAckedSeq,
                    "remoteLocalMaxSeq" to remoteCursor.localMaxSeq,
                    "remoteLastRemoteSeqApplied" to remoteCursor.lastRemoteSeqApplied,
                    "remoteLastLocalSeqAckedByPeer" to remoteCursor.lastLocalSeqAckedByPeer,
                    "fullSnapshot" to outgoingWindow.fullSnapshot,
                    "fallbackReason" to outgoingWindow.fallbackReason
                )
            )
            val manifestResponse = if (supportsChunkedBodies) {
                val bodyRequests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
                    localManifest = outgoingWindow.fullArticleManifest,
                    remoteManifest = remoteManifest,
                    maxBodyRequestChunks = LibrarySyncPayload.MAX_BODY_REQUEST_CHUNKS_PER_SYNC,
                    supportsMetadataOnlyArticles = supportsMetadataOnlyArticles
                )
                LibrarySyncPayload.buildManifestResponseFromEntries(
                    deviceId = localDeviceId,
                    articleManifest = outgoingWindow.articleManifest,
                    bodyRequests = bodyRequests,
                    rssSources = outgoingWindow.rssSources,
                    sourcesApplied = sourceStats.applied,
                    changeSequence = LibraryChangeSequence(
                        fromSeqExclusive = outgoingWindow.fromSeqExclusive,
                        toSeqInclusive = outgoingWindow.toSeqInclusive,
                        fullSnapshot = outgoingWindow.fullSnapshot,
                        fallbackReason = outgoingWindow.fallbackReason
                    )
                )
            } else {
                val outgoing = app.container.rssRepository.exportSyncedSavedArticles(localDeviceId)
                LibrarySyncPayload.buildManifestResponse(
                    deviceId = localDeviceId,
                    articles = outgoing,
                    rssSources = outgoingWindow.rssSources,
                    sourcesApplied = sourceStats.applied,
                    changeSequence = LibraryChangeSequence(
                        fromSeqExclusive = outgoingWindow.fromSeqExclusive,
                        toSeqInclusive = outgoingWindow.toSeqInclusive,
                        fullSnapshot = outgoingWindow.fullSnapshot,
                        fallbackReason = outgoingWindow.fallbackReason
                    )
                )
            }.apply {
                if (persistentSessionAccepted) putPersistentSessionAccepted()
            }
            val manifestResponseFrames = if (LibrarySyncPayload.supportsManifestBatches(request)) {
                LibrarySyncPayload.buildManifestFrames(manifestResponse)
            } else {
                listOf(manifestResponse)
            }
            manifestResponseFrames.forEachIndexed { index, frame ->
                writeFrameLogged(
                    client,
                    sessionId,
                    batchLabel("manifestResponse", index, manifestResponseFrames.size),
                    frame
                )
            }

            val chunkedArticleRequests = if (supportsChunkedBodies) {
                readChunkedArticleRequestFrames(client, sessionId) { articles ->
                    app.container.rssRepository.mergeSyncedChunkedArticles(
                        articles = articles,
                        remoteDeviceId = remoteDeviceId,
                        localDeviceId = localDeviceId
                    )
                }
            } else {
                null
            }
            val articleRequestFrames = if (supportsChunkedBodies) {
                emptyList()
            } else {
                readArticleRequestFrames(client, sessionId)
            }
            val incoming = if (supportsChunkedBodies) {
                emptyList()
            } else {
                articleRequestFrames.flatMap { LibrarySyncPayload.parseArticles(it) }
            }
            val incomingArticleCount = chunkedArticleRequests?.receivedArticles ?: incoming.size
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.articles.parsed",
                fields = (chunkedArticleRequests?.stats?.fields("articlesRequest")
                    ?: batchFields("articlesRequest", articleRequestFrames)) +
                    mapOf("incomingArticles" to incomingArticleCount)
            )
            val stats = if (supportsChunkedBodies) {
                requireNotNull(chunkedArticleRequests).mergeStats
            } else {
                app.container.rssRepository.mergeSyncedSavedArticles(
                    articles = incoming.filterIsInstance<com.lightningstudio.watchrss.data.rss.SyncedSavedArticle>(),
                    remoteDeviceId = remoteDeviceId,
                    localDeviceId = localDeviceId
                )
            }
            val outgoingDiff = if (supportsChunkedBodies) {
                emptyList()
            } else {
                val outgoing = app.container.rssRepository.exportSyncedSavedArticles(localDeviceId)
                LibrarySyncPayload.filterArticlesNeedingSync(outgoing, remoteManifest)
            }
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.articles.merged",
                fields = mapOf(
                    "incomingArticles" to incomingArticleCount,
                    "articlesApplied" to stats.applied,
                    "outgoingDiff" to outgoingDiff.size,
                    "chunked" to supportsChunkedBodies
                )
            )
            val responseFrames = if (supportsChunkedBodies) {
                val exportStartedAt = SystemClock.elapsedRealtime()
                val phoneRequests = chunkedArticleRequests?.bodyRequests.orEmpty()
                val outgoingArticles = app.container.rssRepository.exportSyncedSavedArticlesForRequests(
                    deviceId = localDeviceId,
                    requests = phoneRequests
                )
                WatchBluetoothDebugLog.event(
                    sessionId = sessionId,
                    event = "library.response.articles.exported",
                    fields = mapOf(
                        "bodyRequests" to phoneRequests.size,
                        "metadataOnlyRequests" to phoneRequests.count { it.metadataOnly },
                        "outgoingArticles" to outgoingArticles.size,
                        "requestedChunks" to phoneRequests.sumOf { it.chunkIndexes.size },
                        "elapsedMs" to elapsedSince(exportStartedAt)
                    )
                )
                val framesStartedAt = SystemClock.elapsedRealtime()
                LibrarySyncPayload.buildChunkedResponseFramesParallel(
                    deviceId = localDeviceId,
                    articles = outgoingArticles,
                    articleRequests = phoneRequests,
                    applied = stats.applied,
                    sourcesApplied = sourceStats.applied,
                    useBatches = remoteSupportsArticleBatches,
                    allowMetadataOnlyArticles = supportsMetadataOnlyArticles
                ).also { frames ->
                    WatchBluetoothDebugLog.event(
                        sessionId = sessionId,
                        event = "library.response.frames.built",
                        fields = batchFields("libraryResponse", frames) +
                            mapOf(
                                "elapsedMs" to elapsedSince(framesStartedAt),
                                "encoderWorkers" to minOf(
                                    LibrarySyncPayload.CHUNKED_RESPONSE_ENCODER_WORKERS,
                                    outgoingArticles.size
                                )
                            )
                    )
                }
            } else {
                val framesStartedAt = SystemClock.elapsedRealtime()
                LibrarySyncPayload.buildResponseFrames(
                    deviceId = localDeviceId,
                    articles = outgoingDiff,
                    applied = stats.applied,
                    sourcesApplied = sourceStats.applied,
                    useBatches = remoteSupportsArticleBatches
                ).also { frames ->
                    WatchBluetoothDebugLog.event(
                        sessionId = sessionId,
                        event = "library.response.frames.built",
                        fields = batchFields("libraryResponse", frames) +
                            mapOf("elapsedMs" to elapsedSince(framesStartedAt))
                    )
                }
            }
            responseFrames.forEachIndexed { index, responseFrame ->
                writeFrameLogged(
                    client = client,
                    sessionId = sessionId,
                    label = batchLabel("libraryResponse", index, responseFrames.size),
                    payload = responseFrame
                )
            }
            val response = summarizeLibraryResponse(responseFrames)
            Log.i(
                TAG,
                "library manifest exchange complete incoming=$incomingArticleCount outgoing=${outgoingDiff.size} sources=${outgoingWindow.rssSources.size}"
            )
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.manifest.handle.complete",
                fields = batchFields("libraryResponse", responseFrames) + payloadFields("combinedLibraryResponse", response) + mapOf(
                    "incomingArticles" to incomingArticleCount,
                    "outgoingDiff" to outgoingDiff.size,
                    "outgoingSources" to outgoingWindow.rssSources.size,
                    "localSeqMax" to outgoingWindow.toSeqInclusive,
                    "peerAckedSeq" to outgoingWindow.peerAckedSeq,
                    "remoteSeqPendingAck" to remoteChangeSequence.toSeqInclusive,
                    "deltaArticleCount" to outgoingWindow.articleManifest.size,
                    "deltaSourceCount" to outgoingWindow.rssSources.size,
                    "fullSnapshot" to outgoingWindow.fullSnapshot,
                    "fallbackReason" to outgoingWindow.fallbackReason,
                    "peerStatePendingAck" to true,
                    "elapsedMs" to elapsedSince(startedAt)
                )
            )
            LibraryManifestExchangeResult(
                response = response,
                pendingSyncSuccess = PendingLibrarySyncSuccess(
                    remoteDeviceId = remoteDeviceId,
                    localSeqToInclusive = outgoingWindow.toSeqInclusive,
                    remoteSeqToInclusive = remoteChangeSequence.toSeqInclusive,
                    remoteProtocolVersion = request.optInt("version"),
                    fullSnapshot = outgoingWindow.fullSnapshot,
                    localArticleCount = outgoingWindow.articleManifest.size,
                    remoteArticleCount = remoteManifest.size
                )
            )
        }.getOrElse { throwable ->
            Log.e(TAG, "handle manifest library exchange failed action=${request.optString("action")} phase=${request.optString("phase")}", throwable)
            WatchBluetoothDebugLog.error(
                sessionId = sessionId,
                event = "library.manifest.handle.failed",
                fields = payloadFields("manifestRequest", request) + mapOf(
                    "elapsedMs" to elapsedSince(startedAt),
                    "errorClass" to throwable::class.java.name,
                    "message" to throwable.message.orEmpty()
                ),
                throwable = throwable
            )
            JSONObject().apply {
                put("success", false)
                put("message", throwable.message.orEmpty())
            }.also { errorResponse ->
                runCatching {
                    writeFrameLogged(client, sessionId, "errorResponse", errorResponse)
                }.onFailure { writeFailure ->
                    WatchBluetoothDebugLog.warn(
                        sessionId = sessionId,
                        event = "library.errorResponse.write.failed",
                        fields = mapOf(
                            "errorClass" to writeFailure::class.java.name,
                            "message" to writeFailure.message.orEmpty()
                        ),
                        throwable = writeFailure
                    )
                }
            }.let { errorResponse ->
                LibraryManifestExchangeResult(
                    response = errorResponse,
                    pendingSyncSuccess = null
                )
            }
        }
    }

    private suspend fun readManifestRequestFrames(
        client: WatchSyncTransport,
        sessionId: String,
        first: JSONObject
    ): JSONObject {
        val batchCount = LibrarySyncPayload.manifestBatchCount(first)
        if (batchCount <= 1) return first
        val frames = ArrayList<JSONObject>(batchCount)
        frames += first
        for (index in 1 until batchCount) {
            frames += readFrameLoggedWithTimeout(
                client = client,
                sessionId = sessionId,
                label = batchLabel("manifestRequest", index, batchCount),
                timeoutMs = EXCHANGE_FRAME_READ_TIMEOUT_MS
            )
        }
        return LibrarySyncPayload.combineManifestFrames(frames)
    }

    private suspend fun readArticleRequestFrames(
        client: WatchSyncTransport,
        sessionId: String
    ): List<JSONObject> {
        val first = readFrameLoggedWithTimeout(
            client = client,
            sessionId = sessionId,
            label = "articlesRequest",
            timeoutMs = EXCHANGE_FRAME_READ_TIMEOUT_MS
        )
        val batchCount = LibrarySyncPayload.validateArticleRequestFrame(
            frame = first,
            expectedBatchIndex = 0
        )
        val frames = mutableListOf(first)
        while (frames.size < batchCount) {
            val index = frames.size
            val frame = readFrameLoggedWithTimeout(
                client = client,
                sessionId = sessionId,
                label = batchLabel("articlesRequest", index, batchCount),
                timeoutMs = EXCHANGE_FRAME_READ_TIMEOUT_MS
            )
            LibrarySyncPayload.validateArticleRequestFrame(
                frame = frame,
                expectedBatchIndex = index,
                expectedBatchCount = batchCount
            )
            frames += frame
        }
        return frames
    }

    private suspend fun readChunkedArticleRequestFrames(
        client: WatchSyncTransport,
        sessionId: String,
        mergeArticles: suspend (List<SyncedChunkedArticle>) -> SyncedSavedArticleMergeStats
    ): ChunkedArticleRequestFrames {
        val first = readFrameLoggedWithTimeout(
            client = client,
            sessionId = sessionId,
            label = "articlesRequest",
            timeoutMs = EXCHANGE_FRAME_READ_TIMEOUT_MS
        )
        val batchCount = LibrarySyncPayload.validateArticleRequestFrame(
            frame = first,
            expectedBatchIndex = 0
        )
        val bodyRequests = mutableListOf<SyncedArticleBodyRequest>()
        val stats = ArticleRequestBatchStats()
        val completedArticleIds = hashSetOf<String>()
        var pendingArticle: SyncedChunkedArticle? = null
        var receivedArticles = 0
        var receivedForMerge = 0
        var applied = 0

        suspend fun mergeReady(articles: List<SyncedChunkedArticle>) {
            if (articles.isEmpty()) return
            val merged = mergeArticles(articles)
            receivedForMerge += merged.received
            applied += merged.applied
        }

        suspend fun consume(frame: JSONObject) {
            stats.add(frame)
            val ready = mutableListOf<SyncedChunkedArticle>()
            LibrarySyncPayload.parseChunkedArticles(frame).forEach { payload ->
                val current = pendingArticle
                if (current == null) {
                    require(payload.article.articleId !in completedArticleIds) {
                        "同步正文文章批次顺序异常：${payload.article.articleId}"
                    }
                    pendingArticle = payload
                    receivedArticles += 1
                } else if (current.article.articleId == payload.article.articleId) {
                    pendingArticle = current.mergeChunkedArticle(payload)
                } else {
                    completedArticleIds += current.article.articleId
                    ready += current
                    require(payload.article.articleId !in completedArticleIds) {
                        "同步正文文章批次顺序异常：${payload.article.articleId}"
                    }
                    pendingArticle = payload
                    receivedArticles += 1
                }
            }
            bodyRequests += LibrarySyncPayload.parseBodyRequests(frame)
            mergeReady(ready)
        }

        consume(first)
        var frameCount = 1
        while (frameCount < batchCount) {
            val frame = readFrameLoggedWithTimeout(
                client = client,
                sessionId = sessionId,
                label = batchLabel("articlesRequest", frameCount, batchCount),
                timeoutMs = EXCHANGE_FRAME_READ_TIMEOUT_MS
            )
            LibrarySyncPayload.validateArticleRequestFrame(
                frame = frame,
                expectedBatchIndex = frameCount,
                expectedBatchCount = batchCount
            )
            consume(frame)
            frameCount += 1
        }
        pendingArticle?.let { article ->
            mergeReady(listOf(article))
            completedArticleIds += article.article.articleId
            pendingArticle = null
        }
        require(receivedForMerge == receivedArticles) {
            "同步正文流式合并数量异常：received=$receivedArticles merged=$receivedForMerge"
        }
        return ChunkedArticleRequestFrames(
            bodyRequests = bodyRequests,
            stats = stats,
            receivedArticles = receivedArticles,
            mergeStats = SyncedSavedArticleMergeStats(
                received = receivedForMerge,
                applied = applied
            )
        )
    }

    private fun SyncedChunkedArticle.mergeChunkedArticle(
        payload: SyncedChunkedArticle
    ): SyncedChunkedArticle {
        require(article.articleId == payload.article.articleId) {
            "同步正文文章不一致：${article.articleId} != ${payload.article.articleId}"
        }
        require(
            bodyHash == payload.bodyHash &&
                chunkSize == payload.chunkSize &&
                chunkHashes == payload.chunkHashes &&
                metadataOnly == payload.metadataOnly
        ) {
            "同步正文分块元数据冲突：${payload.article.articleId}"
        }
        return copy(
            article = payload.article,
            chunks = (chunks + payload.chunks)
                .distinctBy { it.index }
                .sortedBy { it.index }
        )
    }

    private fun summarizeLibraryResponse(responseFrames: List<JSONObject>): JSONObject {
        if (responseFrames.isEmpty()) return JSONObject()
        val first = responseFrames.first()
        if (!first.optBoolean("success", true)) return first
        return JSONObject().apply {
            put("success", true)
            put("version", first.optInt("version", LibrarySyncPayload.PROTOCOL_VERSION))
            put("action", BluetoothSyncProtocol.ACTION_SYNC_LIBRARY)
            put("phase", first.optString("phase").ifBlank { LibrarySyncPayload.PHASE_COMPLETE })
            put("deviceId", first.optString("deviceId"))
            put("sentAt", first.optLong("sentAt"))
            put("batchCount", responseFrames.size)
            put("totalArticles", responseFrames.sumOf { it.optJSONArray("articles")?.length() ?: 0 })
            first.optJSONObject("stats")?.let { put("stats", it) }
            first.optString("message").takeIf { it.isNotBlank() }?.let { put("message", it) }
        }
    }

    private suspend fun waitForResponseAck(client: WatchSyncTransport, sessionId: String): BluetoothSyncAck? {
        val startedAt = SystemClock.elapsedRealtime()
        WatchBluetoothDebugLog.event(
            sessionId = sessionId,
            event = "ack.read.start",
            fields = mapOf("timeoutMs" to responseAckTimeoutMs)
        )
        var ack = readAckPayloadOrNull(client, sessionId, responseAckTimeoutMs, startedAt)
        var parsedAck = BluetoothSyncProtocol.parseAck(ack)
        if (
            parsedAck != null &&
            parsedAck.success &&
            !parsedAck.applied &&
            parsedAck.phase == BluetoothSyncProtocol.ACK_PHASE_RECEIVED
        ) {
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "ack.received.waitApplied",
                fields = mapOf(
                    "timeoutMs" to APPLIED_ACK_TIMEOUT_MS,
                    "elapsedMs" to elapsedSince(startedAt)
                )
            )
            val appliedPayload = readAckPayloadOrNull(client, sessionId, APPLIED_ACK_TIMEOUT_MS, startedAt)
            val appliedAck = BluetoothSyncProtocol.parseAck(appliedPayload)
            if (appliedAck != null) {
                ack = appliedPayload
                parsedAck = appliedAck
            }
        }
        return if (parsedAck != null) {
            Log.i(TAG, "response ack received phase=${parsedAck.phase} applied=${parsedAck.applied}")
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "ack.read.success",
                fields = payloadFields("ack", ack!!) + mapOf(
                    "ackApplicationSucceeded" to parsedAck.applicationSucceeded,
                    "elapsedMs" to elapsedSince(startedAt)
                )
            )
            parsedAck
        } else {
            Log.w(TAG, "response ack missing before socket close")
            WatchBluetoothDebugLog.warn(
                sessionId = sessionId,
                event = "ack.read.missing",
                fields = (ack?.let { payloadFields("ack", it) } ?: emptyMap()) +
                    mapOf("elapsedMs" to elapsedSince(startedAt))
            )
            null
        }
    }

    private suspend fun readAckPayloadOrNull(
        client: WatchSyncTransport,
        sessionId: String,
        timeoutMs: Long,
        startedAt: Long
    ): JSONObject? = coroutineScope {
        val timeoutJob = launch {
            delay(timeoutMs)
            runCatching {
                client.close()
            }.onSuccess {
                WatchBluetoothDebugLog.warn(
                    sessionId = sessionId,
                    event = "ack.read.timeout.closed",
                    fields = mapOf(
                        "timeoutMs" to timeoutMs,
                        "elapsedMs" to elapsedSince(startedAt)
                    )
                )
            }
        }
        runCatching {
            BluetoothSyncProtocol.readFrame(client.inputStream)
        }.also {
            timeoutJob.cancel()
        }.onFailure { throwable ->
            WatchBluetoothDebugLog.warn(
                sessionId = sessionId,
                event = "ack.read.failed",
                fields = mapOf(
                    "elapsedMs" to elapsedSince(startedAt),
                    "errorClass" to throwable::class.java.name,
                    "message" to throwable.message.orEmpty()
                ),
                throwable = throwable
            )
        }.getOrNull()
    }

    private suspend fun readFrameLoggedWithTimeout(
        client: WatchSyncTransport,
        sessionId: String,
        label: String,
        timeoutMs: Long,
        logFrame: Boolean = true
    ): JSONObject = withTransportReadTimeout(
        timeoutMs = timeoutMs,
        closeTransport = {
            runCatching { client.close() }.onSuccess {
                WatchBluetoothDebugLog.warn(
                    sessionId = sessionId,
                    event = "frame.read.timeout.closed",
                    fields = mapOf(
                        "label" to label,
                        "timeoutMs" to timeoutMs
                    )
                )
            }
        },
        read = {
            if (logFrame) {
                readFrameLogged(client, sessionId, label)
            } else {
                BluetoothSyncProtocol.readFrame(client.inputStream)
            }
        }
    )

    private suspend fun markLibrarySyncSuccessAfterAck(
        pending: PendingLibrarySyncSuccess,
        ack: BluetoothSyncAck?,
        sessionId: String
    ) {
        if (ack?.applicationSucceeded != true) {
            WatchBluetoothDebugLog.warn(
                sessionId = sessionId,
                event = "library.peerState.mark.skipped",
                fields = mapOf(
                    "reason" to if (ack == null) "missingAck" else "ackNotApplied",
                    "ackSuccess" to ack?.success,
                    "ackPhase" to ack?.phase,
                    "ackApplied" to ack?.applied,
                    "ackMessage" to ack?.message,
                    "localSeqMax" to pending.localSeqToInclusive,
                    "remoteSeqPendingAck" to pending.remoteSeqToInclusive
                )
            )
            return
        }

        val startedAt = SystemClock.elapsedRealtime()
        runCatching {
            val app = context.applicationContext as WatchRssApplication
            app.container.rssRepository.markLibrarySyncSuccess(
                peerDeviceId = pending.remoteDeviceId,
                localSeqToInclusive = pending.localSeqToInclusive,
                remoteSeqToInclusive = pending.remoteSeqToInclusive,
                remoteProtocolVersion = pending.remoteProtocolVersion,
                fullSnapshot = pending.fullSnapshot
            )
        }.onSuccess {
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.peerState.mark.success",
                fields = mapOf(
                    "remoteDeviceId" to pending.remoteDeviceId,
                    "localSeqMax" to pending.localSeqToInclusive,
                    "remoteSeqApplied" to pending.remoteSeqToInclusive,
                    "remoteProtocolVersion" to pending.remoteProtocolVersion,
                    "fullSnapshot" to pending.fullSnapshot,
                    "localArticleCount" to pending.localArticleCount,
                    "remoteArticleCount" to pending.remoteArticleCount,
                    "ackPhase" to ack.phase,
                    "elapsedMs" to elapsedSince(startedAt)
                )
            )
        }.onFailure { throwable ->
            WatchBluetoothDebugLog.error(
                sessionId = sessionId,
                event = "library.peerState.mark.failed",
                fields = mapOf(
                    "remoteDeviceId" to pending.remoteDeviceId,
                    "localSeqMax" to pending.localSeqToInclusive,
                    "remoteSeqPendingAck" to pending.remoteSeqToInclusive,
                    "errorClass" to throwable::class.java.name,
                    "message" to throwable.message.orEmpty(),
                    "elapsedMs" to elapsedSince(startedAt)
                ),
                throwable = throwable
            )
        }.getOrThrow()
    }

    private suspend fun handleRequest(request: JSONObject, sessionId: String): JSONObject {
        val startedAt = SystemClock.elapsedRealtime()
        WatchBluetoothDebugLog.event(
            sessionId = sessionId,
            event = "request.handle.start",
            fields = payloadFields("request", request)
        )
        return runCatching {
            val action = request.optString("action")
            if (allowedActions != null && action !in allowedActions) {
                error("当前前台自动同步不支持此操作，请在手表上打开对应连接页面")
            }
            when (action) {
                BluetoothSyncProtocol.ACTION_PING -> {
                    JSONObject().apply {
                        put("success", true)
                        put("action", "pong")
                        put("nonce", request.optString("nonce"))
                    }
                }

                BluetoothSyncProtocol.ACTION_REMOTE_INPUT -> {
                    requireExpectedAbility(PhoneConnectionAbility.REMOTE_INPUT)
                    val url = request.optString("url").trim()
                    require(url.isNotBlank()) { "缺少 RSS URL" }
                    JSONObject().apply {
                        put("success", true)
                        put("action", BluetoothSyncProtocol.ACTION_REMOTE_INPUT)
                    }
                }

                BluetoothSyncProtocol.ACTION_PULL_SAVED_ITEMS -> {
                    val saveType = parseSaveType(request.optString("type"))
                    requireExpectedAbility(
                        when (saveType) {
                            SaveType.FAVORITE -> PhoneConnectionAbility.SYNC_FAVORITES
                            SaveType.WATCH_LATER -> PhoneConnectionAbility.SYNC_WATCH_LATER
                        }
                    )
                    val items = (context.applicationContext as WatchRssApplication)
                        .container
                        .rssRepository
                        .observeSavedItems(saveType)
                        .first()
                    val payload = SavedItemsSyncPayload.buildLinksOnly(items)
                    WatchBluetoothDebugLog.event(
                        sessionId = sessionId,
                        event = "request.pullSavedItems.built",
                        fields = mapOf(
                            "type" to saveType.name,
                            "items" to payload.length()
                        )
                    )
                    JSONObject().apply {
                        put("success", true)
                        put("action", BluetoothSyncProtocol.ACTION_PULL_SAVED_ITEMS)
                        put("type", saveType.name)
                        put("count", payload.length())
                        put("items", payload)
                    }
                }

                BluetoothSyncProtocol.ACTION_SYNC_ACCOUNT -> {
                    val app = context.applicationContext as WatchRssApplication
                    val localDeviceId = WatchDeviceIdentity(context).deviceId
                    val state = AccountSyncPayload.parseRequest(request)
                    app.accountStore.save(state)
                    app.usageTelemetry.recordSyncAccount()
                    WatchBluetoothDebugLog.event(
                        sessionId = sessionId,
                        event = "request.syncAccount.saved",
                        fields = mapOf(
                            "userId" to state.userId,
                            "plan" to state.entitlement.plan,
                            "tokenExpiresAt" to state.tokenExpiresAtMillis,
                            "refreshTokenExpiresAt" to state.refreshTokenExpiresAtMillis,
                            "diagnosticsEnabled" to state.telemetryConfig.diagnosticsEnabled
                        )
                    )
                    AccountSyncPayload.buildResponse(
                        deviceId = localDeviceId,
                        state = state,
                        telemetryBacklog = app.usageTelemetry.backlogCount()
                    )
                }

                BluetoothSyncProtocol.ACTION_SYNC_READER -> {
                    val app = context.applicationContext as WatchRssApplication
                    if (
                        request.optString("phase") ==
                        ReaderPresetSyncPayload.PHASE_PUSH_RESOURCE &&
                        request.optString("kind") in setOf("background", "variant")
                    ) {
                        app.readerPresetPreviewSession.refreshResourceTransfer()
                    }
                    ReaderPresetSyncPayload.handle(
                        request = request,
                        repository = app.container.readerPresetRepository
                    )
                }

                BluetoothSyncProtocol.ACTION_PREVIEW_READER -> {
                    val app = context.applicationContext as WatchRssApplication
                    ReaderPresetPreviewPayload.handle(
                        request = request,
                        session = app.readerPresetPreviewSession
                    ).also { response ->
                        if (
                            request.optString("phase") == ReaderPresetPreviewPayload.PHASE_UPDATE &&
                            response.optBoolean("applied")
                        ) {
                            app.openReaderPresetPreview()
                        }
                    }
                }

                BluetoothSyncProtocol.ACTION_SYNC_LLM_TOKEN_USAGE -> {
                    val app = context.applicationContext as WatchRssApplication
                    val limit = request.optInt("limit", 200)
                    val records = app.container.llmTokenUsageRepository.getRecent(limit)
                    val payload = JSONArray().apply {
                        records.forEach { record ->
                            put(JSONObject().apply {
                                put("id", record.id)
                                put("provider", record.provider)
                                put("model", record.model)
                                put("requestId", record.requestId)
                                put("createdAt", record.createdAt)
                                putOpt("promptTokens", record.promptTokens)
                                putOpt("completionTokens", record.completionTokens)
                                putOpt("totalTokens", record.totalTokens)
                                putOpt("reasoningTokens", record.reasoningTokens)
                                putOpt("cachedPromptTokens", record.cachedPromptTokens)
                                putOpt("inputTokens", record.inputTokens)
                                putOpt("outputTokens", record.outputTokens)
                                putOpt("promptTokenCount", record.promptTokenCount)
                                putOpt("candidatesTokenCount", record.candidatesTokenCount)
                                putOpt("totalTokenCount", record.totalTokenCount)
                            })
                        }
                    }
                    WatchBluetoothDebugLog.event(
                        sessionId = sessionId,
                        event = "request.syncLlmTokenUsage.built",
                        fields = mapOf("records" to payload.length())
                    )
                    JSONObject().apply {
                        put("success", true)
                        put("action", BluetoothSyncProtocol.ACTION_SYNC_LLM_TOKEN_USAGE)
                        put("count", payload.length())
                        put("records", payload)
                    }
                }

                BluetoothSyncProtocol.ACTION_SYNC_LIBRARY -> {
                    val localDeviceId = WatchDeviceIdentity(context).deviceId
                    if (request.optString("phase") == LibrarySyncPayload.PHASE_PROBE) {
                        val app = context.applicationContext as WatchRssApplication
                        val ipUpgradeAccepted = request.optJSONObject(FIELD_IP_ENDPOINT_DESCRIPTOR)
                            ?.let(app.ipSyncManager::offerEndpointDescriptor)
                            ?: false
                        WatchBluetoothDebugLog.event(
                            sessionId = sessionId,
                            event = "request.syncLibrary.probe",
                            fields = mapOf(
                                "remoteDeviceId" to request.optString("deviceId"),
                                "ipUpgradeAccepted" to ipUpgradeAccepted
                            )
                        )
                        return@runCatching LibrarySyncPayload.buildProbeResponse(
                            localDeviceId,
                            WatchMediaCapabilities.inspect(context)
                        ).apply { put(FIELD_IP_UPGRADE_ACCEPTED, ipUpgradeAccepted) }
                    }
                    val app = context.applicationContext as WatchRssApplication
                    val remoteDeviceId = request.optString("deviceId").ifBlank { "phone" }
                    val incomingSources = LibrarySyncPayload.parseRssSources(request)
                    val sourceStats = app.container.rssRepository.mergeSyncedRssSources(
                        sources = incomingSources,
                        remoteDeviceId = remoteDeviceId,
                        localDeviceId = localDeviceId
                    )
                    val incoming = LibrarySyncPayload.parseArticles(request)
                    val stats = app.container.rssRepository.mergeSyncedSavedArticles(
                        articles = incoming,
                        remoteDeviceId = remoteDeviceId,
                        localDeviceId = localDeviceId
                    )
                    val outgoing = app.container.rssRepository.exportSyncedSavedArticles(localDeviceId)
                    val outgoingSources = app.container.rssRepository.exportSyncedRssSources(localDeviceId)
                    WatchBluetoothDebugLog.event(
                        sessionId = sessionId,
                        event = "request.syncLibrary.v3.merged",
                        fields = mapOf(
                            "incomingArticles" to incoming.size,
                            "articlesApplied" to stats.applied,
                            "outgoingArticles" to outgoing.size,
                            "incomingSources" to incomingSources.size,
                            "outgoingSources" to outgoingSources.size,
                            "sourcesApplied" to sourceStats.applied
                        )
                    )
                    LibrarySyncPayload.buildResponse(
                        deviceId = localDeviceId,
                        articles = outgoing,
                        applied = stats.applied,
                        rssSources = outgoingSources,
                        sourcesApplied = sourceStats.applied
                    )
                }

                BluetoothSyncProtocol.ACTION_SYNC_NOTES -> {
                    val app = context.applicationContext as WatchRssApplication
                    val incoming = WatchNoteSyncPayload.parse(request)
                    val applied = app.container.watchNoteRepository.merge(incoming)
                    val localDeviceId = WatchDeviceIdentity(context).deviceId
                    WatchNoteSyncPayload.response(localDeviceId, app.container.watchNoteRepository.all(), applied)
                }

                BluetoothSyncProtocol.ACTION_SYNC_NOTE_ASSET -> {
                    WatchNoteAssetSyncPayload.apply(context, request)
                }

                else -> error("未知蓝牙同步动作：$action")
            }
        }.onSuccess { response ->
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "request.handle.complete",
                fields = payloadFields("response", response) +
                    mapOf("elapsedMs" to elapsedSince(startedAt))
            )
        }.getOrElse { throwable ->
            Log.e(TAG, "handle request failed action=${request.optString("action")}", throwable)
            WatchBluetoothDebugLog.error(
                sessionId = sessionId,
                event = "request.handle.failed",
                fields = payloadFields("request", request) + mapOf(
                    "elapsedMs" to elapsedSince(startedAt),
                    "errorClass" to throwable::class.java.name,
                    "message" to throwable.message.orEmpty()
                ),
                throwable = throwable
            )
            JSONObject().apply {
                put("success", false)
                put("message", throwable.message.orEmpty())
            }
        }
    }

    private suspend fun handleReaderPreviewStream(
        client: WatchSyncTransport,
        initialRequest: JSONObject,
        sessionId: String
    ): JSONObject {
        val app = context.applicationContext as WatchRssApplication
        var request = initialRequest
        var lastResponse = JSONObject()
        var lastAcknowledgedAt = 0L
        var lastReadElapsedMs = 0L
        val stats = ReaderPreviewStreamStats()
        while (true) {
            val handleStartedAt = SystemClock.elapsedRealtime()
            lastResponse = ReaderPresetPreviewPayload.handle(
                request = request,
                session = app.readerPresetPreviewSession
            )
            val handledAt = SystemClock.elapsedRealtime()
            val phase = request.optString("phase")
            val shouldAcknowledge =
                lastAcknowledgedAt == 0L ||
                    phase == ReaderPresetPreviewPayload.PHASE_STOP ||
                    phase == ReaderPresetPreviewPayload.PHASE_RESOURCE_HANDOFF ||
                    !lastResponse.optBoolean("applied", true) ||
                    handledAt - lastAcknowledgedAt >= PREVIEW_ACK_INTERVAL_MS
            if (shouldAcknowledge) {
                BluetoothSyncProtocol.writeFrame(client.outputStream, lastResponse)
                lastAcknowledgedAt = SystemClock.elapsedRealtime()
            }
            stats.recordFrame(
                phase = phase,
                handleMs = handledAt - handleStartedAt,
                readMs = lastReadElapsedMs,
                acknowledged = shouldAcknowledge
            )
            stats.snapshotIfDue()?.let { fields ->
                WatchBluetoothDebugLog.event(
                    sessionId = sessionId,
                    event = "preview.stream.stats",
                    fields = fields
                )
            }
            if (
                phase == ReaderPresetPreviewPayload.PHASE_UPDATE &&
                lastResponse.optBoolean("applied")
            ) {
                app.openReaderPresetPreview()
            }
            if (
                phase == ReaderPresetPreviewPayload.PHASE_STOP ||
                phase == ReaderPresetPreviewPayload.PHASE_RESOURCE_HANDOFF
            ) {
                return lastResponse
            }
            val readStartedAt = SystemClock.elapsedRealtime()
            request = readFrameLoggedWithTimeout(
                client = client,
                sessionId = sessionId,
                label = "previewFrame",
                timeoutMs = PREVIEW_STREAM_IDLE_TIMEOUT_MS,
                logFrame = false
            )
            lastReadElapsedMs = SystemClock.elapsedRealtime() - readStartedAt
            require(
                request.optString("action") ==
                    BluetoothSyncProtocol.ACTION_PREVIEW_READER
            ) { "实时预览连接收到了其他蓝牙动作" }
        }
    }

    private fun readFrameLogged(
        client: WatchSyncTransport,
        sessionId: String,
        label: String
    ): JSONObject {
        val startedAt = SystemClock.elapsedRealtime()
        WatchBluetoothDebugLog.event(
            sessionId = sessionId,
            event = "frame.read.start",
            fields = mapOf("label" to label)
        )
        return try {
            BluetoothSyncProtocol.readFrame(client.inputStream).also { payload ->
                WatchBluetoothDebugLog.event(
                    sessionId = sessionId,
                    event = "frame.read.success",
                    fields = mapOf("label" to label, "elapsedMs" to elapsedSince(startedAt)) +
                        payloadFields(label, payload)
                )
            }
        } catch (throwable: Throwable) {
            WatchBluetoothDebugLog.error(
                sessionId = sessionId,
                event = "frame.read.failed",
                fields = mapOf(
                    "label" to label,
                    "elapsedMs" to elapsedSince(startedAt),
                    "errorClass" to throwable::class.java.name,
                    "message" to throwable.message.orEmpty()
                ),
                throwable = throwable
            )
            throw throwable
        }
    }

    private fun writeFrameLogged(
        client: WatchSyncTransport,
        sessionId: String,
        label: String,
        payload: JSONObject
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        val fields = payloadFields(label, payload)
        WatchBluetoothDebugLog.event(
            sessionId = sessionId,
            event = "frame.write.start",
            fields = mapOf("label" to label) + fields
        )
        try {
            BluetoothSyncProtocol.writeFrame(client.outputStream, payload)
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "frame.write.success",
                fields = mapOf("label" to label, "elapsedMs" to elapsedSince(startedAt)) + fields
            )
        } catch (throwable: Throwable) {
            WatchBluetoothDebugLog.error(
                sessionId = sessionId,
                event = "frame.write.failed",
                fields = mapOf(
                    "label" to label,
                    "elapsedMs" to elapsedSince(startedAt),
                    "errorClass" to throwable::class.java.name,
                    "message" to throwable.message.orEmpty()
                ) + fields,
                throwable = throwable
            )
            throw throwable
        }
    }

    private fun payloadFields(prefix: String, payload: JSONObject): Map<String, Any?> {
        return buildMap {
            put("${prefix}Bytes", runCatching { BluetoothSyncProtocol.encodedSize(payload) }.getOrDefault(-1))
            put("${prefix}Action", payload.optString("action").ifBlank { null })
            put("${prefix}Version", if (payload.has("version")) payload.optInt("version") else null)
            put("${prefix}Phase", payload.optString("phase").ifBlank { null })
            put("${prefix}Success", if (payload.has("success")) payload.optBoolean("success") else null)
            put("${prefix}Message", payload.optString("message").ifBlank { null })
            put("${prefix}ArticleManifestCount", payload.optJSONArray("articleManifest")?.length())
            put("${prefix}ArticleCount", payload.optJSONArray("articles")?.length())
            val bodyRequests = payload.optJSONArray("bodyRequests")
            put("${prefix}BodyRequestCount", bodyRequests?.length())
            put("${prefix}MetadataOnlyBodyRequestCount", bodyRequests?.metadataOnlyCount())
            put("${prefix}BodyRequestChunkCount", bodyRequests?.chunkIndexCount())
            put("${prefix}RssSourceCount", payload.optJSONArray("rssSources")?.length())
            put("${prefix}ItemCount", payload.optJSONArray("items")?.length())
            put("${prefix}Count", if (payload.has("count")) payload.optInt("count") else null)
            put("${prefix}Applied", payload.opt("applied")?.takeUnless { it == JSONObject.NULL })
            put("${prefix}SourcesApplied", if (payload.has("sourcesApplied")) payload.optInt("sourcesApplied") else null)
            put("${prefix}BatchIndex", if (payload.has("batchIndex")) payload.optInt("batchIndex") else null)
            put("${prefix}BatchCount", if (payload.has("batchCount")) payload.optInt("batchCount") else null)
            put(
                "${prefix}BatchWireBytes",
                if (payload.has(LibrarySyncPayload.FIELD_BATCH_WIRE_BYTES)) {
                    payload.optLong(LibrarySyncPayload.FIELD_BATCH_WIRE_BYTES)
                } else {
                    null
                }
            )
            put(
                "${prefix}BatchTotalWireBytes",
                if (payload.has(LibrarySyncPayload.FIELD_BATCH_TOTAL_WIRE_BYTES)) {
                    payload.optLong(LibrarySyncPayload.FIELD_BATCH_TOTAL_WIRE_BYTES)
                } else {
                    null
                }
            )
            put("${prefix}TotalArticles", if (payload.has("totalArticles")) payload.optInt("totalArticles") else null)
        }
    }

    private fun org.json.JSONArray.metadataOnlyCount(): Int {
        var count = 0
        for (index in 0 until length()) {
            if (optJSONObject(index)?.optBoolean("metadataOnly", false) == true) count += 1
        }
        return count
    }

    private fun org.json.JSONArray.chunkIndexCount(): Int {
        var count = 0
        for (index in 0 until length()) {
            count += optJSONObject(index)?.optJSONArray("chunkIndexes")?.length() ?: 0
        }
        return count
    }

    private fun batchFields(prefix: String, payloads: List<JSONObject>): Map<String, Any?> {
        val articleCount = payloads.sumOf { it.optJSONArray("articles")?.length() ?: 0 }
        val bytes = payloads.sumOf { payload ->
            runCatching { BluetoothSyncProtocol.encodedSize(payload) }.getOrDefault(0)
        }
        val wireBytes = payloads.sumOf { payload ->
            runCatching { BluetoothSyncProtocol.wireSize(payload) }.getOrDefault(0L)
        }
        val maxBytes = payloads.maxOfOrNull { payload ->
            runCatching { BluetoothSyncProtocol.encodedSize(payload) }.getOrDefault(0)
        } ?: 0
        return mapOf(
            "${prefix}FrameCount" to payloads.size,
            "${prefix}TotalBytes" to bytes,
            "${prefix}TotalWireBytes" to wireBytes,
            "${prefix}MaxFrameBytes" to maxBytes,
            "${prefix}ArticleCount" to articleCount
        )
    }

    private fun batchLabel(prefix: String, index: Int, count: Int): String {
        if (count <= 1) return prefix
        return "$prefix[${index + 1}/$count]"
    }

    private fun remoteFields(name: String, address: String): Map<String, Any?> {
        return mapOf(
            "remoteName" to name,
            "remoteAddress" to address
        )
    }

    private fun elapsedSince(startedAt: Long): Long =
        SystemClock.elapsedRealtime() - startedAt

    private fun requireExpectedAbility(requestedAbility: PhoneConnectionAbility) {
        val expected = expectedAbility ?: return
        require(expected == requestedAbility) {
            "当前等待的是${expected.displayName}，手机请求的是${requestedAbility.displayName}"
        }
    }

    private fun parseSaveType(value: String): SaveType {
        return when (value.trim().uppercase()) {
            "FAVORITE", "SYNC_FAVORITES" -> SaveType.FAVORITE
            "WATCH_LATER", "WATCHLATER", "SYNC_WATCH_LATER" -> SaveType.WATCH_LATER
            else -> error("未知保存类型：$value")
        }
    }

    private suspend fun BluetoothServerSocket.acceptWithTimeout(timeoutMs: Long): BluetoothSocket =
        coroutineScope {
            val startedAt = SystemClock.elapsedRealtime()
            val closeJob = launch {
                try {
                    delay(timeoutMs)
                } finally {
                    runCatching { close() }
                }
            }
            try {
                accept()
            } catch (exception: IOException) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (elapsed >= timeoutMs) {
                    throw IllegalStateException("等待手机蓝牙连接超时", exception)
                }
                throw exception
            } finally {
                closeJob.cancel()
            }
        }

    private fun requireBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        require(granted) { "缺少 BLUETOOTH_CONNECT 权限" }
    }

    private fun JSONObject.isManifestLibrarySync(): Boolean {
        return optString("action") == BluetoothSyncProtocol.ACTION_SYNC_LIBRARY &&
            optInt("version") >= LibrarySyncPayload.LEGACY_PROTOCOL_VERSION &&
            optString("phase") == LibrarySyncPayload.PHASE_MANIFEST
    }

    private fun JSONObject.isCursorLibrarySync(): Boolean {
        return optString("action") == BluetoothSyncProtocol.ACTION_SYNC_LIBRARY &&
            optInt("version") >= LibrarySyncPayload.PROTOCOL_VERSION &&
            optString("phase") == LibrarySyncPayload.PHASE_CURSOR
    }

    private fun JSONObject.putPersistentSessionAccepted() {
        put(BluetoothSyncProtocol.FIELD_SUPPORTS_PERSISTENT_SESSION, true)
        put(BluetoothSyncProtocol.FIELD_PERSISTENT_SESSION_ACCEPTED, true)
    }

    companion object {
        private const val TAG = "WatchRSS_BtSyncServer"
        private const val FIELD_IP_ENDPOINT_DESCRIPTOR = "ipEndpointDescriptor"
        private const val FIELD_IP_UPGRADE_ACCEPTED = "ipUpgradeAccepted"
        private const val DEFAULT_TIMEOUT_MS = 120_000L
        private const val PREVIEW_STREAM_IDLE_TIMEOUT_MS = 30_000L
        private const val PREVIEW_ACK_INTERVAL_MS = 50L
        private const val RESPONSE_ACK_TIMEOUT_MS = 10_000L
        private const val APPLIED_ACK_TIMEOUT_MS = 120_000L
        private const val INITIAL_REQUEST_READ_TIMEOUT_MS = 30_000L
        private const val EXCHANGE_FRAME_READ_TIMEOUT_MS = 180_000L
    }
}
