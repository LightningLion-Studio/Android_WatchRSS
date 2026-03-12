package com.lightningstudio.watchrss.sdk.bili

data class BiliFavoriteFolder(
    val id: Long? = null,
    val fid: Long? = null,
    val mid: Long? = null,
    val title: String? = null,
    val mediaCount: Int? = null,
    val attr: Int? = null,
    val favState: Int? = null
)

data class BiliFavoriteMedia(
    val id: Long? = null,
    val bvid: String? = null,
    val title: String? = null,
    val cover: String? = null,
    val duration: Int? = null,
    val owner: BiliOwner? = null,
    val pubtime: Long? = null,
    val favTime: Long? = null
)

data class BiliFavoritePage(
    val mediaId: Long? = null,
    val title: String? = null,
    val hasMore: Boolean = false,
    val medias: List<BiliFavoriteMedia> = emptyList()
)
