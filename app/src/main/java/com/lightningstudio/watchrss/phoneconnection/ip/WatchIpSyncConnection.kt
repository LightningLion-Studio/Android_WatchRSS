package com.lightningstudio.watchrss.phoneconnection.ip

import okhttp3.WebSocket
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class WatchIpSyncConnection internal constructor(
    val endpoint: IpEndpointCandidate,
    val descriptorEpoch: Long,
    val sessionId: String,
    val routeKind: IpTransportKind,
    val acceptedResumeSeq: Long,
    private val webSocket: WebSocket
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val incoming = PipedInputStream(PIPE_BUFFER_BYTES)
    private val incomingWriter = PipedOutputStream(incoming)

    val inputStream: InputStream = incoming
    val outputStream: OutputStream = object : OutputStream() {
        override fun write(value: Int) {
            write(byteArrayOf(value.toByte()))
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            check(!closed.get()) { "IP 同步连接已关闭" }
            check(webSocket.send(bytes.copyOfRange(offset, offset + length).toByteString())) {
                "IP 同步发送队列已关闭"
            }
        }
    }

    internal fun acceptBinary(bytes: ByteArray) {
        if (!closed.get()) incomingWriter.write(bytes)
    }

    internal fun markClosed() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { incomingWriter.close() }
        runCatching { incoming.close() }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            webSocket.close(1000, "route replaced")
            runCatching { incomingWriter.close() }
            runCatching { incoming.close() }
        }
    }

    companion object {
        private const val PIPE_BUFFER_BYTES = 4 * 1024 * 1024
    }
}
