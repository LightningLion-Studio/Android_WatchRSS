package com.lightningstudio.watchrss.data.account

import com.lightningstudio.watchrss.BuildConfig
import java.net.URI

internal object BackendEndpointPolicy {
    fun requireSecure(raw: String, debugBuild: Boolean = BuildConfig.DEBUG): String {
        val normalized = raw.trim().trimEnd('/')
        require(normalized.isNotEmpty()) { "缺少账号后端地址" }
        val uri = runCatching { URI(normalized) }
            .getOrElse { throw IllegalArgumentException("账号后端地址无效", it) }
        require(!uri.isOpaque && uri.host != null && uri.userInfo == null) {
            "账号后端地址无效"
        }
        val secure = uri.scheme.equals("https", ignoreCase = true)
        val debugLoopback = debugBuild &&
            uri.scheme.equals("http", ignoreCase = true) &&
            uri.host.lowercase() in DEBUG_HTTP_HOSTS
        require(secure || debugLoopback) { "账号后端必须使用 HTTPS" }
        return normalized
    }

    private val DEBUG_HTTP_HOSTS = setOf(
        "localhost", "127.0.0.1", "::1", "[::1]", "10.0.2.2"
    )
}
