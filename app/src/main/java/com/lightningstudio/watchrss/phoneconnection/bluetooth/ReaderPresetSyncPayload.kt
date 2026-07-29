package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.data.reader.ReaderBackgroundAssetEntity
import com.lightningstudio.watchrss.data.reader.ReaderDeletionEntity
import com.lightningstudio.watchrss.data.reader.ReaderFontAssetEntity
import com.lightningstudio.watchrss.data.reader.ReaderPresetEntity
import com.lightningstudio.watchrss.data.reader.ReaderPresetRepository
import com.lightningstudio.watchrss.data.reader.ReaderPresetSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Base64

object ReaderPresetSyncPayload {
    const val PHASE_MANIFEST = "manifest"
    const val PHASE_PUSH_RESOURCE = "pushResource"
    const val PHASE_PULL_RESOURCE = "pullResource"
    const val CHUNK_BYTES = 1024 * 1024

    suspend fun handle(request: JSONObject, repository: ReaderPresetRepository): JSONObject =
        when (request.getString("phase")) {
            PHASE_MANIFEST -> handleManifest(request, repository)
            PHASE_PUSH_RESOURCE -> handlePush(request, repository)
            PHASE_PULL_RESOURCE -> handlePull(request, repository)
            else -> error("未知阅读器同步阶段")
        }

    private suspend fun handleManifest(
        request: JSONObject,
        repository: ReaderPresetRepository
    ): JSONObject {
        repository.mergeRemote(
            presets = parsePresets(request.optJSONArray("presets")),
            fonts = parseFonts(request.optJSONArray("fonts")),
            backgrounds = parseBackgrounds(request.optJSONArray("backgrounds")),
            deletions = parseDeletions(request.optJSONArray("deletions"))
        )
        val local = repository.exportSnapshot()
        return snapshotJson(local).apply {
            put("success", true)
            put("version", 11)
            put("action", BluetoothSyncProtocol.ACTION_SYNC_READER)
            put("phase", PHASE_MANIFEST)
            put("missingResources", missingResources(repository, local))
        }
    }

    private fun handlePush(
        request: JSONObject,
        repository: ReaderPresetRepository
    ): JSONObject {
        val kind = request.getString("kind")
        val fileName = request.getString("fileName")
        val expectedHash = request.getString("sha256")
        val totalBytes = request.getLong("totalBytes")
        require(totalBytes in 0..MAX_RESOURCE_BYTES) { "阅读器资源大小异常" }
        val index = request.getInt("chunkIndex")
        val chunkCount = request.getInt("chunkCount")
        require(index in 0 until chunkCount && chunkCount > 0) { "资源分块序号异常" }
        val data = Base64.getDecoder().decode(request.getString("data"))
        require(data.size <= CHUNK_BYTES) { "资源分块过大" }
        require(sha256(data) == request.getString("chunkSha256")) { "资源分块校验失败" }
        val target = targetFile(repository, kind, fileName)
        if (target.exists() && repository.resourceStore.verify(target, totalBytes, expectedHash)) {
            return pushAck(index, data, applied = true)
        }
        val partial = File(target.parentFile, "${target.name}.part")
        val metadata = File(target.parentFile, "${target.name}.part.meta")
        applyIncomingChunk(
            partial = partial,
            metadata = metadata,
            index = index,
            chunkCount = chunkCount,
            data = data,
            totalBytes = totalBytes,
            expectedHash = expectedHash
        )
        val complete = index == chunkCount - 1
        if (complete) {
            require(repository.resourceStore.verify(partial, totalBytes, expectedHash)) {
                "资源完整校验失败"
            }
            if (target.exists()) target.delete()
            require(partial.renameTo(target)) { "资源落盘失败" }
            metadata.delete()
        }
        return pushAck(index, data, applied = complete)
    }

    private fun pushAck(index: Int, data: ByteArray, applied: Boolean) =
        JSONObject().apply {
            put("success", true)
            put("action", BluetoothSyncProtocol.ACTION_SYNC_READER)
            put("phase", PHASE_PUSH_RESOURCE)
            put("received", true)
            put("applied", applied)
            put("chunkIndex", index)
            put("chunkSha256", sha256(data))
        }

    internal fun applyIncomingChunk(
        partial: File,
        metadata: File,
        index: Int,
        chunkCount: Int,
        data: ByteArray,
        totalBytes: Long,
        expectedHash: String
    ) {
        val signature = "$expectedHash:$totalBytes:$chunkCount"
        val savedSignature = runCatching { metadata.readText() }.getOrNull()
        if (savedSignature != signature) {
            require(index == 0) { "资源传输已变化，请从第一块重试" }
            if (partial.exists()) partial.delete()
            metadata.parentFile?.mkdirs()
            metadata.writeText(signature)
        }

        RandomAccessFile(partial, "rw").use { output ->
            val offset = index.toLong() * CHUNK_BYTES
            val end = offset + data.size
            require(offset <= totalBytes && end <= totalBytes) { "资源分块范围异常" }
            when {
                output.length() < offset -> error("资源分块顺序不连续")
                output.length() >= end -> {
                    val existing = ByteArray(data.size)
                    output.seek(offset)
                    output.readFully(existing)
                    if (!existing.contentEquals(data)) {
                        require(index == 0) { "资源分块内容冲突，请从第一块重试" }
                        output.setLength(0L)
                        output.seek(0L)
                        output.write(data)
                    }
                }
                else -> {
                    output.setLength(offset)
                    output.seek(offset)
                    output.write(data)
                }
            }
            output.fd.sync()
        }
    }

    private fun handlePull(
        request: JSONObject,
        repository: ReaderPresetRepository
    ): JSONObject {
        val kind = request.getString("kind")
        val fileName = request.getString("fileName")
        val file = existingFile(repository, kind, fileName)
        val index = request.getInt("chunkIndex")
        val offset = index.toLong() * CHUNK_BYTES
        require(offset < file.length() || (offset == 0L && file.length() == 0L)) {
            "资源分块越界"
        }
        val count = minOf(CHUNK_BYTES.toLong(), file.length() - offset).toInt()
        val data = ByteArray(count)
        RandomAccessFile(file, "r").use {
            it.seek(offset)
            it.readFully(data)
        }
        return JSONObject().apply {
            put("success", true)
            put("action", BluetoothSyncProtocol.ACTION_SYNC_READER)
            put("phase", PHASE_PULL_RESOURCE)
            put("kind", kind)
            put("fileName", fileName)
            put("totalBytes", file.length())
            put("chunkIndex", index)
            put("chunkCount", ((file.length() + CHUNK_BYTES - 1) / CHUNK_BYTES).coerceAtLeast(1))
            put("chunkSha256", sha256(data))
            put("data", Base64.getEncoder().encodeToString(data))
        }
    }

    fun snapshotJson(snapshot: ReaderPresetSnapshot): JSONObject = JSONObject().apply {
        put("presets", JSONArray().also { array -> snapshot.presets.forEach { array.put(it.toJson()) } })
        put("fonts", JSONArray().also { array -> snapshot.fonts.forEach { array.put(it.toJson()) } })
        put("backgrounds", JSONArray().also { array -> snapshot.backgrounds.forEach { array.put(it.toJson()) } })
        put("deletions", JSONArray().also { array -> snapshot.deletions.forEach { array.put(it.toJson()) } })
    }

    fun parsePresets(array: JSONArray?): List<ReaderPresetEntity> = buildList {
        array.forEachObject { json ->
            add(ReaderPresetEntity(json.getString("id"), json.getString("name"), json.getString("payloadJson"), json.getLong("updatedAt"), json.getString("modifiedBy"), json.getBoolean("deleted")))
        }
    }

    fun parseFonts(array: JSONArray?): List<ReaderFontAssetEntity> = buildList {
        array.forEachObject { j ->
            add(ReaderFontAssetEntity(j.getString("id"), j.getString("sha256"), j.getString("displayName"), j.getString("familyName"), j.getString("fileName"), j.getString("mimeType"), j.getLong("byteCount"), j.getInt("faceCount"), j.getString("metadataJson"), j.getLong("updatedAt"), j.getString("modifiedBy"), j.getBoolean("deleted")))
        }
    }

    fun parseBackgrounds(array: JSONArray?): List<ReaderBackgroundAssetEntity> = buildList {
        array.forEachObject { j ->
            add(ReaderBackgroundAssetEntity(j.getString("id"), j.getString("sha256"), j.getString("displayName"), j.getString("kind"), j.getString("mimeType"), j.getString("masterFileName"), j.getLong("byteCount"), j.getLong("durationMs"), j.getInt("width"), j.getInt("height"), j.optString("posterAssetId").takeIf(String::isNotBlank), j.getString("variantsJson"), j.getLong("updatedAt"), j.getString("modifiedBy"), j.getBoolean("deleted")))
        }
    }

    fun parseDeletions(array: JSONArray?): List<ReaderDeletionEntity> = buildList {
        array.forEachObject { j ->
            add(ReaderDeletionEntity(j.getString("kind"), j.getString("entityId"), j.getLong("deletedAt"), j.getString("deletedBy")))
        }
    }

    private fun missingResources(repository: ReaderPresetRepository, snapshot: ReaderPresetSnapshot) =
        JSONArray().apply {
            snapshot.fonts.filterNot { it.deleted }.forEach {
                val file = repository.resourceStore.fontFile(it.fileName)
                if (file == null || !repository.resourceStore.verify(file, it.byteCount, it.sha256)) {
                    put(resourceJson("font", it.fileName, it.sha256, it.byteCount))
                }
            }
            snapshot.backgrounds.filterNot { it.deleted }.forEach {
                val file = repository.resourceStore.backgroundFile(it.masterFileName)
                if (file == null || !repository.resourceStore.verify(file, it.byteCount, it.sha256)) {
                    put(resourceJson("background", it.masterFileName, it.sha256, it.byteCount))
                }
                val variants = runCatching { JSONObject(it.variantsJson) }.getOrNull()
                listOf("watch", "watchPoster").forEach { key ->
                    variants?.optJSONObject(key)?.let { variant ->
                        val fileName = variant.optString("fileName")
                        val hash = variant.optString("sha256")
                        val byteCount = variant.optLong("byteCount")
                        if (fileName.isNotBlank() && hash.length == 64 && byteCount >= 0L) {
                            val variantFile = repository.resourceStore.variantFile(fileName)
                            if (
                                variantFile == null ||
                                !repository.resourceStore.verify(variantFile, byteCount, hash)
                            ) {
                                put(resourceJson("variant", fileName, hash, byteCount))
                            }
                        }
                    }
                }
            }
        }

    private fun resourceJson(kind: String, fileName: String, hash: String, bytes: Long) =
        JSONObject().apply {
            put("kind", kind)
            put("fileName", fileName)
            put("sha256", hash)
            put("byteCount", bytes)
        }

    private fun targetFile(repository: ReaderPresetRepository, kind: String, fileName: String) =
        when (kind) {
            "font" -> repository.resourceStore.targetFontFile(fileName)
            "background" -> repository.resourceStore.targetBackgroundFile(fileName)
            "variant" -> repository.resourceStore.targetVariantFile(fileName)
            else -> error("未知资源类型")
        }

    private fun existingFile(repository: ReaderPresetRepository, kind: String, fileName: String) =
        when (kind) {
            "font" -> repository.resourceStore.fontFile(fileName)
            "background" -> repository.resourceStore.backgroundFile(fileName)
            "variant" -> repository.resourceStore.variantFile(fileName)
            else -> null
        } ?: error("资源不存在")

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun ReaderPresetEntity.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("payloadJson", payloadJson); put("updatedAt", updatedAt); put("modifiedBy", modifiedBy); put("deleted", deleted)
    }
    private fun ReaderFontAssetEntity.toJson() = JSONObject().apply {
        put("id", id); put("sha256", sha256); put("displayName", displayName); put("familyName", familyName); put("fileName", fileName); put("mimeType", mimeType); put("byteCount", byteCount); put("faceCount", faceCount); put("metadataJson", metadataJson); put("updatedAt", updatedAt); put("modifiedBy", modifiedBy); put("deleted", deleted)
    }
    private fun ReaderBackgroundAssetEntity.toJson() = JSONObject().apply {
        put("id", id); put("sha256", sha256); put("displayName", displayName); put("kind", kind); put("mimeType", mimeType); put("masterFileName", masterFileName); put("byteCount", byteCount); put("durationMs", durationMs); put("width", width); put("height", height); put("posterAssetId", posterAssetId ?: ""); put("variantsJson", variantsJson); put("updatedAt", updatedAt); put("modifiedBy", modifiedBy); put("deleted", deleted)
    }
    private fun ReaderDeletionEntity.toJson() = JSONObject().apply {
        put("kind", kind); put("entityId", entityId); put("deletedAt", deletedAt); put("deletedBy", deletedBy)
    }
    private inline fun JSONArray?.forEachObject(block: (JSONObject) -> Unit) {
        if (this == null) return
        for (index in 0 until length()) optJSONObject(index)?.let(block)
    }

    private const val MAX_RESOURCE_BYTES = 512L * 1024L * 1024L
}
