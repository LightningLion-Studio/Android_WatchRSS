package com.lightningstudio.watchrss.data.rss

const val ARTICLE_BODY_SYNC_MODE_FULL = "full"
const val ARTICLE_BODY_SYNC_MODE_SAVED = "saved"

data class SyncedSavedArticle(
    val articleId: String,
    val sourceDeviceId: String,
    val url: String,
    val title: String,
    val siteName: String,
    val excerpt: String,
    val contentHtml: String?,
    val contentText: String,
    val imageUrl: String?,
    val contentHash: String,
    val importedAt: Long,
    val updatedAt: Long,
    val independentSaved: Boolean = false,
    val independentChangedAt: Long = 0L,
    val independentSortOrder: Long = 0L,
    val rssSourceUrl: String? = null,
    val rssSourceTitle: String? = null,
    val favoriteSaved: Boolean,
    val favoriteChangedAt: Long,
    val favoriteSortOrder: Long,
    val watchLaterSaved: Boolean,
    val watchLaterChangedAt: Long,
    val watchLaterSortOrder: Long,
    val deleted: Boolean,
    val deletedAt: Long,
    val readingProgress: Float = 0f,
    val cachedBodyMetadata: ArticleBodyMetadata? = null
)

data class SyncedSavedArticleMergeStats(
    val received: Int,
    val applied: Int
)

data class SyncedArticleManifest(
    val articleId: String,
    val sourceDeviceId: String,
    val contentHash: String,
    val updatedAt: Long,
    val independentChangedAt: Long,
    val favoriteChangedAt: Long,
    val watchLaterChangedAt: Long,
    val deletedAt: Long,
    val deleted: Boolean = deletedAt > 0L,
    val bodyHash: String,
    val bodyByteCount: Long,
    val chunkSize: Int,
    val chunkHashes: List<String>,
    val metadataHash: String,
    val bodyAvailable: Boolean = true,
    val bodySyncMode: String = ARTICLE_BODY_SYNC_MODE_FULL,
    val readingProgress: Float = 0f
)

data class SyncedArticleBodyRequest(
    val articleId: String,
    val bodyHash: String,
    val chunkIndexes: List<Int>,
    val metadataOnly: Boolean = false
)

data class SyncedArticleBodyChunk(
    val index: Int,
    val hash: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SyncedArticleBodyChunk
        return index == other.index &&
            hash == other.hash &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + hash.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

data class SyncedChunkedArticle(
    val article: SyncedSavedArticle,
    val bodyHash: String,
    val bodyByteCount: Long,
    val chunkSize: Int,
    val chunkHashes: List<String>,
    val chunks: List<SyncedArticleBodyChunk>,
    val metadataOnly: Boolean = false
)

data class SyncedRssSource(
    val url: String,
    val sourceDeviceId: String,
    val title: String,
    val description: String,
    val siteUrl: String?,
    val imageUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Long,
    val isPinned: Boolean = false,
    val deleted: Boolean,
    val deletedAt: Long
)

data class SyncedRssSourceMergeStats(
    val received: Int,
    val applied: Int
)
