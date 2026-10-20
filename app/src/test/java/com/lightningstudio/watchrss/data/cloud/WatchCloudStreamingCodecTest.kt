package com.lightningstudio.watchrss.data.cloud

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WatchCloudStreamingCodecTest {
    private val key = ByteArray(32) { it.toByte() }
    private fun gzip(bytes: ByteArray) = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()
    private fun sealed(bytes: ByteArray): Pair<WatchCloudChunk, ByteArray> {
        val salt = ByteArray(16) { (it + 2).toByte() }
        val nonce = ByteArray(12) { (it + 3).toByte() }
        val hash = WatchCloudCodec.sha256(bytes)
        val encrypted = WatchCloudCodec.aes(true,
            WatchCloudCodec.hkdf(key, salt, "watchrss/cloud/chunk/v1".toByteArray()), nonce, bytes,
            "watchrss-cloud-chunk-v1:$hash".toByteArray())
        val blob = "WRSSCC2".toByteArray() + salt + nonce + encrypted
        return WatchCloudChunk(hash, WatchCloudCodec.sha256(blob), bytes.size, blob.size,
            salt.b64(), nonce.b64()) to blob
    }
    @Test fun legacyGzipMayCrossCiphertextChunkBoundaries() = runBlocking {
        val plain = "legacy-long-text".repeat(10000).toByteArray()
        val encoded = gzip(plain)
        val parts = listOf(sealed(encoded.copyOfRange(0, 19)), sealed(encoded.copyOfRange(19, encoded.size)))
        val blobs = parts.associate { it.first.ciphertextSha256 to it.second }
        val descriptor = WatchCloudObject("legacy", "gzip", plain.size.toLong(), encoded.size.toLong(), parts.map { it.first })
        val restored = WatchCloudCodec().withRestoredObject(key, descriptor, { blobs.getValue(it) }) { it.readBytes() }
        assertArrayEquals(plain, restored)
    }
    @Test fun consumerPrefixDoesNotBypassTailLengthValidation() = runBlocking {
        val part = sealed("authenticated-content".toByteArray())
        val descriptor = WatchCloudObject("invalid-size", "identity", 1, part.first.plaintextBytes.toLong(), listOf(part.first))
        var rejected = false
        try { WatchCloudCodec().withRestoredObject(key, descriptor, { part.second }) { it.read() } }
        catch (expected: java.io.IOException) { rejected = true }
        assertTrue(rejected)
    }
    @Test fun producerChunksAndConsumerStreamsWithoutRetainingCiphertextMap() = runBlocking {
        val blobs = mutableMapOf<String, ByteArray>()
        val plain = ByteArray(32768) { (it % 127).toByte() }
        val objects = WatchCloudCodec().prepareStreamingObjects(key,
            listOf(WatchCloudStreamingObject("fixture") { output -> repeat(160) { output.write(plain) } }),
            storeChunk = { hash, bytes -> blobs[hash] = bytes })
        assertEquals(2, objects.single().chunks.size)
        val count = WatchCloudCodec().withRestoredObject(key, objects.single(), { blobs.getValue(it) }) { input ->
            val buffer = ByteArray(4096); var total = 0L
            while (true) { val n = input.read(buffer); if (n < 0) break; total += n }; total
        }
        assertEquals(plain.size * 160L, count)
    }
}
