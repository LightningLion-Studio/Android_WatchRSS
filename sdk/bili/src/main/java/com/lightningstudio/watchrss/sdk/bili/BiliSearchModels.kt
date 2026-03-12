package com.lightningstudio.watchrss.sdk.bili

// Hot Search Models
data class BiliHotSearchResponse(
    val list: List<BiliTrendingWord>
)

data class BiliTrendingWord(
    val keyword: String,
    val showName: String? = null,
    val icon: String? = null,
    val position: Int? = null
)

// Search Result Models
data class BiliSearchResponse(
    val numResults: Int? = null,
    val numPages: Int? = null,
    val page: Int? = null,
    val pageSize: Int? = null,
    val result: List<BiliSearchResultItem>? = null
)

sealed class BiliSearchResultItem {
    data class Video(val data: BiliSearchedVideo) : BiliSearchResultItem()
    data class User(val data: BiliSearchedUser) : BiliSearchResultItem()
    data class Media(val data: BiliSearchedMedia) : BiliSearchResultItem()
}

data class BiliSearchedVideo(
    val aid: Long? = null,
    val bvid: String? = null,
    val title: String? = null,
    val author: String? = null,
    val mid: Long? = null,
    val pic: String? = null,
    val description: String? = null,
    val duration: String? = null,
    val play: Long? = null,
    val danmaku: Long? = null,
    val pubdate: Long? = null,
    val tag: String? = null
)

data class BiliSearchedUser(
    val mid: Long? = null,
    val uname: String? = null,
    val usign: String? = null,
    val upic: String? = null,
    val fans: Long? = null,
    val videos: Int? = null,
    val officialVerify: BiliOfficialVerify? = null,
    val vip: BiliVipInfo? = null
)

data class BiliSearchedMedia(
    val mediaId: Long? = null,
    val seasonId: Long? = null,
    val title: String? = null,
    val cover: String? = null,
    val areas: String? = null,
    val styles: String? = null,
    val cv: String? = null,
    val desc: String? = null,
    val pubtime: Long? = null,
    val mediaScore: BiliMediaScore? = null
)

data class BiliOfficialVerify(
    val type: Int? = null,
    val desc: String? = null
)

data class BiliVipInfo(
    val type: Int? = null,
    val status: Int? = null,
    val vipDueDate: Long? = null,
    val label: BiliVipLabel? = null
)

data class BiliVipLabel(
    val text: String? = null,
    val labelTheme: String? = null
)

data class BiliMediaScore(
    val userCount: Int? = null,
    val score: Double? = null
)
