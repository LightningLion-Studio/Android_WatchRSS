package com.lightningstudio.watchrss.phoneconnection.ip

import android.net.Network
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class WatchIpWebSocketProbe {
    suspend fun connect(
        descriptor: IpEndpointDescriptor,
        endpoint: IpEndpointCandidate,
        network: Network?,
        watchDeviceId: String,
        resumeSessionId: String?,
        lastAckSeq: Long,
        onClosed: (WatchIpSyncConnection) -> Unit
    ): WatchIpSyncConnection {
        val result = CompletableDeferred<WatchIpSyncConnection>()
        val connectionRef = AtomicReference<WatchIpSyncConnection?>()
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(IpSyncProtocol.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
        if (network != null) clientBuilder.socketFactory(network.socketFactory)
        val client = clientBuilder.build()
        val hello = IpSyncProtocol.buildHello(
            descriptor = descriptor,
            watchDeviceId = watchDeviceId,
            clientNonce = randomNonce(),
            resumeSessionId = resumeSessionId,
            lastAckSeq = lastAckSeq
        )
        val request = Request.Builder()
            .url("ws://${endpoint.address}:${descriptor.port}/sync")
            .header("X-WatchRSS-Protocol", IpSyncProtocol.VERSION.toString())
            .build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!webSocket.send(hello.toString())) {
                    result.completeExceptionally(IllegalStateException("HELLO 发送失败"))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (result.isCompleted) return
                runCatching {
                    val json = JSONObject(text)
                    if (json.optString("type") == IpSyncProtocol.TYPE_ERROR) {
                        error(json.optString("message", "手机拒绝 IP 连接"))
                    }
                    require(json.optString("type") == IpSyncProtocol.TYPE_HELLO_ACK) {
                        "手机未返回 HELLO_ACK"
                    }
                    val ack = IpHelloAck.fromJson(json)
                    require(IpSyncProtocol.verifyAck(descriptor, ack)) { "HELLO_ACK 认证失败" }
                    require(ack.acceptedResumeSeq == lastAckSeq) { "手机续传序号不一致" }
                    WatchIpSyncConnection(
                        endpoint = endpoint,
                        descriptorEpoch = descriptor.epoch,
                        sessionId = ack.sessionId,
                        routeKind = ack.routeKind,
                        acceptedResumeSeq = ack.acceptedResumeSeq,
                        webSocket = webSocket
                    ).also(connectionRef::set)
                }.onSuccess(result::complete)
                    .onFailure(result::completeExceptionally)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                connectionRef.get()?.acceptBinary(bytes.toByteArray())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val connection = connectionRef.getAndSet(null)
                connection?.markClosed()
                if (connection != null) onClosed(connection)
                if (!result.isCompleted) {
                    result.completeExceptionally(IllegalStateException("IP 握手已关闭：$code $reason"))
                }
                client.dispatcher.executorService.shutdown()
            }

            override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                val connection = connectionRef.getAndSet(null)
                connection?.markClosed()
                if (connection != null) onClosed(connection)
                if (!result.isCompleted) result.completeExceptionally(error)
                client.dispatcher.executorService.shutdown()
            }
        }
        val socket = client.newWebSocket(request, listener)
        return try {
            withTimeout(
                IpSyncProtocol.CONNECT_TIMEOUT_MS + IpSyncProtocol.HANDSHAKE_TIMEOUT_MS
            ) { result.await() }
        } catch (error: Throwable) {
            socket.cancel()
            client.dispatcher.executorService.shutdown()
            throw error
        }
    }

    companion object {
        private val random = SecureRandom()

        private fun randomNonce(): String = ByteArray(16).also(random::nextBytes).let {
            Base64.getUrlEncoder().encodeToString(it)
        }
    }
}
