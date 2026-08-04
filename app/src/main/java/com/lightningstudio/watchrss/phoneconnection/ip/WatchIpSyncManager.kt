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
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.phoneconnection.bluetooth.BluetoothSyncProtocol
import com.lightningstudio.watchrss.phoneconnection.bluetooth.WatchBluetoothSyncServer
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.supervisorScope
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
    private val cooldownUntil = ConcurrentHashMap<String, Long>()
    private var activeConnection: WatchIpSyncConnection? = null
    private var descriptor: IpEndpointDescriptor? = null
    private var resumedCount = 0
    private var stopJob: Job? = null
    private var transportJob: Job? = null

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
            stopJob?.cancel()
            stopJob = scope.launch {
                delay(STOP_GRACE_MS)
                if (resumedCount == 0) {
                    transportJob?.cancel()
                    transportJob = null
                    activeConnection?.close()
                    activeConnection = null
                }
            }
        }
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun scheduleRefresh(reason: String) {
        if (resumedCount <= 0 || !hasBluetoothPermissions()) return
        scope.launch {
            delay(NETWORK_DEBOUNCE_MS)
            refreshMutex.withLock { refresh(reason) }
        }
    }

    private suspend fun refresh(reason: String) {
        if (transferInProgress) {
            AppLogger.i(TAG, "IP route refresh deferred during active sync reason=$reason")
            return
        }
        val discovered = runCatching { bleDiscovery.discover() }
            .onFailure { AppLogger.w(TAG, "BLE 端点发现失败，保留已有通道", it) }
            .getOrNull()
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

        val successes = supervisorScope {
            candidates.map { endpoint ->
                async {
                    runCatching {
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
                        cooldownUntil[endpoint.endpointId] =
                            now() + IpSyncProtocol.FAILED_ENDPOINT_COOLDOWN_MS
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
        if (transferInProgress) {
            successes.forEach(WatchIpSyncConnection::close)
            AppLogger.i(TAG, "IP route switch deferred after probe because sync started")
            return
        }
        val selected = successes.maxByOrNull(::connectionPriority) ?: return
        successes.filter { it !== selected }.forEach(WatchIpSyncConnection::close)
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
                    BluetoothSyncProtocol.ACTION_SYNC_ACCOUNT
                ),
                onRequestReceived = { transferInProgress = true }
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
                    transferInProgress = false
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
                    runCatching {
                        (application as? WatchRssApplication)?.cloudSyncService?.syncNow()
                    }
                }
                scheduleRefresh("transfer-complete")
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
        private const val STOP_GRACE_MS = 2_500L
        private const val IP_IDLE_REQUEST_TIMEOUT_MS = 10 * 60 * 1_000L
    }
}
