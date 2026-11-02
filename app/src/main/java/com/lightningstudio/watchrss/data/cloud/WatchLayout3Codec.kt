package com.lightningstudio.watchrss.data.cloud

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.WeakHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** Layout 3 wire codec. Publication is deliberately still gated in WatchCloudSyncService.
 * A decoded document is the authenticated source of every retained descriptor/token. */
internal object WatchLayout3Format {
    const val OVERLAY_NAME = "private/overlay-v1.json"
    const val OVERLAY_FEATURE = "r48.private-overlay.v1"
    const val MAX_OBJECT_BYTES = 256 * 1024 * 1024
    const val MAX_SAFE_INTEGER = 9007199254740991L
    val descriptorFeatures = setOf("r48.entry-ref.v1", "r48.object-key.v1")
    private val featureName = Regex("[a-z0-9][a-z0-9.-]{0,47}")
    val hash = Regex("[a-f0-9]{64}")

    data class Access(val readable: Boolean, val writable: Boolean)

    fun access(raw: WatchLayout3RawDocument, known: Set<String>): Access {
        val json = raw.json()
        if (json.has("requiredFeatures")) {
            val required = json.getJSONArray("requiredFeatures")
            if ((0 until required.length()).any { required.get(it) !is String || required.getString(it) !in known })
                return Access(false, false)
        }
        if (!json.has("features")) return Access(true, true)
        val f = json.getJSONObject("features")
        require(f.keys().asSequence().toSet() == setOf("incompat", "roCompat"))
        fun names(key: String): List<String> {
            val a = f.getJSONArray(key)
            require(a.length() <= 16)
            return (0 until a.length()).map { i ->
                (a.get(i) as? String ?: error("cloud_format_invalid_features")).also { require(featureName.matches(it)) }
            }.also { require(it.distinct().size == it.size) }
        }
        val incompat = names("incompat")
        val ro = names("roCompat")
        require(incompat.intersect(ro.toSet()).isEmpty())
        val readable = incompat.all { it in known }
        return Access(readable, readable && ro.all { it in known })
    }

    fun integer(raw: WatchLayout3RawDocument, name: String, max: Long = MAX_SAFE_INTEGER): Long {
        val token = requireNotNull(raw.raw(listOf(name)))
        // Do not let JSONObject/Double round an authenticated non-integer token.
        require(token.matches(Regex("0|[1-9][0-9]*"))) { "cloud_format_integer" }
        return token.toLong().also { require(it in 0..max) }
    }
    fun text(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    fun base64(value: String, size: Int): ByteArray = Base64.getDecoder().decode(value).also {
        require(it.size == size && Base64.getEncoder().encodeToString(it) == value)
    }
}

internal data class WatchLayout3Object(val raw: WatchLayout3RawDocument) {
    val name: String get() = raw.json().getString("name")
    val access: WatchLayout3Format.Access get() = WatchLayout3Format.access(raw, WatchLayout3Format.descriptorFeatures)
    val json: JSONObject get() = raw.json()
    val chunks: List<WatchCloudChunk> get() = json.optJSONArray("chunks")?.let { a ->
        (0 until a.length()).map { WatchCloudChunk.parse(a.getJSONObject(it)) }
    }.orEmpty()
}

internal data class WatchLayout3Manifest(val raw: WatchLayout3RawDocument) {
    val json: JSONObject get() = raw.json()
    val snapshotId: String get() = json.getString("snapshotId")
    val keyVersion: Int get() = json.getInt("keyVersion")
    val objects: List<WatchLayout3Object> get() = raw.node(listOf("objects"))!!.elements!!.map {
        WatchLayout3Object(WatchLayout3RawDocument.parse(raw.source.substring(it.start, it.end)))
    }
    val access: WatchLayout3Format.Access get() = WatchLayout3Format.access(raw, setOf(WatchLayout3Format.OVERLAY_FEATURE))

    fun validate() {
        val j = json
        require(j.getString("format") == "watchrss-cloud-snapshot" && WatchLayout3Format.integer(raw, "schemaVersion") == 2L)
        require(snapshotId.matches(Regex("[a-zA-Z0-9-]{1,128}")) && j.getString("sourceDeviceId").isNotEmpty())
        require(WatchLayout3Format.integer(raw, "deviceSequence") > 0 && WatchLayout3Format.integer(raw, "keyVersion", Int.MAX_VALUE.toLong()) > 0)
        WatchLayout3Format.integer(raw, "createdAtMillis")
        for (key in j.getJSONObject("parentHeads").keys()) require(j.getJSONObject("parentHeads").get(key) is String)
        val observed = WatchLayout3RawDocument.parse(requireNotNull(raw.raw(listOf("observedHeads"))))
        observed.json().keys().forEach { WatchLayout3Format.integer(observed, it) }
        if (!access.readable) throw WatchCloudUpgradeRequired()
        val list = objects
        require(list.size <= 1024 && list.map { it.name }.distinct().size == list.size)
        val overlay = j.optJSONObject("features")?.getJSONArray("incompat")?.let { a ->
            (0 until a.length()).any { a.getString(it) == WatchLayout3Format.OVERLAY_FEATURE }
        } == true
        require(if (overlay) list.any { it.name == WatchLayout3Format.OVERLAY_NAME && !it.json.has("entryRef") }
            else list.none { it.name == WatchLayout3Format.OVERLAY_NAME || it.name.startsWith("private/articles/") })
        for (o in list) {
            require(o.name.isNotEmpty())
            // Unknown objects are opaque authenticated slices. They are carried, never projected.
            if (!o.access.readable) continue
            val row = o.json
            if (row.getString("encoding") !in setOf("identity", "gzip", "gzip-chunks-v1")) continue
            require(!(row.has("readerKeyEnvelope") && row.has("objectKeyEnvelope")))
            for (field in listOf("readerKeyEnvelope", "objectKeyEnvelope")) row.optJSONObject(field)?.let { e ->
                require(e.getInt("version") == 1)
                WatchLayout3Format.base64(e.getString("saltBase64"), 16)
                WatchLayout3Format.base64(e.getString("nonceBase64"), 12)
                WatchLayout3Format.base64(e.getString("ciphertextBase64"), 48)
                if (field == "objectKeyEnvelope") {
                    require(hasFeature(row, "r48.object-key.v1"))
                    require(e.getString("scope").matches(Regex("migration:[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")))
                }
            }
            WatchLayout3Format.integer(o.raw, "originalBytes", WatchLayout3Format.MAX_OBJECT_BYTES.toLong())
            if (row.has("entryRef")) {
                val ref = row.getJSONObject("entryRef")
                require(hasFeature(row, "r48.entry-ref.v1") && !row.has("chunks") && !row.has("readerKeyEnvelope") && !row.has("objectKeyEnvelope"))
                val path = ref.getString("entryPath")
                require(ref.getString("encoding") == "identity" && path.isNotEmpty() && !path.contains('\\') && !path.contains('\u0000') &&
                    path.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
                val target = list.single { it.name == ref.getString("object") }
                require(target !== o && target.name != o.name && !target.json.has("entryRef") && target.chunks.isNotEmpty())
                continue
            }
            val encoded = WatchLayout3Format.integer(o.raw, "encodedBytes", WatchLayout3Format.MAX_OBJECT_BYTES.toLong())
            val nodes = o.raw.node(listOf("chunks"))!!.elements!!
            require(nodes.size in 1..1024)
            var size = 0L
            for (node in nodes) {
                val part = WatchLayout3RawDocument.parse(o.raw.source.substring(node.start, node.end))
                val c = part.json()
                val plain = WatchLayout3Format.integer(part, "plaintextBytes", 8 * 1024 * 1024)
                require(WatchLayout3Format.integer(part, "ciphertextBytes", 8 * 1024 * 1024) == plain + 51)
                require(listOf("plaintextSha256", "ciphertextSha256").all { WatchLayout3Format.hash.matches(c.getString(it)) })
                WatchLayout3Format.base64(c.getString("saltBase64"), 16)
                WatchLayout3Format.base64(c.getString("nonceBase64"), 12)
                size += plain
            }
            require(size == encoded)
            if (row.has("contentSha256")) require(WatchLayout3Format.hash.matches(row.getString("contentSha256")))
        }
    }

    private fun hasFeature(row: JSONObject, name: String): Boolean = row.optJSONObject("features")?.optJSONArray("incompat")?.let { a ->
        (0 until a.length()).any { a.getString(it) == name }
    } == true
}

internal data class WatchLayout3LogicalObject(
    val name: String, val bytes: ByteArray, val compress: Boolean = true,
    val privateBook: JSONObject? = null, val privateArticle: JSONObject? = null
)
internal data class WatchLayout3Snapshot(
    val manifest: WatchLayout3Manifest, val encryptedManifest: ByteArray, val newChunks: Map<String, ByteArray>
)

/** Pure JVM crypto; no account, network or Room access. */
internal class WatchLayout3Codec(private val random: SecureRandom = SecureRandom()) {
    private val authenticatedRoots = WeakHashMap<WatchLayout3Manifest, String>()

    fun decryptManifest(key: ByteArray, id: String, encrypted: ByteArray): WatchLayout3Manifest {
        require(key.size == 32 && encrypted.size >= 51 && encrypted.size <= 8 * 1024 * 1024 + 51)
        val magic = String(encrypted, 0, 7, Charsets.US_ASCII)
        require(magic in setOf("WRSSCM2", "WRSSCM3"))
        val info = if (magic == "WRSSCM3") "watchrss/cloud/manifest/v2/$id" else "watchrss/cloud/manifest/v1"
        val plain = open(key, encrypted, info, "watchrss-cloud-manifest-v2:$id")
        return try {
            WatchLayout3Manifest(WatchLayout3RawDocument.parse(plain)).also {
                require(it.snapshotId == id)
                it.validate()
                authenticatedRoots[it] = WatchCloudCodec.sha256(key)
            }
        } finally { plain.fill(0) }
    }

    fun create(
        key: ByteArray, keyVersion: Int, device: String, sequence: Long,
        objects: List<WatchLayout3LogicalObject>, settings: JSONObject,
        parentHeads: JSONObject = JSONObject(), observedHeads: JSONObject = JSONObject(),
        previous: WatchLayout3Manifest? = null, removedNames: Set<String> = emptySet(),
        snapshotId: String = UUID.randomUUID().toString(), createdAt: Long = System.currentTimeMillis()
    ): WatchLayout3Snapshot {
        require(key.size == 32 && keyVersion > 0 && sequence > 0)
        val settingsRaw = WatchLayout3RawDocument.parse(settings.toString())
        require(WatchLayout3Format.integer(settingsRaw, "snapshotContentLayout") == 3L)
        listOf("libraryEpoch", "contentGeneration").forEach { WatchCloudProtocolV2.uuid(settings.getString(it)) }
        require(objects.map { it.name }.distinct().size == objects.size)
        previous?.let {
            require(it.keyVersion == keyVersion && authenticatedRoots[it] == WatchCloudCodec.sha256(key)) { "cloud_format_reuse_scope" }
            if (!it.access.writable) throw WatchCloudUpgradeRequired()
            val oldSettings = it.json.getJSONObject("settings")
            for (field in listOf("libraryEpoch", "contentGeneration", "ownerUserId")) {
                if (oldSettings.has(field)) require(oldSettings.get(field) == settings.get(field)) { "cloud_format_reuse_scope" }
            }
        }
        val requested = objects.map { it.name }.toSet()
        val old = previous?.objects.orEmpty()
        old.filter { !it.access.writable }.forEach {
            if (it.name in requested || it.name in removedNames) throw WatchCloudUpgradeRequired()
        }
        val carried = old.filter { it.name !in requested && it.name !in removedNames }
        val chunks = linkedMapOf<String, ByteArray>()
        val descriptors = objects.map { logical ->
            require(logical.bytes.size <= WatchLayout3Format.MAX_OBJECT_BYTES)
            val basis = old.singleOrNull { it.name == logical.name }
            if (basis != null) {
                if (basis.json.getString("encoding") !in setOf("identity", "gzip", "gzip-chunks-v1")) throw WatchCloudUpgradeRequired()
                if (basis.json.optString("contentSha256") == WatchCloudCodec.sha256(logical.bytes) &&
                    basis.json.getLong("originalBytes") == logical.bytes.size.toLong()) {
                    val metadata = JSONObject()
                    logical.privateBook?.let { metadata.put("privateBook", it) }
                    logical.privateArticle?.let { metadata.put("privateArticle", it) }
                    return@map WatchLayout3Object(basis.raw.merge(emptyList(), metadata))
                }
                // Future chunk fields cannot be rebound to new ciphertext. Keep the old
                // object until a writer understands those fields.
                val knownChunkFields = setOf("plaintextSha256", "ciphertextSha256", "plaintextBytes", "ciphertextBytes", "saltBase64", "nonceBase64")
                for (node in basis.raw.node(listOf("chunks"))?.elements.orEmpty()) {
                    val part = WatchLayout3RawDocument.parse(basis.raw.source.substring(node.start, node.end))
                    if (part.unknownBytes(emptyList(), knownChunkFields) > 0) throw WatchCloudUpgradeRequired()
                }
            }
            val encoded = if (logical.compress) contentChunks(logical.bytes).map(::gzip) else logical.bytes.toListOfChunks()
            val parts = encoded.map { plain ->
                val hash = WatchCloudCodec.sha256(plain)
                val blob = seal(key, plain, "WRSSCC2", "watchrss/cloud/chunk/v1", "watchrss-cloud-chunk-v1:$hash")
                val chunk = WatchCloudChunk(hash, WatchCloudCodec.sha256(blob), plain.size, blob.size,
                    Base64.getEncoder().encodeToString(blob.copyOfRange(7, 23)), Base64.getEncoder().encodeToString(blob.copyOfRange(23, 35)))
                chunks[chunk.ciphertextSha256] = blob
                chunk
            }
            val descriptor = JSONObject().put("name", logical.name).put("encoding", if (logical.compress) "gzip-chunks-v1" else "identity")
                .put("originalBytes", logical.bytes.size).put("encodedBytes", encoded.sumOf { it.size.toLong() })
                .put("contentSha256", WatchCloudCodec.sha256(logical.bytes)).put("chunks", JSONArray(parts.map { it.json() }))
            logical.privateBook?.let { descriptor.put("privateBook", it) }
            logical.privateArticle?.let { descriptor.put("privateArticle", it) }
            var raw = (basis?.raw ?: WatchLayout3RawDocument.parse(descriptor.toString())).merge(emptyList(), descriptor)
            for (field in listOf("entryRef", "objectKeyEnvelope", "readerKeyEnvelope")) raw = raw.remove(listOf(field))
            WatchLayout3Object(raw)
        }
        val fresh = JSONObject().put("format", "watchrss-cloud-snapshot").put("schemaVersion", 2).put("snapshotId", snapshotId)
            .put("sourceDeviceId", device).put("deviceSequence", sequence).put("keyVersion", keyVersion).put("createdAtMillis", createdAt)
            .put("parentHeads", parentHeads).put("observedHeads", observedHeads).put("objects", JSONArray())
            .put("settings", settings).put("credentialEnvelopes", JSONObject())
        var raw = (previous?.raw ?: WatchLayout3RawDocument.parse(fresh.toString())).merge(emptyList(), fresh)
        raw = raw.patch(listOf("objects"), (carried + descriptors).joinToString(",", "[", "]") { it.raw.source })
        if ((carried + descriptors).any { it.name == WatchLayout3Format.OVERLAY_NAME } && !raw.json().has("features"))
            raw = raw.patch(listOf("features"), JSONObject().put("incompat", JSONArray().put(WatchLayout3Format.OVERLAY_FEATURE))
                .put("roCompat", JSONArray()).toString())
        val manifest = WatchLayout3Manifest(raw).also { it.validate(); if (!it.access.writable) throw WatchCloudUpgradeRequired() }
        val blob = seal(key, raw.source.toByteArray(), "WRSSCM3", "watchrss/cloud/manifest/v2/$snapshotId", "watchrss-cloud-manifest-v2:$snapshotId")
        authenticatedRoots[manifest] = WatchCloudCodec.sha256(key)
        return WatchLayout3Snapshot(manifest, blob, chunks)
    }

    fun restore(key: ByteArray, manifest: WatchLayout3Manifest, name: String, read: (String) -> ByteArray): ByteArray {
        require(key.size == 32)
        manifest.validate()
        val objectValue = manifest.objects.single { it.name == name }
        if (!objectValue.access.readable) throw WatchCloudUpgradeRequired()
        val o = objectValue.json
        val expected = WatchLayout3Format.integer(objectValue.raw, "originalBytes", WatchLayout3Format.MAX_OBJECT_BYTES.toLong()).toInt()
        val ref = o.optJSONObject("entryRef")
        if (ref != null) {
            val archive = restore(key, manifest, ref.getString("object"), read)
            val bytes = zipEntries(archive, WatchLayout3Format.MAX_OBJECT_BYTES.toLong())[ref.getString("entryPath")]
                ?: error("cloud_entry_ref_missing")
            return verifyObject(o, bytes, expected)
        }
        val envelope = o.optJSONObject("objectKeyEnvelope") ?: o.optJSONObject("readerKeyEnvelope")
        val objectKey = envelope?.let { e ->
            val objectRoot = o.has("objectKeyEnvelope")
            val derived = WatchCloudCodec.hkdf(key, WatchLayout3Format.base64(e.getString("saltBase64"), 16),
                "watchrss/cloud/${if (objectRoot) "object" else "reader"}-key/v1".toByteArray())
            try {
                WatchCloudCodec.aes(false, derived, WatchLayout3Format.base64(e.getString("nonceBase64"), 12),
                    WatchLayout3Format.base64(e.getString("ciphertextBase64"), 48),
                    "watchrss-${if (objectRoot) "object" else "reader"}-key-v1:${e.optString("scope").ifEmpty { name }}".toByteArray())
                    .also { require(it.size == 32) }
            } finally { derived.fill(0) }
        } ?: key
        try {
            val output = ByteArrayOutputStream()
            for (part in objectValue.chunks) {
                val blob = read(part.ciphertextSha256)
                require(blob.size == part.ciphertextBytes && WatchCloudCodec.sha256(blob) == part.ciphertextSha256 &&
                    blob.copyOfRange(0, 7).contentEquals("WRSSCC2".toByteArray()))
                require(blob.copyOfRange(7, 23).contentEquals(WatchLayout3Format.base64(part.saltBase64, 16)) &&
                    blob.copyOfRange(23, 35).contentEquals(WatchLayout3Format.base64(part.nonceBase64, 12)))
                val plain = open(objectKey, blob, "watchrss/cloud/chunk/v1", "watchrss-cloud-chunk-v1:${part.plaintextSha256}")
                try {
                    require(plain.size == part.plaintextBytes && WatchCloudCodec.sha256(plain) == part.plaintextSha256)
                    val max = if (o.getString("encoding") == "gzip") o.getInt("encodedBytes") else expected
                    val decoded = when (o.getString("encoding")) {
                        "gzip-chunks-v1" -> gunzip(plain, minOf(WATCH_CLOUD_CHUNK_BYTES, max - output.size()))
                        "identity", "gzip" -> plain
                        else -> throw WatchCloudUpgradeRequired()
                    }
                    require(output.size().toLong() + decoded.size <= max)
                    output.write(decoded)
                } finally { plain.fill(0) }
            }
            val bytes = if (o.getString("encoding") == "gzip") gunzip(output.toByteArray(), expected) else output.toByteArray()
            return verifyObject(o, bytes, expected)
        } finally { if (objectKey !== key) objectKey.fill(0) }
    }

    private fun verifyObject(json: JSONObject, bytes: ByteArray, expected: Int): ByteArray = bytes.also {
        require(it.size == expected)
        if (json.has("contentSha256")) require(WatchCloudCodec.sha256(it) == json.getString("contentSha256"))
    }
    private fun open(key: ByteArray, blob: ByteArray, info: String, aad: String): ByteArray {
        val derived = WatchCloudCodec.hkdf(key, blob.copyOfRange(7, 23), info.toByteArray())
        return try { WatchCloudCodec.aes(false, derived, blob.copyOfRange(23, 35), blob.copyOfRange(35, blob.size), aad.toByteArray()) }
        finally { derived.fill(0) }
    }
    private fun seal(key: ByteArray, plain: ByteArray, magic: String, info: String, aad: String): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val derived = WatchCloudCodec.hkdf(key, salt, info.toByteArray())
        return try { magic.toByteArray() + salt + nonce + WatchCloudCodec.aes(true, derived, nonce, plain, aad.toByteArray()) }
        finally { derived.fill(0) }
    }
    companion object {
        /** PodJS content-defined boundaries, before independent gzip members. */
        fun contentChunks(bytes: ByteArray): List<ByteArray> {
            val result = mutableListOf<ByteArray>()
            var start = 0
            var rolling = 0
            for (i in bytes.indices) {
                rolling = (rolling shl 1) + ((bytes[i].toInt() and 255) + 1) * 0x9e3779b1.toInt()
                val n = i - start + 1
                if (n >= 65536 && ((rolling and 262143) == 0 || n >= 1048576)) {
                    result += bytes.copyOfRange(start, i + 1); start = i + 1; rolling = 0
                }
            }
            if (start < bytes.size || result.isEmpty()) result += bytes.copyOfRange(start, bytes.size)
            return result
        }
        private fun ByteArray.toListOfChunks(): List<ByteArray> = if (isEmpty()) listOf(byteArrayOf()) else
            (indices step WATCH_CLOUD_CHUNK_BYTES).map { start -> copyOfRange(start, minOf(size, start + WATCH_CLOUD_CHUNK_BYTES)) }
        private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
        private fun gunzip(bytes: ByteArray, max: Int): ByteArray = GZIPInputStream(bytes.inputStream()).use { input -> bounded(input, max.toLong()) }
        internal fun bounded(input: java.io.InputStream, max: Long): ByteArray {
            require(max in 0..WatchLayout3Format.MAX_OBJECT_BYTES)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(out.size().toLong() + n <= max) { "cloud_snapshot_inflate_limit" }
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
        internal fun zipEntries(bytes: ByteArray, max: Long): Map<String, ByteArray> {
            require(bytes.size in 22..WatchLayout3Format.MAX_OBJECT_BYTES)
            val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            fun u16(at: Int): Int { require(at >= 0 && at <= bytes.size - 2); return view.getShort(at).toInt() and 65535 }
            fun u32(at: Int): Long { require(at >= 0 && at <= bytes.size - 4); return view.getInt(at).toLong() and 0xffffffffL }
            var end = bytes.size - 22
            while (end >= maxOf(0, bytes.size - 65557) && !(u32(end) == 0x06054b50L && end + 22 + u16(end + 20) == bytes.size)) end--
            require(end >= maxOf(0, bytes.size - 65557) && u16(end + 4) == 0 && u16(end + 6) == 0)
            val count = u16(end + 10)
            require(u16(end + 8) == count)
            val start = u32(end + 16)
            require(start + u32(end + 12) == end.toLong())
            var at = start.toInt()
            val directory = linkedMapOf<String, Pair<Long, Long>>()
            repeat(count) {
                require(at >= 0 && at + 46 <= end && u32(at) == 0x02014b50L)
                val flags = u16(at + 8); val method = u16(at + 10)
                val packed = u32(at + 20); val length = u32(at + 24)
                val n = u16(at + 28); val extra = u16(at + 30); val comment = u16(at + 32)
                val local = u32(at + 42)
                require(flags and 1 == 0 && method in setOf(0, 8) && at + 46 + n + extra + comment <= end &&
                    packed <= max && length <= max && local + 30 <= start)
                val name = WatchLayout3Format.text(bytes.copyOfRange(at + 46, at + 46 + n))
                require(!directory.containsKey(name))
                val localAt = local.toInt()
                require(u32(localAt) == 0x04034b50L && u16(localAt + 8) == method)
                val ln = u16(localAt + 26); val le = u16(localAt + 28)
                val body = local + 30 + ln + le
                require(body + packed <= start && WatchLayout3Format.text(bytes.copyOfRange(localAt + 30, localAt + 30 + ln)) == name)
                directory[name] = length to u32(at + 16)
                at += 46 + n + extra + comment
            }
            require(at == end && directory.size == count)
            val entries = linkedMapOf<String, ByteArray>()
            var total = 0L
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    require(!e.isDirectory && entries.size < 65535 && !entries.containsKey(e.name) && !e.name.contains('\\') &&
                        !e.name.contains('\u0000') && e.name.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
                    val expected = requireNotNull(directory[e.name])
                    val body = bounded(zip, minOf(max - total, expected.first))
                    require(body.size.toLong() == expected.first && CRC32().apply { update(body) }.value == expected.second)
                    total += body.size
                    entries[e.name] = body
                    zip.closeEntry()
                }
            }
            require(entries.isNotEmpty() && entries.keys == directory.keys)
            return entries
        }
    }
}
