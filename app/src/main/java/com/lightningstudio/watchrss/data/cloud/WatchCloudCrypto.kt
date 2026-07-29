package com.lightningstudio.watchrss.data.cloud

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal const val WATCH_CLOUD_CHUNK_BYTES = 4 * 1024 * 1024

data class WatchCloudLogicalObject(
    val name: String,
    val bytes: ByteArray,
    val compress: Boolean = true
)

data class WatchCloudChunk(
    val plaintextSha256: String,
    val ciphertextSha256: String,
    val plaintextBytes: Int,
    val ciphertextBytes: Int,
    val saltBase64: String,
    val nonceBase64: String
) {
    fun json() = JSONObject().apply {
        put("plaintextSha256", plaintextSha256)
        put("ciphertextSha256", ciphertextSha256)
        put("plaintextBytes", plaintextBytes)
        put("ciphertextBytes", ciphertextBytes)
        put("saltBase64", saltBase64)
        put("nonceBase64", nonceBase64)
    }

    companion object {
        fun parse(json: JSONObject) = WatchCloudChunk(
            json.getString("plaintextSha256"),
            json.getString("ciphertextSha256"),
            json.getInt("plaintextBytes"),
            json.getInt("ciphertextBytes"),
            json.getString("saltBase64"),
            json.getString("nonceBase64")
        )
    }
}

data class WatchCloudObject(
    val name: String,
    val encoding: String,
    val originalBytes: Long,
    val encodedBytes: Long,
    val chunks: List<WatchCloudChunk>
) {
    fun json() = JSONObject().apply {
        put("name", name)
        put("encoding", encoding)
        put("originalBytes", originalBytes)
        put("encodedBytes", encodedBytes)
        put("chunks", JSONArray().apply { chunks.forEach { put(it.json()) } })
    }

    companion object {
        fun parse(json: JSONObject) = WatchCloudObject(
            name = json.getString("name"),
            encoding = json.getString("encoding"),
            originalBytes = json.getLong("originalBytes"),
            encodedBytes = json.getLong("encodedBytes"),
            chunks = json.getJSONArray("chunks").objects(WatchCloudChunk::parse)
        )
    }
}

data class WatchCloudManifest(
    val snapshotId: String,
    val sourceDeviceId: String,
    val deviceSequence: Long,
    val keyVersion: Int,
    val createdAtMillis: Long,
    val parentHeads: Map<String, String>,
    val observedHeads: Map<String, Long>,
    val objects: List<WatchCloudObject>
) {
    val chunks: List<WatchCloudChunk>
        get() = objects.flatMap(WatchCloudObject::chunks)

    fun json() = JSONObject().apply {
        put("format", "watchrss-cloud-snapshot")
        put("schemaVersion", 2)
        put("snapshotId", snapshotId)
        put("sourceDeviceId", sourceDeviceId)
        put("deviceSequence", deviceSequence)
        put("keyVersion", keyVersion)
        put("createdAtMillis", createdAtMillis)
        put("parentHeads", JSONObject(parentHeads))
        put("observedHeads", JSONObject(observedHeads))
        put("objects", JSONArray().apply { objects.forEach { put(it.json()) } })
        put("settings", JSONObject())
        put("credentialEnvelopes", JSONObject())
    }

    companion object {
        fun parse(json: JSONObject): WatchCloudManifest {
            require(
                json.getString("format") == "watchrss-cloud-snapshot" &&
                    json.getInt("schemaVersion") == 2
            ) { "不支持的云快照格式" }
            return WatchCloudManifest(
                snapshotId = json.getString("snapshotId"),
                sourceDeviceId = json.getString("sourceDeviceId"),
                deviceSequence = json.getLong("deviceSequence"),
                keyVersion = json.optInt("keyVersion", 1),
                createdAtMillis = json.getLong("createdAtMillis"),
                parentHeads = json.getJSONObject("parentHeads").stringMap(),
                observedHeads = json.getJSONObject("observedHeads").longMap(),
                objects = json.getJSONArray("objects").objects(WatchCloudObject::parse)
            )
        }
    }
}

data class WatchEncryptedSnapshot(
    val manifest: WatchCloudManifest,
    val encryptedManifest: ByteArray,
    val newChunks: Map<String, ByteArray>
)

class WatchCloudCodec(private val random: SecureRandom = SecureRandom()) {
    fun create(
        accountKey: ByteArray,
        keyVersion: Int,
        sourceDeviceId: String,
        sequence: Long,
        objects: List<WatchCloudLogicalObject>,
        parentHeads: Map<String, String>,
        observedHeads: Map<String, Long>,
        previous: WatchCloudManifest? = null,
        carried: List<WatchCloudObject> = emptyList(),
        snapshotId: String = UUID.randomUUID().toString()
    ): WatchEncryptedSnapshot {
        require(accountKey.size == 32 && sequence > 0 && keyVersion > 0)
        require((objects.map { it.name } + carried.map { it.name }).distinct().size ==
            objects.size + carried.size)
        val reusable = previous?.chunks?.associateBy(WatchCloudChunk::plaintextSha256).orEmpty()
        val newChunks = linkedMapOf<String, ByteArray>()
        val descriptors = objects.map { logical ->
            val encodedChunks = if (logical.compress) {
                logical.bytes.chunks().map(::gzip)
            } else {
                logical.bytes.chunks()
            }
            val chunks = encodedChunks.map { plaintext ->
                val plainHash = sha256(plaintext)
                reusable[plainHash] ?: encryptChunk(accountKey, plaintext, plainHash).also {
                    newChunks[it.first.ciphertextSha256] = it.second
                }.first
            }
            WatchCloudObject(
                logical.name,
                if (logical.compress) "gzip-chunks-v1" else "identity",
                logical.bytes.size.toLong(),
                encodedChunks.sumOf { it.size.toLong() },
                chunks
            )
        }
        val manifest = WatchCloudManifest(
            snapshotId,
            sourceDeviceId,
            sequence,
            keyVersion,
            System.currentTimeMillis(),
            parentHeads,
            observedHeads,
            carried + descriptors
        )
        return WatchEncryptedSnapshot(manifest, encryptManifest(accountKey, manifest), newChunks)
    }

    fun decryptManifest(
        accountKey: ByteArray,
        snapshotId: String,
        encrypted: ByteArray
    ): WatchCloudManifest {
        require(encrypted.size > MANIFEST_HEADER + TAG_BYTES)
        require(encrypted.copyOfRange(0, MANIFEST_MAGIC.size).contentEquals(MANIFEST_MAGIC))
        val saltAt = MANIFEST_MAGIC.size
        val nonceAt = saltAt + SALT_BYTES
        val cipherAt = nonceAt + NONCE_BYTES
        val plain = aes(
            false,
            hkdf(accountKey, encrypted.copyOfRange(saltAt, nonceAt), MANIFEST_INFO),
            encrypted.copyOfRange(nonceAt, cipherAt),
            encrypted.copyOfRange(cipherAt, encrypted.size),
            "watchrss-cloud-manifest-v2:$snapshotId".toByteArray()
        )
        return WatchCloudManifest.parse(JSONObject(plain.toString(Charsets.UTF_8))).also {
            require(it.snapshotId == snapshotId)
        }
    }

    fun restore(
        accountKey: ByteArray,
        manifest: WatchCloudManifest,
        chunk: (String) -> ByteArray
    ): Map<String, ByteArray> = manifest.objects.associate { objectDescriptor ->
        val encodedChunks = objectDescriptor.chunks.map { descriptor ->
            decryptChunk(accountKey, descriptor, chunk(descriptor.ciphertextSha256))
        }
        require(encodedChunks.sumOf { it.size.toLong() } == objectDescriptor.encodedBytes)
        val plain = when (objectDescriptor.encoding) {
            "gzip-chunks-v1" -> encodedChunks.join { gunzip(it) }
            "gzip" -> gunzip(encodedChunks.join())
            "identity" -> encodedChunks.join()
            else -> error("不支持的云快照编码：${objectDescriptor.encoding}")
        }
        require(plain.size.toLong() == objectDescriptor.originalBytes)
        objectDescriptor.name to plain
    }

    private fun encryptChunk(
        key: ByteArray,
        plain: ByteArray,
        plainHash: String
    ): Pair<WatchCloudChunk, ByteArray> {
        val salt = bytes(SALT_BYTES)
        val nonce = bytes(NONCE_BYTES)
        val ciphertext = aes(
            true,
            hkdf(key, salt, CHUNK_INFO),
            nonce,
            plain,
            "watchrss-cloud-chunk-v1:$plainHash".toByteArray()
        )
        val blob = CHUNK_MAGIC + salt + nonce + ciphertext
        return WatchCloudChunk(
            plainHash,
            sha256(blob),
            plain.size,
            blob.size,
            salt.b64(),
            nonce.b64()
        ) to blob
    }

    private fun decryptChunk(
        key: ByteArray,
        descriptor: WatchCloudChunk,
        blob: ByteArray
    ): ByteArray {
        require(blob.size == descriptor.ciphertextBytes && sha256(blob) == descriptor.ciphertextSha256)
        require(blob.copyOfRange(0, CHUNK_MAGIC.size).contentEquals(CHUNK_MAGIC))
        val saltAt = CHUNK_MAGIC.size
        val nonceAt = saltAt + SALT_BYTES
        val cipherAt = nonceAt + NONCE_BYTES
        val salt = blob.copyOfRange(saltAt, nonceAt)
        val nonce = blob.copyOfRange(nonceAt, cipherAt)
        require(salt.contentEquals(descriptor.saltBase64.b64Bytes()))
        require(nonce.contentEquals(descriptor.nonceBase64.b64Bytes()))
        return aes(
            false,
            hkdf(key, salt, CHUNK_INFO),
            nonce,
            blob.copyOfRange(cipherAt, blob.size),
            "watchrss-cloud-chunk-v1:${descriptor.plaintextSha256}".toByteArray()
        ).also {
            require(it.size == descriptor.plaintextBytes && sha256(it) == descriptor.plaintextSha256)
        }
    }

    private fun encryptManifest(key: ByteArray, manifest: WatchCloudManifest): ByteArray {
        val salt = bytes(SALT_BYTES)
        val nonce = bytes(NONCE_BYTES)
        val ciphertext = aes(
            true,
            hkdf(key, salt, MANIFEST_INFO),
            nonce,
            manifest.json().toString().toByteArray(),
            "watchrss-cloud-manifest-v2:${manifest.snapshotId}".toByteArray()
        )
        return MANIFEST_MAGIC + salt + nonce + ciphertext
    }

    private fun bytes(size: Int) = ByteArray(size).also(random::nextBytes)

    private fun ByteArray.chunks(): List<ByteArray> {
        if (isEmpty()) return listOf(ByteArray(0))
        val source = this
        return buildList {
            var offset = 0
            while (offset < source.size) {
                val end = minOf(source.size, offset + WATCH_CLOUD_CHUNK_BYTES)
                add(source.copyOfRange(offset, end))
                offset = end
            }
        }
    }

    companion object {
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
        private val CHUNK_MAGIC = "WRSSCC2".toByteArray(Charsets.US_ASCII)
        private val MANIFEST_MAGIC = "WRSSCM2".toByteArray(Charsets.US_ASCII)
        private val CHUNK_INFO = "watchrss/cloud/chunk/v1".toByteArray()
        private val MANIFEST_INFO = "watchrss/cloud/manifest/v1".toByteArray()
        private const val MANIFEST_HEADER = 7 + SALT_BYTES + NONCE_BYTES

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        fun hkdf(input: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
            val extract = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(salt, "HmacSHA256"))
            }.doFinal(input)
            return Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(extract, "HmacSHA256"))
                update(info)
                update(1.toByte())
            }.doFinal()
        }

        fun aes(
            encrypt: Boolean,
            key: ByteArray,
            nonce: ByteArray,
            input: ByteArray,
            aad: ByteArray
        ): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(
                if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, nonce)
            )
            updateAAD(aad)
            doFinal(input)
        }

        private fun gzip(bytes: ByteArray) = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(bytes) }
            output.toByteArray()
        }

        private fun gunzip(bytes: ByteArray) =
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

        private fun List<ByteArray>.join(
            transform: (ByteArray) -> ByteArray = { it }
        ): ByteArray = ByteArrayOutputStream().use { output ->
            forEach { output.write(transform(it)) }
            output.toByteArray()
        }
    }
}

data class WatchDeviceKeyEnvelope(
    val algorithm: String,
    val keyVersion: Int,
    val wrappedKeyBase64: String,
    val nonceBase64: String,
    val ephemeralPublicKeySpki: String
)

fun unwrapWatchDeviceEnvelope(
    envelope: WatchDeviceKeyEnvelope,
    userId: String,
    deviceId: String,
    privateKey: PrivateKey
): ByteArray {
    require(envelope.algorithm == "P-256+HKDF-SHA256+A256GCM")
    val ephemeral = KeyFactory.getInstance("EC").generatePublic(
        X509EncodedKeySpec(envelope.ephemeralPublicKeySpki.b64Bytes())
    )
    val secret = KeyAgreement.getInstance("ECDH").run {
        init(privateKey)
        doPhase(ephemeral, true)
        generateSecret()
    }
    return try {
        val salt = WatchCloudCodec.sha256(userId.toByteArray()).hexBytes()
        val info = "watchrss/device-envelope/v${envelope.keyVersion}/$deviceId".toByteArray()
        WatchCloudCodec.aes(
            false,
            WatchCloudCodec.hkdf(secret, salt, info),
            envelope.nonceBase64.b64Bytes(),
            envelope.wrappedKeyBase64.b64Bytes(),
            "watchrss-account-key:$userId:${envelope.keyVersion}:$deviceId".toByteArray()
        ).also { require(it.size == 32) }
    } finally {
        secret.fill(0)
    }
}

internal fun ByteArray.b64(): String = Base64.getEncoder().withoutPadding().encodeToString(this)
internal fun String.b64Bytes(): ByteArray = Base64.getDecoder().decode(this)

private fun String.hexBytes() = ByteArray(length / 2) { index ->
    substring(index * 2, index * 2 + 2).toInt(16).toByte()
}

private fun <T> JSONArray.objects(transform: (JSONObject) -> T) = buildList {
    for (index in 0 until length()) add(transform(getJSONObject(index)))
}

private fun JSONObject.stringMap() = keys().asSequence().associateWith(::getString)
private fun JSONObject.longMap() = keys().asSequence().associateWith(::getLong)
