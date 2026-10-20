package com.lightningstudio.watchrss.data.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 180MiB plaintext exceeds the physical watch's 128MiB heap. Only encrypted chunks touch private cache. */
@RunWith(AndroidJUnit4::class)
class WatchCloudCodecLargeStreamTest {
    @Test fun exportAndRestoreBeyondHeapRemainChunkBounded() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".bletest"))
        val directory = File(context.cacheDir, "cloud-stream-fixture-${System.nanoTime()}").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putLong("maxHeapBytes", Runtime.getRuntime().maxMemory())
            putLong("fixturePlaintextBytes", 180L * 1024 * 1024)
        })
        val key = ByteArray(32) { (it + 11).toByte() }
        try {
            val expected = MessageDigest.getInstance("SHA-256")
            val block = ByteArray(8192) { (it % 113).toByte() }
            val repetitions = 180 * 1024 * 1024 / block.size
            val codec = WatchCloudCodec()
            val objects = codec.prepareStreamingObjects(key,
                listOf(WatchCloudStreamingObject("large") { output ->
                    repeat(repetitions) { expected.update(block); output.write(block) }
                }), storeChunk = { hash, bytes -> File(directory, hash).writeBytes(bytes) })
            assertEquals(180L * 1024 * 1024, objects.single().originalBytes)
            assertEquals(45, objects.single().chunks.size)
            val actual = MessageDigest.getInstance("SHA-256")
            val bytes = codec.withRestoredObject(key, objects.single(), { hash -> File(directory, hash).readBytes() }) { input ->
                val buffer = ByteArray(8192); var total = 0L
                while (true) { val n = input.read(buffer); if (n < 0) break; actual.update(buffer, 0, n); total += n }; total
            }
            assertEquals(180L * 1024 * 1024, bytes)
            assertArrayEquals(expected.digest(), actual.digest())
        } finally { key.fill(0); directory.deleteRecursively() }
    }
}
