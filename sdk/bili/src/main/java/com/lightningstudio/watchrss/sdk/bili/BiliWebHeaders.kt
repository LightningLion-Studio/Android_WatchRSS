package com.lightningstudio.watchrss.sdk.bili

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object BiliWebHeaders {
    fun build(
        config: BiliSdkConfig,
        account: BiliAccount?,
        method: String,
        url: String,
        headers: Map<String, String>,
        includeCookies: Boolean
    ): Map<String, String> {
        val resolvedProfile = config.resolveWebBrowserProfile(account?.browserProfile)
        val normalizedMethod = method.uppercase()
        val context = resolveContext(url = url, method = normalizedMethod)
        val resolvedHeaders = linkedMapOf<String, String>()
        val requestReferer = resolveReferer(profile = resolvedProfile, context = context)
        val requestOrigin = resolveOrigin(profile = resolvedProfile, context = context)

        resolvedHeaders["User-Agent"] = resolvedProfile.userAgent
        resolvedHeaders["Accept-Language"] = resolvedProfile.acceptLanguage
        resolvedHeaders["Sec-CH-UA"] = resolvedProfile.secChUa
        resolvedHeaders["Sec-CH-UA-Mobile"] = resolvedProfile.secChUaMobile
        resolvedHeaders["Sec-CH-UA-Platform"] = resolvedProfile.secChUaPlatform
        if (!context.omitReferer) {
            resolvedHeaders["Referer"] = requestReferer
        }

        when (context.kind) {
            RequestKind.DOCUMENT -> {
                resolvedHeaders["Accept"] = DOCUMENT_ACCEPT
                resolvedHeaders["Upgrade-Insecure-Requests"] = "1"
                resolvedHeaders["Sec-Fetch-Site"] = context.fetchSite
                resolvedHeaders["Sec-Fetch-Mode"] = "navigate"
                resolvedHeaders["Sec-Fetch-Dest"] = "document"
                if (context.fetchSite == "none") {
                    resolvedHeaders["Sec-Fetch-User"] = "?1"
                }
            }

            RequestKind.API -> {
                resolvedHeaders["Accept"] = API_ACCEPT
                resolvedHeaders["Sec-Fetch-Site"] = context.fetchSite
                resolvedHeaders["Sec-Fetch-Mode"] = "cors"
                resolvedHeaders["Sec-Fetch-Dest"] = "empty"
                if (normalizedMethod != "GET" && normalizedMethod != "HEAD") {
                    resolvedHeaders["Origin"] = requestOrigin
                }
            }
        }

        if (includeCookies) {
            val cookies = account?.cookies?.takeIf { it.isNotEmpty() }
            if (cookies != null) {
                resolvedHeaders["Cookie"] = cookies.entries.joinToString("; ") { (key, value) -> "$key=$value" }
            }
        }

        headers.forEach { (key, value) ->
            if (value.isBlank()) {
                resolvedHeaders.remove(key)
            } else {
                resolvedHeaders[key] = value
            }
        }
        return resolvedHeaders
    }

    private fun resolveContext(url: String, method: String): RequestContext {
        val httpUrl = url.toHttpUrlOrNull()
        val host = httpUrl?.host.orEmpty()
        val path = httpUrl?.encodedPath.orEmpty()
        val bvid = httpUrl?.queryParameter("bvid")
        val mid = httpUrl?.queryParameter("mid") ?: httpUrl?.queryParameter("vmid")
        val roomId = httpUrl?.queryParameter("room_id") ?: httpUrl?.queryParameter("id")
        val isDynamicEndpoint = path.contains("/x/polymer/web-dynamic/") || path.contains("/x/dynamic/")
        val isDocumentGet = method == "GET" &&
            host == "www.bilibili.com" &&
            !path.startsWith("/x/")
        if (isDocumentGet) {
            val fetchSite = if (path == "/" || path.isBlank()) "none" else "same-origin"
            return RequestContext(
                kind = RequestKind.DOCUMENT,
                fetchSite = fetchSite,
                referer = null,
                origin = null,
                omitReferer = false
            )
        }
        val fetchSite = when {
            host == "www.bilibili.com" -> "same-origin"
            host.endsWith(".bilibili.com") || host == "bilibili.com" -> "same-site"
            else -> "cross-site"
        }
        val referer = when {
            !bvid.isNullOrBlank() -> "https://www.bilibili.com/video/$bvid"
            path.contains("/x/space/") && !mid.isNullOrBlank() -> "https://space.bilibili.com/$mid"
            host == "api.live.bilibili.com" && !roomId.isNullOrBlank() -> "https://live.bilibili.com/$roomId"
            host == "api.live.bilibili.com" -> "https://live.bilibili.com"
            isDynamicEndpoint -> "https://t.bilibili.com/"
            else -> null
        }
        val origin = when {
            host == "api.live.bilibili.com" -> "https://live.bilibili.com"
            isDynamicEndpoint -> "https://t.bilibili.com"
            else -> null
        }
        return RequestContext(
            kind = RequestKind.API,
            fetchSite = fetchSite,
            referer = referer,
            origin = origin,
            omitReferer = path.contains("/wbi/")
        )
    }

    private fun resolveReferer(
        profile: BiliBrowserProfile,
        context: RequestContext
    ): String = context.referer ?: profile.referer

    private fun resolveOrigin(
        profile: BiliBrowserProfile,
        context: RequestContext
    ): String = context.origin ?: profile.origin

    private data class RequestContext(
        val kind: RequestKind,
        val fetchSite: String,
        val referer: String?,
        val origin: String?,
        val omitReferer: Boolean
    )

    private enum class RequestKind {
        DOCUMENT,
        API
    }

    private const val API_ACCEPT = "application/json, text/plain, */*"
    private const val DOCUMENT_ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp," +
            "image/apng,*/*;q=0.8"
}
