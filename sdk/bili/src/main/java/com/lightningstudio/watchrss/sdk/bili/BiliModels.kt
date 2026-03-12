package com.lightningstudio.watchrss.sdk.bili

data class BiliOwner(
    val mid: Long? = null,
    val name: String? = null,
    val face: String? = null
)

data class BiliStat(
    val view: Long? = null,
    val like: Long? = null,
    val danmaku: Long? = null,
    val reply: Long? = null,
    val coin: Long? = null,
    val favorite: Long? = null,
    val share: Long? = null
)

data class BiliItem(
    val aid: Long? = null,
    val bvid: String? = null,
    val cid: Long? = null,
    val title: String? = null,
    val cover: String? = null,
    val duration: Int? = null,
    val pubdate: Long? = null,
    val owner: BiliOwner? = null,
    val stat: BiliStat? = null
)

data class BiliPage(
    val cid: Long? = null,
    val page: Int? = null,
    val part: String? = null,
    val duration: Int? = null
)

data class BiliVideoDetail(
    val item: BiliItem,
    val desc: String? = null,
    val pages: List<BiliPage> = emptyList()
)

enum class BiliFeedSource {
    APP,
    WEB
}

data class BiliFeedPage(
    val items: List<BiliItem>,
    val source: BiliFeedSource
)
