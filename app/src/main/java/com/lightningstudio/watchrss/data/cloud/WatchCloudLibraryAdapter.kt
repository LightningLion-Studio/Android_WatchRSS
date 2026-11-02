package com.lightningstudio.watchrss.data.cloud

import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.SyncedSavedArticle
import com.lightningstudio.watchrss.data.rss.SyncedRssSource
import com.lightningstudio.watchrss.phoneconnection.bluetooth.LibrarySyncPayload
import android.util.JsonReader
import android.util.Log
import com.lightningstudio.watchrss.BuildConfig
import android.util.JsonToken
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
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

    internal suspend fun recordProjection(value: JSONObject): WatchRecordProjection? =
        repository.cloudRecordProjection(value.getString("articleId"),
            value.optString("rssSourceUrl").takeIf { it.isNotBlank() && it != "null" })?.let {
            WatchRecordProjection(it.contentHash, it.importedAt, it.metadataOnly)
        }

    internal suspend fun streamRecordValues(emit: suspend (String, JSONObject) -> Unit) {
        repository.streamCloudArticles(deviceId, stateOnly = true) { article ->
            val row = LibrarySyncPayload.buildResponse(deviceId,
                listOf(article.copy(contentHtml = null, contentText = "")), 0)
                .getJSONArray("articles").getJSONObject(0)
            row.remove("contentHtmlGzip")
            row.remove("contentTextGzip")
            emit("article", WatchCloudRecordCodec.metadata("article", row))
        }
        repository.streamCloudSources(deviceId) { source ->
            val row = LibrarySyncPayload.buildResponse(deviceId, emptyList(), 0, listOf(source))
                .getJSONArray("rssSources").getJSONObject(0)
            emit("source", WatchCloudRecordCodec.metadata("source", row))
        }
    }

    internal suspend fun applyRecordValues(values: List<WatchCloudRecordValue>) {
        val root = JSONObject().put("format", "watchrss-rss-state").put("version", 1)
            .put("articles", JSONArray().apply { values.filter { it.kind == "article" }.forEach { put(it.value) } })
            .put("sources", JSONArray().apply { values.filter { it.kind == "source" }.forEach { put(it.value) } })
        restoreState(root.toString().byteInputStream(), "cloud-record-v1")
    }

    suspend fun export(): ByteArray = ByteArrayOutputStream().also { exportTo(it) }.toByteArray()

    suspend fun exportState(): ByteArray = ByteArrayOutputStream().also { exportStateTo(it) }.toByteArray()

    suspend fun exportTo(output: OutputStream) = writeSnapshot(output, stateOnly = false)

    suspend fun exportStateTo(output: OutputStream) = writeSnapshot(output, stateOnly = true)

    private suspend fun writeSnapshot(output: OutputStream, stateOnly: Boolean) {
        // Do not close the supplied output: its owner finalizes compression,
        // authentication and chunk descriptors after this function returns.
        val writer = output.bufferedWriter(Charsets.UTF_8)
        if (stateOnly) {
            writer.write("{\"format\":\"watchrss-rss-state\",\"version\":1,\"exportedAt\":0,")
        } else {
            writer.write("{\"version\":${LibrarySyncPayload.PROTOCOL_VERSION},\"deviceId\":")
            writer.write(JSONObject.quote(deviceId))
            writer.write(",\"success\":true,")
        }
        writer.write("\"articles\":[")
        var first = true
        repository.streamCloudArticles(deviceId, stateOnly) { article ->
            if (!first) writer.write(",")
            first = false
            val privateBody = ImportedContentIds.isImportedContentUrl(article.url) ||
                ImportedContentIds.isImportedContentUrl(article.rssSourceUrl) || article.rssSourceUrl.isNullOrBlank()
            val bounded = if (privateBody && !stateOnly) article else article.copy(contentHtml = null, contentText = "")
            // Canonical Bluetooth serializer keeps gzip body fields and all LWW
            // metadata compatible. Only one row/one chapter is materialized.
            val row = LibrarySyncPayload.buildResponse(deviceId, listOf(bounded), 0)
                .getJSONArray("articles").getJSONObject(0)
            if (stateOnly) {
                row.remove("contentHtmlGzip")
                row.remove("contentTextGzip")
            }
            writer.write(row.toString())
        }
        writer.write(if (stateOnly) "],\"sources\":[" else "],\"rssSources\":[")
        first = true
        repository.streamCloudSources(deviceId) { source ->
            if (!first) writer.write(",")
            first = false
            val row = LibrarySyncPayload.buildResponse(deviceId, emptyList(), 0, listOf(source))
                .getJSONArray("rssSources").getJSONObject(0)
            writer.write(row.toString())
        }
        writer.write("]}")
        writer.flush()
    }

    suspend fun restore(bytes: ByteArray, remoteDeviceId: String): Int =
        restore(bytes.inputStream(), remoteDeviceId)

    suspend fun restore(input: InputStream, remoteDeviceId: String): Int =
        restoreVerified(input, remoteDeviceId, stateOnly = false)

    suspend fun restoreState(bytes: ByteArray, remoteDeviceId: String): Int =
        restoreState(bytes.inputStream(), remoteDeviceId)

    suspend fun restoreState(input: InputStream, remoteDeviceId: String): Int =
        restoreVerified(input, remoteDeviceId, stateOnly = true)

    /** Authenticate the complete object and inspect its format before any repository mutation. */
    private suspend fun restoreVerified(input: InputStream, remoteDeviceId: String, stateOnly: Boolean): Int {
        val file = java.io.File.createTempFile("watchrss-cloud-restore-", ".json")
        try {
            file.outputStream().use { input.copyTo(it) }
            file.inputStream().use { validateFormat(it, stateOnly) }
            return file.inputStream().use { restoreStream(it, remoteDeviceId, stateOnly) }
        } finally { file.delete() }
    }

    private fun validateFormat(input: InputStream, stateOnly: Boolean) {
        val reader = JsonReader(input.reader(Charsets.UTF_8))
        var format: String? = null
        var version = 0
        val seen = hashSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (!seen.add(name)) throw WatchCloudUpgradeRequired()
            when (name) {
                "format" -> format = reader.nextString()
                "version" -> version = reader.nextInt()
                "articles", "rssSources", "sources" -> {
                    reader.beginArray()
                    while (reader.hasNext()) WatchCloudRecordCodec.requireKnownFields(
                        if (name == "articles") "article" else "source", readObject(reader), allowBody = !stateOnly)
                    reader.endArray()
                }
                "deviceId", "success", "action", "sentAt", "exportedAt", "stats" -> reader.skipValue()
                else -> throw WatchCloudUpgradeRequired()
            }
        }
        reader.endObject()
        require(reader.peek() == JsonToken.END_DOCUMENT) { "云端资料包含多余内容" }
        if (stateOnly) {
            if (format != "watchrss-rss-state" || version != 1) throw WatchCloudUpgradeRequired()
        } else if (format != null || version !in 1..LibrarySyncPayload.PROTOCOL_VERSION) throw WatchCloudUpgradeRequired()
    }

    // A failed/truncated stream may have committed earlier rows. Merge APIs retain
    // their normal LWW/tombstone checks, so retrying never resets local state.
    // The caller acknowledges the remote head only after the crypto stream's EOF
    // integrity check has also completed successfully.
    private suspend fun restoreStream(input: InputStream, remoteDeviceId: String, stateOnly: Boolean): Int {
        val reader = JsonReader(input.reader(Charsets.UTF_8))
        var applied = 0
        var format: String? = null
        var version = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "format" -> format = reader.nextString()
                "version" -> version = reader.nextInt()
                "articles" -> {
                    val batch = ArrayList<SyncedSavedArticle>(CLOUD_BATCH_ROWS)
                    var bytes = 0L
                    var committedRows = 0
                    var reportedRows = 0
                    suspend fun flush() {
                        if (batch.isEmpty()) return
                        applied += repository.mergeCloudArticleBatch(batch, remoteDeviceId, deviceId).applied
                        committedRows += batch.size
                        if (committedRows - reportedRows >= 500 && BuildConfig.DEBUG) {
                            Log.i("WatchCloudSync", "restore_rows object=${if (stateOnly) "rss-state" else "library"} committed=$committedRows")
                            reportedRows = committedRows
                        }
                        batch.clear()
                        bytes = 0L
                    }
                    reader.beginArray()
                    while (reader.hasNext()) {
                        val row = readObject(reader)
                        val articles = if (stateOnly) listOf(parseState(row))
                            else LibrarySyncPayload.parseArticles(JSONArray().put(row))
                        for (article in articles) {
                            val size = cloudArticleBatchBytes(article)
                            if (batch.isNotEmpty() && (batch.size >= CLOUD_BATCH_ROWS || bytes + size > CLOUD_BATCH_BYTES)) flush()
                            batch.add(article)
                            bytes += size
                            if (batch.size >= CLOUD_BATCH_ROWS || bytes >= CLOUD_BATCH_BYTES) flush()
                        }
                    }
                    reader.endArray()
                    flush()
                    if (BuildConfig.DEBUG) Log.i("WatchCloudSync", "restore_articles_done object=${if (stateOnly) "rss-state" else "library"} rows=$committedRows")
                }
                "rssSources", "sources" -> {
                    val batch = ArrayList<SyncedRssSource>(CLOUD_BATCH_ROWS)
                    var bytes = 0L
                    suspend fun flush() {
                        if (batch.isEmpty()) return
                        applied += repository.mergeCloudSourceBatch(batch, remoteDeviceId, deviceId).applied
                        batch.clear()
                        bytes = 0L
                    }
                    reader.beginArray()
                    while (reader.hasNext()) {
                        for (source in LibrarySyncPayload.parseRssSources(JSONArray().put(readObject(reader)))) {
                            val size = 256L + 2L * (source.url.length + source.title.length + source.description.length +
                                (source.siteUrl?.length ?: 0) + (source.imageUrl?.length ?: 0))
                            if (batch.isNotEmpty() && (batch.size >= CLOUD_BATCH_ROWS || bytes + size > CLOUD_BATCH_BYTES)) flush()
                            batch.add(source)
                            bytes += size
                            if (batch.size >= CLOUD_BATCH_ROWS || bytes >= CLOUD_BATCH_BYTES) flush()
                        }
                    }
                    reader.endArray()
                    flush()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        require(reader.peek() == JsonToken.END_DOCUMENT) { "云端资料包含多余内容" }
        if (stateOnly) require(format == "watchrss-rss-state" && version == 1) { "云端RSS状态格式不受支持" }
        return applied
    }

    // Protocol rows are flat scalar objects. Unknown fields stop publication instead
    // of being dropped from a future format; memory is bounded by the current chapter.
    private fun readObject(reader: JsonReader): JSONObject {
        val row = JSONObject()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (row.has(name)) throw WatchCloudUpgradeRequired()
            when (reader.peek()) {
                JsonToken.NULL -> { reader.nextNull(); row.put(name, JSONObject.NULL) }
                JsonToken.BOOLEAN -> row.put(name, reader.nextBoolean())
                JsonToken.NUMBER -> {
                    val number = reader.nextString()
                    row.put(name, number.toLongOrNull() ?: number.toDouble())
                }
                JsonToken.STRING -> row.put(name, reader.nextString())
                else -> throw WatchCloudUpgradeRequired()
            }
        }
        reader.endObject()
        return row
    }

    private fun parseState(state: JSONObject): SyncedSavedArticle =
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
            readingPositionBytes = state.optLong("readingPositionBytes").coerceAtLeast(0L),
            readingPositionContentHash = state.optString("readingPositionContentHash"),
            readingPositionChangedAt = state.optLong("readingPositionChangedAt").coerceAtLeast(0L),
            isRead = state.optBoolean("isRead")
        )


    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)
}

internal const val CLOUD_BATCH_ROWS = 16
internal const val CLOUD_BATCH_BYTES = 512L * 1024L
internal fun cloudArticleBatchBytes(row: SyncedSavedArticle): Long = 512L + 2L * (
    row.title.length.toLong() + row.url.length + row.siteName.length + row.excerpt.length +
        row.contentText.length + (row.contentHtml?.length ?: 0) + (row.imageUrl?.length ?: 0) +
        (row.rssSourceUrl?.length ?: 0) + (row.rssSourceTitle?.length ?: 0))
