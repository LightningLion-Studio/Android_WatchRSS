package com.lightningstudio.watchrss.data.bili

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import com.lightningstudio.watchrss.sdk.bili.TvQrCode
import com.lightningstudio.watchrss.sdk.bili.WebQrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
import javax.net.ssl.SSLException
import kotlin.math.max
import kotlin.math.min

private val FEED_CACHE_JSON = stringPreferencesKey("bili_feed_cache_json")
private val FEED_CACHE_AT = longPreferencesKey("bili_feed_cache_at")
private val SEARCH_HISTORY_JSON = stringPreferencesKey("bili_search_history")
private const val FEED_CACHE_LIMIT = 50
private const val PREVIEW_CACHE_QN = 32
private const val PREVIEW_CACHE_MS = 30 * 60 * 1000L

class BiliRepository(
    context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val accountStore = EncryptedBiliAccountStore(context)
    private val client = BiliClient(BiliSdkConfig(), accountStore)
    private val previewCacheDir = File(context.filesDir, "offline/bili").apply { mkdirs() }
    private val downloadClient = OkHttpClient.Builder()
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun isLoggedIn(): Boolean {
        val account = accountStore.read()
        return !account?.cookies?.get("SESSDATA").isNullOrBlank()
    }

    suspend fun readAccount(): BiliAccount? = accountStore.read()

    suspend fun clearAccount() {
        accountStore.write(BiliAccount())
    }

    suspend fun applyCookieHeader(rawCookie: String): Result<Unit> {
        val cookies = BiliCookies.parseCookieHeader(rawCookie)
        if (cookies.isEmpty()) {
            return Result.failure(IllegalArgumentException("缺少有效 Cookie"))
        }
        client.auth.applyCookies(cookies)
        debugLogAuth("cookie", "applied")
        return Result.success(Unit)
    }

    suspend fun requestWebQrCode(): WebQrCode? = safeNullableCall { client.auth.requestWebQrCode() }

    suspend fun requestTvQrCode(): TvQrCode? = safeNullableCall { client.auth.requestTvQrCode() }

    suspend fun pollWebQrCode(qrKey: String): QrPollResult {
        val result = safeQrPoll { client.auth.pollWebQrCode(qrKey) }
        if (result.status == QrPollStatus.SUCCESS) {
            debugLogAuth("web_qr", "success")
        }
        return result
    }

    suspend fun pollTvQrCode(authCode: String): QrPollResult {
        val result = safeQrPoll { client.auth.pollTvQrCode(authCode) }
        if (result.status == QrPollStatus.SUCCESS) {
            debugLogAuth("tv_qr", "success")
        }
        return result
    }

    suspend fun fetchFeed(): BiliResult<BiliFeedPage> =
        safeCall { client.feed.fetchDefaultFeed() }

    suspend fun readFeedCache(): List<BiliItem> = withContext(Dispatchers.IO) {
        val raw = dataStore.data.first()[FEED_CACHE_JSON].orEmpty()
        if (raw.isBlank()) return@withContext emptyList()
        parseFeedCache(raw)
    }

    suspend fun writeFeedCache(items: List<BiliItem>) {
        val trimmed = items.take(FEED_CACHE_LIMIT)
        val raw = buildFeedCacheJson(trimmed)
        dataStore.edit { preferences ->
            preferences[FEED_CACHE_JSON] = raw
            preferences[FEED_CACHE_AT] = System.currentTimeMillis()
        }
    }

    suspend fun fetchVideoDetail(aid: Long? = null, bvid: String? = null): BiliResult<BiliVideoDetail> =
        safeCall { client.video.fetchView(aid = aid, bvid = bvid, useWbi = true) }

    suspend fun fetchPlayUrlMp4(
        cid: Long,
        aid: Long? = null,
        bvid: String? = null,
        qn: Int = 32
    ): BiliResult<BiliPlayUrl> = safeCall {
        client.play.fetchMp4Url(
            cid = cid,
            aid = aid,
            bvid = bvid,
            qn = qn
        )
    }

    suspend fun like(aid: Long, like: Boolean): BiliResult<Unit> {
        debugLogAction("like", aid, "start", extra = "like=$like")
        val result = safeCall { client.action.like(aid, like) }
        debugLogAction("like", aid, "result", result = result)
        return result
    }

    suspend fun coin(aid: Long, multiply: Int = 1, selectLike: Boolean = false): BiliResult<Boolean> {
        debugLogAction(
            "coin",
            aid,
            "start",
            extra = "multiply=$multiply selectLike=$selectLike"
        )
        val result = safeCall { client.action.coin(aid, multiply, selectLike) }
        debugLogAction(
            "coin",
            aid,
            "result",
            extra = "likeResult=${result.data}",
            result = result
        )
        return result
    }

    suspend fun triple(aid: Long): BiliResult<com.lightningstudio.watchrss.sdk.bili.BiliTripleResult> {
        debugLogAction("triple", aid, "start")
        val result = safeCall { client.action.triple(aid) }
        debugLogAction("triple", aid, "result", result = result)
        return result
    }

    suspend fun favorite(aid: Long, add: Boolean): BiliResult<Boolean> = safeCall {
        val folderId = defaultFavoriteFolderId()
            ?: return@safeCall BiliResult(BiliErrorCodes.MISSING_FAVORITE_FOLDER, "missing_favorite_folder")
        val addIds = if (add) listOf(folderId) else emptyList()
        val delIds = if (add) emptyList() else listOf(folderId)
        client.action.favorite(aid, addMediaIds = addIds, delMediaIds = delIds)
    }

    suspend fun addToView(aid: Long? = null, bvid: String? = null): BiliResult<Unit> =
        safeCall { client.history.addToView(aid, bvid) }

    suspend fun fetchToView(): BiliResult<BiliToViewPage> =
        safeCall { client.history.fetchToView() }

    suspend fun fetchHistory(cursor: BiliHistoryCursor? = null): BiliResult<BiliHistoryPage> =
        safeCall { client.history.fetchHistory(cursor) }

    suspend fun fetchFavoriteFolders(): BiliResult<List<BiliFavoriteFolder>> = safeCall {
        val mid = currentUserMid() ?: return@safeCall BiliResult(BiliErrorCodes.MISSING_MID, "missing_mid")
        client.favorite.listFolders(mid)
    }

    suspend fun fetchFavoriteItems(mediaId: Long, pn: Int = 1, ps: Int = 20): BiliResult<BiliFavoritePage> =
        safeCall { client.favorite.listResources(mediaId = mediaId, pn = pn, ps = ps) }

    // Search methods
    suspend fun getHotSearch(): BiliResult<BiliHotSearchResponse> =
        safeCall { client.search.getHotSearch() }

    suspend fun searchAll(keyword: String, page: Int): BiliResult<BiliSearchResponse> =
        safeCall { client.search.searchAll(keyword, page) }

    suspend fun getSearchHistory(): List<String> = withContext(Dispatchers.IO) {
        val raw = dataStore.data.first()[SEARCH_HISTORY_JSON].orEmpty()
        if (raw.isBlank()) return@withContext emptyList()
        parseSearchHistory(raw)
    }

    suspend fun addSearchHistory(keyword: String) {
        val history = getSearchHistory().toMutableList()
        history.remove(keyword)
        history.add(0, keyword)
        val trimmed = history.take(20)
        val raw = buildSearchHistoryJson(trimmed)
        dataStore.edit { preferences ->
            preferences[SEARCH_HISTORY_JSON] = raw
        }
    }

    suspend fun clearSearchHistory() {
        dataStore.edit { preferences ->
            preferences.remove(SEARCH_HISTORY_JSON)
        }
    }

    // Comment methods
    suspend fun getComments(oid: Long, next: Long = 0): BiliResult<BiliCommentPage> =
        safeCall { client.comment.getComments(oid, next) }

    suspend fun getReplies(oid: Long, root: Long, pn: Int = 1): BiliResult<BiliCommentReplyPage> =
        safeCall { client.comment.getReplies(oid, root, pn) }

    suspend fun buildPlayHeaders(): Map<String, String> {
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

    fun shareLink(bvid: String?, aid: Long?): String? {
        return when {
            !bvid.isNullOrBlank() -> "https://www.bilibili.com/video/$bvid"
            aid != null -> "https://www.bilibili.com/video/av$aid"
            else -> null
        }
    }

    fun savedLink(bvid: String?, aid: Long?, cid: Long?): String? {
        val base = shareLink(bvid, aid) ?: return null
        val safeCid = cid ?: return base
        return "$base?cid=$safeCid"
    }

    suspend fun cachedPreviewUri(aid: Long?, bvid: String?, cid: Long?): String? {
        return withContext(Dispatchers.IO) {
            val file = previewCacheFile(aid, bvid, cid) ?: return@withContext null
            if (file.exists() && file.length() > 0) {
                file.toURI().toString()
            } else {
                null
            }
        }
    }

    suspend fun cachedPreviewUriAny(aid: Long?, bvid: String?): String? = withContext(Dispatchers.IO) {
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
        match?.toURI()?.toString()
    }

    suspend fun cachePreviewClip(
        aid: Long?,
        bvid: String?,
        cid: Long?
    ): Result<String> = withContext(Dispatchers.IO) {
        val file = previewCacheFile(aid, bvid, cid)
            ?: return@withContext Result.failure(IllegalArgumentException("missing_video_id"))
        if (file.exists() && file.length() > 0) {
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
        val durl = result.data?.durl?.firstOrNull()
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
        val targetMs = min(PREVIEW_CACHE_MS, lengthMs)
        val bytes = max(1L, size * targetMs / lengthMs) - 1L
        val headers = buildPlayHeaders()
        val request = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=0-$bytes")
            .apply {
                headers.forEach { (key, value) -> addHeader(key, value) }
            }
            .build()
        runCatching {
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("download_failed:${response.code}")
                }
                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("empty_body")
            }
            if (!file.exists() || file.length() <= 0) {
                throw IllegalStateException("empty_file")
            }
            Result.success(file.absolutePath)
        }.getOrElse { error ->
            runCatching { file.delete() }
            Result.failure(error)
        }
    }

    suspend fun clearCachedPreview(aid: Long?, bvid: String?, cid: Long?) {
        withContext(Dispatchers.IO) {
            val file = previewCacheFile(aid, bvid, cid) ?: return@withContext
            if (file.exists()) {
                runCatching { file.delete() }
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
        result: BiliResult<*>? = null
    ) {
        if (!DebugLogBuffer.isEnabled()) return
        val flags = readDebugAccountFlags()
        val mode = if (flags.hasAccessKey) "app" else "web"
        val extraPart = if (extra.isNullOrBlank()) "" else " $extra"
        val resultPart = result?.let { " code=${it.code} msg=${it.message}" }.orEmpty()
        DebugLogBuffer.log(
            "bili",
            "action=$action phase=$phase mode=$mode aid=$aid accessKey=${flags.hasAccessKey} " +
                "sess=${flags.hasSessdata} csrf=${flags.hasCsrf} buvid3=${flags.hasBuvid3} " +
                "buvid4=${flags.hasBuvid4} ticket=${flags.hasTicket}$extraPart$resultPart"
        )
    }

    private suspend fun debugLogAuth(source: String, phase: String) {
        if (!DebugLogBuffer.isEnabled()) return
        val flags = readDebugAccountFlags()
        val mode = if (flags.hasAccessKey) "app" else "web"
        DebugLogBuffer.log(
            "bili",
            "auth=$source phase=$phase mode=$mode accessKey=${flags.hasAccessKey} " +
                "sess=${flags.hasSessdata} csrf=${flags.hasCsrf} buvid3=${flags.hasBuvid3} " +
                "buvid4=${flags.hasBuvid4} ticket=${flags.hasTicket}"
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
            hasTicket = !account?.biliTicket.isNullOrBlank()
        )
    }

    private data class DebugAccountFlags(
        val hasAccessKey: Boolean,
        val hasSessdata: Boolean,
        val hasCsrf: Boolean,
        val hasBuvid3: Boolean,
        val hasBuvid4: Boolean,
        val hasTicket: Boolean
    )

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
}
