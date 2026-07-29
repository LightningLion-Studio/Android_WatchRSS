package com.lightningstudio.watchrss.data.cloud

import com.lightningstudio.watchrss.data.account.WatchAccountState
import com.lightningstudio.watchrss.data.rss.SyncedSavedArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.Base64

data class WatchCloudMember(
    val readable: Boolean,
    val writable: Boolean,
    val quotaBytes: Long,
    val usedBytes: Long,
    val reservedBytes: Long
)

data class WatchCloudDevice(
    val deviceId: String,
    val lastSequence: Long
)

data class WatchCloudBootstrap(
    val member: WatchCloudMember,
    val devices: List<WatchCloudDevice>,
    val envelopes: List<WatchDeviceKeyEnvelope>
)

data class WatchCloudHead(
    val id: String,
    val sourceDeviceId: String,
    val sequence: Long,
    val keyVersion: Int,
    val manifestHash: String,
    val manifestBytes: Long,
    val observedHeads: Map<String, Long>
)

data class WatchCloudDownloadObject(
    val sha256: String,
    val sizeBytes: Long,
    val signedUrl: String
)

data class WatchCloudDownload(
    val manifestUrl: String,
    val chunks: List<WatchCloudDownloadObject>
)

data class WatchCloudUploadTarget(
    val kind: String,
    val sha256: String,
    val sizeBytes: Long,
    val objectPath: String,
    val token: String,
    val tusEndpoint: String,
    val bucketName: String
)

data class WatchRssInventory(
    val sourceUrl: String,
    val sourceTitle: String,
    val articles: List<SyncedSavedArticle>
)

class WatchCloudClient(private val http: OkHttpClient = OkHttpClient()) {
    suspend fun bootstrap(account: WatchAccountState): WatchCloudBootstrap =
        post(account, "/functions/v1/cloud/bootstrap", JSONObject()).use { response ->
            val json = response.json()
            val member = json.getJSONObject("member")
            val envelopes = json.getJSONArray("keyEnvelopes")
            val envelopeJson = (0 until envelopes.length())
                .map(envelopes::getJSONObject)
                .filter { it.optString("recipientType") == "device" }
            WatchCloudBootstrap(
                member = WatchCloudMember(
                    readable = member.optBoolean("readable"),
                    writable = member.optBoolean("writable"),
                    quotaBytes = member.optLong("quotaBytes"),
                    usedBytes = member.optLong("usedBytes"),
                    reservedBytes = member.optLong("reservedBytes")
                ),
                devices = json.getJSONArray("devices").objects {
                    WatchCloudDevice(it.getString("deviceId"), it.optLong("lastSequence"))
                },
                envelopes = envelopeJson.map {
                    WatchDeviceKeyEnvelope(
                        algorithm = it.getString("algorithm"),
                        keyVersion = it.getInt("keyVersion"),
                        wrappedKeyBase64 = it.getString("wrappedKeyBase64"),
                        nonceBase64 = it.getString("nonceBase64"),
                        ephemeralPublicKeySpki = it.getString("ephemeralPublicKeySpki")
                    )
                }
            )
        }

    suspend fun register(
        account: WatchAccountState,
        deviceId: String,
        publicKeySpki: String,
        displayName: String,
        keyVersion: Int
    ) {
        post(
            account,
            "/functions/v1/cloud/devices/register",
            JSONObject().apply {
                put("deviceId", deviceId)
                put("platform", "watch")
                put("displayName", displayName)
                put("publicKeySpki", publicKeySpki)
                put("keyVersion", keyVersion)
                put("capabilities", JSONObject().apply {
                    put("snapshotSchema", 2)
                    put("cloudRelay", true)
                    put("rtosProtocolReserved", true)
                })
            }
        ).close()
    }

    suspend fun heads(account: WatchAccountState): List<WatchCloudHead> =
        get(account, "/functions/v1/cloud/snapshots/heads").use { response ->
            response.json().getJSONArray("heads").objects { item ->
                WatchCloudHead(
                    id = item.getString("id"),
                    sourceDeviceId = item.getString("sourceDeviceId"),
                    sequence = item.getLong("deviceSequence"),
                    keyVersion = item.optInt("keyVersion", 1),
                    manifestHash = item.getString("manifestSha256"),
                    manifestBytes = item.getLong("manifestSizeBytes"),
                    observedHeads = item.getJSONObject("observedHeads").longMap()
                )
            }
        }

    suspend fun snapshot(account: WatchAccountState, id: String): WatchCloudDownload =
        get(account, "/functions/v1/cloud/snapshots/$id").use { response ->
            val json = response.json()
            WatchCloudDownload(
                manifestUrl = json.getJSONObject("snapshot").getString("manifestSignedUrl"),
                chunks = json.getJSONArray("chunks").objects {
                    WatchCloudDownloadObject(
                        it.getString("sha256"),
                        it.getLong("sizeBytes"),
                        it.getString("signedUrl")
                    )
                }
            )
        }

    suspend fun reserve(
        account: WatchAccountState,
        snapshot: WatchEncryptedSnapshot,
        retentionDays: Int?
    ): List<WatchCloudUploadTarget> =
        post(
            account,
            "/functions/v1/cloud/snapshots/reserve",
            JSONObject().apply {
                put("snapshotId", snapshot.manifest.snapshotId)
                put("sourceDeviceId", snapshot.manifest.sourceDeviceId)
                put("deviceSequence", snapshot.manifest.deviceSequence)
                put("keyVersion", snapshot.manifest.keyVersion)
                put("manifest", JSONObject().apply {
                    put("sha256", WatchCloudCodec.sha256(snapshot.encryptedManifest))
                    put("sizeBytes", snapshot.encryptedManifest.size)
                })
                put("parentHeads", JSONObject(snapshot.manifest.parentHeads))
                put("observedHeads", JSONObject(snapshot.manifest.observedHeads))
                put("retentionDays", retentionDays ?: JSONObject.NULL)
                put("chunks", JSONArray().apply {
                    snapshot.manifest.chunks.distinctBy(WatchCloudChunk::ciphertextSha256)
                        .forEach {
                            put(JSONObject().apply {
                                put("sha256", it.ciphertextSha256)
                                put("sizeBytes", it.ciphertextBytes)
                            })
                        }
                })
            }
        ).use { response ->
            response.json().getJSONArray("missingObjects").objects {
                WatchCloudUploadTarget(
                    kind = it.getString("kind"),
                    sha256 = it.getString("sha256"),
                    sizeBytes = it.getLong("sizeBytes"),
                    objectPath = it.getString("objectPath"),
                    token = it.getString("token"),
                    tusEndpoint = it.getString("tusEndpoint"),
                    bucketName = it.getString("bucketName")
                )
            }
        }

    suspend fun complete(account: WatchAccountState, snapshot: WatchEncryptedSnapshot) {
        post(
            account,
            "/functions/v1/cloud/snapshots/complete",
            JSONObject().apply {
                put("snapshotId", snapshot.manifest.snapshotId)
                put(
                    "chunkHashes",
                    JSONArray(
                        snapshot.manifest.chunks.map(WatchCloudChunk::ciphertextSha256).distinct()
                    )
                )
            }
        ).close()
    }

    suspend fun acknowledge(account: WatchAccountState, snapshotId: String, deviceId: String) {
        post(
            account,
            "/functions/v1/cloud/snapshots/$snapshotId/ack",
            JSONObject().apply {
                put("deviceId", deviceId)
                put("result", "applied")
            }
        ).close()
    }

    suspend fun rssInventory(
        account: WatchAccountState,
        sourceUrl: String
    ): WatchRssInventory {
        val sourceId = post(
            account,
            "/functions/v1/rss/sources/resolve",
            JSONObject().apply { put("url", sourceUrl) }
        ).use { response ->
            response.json().getJSONObject("source").getString("id")
        }
        return get(
            account,
            "/functions/v1/rss/sources/$sourceId/entries?limit=128"
        ).use { response ->
            val json = response.json()
            val source = json.getJSONObject("source")
            val sourceTitle = source.optString("title").ifBlank { sourceUrl }
            val now = System.currentTimeMillis()
            WatchRssInventory(
                sourceUrl = sourceUrl,
                sourceTitle = sourceTitle,
                articles = json.getJSONArray("entries").objects { entry ->
                    val entryId = entry.getString("id")
                    val link = entry.nullableString("link")
                        ?: "$sourceUrl${if ('?' in sourceUrl) '&' else '?'}watchrss_entry=$entryId"
                    SyncedSavedArticle(
                        articleId = stableArticleId(link),
                        sourceDeviceId = RSS_INVENTORY_DEVICE_ID,
                        url = link,
                        title = entry.optString("title").ifBlank { link },
                        siteName = sourceTitle,
                        excerpt = entry.optString("excerpt"),
                        contentHtml = entry.nullableString("contentHtml"),
                        contentText = entry.nullableString("contentText")
                            .orEmpty()
                            .ifBlank { entry.optString("excerpt") },
                        imageUrl = entry.nullableString("imageUrl"),
                        contentHash = entry.nullableString("contentHash")
                            ?: sha256(
                                entry.nullableString("contentHtml")
                                    ?: entry.nullableString("contentText")
                                    ?: link
                            ),
                        importedAt = now,
                        updatedAt = now,
                        rssSourceUrl = sourceUrl,
                        rssSourceTitle = sourceTitle,
                        favoriteSaved = false,
                        favoriteChangedAt = 0,
                        favoriteSortOrder = 0,
                        watchLaterSaved = false,
                        watchLaterChangedAt = 0,
                        watchLaterSortOrder = 0,
                        deleted = false,
                        deletedAt = 0
                    )
                }
            )
        }
    }

    suspend fun download(url: String, size: Long, sha256: String): ByteArray =
        withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw response.error()
                val bytes = response.body?.bytes() ?: ByteArray(0)
                require(bytes.size.toLong() == size && WatchCloudCodec.sha256(bytes) == sha256) {
                    "云对象校验失败"
                }
                bytes
            }
        }

    suspend fun upload(target: WatchCloudUploadTarget, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            require(bytes.size.toLong() == target.sizeBytes)
            val metadata = listOf(
                "bucketName" to target.bucketName,
                "objectName" to target.objectPath,
                "contentType" to "application/octet-stream",
                "cacheControl" to "3600"
            ).joinToString(",") { (key, value) ->
                "$key ${Base64.getEncoder().encodeToString(value.toByteArray())}"
            }
            val create = Request.Builder()
                .url(target.tusEndpoint)
                .header("Tus-Resumable", TUS_VERSION)
                .header("Upload-Length", bytes.size.toString())
                .header("Upload-Metadata", metadata)
                .header("x-signature", target.token)
                .header("x-upsert", "false")
                .post(ByteArray(0).toRequestBody(OCTET))
                .build()
            val uploadUrl = http.newCall(create).execute().use { response ->
                if (response.code == 409) return@withContext
                if (!response.isSuccessful) throw response.error()
                val location = response.header("Location") ?: error("TUS未返回上传地址")
                response.request.url.resolve(location)?.toString() ?: error("TUS上传地址无效")
            }
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(bytes.size, offset + TUS_CHUNK_BYTES)
                val patch = Request.Builder()
                    .url(uploadUrl)
                    .header("Tus-Resumable", TUS_VERSION)
                    .header("Upload-Offset", offset.toString())
                    .header("x-signature", target.token)
                    .patch(bytes.copyOfRange(offset, end).toRequestBody(TUS_CONTENT))
                    .build()
                offset = http.newCall(patch).execute().use { response ->
                    if (response.code == 409) return@use bytes.size
                    if (!response.isSuccessful) throw response.error()
                    response.header("Upload-Offset")?.toIntOrNull()
                        ?: error("TUS未返回偏移量")
                }
            }
        }

    private suspend fun post(
        account: WatchAccountState,
        path: String,
        body: JSONObject
    ) = request(
        account,
        Request.Builder()
            .url(account.backendBaseUrl.trimEnd('/') + path)
            .post(body.toString().toRequestBody(JSON))
    )

    private suspend fun get(account: WatchAccountState, path: String) =
        request(
            account,
            Request.Builder().url(account.backendBaseUrl.trimEnd('/') + path).get()
        )

    private suspend fun request(account: WatchAccountState, builder: Request.Builder) =
        withContext(Dispatchers.IO) {
            require(account.backendBaseUrl.isNotBlank() && !account.isTokenExpired) {
                "手表账号令牌无效，请重新从手机同步账号"
            }
            http.newCall(
                builder
                    .header("authorization", "Bearer ${account.watchDeviceToken}")
                    .header("accept", "application/json")
                    .build()
            ).execute().also {
                if (!it.isSuccessful) throw it.error()
            }
        }

    private fun okhttp3.Response.json(): JSONObject =
        JSONObject(body?.string().orEmpty().ifBlank { "{}" })

    private fun okhttp3.Response.error(): IOException {
        val detail = body?.string().orEmpty()
        close()
        return IOException("HTTP $code: ${detail.ifBlank { message }}")
    }

    private fun <T> JSONArray.objects(transform: (JSONObject) -> T) = buildList {
        for (index in 0 until length()) add(transform(getJSONObject(index)))
    }

    private fun JSONObject.longMap() = keys().asSequence().associateWith(::getLong)

    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private companion object {
        private const val RSS_INVENTORY_DEVICE_ID = "cloud-rss-inventory"
        private const val TUS_VERSION = "1.0.0"
        private const val TUS_CHUNK_BYTES = 6 * 1024 * 1024
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val OCTET = "application/octet-stream".toMediaType()
        private val TUS_CONTENT = "application/offset+octet-stream".toMediaType()

        private fun stableArticleId(value: String): String = sha256(
            runCatching {
                val uri = URI(value.trim())
                val scheme = uri.scheme.lowercase()
                val host = uri.host.orEmpty().lowercase().removePrefix("www.")
                val path = uri.rawPath.orEmpty().ifBlank { "/" }
                val query = uri.rawQuery?.takeIf(String::isNotBlank)?.let { "?$it" }.orEmpty()
                "$scheme://$host$path$query"
            }.getOrElse { value.trim() }
        )

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
    }
}
