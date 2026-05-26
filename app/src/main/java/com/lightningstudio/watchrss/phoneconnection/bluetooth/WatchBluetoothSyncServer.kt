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
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.phoneconnection.SavedItemsSyncPayload
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException

data class BluetoothSyncResult(
    val remoteName: String,
    val remoteAddress: String,
    val request: JSONObject,
    val response: JSONObject
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
                    val response = if (request.isManifestLibrarySync()) {
                        handleLibraryManifestExchange(client, request, sessionId)
                    } else {
                        handleRequest(request, sessionId).also { response ->
                            writeFrameLogged(client, sessionId, "response", response)
                        }
                    }
                    waitForResponseAck(client, sessionId)
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
    ): JSONObject {
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
            val remoteManifest = LibrarySyncPayload.parseArticleManifest(request)
            val incomingSources = LibrarySyncPayload.parseRssSources(request)
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.manifest.parsed",
                fields = mapOf(
                    "remoteManifest" to remoteManifest.size,
                    "incomingSources" to incomingSources.size
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
                request.optInt("version") >= LibrarySyncPayload.PROTOCOL_VERSION
            val outgoingManifest = app.container.rssRepository.exportSyncedArticleManifests(localDeviceId)
            val outgoingSources = app.container.rssRepository.exportSyncedRssSources(localDeviceId)
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.local.exported",
                fields = mapOf(
                    "outgoingArticles" to outgoingManifest.size,
                    "outgoingSources" to outgoingSources.size,
                    "chunked" to supportsChunkedBodies
                )
            )
            val manifestResponse = if (supportsChunkedBodies) {
                val bodyRequests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
                    localManifest = outgoingManifest,
                    remoteManifest = remoteManifest
                )
                LibrarySyncPayload.buildManifestResponseFromEntries(
                    deviceId = localDeviceId,
                    articleManifest = outgoingManifest,
                    bodyRequests = bodyRequests,
                    rssSources = outgoingSources,
                    sourcesApplied = sourceStats.applied
                )
            } else {
                val outgoing = app.container.rssRepository.exportSyncedSavedArticles(localDeviceId)
                LibrarySyncPayload.buildManifestResponse(
                    deviceId = localDeviceId,
                    articles = outgoing,
                    rssSources = outgoingSources,
                    sourcesApplied = sourceStats.applied
                )
            }
            writeFrameLogged(client, sessionId, "manifestResponse", manifestResponse)

            val articleRequestFrames = readArticleRequestFrames(client, sessionId)
            val combinedArticleRequest = LibrarySyncPayload.combineArticlePayloads(articleRequestFrames)
            val incoming = if (supportsChunkedBodies) {
                LibrarySyncPayload.parseChunkedArticles(combinedArticleRequest)
            } else {
                articleRequestFrames.flatMap { LibrarySyncPayload.parseArticles(it) }
            }
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.articles.parsed",
                fields = batchFields("articlesRequest", articleRequestFrames) +
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
                val phoneRequests = LibrarySyncPayload.parseBodyRequests(combinedArticleRequest)
                val outgoingArticles = app.container.rssRepository.exportSyncedSavedArticlesForRequests(
                    deviceId = localDeviceId,
                    requests = phoneRequests
                )
                LibrarySyncPayload.buildChunkedResponseFrames(
                    deviceId = localDeviceId,
                    articles = outgoingArticles,
                    articleRequests = phoneRequests,
                    applied = stats.applied,
                    sourcesApplied = sourceStats.applied,
                    useBatches = remoteSupportsArticleBatches
                )
            } else {
                LibrarySyncPayload.buildResponseFrames(
                    deviceId = localDeviceId,
                    articles = outgoingDiff,
                    applied = stats.applied,
                    sourcesApplied = sourceStats.applied,
                    useBatches = remoteSupportsArticleBatches
                )
            }
            responseFrames.forEachIndexed { index, responseFrame ->
                writeFrameLogged(
                    client = client,
                    sessionId = sessionId,
                    label = batchLabel("libraryResponse", index, responseFrames.size),
                    payload = responseFrame
                )
            }
            val response = LibrarySyncPayload.combineArticlePayloads(responseFrames)
            Log.i(
                TAG,
                "library manifest exchange complete incoming=${incoming.size} outgoing=${outgoingDiff.size} sources=${outgoingSources.size}"
            )
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "library.manifest.handle.complete",
                fields = batchFields("libraryResponse", responseFrames) + payloadFields("combinedLibraryResponse", response) + mapOf(
                    "incomingArticles" to incoming.size,
                    "outgoingDiff" to outgoingDiff.size,
                    "outgoingSources" to outgoingSources.size,
                    "elapsedMs" to elapsedSince(startedAt)
                )
            )
            response
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
            }
        }
    }

    private fun readArticleRequestFrames(
        client: BluetoothSocket,
        sessionId: String
    ): List<JSONObject> {
        val first = readFrameLogged(client, sessionId, "articlesRequest")
        val batchCount = first.optInt("batchCount", 1).coerceAtLeast(1)
        val frames = mutableListOf(first)
        while (frames.size < batchCount) {
            val index = frames.size
            frames += readFrameLogged(client, sessionId, batchLabel("articlesRequest", index, batchCount))
        }
        return frames
    }

    private suspend fun waitForResponseAck(client: BluetoothSocket, sessionId: String) {
        val startedAt = SystemClock.elapsedRealtime()
        WatchBluetoothDebugLog.event(
            sessionId = sessionId,
            event = "ack.read.start",
            fields = mapOf("timeoutMs" to RESPONSE_ACK_TIMEOUT_MS)
        )
        val ack = withTimeoutOrNull(RESPONSE_ACK_TIMEOUT_MS) {
            runCatching {
                BluetoothSyncProtocol.readFrame(client.inputStream)
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
        if (ack?.optString("action") == BluetoothSyncProtocol.ACTION_ACK) {
            Log.i(TAG, "response ack received")
            WatchBluetoothDebugLog.event(
                sessionId = sessionId,
                event = "ack.read.success",
                fields = payloadFields("ack", ack) + mapOf("elapsedMs" to elapsedSince(startedAt))
            )
        } else {
            Log.w(TAG, "response ack missing before socket close")
            WatchBluetoothDebugLog.warn(
                sessionId = sessionId,
                event = "ack.read.missing",
                fields = mapOf("elapsedMs" to elapsedSince(startedAt))
            )
        }
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
                    val app = context.applicationContext as WatchRssApplication
                    val localDeviceId = WatchDeviceIdentity(context).deviceId
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
            put("${prefix}BodyRequestCount", payload.optJSONArray("bodyRequests")?.length())
            put("${prefix}RssSourceCount", payload.optJSONArray("rssSources")?.length())
            put("${prefix}ItemCount", payload.optJSONArray("items")?.length())
            put("${prefix}Count", if (payload.has("count")) payload.optInt("count") else null)
            put("${prefix}Applied", if (payload.has("applied")) payload.optInt("applied") else null)
            put("${prefix}SourcesApplied", if (payload.has("sourcesApplied")) payload.optInt("sourcesApplied") else null)
            put("${prefix}BatchIndex", if (payload.has("batchIndex")) payload.optInt("batchIndex") else null)
            put("${prefix}BatchCount", if (payload.has("batchCount")) payload.optInt("batchCount") else null)
            put("${prefix}TotalArticles", if (payload.has("totalArticles")) payload.optInt("totalArticles") else null)
        }
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
            optString("phase") == "manifest"
    }

    companion object {
        private const val TAG = "WatchRSS_BtSyncServer"
        private const val DEFAULT_TIMEOUT_MS = 120_000L
        private const val RESPONSE_ACK_TIMEOUT_MS = 10_000L
    }
}
