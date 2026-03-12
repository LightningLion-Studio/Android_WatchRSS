package com.lightningstudio.watchrss.sdk.bili

class BiliFavorite(private val client: BiliClient) {
    suspend fun listFolders(
        upMid: Long,
        type: Int? = null,
        rid: Long? = null
    ): BiliResult<List<BiliFavoriteFolder>> {
        val params = mutableMapOf("up_mid" to upMid.toString())
        type?.let { params["type"] = it.toString() }
        rid?.let { params["rid"] = it.toString() }
        val url = "${client.config.webBaseUrl}/x/v3/fav/folder/created/list-all"
        val response = client.httpClient.get(url, params = params)
        val status = parseBiliStatus(response.body)
        if (status.code != 0) {
            return BiliResult(status.code, status.message)
        }
        val data = status.data?.asObjectOrNull() ?: return BiliResult(-1, "empty_data")
        val list = data.arrayOrNull("list")
            ?.mapNotNull { it.asObjectOrNull() }
            ?.map {
                BiliFavoriteFolder(
                    id = it.longOrNull("id"),
                    fid = it.longOrNull("fid"),
                    mid = it.longOrNull("mid"),
                    title = it.stringOrNull("title"),
                    mediaCount = it.intOrNull("media_count"),
                    attr = it.intOrNull("attr"),
                    favState = it.intOrNull("fav_state")
                )
            }
            ?: emptyList()
        return BiliResult(status.code, status.message, list)
    }

    suspend fun listResources(
        mediaId: Long,
        pn: Int = 1,
        ps: Int = 20,
        keyword: String? = null,
        order: String? = null,
        type: Int? = null
    ): BiliResult<BiliFavoritePage> {
        val params = mutableMapOf(
            "media_id" to mediaId.toString(),
            "pn" to pn.toString(),
            "ps" to ps.toString()
        )
        keyword?.let { params["keyword"] = it }
        order?.let { params["order"] = it }
        type?.let { params["type"] = it.toString() }
        val url = "${client.config.webBaseUrl}/x/v3/fav/resource/list"
        val response = client.httpClient.get(url, params = params)
        val status = parseBiliStatus(response.body)
        if (status.code != 0) {
            return BiliResult(status.code, status.message)
        }
        val data = status.data?.asObjectOrNull() ?: return BiliResult(-1, "empty_data")
        val info = data.objOrNull("info")
        val medias = data.arrayOrNull("medias")
            ?.mapNotNull { it.asObjectOrNull() }
            ?.map { item ->
                val upper = item.objOrNull("upper")
                BiliFavoriteMedia(
                    id = item.longOrNull("id"),
                    bvid = item.stringOrNull("bvid") ?: item.stringOrNull("bv_id"),
                    title = item.stringOrNull("title"),
                    cover = item.stringOrNull("cover"),
                    duration = item.intOrNull("duration"),
                    owner = upper?.let {
                        BiliOwner(
                            mid = it.longOrNull("mid"),
                            name = it.stringOrNull("name"),
                            face = it.stringOrNull("face")
                        )
                    },
                    pubtime = item.longOrNull("pubtime"),
                    favTime = item.longOrNull("fav_time")
                )
            }
            ?: emptyList()
        return BiliResult(
            status.code,
            status.message,
            BiliFavoritePage(
                mediaId = info?.longOrNull("id") ?: mediaId,
                title = info?.stringOrNull("title"),
                hasMore = data.booleanOrNull("has_more") ?: false,
                medias = medias
            )
        )
    }
}
