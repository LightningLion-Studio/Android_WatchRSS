package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.JsonObject

class BiliVideo(private val client: BiliClient) {
    suspend fun fetchView(
        aid: Long? = null,
        bvid: String? = null,
        useWbi: Boolean = true
    ): BiliResult<BiliVideoDetail> {
        if (aid == null && bvid.isNullOrBlank()) {
            return BiliResult(-1, "missing_id")
        }
        val params = mutableMapOf<String, String>()
        if (aid != null) params["aid"] = aid.toString()
        if (!bvid.isNullOrBlank()) params["bvid"] = bvid

        val signed = if (useWbi) client.signedWbiParams(params) else params
        val useSigned = signed.containsKey("w_rid")
        val url = if (useSigned && useWbi) {
            "${client.config.webBaseUrl}/x/web-interface/wbi/view"
        } else {
            "${client.config.webBaseUrl}/x/web-interface/view"
        }

        val response = client.httpClient.get(url, params = if (useSigned) signed else params)
        val status = parseBiliStatus(response.body)
        if (status.code != 0) {
            return BiliResult(status.code, status.message)
        }
        val data = status.data?.asObjectOrNull() ?: return BiliResult(-1, "empty_data")
        val detail = parseVideoDetail(data)
        return BiliResult(status.code, status.message, detail)
    }

    private fun parseVideoDetail(data: JsonObject): BiliVideoDetail {
        val ownerObj = data.objOrNull("owner")
        val statObj = data.objOrNull("stat")
        val item = BiliItem(
            aid = data.longOrNull("aid"),
            bvid = data.stringOrNull("bvid"),
            cid = data.longOrNull("cid"),
            title = data.stringOrNull("title"),
            cover = data.stringOrNull("pic"),
            duration = data.intOrNull("duration"),
            pubdate = data.longOrNull("pubdate"),
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
        val pages = data.arrayOrNull("pages")
            ?.mapNotNull { it.asObjectOrNull() }
            ?.map {
                BiliPage(
                    cid = it.longOrNull("cid"),
                    page = it.intOrNull("page"),
                    part = it.stringOrNull("part"),
                    duration = it.intOrNull("duration")
                )
            }
            ?: emptyList()
        return BiliVideoDetail(
            item = item,
            desc = data.stringOrNull("desc"),
            pages = pages
        )
    }
}
