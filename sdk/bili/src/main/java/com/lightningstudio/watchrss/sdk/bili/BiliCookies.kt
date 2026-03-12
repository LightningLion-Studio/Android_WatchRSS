package com.lightningstudio.watchrss.sdk.bili

import okhttp3.Headers

object BiliCookies {
    fun parseSetCookieHeaders(headers: Headers): Map<String, String> {
        val result = mutableMapOf<String, String>()
        headers.values("Set-Cookie").forEach { header ->
            val pair = header.substringBefore(';')
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val name = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (name.isNotEmpty()) {
                    result[name] = value
                }
            }
        }
        return result
    }

    fun parseCookieHeader(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        raw.split(';').forEach { token ->
            val idx = token.indexOf('=')
            if (idx > 0) {
                val name = token.substring(0, idx).trim()
                val value = token.substring(idx + 1).trim()
                if (name.isNotEmpty()) {
                    result[name] = value
                }
            }
        }
        return result
    }

    fun merge(base: Map<String, String>, updates: Map<String, String>): Map<String, String> {
        if (updates.isEmpty()) return base
        val merged = base.toMutableMap()
        merged.putAll(updates)
        return merged
    }
}
