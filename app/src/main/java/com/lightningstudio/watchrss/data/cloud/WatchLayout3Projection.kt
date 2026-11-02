package com.lightningstudio.watchrss.data.cloud

import com.lightningstudio.watchrss.data.rss.SyncedSavedArticle
import org.json.JSONObject

/** Side-effect-free preparation for the native Room boundary.
 * The source documents/package must be retained separately: SyncedSavedArticle
 * cannot represent book identity, deletion sets or separate save provenance. */
internal object WatchLayout3Projection {
    fun bookRows(
        bytes: ByteArray, descriptor: JSONObject, state: WatchLayout3RawDocument,
        reading: List<WatchLayout3RawDocument>
    ): List<SyncedSavedArticle> {
        val book = WatchLayout3Books.decode(bytes, descriptor)
        WatchLayout3Books.validateState(state)
        val s = state.json()
        require(s.getString("bookId") == book.bookId && s.getString("fingerprint") == book.fingerprint &&
            s.getInt("chapterCount") == book.chapters.size)
        val readings = mutableMapOf<Int, WatchLayout3RawDocument>()
        reading.forEach {
            WatchLayout3Books.validateReading(it)
            require(it.json().getString("bookId") == book.bookId && it.json().getInt("chapterIndex") in 1..book.chapters.size)
            val index = it.json().getInt("chapterIndex")
            readings[index] = WatchLayout3Books.mergeReading(readings[index], it)
        }
        val deleted = s.getJSONArray("deletedChapters").let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() }
        return book.chapters.map { c ->
            val r = readings[c.index]?.json() ?: JSONObject()
            SyncedSavedArticle(
                articleId = WatchLayout3Books.articleId(book.bookId, c.index),
                sourceDeviceId = r.optString("sourceDeviceId", s.getString("sourceDeviceId")),
                url = c.url, title = c.title, siteName = s.getString("title"), excerpt = "",
                contentHtml = c.html, contentText = c.text, imageUrl = null, contentHash = book.hashes[c.index - 1],
                importedAt = s.getLong("updatedAt"), updatedAt = maxOf(s.getLong("updatedAt"), r.optLong("readingPositionChangedAt")),
                independentSaved = true, rssSourceUrl = book.header.getString("sourceUrl"), rssSourceTitle = s.getString("title"),
                favoriteSaved = r.optBoolean("favoriteSaved"), favoriteChangedAt = r.optLong("favoriteChangedAt"), favoriteSortOrder = r.optLong("favoriteSortOrder"),
                watchLaterSaved = r.optBoolean("watchLaterSaved"), watchLaterChangedAt = r.optLong("watchLaterChangedAt"), watchLaterSortOrder = r.optLong("watchLaterSortOrder"),
                deleted = s.getBoolean("deleted") || c.index in deleted,
                deletedAt = if (s.getBoolean("deleted") || c.index in deleted) maxOf(s.getLong("deletedAt"), s.getLong("deletionClock")) else 0,
                isRead = r.optBoolean("isRead"), readingProgress = r.optDouble("readingProgress", 0.0).toFloat(),
                readingPositionBytes = r.optLong("readingPositionBytes"), readingPositionContentHash = r.optString("readingPositionContentHash"),
                readingPositionChangedAt = r.optLong("readingPositionChangedAt")
            )
        }
    }
    fun articleRow(article: WatchLayout3Archive.Article): SyncedSavedArticle {
        val v = article.metadata.json()
        return SyncedSavedArticle(
            articleId = v.getString("articleId"), sourceDeviceId = v.optString("sourceDeviceId"), url = v.getString("url"),
            title = v.getString("title"), siteName = v.optString("siteName"), excerpt = v.optString("excerpt"),
            contentHtml = article.html, contentText = article.text, imageUrl = nullable(v, "imageUrl"),
            contentHash = v.getString("contentHash"), importedAt = v.optLong("importedAt"), updatedAt = v.optLong("updatedAt"),
            rssSourceUrl = nullable(v, "rssSourceUrl"), rssSourceTitle = nullable(v, "rssSourceTitle"),
            independentSaved = v.optBoolean("independentSaved"), independentChangedAt = v.optLong("independentChangedAt"), independentSortOrder = v.optLong("independentSortOrder"),
            favoriteSaved = v.optBoolean("favoriteSaved"), favoriteChangedAt = v.optLong("favoriteChangedAt"), favoriteSortOrder = v.optLong("favoriteSortOrder"),
            watchLaterSaved = v.optBoolean("watchLaterSaved"), watchLaterChangedAt = v.optLong("watchLaterChangedAt"), watchLaterSortOrder = v.optLong("watchLaterSortOrder"),
            deleted = v.optBoolean("deleted"), deletedAt = v.optLong("deletedAt"), isRead = v.optBoolean("isRead"),
            readingProgress = v.optDouble("readingProgress", 0.0).toFloat(), readingPositionBytes = v.optLong("readingPositionBytes"),
            readingPositionContentHash = v.optString("readingPositionContentHash"), readingPositionChangedAt = v.optLong("readingPositionChangedAt")
        )
    }
    private fun nullable(json: JSONObject, name: String): String? = if (!json.has(name) || json.isNull(name)) null else json.getString(name)
}
