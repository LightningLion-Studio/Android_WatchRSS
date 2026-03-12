package com.lightningstudio.watchrss.data.rss

data class RssRecommendChannel(
    val title: String,
    val url: String
)

data class RssRecommendGroup(
    val id: String,
    val name: String,
    val description: String?,
    val channels: List<RssRecommendChannel>
)

object RssRecommendations {
    val groups: List<RssRecommendGroup> = listOf(
        RssRecommendGroup(
            id = "bilibili",
            name = "哔哩哔哩",
            description = "进入后登录获取内容。",
            channels = listOf(
                RssRecommendChannel("推荐入口", "https://www.bilibili.com")
            )
        ),
        RssRecommendGroup(
            id = "douyin",
            name = "抖音",
            description = "进入后登录获取内容。",
            channels = listOf(
                RssRecommendChannel("推荐入口", "https://www.douyin.com")
            )
        ),
        RssRecommendGroup(
            id = "sspai",
            name = "少数派",
            description = "极客和数字生活爱好者的优质内容源。",
            channels = listOf(
                RssRecommendChannel("RSS 地址", "https://sspai.com/feed")
            )
        ),
        RssRecommendGroup(
            id = "36kr",
            name = "36氪",
            description = "让一部分人先看到未来",
            channels = listOf(
                RssRecommendChannel("综合资讯", "https://36kr.com/feed"),
                RssRecommendChannel("文章资讯", "https://36kr.com/feed-article"),
                RssRecommendChannel("最新快讯", "https://36kr.com/feed-newsflash"),
                RssRecommendChannel("动态内容", "https://36kr.com/feed-moment")
            )
        ),
        RssRecommendGroup(
            id = "huxiu",
            name = "虎嗅",
            description = "商业科技深度分析的好去处。",
            channels = listOf(
                RssRecommendChannel("RSS 地址", "https://rss.huxiu.com/")
            )
        ),
        RssRecommendGroup(
            id = "chinanews",
            name = "中国新闻网",
            description = "权威新闻汇总，覆盖国内外要闻与专题栏目。",
            channels = listOf(
                RssRecommendChannel("即时新闻", "https://www.chinanews.com.cn/rss/scroll-news.xml"),
                RssRecommendChannel("要闻导读", "https://www.chinanews.com.cn/rss/importnews.xml"),
                RssRecommendChannel("时政新闻", "https://www.chinanews.com.cn/rss/china.xml"),
                RssRecommendChannel("东西问", "https://www.chinanews.com.cn/rss/dxw.xml"),
                RssRecommendChannel("国际新闻", "https://www.chinanews.com.cn/rss/world.xml"),
                RssRecommendChannel("社会新闻", "https://www.chinanews.com.cn/rss/society.xml"),
                RssRecommendChannel("财经新闻", "https://www.chinanews.com.cn/rss/finance.xml"),
                RssRecommendChannel("健康·生活", "https://www.chinanews.com.cn/rss/life.xml"),
                RssRecommendChannel("大湾区", "https://www.chinanews.com.cn/rss/dwq.xml"),
                RssRecommendChannel("华人", "https://www.chinanews.com.cn/rss/chinese.xml"),
                RssRecommendChannel("文娱新闻", "https://www.chinanews.com.cn/rss/culture.xml"),
                RssRecommendChannel("体育新闻", "https://www.chinanews.com.cn/rss/sports.xml"),
                RssRecommendChannel("视频", "https://www.chinanews.com.cn/rss/sp.xml"),
                RssRecommendChannel("图片", "https://www.chinanews.com.cn/rss/photo.xml"),
                RssRecommendChannel("创意", "https://www.chinanews.com.cn/rss/chuangyi.xml"),
                RssRecommendChannel("直播", "https://www.chinanews.com.cn/rss/zhibo.xml")
            )
        )
    )

    fun findGroup(id: String): RssRecommendGroup? {
        return groups.firstOrNull { it.id == id }
    }
}
