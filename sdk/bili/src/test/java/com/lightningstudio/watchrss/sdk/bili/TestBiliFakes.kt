package com.lightningstudio.watchrss.sdk.bili

import okhttp3.Headers

class TestBiliAccountStore(initial: BiliAccount = BiliAccount()) : BiliAccountStore {
    private var account: BiliAccount? = initial

    override suspend fun read(): BiliAccount? = account

    override suspend fun write(account: BiliAccount) {
        this.account = account
    }

    override suspend fun update(transform: (BiliAccount) -> BiliAccount) {
        account = transform(account ?: BiliAccount())
    }
}

class TestBiliHttpService : BiliHttpService {
    data class RecordedRequest(
        val method: String,
        val url: String,
        val body: Map<String, String>,
        val headers: Map<String, String>,
        val includeCookies: Boolean
    )

    private data class QueuedResponse(
        val method: String,
        val urlContains: String,
        val response: BiliHttpResult
    )

    private val queuedResponses = ArrayDeque<QueuedResponse>()
    val recordedRequests = mutableListOf<RecordedRequest>()

    fun enqueue(
        method: String,
        urlContains: String,
        code: Int = 200,
        body: String = """{"code":0}""",
        headers: Headers = Headers.headersOf()
    ) {
        queuedResponses.addLast(
            QueuedResponse(
                method = method,
                urlContains = urlContains,
                response = BiliHttpResult(code = code, body = body, headers = headers)
            )
        )
    }

    override suspend fun get(
        url: String,
        params: Map<String, String>,
        headers: Map<String, String>,
        includeCookies: Boolean
    ): BiliHttpResult {
        val fullUrl = if (params.isEmpty()) {
            url
        } else {
            buildString {
                append(url)
                append('?')
                append(params.entries.joinToString("&") { "${it.key}=${it.value}" })
            }
        }
        recordedRequests += RecordedRequest("GET", fullUrl, emptyMap(), headers, includeCookies)
        return dequeue("GET", fullUrl)
    }

    override suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
        includeCookies: Boolean
    ): BiliHttpResult {
        recordedRequests += RecordedRequest("POST", url, form, headers, includeCookies)
        return dequeue("POST", url)
    }

    override suspend fun postJson(
        url: String,
        json: String,
        headers: Map<String, String>,
        includeCookies: Boolean
    ): BiliHttpResult {
        recordedRequests += RecordedRequest("POST_JSON", url, mapOf("json" to json), headers, includeCookies)
        return dequeue("POST_JSON", url)
    }

    private fun dequeue(method: String, url: String): BiliHttpResult {
        val next = queuedResponses.removeFirstOrNull()
            ?: error("No queued response for $method $url")
        check(next.method == method) {
            "Expected ${next.method} for ${next.urlContains}, got $method $url"
        }
        check(url.contains(next.urlContains)) {
            "Expected url containing '${next.urlContains}', got '$url'"
        }
        return next.response
    }
}
