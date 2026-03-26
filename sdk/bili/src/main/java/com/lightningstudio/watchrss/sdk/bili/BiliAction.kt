package com.lightningstudio.watchrss.sdk.bili

class BiliAction(private val client: BiliClient) {
    suspend fun like(aid: Long, like: Boolean, preferApp: Boolean = true): BiliResult<Unit> {
        val appAccess = if (preferApp) client.accessKey() else null
        val status = if (!appAccess.isNullOrBlank()) {
            postAppAction(
                "${client.config.appBaseUrl}/x/v2/view/like",
                mapOf(
                    "aid" to aid.toString(),
                    "like" to if (like) "0" else "1"
                )
            )
        } else {
            postWebAction(
                "${client.config.webBaseUrl}/x/web-interface/archive/like",
                mapOf(
                    "aid" to aid.toString(),
                    "like" to if (like) "1" else "2"
                )
            )
        }
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
        preferApp: Boolean = true
    ): BiliResult<Boolean> {
        val params = mapOf(
            "aid" to aid.toString(),
            "multiply" to multiply.toString(),
            "select_like" to if (selectLike) "1" else "0"
        )
        val status = if (preferApp && !client.accessKey().isNullOrBlank()) {
            postAppAction("${client.config.appBaseUrl}/x/v2/view/coin/add", params)
        } else {
            postWebAction("${client.config.webBaseUrl}/x/web-interface/coin/add", params)
        }
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

    suspend fun triple(aid: Long, preferApp: Boolean = true): BiliResult<BiliTripleResult> {
        val params = mapOf("aid" to aid.toString())
        val status = if (preferApp && !client.accessKey().isNullOrBlank()) {
            postAppAction("${client.config.appBaseUrl}/x/v2/view/like/triple", params)
        } else {
            postWebAction("${client.config.webBaseUrl}/x/web-interface/archive/like/triple", params)
        }
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
        preferApp: Boolean = true
    ): BiliResult<Boolean> {
        val params = mutableMapOf(
            "rid" to aid.toString(),
            "type" to "2",
            "add_media_ids" to addMediaIds.joinToString(","),
            "del_media_ids" to delMediaIds.joinToString(",")
        )
        val status = if (preferApp && !client.accessKey().isNullOrBlank()) {
            params["access_key"] = client.accessKey().orEmpty()
            postRawAction(
                "${client.config.webBaseUrl}/medialist/gateway/coll/resource/deal",
                params,
                requestMode = REQUEST_MODE_APP
            )
        } else {
            val csrf = client.csrfToken()
            if (csrf.isNullOrBlank()) {
                return BiliResult(-111, "missing_csrf", requestMode = REQUEST_MODE_WEB)
            }
            params["csrf"] = csrf
            postRawAction(
                "${client.config.webBaseUrl}/medialist/gateway/coll/resource/deal",
                params,
                requestMode = REQUEST_MODE_WEB
            )
        }
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

    private suspend fun postAppAction(url: String, params: Map<String, String>): BiliStatus {
        val signed = client.signedAppParams(params)
        val response = client.httpClient.postForm(
            url,
            signed,
            headers = mapOf("User-Agent" to client.config.appUserAgent)
        )
        return parseBiliStatus(response, REQUEST_MODE_APP)
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

    private suspend fun postRawAction(
        url: String,
        params: Map<String, String>,
        requestMode: String
    ): BiliStatus {
        val response = client.httpClient.postForm(url, params)
        return parseBiliStatus(response, requestMode)
    }

    private companion object {
        private const val REQUEST_MODE_APP = "app"
        private const val REQUEST_MODE_WEB = "web"
    }
}
