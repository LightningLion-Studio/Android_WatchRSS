package com.lightningstudio.watchrss.testutil

import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.sdk.bili.BiliCommentContent
import com.lightningstudio.watchrss.sdk.bili.BiliCommentCursor
import com.lightningstudio.watchrss.sdk.bili.BiliCommentData
import com.lightningstudio.watchrss.sdk.bili.BiliCommentMember
import com.lightningstudio.watchrss.sdk.bili.BiliCommentPage
import com.lightningstudio.watchrss.sdk.bili.BiliCommentReplyPage
import com.lightningstudio.watchrss.sdk.bili.BiliFavoriteFolder
import com.lightningstudio.watchrss.sdk.bili.BiliFavoriteMedia
import com.lightningstudio.watchrss.sdk.bili.BiliFavoritePage
import com.lightningstudio.watchrss.sdk.bili.BiliFeedPage
import com.lightningstudio.watchrss.sdk.bili.BiliFeedSource
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryEntry
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryItem
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryPage
import com.lightningstudio.watchrss.sdk.bili.BiliHotSearchResponse
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import com.lightningstudio.watchrss.sdk.bili.BiliOwner
import com.lightningstudio.watchrss.sdk.bili.BiliPage
import com.lightningstudio.watchrss.sdk.bili.BiliSearchResponse
import com.lightningstudio.watchrss.sdk.bili.BiliSearchResultItem
import com.lightningstudio.watchrss.sdk.bili.BiliStat
import com.lightningstudio.watchrss.sdk.bili.BiliToViewPage
import com.lightningstudio.watchrss.sdk.bili.BiliTrendingWord
import com.lightningstudio.watchrss.sdk.bili.BiliVideoDetail
import com.lightningstudio.watchrss.sdk.bili.BiliSearchedVideo
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.sdk.douyin.DouyinFeedPage
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo

fun sampleBiliBuiltinChannel(): RssChannel {
    return RssChannel(
        id = 1001L,
        url = "builtin:bili",
        title = "B 站",
        description = "Bili builtin",
        imageUrl = null,
        lastFetchedAt = null,
        sortOrder = 0,
        isPinned = false,
        useOriginalContent = true,
        unreadCount = 0
    )
}

fun sampleDouyinBuiltinChannel(): RssChannel {
    return RssChannel(
        id = 1002L,
        url = "builtin:douyin",
        title = "抖音",
        description = "Douyin builtin",
        imageUrl = null,
        lastFetchedAt = null,
        sortOrder = 1,
        isPinned = false,
        useOriginalContent = true,
        unreadCount = 0
    )
}

fun sampleBiliItem(
    aid: Long = 2233L,
    bvid: String = "BV1xx411c7mD",
    cid: Long = 4455L,
    title: String = "测试 B 站视频"
): BiliItem {
    return BiliItem(
        aid = aid,
        bvid = bvid,
        cid = cid,
        title = title,
        cover = "https://example.com/bili-cover.jpg",
        duration = 120,
        pubdate = 1_710_000_000L,
        owner = BiliOwner(mid = 7788L, name = "测试 UP"),
        stat = BiliStat(view = 100, like = 8, favorite = 3, share = 2)
    )
}

fun sampleBiliFeedPage(items: List<BiliItem> = listOf(sampleBiliItem())): BiliFeedPage {
    return BiliFeedPage(items = items, source = BiliFeedSource.APP)
}

fun sampleBiliVideoDetail(item: BiliItem = sampleBiliItem()): BiliVideoDetail {
    return BiliVideoDetail(
        item = item,
        desc = "这是一个用于自动化测试的 B 站详情",
        pages = listOf(BiliPage(cid = item.cid, page = 1, part = "P1", duration = item.duration))
    )
}

fun sampleBiliFavoriteFolders(): List<BiliFavoriteFolder> {
    return listOf(
        BiliFavoriteFolder(id = 1L, fid = 1L, mid = 7788L, title = "默认收藏夹", mediaCount = 1)
    )
}

fun sampleBiliFavoritePage(item: BiliItem = sampleBiliItem()): BiliFavoritePage {
    return BiliFavoritePage(
        mediaId = 1L,
        title = "默认收藏夹",
        medias = listOf(
            BiliFavoriteMedia(
                id = item.aid,
                bvid = item.bvid,
                title = item.title,
                cover = item.cover,
                duration = item.duration,
                owner = item.owner,
                pubtime = item.pubdate,
                favTime = item.pubdate
            )
        )
    )
}

fun sampleBiliToViewPage(item: BiliItem = sampleBiliItem()): BiliToViewPage {
    return BiliToViewPage(count = 1, items = listOf(item))
}

fun sampleBiliHistoryPage(item: BiliItem = sampleBiliItem()): BiliHistoryPage {
    return BiliHistoryPage(
        items = listOf(
            BiliHistoryItem(
                title = item.title,
                cover = item.cover,
                viewAt = 1_710_000_100L,
                duration = item.duration?.toLong(),
                progress = 15L,
                authorName = item.owner?.name,
                authorMid = item.owner?.mid,
                history = BiliHistoryEntry(
                    oid = item.aid,
                    bvid = item.bvid,
                    cid = item.cid
                )
            )
        )
    )
}

fun sampleBiliHotSearch(): BiliHotSearchResponse {
    return BiliHotSearchResponse(
        list = listOf(BiliTrendingWord(keyword = "测试关键词", showName = "测试关键词"))
    )
}

fun sampleBiliSearchResponse(item: BiliItem = sampleBiliItem()): BiliSearchResponse {
    return BiliSearchResponse(
        numResults = 1,
        numPages = 1,
        page = 1,
        result = listOf(
            BiliSearchResultItem.Video(
                BiliSearchedVideo(
                    aid = item.aid,
                    bvid = item.bvid,
                    title = item.title,
                    author = item.owner?.name,
                    mid = item.owner?.mid,
                    pic = item.cover
                )
            )
        )
    )
}

fun sampleBiliCommentPage(): BiliCommentPage {
    val comment = BiliCommentData(
        rpid = 1L,
        oid = 2233L,
        member = BiliCommentMember(mid = "7788", uname = "评论用户"),
        content = BiliCommentContent(message = "测试评论")
    )
    return BiliCommentPage(
        cursor = BiliCommentCursor(next = 0L, isEnd = true, mode = 3),
        replies = listOf(comment)
    )
}

fun sampleBiliReplyPage(): BiliCommentReplyPage {
    val root = BiliCommentData(
        rpid = 1L,
        oid = 2233L,
        member = BiliCommentMember(mid = "7788", uname = "楼主"),
        content = BiliCommentContent(message = "根评论")
    )
    val reply = BiliCommentData(
        rpid = 2L,
        oid = 2233L,
        root = 1L,
        member = BiliCommentMember(mid = "8899", uname = "回复用户"),
        content = BiliCommentContent(message = "测试回复")
    )
    return BiliCommentReplyPage(root = root, replies = listOf(reply))
}

fun sampleDouyinVideo(
    awemeId: String = "7357000000000000001",
    desc: String = "测试抖音视频"
): DouyinVideo {
    return DouyinVideo().apply {
        this.awemeId = awemeId
        this.desc = desc
        authorId = "douyin-author"
        authorName = "测试作者"
        authorAvatar = "https://example.com/avatar.jpg"
        likeCount = 12
        commentCount = 3
        shareCount = 2
        collectCount = 1
        playUrl = "https://example.com/douyin.mp4"
        coverUrl = "https://example.com/douyin-cover.jpg"
        duration = 18
    }
}

fun sampleDouyinFeedPage(items: List<DouyinVideo> = listOf(sampleDouyinVideo())): DouyinFeedPage {
    return DouyinFeedPage(items = items, nextCursor = null, hasMore = false)
}

fun sampleDouyinVideoContent(awemeId: String = "7357000000000000001"): DouyinContent.Video {
    return DouyinContent.Video(
        awemeId = awemeId,
        desc = "测试抖音详情",
        authorName = "测试作者",
        diggCount = 12,
        playUrl = "https://example.com/douyin-detail.mp4",
        coverUrl = "https://example.com/douyin-detail.jpg"
    )
}
