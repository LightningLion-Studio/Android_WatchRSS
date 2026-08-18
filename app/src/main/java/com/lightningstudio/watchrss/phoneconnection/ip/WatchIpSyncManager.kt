package com.lightningstudio.watchrss.phoneconnection.ip

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.phoneconnection.bluetooth.BluetoothSyncProtocol
import com.lightningstudio.watchrss.phoneconnection.bluetooth.WatchBluetoothSyncServer
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class WatchIpSyncManager(
    private val application: Application
) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager =
        application.getSystemService(ConnectivityManager::class.java)
    private val bleDiscovery = WatchIpBleEndpointDiscovery(application)
    private val nsdDiscovery = WatchIpNsdDiscovery(application)
    private val routeResolver = WatchIpRouteResolver(application)
    private val probe = WatchIpWebSocketProbe()
    private val watchDeviceId = WatchDeviceIdentity(application).deviceId
    private val refreshMutex = Mutex()
    private val mediaKeepAlive =
        (application as WatchRssApplication).syncMediaKeepAlive
    private val cooldownUntil = ConcurrentHashMap<String, Long>()
    private var activeConnection: WatchIpSyncConnection? = null
    private var descriptor: IpEndpointDescriptor? = null
    private var resumedCount = 0
    private var stopJob: Job? = null
    private var refreshJob: Job? = null
    private var transportJob: Job? = null
    private var mediaReleaseJob: Job? = null

    @Volatile
    private var transferInProgress = false

    @Volatile
    private var lastAckSeq = 0L

    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleRefresh("default-available")
        override fun onLost(network: Network) = scheduleRefresh("default-lost")
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            scheduleRefresh("default-capabilities")
    }
    private val localWifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleRefresh("wifi-available")
        override fun onLost(network: Network) = scheduleRefresh("wifi-lost")
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            scheduleRefresh("wifi-capabilities")
    }

    fun install() {
        application.registerActivityLifecycleCallbacks(this)
        runCatching { connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback) }
            .onFailure { AppLogger.w(TAG, "默认网络监听注册失败", it) }
        runCatching {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                localWifiCallback
            )
        }.onFailure { AppLogger.w(TAG, "本地 WiFi 监听注册失败", it) }
    }

    fun currentConnection(): WatchIpSyncConnection? = activeConnection

    fun offerEndpointDescriptor(json: JSONObject): Boolean {
        val offered = runCatching { IpEndpointDescriptor.fromJson(json) }.getOrNull() ?: return false
        if (!offered.verify()) return false
        descriptor = offered
        scheduleRefresh(RFCOMM_BOOTSTRAP_REASON)
        AppLogger.i(TAG, "IP endpoint accepted from RFCOMM bootstrap endpoints=${offered.endpoints.size}")
        return true
    }

    fun updateLastAckSequence(sequence: Long) {
        lastAckSeq = maxOf(lastAckSeq, sequence)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedCount += 1
        stopJob?.cancel()
        stopJob = null
        scheduleRefresh("foreground")
    }

    override fun onActivityPaused(activity: Activity) {
        resumedCount = (resumedCount - 1).coerceAtLeast(0)
        if (resumedCount == 0) {
            scheduleBackgroundStop(STOP_GRACE_MS)
        }
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun scheduleRefresh(reason: String) {
        if (!shouldScheduleIpRefresh(resumedCount, reason == RFCOMM_BOOTSTRAP_REASON) ||
            !hasBluetoothPermissions()
        ) return
        if (reason == RFCOMM_BOOTSTRAP_REASON) {
            refreshJob?.cancel()
        } else if (refreshJob?.isActive == true) {
            return
        }
        refreshJob = scope.launch {
            if (reason != RFCOMM_BOOTSTRAP_REASON) delay(NETWORK_DEBOUNCE_MS)
            refreshMutex.withLock { refresh(reason) }
        }
    }

    private suspend fun refresh(reason: String) {
        if (transferInProgress) {
            AppLogger.i(TAG, "IP route refresh deferred during active sync reason=$reason")
            return
        }
        val discovered = if (reason == RFCOMM_BOOTSTRAP_REASON && descriptor?.verify() == true) {
            null
        } else {
            runCatching { bleDiscovery.discover() }
                .onFailure {
                    if (it is CancellationException) throw it
                    AppLogger.w(TAG, "BLE 端点发现失败，保留已有通道", it)
                }
                .getOrNull()
        }
        if (discovered != null) descriptor = discovered
        var current = descriptor ?: return
        if (!current.verify()) {
            descriptor = null
            return
        }
        val mdns = runCatching { nsdDiscovery.discover(current.port) }
            .getOrDefault(emptyList())
        current = current.withAdditionalEndpoints(mdns)
        val previous = activeConnection
        val minimumPriority = if (previous?.descriptorEpoch == current.epoch) {
            previous.endpoint.priority
        } else {
            -1
        }
        val candidates = current.endpoints
            .filter {
                it.family == "ipv4" && it.priority > minimumPriority &&
                    cooldownUntil.getOrDefault(it.endpointId, 0L) <= now()
            }
            .sortedByDescending { it.priority }
            .take(IpSyncProtocol.MAX_PROBE_CANDIDATES)
        if (candidates.isEmpty()) return

        var selected: WatchIpSyncConnection? = null
        for (endpoint in candidates) {
            selected = runCatching {
                probe.connect(
                    descriptor = current,
                    endpoint = endpoint,
                    network = routeResolver.networkFor(endpoint.address),
                    watchDeviceId = watchDeviceId,
                    resumeSessionId = previous?.sessionId,
                    lastAckSeq = lastAckSeq,
                    onClosed = ::onConnectionClosed
                )
            }.onFailure {
                if (it is CancellationException) throw it
                cooldownUntil[endpoint.endpointId] =
                    now() + IpSyncProtocol.FAILED_ENDPOINT_COOLDOWN_MS
                AppLogger.w(
                    TAG,
                    "IP candidate failed kind=${endpoint.transportKind.wireName} " +
                        "endpoint=${endpoint.endpointId}",
                    it
                )
            }.getOrNull()
            if (selected != null) break
        }
        if (transferInProgress) {
            selected?.close()
            AppLogger.i(TAG, "IP route switch deferred after probe because sync started")
            return
        }
        selected ?: return
        if (previous != null && connectionPriority(previous) > connectionPriority(selected)) {
            selected.close()
            return
        }
        activeConnection = selected
        if (previous !== selected) previous?.close()
        startServing(selected)
        AppLogger.i(
            TAG,
            "IP route ready reason=$reason route=${selected.routeKind.wireName} " +
                "endpoint=${selected.endpoint.endpointId} resume=${previous != null}"
        )
    }

    private fun onConnectionClosed(connection: WatchIpSyncConnection) {
        if (activeConnection === connection) {
            activeConnection = null
            scheduleRefresh("socket-closed")
        }
    }

    private fun startServing(connection: WatchIpSyncConnection) {
        transportJob?.cancel()
        transportJob = scope.launch {
            val server = WatchBluetoothSyncServer(
                context = application,
                allowedActions = setOf(
                    BluetoothSyncProtocol.ACTION_SYNC_LIBRARY,
                    BluetoothSyncProtocol.ACTION_SYNC_READER,
                    BluetoothSyncProtocol.ACTION_PREVIEW_READER,
                    BluetoothSyncProtocol.ACTION_SYNC_ACCOUNT,
                    BluetoothSyncProtocol.ACTION_SYNC_LLM_TOKEN_USAGE
                ),
                onRequestReceived = { setTransferInProgress(true) },
                // WebSocket.send() only confirms that bytes entered OkHttp's queue. The phone can
                // still need minutes to drain a multi-megabyte response through the Bluetooth
                // proxy, so the RFCOMM-sized 10 second ACK window is not valid for this transport.
                responseAckTimeoutMs = IP_RESPONSE_ACK_TIMEOUT_MS
            )
            while (activeConnection === connection) {
                val result = runCatching {
                    server.serveStreams(
                        inputStream = connection.inputStream,
                        outputStream = connection.outputStream,
                        remoteName = "WatchRSS Phone",
                        remoteAddress = "ip:${connection.endpoint.address}",
                        closeTransport = connection::close,
                        initialRequestTimeoutMs = IP_IDLE_REQUEST_TIMEOUT_MS
                    )
                }.also {
                    setTransferInProgress(false)
                    if (resumedCount == 0) {
                        // A library sync can outlive the watch display timeout. Keep
                        // the route through the current request and allow the next
                        // protocol phase to arrive before reclaiming it.
                        scheduleBackgroundStop(BACKGROUND_TRANSFER_GRACE_MS)
                    }
                }.getOrElse { error ->
                    if (activeConnection === connection) {
                        AppLogger.w(TAG, "IP 同步数据通道失败", error)
                        activeConnection = null
                        connection.close()
                        scheduleRefresh("transport-failed")
                    }
                    return@launch
                }
                lastAckSeq += 1L
                if (result.request.optString("action") == BluetoothSyncProtocol.ACTION_SYNC_LIBRARY) {
                    if (BuildConfig.DEBUG) {
                        runCatching {
                            (application as? WatchRssApplication)?.cloudSyncService?.syncNow()
                        }
                    }
                }
                scheduleRefresh("transfer-complete")
            }
        }
    }

    private fun scheduleBackgroundStop(delayMs: Long) {
        stopJob?.cancel()
        stopJob = scope.launch {
            delay(delayMs)
            if (shouldStopIpTransport(resumedCount, transferInProgress)) {
                transportJob?.cancel()
                transportJob = null
                activeConnection?.close()
                activeConnection = null
            }
        }
    }

    private fun setTransferInProgress(value: Boolean) {
        transferInProgress = value
        mediaReleaseJob?.cancel()
        mediaReleaseJob = null
        if (value) {
            mediaKeepAlive.acquire(MEDIA_KEEP_ALIVE_OWNER)
        } else {
            // One manual sync is a sequence of library, reader-resource and token requests.
            // Retain the lease briefly between phases, then release it once the session is idle.
            mediaReleaseJob = scope.launch {
                delay(MEDIA_KEEP_ALIVE_IDLE_GRACE_MS)
                mediaKeepAlive.release(MEDIA_KEEP_ALIVE_OWNER)
            }
        }
    }

    private fun connectionPriority(connection: WatchIpSyncConnection): Int =
        maxOf(connection.routeKind.priority, connection.endpoint.priority)

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun now(): Long = android.os.SystemClock.elapsedRealtime()

    companion object {
        private const val TAG = "WatchRSS_IpSync"
        private const val NETWORK_DEBOUNCE_MS = 350L
        private const val RFCOMM_BOOTSTRAP_REASON = "rfcomm-bootstrap"
        private const val STOP_GRACE_MS = 2_500L
        private const val BACKGROUND_TRANSFER_GRACE_MS = 30_000L
        private const val IP_IDLE_REQUEST_TIMEOUT_MS = 10 * 60 * 1_000L
        private const val IP_RESPONSE_ACK_TIMEOUT_MS = 10 * 60 * 1_000L
        private const val MEDIA_KEEP_ALIVE_OWNER = "ip-sync"
        private const val MEDIA_KEEP_ALIVE_IDLE_GRACE_MS = 30_000L
    }
}

internal fun shouldStopIpTransport(resumedCount: Int, transferInProgress: Boolean): Boolean =
    resumedCount == 0 && !transferInProgress

internal fun shouldScheduleIpRefresh(resumedCount: Int, rfcommBootstrap: Boolean): Boolean =
    resumedCount > 0 || rfcommBootstrap
