package com.lightningstudio.watchrss.data.cloud

import com.lightningstudio.watchrss.data.reader.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WatchCloudReaderArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private val font = "fixture-font".toByteArray()
    private val background = "fixture-background".toByteArray()
    private val variant = "fixture-watch-variant".toByteArray()
    private val bytes get() = mapOf("font" to font, "background" to background, "variant" to variant)

    private fun snapshot(version: Long = 1) = ReaderPresetSnapshot(
        listOf(ReaderPresetEntity("cloud-reader", "云预设", "{}", version, "fixture", false)),
        listOf(ReaderFontAssetEntity("font", WatchCloudCodec.sha256(font), "字体", "Fixture", "font.bin", "font/ttf", font.size.toLong(), 1, "{}", version, "fixture", false)),
        listOf(ReaderBackgroundAssetEntity("background", WatchCloudCodec.sha256(background), "背景", "image", "image/png", "background.bin", background.size.toLong(), 0, 1, 1, null,
            """{"watch":{"fileName":"watch.bin","sha256":"${WatchCloudCodec.sha256(variant)}","byteCount":${variant.size}}}""", version, "fixture", false)),
        listOf(ReaderDeletionEntity("preset", "deleted-preset", 1, "fixture"))
    )

    private fun archive(snapshot: ReaderPresetSnapshot = snapshot(), exportedAt: Long = 1): ByteArray = WatchCloudReaderArchive.encode(snapshot, { ref ->
        temporary.newFile().apply { writeBytes(bytes.getValue(ref.kind)) }
    }, exportedAt)

    @Test fun allReaderTypesRoundTripAndInstallWithHashChecks() {
        val expected = snapshot()
        assertEquals("reader-only", org.json.JSONObject(unzip(archive()).getValue("manifest.json").toString(Charsets.UTF_8)).getString("scope"))
        val staging = temporary.newFolder()
        val target = temporary.newFolder()
        WatchCloudReaderArchive.prepare(archive(), staging).use { prepared ->
            assertEquals(expected, prepared.snapshot)
            assertTrue(prepared.install(expected) { File(target, it.fileName) })
            assertArrayEquals(font, File(target, "font.bin").readBytes())
            assertArrayEquals(background, File(target, "background.bin").readBytes())
            assertArrayEquals(variant, File(target, "watch.bin").readBytes())
            assertFalse(prepared.install(expected) { File(target, it.fileName) })
        }
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }

    @Test fun corruptMissingAndUnreferencedResourcesFailBeforeInstallationAndCleanStaging() {
        val entries = unzip(archive())
        val resource = entries.keys.first { it.startsWith("reader/fonts/") }
        val staging = temporary.newFolder()
        listOf(entries - resource, entries + (resource to "corrupt".toByteArray()),
            entries + ("reader/fonts/${"a".repeat(64)}.bin" to font)).forEach { changed ->
            assertThrows(IllegalArgumentException::class.java) { WatchCloudReaderArchive.prepare(zip(changed), staging) }
            assertTrue(staging.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun pathsAndUnknownVersionsAreRejected() {
        val entries = unzip(archive())
        val staging = temporary.newFolder()
        assertThrows(IllegalArgumentException::class.java) { WatchCloudReaderArchive.prepare(zip(entries + ("../foreign.txt" to font)), staging) }
        val manifest = entries.getValue("manifest.json").toString(Charsets.UTF_8).replace("\"version\":3", "\"version\":99")
        assertThrows(IllegalArgumentException::class.java) { WatchCloudReaderArchive.prepare(zip(entries + ("manifest.json" to manifest.toByteArray())), staging) }
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }

    @Test fun olderRemoteDoesNotReplaceNewerLocalResourceWithSameFileName() {
        val old = snapshot(1)
        val newBytes = "newer-font".toByteArray()
        val newer = snapshot(2).copy(fonts = old.fonts.map { it.copy(sha256 = WatchCloudCodec.sha256(newBytes), byteCount = newBytes.size.toLong(), updatedAt = 2) })
        val effective = WatchCloudReaderArchive.mergedSnapshot(newer, old)
        assertEquals(newer.fonts, effective.fonts)
        val target = temporary.newFolder()
        File(target, "font.bin").writeBytes(newBytes)
        WatchCloudReaderArchive.prepare(archive(old), temporary.newFolder()).use { prepared ->
            prepared.install(effective) { File(target, it.fileName) }
            assertArrayEquals(newBytes, File(target, "font.bin").readBytes())
        }
    }

    @Test fun readerFingerprintIgnoresExportTimeButRetainsRealChangesAndExportFailsForMissingAsset() {
        val state = """{"articles":[]}""".toByteArray()
        assertEquals(watchCloudContentHash(state, null, 1, archive(exportedAt = 1)), watchCloudContentHash(state, null, 1, archive(exportedAt = 2000)))
        assertNotEquals(watchCloudContentHash(state, null, 1, archive()), watchCloudContentHash(state, null, 1, archive(snapshot(2))))
        assertThrows(IllegalArgumentException::class.java) { WatchCloudReaderArchive.encode(snapshot(), { File(temporary.root, "missing") }) }
    }

    @Test fun exportsWrssInteroperabilityFixtureForCompanionDecoder() {
        val output = File("build/test-fixtures/watch-reader-cloud.wrss").apply { parentFile!!.mkdirs() }
        output.writeBytes(archive())
        assertTrue(output.length() > 0)
    }

    @Test fun readsArchiveReencodedByTheActualCompanionBackupCodec() {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/cloud/phone-reader-cloud.wrss")).use { it.readBytes() }
        val staging = temporary.newFolder()
        WatchCloudReaderArchive.prepare(bytes, staging).use { prepared ->
            assertEquals(snapshot(), prepared.snapshot)
            val target = temporary.newFolder()
            prepared.install(requireNotNull(prepared.snapshot)) { File(target, it.fileName) }
            assertArrayEquals(font, File(target, "font.bin").readBytes())
            assertArrayEquals(variant, File(target, "watch.bin").readBytes())
        }
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = ZipInputStream(bytes.inputStream()).use { zip ->
        buildMap { while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()); zip.closeEntry() } }
    }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
        output.toByteArray()
    }
}
