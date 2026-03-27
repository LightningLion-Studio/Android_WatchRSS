package com.lightningstudio.watchrss.sdk.bili

class BiliAction(private val client: BiliClient) {
    suspend fun like(aid: Long, like: Boolean, bvid: String? = null): BiliResult<Unit> {
        val status = postWebAction(
            "${client.config.webBaseUrl}/x/web-interface/archive/like",
            mapOf(
                "aid" to aid.toString(),
                "like" to if (like) "1" else "2"
            ),
            aid = aid,
            bvid = bvid
        )
        return BiliResult(
            code = status.code,
            message = status.message,
            httpCode = status.httpCode,
            requestMode = status.requestMode
        )
    }

    suspend fun coin(
        aid: Long,
        multiply: Int = 1,
        selectLike: Boolean = false,
        bvid: String? = null
    ): BiliResult<Boolean> {
        val params = mapOf(
            "aid" to aid.toString(),
            "multiply" to multiply.toString(),
            "select_like" to if (selectLike) "1" else "0"
        )
        val status = postWebAction(
            "${client.config.webBaseUrl}/x/web-interface/coin/add",
            params,
            aid = aid,
            bvid = bvid
        )
        val dataObj = status.data?.asObjectOrNull()
        val likeResult = dataObj?.booleanOrNull("like") ?: false
        return BiliResult(
            code = status.code,
            message = status.message,
            data = likeResult,
            httpCode = status.httpCode,
            requestMode = status.requestMode
        )
    }

    suspend fun triple(aid: Long, bvid: String? = null): BiliResult<BiliTripleResult> {
        val params = mapOf("aid" to aid.toString())
        val status = postWebAction(
            "${client.config.webBaseUrl}/x/web-interface/archive/like/triple",
            params,
            aid = aid,
            bvid = bvid
        )
        val dataObj = status.data?.asObjectOrNull()
        val result = BiliTripleResult(
            like = dataObj?.booleanOrNull("like") ?: false,
            coin = dataObj?.booleanOrNull("coin") ?: false,
            fav = dataObj?.booleanOrNull("fav") ?: false
        )
        return BiliResult(
            code = status.code,
            message = status.message,
            data = result,
            httpCode = status.httpCode,
            requestMode = status.requestMode
        )
    }

    suspend fun favorite(
        aid: Long,
        addMediaIds: List<Long> = emptyList(),
        delMediaIds: List<Long> = emptyList(),
        bvid: String? = null
    ): BiliResult<Boolean> {
        val status = postWebAction(
            "${client.config.webBaseUrl}/medialist/gateway/coll/resource/deal",
            mapOf(
                "rid" to aid.toString(),
                "type" to "2",
                "add_media_ids" to addMediaIds.joinToString(","),
                "del_media_ids" to delMediaIds.joinToString(",")
            ),
            aid = aid,
            bvid = bvid
        )
        val dataObj = status.data?.asObjectOrNull()
        val prompt = dataObj?.booleanOrNull("prompt") ?: false
        return BiliResult(
            code = status.code,
            message = status.message,
            data = prompt,
            httpCode = status.httpCode,
            requestMode = status.requestMode
        )
    }

    private suspend fun postWebAction(
        url: String,
        params: Map<String, String>,
        aid: Long,
        bvid: String?
    ): BiliStatus {
        val csrf = client.csrfToken()
        if (csrf.isNullOrBlank()) {
            return BiliStatus(-111, "missing_csrf", requestMode = REQUEST_MODE_WEB)
        }
        val payload = params.toMutableMap()
        payload["csrf"] = csrf
        val response = client.httpClient.postForm(
            url,
            payload,
            headers = actionHeaders(aid = aid, bvid = bvid)
        )
        return parseBiliStatus(response, REQUEST_MODE_WEB)
    }

    private fun actionHeaders(aid: Long, bvid: String?): Map<String, String> {
        return mapOf(
            "Referer" to videoPageReferer(aid = aid, bvid = bvid),
            "Origin" to BiliBrowserProfile.DEFAULT_ORIGIN
        )
    }

    private fun videoPageReferer(aid: Long, bvid: String?): String {
        val videoId = bvid?.trim()?.takeIf { it.isNotEmpty() } ?: "av$aid"
        return "${client.config.webReferer.trimEnd('/')}/video/$videoId"
    }

    private companion object {
        private const val REQUEST_MODE_WEB = "web"
    }
}
