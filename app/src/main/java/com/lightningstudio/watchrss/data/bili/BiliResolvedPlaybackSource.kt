package com.lightningstudio.watchrss.data.bili

data class BiliResolvedPlaybackSource(
    val cid: Long,
    val url: String,
    val headers: Map<String, String>,
    val cacheKey: String,
    val quality: Int,
    val detailPreviewBytes: Long? = null
)
