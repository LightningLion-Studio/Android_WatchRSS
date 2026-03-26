package com.lightningstudio.watchrss.data.bili

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lightningstudio.watchrss.data.cache.CacheTrimReason
import com.lightningstudio.watchrss.data.cache.ManagedCacheBucket
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.debug.DebugLogBuffer
import com.lightningstudio.watchrss.sdk.bili.BiliAccount
import com.lightningstudio.watchrss.sdk.bili.BiliClient
import com.lightningstudio.watchrss.sdk.bili.BiliCommentPage
import com.lightningstudio.watchrss.sdk.bili.BiliCommentReplyPage
import com.lightningstudio.watchrss.sdk.bili.BiliCookies
import com.lightningstudio.watchrss.sdk.bili.BiliFavoriteFolder
import com.lightningstudio.watchrss.sdk.bili.BiliFavoritePage
import com.lightningstudio.watchrss.sdk.bili.BiliFeedPage
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryCursor
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryPage
import com.lightningstudio.watchrss.sdk.bili.BiliHotSearchResponse
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import com.lightningstudio.watchrss.sdk.bili.BiliPlayUrl
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.sdk.bili.BiliSearchResponse
import com.lightningstudio.watchrss.sdk.bili.BiliSdkConfig
import com.lightningstudio.watchrss.sdk.bili.BiliToViewPage
import com.lightningstudio.watchrss.sdk.bili.BiliVideoDetail
import com.lightningstudio.watchrss.sdk.bili.EncryptedBiliAccountStore
import com.lightningstudio.watchrss.sdk.bili.QrPollResult
import com.lightningstudio.watchrss.sdk.bili.QrPollStatus
import com.lightningstudio.watchrss.sdk.bili.WebQrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.LinkedHashMap
import javax.net.ssl.SSLException
import kotlin.math.max
import kotlin.math.min

private val FEED_CACHE_JSON = stringPreferencesKey("bili_feed_cache_json")
private val FEED_CACHE_AT = longPreferencesKey("bili_feed_cache_at")
private val SEARCH_HISTORY_JSON = stringPreferencesKey("bili_search_history")
private val LOCAL_INTERACTION_STATE_JSON = stringPreferencesKey("bili_local_interaction_state_json")
private val LOCAL_PLAYBACK_PROGRESS_JSON = stringPreferencesKey("bili_playback_progress_json")
private const val FEED_CACHE_LIMIT = 50
private const val PREVIEW_CACHE_QN = 32
private const val PREVIEW_CACHE_MS = 30 * 60 * 1000L
private const val DETAIL_PREVIEW_CACHE_MS = 60 * 1000L
private const val PLAYBACK_SOURCE_CACHE_TTL_MS = 5 * 60 * 1000L
private const val PLAYBACK_SOURCE_CACHE_LIMIT = 24

class BiliRepository(
    context: Context,
    private val dataStore: DataStore<Preferences>,
    private val cacheService: ManagedCacheService? = null,
    private val playbackCacheManager: BiliPlaybackCacheManager? = null
) : BiliRepositoryContract {
    private val appContext = context.applicationContext
    private val accountStore = EncryptedBiliAccountStore(context)
    private val client = BiliClient(BiliSdkConfig(), accountStore)
    private val previewCacheDir = File(appContext.filesDir, PREVIEW_CACHE_DIR_NAME).apply { mkdirs() }
    private val downloadClient = OkHttpClient.Builder()
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val playbackSourceCacheLock = Any()
    private val playbackSourceCache = LinkedHashMap<String, CachedPlaybackSource>()
    private val playbackSourceResolutionLocks = LinkedHashMap<String, Mutex>()

    override suspend fun isLoggedIn(): Boolean {
        val account = accountStore.read()
        return !account?.cookies?.get("SESSDATA").isNullOrBlank()
    }

    override suspend fun readAccount(): BiliAccount? = accountStore.read()

    override suspend fun clearAccount() {
        accountStore.write(BiliAccount())
    }

    override suspend fun logoutAndClearPreviewCache() {
        clearAccount()
        clearLocalInteractionState()
        clearLocalPlaybackProgress()
        clearCachedPlaybackSources()
        playbackCacheManager?.clearAll()
        if (cacheService != null) {
            cacheService.clearBucket(ManagedCacheBucket.BILI_PREVIEW)
        } else {
            previewCacheDir.deleteRecursively()
            previewCacheDir.mkdirs()
        }
    }

    override suspend fun applyCookieHeader(rawCookie: String): Result<Unit> {
        val cookies = BiliCookies.parseCookieHeader(rawCookie)
        if (cookies.isEmpty()) {
            return Result.failure(IllegalArgumentException("缺少有效 Cookie"))
        }
        if (cookies["SESSDATA"].isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("missing_cookie:SESSDATA"))
        }
        if (cookies["bili_jct"].isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("missing_cookie:bili_jct"))
        }
        client.auth.applyCookies(cookies)
        debugLogAuth("cookie", "applied")
        return Result.success(Unit)
    }

    override suspend fun requestWebQrCode(): WebQrCode? = safeNullableCall { client.auth.requestWebQrCode() }

    override suspend fun pollWebQrCode(qrKey: String): QrPollResult {
        val result = safeQrPoll { client.auth.pollWebQrCode(qrKey) }
        if (result.status == QrPollStatus.SUCCESS) {
            debugLogAuth("web_qr", "success")
        }
        return result
    }

    override suspend fun fetchFeed(): BiliResult<BiliFeedPage> {
        debugLogFeed(phase = "start")
        val result = safeCall { client.feed.fetchDefaultFeed() }
        debugLogFeed(
            phase = "result",
            result = result,
            source = result.data?.source,
            itemCount = result.data?.items?.size
        )
        return result
    }

    override suspend fun readFeedCache(): List<BiliItem> = withContext(Dispatchers.IO) {
        val raw = dataStore.data.first()[FEED_CACHE_JSON].orEmpty()
        if (raw.isBlank()) return@withContext emptyList()
        parseFeedCache(raw)
    }

    override suspend fun writeFeedCache(items: List<BiliItem>) {
        val trimmed = items.take(FEED_CACHE_LIMIT)
        val raw = buildFeedCacheJson(trimmed)
        dataStore.edit { preferences ->
            preferences[FEED_CACHE_JSON] = raw
            preferences[FEED_CACHE_AT] = System.currentTimeMillis()
        }
    }

    override suspend fun fetchVideoDetail(aid: Long?, bvid: String?): BiliResult<BiliVideoDetail> {
        debugLogDetail(
            phase = "start",
            aid = aid,
            bvid = bvid
        )
        val result = safeCall { client.video.fetchView(aid = aid, bvid = bvid, useWbi = true) }
        debugLogDetail(
            phase = "result",
            aid = aid,
            bvid = bvid,
            result = result
        )
        return result
    }

    override suspend fun fetchRemoteInteractionState(
        aid: Long?,
        bvid: String?
    ): BiliResult<BiliInteractionState> {
        debugLogRelation(
            phase = "start",
            aid = aid,
            bvid = bvid
        )
        val result = safeCall {
            val relation = client.video.fetchRelation(aid = aid, bvid = bvid)
            if (!relation.isSuccess) {
                return@safeCall BiliResult(
                    code = relation.code,
                    message = relation.message,
                    httpCode = relation.httpCode,
                    requestMode = relation.requestMode
                )
            }
            val data = relation.data ?: com.lightningstudio.watchrss.sdk.bili.BiliVideoInteraction()
            BiliResult(
                code = relation.code,
                message = relation.message,
                data = BiliInteractionState(
                    isLiked = data.like == true,
                    isCoined = data.coin == true
                ),
                httpCode = relation.httpCode,
                requestMode = relation.requestMode
            )
        }
        debugLogRelation(
            phase = "result",
            aid = aid,
            bvid = bvid,
            result = result
        )
        return result
    }

    override suspend fun readLocalInteractionState(aid: Long?, bvid: String?): BiliInteractionState =
        withContext(Dispatchers.IO) {
            val raw = dataStore.data.first()[LOCAL_INTERACTION_STATE_JSON].orEmpty()
            if (raw.isBlank()) return@withContext BiliInteractionState()
            findBiliInteractionState(
                records = parseBiliInteractionRecords(raw),
                aid = aid,
                bvid = bvid
            )
        }

    override suspend fun writeLocalInteractionState(aid: Long?, bvid: String?, state: BiliInteractionState) {
        withContext(Dispatchers.IO) {
            dataStore.edit { preferences ->
                val current = parseBiliInteractionRecords(
                    preferences[LOCAL_INTERACTION_STATE_JSON].orEmpty()
                )
                val updated = upsertBiliInteractionState(
                    records = current,
                    aid = aid,
                    bvid = bvid,
                    state = state
                )
                if (updated.isEmpty()) {
                    preferences.remove(LOCAL_INTERACTION_STATE_JSON)
                } else {
                    preferences[LOCAL_INTERACTION_STATE_JSON] = buildBiliInteractionRecordsJson(updated)
                }
            }
        }
    }

    override suspend fun readLatestPlaybackProgress(aid: Long?, bvid: String?): BiliPlaybackProgress? =
        withContext(Dispatchers.IO) {
            val raw = dataStore.data.first()[LOCAL_PLAYBACK_PROGRESS_JSON].orEmpty()
            if (raw.isBlank()) return@withContext null
            findLatestBiliPlaybackProgress(
                records = parseBiliPlaybackProgressRecords(raw),
                aid = aid,
                bvid = bvid
            )
        }

    override suspend fun readPlaybackProgress(aid: Long?, bvid: String?, cid: Long): BiliPlaybackProgress? =
        withContext(Dispatchers.IO) {
            val raw = dataStore.data.first()[LOCAL_PLAYBACK_PROGRESS_JSON].orEmpty()
            if (raw.isBlank()) return@withContext null
            findBiliPlaybackProgress(
                records = parseBiliPlaybackProgressRecords(raw),
                aid = aid,
                bvid = bvid,
                cid = cid
            )
        }

    override suspend fun writePlaybackProgress(progress: BiliPlaybackProgress) {
        withContext(Dispatchers.IO) {
            dataStore.edit { preferences ->
                val current = parseBiliPlaybackProgressRecords(
                    preferences[LOCAL_PLAYBACK_PROGRESS_JSON].orEmpty()
                )
                val updated = upsertBiliPlaybackProgress(
                    records = current,
                    progress = progress
                )
                if (updated.isEmpty()) {
                    preferences.remove(LOCAL_PLAYBACK_PROGRESS_JSON)
                } else {
                    preferences[LOCAL_PLAYBACK_PROGRESS_JSON] = buildBiliPlaybackProgressRecordsJson(updated)
                }
            }
        }
    }

    override suspend fun clearPlaybackProgress(aid: Long?, bvid: String?, cid: Long) {
        withContext(Dispatchers.IO) {
            dataStore.edit { preferences ->
                val current = parseBiliPlaybackProgressRecords(
                    preferences[LOCAL_PLAYBACK_PROGRESS_JSON].orEmpty()
                )
                val updated = removeBiliPlaybackProgress(
                    records = current,
                    aid = aid,
                    bvid = bvid,
                    cid = cid
                )
                if (updated.isEmpty()) {
                    preferences.remove(LOCAL_PLAYBACK_PROGRESS_JSON)
                } else {
                    preferences[LOCAL_PLAYBACK_PROGRESS_JSON] = buildBiliPlaybackProgressRecordsJson(updated)
                }
            }
        }
    }

    override suspend fun fetchPlayUrlMp4(
        cid: Long,
        aid: Long?,
        bvid: String?,
        qn: Int
    ): BiliResult<BiliPlayUrl> = safeCall {
        client.play.fetchMp4Url(
            cid = cid,
            aid = aid,
            bvid = bvid,
            qn = qn
        )
    }

    override suspend fun resolvePlaybackSource(
        aid: Long?,
        bvid: String?,
        cid: Long,
        qn: Int
    ): BiliResult<BiliResolvedPlaybackSource> {
        readCachedPlaybackSource(aid = aid, bvid = bvid, cid = cid, qn = qn)?.let { cached ->
            return BiliResult(code = 0, data = cached)
        }
        val sourceCacheKey = playbackSourceCacheKey(aid = aid, bvid = bvid, cid = cid, qn = qn)
        return obtainPlaybackSourceResolutionLock(sourceCacheKey).withLock {
            readCachedPlaybackSource(aid = aid, bvid = bvid, cid = cid, qn = qn)?.let { cached ->
                return@withLock BiliResult(code = 0, data = cached)
            }
            val result = fetchPlayUrlMp4(
                cid = cid,
                aid = aid,
                bvid = bvid,
                qn = qn
            )
            if (!result.isSuccess) {
                return@withLock BiliResult(code = result.code, message = result.message)
            }
            val playUrl = result.data
                ?: return@withLock BiliResult(BiliErrorCodes.PLAY_URL_EMPTY, "empty_play_url")
            val durl = playUrl.durl.firstOrNull()
                ?: return@withLock BiliResult(BiliErrorCodes.PLAY_URL_EMPTY, "empty_play_url")
            val url = durl.url?.trim().takeUnless { it.isNullOrEmpty() }
                ?: return@withLock BiliResult(BiliErrorCodes.PLAY_URL_EMPTY, "empty_play_url")
            val quality = playUrl.quality ?: qn
            val resolved = BiliResolvedPlaybackSource(
                cid = cid,
                url = url,
                headers = buildPlayHeaders(),
                cacheKey = BiliPlaybackCacheManager.buildCacheKey(
                    aid = aid,
                    bvid = bvid,
                    cid = cid,
                    quality = quality
                ),
                quality = quality,
                detailPreviewBytes = BiliPlaybackCacheManager.estimatePreviewBytes(
                    durl = durl,
                    maxPreviewMs = DETAIL_PREVIEW_CACHE_MS
                )
            )
            rememberCachedPlaybackSource(
                aid = aid,
                bvid = bvid,
                cid = cid,
                qn = qn,
                source = resolved
            )
            BiliResult(code = 0, data = resolved)
        }
    }

    override suspend fun warmupDetailPreview(aid: Long?, bvid: String?, cid: Long?): Result<Unit> {
        val manager = playbackCacheManager ?: return Result.success(Unit)
        val safeCid = cid ?: return Result.failure(IllegalArgumentException("missing_cid"))
        val result = resolvePlaybackSource(
            aid = aid,
            bvid = bvid,
            cid = safeCid,
            qn = PREVIEW_CACHE_QN
        )
        if (!result.isSuccess) {
            return Result.failure(IllegalStateException(result.message ?: "fetch_failed"))
        }
        val source = result.data ?: return Result.failure(IllegalStateException("empty_play_url"))
        val previewBytes = source.detailPreviewBytes
            ?.takeIf { it > 0L }
            ?: return Result.success(Unit)
        return manager.prefetch(
            url = source.url,
            headers = source.headers,
            cacheKey = source.cacheKey,
            lengthBytes = previewBytes
        )
    }

    override suspend fun ensureInteractionReady(aid: Long?, bvid: String?, cid: Long?): Result<Unit> {
        if (cachedPreviewUri(aid, bvid, cid) != null) {
            return Result.success(Unit)
        }
        val safeCid = cid ?: return Result.failure(IllegalArgumentException("missing_cid"))
        val result = fetchPlayUrlMp4(
            cid = safeCid,
            aid = aid,
            bvid = bvid,
            qn = PREVIEW_CACHE_QN
        )
        return if (result.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(result.message ?: "fetch_failed"))
        }
    }

    override suspend fun like(aid: Long, like: Boolean): BiliResult<Unit> {
        return performWebAction(
            action = "like",
            aid = aid,
            startExtra = "like=$like"
        ) {
            client.action.like(aid, like)
        }
    }

    override suspend fun coin(aid: Long, multiply: Int, selectLike: Boolean): BiliResult<Boolean> {
        return performWebAction(
            action = "coin",
            aid = aid,
            startExtra = "multiply=$multiply selectLike=$selectLike",
            resultExtra = { result -> "likeResult=${result.data}" }
        ) {
            client.action.coin(aid, multiply, selectLike)
        }
    }

    override suspend fun triple(aid: Long): BiliResult<com.lightningstudio.watchrss.sdk.bili.BiliTripleResult> {
        return performWebAction(
            action = "triple",
            aid = aid
        ) {
            client.action.triple(aid)
        }
    }

    override suspend fun favorite(aid: Long, add: Boolean): BiliResult<Boolean> {
        val folderId = defaultFavoriteFolderId()
            ?: return BiliResult(BiliErrorCodes.MISSING_FAVORITE_FOLDER, "missing_favorite_folder")
        val addIds = if (add) listOf(folderId) else emptyList()
        val delIds = if (add) emptyList() else listOf(folderId)
        return performWebAction(
            action = "favorite",
            aid = aid,
            startExtra = "add=$add"
        ) {
            client.action.favorite(
                aid,
                addMediaIds = addIds,
                delMediaIds = delIds
            )
        }
    }

    override suspend fun addToView(aid: Long?, bvid: String?): BiliResult<Unit> =
        safeCall { client.history.addToView(aid, bvid) }

    override suspend fun fetchToView(): BiliResult<BiliToViewPage> =
        safeCall { client.history.fetchToView() }

    override suspend fun fetchHistory(cursor: BiliHistoryCursor?): BiliResult<BiliHistoryPage> =
        safeCall { client.history.fetchHistory(cursor) }

    override suspend fun fetchFavoriteFolders(): BiliResult<List<BiliFavoriteFolder>> = safeCall {
        val mid = currentUserMid() ?: return@safeCall BiliResult(BiliErrorCodes.MISSING_MID, "missing_mid")
        client.favorite.listFolders(mid)
    }

    override suspend fun fetchFavoriteItems(mediaId: Long, pn: Int, ps: Int): BiliResult<BiliFavoritePage> =
        safeCall { client.favorite.listResources(mediaId = mediaId, pn = pn, ps = ps) }

    // Search methods
    override suspend fun getHotSearch(): BiliResult<BiliHotSearchResponse> =
        safeCall { client.search.getHotSearch() }

    override suspend fun searchAll(keyword: String, page: Int): BiliResult<BiliSearchResponse> =
        safeCall { client.search.searchAll(keyword, page) }

    override suspend fun getSearchHistory(): List<String> = withContext(Dispatchers.IO) {
        val raw = dataStore.data.first()[SEARCH_HISTORY_JSON].orEmpty()
        if (raw.isBlank()) return@withContext emptyList()
        parseSearchHistory(raw)
    }

    override suspend fun addSearchHistory(keyword: String) {
        val history = getSearchHistory().toMutableList()
        history.remove(keyword)
        history.add(0, keyword)
        val trimmed = history.take(20)
        val raw = buildSearchHistoryJson(trimmed)
        dataStore.edit { preferences ->
            preferences[SEARCH_HISTORY_JSON] = raw
        }
    }

    override suspend fun clearSearchHistory() {
        dataStore.edit { preferences ->
            preferences.remove(SEARCH_HISTORY_JSON)
        }
    }

    // Comment methods
    override suspend fun getComments(oid: Long, next: Long): BiliResult<BiliCommentPage> =
        safeCall { client.comment.getComments(oid, next) }

    override suspend fun getReplies(oid: Long, root: Long, pn: Int): BiliResult<BiliCommentReplyPage> =
        safeCall { client.comment.getReplies(oid, root, pn) }

    override suspend fun buildPlayHeaders(): Map<String, String> {
        val account = accountStore.read()
        val cookies = account?.cookies?.takeIf { it.isNotEmpty() }
        val headers = mutableMapOf(
            "User-Agent" to client.config.webUserAgent,
            "Referer" to client.config.webReferer
        )
        if (cookies != null) {
            headers["Cookie"] = cookies.entries.joinToString("; ") { (k, v) -> "$k=$v" }
        }
        return headers
    }

    override fun shareLink(bvid: String?, aid: Long?): String? {
        return when {
            !bvid.isNullOrBlank() -> "https://www.bilibili.com/video/$bvid"
            aid != null -> "https://www.bilibili.com/video/av$aid"
            else -> null
        }
    }

    override fun savedLink(bvid: String?, aid: Long?, cid: Long?): String? {
        val base = shareLink(bvid, aid) ?: return null
        val safeCid = cid ?: return base
        return "$base?cid=$safeCid"
    }

    override suspend fun cachedPreviewUri(aid: Long?, bvid: String?, cid: Long?): String? {
        return withContext(Dispatchers.IO) {
            val file = previewCacheFile(aid, bvid, cid) ?: return@withContext null
            if (file.exists() && file.length() > 0) {
                touchPreviewFile(file)
                file.toURI().toString()
            } else {
                null
            }
        }
    }

    override suspend fun cachedPreviewUriAny(aid: Long?, bvid: String?): String? = withContext(Dispatchers.IO) {
        val key = when {
            !bvid.isNullOrBlank() -> bvid
            aid != null -> "av$aid"
            else -> null
        } ?: return@withContext null
        val prefix = "${key}_"
        val suffix = "_q$PREVIEW_CACHE_QN.mp4"
        val match = previewCacheDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.length() > 0 && it.name.startsWith(prefix) && it.name.endsWith(suffix) }
            ?.maxByOrNull { it.lastModified() }
        match?.let(::touchPreviewFile)
        match?.toURI()?.toString()
    }

    override suspend fun cachePreviewClip(
        aid: Long?,
        bvid: String?,
        cid: Long?
    ): Result<String> = cachePreviewClipInternal(
        aid = aid,
        bvid = bvid,
        cid = cid,
        maxPreviewMs = PREVIEW_CACHE_MS
    )

    private suspend fun cachePreviewClipInternal(
        aid: Long?,
        bvid: String?,
        cid: Long?,
        maxPreviewMs: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        val file = previewCacheFile(aid, bvid, cid)
            ?: return@withContext Result.failure(IllegalArgumentException("missing_video_id"))
        if (file.exists() && file.length() > 0) {
            touchPreviewFile(file)
            return@withContext Result.success(file.absolutePath)
        }
        val safeCid = cid ?: return@withContext Result.failure(IllegalArgumentException("missing_cid"))
        val result = fetchPlayUrlMp4(
            cid = safeCid,
            aid = aid,
            bvid = bvid,
            qn = PREVIEW_CACHE_QN
        )
        if (!result.isSuccess) {
            return@withContext Result.failure(IllegalStateException(result.message ?: "fetch_failed"))
        }
        downloadPreviewClip(
            aid = aid,
            bvid = bvid,
            cid = safeCid,
            playUrl = result.data,
            maxPreviewMs = maxPreviewMs
        )
    }

    private suspend fun downloadPreviewClip(
        aid: Long?,
        bvid: String?,
        cid: Long,
        playUrl: BiliPlayUrl?,
        maxPreviewMs: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        val file = previewCacheFile(aid, bvid, cid)
            ?: return@withContext Result.failure(IllegalArgumentException("missing_video_id"))
        if (file.exists() && file.length() > 0) {
            touchPreviewFile(file)
            return@withContext Result.success(file.absolutePath)
        }
        val durl = playUrl?.durl?.firstOrNull()
            ?: return@withContext Result.failure(IllegalStateException("empty_play_url"))
        val url = durl.url
            ?: return@withContext Result.failure(IllegalStateException("empty_play_url"))
        val size = durl.size
            ?: return@withContext Result.failure(IllegalStateException("missing_size"))
        val lengthMs = durl.length
            ?: return@withContext Result.failure(IllegalStateException("missing_length"))
        if (size <= 0 || lengthMs <= 0) {
            return@withContext Result.failure(IllegalStateException("invalid_length"))
        }
        val targetMs = min(maxPreviewMs, lengthMs)
        val bytes = max(1L, size * targetMs / lengthMs) - 1L
        val headers = buildPlayHeaders()
        val request = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=0-$bytes")
            .apply {
                headers.forEach { (key, value) -> addHeader(key, value) }
            }
            .build()
        val tempFile = File(previewCacheDir, "${file.name}.tmp")
        runCatching {
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("download_failed:${response.code}")
                }
                response.body?.byteStream()?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("empty_body")
            }
            if (tempFile.length() <= 0) {
                throw IllegalStateException("empty_file")
            }
            if (file.exists()) {
                file.delete()
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
            touchPreviewFile(file)
            cacheService?.scheduleMaintenance(CacheTrimReason.CACHE_WRITE)
            Result.success(file.absolutePath)
        }.getOrElse { error ->
            runCatching { tempFile.delete() }
            runCatching { file.delete() }
            Result.failure(error)
        }
    }

    override suspend fun clearCachedPreview(aid: Long?, bvid: String?, cid: Long?) {
        withContext(Dispatchers.IO) {
            val file = previewCacheFile(aid, bvid, cid) ?: return@withContext
            if (file.exists()) {
                runCatching { file.delete() }
                cacheService?.scheduleMaintenance(CacheTrimReason.CACHE_DELETE)
            }
        }
    }

    private suspend fun currentUserMid(): Long? {
        val account = accountStore.read()
        val raw = account?.cookies?.get("DedeUserID") ?: return null
        return raw.toLongOrNull()
    }

    private suspend fun defaultFavoriteFolderId(): Long? {
        val folders = fetchFavoriteFolders()
        if (!folders.isSuccess) return null
        val first = folders.data?.firstOrNull() ?: return null
        return first.id ?: first.fid
    }

    private suspend fun clearLocalInteractionState() {
        dataStore.edit { preferences ->
            preferences.remove(LOCAL_INTERACTION_STATE_JSON)
        }
    }

    private suspend fun clearLocalPlaybackProgress() {
        dataStore.edit { preferences ->
            preferences.remove(LOCAL_PLAYBACK_PROGRESS_JSON)
        }
    }

    private fun readCachedPlaybackSource(
        aid: Long?,
        bvid: String?,
        cid: Long,
        qn: Int
    ): BiliResolvedPlaybackSource? {
        val key = playbackSourceCacheKey(aid = aid, bvid = bvid, cid = cid, qn = qn)
        val now = System.currentTimeMillis()
        return synchronized(playbackSourceCacheLock) {
            val entry = playbackSourceCache[key] ?: return@synchronized null
            if (now - entry.cachedAtMs > PLAYBACK_SOURCE_CACHE_TTL_MS) {
                playbackSourceCache.remove(key)
                null
            } else {
                entry.source
            }
        }
    }

    private fun rememberCachedPlaybackSource(
        aid: Long?,
        bvid: String?,
        cid: Long,
        qn: Int,
        source: BiliResolvedPlaybackSource
    ) {
        val key = playbackSourceCacheKey(aid = aid, bvid = bvid, cid = cid, qn = qn)
        synchronized(playbackSourceCacheLock) {
            playbackSourceCache.remove(key)
            playbackSourceCache[key] = CachedPlaybackSource(
                source = source,
                cachedAtMs = System.currentTimeMillis()
            )
            while (playbackSourceCache.size > PLAYBACK_SOURCE_CACHE_LIMIT) {
                val eldestKey = playbackSourceCache.entries.firstOrNull()?.key ?: break
                playbackSourceCache.remove(eldestKey)
            }
        }
    }

    private fun clearCachedPlaybackSources() {
        synchronized(playbackSourceCacheLock) {
            playbackSourceCache.clear()
            playbackSourceResolutionLocks.clear()
        }
    }

    private fun obtainPlaybackSourceResolutionLock(key: String): Mutex {
        return synchronized(playbackSourceCacheLock) {
            playbackSourceResolutionLocks[key] ?: Mutex().also { created ->
                playbackSourceResolutionLocks[key] = created
            }
        }
    }

    private fun playbackSourceCacheKey(
        aid: Long?,
        bvid: String?,
        cid: Long,
        qn: Int
    ): String {
        val videoKey = when {
            !bvid.isNullOrBlank() -> "bv:${bvid.trim()}"
            aid != null -> "av:$aid"
            else -> "cid:$cid"
        }
        return "$videoKey:$cid:q$qn"
    }

    private fun buildFeedCacheJson(items: List<BiliItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(toCacheJson(item))
        }
        return array.toString()
    }

    private fun toCacheJson(item: BiliItem): JSONObject {
        val obj = JSONObject()
        item.aid?.let { obj.put("aid", it) }
        item.bvid?.let { obj.put("bvid", it) }
        item.cid?.let { obj.put("cid", it) }
        item.title?.let { obj.put("title", it) }
        item.cover?.let { obj.put("cover", it) }
        item.duration?.let { obj.put("duration", it) }
        item.pubdate?.let { obj.put("pubdate", it) }
        item.owner?.name?.let { obj.put("owner", it) }
        item.stat?.view?.let { obj.put("view", it) }
        item.stat?.like?.let { obj.put("like", it) }
        item.stat?.danmaku?.let { obj.put("danmaku", it) }
        return obj
    }

    private fun parseFeedCache(raw: String): List<BiliItem> {
        return runCatching {
            val array = JSONArray(raw)
            val items = mutableListOf<BiliItem>()
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                items.add(fromCacheJson(obj))
            }
            items
        }.getOrDefault(emptyList())
    }

    private fun fromCacheJson(obj: JSONObject): BiliItem {
        val aid = obj.optLong("aid", -1L).takeIf { it > 0 }
        val bvid = obj.optString("bvid", "").takeIf { it.isNotBlank() }
        val cid = obj.optLong("cid", -1L).takeIf { it > 0 }
        val title = obj.optString("title", "").takeIf { it.isNotBlank() }
        val cover = obj.optString("cover", "").takeIf { it.isNotBlank() }
        val duration = obj.optInt("duration", -1).takeIf { it > 0 }
        val pubdate = obj.optLong("pubdate", -1L).takeIf { it > 0 }
        val ownerName = obj.optString("owner", "").takeIf { it.isNotBlank() }
        val view = obj.optLong("view", -1L).takeIf { it >= 0 }
        val like = obj.optLong("like", -1L).takeIf { it >= 0 }
        val danmaku = obj.optLong("danmaku", -1L).takeIf { it >= 0 }
        val owner = ownerName?.let { com.lightningstudio.watchrss.sdk.bili.BiliOwner(name = it) }
        val stat = if (view != null || like != null || danmaku != null) {
            com.lightningstudio.watchrss.sdk.bili.BiliStat(
                view = view,
                like = like,
                danmaku = danmaku
            )
        } else {
            null
        }
        return BiliItem(
            aid = aid,
            bvid = bvid,
            cid = cid,
            title = title,
            cover = cover,
            duration = duration,
            pubdate = pubdate,
            owner = owner,
            stat = stat
        )
    }

    private fun previewCacheFile(aid: Long?, bvid: String?, cid: Long?): File? {
        val key = when {
            !bvid.isNullOrBlank() -> bvid
            aid != null -> "av$aid"
            else -> null
        } ?: return null
        val safeCid = cid ?: 0L
        return File(previewCacheDir, "${key}_${safeCid}_q$PREVIEW_CACHE_QN.mp4")
    }

    private fun touchPreviewFile(file: File) {
        runCatching { file.setLastModified(System.currentTimeMillis()) }
    }

    private fun errorMessage(error: Throwable): String {
        return when (error) {
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException,
            is SSLException,
            is IOException -> "网络不可用"
            else -> error.message ?: "请求失败"
        }
    }

    private suspend fun debugLogAction(
        action: String,
        aid: Long,
        phase: String,
        extra: String? = null,
        result: BiliResult<*>? = null,
        modeOverride: String? = null
    ) {
        if (!DebugLogBuffer.isEnabled()) return
        val flags = readDebugAccountFlags()
        val mode = modeOverride ?: result?.requestMode ?: "web"
        val extraPart = if (extra.isNullOrBlank()) "" else " $extra"
        val resultPart = result?.let {
            " code=${it.code} http=${it.httpCode} resultMode=${it.requestMode} msg=${it.message}"
        }.orEmpty()
        DebugLogBuffer.log(
            "bili",
            "action=$action phase=$phase mode=$mode aid=$aid accessKey=${flags.hasAccessKey} " +
                "sess=${flags.hasSessdata} csrf=${flags.hasCsrf} buvid3=${flags.hasBuvid3} " +
                "buvid4=${flags.hasBuvid4} bnut=${flags.hasBNut} ticket=${flags.hasTicket}$extraPart$resultPart"
        )
    }

    private suspend fun debugLogAuth(source: String, phase: String) {
        if (!DebugLogBuffer.isEnabled()) return
        val flags = readDebugAccountFlags()
        DebugLogBuffer.log(
            "bili",
            "auth=$source phase=$phase mode=web accessKey=${flags.hasAccessKey} " +
                "sess=${flags.hasSessdata} csrf=${flags.hasCsrf} buvid3=${flags.hasBuvid3} " +
                "buvid4=${flags.hasBuvid4} bnut=${flags.hasBNut} ticket=${flags.hasTicket}"
        )
    }

    private suspend fun debugLogFeed(
        phase: String,
        result: BiliResult<BiliFeedPage>? = null,
        source: com.lightningstudio.watchrss.sdk.bili.BiliFeedSource? = null,
        itemCount: Int? = null
    ) {
        if (!DebugLogBuffer.isEnabled()) return
        val flags = readDebugAccountFlags()
        val sourcePart = source?.name ?: "-"
        val countPart = itemCount?.toString() ?: "-"
        val resultPart = result?.let {
            " code=${it.code} http=${it.httpCode} mode=${it.requestMode} msg=${it.message}"
        }.orEmpty()
        DebugLogBuffer.log(
            "bili",
            "feed phase=$phase source=$sourcePart items=$countPart accessKey=${flags.hasAccessKey} " +
                "sess=${flags.hasSessdata} csrf=${flags.hasCsrf} buvid3=${flags.hasBuvid3} " +
                "buvid4=${flags.hasBuvid4} bnut=${flags.hasBNut} ticket=${flags.hasTicket}$resultPart"
        )
    }

    private suspend fun debugLogDetail(
        phase: String,
        aid: Long?,
        bvid: String?,
        result: BiliResult<BiliVideoDetail>? = null
    ) {
        if (!DebugLogBuffer.isEnabled()) return
        val flags = readDebugAccountFlags()
        val interaction = result?.data?.interaction
        val aidPart = result?.data?.item?.aid ?: aid
        val bvidPart = result?.data?.item?.bvid ?: bvid
        val interactionPart = if (interaction == null) {
            " interaction=-"
        } else {
            " interaction.like=${interaction.like} interaction.coin=${interaction.coin} " +
                "interaction.favorite=${interaction.favorite}"
        }
        val resultPart = result?.let {
            " code=${it.code} http=${it.httpCode} mode=${it.requestMode} msg=${it.message}"
        }.orEmpty()
        DebugLogBuffer.log(
            "bili",
            "detail phase=$phase aid=$aidPart bvid=$bvidPart accessKey=${flags.hasAccessKey} " +
                "sess=${flags.hasSessdata} csrf=${flags.hasCsrf} buvid3=${flags.hasBuvid3} " +
                "buvid4=${flags.hasBuvid4} bnut=${flags.hasBNut} ticket=${flags.hasTicket}" +
                "$interactionPart$resultPart"
        )
    }

    private suspend fun debugLogRelation(
        phase: String,
        aid: Long?,
        bvid: String?,
        result: BiliResult<BiliInteractionState>? = null
    ) {
        if (!DebugLogBuffer.isEnabled()) return
        val flags = readDebugAccountFlags()
        val statePart = result?.data?.let {
            " state.like=${it.isLiked} state.coin=${it.isCoined}"
        }.orEmpty()
        val resultPart = result?.let {
            " code=${it.code} http=${it.httpCode} mode=${it.requestMode} msg=${it.message}"
        }.orEmpty()
        DebugLogBuffer.log(
            "bili",
            "relation phase=$phase aid=$aid bvid=$bvid accessKey=${flags.hasAccessKey} " +
                "sess=${flags.hasSessdata} csrf=${flags.hasCsrf} buvid3=${flags.hasBuvid3} " +
                "buvid4=${flags.hasBuvid4} bnut=${flags.hasBNut} ticket=${flags.hasTicket}" +
                "$statePart$resultPart"
        )
    }

    private suspend fun readDebugAccountFlags(): DebugAccountFlags {
        val account = accountStore.read()
        val cookies = account?.cookies.orEmpty()
        return DebugAccountFlags(
            hasAccessKey = !account?.accessToken.isNullOrBlank(),
            hasSessdata = !cookies["SESSDATA"].isNullOrBlank(),
            hasCsrf = !cookies["bili_jct"].isNullOrBlank(),
            hasBuvid3 = !cookies["buvid3"].isNullOrBlank() || !account?.buvid3.isNullOrBlank(),
            hasBuvid4 = !cookies["buvid4"].isNullOrBlank() || !account?.buvid4.isNullOrBlank(),
            hasBNut = !cookies["b_nut"].isNullOrBlank() || !account?.bNut.isNullOrBlank(),
            hasTicket = !account?.biliTicket.isNullOrBlank()
        )
    }

    private data class DebugAccountFlags(
        val hasAccessKey: Boolean,
        val hasSessdata: Boolean,
        val hasCsrf: Boolean,
        val hasBuvid3: Boolean,
        val hasBuvid4: Boolean,
        val hasBNut: Boolean,
        val hasTicket: Boolean
    )

    private suspend fun <T> performWebAction(
        action: String,
        aid: Long,
        startExtra: String? = null,
        resultExtra: ((BiliResult<T>) -> String?)? = null,
        block: suspend () -> BiliResult<T>
    ): BiliResult<T> {
        debugLogAction(
            action = action,
            aid = aid,
            phase = "start",
            extra = startExtra,
            modeOverride = "web"
        )

        val result = safeCall { block() }
        debugLogAction(
            action = action,
            aid = aid,
            phase = "result",
            extra = resultExtra?.invoke(result),
            result = result
        )
        return result
    }

    private suspend fun <T> safeCall(block: suspend () -> BiliResult<T>): BiliResult<T> {
        return try {
            block()
        } catch (error: Exception) {
            BiliResult(BiliErrorCodes.REQUEST_FAILED, errorMessage(error))
        }
    }

    private suspend fun <T> safeNullableCall(block: suspend () -> T?): T? {
        return try {
            block()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun safeQrPoll(block: suspend () -> QrPollResult): QrPollResult {
        return try {
            block()
        } catch (error: Exception) {
            QrPollResult(QrPollStatus.ERROR, BiliErrorCodes.REQUEST_FAILED, errorMessage(error))
        }
    }

    private fun buildSearchHistoryJson(history: List<String>): String {
        val array = JSONArray()
        history.forEach { array.put(it) }
        return array.toString()
    }

    private fun parseSearchHistory(raw: String): List<String> {
        return runCatching {
            val array = JSONArray(raw)
            val list = mutableListOf<String>()
            for (index in 0 until array.length()) {
                val keyword = array.optString(index, "")
                if (keyword.isNotBlank()) {
                    list.add(keyword)
                }
            }
            list
        }.getOrDefault(emptyList())
    }

    private data class CachedPlaybackSource(
        val source: BiliResolvedPlaybackSource,
        val cachedAtMs: Long
    )

    companion object {
        internal const val PREVIEW_CACHE_DIR_NAME = "offline/bili"
    }
}
