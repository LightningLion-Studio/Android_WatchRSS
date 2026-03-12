package com.lightningstudio.watchrss.sdk.bili

class BiliHistory(private val client: BiliClient) {
    suspend fun fetchHistory(
        cursor: BiliHistoryCursor? = null,
        type: String? = null,
        ps: Int = 20
    ): BiliResult<BiliHistoryPage> {
        val params = mutableMapOf(
            "ps" to ps.toString()
        )
        cursor?.max?.let { params["max"] = it.toString() }
        cursor?.viewAt?.let { params["view_at"] = it.toString() }
        cursor?.business?.let { params["business"] = it }
        type?.let { params["type"] = it }

        val url = "${client.config.webBaseUrl}/x/web-interface/history/cursor"
        val response = client.httpClient.get(url, params = params)
        val status = parseBiliStatus(response.body)
        if (status.code != 0) {
            return BiliResult(status.code, status.message)
        }
        val data = status.data?.asObjectOrNull() ?: return BiliResult(-1, "empty_data")
        val cursorObj = data.objOrNull("cursor")
        val parsedCursor = cursorObj?.let {
            BiliHistoryCursor(
                max = it.longOrNull("max"),
                viewAt = it.longOrNull("view_at"),
                business = it.stringOrNull("business"),
                ps = it.intOrNull("ps")
            )
        }
        val list = data.arrayOrNull("list")
            ?.mapNotNull { it.asObjectOrNull() }
            ?.map { item ->
                val historyObj = item.objOrNull("history")
                val history = historyObj?.let {
                    BiliHistoryEntry(
                        oid = it.longOrNull("oid"),
                        bvid = it.stringOrNull("bvid"),
                        cid = it.longOrNull("cid"),
                        page = it.intOrNull("page"),
                        part = it.stringOrNull("part"),
                        business = it.stringOrNull("business")
                    )
                }
                BiliHistoryItem(
                    title = item.stringOrNull("title"),
                    cover = item.stringOrNull("cover"),
                    viewAt = item.longOrNull("view_at"),
                    duration = item.longOrNull("duration"),
                    progress = item.longOrNull("progress"),
                    authorName = item.stringOrNull("author_name"),
                    authorMid = item.longOrNull("author_mid"),
                    history = history
                )
            }
            ?: emptyList()
        return BiliResult(status.code, status.message, BiliHistoryPage(parsedCursor, list))
    }

    suspend fun fetchToView(): BiliResult<BiliToViewPage> {
        val url = "${client.config.webBaseUrl}/x/v2/history/toview"
        val response = client.httpClient.get(url)
        val status = parseBiliStatus(response.body)
        if (status.code != 0) {
            return BiliResult(status.code, status.message)
        }
        val data = status.data?.asObjectOrNull() ?: return BiliResult(-1, "empty_data")
        val items = data.arrayOrNull("list")
            ?.mapNotNull { it.asObjectOrNull() }
            ?.map { it.toBiliItem() }
            ?: emptyList()
        return BiliResult(
            status.code,
            status.message,
            BiliToViewPage(
                count = data.intOrNull("count") ?: 0,
                items = items
            )
        )
    }

    suspend fun addToView(aid: Long? = null, bvid: String? = null): BiliResult<Unit> {
        if (aid == null && bvid.isNullOrBlank()) {
            return BiliResult(-1, "missing_id")
        }
        val csrf = client.csrfToken()
        if (csrf.isNullOrBlank()) {
            return BiliResult(-1, "missing_csrf")
        }
        val params = mutableMapOf("csrf" to csrf)
        if (aid != null) params["aid"] = aid.toString()
        if (!bvid.isNullOrBlank()) params["bvid"] = bvid
        val url = "${client.config.webBaseUrl}/x/v2/history/toview/add"
        val response = client.httpClient.postForm(url, params)
        val status = parseBiliStatus(response.body)
        return BiliResult(status.code, status.message)
    }

    private fun kotlinx.serialization.json.JsonObject.toBiliItem(): BiliItem {
        val ownerObj = objOrNull("owner")
        val statObj = objOrNull("stat")
        return BiliItem(
            aid = longOrNull("aid"),
            bvid = stringOrNull("bvid"),
            cid = longOrNull("cid"),
            title = stringOrNull("title"),
            cover = stringOrNull("pic"),
            duration = intOrNull("duration"),
            pubdate = longOrNull("pubdate"),
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
