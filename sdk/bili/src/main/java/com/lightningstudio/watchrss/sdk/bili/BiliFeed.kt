package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.JsonObject

class BiliFeed(private val client: BiliClient) {
    suspend fun fetchAppFeed(extraParams: Map<String, String> = emptyMap()): BiliResult<BiliFeedPage> {
        val url = "${client.config.appBaseUrl}/x/v2/feed/index"
        val defaults = mapOf(
            "fnval" to "272",
            "fnver" to "1",
            "qn" to "32"
        )
        val params = client.signedAppParams(defaults + extraParams)
        val response = client.httpClient.get(
            url,
            params = params,
            headers = mapOf("User-Agent" to client.config.appUserAgent)
        )
        val status = parseBiliStatus(response.body)
        if (status.code != 0) {
            return BiliResult(status.code, status.message)
        }
        val data = status.data?.asObjectOrNull() ?: return BiliResult(-1, "empty_data")
        val itemsArray = data.arrayOrNull("items")
        val items = itemsArray
            ?.mapNotNull { parseAppFeedItem(it.asObjectOrNull()) }
            ?: emptyList()
        return BiliResult(status.code, status.message, BiliFeedPage(items, BiliFeedSource.APP))
    }

    suspend fun fetchWebFeed(params: Map<String, String> = emptyMap()): BiliResult<BiliFeedPage> {
        val url = "${client.config.webBaseUrl}/x/web-interface/wbi/index/top/feed/rcmd"
        val signed = client.signedWbiParams(params)
        val response = client.httpClient.get(url, params = signed)
        val status = parseBiliStatus(response.body)
        if (status.code != 0) {
            return BiliResult(status.code, status.message)
        }
        val data = status.data?.asObjectOrNull() ?: return BiliResult(-1, "empty_data")
        val itemsArray = data.arrayOrNull("item")
        val items = itemsArray
            ?.mapNotNull { parseWebFeedItem(it.asObjectOrNull()) }
            ?: emptyList()
        return BiliResult(status.code, status.message, BiliFeedPage(items, BiliFeedSource.WEB))
    }

    suspend fun fetchDefaultFeed(
        appParams: Map<String, String> = emptyMap(),
        webParams: Map<String, String> = emptyMap()
    ): BiliResult<BiliFeedPage> {
        val appResult = fetchAppFeed(appParams)
        if (appResult.isSuccess && !appResult.data?.items.isNullOrEmpty()) {
            return appResult
        }
        return fetchWebFeed(webParams)
    }

    private fun parseAppFeedItem(obj: JsonObject?): BiliItem? {
        if (obj == null) return null
        val goto = obj.stringOrNull("goto") ?: obj.stringOrNull("card_goto")
        if (goto != "av") return null
        val playerArgs = obj.objOrNull("player_args")
        val aid = obj.longOrNull("param") ?: playerArgs?.longOrNull("aid")
        val cid = playerArgs?.longOrNull("cid")
        val duration = playerArgs?.intOrNull("duration") ?: obj.intOrNull("duration")
        val ownerName = obj.objOrNull("desc_button")?.stringOrNull("text")
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
}
