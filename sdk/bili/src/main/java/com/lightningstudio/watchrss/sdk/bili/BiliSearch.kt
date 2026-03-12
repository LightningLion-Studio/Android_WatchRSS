package com.lightningstudio.watchrss.sdk.bili

import android.util.Log
import kotlinx.serialization.json.JsonObject

class BiliSearch(private val client: BiliClient) {

    suspend fun getHotSearch(): BiliResult<BiliHotSearchResponse> {
        val url = "${client.config.webBaseUrl}/x/web-interface/wbi/search/square"
        val params = client.signedWbiParams(mapOf("limit" to "10"))

        Log.d("BiliSearch", "getHotSearch - URL: $url")
        Log.d("BiliSearch", "getHotSearch - Params: $params")

        val response = client.httpClient.get(url, params = params)

        Log.d("BiliSearch", "getHotSearch - Response code: ${response.code}")
        Log.d("BiliSearch", "getHotSearch - Response body: ${response.body.take(500)}")

        val status = parseBiliStatus(response.body)

        if (status.code != 0) {
            Log.e("BiliSearch", "getHotSearch - Failed with code: ${status.code}, message: ${status.message}")
            return BiliResult(status.code, status.message)
        }

        val data = status.data?.asObjectOrNull() ?: return BiliResult(-1, "empty_data")
        val trendingArray = data.arrayOrNull("trending")
        val list = trendingArray?.mapNotNull { parseTrendingWord(it.asObjectOrNull()) } ?: emptyList()

        Log.d("BiliSearch", "getHotSearch - Success, found ${list.size} trending words")
        return BiliResult(status.code, status.message, BiliHotSearchResponse(list))
    }

    suspend fun searchAll(keyword: String, page: Int): BiliResult<BiliSearchResponse> {
        val url = "${client.config.webBaseUrl}/x/web-interface/wbi/search/all/v2"
        val params = client.signedWbiParams(mapOf(
            "keyword" to keyword,
            "page" to page.toString(),
            "page_size" to "20"
        ))

        Log.d("BiliSearch", "searchAll - Keyword: $keyword, Page: $page")
        Log.d("BiliSearch", "searchAll - URL: $url")
        Log.d("BiliSearch", "searchAll - Params: $params")

        val response = client.httpClient.get(url, params = params)

        Log.d("BiliSearch", "searchAll - Response code: ${response.code}")
        Log.d("BiliSearch", "searchAll - Response body length: ${response.body.length}")
        Log.d("BiliSearch", "searchAll - Response body preview: ${response.body.take(1000)}")

        val status = parseBiliStatus(response.body)

        Log.d("BiliSearch", "searchAll - Status code: ${status.code}, message: ${status.message}")
        Log.d("BiliSearch", "searchAll - Full response body: ${response.body}")

        if (status.code != 0) {
            Log.e("BiliSearch", "searchAll - Failed with code: ${status.code}, message: ${status.message}")
            return BiliResult(status.code, status.message)
        }

        val data = status.data?.asObjectOrNull()
        if (data == null) {
            Log.e("BiliSearch", "searchAll - Empty data")
            return BiliResult(-1, "empty_data")
        }

        Log.d("BiliSearch", "searchAll - Data keys: ${data.keys}")

        val numResults = data.intOrNull("numResults")
        val numPages = data.intOrNull("numPages")
        val pageNum = data.intOrNull("page")
        val pageSize = data.intOrNull("pagesize")

        Log.d("BiliSearch", "searchAll - numResults: $numResults, numPages: $numPages, page: $pageNum, pageSize: $pageSize")

        val results = mutableListOf<BiliSearchResultItem>()

        // The result field is an array of objects with result_type and data fields
        val resultArray = data.arrayOrNull("result")
        Log.d("BiliSearch", "searchAll - Result array size: ${resultArray?.size}")

        resultArray?.forEach { resultItem ->
            val resultObj = resultItem.asObjectOrNull() ?: return@forEach
            val resultType = resultObj.stringOrNull("result_type")
            val dataArray = resultObj.arrayOrNull("data")

            Log.d("BiliSearch", "searchAll - Processing result_type: $resultType, data size: ${dataArray?.size}")

            when (resultType) {
                "video" -> {
                    dataArray?.forEach { item ->
                        parseSearchedVideo(item.asObjectOrNull())?.let {
                            results.add(BiliSearchResultItem.Video(it))
                        }
                    }
                }
                "bili_user" -> {
                    dataArray?.forEach { item ->
                        parseSearchedUser(item.asObjectOrNull())?.let {
                            results.add(BiliSearchResultItem.User(it))
                        }
                    }
                }
                "media_bangumi" -> {
                    dataArray?.forEach { item ->
                        parseSearchedMedia(item.asObjectOrNull())?.let {
                            results.add(BiliSearchResultItem.Media(it))
                        }
                    }
                }
                "media_ft" -> {
                    dataArray?.forEach { item ->
                        parseSearchedMedia(item.asObjectOrNull())?.let {
                            results.add(BiliSearchResultItem.Media(it))
                        }
                    }
                }
            }
        }

        Log.d("BiliSearch", "searchAll - Total results parsed: ${results.size}")

        return BiliResult(
            status.code,
            status.message,
            BiliSearchResponse(
                numResults = numResults,
                numPages = numPages,
                page = pageNum,
                pageSize = pageSize,
                result = results
            )
        )
    }

    private fun parseTrendingWord(obj: JsonObject?): BiliTrendingWord? {
        if (obj == null) return null
        return BiliTrendingWord(
            keyword = obj.stringOrNull("keyword") ?: return null,
            showName = obj.stringOrNull("show_name"),
            icon = obj.stringOrNull("icon"),
            position = obj.intOrNull("position")
        )
    }

    private fun parseSearchedVideo(obj: JsonObject?): BiliSearchedVideo? {
        if (obj == null) return null
        return BiliSearchedVideo(
            aid = obj.longOrNull("aid"),
            bvid = obj.stringOrNull("bvid"),
            title = obj.stringOrNull("title"),
            author = obj.stringOrNull("author"),
            mid = obj.longOrNull("mid"),
            pic = obj.stringOrNull("pic")?.let { "https:$it" },
            description = obj.stringOrNull("description"),
            duration = obj.stringOrNull("duration"),
            play = obj.longOrNull("play"),
            danmaku = obj.longOrNull("video_review"),
            pubdate = obj.longOrNull("pubdate"),
            tag = obj.stringOrNull("tag")
        )
    }

    private fun parseSearchedUser(obj: JsonObject?): BiliSearchedUser? {
        if (obj == null) return null
        return BiliSearchedUser(
            mid = obj.longOrNull("mid"),
            uname = obj.stringOrNull("uname"),
            usign = obj.stringOrNull("usign"),
            upic = obj.stringOrNull("upic")?.let { "https:$it" },
            fans = obj.longOrNull("fans"),
            videos = obj.intOrNull("videos"),
            officialVerify = obj.objOrNull("official_verify")?.let {
                BiliOfficialVerify(
                    type = it.intOrNull("type"),
                    desc = it.stringOrNull("desc")
                )
            },
            vip = obj.objOrNull("vip")?.let {
                BiliVipInfo(
                    type = it.intOrNull("type"),
                    status = it.intOrNull("status"),
                    vipDueDate = it.longOrNull("vipDueDate"),
                    label = it.objOrNull("label")?.let { label ->
                        BiliVipLabel(
                            text = label.stringOrNull("text"),
                            labelTheme = label.stringOrNull("label_theme")
                        )
                    }
                )
            }
        )
    }

    private fun parseSearchedMedia(obj: JsonObject?): BiliSearchedMedia? {
        if (obj == null) return null
        return BiliSearchedMedia(
            mediaId = obj.longOrNull("media_id"),
            seasonId = obj.longOrNull("season_id"),
            title = obj.stringOrNull("title"),
            cover = obj.stringOrNull("cover")?.let { "https:$it" },
            areas = obj.stringOrNull("areas"),
            styles = obj.stringOrNull("styles"),
            cv = obj.stringOrNull("cv"),
            desc = obj.stringOrNull("desc"),
            pubtime = obj.longOrNull("pubtime"),
            mediaScore = obj.objOrNull("media_score")?.let {
                BiliMediaScore(
                    userCount = it.intOrNull("user_count"),
                    score = it.doubleOrNull("score")
                )
            }
        )
    }
}
