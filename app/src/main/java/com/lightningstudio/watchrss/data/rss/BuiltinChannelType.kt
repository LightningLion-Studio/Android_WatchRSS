package com.lightningstudio.watchrss.data.rss

enum class BuiltinChannelType(
    val url: String,
    val title: String,
    val description: String,
    val useOriginalContentByDefault: Boolean
) {
    BILI("builtin:bili", "哔哩哔哩", "进入后登录获取推荐内容", true),
    DOUYIN("builtin:douyin", "抖音", "进入后登录获取推荐内容", true);

    companion object {
        fun fromUrl(url: String?): BuiltinChannelType? {
            if (url.isNullOrBlank()) return null
            return values().firstOrNull { it.url == url }
        }

        fun fromHost(host: String?): BuiltinChannelType? {
            val normalized = host?.lowercase() ?: return null
            return when {
                normalized.endsWith("bilibili.com") -> BILI
                normalized.endsWith("douyin.com") -> DOUYIN
                else -> null
            }
        }
    }
}
