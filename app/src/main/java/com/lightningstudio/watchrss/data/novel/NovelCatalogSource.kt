package com.lightningstudio.watchrss.data.novel

import com.lightningstudio.watchrss.data.db.RssItemDao
import com.lightningstudio.watchrss.data.rss.RssItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun interface NovelCatalogSource {
    fun observe(channelId: Long): Flow<List<RssItem>>
}

/** Directory-specific metadata path: no article hydration, previews, or media requests. */
class DatabaseNovelCatalogSource(private val dao: RssItemDao) : NovelCatalogSource {
    override fun observe(channelId: Long): Flow<List<RssItem>> = dao.observeItemsPaged(channelId, Int.MAX_VALUE)
        .map { rows ->
            rows.map { row ->
                RssItem(row.id, row.channelId, row.title, null, null, null, row.link, row.pubDate,
                    null, null, null, null, null, row.isRead, row.isLiked, row.readingProgress, row.fetchedAt)
            }
        }
}
