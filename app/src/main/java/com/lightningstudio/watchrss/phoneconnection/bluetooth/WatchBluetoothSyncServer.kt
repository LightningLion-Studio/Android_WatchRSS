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
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.phoneconnection.SavedItemsSyncPayload
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException

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
    var maxFrameBytes: Int = 0,
    var articleCount: Int = 0
) {
    fun add(frame: JSONObject) {
        frameCount += 1
        val bytes = runCatching { BluetoothSyncProtocol.encodedSize(frame) }.getOrDefault(0)
        totalBytes += bytes
        maxFrameBytes = maxOf(maxFrameBytes, bytes)
        articleCount += frame.optJSONArray("articles")?.length() ?: 0
    }

    fun fields(prefix: String): Map<String, Any?> {
        return mapOf(
            "${prefix}FrameCount" to frameCount,
            "${prefix}TotalBytes" to totalBytes,
            "${prefix}MaxFrameBytes" to maxFrameBytes,
            "${prefix}ArticleCount" to articleCount
        )
    }
}

private data class ChunkedArticleRequestFrames(
    val articles: List<SyncedChunkedArticle>,
    val bodyRequests: List<SyncedArticleBodyRequest>,
    val stats: ArticleRequestBatchStats
)

class WatchBluetoothSyncServer(
    private val context: Context,
    private val expectedAbility: PhoneConnectionAbility? = null,
    private val allowedActions: Set<String>? = null,
    private val onClientAccepted: (() -> Unit)? = null
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
                bluetoothSocket.use { client ->
                    val remoteName = client.remoteDevice?.name.orEmpty()
                    val remoteAddress = client.remoteDevice?.address.orEmpty()
                    Log.i(TAG, "accepted from name=$remoteName address=$remoteAddress")
                    WatchBluetoothDebugLog.event(
                        sessionId = sessionId,
                        event = "server.client.accepted",
                        fields = remoteFields(remoteName, remoteAddress)
                    )
                    onClientAccepted?.invoke()
                    val request = readFrameLogged(client, sessionId, "initialRequest")
                    val libraryExchange = if (request.isManifestLibrarySync()) {
                        handleLibraryManifestExchange(client, request, sessionId)
                    } else {
                        null
                    }
                    val response = libraryExchange?.response ?: run {
                        handleRequest(request, sessionId).also { response ->
                            writeFrameLogged(client, sessionId, "response", response)
                        }
                    }
                    val ack = waitForResponseAck(client, sessionId)
                    libraryExchange?.pendingSyncSuccess?.let { pending ->
                        markLibrarySyncSuccessAfterAck(pending, ack, sessionId)
                    }
                    WatchBluetoothDebugLog.event(
                        sessionId = sessionId,
                        event = "server.acceptOnce.complete",
                        fields = remoteFields(remoteName, remoteAddress) +
                            payloadFields("request", request) + payloadFields("response", response) +
                            mapOf("elapsedMs" to elapsedSince(totalStartedAt))
                    )
                    BluetoothSyncResult(
                        remoteName = remoteName,
                        remoteAddress = remoteAddress,
                        request = request,
                        response = response
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

    private suspend fun handleLibraryManifestExchange(
        client: BluetoothSocket,
        request: JSONObject,
        sessionId: String
    ): LibraryManifestExchangeResult {
        val startedAt = SystemClock.elapsedRealtime()
        WatchBluetoothDebugLog.event(
            sessionId = sessionId,
            event = "library.manifest.handle.start",
            fields = payloadFields("manifestRequest", request)
        )
        return runCatching {
            if (allowedActions != null && BluetoothSyncProtocol.ACTION_SYNC_LIBRARY !in allowedActions) {
                error("当前前台自动同步只支持资料库同步，请在手表上打开对应连接页面")
            }
            val app = context.applicationContext as WatchRssApplication
            val localDeviceId = WatchDeviceIdentity(context).deviceId
            val remoteDeviceId = request.optString("deviceId").ifBlank { "phone" }
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
                localDeviceId = localDeviceId
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
            }
            writeFrameLogged(client, sessionId, "manifestResponse", manifestResponse)

            val chunkedArticleRequests = if (supportsChunkedBodies) {
                readChunkedArticleRequestFrames(client, sessionId)
            } else {
                null
            }
            val articleRequestFrames = if (supportsChunkedBodies) {
                emptyList()
            } else {
                readArticleRequestFrames(client, sessionId)
            }
            val incoming = if (supportsChunkedBodies) {
                chunkedArticleRequests?.articles.orEmpty()
            } else {
                articleRequestFrames.flatMap { LibrarySyncPayload.parseArticles(it) }
            }
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.articles.parsed",
                fields = (chunkedArticleRequests?.stats?.fields("articlesRequest")
                    ?: batchFields("articlesRequest", articleRequestFrames)) +
                    mapOf("incomingArticles" to incoming.size)
            )
            val stats = if (supportsChunkedBodies) {
                app.container.rssRepository.mergeSyncedChunkedArticles(
                    articles = incoming.filterIsInstance<com.lightningstudio.watchrss.data.rss.SyncedChunkedArticle>(),
                    remoteDeviceId = remoteDeviceId,
                    localDeviceId = localDeviceId
                )
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
                    "incomingArticles" to incoming.size,
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
                LibrarySyncPayload.buildChunkedResponseFrames(
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
                            mapOf("elapsedMs" to elapsedSince(framesStartedAt))
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
                "library manifest exchange complete incoming=${incoming.size} outgoing=${outgoingDiff.size} sources=${outgoingWindow.rssSources.size}"
            )
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.manifest.handle.complete",
                fields = batchFields("libraryResponse", responseFrames) + payloadFields("combinedLibraryResponse", response) + mapOf(
                    "incomingArticles" to incoming.size,
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
                    fullSnapshot = outgoingWindow.fullSnapshot || remoteChangeSequence.fullSnapshot,
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

    private fun readArticleRequestFrames(
        client: BluetoothSocket,
        sessionId: String
    ): List<JSONObject> {
        val first = readFrameLogged(client, sessionId, "articlesRequest")
        val batchCount = LibrarySyncPayload.validateArticleRequestFrame(
            frame = first,
            expectedBatchIndex = 0
        )
        val frames = mutableListOf(first)
        while (frames.size < batchCount) {
            val index = frames.size
            val frame = readFrameLogged(client, sessionId, batchLabel("articlesRequest", index, batchCount))
            LibrarySyncPayload.validateArticleRequestFrame(
                frame = frame,
                expectedBatchIndex = index,
                expectedBatchCount = batchCount
            )
            frames += frame
        }
        return frames
    }

    private fun readChunkedArticleRequestFrames(
        client: BluetoothSocket,
        sessionId: String
    ): ChunkedArticleRequestFrames {
        val first = readFrameLogged(client, sessionId, "articlesRequest")
        val batchCount = LibrarySyncPayload.validateArticleRequestFrame(
            frame = first,
            expectedBatchIndex = 0
        )
        val byArticleId = linkedMapOf<String, SyncedChunkedArticle>()
        val bodyRequests = mutableListOf<SyncedArticleBodyRequest>()
        val stats = ArticleRequestBatchStats()

        fun consume(frame: JSONObject) {
            stats.add(frame)
            LibrarySyncPayload.parseChunkedArticles(frame).forEach { payload ->
                byArticleId.mergeChunkedArticle(payload)
            }
            bodyRequests += LibrarySyncPayload.parseBodyRequests(frame)
        }

        consume(first)
        var frameCount = 1
        while (frameCount < batchCount) {
            val frame = readFrameLogged(
                client,
                sessionId,
                batchLabel("articlesRequest", frameCount, batchCount)
            )
            LibrarySyncPayload.validateArticleRequestFrame(
                frame = frame,
                expectedBatchIndex = frameCount,
                expectedBatchCount = batchCount
            )
            consume(frame)
            frameCount += 1
        }
        return ChunkedArticleRequestFrames(
            articles = byArticleId.values.toList(),
            bodyRequests = bodyRequests,
            stats = stats
        )
    }

    private fun MutableMap<String, SyncedChunkedArticle>.mergeChunkedArticle(
        payload: SyncedChunkedArticle
    ) {
        val existing = this[payload.article.articleId]
        this[payload.article.articleId] = if (existing == null) {
            payload
        } else {
            require(
                existing.bodyHash == payload.bodyHash &&
                    existing.chunkSize == payload.chunkSize &&
                    existing.chunkHashes == payload.chunkHashes &&
                    existing.metadataOnly == payload.metadataOnly
            ) {
                "同步正文分块元数据冲突：${payload.article.articleId}"
            }
            existing.copy(
                article = payload.article,
                chunks = (existing.chunks + payload.chunks)
                    .distinctBy { it.index }
                    .sortedBy { it.index }
            )
        }
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

    private suspend fun waitForResponseAck(client: BluetoothSocket, sessionId: String): BluetoothSyncAck? {
        val startedAt = SystemClock.elapsedRealtime()
        WatchBluetoothDebugLog.event(
            sessionId = sessionId,
            event = "ack.read.start",
            fields = mapOf("timeoutMs" to RESPONSE_ACK_TIMEOUT_MS)
        )
        val ack = coroutineScope {
            val timeoutJob = launch {
                delay(RESPONSE_ACK_TIMEOUT_MS)
                runCatching {
                    client.close()
                }.onSuccess {
                    WatchBluetoothDebugLog.warn(
                        sessionId = sessionId,
                        event = "ack.read.timeout.closed",
                        fields = mapOf("elapsedMs" to elapsedSince(startedAt))
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
        val parsedAck = BluetoothSyncProtocol.parseAck(ack)
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
                error("当前前台自动同步只支持资料库同步，请在手表上打开对应连接页面")
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

                BluetoothSyncProtocol.ACTION_SYNC_LIBRARY -> {
                    val localDeviceId = WatchDeviceIdentity(context).deviceId
                    if (request.optString("phase") == LibrarySyncPayload.PHASE_PROBE) {
                        WatchBluetoothDebugLog.event(
                            sessionId = sessionId,
                            event = "request.syncLibrary.probe",
                            fields = mapOf("remoteDeviceId" to request.optString("deviceId"))
                        )
                        return@runCatching LibrarySyncPayload.buildProbeResponse(localDeviceId)
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

    private fun readFrameLogged(
        client: BluetoothSocket,
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
        client: BluetoothSocket,
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
        val maxBytes = payloads.maxOfOrNull { payload ->
            runCatching { BluetoothSyncProtocol.encodedSize(payload) }.getOrDefault(0)
        } ?: 0
        return mapOf(
            "${prefix}FrameCount" to payloads.size,
            "${prefix}TotalBytes" to bytes,
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

    companion object {
        private const val TAG = "WatchRSS_BtSyncServer"
        private const val DEFAULT_TIMEOUT_MS = 120_000L
        private const val RESPONSE_ACK_TIMEOUT_MS = 10_000L
    }
}
