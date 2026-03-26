package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.JsonObject

class BiliFeed(private val client: BiliClient) {
    suspend fun fetchAppFeed(extraParams: Map<String, String> = emptyMap()): BiliResult<BiliFeedPage> {
        val url = "${client.config.appBaseUrl}/x/v2/feed/index"
        val defaults = mutableMapOf(
            "fnval" to "272",
            "fnver" to "1",
            "qn" to "32"
        )
        val accessKey = client.accessKey()
        if (!accessKey.isNullOrBlank()) {
            // `/x/v2/feed/index` uses `accessKey` to unlock personalized recommendations.
            defaults["accessKey"] = accessKey
        }
        val params = client.signedAppParams(
            defaults + extraParams,
            includeAccessKey = false
        )
        val response = client.httpClient.get(
            url,
            params = params,
            headers = mapOf("User-Agent" to client.config.appUserAgent)
        )
        val status = parseBiliStatus(response, REQUEST_MODE_APP)
        if (status.code != 0) {
            return BiliResult(
                code = status.code,
                message = status.message,
                httpCode = status.httpCode,
                requestMode = status.requestMode
            )
        }
        val data = status.data?.asObjectOrNull() ?: return BiliResult(
            code = -1,
            message = "empty_data",
            httpCode = status.httpCode,
            requestMode = status.requestMode
        )
        val itemsArray = data.arrayOrNull("items")
        val items = itemsArray
            ?.mapNotNull { parseAppFeedItem(it.asObjectOrNull()) }
            ?: emptyList()
        return BiliResult(
            code = status.code,
            message = status.message,
            data = BiliFeedPage(items, BiliFeedSource.APP),
            httpCode = status.httpCode,
            requestMode = status.requestMode
        )
    }

    suspend fun fetchWebFeed(params: Map<String, String> = emptyMap()): BiliResult<BiliFeedPage> {
        val url = "${client.config.webBaseUrl}/x/web-interface/wbi/index/top/feed/rcmd"
        val signed = client.signedWbiParams(params)
        val response = client.httpClient.get(url, params = signed)
        val status = parseBiliStatus(response, REQUEST_MODE_WEB)
        if (status.code != 0) {
            return BiliResult(
                code = status.code,
                message = status.message,
                httpCode = status.httpCode,
                requestMode = status.requestMode
            )
        }
        val data = status.data?.asObjectOrNull() ?: return BiliResult(
            code = -1,
            message = "empty_data",
            httpCode = status.httpCode,
            requestMode = status.requestMode
        )
        val itemsArray = data.arrayOrNull("item")
        val items = itemsArray
            ?.mapNotNull { parseWebFeedItem(it.asObjectOrNull()) }
            ?: emptyList()
        return BiliResult(
            code = status.code,
            message = status.message,
            data = BiliFeedPage(items, BiliFeedSource.WEB),
            httpCode = status.httpCode,
            requestMode = status.requestMode
        )
    }

    suspend fun fetchDefaultFeed(
        appParams: Map<String, String> = emptyMap(),
        webParams: Map<String, String> = emptyMap()
    ): BiliResult<BiliFeedPage> {
        val hasAccessKey = !client.accessKey().isNullOrBlank()
        val appResult = fetchAppFeed(appParams)
        if (appResult.isSuccess && !appResult.data?.items.isNullOrEmpty()) {
            return appResult
        }
        if (hasAccessKey) {
            return appResult
        }
        return fetchWebFeed(webParams)
    }

    private fun parseAppFeedItem(obj: JsonObject?): BiliItem? {
        if (obj == null) return null
        val goto = obj.stringOrNull("goto") ?: obj.stringOrNull("card_goto")
        if (goto != "av") return null
        val playerArgs = obj.objOrNull("player_args")
        val args = obj.objOrNull("args")
        val aid = obj.longOrNull("param") ?: playerArgs?.longOrNull("aid") ?: args?.longOrNull("aid")
        val cid = playerArgs?.longOrNull("cid")
        val duration = playerArgs?.intOrNull("duration") ?: obj.intOrNull("duration")
        val ownerName = obj.objOrNull("desc_button")?.stringOrNull("text")
            ?: args?.stringOrNull("up_name")
        val owner = if (!ownerName.isNullOrBlank()) BiliOwner(name = ownerName) else null
        return BiliItem(
            aid = aid,
            bvid = playerArgs?.stringOrNull("bvid") ?: obj.stringOrNull("bvid"),
            cid = cid,
            title = obj.stringOrNull("title"),
            cover = obj.stringOrNull("cover"),
            duration = duration,
            owner = owner
        )
    }

    private fun parseWebFeedItem(obj: JsonObject?): BiliItem? {
        if (obj == null) return null
        val goto = obj.stringOrNull("goto")
        if (goto != "av") return null
        val ownerObj = obj.objOrNull("owner")
        val statObj = obj.objOrNull("stat")
        return BiliItem(
            aid = obj.longOrNull("id"),
            bvid = obj.stringOrNull("bvid"),
            cid = obj.longOrNull("cid"),
            title = obj.stringOrNull("title"),
            cover = obj.stringOrNull("pic"),
            duration = obj.intOrNull("duration") ?: obj.intOrNull("duraion"),
            pubdate = obj.longOrNull("pubdate"),
            owner = ownerObj?.let {
                BiliOwner(
                    mid = it.longOrNull("mid"),
                    name = it.stringOrNull("name"),
                    face = it.stringOrNull("face")
                )
            },
            stat = statObj?.let {
                BiliStat(
                    view = it.longOrNull("view"),
                    like = it.longOrNull("like"),
                    danmaku = it.longOrNull("danmaku"),
                    reply = it.longOrNull("reply"),
                    coin = it.longOrNull("coin"),
                    favorite = it.longOrNull("favorite"),
                    share = it.longOrNull("share")
                )
            }
        )
    }

    private companion object {
        private const val REQUEST_MODE_APP = "app"
        private const val REQUEST_MODE_WEB = "web"
    }
}
