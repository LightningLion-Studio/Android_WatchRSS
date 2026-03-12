package com.lightningstudio.watchrss.data.douyin

data class DouyinStreamItem(
    val awemeId: String,
    val playUrl: String,
    val coverUrl: String?,
    val title: String?,
    val author: String?,
    val likeCount: Long
)
