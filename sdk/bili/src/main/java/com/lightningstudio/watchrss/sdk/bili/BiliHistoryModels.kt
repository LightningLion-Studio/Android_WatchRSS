package com.lightningstudio.watchrss.sdk.bili

data class BiliHistoryCursor(
    val max: Long? = null,
    val viewAt: Long? = null,
    val business: String? = null,
    val ps: Int? = null
)

data class BiliHistoryEntry(
    val oid: Long? = null,
    val bvid: String? = null,
    val cid: Long? = null,
    val page: Int? = null,
    val part: String? = null,
    val business: String? = null
)

data class BiliHistoryItem(
    val title: String? = null,
    val cover: String? = null,
    val viewAt: Long? = null,
    val duration: Long? = null,
    val progress: Long? = null,
    val authorName: String? = null,
    val authorMid: Long? = null,
    val history: BiliHistoryEntry? = null
)

data class BiliHistoryPage(
    val cursor: BiliHistoryCursor? = null,
    val items: List<BiliHistoryItem> = emptyList()
)

data class BiliToViewPage(
    val count: Int = 0,
    val items: List<BiliItem> = emptyList()
)
