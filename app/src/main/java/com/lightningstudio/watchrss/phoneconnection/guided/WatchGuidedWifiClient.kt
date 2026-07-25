package com.lightningstudio.watchrss.phoneconnection.guided

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ActiveGuidedWifiConnection(
    val network: Network?,
    private val release: () -> Unit
) : AutoCloseable {
    override fun close() {
        release()
    }
}

class WatchGuidedWifiClient(
    context: Context
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    suspend fun connectToHotspot(
        ssid: String,
        passphrase: String,
        timeoutMs: Int = 45_000
    ): ActiveGuidedWifiConnection {
        if (passphrase.isBlank()) {
            return currentWifiConnectionIfConnectedTo(ssid)
                ?: error("手机和手表需要连接到同一个 WiFi")
        }

        currentWifiConnectionIfConnectedTo(ssid)?.let { connection ->
            Log.i(TAG, "already connected to ssid=$ssid, using current WiFi connection")
            return connection
        }

        return suspendCancellableCoroutine { continuation ->
            Log.i(TAG, "request WiFi ssid=$ssid timeoutMs=$timeoutMs")
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(passphrase)
                .build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "WiFi network available for ssid=$ssid")
                    if (!continuation.isCompleted) {
                        continuation.resume(
                            ActiveGuidedWifiConnection(network) {
                                runCatching { connectivityManager.unregisterNetworkCallback(this) }
                            }
                        )
                    }
                }

                override fun onUnavailable() {
                    Log.w(TAG, "WiFi network unavailable for ssid=$ssid")
                    if (!continuation.isCompleted) {
                        continuation.resumeWithException(IllegalStateException("手表未能连接到手机热点"))
                    }
                }
            }
            connectivityManager.requestNetwork(request, callback, timeoutMs)
            continuation.invokeOnCancellation {
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
        }
    }

    suspend fun fetchRemoteUrl(
        connection: ActiveGuidedWifiConnection,
        host: String,
        port: Int,
        token: String
    ): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "fetchRemoteUrl host=$host port=$port")
        val httpConnection = openConnection(connection.network, "http://$host:$port/pullRemoteInput?token=$token")
        httpConnection.useJson { json ->
            require(json.optBoolean("success", true)) { json.optString("message", "获取 RSS 失败") }
            json.optString("url").takeIf { it.isNotBlank() } ?: error("手机端没有待发送的 RSS 地址")
        }
    }

    suspend fun uploadSavedItems(
        connection: ActiveGuidedWifiConnection,
        host: String,
        port: Int,
        token: String,
        path: String,
        items: JSONArray
    ) = withContext(Dispatchers.IO) {
        Log.i(TAG, "uploadSavedItems host=$host port=$port path=$path count=${items.length()}")
        val httpConnection = openConnection(connection.network, "http://$host:$port/$path?token=$token")
        httpConnection.requestMethod = "POST"
        httpConnection.doOutput = true
        httpConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val payload = JSONObject().apply { put("items", items) }.toString()
        httpConnection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        httpConnection.useJson { json ->
            require(json.optBoolean("success", true)) { json.optString("message", "上传失败") }
        }
    }

    private fun openConnection(network: Network?, url: String): HttpURLConnection {
        val target = URL(url)
        val connection = if (network != null) {
            network.openConnection(target) as HttpURLConnection
        } else {
            target.openConnection() as HttpURLConnection
        }
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        return connection
    }

    private fun currentWifiConnectionIfConnectedTo(ssid: String): ActiveGuidedWifiConnection? {
        val connectedSsid = wifiManager.connectionInfo?.ssid?.trim('"')
        if (ssid.isNotBlank() && connectedSsid != ssid) {
            Log.i(TAG, "current WiFi ssid=$connectedSsid, target=$ssid")
            return null
        }

        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let(connectivityManager::getNetworkCapabilities)
        if (activeNetwork != null && capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            return ActiveGuidedWifiConnection(activeNetwork) {}
        }

        Log.i(TAG, "target WiFi is connected but no active WiFi Network handle; using default network")
        return ActiveGuidedWifiConnection(null) {}
    }

    private inline fun <T> HttpURLConnection.useJson(block: (JSONObject) -> T): T {
        return try {
            connect()
            Log.i(TAG, "HTTP ${url} responseCode=$responseCode")
            require(responseCode in 200..299) { "请求失败：$responseCode" }
            val body = inputStream.bufferedReader().use { it.readText() }
            block(JSONObject(body.ifBlank { "{}" }))
        } finally {
            disconnect()
        }
    }

    companion object {
        private const val TAG = "WatchRSS_GuidedWifi"
    }
}
