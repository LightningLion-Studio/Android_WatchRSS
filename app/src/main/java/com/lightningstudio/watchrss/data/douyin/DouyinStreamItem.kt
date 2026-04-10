package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoVariant

data class DouyinStreamItem(
    val awemeId: String,
    val playUrl: String,
    val coverUrl: String?,
    val title: String?,
    val author: String?,
    val likeCount: Long,
    val playUrlResolvedAtMs: Long,
    val sourceOrigin: DouyinSourceOrigin,
    val durationMs: Long = 0L,
    val variants: List<DouyinVideoVariant> = emptyList()
)
