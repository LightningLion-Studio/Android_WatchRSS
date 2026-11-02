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

    @Test fun streamingSnapshotKeepsReaderAndNoteAttachmentsCompatibleWithLegacyRestore() = runBlocking {
        val codec = WatchCloudCodec()
        val reader = requireNotNull(javaClass.getResourceAsStream("/cloud/phone-reader-cloud.wrss")).use { it.readBytes() }
        val noteState = """{"notes":[],"folders":[]}""".toByteArray()
        val archive = ByteArrayOutputStream().also { output ->
            java.util.zip.ZipOutputStream(output).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("notes/assets/image.jpg"))
                zip.write("fixture-image".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val payloads = linkedMapOf(
            "rss-state.json" to "{}".toByteArray(),
            "library-sync.json" to "{\"articles\":[]}".toByteArray(),
            "private-library.wrss" to reader,
            "notes-state.json" to noteState,
            "notes-assets.zip" to archive
        )
        val chunks = mutableMapOf<String, ByteArray>()
        val prepared = codec.prepareStreamingObjects(key, payloads.map { (name, bytes) ->
            WatchCloudStreamingObject(name) { it.write(bytes) }
        }, storeChunk = { hash, bytes -> chunks[hash] = bytes })
        val snapshot = codec.createFromPrepared(key, 1, "fixture-watch", 1, prepared, emptyMap(), emptyMap())
        val manifest = codec.decryptManifest(key, snapshot.manifest.snapshotId, snapshot.encryptedManifest)
        val attachments = manifest.objects.filter { it.name !in setOf("rss-state.json", "library-sync.json") }
        val restored = codec.restore(key, manifest.copy(objects = attachments), chunks::getValue)
        assertEquals(setOf("private-library.wrss", "notes-state.json", "notes-assets.zip"), restored.keys)
        restored.forEach { (name, bytes) -> assertArrayEquals(payloads.getValue(name), bytes) }
        val stateOnly = codec.prepareStreamingObjects(key,
            listOf(WatchCloudStreamingObject("rss-state.json") { it.write("{\"updated\":true}".toByteArray()) }),
            previous = manifest, storeChunk = { hash, bytes -> chunks[hash] = bytes })
        val carried = manifest.objects.filterNot { it.name == "rss-state.json" }
        val partial = codec.createFromPrepared(key, 1, "fixture-watch", 2, carried + stateOnly, emptyMap(), emptyMap())
        val replayed = codec.restore(key, partial.manifest, chunks::getValue)
        payloads.filterKeys { it != "rss-state.json" }.forEach { (name, bytes) ->
            assertArrayEquals(bytes, replayed.getValue(name))
        }
    }
}
