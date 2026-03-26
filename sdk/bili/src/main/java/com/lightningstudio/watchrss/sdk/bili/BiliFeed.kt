package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.JsonObject

class BiliFeed(private val client: BiliClient) {
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

    suspend fun fetchDefaultFeed(params: Map<String, String> = emptyMap()): BiliResult<BiliFeedPage> {
        return fetchWebFeed(params)
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
        private const val REQUEST_MODE_WEB = "web"
    }
}
