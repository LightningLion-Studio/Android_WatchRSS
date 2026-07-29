package com.lightningstudio.watchrss.data.cloud

import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.SyncedSavedArticle
import com.lightningstudio.watchrss.data.rss.SyncedRssSource
import com.lightningstudio.watchrss.phoneconnection.bluetooth.LibrarySyncPayload
import org.json.JSONArray
import org.json.JSONObject

class WatchCloudLibraryAdapter(
    private val repository: RssRepository,
    private val deviceId: String
) {
    suspend fun publicSources(): List<SyncedRssSource> =
        repository.exportSyncedRssSources(deviceId).filter {
            !it.deleted && !ImportedContentIds.isImportedContentUrl(it.url)
        }

    suspend fun mergeInventory(inventory: WatchRssInventory): Int =
        repository.mergeSyncedSavedArticles(
            inventory.articles,
            "cloud-rss-inventory",
            deviceId
        ).applied

    suspend fun export(): ByteArray {
        val articles = repository.exportSyncedSavedArticles(deviceId).map { article ->
            val privateBody =
                ImportedContentIds.isImportedContentUrl(article.url) ||
                    ImportedContentIds.isImportedContentUrl(article.rssSourceUrl) ||
                    article.rssSourceUrl.isNullOrBlank()
            if (privateBody) article else article.copy(contentHtml = null, contentText = "")
        }
        val sources = repository.exportSyncedRssSources(deviceId)
        return LibrarySyncPayload.buildResponse(
            deviceId = deviceId,
            articles = articles,
            applied = 0,
            rssSources = sources
        ).toString().toByteArray(Charsets.UTF_8)
    }

    suspend fun exportState(): ByteArray {
        val articles = repository.exportCloudRssStateArticles(deviceId)
        val sources = repository.exportSyncedRssSources(deviceId)
        return JSONObject().apply {
            put("format", "watchrss-rss-state")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("articles", JSONArray().apply {
                articles.forEach { article ->
                    put(JSONObject().apply {
                        put("articleId", article.articleId)
                        put("sourceDeviceId", article.sourceDeviceId)
                        put("url", article.url)
                        put("title", article.title)
                        put("siteName", article.siteName)
                        put("excerpt", article.excerpt)
                        put("imageUrl", article.imageUrl ?: JSONObject.NULL)
                        put("contentHash", article.contentHash)
                        put("importedAt", article.importedAt)
                        put("updatedAt", article.updatedAt)
                        put("rssSourceUrl", article.rssSourceUrl ?: JSONObject.NULL)
                        put("rssSourceTitle", article.rssSourceTitle ?: JSONObject.NULL)
                        put("independentSaved", article.independentSaved)
                        put("independentChangedAt", article.independentChangedAt)
                        put("independentSortOrder", article.independentSortOrder)
                        put("favoriteSaved", article.favoriteSaved)
                        put("favoriteChangedAt", article.favoriteChangedAt)
                        put("favoriteSortOrder", article.favoriteSortOrder)
                        put("watchLaterSaved", article.watchLaterSaved)
                        put("watchLaterChangedAt", article.watchLaterChangedAt)
                        put("watchLaterSortOrder", article.watchLaterSortOrder)
                        put("deleted", article.deleted)
                        put("deletedAt", article.deletedAt)
                        put("readingProgress", article.readingProgress.toDouble())
                        put("isRead", article.isRead)
                    })
                }
            })
            put("sources", JSONArray().apply {
                sources.forEach { source ->
                    put(JSONObject().apply {
                        put("url", source.url)
                        put("sourceDeviceId", source.sourceDeviceId)
                        put("title", source.title)
                        put("description", source.description)
                        put("siteUrl", source.siteUrl ?: JSONObject.NULL)
                        put("imageUrl", source.imageUrl ?: JSONObject.NULL)
                        put("createdAt", source.createdAt)
                        put("updatedAt", source.updatedAt)
                        put("sortOrder", source.sortOrder)
                        put("isPinned", source.isPinned)
                        put("deleted", source.deleted)
                        put("deletedAt", source.deletedAt)
                    })
                }
            })
        }.toString().toByteArray(Charsets.UTF_8)
    }

    suspend fun restore(bytes: ByteArray, remoteDeviceId: String): Int {
        val payload = JSONObject(bytes.toString(Charsets.UTF_8))
        val sources = LibrarySyncPayload.parseRssSources(payload)
        val articles = LibrarySyncPayload.parseArticles(payload)
        val sourceStats = repository.mergeSyncedRssSources(
            sources,
            remoteDeviceId,
            deviceId
        )
        val articleStats = repository.mergeSyncedSavedArticles(
            articles,
            remoteDeviceId,
            deviceId
        )
        return sourceStats.applied + articleStats.applied
    }

    suspend fun restoreState(bytes: ByteArray, remoteDeviceId: String): Int {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(
            root.getString("format") == "watchrss-rss-state" &&
                root.getInt("version") == 1
        ) { "云端RSS状态格式不受支持" }
        val states = root.getJSONArray("articles")
        val articles = buildList {
            for (index in 0 until states.length()) {
                val state = states.getJSONObject(index)
                add(
                    SyncedSavedArticle(
                        articleId = state.getString("articleId"),
                        sourceDeviceId = state.optString("sourceDeviceId"),
                        url = state.optString("url"),
                        title = state.optString("title"),
                        siteName = state.optString("siteName"),
                        excerpt = state.optString("excerpt"),
                        contentHtml = null,
                        contentText = "",
                        imageUrl = state.nullableString("imageUrl"),
                        contentHash = state.optString("contentHash"),
                        importedAt = state.optLong("importedAt"),
                        updatedAt = state.optLong("updatedAt"),
                        independentSaved = state.optBoolean("independentSaved"),
                        independentChangedAt = state.optLong("independentChangedAt"),
                        independentSortOrder = state.optLong("independentSortOrder"),
                        rssSourceUrl = state.nullableString("rssSourceUrl"),
                        rssSourceTitle = state.nullableString("rssSourceTitle"),
                        favoriteSaved = state.optBoolean("favoriteSaved"),
                        favoriteChangedAt = state.optLong("favoriteChangedAt"),
                        favoriteSortOrder = state.optLong("favoriteSortOrder"),
                        watchLaterSaved = state.optBoolean("watchLaterSaved"),
                        watchLaterChangedAt = state.optLong("watchLaterChangedAt"),
                        watchLaterSortOrder = state.optLong("watchLaterSortOrder"),
                        deleted = state.optBoolean("deleted"),
                        deletedAt = state.optLong("deletedAt"),
                        readingProgress = state.optDouble("readingProgress")
                            .toFloat()
                            .coerceIn(0f, 1f),
                        isRead = state.optBoolean("isRead")
                    )
                )
            }
        }
        val sourceArray = root.optJSONArray("sources") ?: JSONArray()
        val sources = buildList {
            for (index in 0 until sourceArray.length()) {
                val source = sourceArray.getJSONObject(index)
                add(
                    SyncedRssSource(
                        url = source.getString("url"),
                        sourceDeviceId = source.optString("sourceDeviceId"),
                        title = source.optString("title"),
                        description = source.optString("description"),
                        siteUrl = source.nullableString("siteUrl"),
                        imageUrl = source.nullableString("imageUrl"),
                        createdAt = source.optLong("createdAt"),
                        updatedAt = source.optLong("updatedAt"),
                        sortOrder = source.optLong("sortOrder"),
                        isPinned = source.optBoolean("isPinned"),
                        deleted = source.optBoolean("deleted"),
                        deletedAt = source.optLong("deletedAt")
                    )
                )
            }
        }
        val sourceApplied = repository.mergeSyncedRssSources(
            sources,
            remoteDeviceId,
            deviceId
        ).applied
        return sourceApplied + repository.mergeSyncedSavedArticles(
            articles,
            remoteDeviceId,
            deviceId
        ).applied
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)
}
