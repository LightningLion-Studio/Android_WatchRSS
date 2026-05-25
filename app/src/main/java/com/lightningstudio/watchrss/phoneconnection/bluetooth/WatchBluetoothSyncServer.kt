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
        requireBluetoothConnectPermission()
        val adapter = context.getSystemService(BluetoothManager::class.java)
            ?.adapter
            ?: error("此设备没有蓝牙适配器")
        require(adapter.isEnabled) { "蓝牙未开启" }

        val serverSocket = adapter.listenUsingRfcommWithServiceRecord(
            BluetoothSyncProtocol.SERVICE_NAME,
            BluetoothSyncProtocol.SERVICE_UUID
        )
        Log.i(TAG, "listening uuid=${BluetoothSyncProtocol.SERVICE_UUID} timeoutMs=$timeoutMs")
        return serverSocket.use { socket ->
            val bluetoothSocket = socket.acceptWithTimeout(timeoutMs)
            bluetoothSocket.use { client ->
                val remoteName = client.remoteDevice?.name.orEmpty()
                val remoteAddress = client.remoteDevice?.address.orEmpty()
                Log.i(TAG, "accepted from name=$remoteName address=$remoteAddress")
                onClientAccepted?.invoke()
                val request = BluetoothSyncProtocol.readFrame(client.inputStream)
                val response = handleRequest(request)
                BluetoothSyncProtocol.writeFrame(client.outputStream, response)
                waitForResponseAck(client)
                BluetoothSyncResult(
                    remoteName = remoteName,
                    remoteAddress = remoteAddress,
                    request = request,
                    response = response
                )
            }
        }
    }

    private suspend fun waitForResponseAck(client: BluetoothSocket) {
        val ack = withTimeoutOrNull(RESPONSE_ACK_TIMEOUT_MS) {
            runCatching {
                BluetoothSyncProtocol.readFrame(client.inputStream)
            }.getOrNull()
        }
        if (ack?.optString("action") == BluetoothSyncProtocol.ACTION_ACK) {
            Log.i(TAG, "response ack received")
        } else {
            Log.w(TAG, "response ack missing before socket close")
        }
    }

    private suspend fun handleRequest(request: JSONObject): JSONObject {
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
                    val incoming = LibrarySyncPayload.parseArticles(request)
                    val stats = app.container.rssRepository.mergeSyncedSavedArticles(
                        articles = incoming,
                        remoteDeviceId = remoteDeviceId,
                        localDeviceId = localDeviceId
                    )
                    val outgoing = app.container.rssRepository.exportSyncedSavedArticles(localDeviceId)
                    LibrarySyncPayload.buildResponse(
                        deviceId = localDeviceId,
                        articles = outgoing,
                        applied = stats.applied
                    )
                }

                else -> error("未知蓝牙同步动作：$action")
            }
        }.getOrElse { throwable ->
            Log.e(TAG, "handle request failed request=$request", throwable)
            JSONObject().apply {
                put("success", false)
                put("message", throwable.message.orEmpty())
            }
        }
    }

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

    companion object {
        private const val TAG = "WatchRSS_BtSyncServer"
        private const val DEFAULT_TIMEOUT_MS = 120_000L
        private const val RESPONSE_ACK_TIMEOUT_MS = 10_000L
    }
}
