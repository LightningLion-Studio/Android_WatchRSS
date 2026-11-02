package com.lightningstudio.watchrss.sdk.douyin

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/** Process-session fuse shared by API, foreground playback and background downloads.
 * Three forbidden responses in 30 seconds latch it until process restart. Successes from
 * unrelated API requests must not reset CDN failures. No cookie or login operation resets it.
 */
class DouyinTrafficGate(private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val lock = Any()
    private val forbiddenAt = ArrayDeque<Long>()
    private val calls = linkedSetOf<Call>()
    private val mutableStopped = MutableStateFlow(false)
    val stopped: StateFlow<Boolean> = mutableStopped

    fun checkAllowed() {
        if (stopped.value) throw IOException("抖音请求已暂停")
    }

    fun register(call: Call) = synchronized(lock) {
        checkAllowed()
        calls.add(call)
        Unit
    }

    fun unregister(call: Call) = synchronized(lock) { calls.remove(call); Unit }

    fun recordStatus(code: Int) {
        if (code != 403) return
        val cancel = synchronized(lock) {
            if (mutableStopped.value) return
            val now = nowMs()
            while (forbiddenAt.isNotEmpty() && now - forbiddenAt.first() > 30_000) forbiddenAt.removeFirst()
            forbiddenAt.addLast(now)
            if (forbiddenAt.size < 3) return
            mutableStopped.value = true
            calls.toList()
        }
        cancel.forEach(Call::cancel)
    }
}

object DouyinSessionTraffic {
    val gate = DouyinTrafficGate()
}

class DouyinTrafficInterceptor(private val gate: DouyinTrafficGate = DouyinSessionTraffic.gate) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        gate.register(chain.call())
        return try {
            val response = chain.proceed(chain.request())
            gate.recordStatus(response.code)
            val body = response.body
            if (body == null) {
                gate.unregister(chain.call())
                response
            } else response.newBuilder().body(object : ResponseBody() {
                private val source = object : ForwardingSource(body.source()) {
                    override fun close() {
                        try { super.close() } finally { gate.unregister(chain.call()) }
                    }
                }.buffer()
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source(): BufferedSource = source
            }).build()
        } catch (error: Throwable) {
            gate.unregister(chain.call())
            throw error
        }
    }
}
