package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.data.rss.ArticleSyncBody
import com.lightningstudio.watchrss.data.rss.ArticleBodyMetadata
import com.lightningstudio.watchrss.data.rss.ARTICLE_BODY_SYNC_MODE_FULL
import com.lightningstudio.watchrss.data.rss.ARTICLE_BODY_SYNC_MODE_SAVED
import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.data.rss.SyncedArticleBodyChunk
import com.lightningstudio.watchrss.data.rss.SyncedArticleBodyRequest
import com.lightningstudio.watchrss.data.rss.SyncedArticleManifest
import com.lightningstudio.watchrss.data.rss.SyncedChunkedArticle
import com.lightningstudio.watchrss.data.rss.SyncedRssSource
import com.lightningstudio.watchrss.data.rss.SyncedSavedArticle
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class ArticleSyncManifestEntry(
    val articleId: String,
    val sourceDeviceId: String = "",
    val contentHash: String,
    val updatedAt: Long,
    val independentChangedAt: Long,
    val favoriteChangedAt: Long,
    val watchLaterChangedAt: Long,
    val deletedAt: Long,
    val deleted: Boolean = deletedAt > 0L,
    val bodyHash: String = contentHash,
    val bodyByteCount: Long = 0L,
    val chunkSize: Int = 0,
    val chunkHashes: List<String> = emptyList(),
    val metadataHash: String = "",
    val bodyAvailable: Boolean = true,
    val bodySyncMode: String = ARTICLE_BODY_SYNC_MODE_FULL,
    val readingProgress: Float = 0f
)

data class LibraryChangeSequence(
    val fromSeqExclusive: Long,
    val toSeqInclusive: Long,
    val fullSnapshot: Boolean,
    val fallbackReason: String = ""
)

object LibrarySyncPayload {
    const val PROTOCOL_VERSION = 9
    const val LEGACY_PROTOCOL_VERSION = 4
    const val MAX_BODY_REQUEST_CHUNKS_PER_SYNC = Int.MAX_VALUE
    const val MAX_ARTICLE_REQUEST_BATCH_COUNT = 256
    const val PHASE_MANIFEST = "manifest"
    const val PHASE_PROBE = "probe"
    const val PHASE_ARTICLES = "articles"
    const val PHASE_COMPLETE = "complete"

    fun buildProbeResponse(deviceId: String): JSONObject {
        return JSONObject().apply {
            put("success", true)
            put("version", PROTOCOL_VERSION)
            put("action", BluetoothSyncProtocol.ACTION_SYNC_LIBRARY)
            put("phase", PHASE_PROBE)
            put("deviceId", deviceId)
            put("sentAt", System.currentTimeMillis())
        }
    }

    fun parseArticles(payload: JSONObject): List<SyncedSavedArticle> {
        return parseArticles(payload.optJSONArray("articles") ?: JSONArray())
    }

    fun parseArticleManifest(payload: JSONObject): List<ArticleSyncManifestEntry> {
        return parseArticleManifest(payload.optJSONArray("articleManifest") ?: JSONArray())
    }

    fun parseArticleManifest(array: JSONArray): List<ArticleSyncManifestEntry> {
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val articleId = item.optString("articleId").trim()
                if (articleId.isBlank()) continue
                add(
                    ArticleSyncManifestEntry(
                        articleId = articleId,
                        sourceDeviceId = item.optString("sourceDeviceId").trim(),
                        contentHash = item.optString("contentHash").trim(),
                        updatedAt = item.optLong("updatedAt"),
                        independentChangedAt = item.optLong("independentChangedAt"),
                        favoriteChangedAt = item.optLong("favoriteChangedAt"),
                        watchLaterChangedAt = item.optLong("watchLaterChangedAt"),
                        deletedAt = item.optLong("deletedAt"),
                        deleted = item.optBoolean("deleted", item.optLong("deletedAt") > 0L),
                        bodyHash = item.optString("bodyHash").trim().ifBlank {
                            item.optString("contentHash").trim()
                        },
                        bodyByteCount = item.optLong("bodyByteCount"),
                        chunkSize = item.optInt("chunkSize"),
                        chunkHashes = item.optStringArray("chunkHashes"),
                        metadataHash = item.optString("metadataHash").trim(),
                        bodyAvailable = item.optBoolean("bodyAvailable", true),
                        bodySyncMode = item.optString("bodySyncMode")
                            .trim()
                            .ifBlank { ARTICLE_BODY_SYNC_MODE_FULL },
                        readingProgress = item.optDouble("readingProgress", 0.0)
                            .toFloat()
                            .coerceIn(0f, 1f)
                    )
                )
            }
        }
    }

    fun buildBodyRequestsForRemoteArticles(
        localManifest: List<SyncedArticleManifest>,
        remoteManifest: List<ArticleSyncManifestEntry>,
        maxBodyRequestChunks: Int = Int.MAX_VALUE,
        supportsMetadataOnlyArticles: Boolean = false
    ): List<SyncedArticleBodyRequest> {
        val localById = localManifest.associateBy { it.articleId }
        return remoteManifest.mapNotNull { remote ->
            val local = localById[remote.articleId]
            val remoteMetadataNewer = local == null ||
                remote.updatedAt > local.updatedAt
            val needsMetadata = if (remote.deleted) {
                local == null ||
                    !local.deleted ||
                    remote.deletedAt > local.deletedAt ||
                    remote.independentChangedAt > local.independentChangedAt ||
                    remote.favoriteChangedAt > local.favoriteChangedAt ||
                    remote.watchLaterChangedAt > local.watchLaterChangedAt
            } else {
                local == null ||
                    (remote.metadataHash != local.metadataHash && remoteMetadataNewer) ||
                    remote.updatedAt > local.updatedAt ||
                    remote.independentChangedAt > local.independentChangedAt ||
                    remote.favoriteChangedAt > local.favoriteChangedAt ||
                    remote.watchLaterChangedAt > local.watchLaterChangedAt ||
                    remote.deletedAt > local.deletedAt ||
                    remote.deleted != local.deleted ||
                    remote.readingProgress.isMeaningfullyAheadOf(local.readingProgress)
            }
            val localHasBody = local?.bodyAvailable == true
            val hasReusableLocalBody = localHasBody &&
                remote.bodyHash == local?.bodyHash &&
                local.chunkHashes.isNotEmpty()
            val shouldRequestMetadataOnlyBody = remote.shouldRequestMetadataOnlyBody(
                supportsMetadataOnlyArticles = supportsMetadataOnlyArticles
            )
            val metadataOnly = needsMetadata && shouldRequestMetadataOnlyBody
            if (!remote.deleted && !remote.bodyAvailable) {
                return@mapNotNull null
            }
            val needsBody = !remote.deleted &&
                remote.bodyAvailable &&
                !hasReusableLocalBody &&
                !shouldRequestMetadataOnlyBody
            if (!needsMetadata && !needsBody) return@mapNotNull null
            val localHashes = if (local?.bodyAvailable == true) {
                local.chunkHashes.toSet()
            } else {
                emptySet()
            }
            val chunkIndexes = if (needsBody) {
                remote.chunkHashes.mapIndexedNotNull { index, hash ->
                    index.takeIf { hash !in localHashes }
                }
            } else {
                emptyList()
            }
            SyncedArticleBodyRequest(
                articleId = remote.articleId,
                bodyHash = remote.bodyHash,
                chunkIndexes = chunkIndexes,
                metadataOnly = metadataOnly
            )
        }.limitBodyRequestChunks(maxBodyRequestChunks)
    }

    private fun ArticleSyncManifestEntry.shouldRequestMetadataOnlyBody(
        supportsMetadataOnlyArticles: Boolean
    ): Boolean {
        return supportsMetadataOnlyArticles && !deleted && bodyAvailable
    }

    fun parseBodyRequests(payload: JSONObject): List<SyncedArticleBodyRequest> {
        val array = payload.optJSONArray("bodyRequests") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val articleId = item.optString("articleId").trim()
                if (articleId.isBlank()) continue
                add(
                    SyncedArticleBodyRequest(
                        articleId = articleId,
                        bodyHash = item.optString("bodyHash").trim(),
                        chunkIndexes = item.optIntArray("chunkIndexes"),
                        metadataOnly = item.optBoolean("metadataOnly", false)
                    )
                )
            }
        }
    }

    fun parseBodyRequests(payloads: Iterable<JSONObject>): List<SyncedArticleBodyRequest> {
        return payloads.flatMap(::parseBodyRequests)
    }

    fun supportsChangeSequences(payload: JSONObject): Boolean {
        return payload.optBoolean("supportsChangeSequences", false) &&
            payload.optInt("version") >= CHANGE_SEQUENCE_PROTOCOL_VERSION
    }

    fun supportsMetadataOnlyArticles(payload: JSONObject): Boolean {
        return payload.optBoolean("supportsMetadataOnlyArticles", false) &&
            payload.optInt("version") >= METADATA_ONLY_ARTICLES_PROTOCOL_VERSION
    }

    fun parseChangeSequence(payload: JSONObject): LibraryChangeSequence {
        val range = payload.optJSONObject("changeSeqRange") ?: JSONObject()
        return LibraryChangeSequence(
            fromSeqExclusive = range.optLong("fromExclusive"),
            toSeqInclusive = range.optLong("toInclusive"),
            fullSnapshot = payload.optBoolean("fullSnapshot", true),
            fallbackReason = payload.optString("fallbackReason").trim()
        )
    }

    fun filterArticlesNeedingSync(
        localArticles: List<SyncedSavedArticle>,
        remoteManifest: List<ArticleSyncManifestEntry>
    ): List<SyncedSavedArticle> {
        val remoteById = remoteManifest.associateBy { it.articleId }
        return localArticles.filter { article ->
            val remote = remoteById[article.articleId] ?: return@filter true
            article.contentHash != remote.contentHash ||
                article.updatedAt > remote.updatedAt ||
                article.independentChangedAt > remote.independentChangedAt ||
                article.favoriteChangedAt > remote.favoriteChangedAt ||
                article.watchLaterChangedAt > remote.watchLaterChangedAt ||
                article.deletedAt > remote.deletedAt ||
                article.deleted != remote.deleted ||
                article.readingProgress.isMeaningfullyAheadOf(remote.readingProgress)
        }
    }

    fun parseArticles(array: JSONArray): List<SyncedSavedArticle> {
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val articleId = item.optString("articleId").trim()
                val url = item.optString("url").trim()
                if (articleId.isBlank() || url.isBlank()) continue
                val rssSourceUrl = item.optString("rssSourceUrl").trim().ifBlank { null }
                add(
                    SyncedSavedArticle(
                        articleId = articleId,
                        sourceDeviceId = item.optString("sourceDeviceId").ifBlank {
                            item.optString("deviceId")
                        },
                        url = url,
                        title = item.optString("title").trim().ifBlank { url },
                        siteName = item.optString("siteName").trim(),
                        excerpt = item.optString("excerpt").trim(),
                        contentHtml = item.optCompressedString("contentHtmlGzip"),
                        contentText = item.optCompressedString("contentTextGzip").orEmpty(),
                        imageUrl = item.optString("imageUrl").trim().ifBlank { null },
                        contentHash = item.optString("contentHash").trim(),
                        importedAt = item.optLong("importedAt"),
                        updatedAt = item.optLong("updatedAt"),
                        independentSaved = item.optBoolean(
                            "independentSaved",
                            !item.optBoolean("favoriteSaved") &&
                                !item.optBoolean("watchLaterSaved") &&
                                !item.optBoolean("deleted") &&
                                rssSourceUrl.isNullOrBlank()
                        ),
                        independentChangedAt = item.optLong("independentChangedAt"),
                        independentSortOrder = item.optLong("independentSortOrder"),
                        rssSourceUrl = rssSourceUrl,
                        rssSourceTitle = item.optString("rssSourceTitle").trim().ifBlank { null },
                        favoriteSaved = item.optBoolean("favoriteSaved"),
                        favoriteChangedAt = item.optLong("favoriteChangedAt"),
                        favoriteSortOrder = item.optLong("favoriteSortOrder"),
                        watchLaterSaved = item.optBoolean("watchLaterSaved"),
                        watchLaterChangedAt = item.optLong("watchLaterChangedAt"),
                        watchLaterSortOrder = item.optLong("watchLaterSortOrder"),
                        deleted = item.optBoolean("deleted"),
                        deletedAt = item.optLong("deletedAt"),
                        readingProgress = item.optDouble("readingProgress", 0.0)
                            .toFloat()
                            .coerceIn(0f, 1f)
                    )
                )
            }
        }
    }

    fun parseChunkedArticles(payload: JSONObject): List<SyncedChunkedArticle> {
        return parseChunkedArticles(payload.optJSONArray("articles") ?: JSONArray())
    }

    fun parseChunkedArticles(array: JSONArray): List<SyncedChunkedArticle> {
        val byArticleId = linkedMapOf<String, SyncedChunkedArticle>()
        appendChunkedArticles(array, byArticleId)
        return byArticleId.values.toList()
    }

    fun parseChunkedArticles(payloads: Iterable<JSONObject>): List<SyncedChunkedArticle> {
        val byArticleId = linkedMapOf<String, SyncedChunkedArticle>()
        payloads.forEach { payload ->
            appendChunkedArticles(payload.optJSONArray("articles") ?: JSONArray(), byArticleId)
        }
        return byArticleId.values.toList()
    }

    private fun appendChunkedArticles(
        array: JSONArray,
        byArticleId: MutableMap<String, SyncedChunkedArticle>
    ) {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val article = parseArticles(JSONArray().put(item)).firstOrNull() ?: continue
            val body = item.optJSONObject("body") ?: JSONObject()
            val chunks = body.optJSONArray("chunks") ?: JSONArray()
            val payload = SyncedChunkedArticle(
                article = article,
                bodyHash = body.optString("bodyHash").trim().ifBlank { article.contentHash },
                bodyByteCount = body.optLong("bodyByteCount"),
                chunkSize = body.optInt("chunkSize"),
                chunkHashes = body.optStringArray("chunkHashes"),
                chunks = buildList {
                    for (chunkIndex in 0 until chunks.length()) {
                        val chunk = chunks.optJSONObject(chunkIndex) ?: continue
                        val encoded = chunk.optString("data").takeIf { it.isNotBlank() } ?: continue
                        add(
                            SyncedArticleBodyChunk(
                                index = chunk.optInt("index"),
                                hash = chunk.optString("hash").trim(),
                                bytes = ArticleSyncBody.decodeChunkData(encoded)
                            )
                        )
                    }
                },
                metadataOnly = body.optBoolean("metadataOnly", false)
            )
            val existing = byArticleId[article.articleId]
            byArticleId[article.articleId] = if (existing == null) {
                payload
            } else {
                require(
                    existing.bodyHash == payload.bodyHash &&
                        existing.chunkSize == payload.chunkSize &&
                        existing.chunkHashes == payload.chunkHashes &&
                        existing.metadataOnly == payload.metadataOnly
                ) {
                    "同步正文分块元数据冲突：${article.articleId}"
                }
                existing.copy(
                    article = payload.article,
                    chunks = (existing.chunks + payload.chunks)
                        .distinctBy { it.index }
                        .sortedBy { it.index }
                )
            }
        }
    }

    fun parseRssSources(payload: JSONObject): List<SyncedRssSource> {
        return parseRssSources(payload.optJSONArray("rssSources") ?: JSONArray())
    }

    fun parseRssSources(array: JSONArray): List<SyncedRssSource> {
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url").trim()
                if (url.isBlank()) continue
                add(
                    SyncedRssSource(
                        url = url,
                        sourceDeviceId = item.optString("sourceDeviceId").ifBlank {
                            item.optString("deviceId")
                        },
                        title = item.optString("title").trim().ifBlank { url },
                        description = item.optString("description").trim(),
                        siteUrl = item.optString("siteUrl").trim().ifBlank { null },
                        imageUrl = item.optString("imageUrl").trim().ifBlank { null },
                        createdAt = item.optLong("createdAt"),
                        updatedAt = item.optLong("updatedAt"),
                        sortOrder = item.optLong("sortOrder"),
                        isPinned = item.optBoolean("isPinned"),
                        deleted = item.optBoolean("deleted"),
                        deletedAt = item.optLong("deletedAt")
                    )
                )
            }
        }
    }

    fun buildResponse(
        deviceId: String,
        articles: List<SyncedSavedArticle>,
        applied: Int,
        rssSources: List<SyncedRssSource> = emptyList(),
        sourcesApplied: Int = 0
    ): JSONObject = buildResponseFrame(
        deviceId = deviceId,
        articles = articles,
        applied = applied,
        rssSources = rssSources,
        sourcesApplied = sourcesApplied,
        batchIndex = null,
        batchCount = null,
        totalArticles = null
    )

    fun buildResponseFrames(
        deviceId: String,
        articles: List<SyncedSavedArticle>,
        applied: Int,
        rssSources: List<SyncedRssSource> = emptyList(),
        sourcesApplied: Int = 0,
        useBatches: Boolean
    ): List<JSONObject> {
        if (!useBatches) {
            return listOf(buildResponse(deviceId, articles, applied, rssSources, sourcesApplied))
        }
        return buildArticleFrames(
            articleItems = articles.map { it.toJson() },
            totalArticles = articles.size
        ) { array, batchIndex, batchCount, totalArticles ->
            buildResponseFrame(
                deviceId = deviceId,
                articleArray = array,
                articleCount = articles.size,
                applied = applied,
                rssSources = if (batchIndex == 0) rssSources else emptyList(),
                sourcesApplied = sourcesApplied,
                batchIndex = batchIndex,
                batchCount = batchCount,
                totalArticles = totalArticles
            )
        }
    }

    fun buildChunkedResponseFrames(
        deviceId: String,
        articles: List<SyncedSavedArticle>,
        articleRequests: List<SyncedArticleBodyRequest>,
        applied: Int,
        sourcesApplied: Int = 0,
        useBatches: Boolean,
        allowMetadataOnlyArticles: Boolean = false
    ): List<JSONObject> {
        val requestById = articleRequests.associateBy { it.articleId }
        val articleItems = articles.flatMap { article ->
            article.toChunkedJsonItems(
                request = requestById[article.articleId],
                allowMetadataOnlyArticles = allowMetadataOnlyArticles
            )
        }
        if (!useBatches) {
            return listOf(buildChunkedResponse(deviceId, articleItems, applied, sourcesApplied))
        }
        return buildArticleFrames(
            articleItems = articleItems,
            totalArticles = articles.size
        ) { array, batchIndex, batchCount, totalArticles ->
            JSONObject().apply {
                put("success", true)
                put("version", PROTOCOL_VERSION)
                put("action", BluetoothSyncProtocol.ACTION_SYNC_LIBRARY)
                put("phase", PHASE_COMPLETE)
                put("deviceId", deviceId)
                put("sentAt", System.currentTimeMillis())
                put("articles", array)
                putBatchFields(batchIndex, batchCount, totalArticles)
                if (batchIndex == 0) {
                    putStats(articleCount = articles.size, applied = applied, sourcesApplied = sourcesApplied)
                }
            }
        }
    }

    fun combineArticlePayloads(frames: List<JSONObject>): JSONObject {
        if (frames.isEmpty()) return JSONObject()
        if (frames.size == 1 && !frames.first().optBoolean("success", true)) return frames.first()
        val first = frames.first()
        val articles = JSONArray()
        val sources = JSONArray()
        val bodyRequests = JSONArray()
        frames.forEach { frame ->
            frame.optJSONArray("articles")?.let { source ->
                for (index in 0 until source.length()) {
                    source.optJSONObject(index)?.let(articles::put)
                }
            }
            frame.optJSONArray("rssSources")?.let { source ->
                for (index in 0 until source.length()) {
                    source.optJSONObject(index)?.let(sources::put)
                }
            }
            frame.optJSONArray("bodyRequests")?.let { source ->
                for (index in 0 until source.length()) {
                    source.optJSONObject(index)?.let(bodyRequests::put)
                }
            }
        }
        return JSONObject().apply {
            put("success", frames.all { it.optBoolean("success", true) })
            put("version", first.optInt("version", PROTOCOL_VERSION))
            put("action", BluetoothSyncProtocol.ACTION_SYNC_LIBRARY)
            put("phase", first.optString("phase").ifBlank { PHASE_COMPLETE })
            put("deviceId", first.optString("deviceId"))
            put("sentAt", first.optLong("sentAt"))
            put("articles", articles)
            if (sources.length() > 0) {
                put("rssSources", sources)
            }
            if (bodyRequests.length() > 0) {
                put("bodyRequests", bodyRequests)
            }
            first.optJSONObject("stats")?.let { put("stats", it) }
            first.optString("message").takeIf { it.isNotBlank() }?.let { put("message", it) }
        }
    }

    fun validateArticleRequestFrame(
        frame: JSONObject,
        expectedBatchIndex: Int,
        expectedBatchCount: Int? = null
    ): Int {
        val action = frame.optString("action")
        require(action == BluetoothSyncProtocol.ACTION_SYNC_LIBRARY) {
            "资料库文章请求帧 action 异常：frame=$expectedBatchIndex expected=${BluetoothSyncProtocol.ACTION_SYNC_LIBRARY} actual=$action"
        }
        val phase = frame.optString("phase")
        require(phase == PHASE_ARTICLES) {
            "资料库文章请求帧 phase 异常：frame=$expectedBatchIndex expected=$PHASE_ARTICLES actual=$phase"
        }

        val hasBatchIndex = frame.has("batchIndex")
        val hasBatchCount = frame.has("batchCount")
        if (!hasBatchIndex && !hasBatchCount && expectedBatchCount == null) {
            require(expectedBatchIndex == 0) {
                "资料库文章请求批次索引异常：frame=$expectedBatchIndex expectedBatchIndex=0 actual=missing"
            }
            return 1
        }
        require(hasBatchIndex && hasBatchCount) {
            "资料库文章请求批次字段不完整：frame=$expectedBatchIndex batchIndexPresent=$hasBatchIndex batchCountPresent=$hasBatchCount"
        }

        val batchIndex = frame.optInt("batchIndex", -1)
        val batchCount = frame.optInt("batchCount", -1)
        require(batchCount in 1..MAX_ARTICLE_REQUEST_BATCH_COUNT) {
            "资料库文章请求批次数异常：batchCount=$batchCount max=$MAX_ARTICLE_REQUEST_BATCH_COUNT"
        }
        if (expectedBatchCount != null) {
            require(batchCount == expectedBatchCount) {
                "资料库文章请求批次数不一致：frame=$expectedBatchIndex expectedBatchCount=$expectedBatchCount actual=$batchCount"
            }
        }
        require(batchIndex in 0 until batchCount) {
            "资料库文章请求批次索引越界：frame=$expectedBatchIndex batchIndex=$batchIndex batchCount=$batchCount"
        }
        require(batchIndex == expectedBatchIndex) {
            "资料库文章请求批次索引异常：frame=$expectedBatchIndex expectedBatchIndex=$expectedBatchIndex actual=$batchIndex batchCount=$batchCount"
        }
        return batchCount
    }

    private fun buildResponseFrame(
        deviceId: String,
        articles: List<SyncedSavedArticle>,
        applied: Int,
        rssSources: List<SyncedRssSource> = emptyList(),
        sourcesApplied: Int = 0,
        batchIndex: Int?,
        batchCount: Int?,
        totalArticles: Int?
    ): JSONObject = buildResponseFrame(
        deviceId = deviceId,
        articleArray = articles.toJsonArray(),
        articleCount = articles.size,
        applied = applied,
        rssSources = rssSources,
        sourcesApplied = sourcesApplied,
        batchIndex = batchIndex,
        batchCount = batchCount,
        totalArticles = totalArticles
    )

    private fun buildResponseFrame(
        deviceId: String,
        articleArray: JSONArray,
        articleCount: Int,
        applied: Int,
        rssSources: List<SyncedRssSource> = emptyList(),
        sourcesApplied: Int = 0,
        batchIndex: Int?,
        batchCount: Int?,
        totalArticles: Int?
    ): JSONObject {
        return JSONObject().apply {
            put("success", true)
            put("version", PROTOCOL_VERSION)
            put("action", BluetoothSyncProtocol.ACTION_SYNC_LIBRARY)
            put("phase", PHASE_COMPLETE)
            put("deviceId", deviceId)
            put("sentAt", System.currentTimeMillis())
            put("articles", articleArray)
            put("rssSources", rssSources.toSourceJsonArray())
            putBatchFields(batchIndex, batchCount, totalArticles)
            put(
                "stats",
                JSONObject().apply {
                    put("sent", articleCount)
                    put("applied", applied)
                    put("sourcesSent", rssSources.size)
                    put("sourcesApplied", sourcesApplied)
                }
            )
        }
    }

    fun buildManifestResponse(
        deviceId: String,
        articles: List<SyncedSavedArticle>,
        rssSources: List<SyncedRssSource> = emptyList(),
        sourcesApplied: Int = 0,
        changeSequence: LibraryChangeSequence? = null
    ): JSONObject {
        return JSONObject().apply {
            put("success", true)
            put("version", PROTOCOL_VERSION)
            put("action", BluetoothSyncProtocol.ACTION_SYNC_LIBRARY)
            put("phase", PHASE_MANIFEST)
            put("supportsArticleBatches", true)
            put("supportsChunkedBodies", true)
            put("supportsChangeSequences", true)
            put("supportsMetadataOnlyArticles", true)
            put("deviceId", deviceId)
            put("sentAt", System.currentTimeMillis())
            put("articleManifest", articles.toManifestJsonArray())
            put("rssSources", rssSources.toSourceJsonArray())
            putChangeSequence(changeSequence)
            put(
                "stats",
                JSONObject().apply {
                    put("sourcesSent", rssSources.size)
                    put("sourcesApplied", sourcesApplied)
                }
            )
        }
    }

    fun buildManifestResponseFromEntries(
        deviceId: String,
        articleManifest: List<SyncedArticleManifest>,
        bodyRequests: List<SyncedArticleBodyRequest>,
        rssSources: List<SyncedRssSource> = emptyList(),
        sourcesApplied: Int = 0,
        changeSequence: LibraryChangeSequence? = null
    ): JSONObject {
        return JSONObject().apply {
            put("success", true)
            put("version", PROTOCOL_VERSION)
            put("action", BluetoothSyncProtocol.ACTION_SYNC_LIBRARY)
            put("phase", PHASE_MANIFEST)
            put("supportsArticleBatches", true)
            put("supportsChunkedBodies", true)
            put("supportsChangeSequences", true)
            put("supportsMetadataOnlyArticles", true)
            put("deviceId", deviceId)
            put("sentAt", System.currentTimeMillis())
            put("articleManifest", articleManifest.toEntryJsonArray())
            put("bodyRequests", bodyRequests.toBodyRequestJsonArray())
            put("rssSources", rssSources.toSourceJsonArray())
            putChangeSequence(changeSequence)
            put(
                "stats",
                JSONObject().apply {
                    put("sourcesSent", rssSources.size)
                    put("sourcesApplied", sourcesApplied)
                    put("bodyRequests", bodyRequests.size)
                    put("metadataOnlyRequests", bodyRequests.count { it.metadataOnly })
                    put("bodyRequestChunks", bodyRequests.sumOf { it.chunkIndexes.size })
                }
            )
        }
    }

    private fun List<SyncedRssSource>.toSourceJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { source -> array.put(source.toJson()) }
        }
    }

    private fun List<SyncedSavedArticle>.toJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { article -> array.put(article.toJson()) }
        }
    }

    private fun List<SyncedSavedArticle>.toManifestJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { article -> array.put(article.toManifestJson()) }
        }
    }

    private fun List<SyncedArticleManifest>.toEntryJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { entry -> array.put(entry.toJson()) }
        }
    }

    private fun List<JSONObject>.toRawJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach(array::put)
        }
    }

    private fun buildArticleFrames(
        articleItems: List<JSONObject>,
        totalArticles: Int,
        buildPayload: (JSONArray, Int, Int, Int) -> JSONObject
    ): List<JSONObject> {
        if (articleItems.isEmpty()) {
            return listOf(buildPayload(JSONArray(), 0, 1, totalArticles))
        }

        val chunks = mutableListOf<List<JSONObject>>()
        var current = mutableListOf<JSONObject>()
        var currentBytes = 0
        val articleSizes = articleItems.map(BluetoothSyncProtocol::encodedSize)
        articleItems.forEachIndexed { index, article ->
            val articleSize = articleSizes[index]
            if (current.isNotEmpty() && currentBytes + articleSize > ARTICLE_BATCH_TARGET_BYTES) {
                chunks += current
                current = mutableListOf(article)
                currentBytes = articleSize
            } else {
                current.add(article)
                currentBytes += articleSize
            }
        }
        if (current.isNotEmpty()) {
            chunks += current
        }

        while (true) {
            val batchCount = chunks.size.coerceAtLeast(1)
            val payloads = chunks.mapIndexed { index, chunk ->
                buildPayload(chunk.toRawJsonArray(), index, batchCount, totalArticles)
            }
            val oversizedIndex = payloads.indexOfFirst { payload ->
                BluetoothSyncProtocol.encodedSize(payload) > BluetoothSyncProtocol.MAX_FRAME_BYTES
            }
            if (oversizedIndex < 0) return payloads
            val oversized = chunks[oversizedIndex]
            require(oversized.size > 1) {
                val item = oversized.first()
                val payloadSize = BluetoothSyncProtocol.encodedSize(payloads[oversizedIndex])
                "单篇文章蓝牙消息过大：${item.optString("title").ifBlank { item.optString("url") }.take(40)}（$payloadSize 字节）"
            }
            val midpoint = oversized.size / 2
            chunks[oversizedIndex] = oversized.take(midpoint)
            chunks.add(oversizedIndex + 1, oversized.drop(midpoint))
        }
    }

    private fun JSONObject.putBatchFields(
        batchIndex: Int?,
        batchCount: Int?,
        totalArticles: Int?
    ) {
        if (batchIndex != null && batchCount != null) {
            put("batchIndex", batchIndex)
            put("batchCount", batchCount)
        }
        if (totalArticles != null) {
            put("totalArticles", totalArticles)
        }
    }

    private fun JSONObject.putChangeSequence(changeSequence: LibraryChangeSequence?) {
        if (changeSequence == null) return
        put("supportsChangeSequences", true)
        put("fullSnapshot", changeSequence.fullSnapshot)
        put("fallbackReason", changeSequence.fallbackReason)
        put(
            "changeSeqRange",
            JSONObject().apply {
                put("fromExclusive", changeSequence.fromSeqExclusive)
                put("toInclusive", changeSequence.toSeqInclusive)
            }
        )
    }

    private fun SyncedSavedArticle.toManifestJson(): JSONObject {
        val metadata = cachedBodyMetadata ?: ArticleSyncBody.metadataFor(this)
        return JSONObject().apply {
            put("articleId", articleId)
            put("sourceDeviceId", sourceDeviceId)
            put("contentHash", contentHash)
            put("updatedAt", updatedAt)
            put("independentChangedAt", independentChangedAt)
            put("favoriteChangedAt", favoriteChangedAt)
            put("watchLaterChangedAt", watchLaterChangedAt)
            put("deletedAt", deletedAt)
            put("deleted", deleted)
            put("bodyHash", metadata.bodyHash)
            put("bodyByteCount", metadata.bodyByteCount)
            put("chunkSize", metadata.chunkSize)
            put("chunkHashes", JSONArray(metadata.chunkHashes))
            put("metadataHash", metadata.metadataHash)
            put("bodyAvailable", true)
            put("bodySyncMode", bodySyncModeForSync())
            put("readingProgress", readingProgress.coerceIn(0f, 1f))
        }
    }

    private fun SyncedArticleManifest.toJson(): JSONObject {
        return JSONObject().apply {
            put("articleId", articleId)
            put("sourceDeviceId", sourceDeviceId)
            put("contentHash", contentHash)
            put("updatedAt", updatedAt)
            put("independentChangedAt", independentChangedAt)
            put("favoriteChangedAt", favoriteChangedAt)
            put("watchLaterChangedAt", watchLaterChangedAt)
            put("deletedAt", deletedAt)
            put("deleted", deleted)
            put("bodyHash", bodyHash)
            put("bodyByteCount", bodyByteCount)
            put("chunkSize", chunkSize)
            put("chunkHashes", JSONArray(chunkHashes))
            put("metadataHash", metadataHash)
            put("bodyAvailable", bodyAvailable)
            put("bodySyncMode", bodySyncMode)
            put("readingProgress", readingProgress.coerceIn(0f, 1f))
        }
    }

    private fun SyncedSavedArticle.toJson(includeBody: Boolean = true): JSONObject {
        return JSONObject().apply {
            put("articleId", articleId)
            put("sourceDeviceId", sourceDeviceId)
            put("url", url)
            put("title", title)
            put("siteName", siteName)
            put("excerpt", excerpt)
            if (includeBody) {
                putCompressedString("contentHtmlGzip", contentHtml)
                putCompressedString("contentTextGzip", contentText)
            }
            put("imageUrl", imageUrl)
            put("contentHash", contentHash)
            put("importedAt", importedAt)
            put("updatedAt", updatedAt)
            put("independentSaved", independentSaved)
            put("independentChangedAt", independentChangedAt)
            put("independentSortOrder", independentSortOrder)
            put("rssSourceUrl", rssSourceUrl)
            put("rssSourceTitle", rssSourceTitle)
            put("favoriteSaved", favoriteSaved)
            put("favoriteChangedAt", favoriteChangedAt)
            put("favoriteSortOrder", favoriteSortOrder)
            put("watchLaterSaved", watchLaterSaved)
            put("watchLaterChangedAt", watchLaterChangedAt)
            put("watchLaterSortOrder", watchLaterSortOrder)
            put("deleted", deleted)
            put("deletedAt", deletedAt)
            put("readingProgress", readingProgress.coerceIn(0f, 1f))
        }
    }

    private fun SyncedSavedArticle.bodySyncModeForSync(): String {
        return if (
            independentSaved ||
            ImportedContentIds.isImportedContentUrl(rssSourceUrl) ||
            ImportedContentIds.isImportedContentUrl(url)
        ) {
            ARTICLE_BODY_SYNC_MODE_FULL
        } else {
            ARTICLE_BODY_SYNC_MODE_SAVED
        }
    }

    private fun Float.isMeaningfullyAheadOf(other: Float): Boolean {
        return coerceIn(0f, 1f) > other.coerceIn(0f, 1f) + READING_PROGRESS_SYNC_EPSILON
    }

    private fun SyncedSavedArticle.toChunkedJsonItems(
        request: SyncedArticleBodyRequest?,
        allowMetadataOnlyArticles: Boolean
    ): List<JSONObject> {
        val responseRequest = when {
            request?.metadataOnly == true -> request
            allowMetadataOnlyArticles && shouldRespondMetadataOnly(request) -> metadataOnlyRequest(request)
            else -> request
        }
        if (responseRequest?.metadataOnly == true) {
            return listOf(toMetadataOnlyChunkedJson(responseRequest))
        }
        val payload = if (responseRequest != null) {
            ArticleSyncBody.payloadForRequest(this, responseRequest, cachedBodyMetadata)
        } else {
            val metadata = cachedBodyMetadata ?: ArticleSyncBody.metadataFor(this)
            val bodyRequest = SyncedArticleBodyRequest(
                articleId = articleId,
                bodyHash = metadata.bodyHash,
                chunkIndexes = metadata.chunkHashes.indices.toList()
            )
            ArticleSyncBody.payloadForRequest(this, bodyRequest, metadata)
        }
        val metadata = payload.metadata
        val chunks = payload.chunks
        if (chunks.isEmpty()) {
            return listOf(toChunkedJson(metadata, emptyList()))
        }
        return chunks.map { chunk -> toChunkedJson(metadata, listOf(chunk)) }
    }

    private fun SyncedSavedArticle.shouldRespondMetadataOnly(request: SyncedArticleBodyRequest?): Boolean {
        if (deleted || request == null || request.chunkIndexes.isEmpty()) return false
        return true
    }

    private fun SyncedSavedArticle.metadataOnlyRequest(
        request: SyncedArticleBodyRequest?
    ): SyncedArticleBodyRequest {
        val bodyHash = request?.bodyHash?.takeIf { it.isNotBlank() }
            ?: cachedBodyMetadata?.bodyHash
            ?: ArticleSyncBody.metadataFor(this).bodyHash
        return SyncedArticleBodyRequest(
            articleId = articleId,
            bodyHash = bodyHash,
            chunkIndexes = emptyList(),
            metadataOnly = true
        )
    }

    private fun SyncedSavedArticle.toMetadataOnlyChunkedJson(request: SyncedArticleBodyRequest): JSONObject {
        return toJson(includeBody = false).apply {
            put(
                "body",
                JSONObject().apply {
                    put("bodyHash", request.bodyHash)
                    put("bodyByteCount", 0L)
                    put("chunkSize", 0)
                    put("chunkHashes", JSONArray())
                    put("chunks", JSONArray())
                    put("metadataOnly", true)
                }
            )
        }
    }

    private fun SyncedSavedArticle.toChunkedJson(
        metadata: ArticleBodyMetadata,
        chunks: List<SyncedArticleBodyChunk>
    ): JSONObject {
        return toJson(includeBody = false).apply {
            put(
                "body",
                JSONObject().apply {
                    put("bodyHash", metadata.bodyHash)
                    put("bodyByteCount", metadata.bodyByteCount)
                    put("chunkSize", metadata.chunkSize)
                    put("chunkHashes", JSONArray(metadata.chunkHashes))
                    put("metadataOnly", false)
                    put(
                        "chunks",
                        JSONArray().also { array ->
                            chunks.forEach { chunk ->
                                array.put(
                                    JSONObject().apply {
                                        put("index", chunk.index)
                                        put("hash", chunk.hash)
                                        put("data", ArticleSyncBody.encodeChunkData(chunk.bytes))
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
    }

    private fun buildChunkedResponse(
        deviceId: String,
        articleItems: List<JSONObject>,
        applied: Int,
        sourcesApplied: Int
    ): JSONObject {
        return JSONObject().apply {
            put("success", true)
            put("version", PROTOCOL_VERSION)
            put("action", BluetoothSyncProtocol.ACTION_SYNC_LIBRARY)
            put("phase", PHASE_COMPLETE)
            put("deviceId", deviceId)
            put("sentAt", System.currentTimeMillis())
            put("articles", articleItems.toRawJsonArray())
            putStats(articleCount = articleItems.size, applied = applied, sourcesApplied = sourcesApplied)
        }
    }

    private fun List<SyncedArticleBodyRequest>.toBodyRequestJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { request ->
                array.put(
                    JSONObject().apply {
                        put("articleId", request.articleId)
                        put("bodyHash", request.bodyHash)
                        put("chunkIndexes", JSONArray(request.chunkIndexes))
                        put("metadataOnly", request.metadataOnly)
                    }
                )
            }
        }
    }

    private fun List<SyncedArticleBodyRequest>.limitBodyRequestChunks(maxChunks: Int): List<SyncedArticleBodyRequest> {
        if (maxChunks == Int.MAX_VALUE) return this
        if (maxChunks <= 0) return filter { it.chunkIndexes.isEmpty() }
        var usedChunks = 0
        val limited = mutableListOf<SyncedArticleBodyRequest>()
        for (request in this) {
            val chunkCount = request.chunkIndexes.size
            if (chunkCount == 0) {
                limited += request
                continue
            }
            if (usedChunks == 0 && chunkCount > maxChunks) {
                limited += request
                usedChunks += chunkCount
                continue
            }
            if (usedChunks + chunkCount > maxChunks) continue
            limited += request
            usedChunks += chunkCount
        }
        return limited
    }

    private fun JSONObject.putStats(articleCount: Int, applied: Int, sourcesApplied: Int) {
        put(
            "stats",
            JSONObject().apply {
                put("sent", articleCount)
                put("applied", applied)
                put("sourcesSent", 0)
                put("sourcesApplied", sourcesApplied)
            }
        )
    }

    private fun SyncedRssSource.toJson(): JSONObject {
        return JSONObject().apply {
            put("url", url)
            put("sourceDeviceId", sourceDeviceId)
            put("title", title)
            put("description", description)
            put("siteUrl", siteUrl)
            put("imageUrl", imageUrl)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("sortOrder", sortOrder)
            put("isPinned", isPinned)
            put("deleted", deleted)
            put("deletedAt", deletedAt)
        }
    }

    private fun JSONObject.putCompressedString(name: String, value: String?) {
        val safe = value?.takeIf { it.isNotBlank() } ?: return
        put(name, Base64.getEncoder().encodeToString(gzip(safe)))
    }

    private fun JSONObject.optCompressedString(name: String): String? {
        val encoded = optString(name).takeIf { it.isNotBlank() } ?: return null
        return runCatching { gunzip(Base64.getDecoder().decode(encoded)) }.getOrNull()
    }

    private fun JSONObject.optStringArray(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONObject.optIntArray(name: String): List<Int> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.optInt(index))
            }
        }
    }

    private fun gzip(value: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gzip ->
            gzip.write(value.toByteArray(Charsets.UTF_8))
        }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): String {
        return GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
            gzip.readBytes().toString(Charsets.UTF_8)
        }
    }

    private const val ARTICLE_BATCH_TARGET_BYTES = 384 * 1024
    private const val MAX_BATCH_COUNT_FOR_SIZING = 9999
    private const val CHANGE_SEQUENCE_PROTOCOL_VERSION = 8
    private const val METADATA_ONLY_ARTICLES_PROTOCOL_VERSION = 8
    private const val READING_PROGRESS_SYNC_EPSILON = 0.001f
}
