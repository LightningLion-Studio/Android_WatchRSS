package com.lightningstudio.watchrss.port.layout3

import com.lightningstudio.watchrss.data.cloud.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

class WatchLayout3GoldenTest {
    private val directory: File get() = listOf(File("src/test/java/com/lightningstudio/watchrss/port/layout3"),
        File("app/src/test/java/com/lightningstudio/watchrss/port/layout3")).first { it.isDirectory }
    private val fixture: JSONObject get() = JSONObject(File(directory, "podjs-golden.json").readText())
    private val key = ByteArray(32) { 41 }
    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
    private fun chunks(f: JSONObject): Map<String, ByteArray> = f.getJSONObject("chunks").let { v -> v.keys().asSequence().associateWith { decode(v.getString(it)) } }
    private fun manifest(codec: WatchLayout3Codec, f: JSONObject = fixture): WatchLayout3Manifest =
        codec.decryptManifest(key, f.getString("snapshotId"), decode(f.getString("encryptedManifestBase64")))
    private fun rejected(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: Exception) { rejected = true }
        assertTrue("corrupt or unsupported input must be rejected", rejected)
    }
    private class DeterministicRandom : SecureRandom() {
        private var counter = 0
        override fun nextBytes(bytes: ByteArray) { bytes.indices.forEach { bytes[it] = (++counter % 251).toByte() } }
    }
    private fun verifyInPodjs(snapshot: WatchLayout3Snapshot, all: Map<String, ByteArray>, expectFuture: Boolean) {
        val temp = Files.createTempDirectory("layout3-golden-").toFile()
        try {
            val outgoing = JSONObject().put("snapshotId", snapshot.manifest.snapshotId)
                .put("encryptedManifestBase64", Base64.getEncoder().encodeToString(snapshot.encryptedManifest))
                .put("chunks", JSONObject().apply { all.forEach { (h, b) -> put(h, Base64.getEncoder().encodeToString(b)) } })
                .put("expectFuture", expectFuture)
            val file = File(temp, "kotlin.json").apply { writeText(outgoing.toString()) }
            val log = File(temp, "bun.log")
            val process = ProcessBuilder("bun", File(directory, "podjs-golden.ts").absolutePath, "verify", file.absolutePath)
                .redirectErrorStream(true).redirectOutput(log).start()
            if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("PodJS verifier timed out") }
            assertEquals(log.readText(), 0, process.exitValue())
            assertTrue(log.readText().contains("PodJS decoded Kotlin CM3/CC2"))
        } finally { temp.deleteRecursively() }
    }

    @Test fun podjsDeterministicFixtureIsReproducible() {
        val temp = Files.createTempFile("layout3-regenerate-", ".json").toFile()
        try {
            val log = File(temp.parentFile, temp.name + ".log")
            try {
                val process = ProcessBuilder("bun", File(directory, "podjs-golden.ts").absolutePath, "generate", temp.absolutePath)
                    .redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("generator timed out") }
                assertEquals(log.readText(), 0, process.exitValue())
                assertArrayEquals(File(directory, "podjs-golden.json").readBytes(), temp.readBytes())
            } finally { log.delete() }
        } finally { temp.delete() }
    }

    @Test fun kotlinDecodesActualPodjsCm3Cc2BookAndMicroArchive() {
        val f = fixture; val c = WatchLayout3Codec(); val m = manifest(c, f); val chunks = chunks(f)
        assertEquals(2, m.json.getLong("deviceSequence"))
        assertTrue(m.raw.source.contains("\"futureRoot\":9007199254740993123456789"))
        val bookName = f.getJSONObject("privateBook").getString("packagePath")
        val bookBytes = c.restore(key, m, bookName, chunks::getValue)
        assertEquals(f.getString("packageUtf8"), bookBytes.toString(Charsets.UTF_8))
        val book = WatchLayout3Books.decode(bookBytes, f.getJSONObject("privateBook"))
        assertEquals(f.getJSONObject("privateBook").getString("bookId"), book.bookId)
        assertEquals((0 until 2).map { f.getJSONArray("chapterHashes").getString(it) }, book.hashes)
        val micro = c.restore(key, m, f.getString("microName"), chunks::getValue)
        val article = WatchLayout3Archive.decodeMicro(micro, "layout3-article")
        assertEquals("离线正文😀", article.text)
        assertEquals("<p>离线正文😀</p>", article.html)
        assertTrue(article.metadata.source.contains("9007199254740993123456789"))
        assertEquals(2, WatchLayout3Overlay.decode(c.restore(key, m, WatchLayout3Format.OVERLAY_NAME, chunks::getValue)).size)
    }

    @Test fun kotlinUnwrapsMigrationObjectRootAndResolvesEntryReference() {
        val f = fixture; val c = WatchLayout3Codec(); val m = manifest(c, f); val data = chunks(f)
        assertArrayEquals(decode(f.getString("microBase64")), c.restore(key, m, "wrapped.wrss", data::getValue))
        assertEquals("离线正文😀", c.restore(key, m, "reader-ref.txt", data::getValue).toString(Charsets.UTF_8))
        val corrupt = WatchLayout3Manifest(m.raw.patch(listOf("objects"), m.objects.joinToString(",", "[", "]") {
            if (it.name == "reader-ref.txt") it.raw.patch(listOf("entryRef", "entryPath"), JSONObject.quote("../unsafe")).source else it.raw.source
        }))
        rejected { corrupt.validate() }
    }

    @Test fun kotlinReencryptsFreshObjectsAndPodjsDecodesThem() {
        val f = fixture
        val privateBook = f.getJSONObject("privateBook")
        val article = WatchLayout3Archive.decodeMicro(decode(f.getString("microBase64")), "layout3-article")
        val reencoded = WatchLayout3Archive.encodeMicro(article)
        assertEquals(article.metadata.raw(listOf("futureExact")), WatchLayout3Archive.decodeMicro(reencoded, "layout3-article").metadata.raw(listOf("futureExact")))
        val c = WatchLayout3Codec(DeterministicRandom())
        val snapshot = c.create(key, 1, "kotlin-fixture", 1,
            listOf(WatchLayout3LogicalObject(privateBook.getString("packagePath"), f.getString("packageUtf8").toByteArray(), privateBook = privateBook),
                WatchLayout3LogicalObject(f.getString("microName"), reencoded, compress = false),
                WatchLayout3LogicalObject(WatchLayout3Format.OVERLAY_NAME, decode(f.getString("overlayBase64")))), f.getJSONObject("settings"),
            snapshotId = "kotlin-layout3", createdAt = 123)
        assertEquals("WRSSCM3", String(snapshot.encryptedManifest, 0, 7))
        verifyInPodjs(snapshot, snapshot.newChunks, false)
    }

    @Test fun kotlinCarriesUnknownObjectsAndNumbersWithoutReserialization() {
        val f = fixture; val c = WatchLayout3Codec(); val m = manifest(c, f)
        val outgoing = c.create(key, 1, "kotlin-fixture", 3, emptyList(), f.getJSONObject("settings"), previous = m,
            snapshotId = "kotlin-carried", createdAt = 200)
        assertTrue(outgoing.newChunks.isEmpty())
        assertEquals(m.objects.map { it.raw.source }, outgoing.manifest.objects.map { it.raw.source })
        assertEquals(m.raw.raw(listOf("futureRoot")), outgoing.manifest.raw.raw(listOf("futureRoot")))
        verifyInPodjs(outgoing, chunks(f), true)
    }

    @Test fun accountRootAndVersionPreventCrossScopeChunkReuse() {
        val f = fixture; val c = WatchLayout3Codec(); val m = manifest(c, f)
        rejected { c.create(ByteArray(32) { 42 }, 1, "watch", 1, emptyList(), f.getJSONObject("settings"), previous = m) }
        rejected { c.create(key, 2, "watch", 1, emptyList(), f.getJSONObject("settings"), previous = m) }
        val otherGeneration = JSONObject(f.getJSONObject("settings").toString()).put("contentGeneration", "44444444-4444-4444-8444-444444444444")
        rejected { c.create(key, 1, "watch", 1, emptyList(), otherGeneration, previous = m) }
        rejected { WatchLayout3Codec().create(key, 1, "watch", 1, emptyList(), f.getJSONObject("settings"), previous = m) }
    }

    @Test fun authenticatedUnknownRequiredFeaturesPreventProjectionAndPublication() {
        val c = WatchLayout3Codec(); val f = fixture; val m = manifest(c, f)
        val unknown = WatchLayout3Manifest(m.raw.patch(listOf("features"), "{\"incompat\":[\"future.required.v1\"],\"roCompat\":[]}"))
        rejected { unknown.validate() }
        val readOnly = WatchLayout3Manifest(m.raw.patch(listOf("features"), "{\"incompat\":[\"r48.private-overlay.v1\"],\"roCompat\":[\"future.optional.v1\"]}"))
        readOnly.validate(); assertFalse(readOnly.access.writable)
        val opaque = WatchLayout3Object(WatchLayout3RawDocument.parse("{\"name\":\"future\",\"features\":{\"incompat\":[\"future.object.v1\"],\"roCompat\":[]}}"))
        assertFalse(opaque.access.readable)
    }

    @Test fun cipherTamperingWrongSnapshotAndDecompressionBoundAreRejected() {
        val f = fixture; val c = WatchLayout3Codec(); val bytes = decode(f.getString("encryptedManifestBase64"))
        rejected { c.decryptManifest(key, "another-id", bytes) }
        rejected { c.decryptManifest(key, f.getString("snapshotId"), bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }) }
        val m = manifest(c, f); val data = chunks(f); val name = f.getJSONObject("privateBook").getString("packagePath")
        val part = m.objects.single { it.name == name }.chunks.first()
        rejected { c.restore(key, m, name) { hash -> data.getValue(hash).copyOf().also { if (hash == part.ciphertextSha256) it[it.lastIndex] = 0 } } }
        val shrunk = WatchLayout3Manifest(m.raw.patch(listOf("objects"), m.objects.joinToString(",", "[", "]") {
            if (it.name == name) it.raw.patch(listOf("originalBytes"), "1").source else it.raw.source
        }))
        rejected { c.restore(key, shrunk, name, data::getValue) }
    }

    @Test fun rawParserRejectsDuplicateKeysInvalidUtf8AndRoundedIntegers() {
        rejected { WatchLayout3RawDocument.parse("{\"a\":1,\"a\":2}") }
        rejected { WatchLayout3RawDocument.parse("{\"x\":\"\\ud800\"}") }
        rejected { WatchLayout3RawDocument.parse(byteArrayOf(123, 34, 120, 34, 58, 34, 0xc0.toByte(), 34, 125)) }
        listOf("9007199254740991.1", "1e3", "-1", "9007199254740992").forEach { token ->
            rejected { WatchLayout3Format.integer(WatchLayout3RawDocument.parse("{\"x\":$token}"), "x") }
        }
    }

    @Test fun bookPackagesRejectTruncationWrongIdentityAndReorderedChapters() {
        val f = fixture; val text = f.getString("packageUtf8"); val expected = f.getJSONObject("privateBook")
        listOf(text.dropLast(1), "\ufeff$text", text.replace("\n", "\r\n"), text.replace("\"chapterIndex\":1", "\"chapterIndex\":3"),
            text.replace("甲", "改"), text.replace("https://watchrss.local", "https://public.example")).forEach { bad ->
            rejected { WatchLayout3Books.decode(bad.toByteArray(), expected) }
        }
        val book = WatchLayout3Books.decode(text.toByteArray(), expected)
        assertEquals((0 until 2).map { f.getJSONArray("articleIds").getString(it) }, (1..2).map { WatchLayout3Books.articleId(book.bookId, it) })
        assertEquals(text, WatchLayout3Books.encode(book.header, book.chapters).toString(Charsets.UTF_8))
    }

    @Test fun independentReadingAndSaveClocksMatchPodjsMerge() {
        val f = fixture
        val merged = WatchLayout3Books.mergeReading(WatchLayout3RawDocument.parse(f.getJSONObject("reading").toString()),
            WatchLayout3RawDocument.parse(f.getJSONObject("saveUpdate").toString()))
        assertEquals(WatchCloudRecordCodec.canonical(f.getJSONObject("mergedReading")), WatchCloudRecordCodec.canonical(merged.json()))
        assertEquals("reader", merged.json().getString("sourceDeviceId"))
        assertEquals("unsave-owner", merged.json().getString("favoriteSourceDeviceId"))
    }

    @Test fun bookDeletionUnionIsPermanentAndNativeProjectionHasCanonicalIds() {
        val f = fixture; val s = f.getJSONObject("bookState")
        val local = WatchLayout3RawDocument.parse(s.toString())
        val deleted = WatchLayout3RawDocument.parse(JSONObject(s.toString()).put("deletedChapters", JSONArray().put(2)).put("deletionClock", 300).put("deletedAt", 300).toString())
        val merged = WatchLayout3Books.mergeState(deleted, local)
        assertEquals("[2]", merged.json().getJSONArray("deletedChapters").toString())
        val rows = WatchLayout3Projection.bookRows(f.getString("packageUtf8").toByteArray(), f.getJSONObject("privateBook"), merged,
            listOf(WatchLayout3RawDocument.parse(f.getJSONObject("reading").toString())))
        assertEquals(2, rows.size)
        assertEquals(f.getJSONArray("articleIds").getString(0), rows[0].articleId)
        assertEquals(4L, rows[0].readingPositionBytes)
        assertFalse(rows[0].deleted); assertTrue(rows[1].deleted)
    }

    @Test fun overlayTombstonesMissingBodyAndAllHeadConflictsKeepBackingSafe() {
        val f = fixture; val c = WatchLayout3Codec(); val m = manifest(c, f); val data = chunks(f)
        val payload = m.objects.associate { it.name to c.restore(key, m, it.name, data::getValue) }
        val set = WatchLayout3OverlayReadSet()
        assertEquals(0, set.observe(m, payload))
        assertTrue(set.allow("article", "layout3-article", m.snapshotId, f.getString("microName")))
        assertFalse(set.allow("article", "layout3-article", "backing"))
        val deletion = WatchLayout3OverlayEntry("article", "layout3-article", null, 100, "phone-fixture")
        assertEquals(0, set.observe(m, mapOf(WatchLayout3Format.OVERLAY_NAME to WatchLayout3Overlay.encode(listOf(deletion)))))
        assertFalse(set.allow("article", "layout3-article", "backing"))
        val missing = WatchLayout3OverlayReadSet()
        assertEquals(1, missing.observe(m, payload - f.getString("microName")))
        assertTrue(missing.allow("article", "layout3-article", "backing"))
        val conflictingManifest = WatchLayout3Manifest(m.raw.patch(listOf("objects"), m.objects.joinToString(",", "[", "]") {
            if (it.name == f.getString("microName")) it.raw.patch(listOf("contentSha256"), JSONObject.quote("f".repeat(64))).source else it.raw.source
        }))
        val conflict = WatchLayout3OverlayReadSet(); conflict.observe(m, payload)
        assertTrue(conflict.observe(conflictingManifest, payload) > 0)
        assertTrue(conflict.allow("article", "layout3-article", "backing"))
        assertFalse(conflict.allow("article", "layout3-article", m.snapshotId, f.getString("microName")))
    }

    @Test fun overlayRejectsDuplicateUnsortedUnsafeAndFractionalClocks() {
        val valid = decode(fixture.getString("overlayBase64"))
        val root = JSONObject(valid.toString(Charsets.UTF_8)); val entries = root.getJSONArray("entries")
        listOf(JSONObject(root.toString()).put("future", 1),
            JSONObject(root.toString()).put("entries", JSONArray().put(entries.get(0)).put(entries.get(0))),
            JSONObject(root.toString()).put("entries", JSONArray().put(entries.get(1)).put(entries.get(0))),
            JSONObject(root.toString()).put("entries", JSONArray().put(JSONObject(entries.getJSONObject(0).toString()).put("object", "../x")))).forEach {
            rejected { WatchLayout3Overlay.decode(it.toString().toByteArray()) }
        }
        rejected { WatchLayout3Overlay.decode(valid.toString(Charsets.UTF_8).replace("\"changedAt\":100", "\"changedAt\":9007199254740991.1").toByteArray()) }
    }

    @Test fun wrssRejectsMissingCentralDirectoryAndCorruptCrc() {
        val bytes = decode(fixture.getString("microBase64"))
        rejected { WatchLayout3Archive.decodeMicro(bytes.copyOf(bytes.size - 22), "layout3-article") }
        rejected { WatchLayout3Archive.decodeMicro(bytes.copyOf().also { it[50] = (it[50].toInt() xor 1).toByte() }, "layout3-article") }
    }

    @Test fun bookAndReadingRecordsDecodeBothDirectionsThroughPodjs() {
        val f = fixture
        val rows = f.getJSONArray("records")
        val encoded = JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val id = row.getString("recordId")
            val doc = WatchLayout3BookRecords.decode(key, key, "offline-user", id, row.getJSONObject("envelope"))
            assertEquals(if (i == 0) "book" else "book-reading", doc.kind)
            assertEquals(id, WatchLayout3BookRecords.id(key, "offline-user", doc))
            encoded.put(JSONObject().put("recordId", id).put("envelope", WatchLayout3BookRecords.encode(key, "offline-user", id, 1, doc, ByteArray(12) { (it + 1).toByte() })))
            rejected { WatchLayout3BookRecords.decode(key, key, "wrong-user", id, row.getJSONObject("envelope")) }
        }
        val temp = Files.createTempDirectory("layout3-records-").toFile()
        try {
            val file = File(temp, "records.json").apply { writeText(JSONObject().put("records", encoded).toString()) }
            val log = File(temp, "verify.log")
            val p = ProcessBuilder("bun", File(directory, "podjs-golden.ts").absolutePath, "verify", file.absolutePath)
                .redirectErrorStream(true).redirectOutput(log).start()
            if (!p.waitFor(45, TimeUnit.SECONDS)) { p.destroyForcibly(); fail("record verifier timed out") }
            assertEquals(log.readText(), 0, p.exitValue())
            assertTrue(log.readText().contains("PodJS decoded Kotlin book/book-reading records"))
        } finally { temp.deleteRecursively() }
    }

    @Test fun contentDefinedChunkBoundariesMatchPodjs() {
        val bytes = ByteArray(2200000) { (it % 251).toByte() }
        val expected = fixture.getJSONArray("contentChunkSizes").let { a -> (0 until a.length()).map { a.getInt(it) } }
        assertEquals(expected, WatchLayout3Codec.contentChunks(bytes).map { it.size })
    }
}
