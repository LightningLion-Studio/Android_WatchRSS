package com.lightningstudio.watchrss.data.cloud

import org.json.JSONObject
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class WatchCloudRecordValue(val kind: String, val value: JSONObject)

/** Version-one metadata records. Article bodies never cross this codec. */
internal object WatchCloudRecordCodec {
    private const val MAX_CIPHERTEXT = 64 * 1024
    private val articleFields = setOf("articleId", "sourceDeviceId", "url", "title", "siteName", "excerpt",
        "imageUrl", "contentHash", "importedAt", "updatedAt", "rssSourceUrl", "rssSourceTitle",
        "independentSaved", "independentChangedAt", "independentSortOrder", "favoriteSaved",
        "favoriteChangedAt", "favoriteSortOrder", "watchLaterSaved", "watchLaterChangedAt",
        "watchLaterSortOrder", "deleted", "deletedAt", "readingProgress", "readingPositionBytes",
        "readingPositionContentHash", "readingPositionChangedAt", "isRead")
    private val sourceFields = setOf("url", "sourceDeviceId", "title", "description", "siteUrl", "imageUrl",
        "createdAt", "updatedAt", "sortOrder", "isPinned", "deleted", "deletedAt", "useOriginalContent", "continuePlaybackInBackground")

    internal fun requireKnownFields(kind: String, value: JSONObject, allowBody: Boolean = false) {
        val supported = when (kind) { "article" -> articleFields; "source" -> sourceFields; else -> throw WatchCloudUpgradeRequired() }
        val extra = if (allowBody && kind == "article") setOf("contentHtmlGzip", "contentTextGzip") else emptySet()
        if (value.keys().asSequence().any { it !in supported && it !in extra }) throw WatchCloudUpgradeRequired()
    }

    fun metadata(kind: String, value: JSONObject): JSONObject {
        val allowed = when (kind) { "article" -> articleFields; "source" -> sourceFields; else -> error("云记录类型无效") }
        require(!value.has("contentHtml") && !value.has("contentText") &&
            !value.has("contentHtmlGzip") && !value.has("contentTextGzip")) { "增量同步不接受正文" }
        val result = JSONObject()
        val nullable = setOf("imageUrl", "rssSourceUrl", "rssSourceTitle", "siteUrl")
        val booleans = setOf("independentSaved", "favoriteSaved", "watchLaterSaved", "deleted", "isRead", "isPinned", "useOriginalContent", "continuePlaybackInBackground")
        val numbers = setOf("importedAt", "updatedAt", "independentChangedAt", "independentSortOrder",
            "favoriteChangedAt", "favoriteSortOrder", "watchLaterChangedAt", "watchLaterSortOrder",
            "deletedAt", "readingPositionBytes", "readingPositionChangedAt", "createdAt", "sortOrder")
        for (name in allowed.sorted()) {
            if (name in setOf("useOriginalContent", "continuePlaybackInBackground") && !value.has(name)) continue
            if (value.has(name)) {
                val scalar = value.get(name)
                require(scalar === JSONObject.NULL || scalar is String || scalar is Boolean || scalar is Number) { "云记录字段无效" }
            }
            result.put(name, when {
                name in nullable -> if (!value.has(name) || value.isNull(name)) JSONObject.NULL else value.getString(name)
                name in booleans -> value.optBoolean(name, false)
                name in numbers -> value.optLong(name, 0L)
                name == "readingProgress" -> value.optDouble(name, 0.0).also { require(it.isFinite()) }.coerceIn(0.0, 1.0)
                else -> if (!value.has(name) || value.isNull(name)) "" else value.getString(name)
            })
        }
        require(result.optString(if (kind == "article") "articleId" else "url").isNotBlank()) { "云记录标识无效" }
        return result
    }

    fun recordId(key: ByteArray, userId: String, kind: String, value: JSONObject): String {
        val selected = metadata(kind, value)
        val logicalId = selected.getString(if (kind == "article") "articleId" else "url")
        val derived = WatchCloudCodec.hkdf(key, userId.toByteArray(), "watchrss/cloud/record-id/v1".toByteArray())
        try {
            return Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(derived, "HmacSHA256")); doFinal("$kind:$logicalId".toByteArray())
            }.hex()
        } finally { derived.fill(0) }
    }

    fun fingerprint(kind: String, value: JSONObject): String {
        val normalized = metadata(kind, value)
        normalized.remove("sourceDeviceId")
        if (kind == "article") {
            val progress = normalized.optDouble("readingProgress", 0.0).coerceIn(0.0, 1.0)
            normalized.put("readingProgress", kotlin.math.floor(progress * 1_000_000.0 + 0.5) / 1_000_000.0)
        }
        if (kind == "source") normalized.remove("createdAt")
        return MessageDigest.getInstance("SHA-256").digest(
            (kind + ":" + canonical(normalized)).toByteArray(Charsets.UTF_8)).hex()
    }

    fun encrypt(key: ByteArray, userId: String, keyVersion: Int, kind: String, value: JSONObject,
        nonce: ByteArray = ByteArray(12).also(SecureRandom()::nextBytes)): Pair<String, JSONObject> {
        require(keyVersion > 0 && nonce.size == 12 && key.size == 32)
        val id = recordId(key, userId, kind, value)
        val body = plain(kind, value)
        require(body.size + 16 <= MAX_CIPHERTEXT) { "单条云同步元数据过大，请先使用兼容同步" }
        val derived = WatchCloudCodec.hkdf(key, userId.toByteArray(), "watchrss/cloud/record/v1".toByteArray())
        val ciphertext = try { WatchCloudCodec.aes(true, derived, nonce, body, aad(userId, id, keyVersion)) }
            finally { derived.fill(0); body.fill(0) }
        return id to JSONObject().put("algorithm", "A256GCM").put("keyVersion", keyVersion)
            .put("nonceBase64", Base64.getEncoder().encodeToString(nonce))
            .put("ciphertextBase64", Base64.getEncoder().encodeToString(ciphertext))
    }

    fun decrypt(userId: String, recordId: String, envelope: JSONObject, keyForVersion: (Int) -> ByteArray?): WatchCloudRecordValue {
        require(recordId.matches(Regex("[0-9a-f]{64}")) && envelope.getString("algorithm") == "A256GCM")
        val version = envelope.getInt("keyVersion")
        require(version > 0)
        val nonce = Base64.getDecoder().decode(envelope.getString("nonceBase64"))
        val ciphertext = Base64.getDecoder().decode(envelope.getString("ciphertextBase64"))
        require(nonce.size == 12 && ciphertext.size in 16..MAX_CIPHERTEXT)
        val key = keyForVersion(version)?.copyOf() ?: error("缺少第${version}版云密钥，请重新批准此设备")
        var derived: ByteArray? = null
        var body: ByteArray? = null
        try {
            require(key.size == 32)
            derived = WatchCloudCodec.hkdf(key, userId.toByteArray(), "watchrss/cloud/record/v1".toByteArray())
            body = WatchCloudCodec.aes(false, derived, nonce, ciphertext, aad(userId, recordId, version))
            try {
                val root = JSONObject(body.toString(Charsets.UTF_8))
                WatchCloudProtocolV2.features(root)
                if (root.keys().asSequence().any { it !in setOf("format", "version", "kind", "value", "features", "requiredFeatures") })
                    throw WatchCloudUpgradeRequired()
                require(root.getString("format") == "watchrss-rss-record" && root.getInt("version") == 1)
                val kind = root.getString("kind")
                val raw = root.getJSONObject("value")
                requireKnownFields(kind, raw)
                val value = metadata(kind, raw)
                val expected = recordId(key, userId, kind, value)
                require(MessageDigest.isEqual(expected.toByteArray(), recordId.toByteArray()))
                return WatchCloudRecordValue(kind, value)
            } catch (upgrade: WatchCloudUpgradeRequired) {
                throw upgrade
            } catch (_: Exception) {
                // JSONException can embed plaintext snippets. Never propagate its
                // message/cause into the app's generic error telemetry pipeline.
                throw IllegalArgumentException("cloud_record_invalid")
            }
        } finally { derived?.fill(0); key.fill(0); body?.fill(0) }
    }

    private fun plain(kind: String, value: JSONObject): ByteArray = canonical(JSONObject()
        .put("format", "watchrss-rss-record").put("version", 1).put("kind", kind)
        .put("value", metadata(kind, value))).toByteArray(Charsets.UTF_8)
    private fun aad(user: String, id: String, version: Int) = "watchrss-cloud-record-v1:$user:$id:$version".toByteArray()
    internal fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
            JSONObject.quote(it) + ":" + canonical(value.get(it))
        }
        is String -> JSONObject.quote(value)
        is Boolean -> value.toString()
        is Number -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        else -> error("云记录字段类型无效")
    }
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
}
