package com.lightningstudio.watchrss.sdk.douyin

class DouyinVideo {
    var awemeId: String? = null
    var desc: String? = null
    var createTime: Long = 0

    var authorId: String? = null
    var authorName: String? = null
    var authorAvatar: String? = null

    var likeCount: Long = 0
    var commentCount: Long = 0
    var shareCount: Long = 0
    var collectCount: Long = 0

    var playUrl: String? = null
    var coverUrl: String? = null
    var duration: Int = 0

    override fun toString(): String {
        return String.format(
            "视频[%s]: %s | 作者: %s | 点赞: %d",
            awemeId,
            desc,
            authorName,
            likeCount
        )
    }
}

data class DouyinFeedPage(
    val items: List<DouyinVideo>,
    val nextCursor: String?,
    val hasMore: Boolean
)

sealed class DouyinContent {
    abstract val awemeId: String
    abstract val desc: String
    abstract val authorName: String
    abstract val diggCount: Long

    data class Video(
        override val awemeId: String,
        override val desc: String,
        override val authorName: String,
        override val diggCount: Long,
        val playUrl: String,
        val coverUrl: String
    ) : DouyinContent()

    data class Note(
        override val awemeId: String,
        override val desc: String,
        override val authorName: String,
        override val diggCount: Long,
        val imageUrls: List<String>
    ) : DouyinContent()
}
