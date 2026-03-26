package com.lightningstudio.watchrss.sdk.bili

class BiliAction(private val client: BiliClient) {
    suspend fun like(aid: Long, like: Boolean): BiliResult<Unit> {
        val status = postWebAction(
            "${client.config.webBaseUrl}/x/web-interface/archive/like",
            mapOf(
                "aid" to aid.toString(),
                "like" to if (like) "1" else "2"
            )
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
        selectLike: Boolean = false
    ): BiliResult<Boolean> {
        val params = mapOf(
            "aid" to aid.toString(),
            "multiply" to multiply.toString(),
            "select_like" to if (selectLike) "1" else "0"
        )
        val status = postWebAction("${client.config.webBaseUrl}/x/web-interface/coin/add", params)
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

    suspend fun triple(aid: Long): BiliResult<BiliTripleResult> {
        val params = mapOf("aid" to aid.toString())
        val status = postWebAction("${client.config.webBaseUrl}/x/web-interface/archive/like/triple", params)
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
        delMediaIds: List<Long> = emptyList()
    ): BiliResult<Boolean> {
        val status = postWebAction(
            "${client.config.webBaseUrl}/medialist/gateway/coll/resource/deal",
            mapOf(
                "rid" to aid.toString(),
                "type" to "2",
                "add_media_ids" to addMediaIds.joinToString(","),
                "del_media_ids" to delMediaIds.joinToString(",")
            )
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

    private suspend fun postWebAction(url: String, params: Map<String, String>): BiliStatus {
        val csrf = client.csrfToken()
        if (csrf.isNullOrBlank()) {
            return BiliStatus(-111, "missing_csrf", requestMode = REQUEST_MODE_WEB)
        }
        val payload = params.toMutableMap()
        payload["csrf"] = csrf
        val response = client.httpClient.postForm(url, payload)
        return parseBiliStatus(response, REQUEST_MODE_WEB)
    }

    private companion object {
        private const val REQUEST_MODE_WEB = "web"
    }
}
