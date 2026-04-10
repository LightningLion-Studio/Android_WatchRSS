package com.lightningstudio.watchrss.sdk.douyin

import org.json.JSONArray
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
            val variants = parseVideoVariants(videoData)
            video.duration = videoData?.optInt("duration") ?: 0
            video.playUrl = videoData?.optJSONObject("play_addr")
                ?.optJSONArray("url_list")
                ?.optString(0)
                ?.takeIf { it.isNotBlank() }
                ?: variants.firstOrNull()?.playUrl
            video.coverUrl = videoData?.optJSONObject("cover")
                ?.optJSONArray("url_list")
                ?.optString(0)
            video.variants = variants

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
            val variants = parseVideoVariants(videoObj)
            val playUrl = firstNonBlankString(videoObj.optJSONObject("play_addr"), "url_list")
                ?: variants.firstOrNull()?.playUrl
                ?: throw JSONException("视频播放地址为空")
            val coverUrl = firstNonBlankString(videoObj.optJSONObject("cover"), "url_list").orEmpty()

            DouyinContent.Video(
                awemeId = awemeId,
                desc = desc,
                authorName = authorName,
                diggCount = diggCount,
                playUrl = playUrl,
                coverUrl = coverUrl,
                variants = variants
            )
        }
    }

    private fun parseVideoVariants(videoData: JSONObject?): List<DouyinVideoVariant> {
        if (videoData == null) return emptyList()
        val variants = linkedMapOf<String, DouyinVideoVariant>()
        val bitRateArray = videoData.optJSONArray("bit_rate")
        if (bitRateArray != null) {
            for (i in 0 until bitRateArray.length()) {
                val variant = parseVideoVariant(bitRateArray.optJSONObject(i)) ?: continue
                variants.putIfAbsent(variant.playUrl, variant)
            }
        }
        listOf(
            parseFallbackVariant(videoData, fieldName = "play_addr_h264", codec = DouyinVideoCodec.H264),
            parseFallbackVariant(videoData, fieldName = "play_addr_h265", codec = DouyinVideoCodec.H265),
            parseFallbackVariant(videoData, fieldName = "play_addr_bytevc1", codec = DouyinVideoCodec.H265),
            parseFallbackVariant(videoData, fieldName = "play_addr", codec = DouyinVideoCodec.UNKNOWN)
        ).forEach { variant ->
            if (variant != null) {
                variants.putIfAbsent(variant.playUrl, variant)
            }
        }
        return variants.values.toList()
    }

    private fun parseVideoVariant(item: JSONObject?): DouyinVideoVariant? {
        if (item == null) return null
        val playAddr = item.optJSONObject("play_addr") ?: return null
        val playUrl = firstNonBlankString(playAddr.optJSONArray("url_list")) ?: return null
        val videoExtra = item.optJSONObject("video_extra")
        val gearName = item.optString("gear_name").trim().takeIf { it.isNotEmpty() }
        val definition = videoExtra?.optString("definition")?.trim()?.takeIf { it.isNotEmpty() }
        val quality = videoExtra?.optString("quality")?.trim()?.takeIf { it.isNotEmpty() }
            ?: item.optString("quality_type").trim().takeIf { it.isNotEmpty() }
        return DouyinVideoVariant(
            playUrl = playUrl,
            codec = resolveCodec(
                isH265Flag = item.optInt("is_h265", 0) == 1,
                gearName = gearName,
                codecType = item.optString("codec_type").trim().takeIf { it.isNotEmpty() }
            ),
            bitrate = item.optLong("bit_rate"),
            width = playAddr.optInt("width"),
            height = playAddr.optInt("height"),
            definition = definition,
            quality = quality,
            gearName = gearName
        )
    }

    private fun parseFallbackVariant(
        videoData: JSONObject,
        fieldName: String,
        codec: DouyinVideoCodec
    ): DouyinVideoVariant? {
        val playAddr = videoData.optJSONObject(fieldName) ?: return null
        val playUrl = firstNonBlankString(playAddr.optJSONArray("url_list")) ?: return null
        return DouyinVideoVariant(
            playUrl = playUrl,
            codec = codec,
            bitrate = 0L,
            width = playAddr.optInt("width"),
            height = playAddr.optInt("height"),
            gearName = fieldName
        )
    }

    private fun resolveCodec(
        isH265Flag: Boolean,
        gearName: String?,
        codecType: String?
    ): DouyinVideoCodec {
        if (isH265Flag) return DouyinVideoCodec.H265
        val normalized = listOfNotNull(gearName, codecType)
            .joinToString(separator = " ")
            .lowercase()
        return when {
            normalized.contains("h264") ||
                normalized.contains("avc") ||
                normalized.contains("avc1") -> DouyinVideoCodec.H264
            normalized.contains("h265") ||
                normalized.contains("hevc") ||
                normalized.contains("hev1") ||
                normalized.contains("hvc1") ||
                normalized.contains("bvc1") ||
                normalized.contains("bytevc1") ->
                DouyinVideoCodec.H265
            else -> DouyinVideoCodec.UNKNOWN
        }
    }

    private fun firstNonBlankString(array: JSONArray?): String? {
        if (array == null) return null
        for (i in 0 until array.length()) {
            val candidate = array.optString(i).trim()
            if (candidate.isNotEmpty()) return candidate
        }
        return null
    }

    private fun firstNonBlankString(obj: JSONObject?, fieldName: String): String? {
        return firstNonBlankString(obj?.optJSONArray(fieldName))
    }
}
