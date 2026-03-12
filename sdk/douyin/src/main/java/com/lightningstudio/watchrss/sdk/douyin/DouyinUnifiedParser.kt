package com.lightningstudio.watchrss.sdk.douyin

import org.json.JSONException
import org.json.JSONObject

class DouyinUnifiedParser {
    fun parseFeed(jsonString: String): List<DouyinVideo> {
        return parseFeedPage(jsonString).items
    }

    fun parseFeedPage(jsonString: String): DouyinFeedPage {
        val videoList: MutableList<DouyinVideo> = ArrayList()
        val root = JSONObject(jsonString)
        val awemeArray = root.optJSONArray("aweme_list") ?: return DouyinFeedPage(
            items = emptyList(),
            nextCursor = null,
            hasMore = false
        )

        for (i in 0 until awemeArray.length()) {
            val item = awemeArray.optJSONObject(i) ?: continue
            val video = DouyinVideo()

            video.awemeId = item.optString("aweme_id")
            video.desc = item.optString("desc")
            video.createTime = item.optLong("create_time")

            val author = item.optJSONObject("author")
            video.authorId = author?.optString("uid")
            video.authorName = author?.optString("nickname")
            video.authorAvatar = author?.optJSONObject("avatar_thumb")
                ?.optJSONArray("url_list")
                ?.optString(0)

            val stats = item.optJSONObject("statistics")
            video.likeCount = stats?.optLong("digg_count") ?: 0
            video.commentCount = stats?.optLong("comment_count") ?: 0
            video.shareCount = stats?.optLong("share_count") ?: 0
            video.collectCount = stats?.optLong("collect_count") ?: 0

            val videoData = item.optJSONObject("video")
            video.duration = videoData?.optInt("duration") ?: 0
            video.playUrl = videoData?.optJSONObject("play_addr")
                ?.optJSONArray("url_list")
                ?.optString(0)
            video.coverUrl = videoData?.optJSONObject("cover")
                ?.optJSONArray("url_list")
                ?.optString(0)

            if (!video.awemeId.isNullOrBlank() && !video.playUrl.isNullOrBlank()) {
                videoList.add(video)
            }
        }

        val nextCursor = root.optLong("max_cursor").takeIf { it > 0 }?.toString()
            ?: root.optString("cursor").trim().takeIf { !it.isNullOrBlank() }
        val hasMore = root.optInt("has_more", if (videoList.isEmpty()) 0 else 1) == 1
        return DouyinFeedPage(items = videoList, nextCursor = nextCursor, hasMore = hasMore)
    }

    fun parse(jsonString: String): DouyinContent {
        val root = JSONObject(jsonString)

        val awemeDetailValue = root.opt("aweme_detail")

        if (awemeDetailValue == null || awemeDetailValue !is JSONObject) {
            throw JSONException("请求视频失败，如果您确定awemeID没问题，请查看原始响应JSON")
        }

        val awemeDetail = awemeDetailValue as JSONObject
        val awemeId = awemeDetail.getString("aweme_id")
        val desc = awemeDetail.getString("desc")
        val authorName = awemeDetail.getJSONObject("author").getString("nickname")
        val diggCount = awemeDetail.getJSONObject("statistics").getLong("digg_count")

        val type = awemeDetail.getInt("aweme_type")

        return if (type == 68 || type == 150) {
            val imagesArray = awemeDetail.getJSONArray("images")
            val urls = mutableListOf<String>()

            for (i in 0 until imagesArray.length()) {
                val imgObj = imagesArray.getJSONObject(i)
                val imgUrl = imgObj.getJSONArray("url_list").getString(0)
                urls.add(imgUrl)
            }

            if (urls.isEmpty()) throw JSONException("Note type but no images found")

            DouyinContent.Note(awemeId, desc, authorName, diggCount, urls)
        } else {
            val videoObj = awemeDetail.getJSONObject("video")
            val playAddr = videoObj.getJSONObject("play_addr")
            val playUrl = playAddr.getJSONArray("url_list").getString(0)
            val coverUrl = videoObj.getJSONObject("cover").getJSONArray("url_list").getString(0)

            DouyinContent.Video(awemeId, desc, authorName, diggCount, playUrl, coverUrl)
        }
    }
}
