package com.lightningstudio.watchrss.phoneconnection.bluetooth

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.util.Base64

class WatchNoteAssetSyncPayloadTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun chunksAreReassembledAndVerified() {
        val data = ByteArray(700_000) { (it % 251).toByte() }
        val first = data.copyOfRange(0, 512 * 1024)
        val second = data.copyOfRange(first.size, data.size)
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it) }

        val firstResponse = WatchNoteAssetSyncPayload.apply(
            temporaryFolder.root,
            request("example.jpg", sha256, 0, 2, first)
        )
        val secondResponse = WatchNoteAssetSyncPayload.apply(
            temporaryFolder.root,
            request("example.jpg", sha256, 1, 2, second)
        )

        assertFalse(firstResponse.getBoolean("complete"))
        assertTrue(secondResponse.getBoolean("complete"))
        assertArrayEquals(
            data,
            temporaryFolder.root.resolve("notes/assets/example.jpg").readBytes()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsafeStorageKeyIsRejected() {
        WatchNoteAssetSyncPayload.apply(
            temporaryFolder.root,
            request("../escape.jpg", "0".repeat(64), 0, 1, byteArrayOf())
        )
    }

    private fun request(
        storageKey: String,
        sha256: String,
        index: Int,
        count: Int,
        data: ByteArray
    ) = JSONObject().apply {
        put("action", WatchNoteAssetSyncPayload.ACTION)
        put("version", WatchNoteAssetSyncPayload.VERSION)
        put("storageKey", storageKey)
        put("sha256", sha256)
        put("chunkIndex", index)
        put("chunkCount", count)
        put("data", Base64.getEncoder().encodeToString(data))
    }
}
