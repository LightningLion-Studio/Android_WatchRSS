package com.lightningstudio.watchrss.data.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.lightningstudio.watchrss.data.db.WatchRssDatabase
import com.lightningstudio.watchrss.data.db.RssItemEntity
import com.lightningstudio.watchrss.data.db.RssChannelEntity
import com.lightningstudio.watchrss.data.db.SavedEntryEntity
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.SyncedSavedArticle
import com.lightningstudio.watchrss.data.rss.SyncedSavedArticleMergeStats
import com.lightningstudio.watchrss.phoneconnection.bluetooth.LibrarySyncPayload
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Synthetic lazy data only. No account, disk, network, or application DB access. */
@RunWith(AndroidJUnit4::class)
class WatchCloudLibraryStreamingTest {
    @Test fun restoresMoreThanHeapWithoutCollectingTheLibrary() = runBlocking {
        val row = article(excerpt = "x".repeat(512 * 1024), content = "chapter正文".repeat(16 * 1024))
        val encoded = LibrarySyncPayload.buildResponse("fixture", listOf(row), 0)
            .getJSONArray("articles").getJSONObject(0).toString().toByteArray()
        val repetitions = 320 // JSON exceeds 160 MiB; target watch heap is 128 MiB.
        val stream = repeatedRows(encoded, repetitions)
        var count = 0
        var maximumLiveRows = 0
        val repository = object : RssRepository by FakeRssRepository() {
            override suspend fun mergeCloudArticleBatch(articles: List<SyncedSavedArticle>,
                remoteDeviceId: String, localDeviceId: String): SyncedSavedArticleMergeStats =
                mergeSyncedSavedArticles(articles, remoteDeviceId, localDeviceId, false)
            override suspend fun mergeSyncedSavedArticles(
                articles: List<SyncedSavedArticle>, remoteDeviceId: String,
                localDeviceId: String, authoritative: Boolean
            ): SyncedSavedArticleMergeStats {
                maximumLiveRows = maxOf(maximumLiveRows, articles.size)
                assertEquals(row, articles.single())
                count++
                return SyncedSavedArticleMergeStats(articles.size, articles.size)
            }
        }
        assertEquals(repetitions, WatchCloudLibraryAdapter(repository, "watch-fixture").restore(stream, "phone-fixture"))
        assertEquals(repetitions, count)
        assertEquals(1, maximumLiveRows)
        assertTrue(encoded.size.toLong() * repetitions > 128L * 1024 * 1024)
    }

    @Test fun exportsLazilyAndDeterministicallyBeyondHeap() = runBlocking {
        val row = article(excerpt = "x".repeat(512 * 1024), content = "chapter正文".repeat(16 * 1024))
        var streamed = 0
        val repository = object : RssRepository by FakeRssRepository() {
            override suspend fun mergeCloudArticleBatch(articles: List<SyncedSavedArticle>,
                remoteDeviceId: String, localDeviceId: String): SyncedSavedArticleMergeStats =
                mergeSyncedSavedArticles(articles, remoteDeviceId, localDeviceId, false)
            override suspend fun exportSyncedSavedArticles(deviceId: String): List<SyncedSavedArticle> =
                error("Whole-library export must never be used")
            override suspend fun streamCloudArticles(
                deviceId: String, stateOnly: Boolean, emit: suspend (SyncedSavedArticle) -> Unit
            ) {
                repeat(320) { emit(row); streamed++ }
            }
        }
        val adapter = WatchCloudLibraryAdapter(repository, "watch-fixture")
        var bytes = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = object : OutputStream() {
            override fun write(value: Int) { bytes++; digest.update(value.toByte()) }
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                bytes += length; digest.update(buffer, offset, length)
            }
        }
        adapter.exportTo(sink)
        val first = digest.digest()
        assertTrue(bytes > 128L * 1024 * 1024)
        adapter.exportTo(sink)
        assertArrayEquals(first, digest.digest())
        assertEquals(640, streamed)
    }

    @Test fun stateRoundTripPreservesTombstonesAndProgressAndOmitsBody() = runBlocking {
        val rows = listOf(article("one").copy(contentText = "private chapter", favoriteSaved = true,
            favoriteChangedAt = 100, readingPositionBytes = 12345,
            readingPositionContentHash = "body-hash", readingPositionChangedAt = 101,
            readingProgress = .45f), article("deleted").copy(deleted = true, deletedAt = 200,
            independentSaved = false, favoriteChangedAt = 200))
        val exported = object : RssRepository by FakeRssRepository() {
            override suspend fun mergeCloudArticleBatch(articles: List<SyncedSavedArticle>,
                remoteDeviceId: String, localDeviceId: String): SyncedSavedArticleMergeStats =
                mergeSyncedSavedArticles(articles, remoteDeviceId, localDeviceId, false)
            override suspend fun streamCloudArticles(deviceId: String, stateOnly: Boolean,
                emit: suspend (SyncedSavedArticle) -> Unit) { rows.forEach { emit(it) } }
        }
        val buffer = ByteArrayOutputStream()
        WatchCloudLibraryAdapter(exported, "fixture").exportStateTo(buffer)
        assertFalse(buffer.toString("UTF-8").contains("private chapter"))
        val received = mutableListOf<SyncedSavedArticle>()
        val imported = object : RssRepository by FakeRssRepository() {
            override suspend fun mergeCloudArticleBatch(articles: List<SyncedSavedArticle>,
                remoteDeviceId: String, localDeviceId: String): SyncedSavedArticleMergeStats =
                mergeSyncedSavedArticles(articles, remoteDeviceId, localDeviceId, false)
            override suspend fun mergeSyncedSavedArticles(articles: List<SyncedSavedArticle>,
                remoteDeviceId: String, localDeviceId: String, authoritative: Boolean): SyncedSavedArticleMergeStats {
                received.addAll(articles)
                return SyncedSavedArticleMergeStats(articles.size, articles.size)
            }
        }
        WatchCloudLibraryAdapter(imported, "watch-fixture").restoreState(buffer.toByteArray().inputStream(), "fixture")
        assertEquals(rows.map { it.copy(contentText = "") }, received)
    }

    @Test fun truncatedStreamFailsWithoutPretendingItCompleted() = runBlocking {
        var count = 0
        val row = LibrarySyncPayload.buildResponse("fixture", listOf(article()), 0)
            .getJSONArray("articles").getJSONObject(0).toString()
        val repository = object : RssRepository by FakeRssRepository() {
            override suspend fun mergeCloudArticleBatch(articles: List<SyncedSavedArticle>,
                remoteDeviceId: String, localDeviceId: String): SyncedSavedArticleMergeStats =
                mergeSyncedSavedArticles(articles, remoteDeviceId, localDeviceId, false)
            override suspend fun mergeSyncedSavedArticles(articles: List<SyncedSavedArticle>,
                remoteDeviceId: String, localDeviceId: String, authoritative: Boolean): SyncedSavedArticleMergeStats {
                count += articles.size
                return SyncedSavedArticleMergeStats(articles.size, articles.size)
            }
        }
        try {
            WatchCloudLibraryAdapter(repository, "fixture").restore(
                ("{\"articles\":[" + List(16) { row }.joinToString(",") + ",{\"articleId\":").byteInputStream(), "fixture")
            fail("Truncated JSON cannot acknowledge completion")
        } catch (_: java.io.IOException) { }
        assertEquals(16, count) // One bounded committed batch; incomplete tail is discarded.
    }

    @Test fun privateChapterRoundTripPreservesCompressedBodyAndBookIdentity() = runBlocking {
        val row = article("chapter", content = "正文 \n".repeat(1024)).copy(
            url = "https://watchrss.local/import-epub/book/chapter-1",
            rssSourceUrl = "https://watchrss.local/import-epub/book", independentSaved = false)
        val producer = object : RssRepository by FakeRssRepository() {
            override suspend fun mergeCloudArticleBatch(articles: List<SyncedSavedArticle>,
                remoteDeviceId: String, localDeviceId: String): SyncedSavedArticleMergeStats =
                mergeSyncedSavedArticles(articles, remoteDeviceId, localDeviceId, false)
            override suspend fun streamCloudArticles(deviceId: String, stateOnly: Boolean,
                emit: suspend (SyncedSavedArticle) -> Unit) { emit(row) }
        }
        val sink = ByteArrayOutputStream()
        WatchCloudLibraryAdapter(producer, "fixture").exportTo(sink)
        var received: SyncedSavedArticle? = null
        val consumer = object : RssRepository by FakeRssRepository() {
            override suspend fun mergeCloudArticleBatch(articles: List<SyncedSavedArticle>,
                remoteDeviceId: String, localDeviceId: String): SyncedSavedArticleMergeStats =
                mergeSyncedSavedArticles(articles, remoteDeviceId, localDeviceId, false)
            override suspend fun mergeSyncedSavedArticles(articles: List<SyncedSavedArticle>,
                remoteDeviceId: String, localDeviceId: String, authoritative: Boolean): SyncedSavedArticleMergeStats {
                received = articles.single()
                return SyncedSavedArticleMergeStats(1, 1)
            }
        }
        WatchCloudLibraryAdapter(consumer, "watch-fixture").restore(sink.toByteArray().inputStream(), "fixture")
        assertEquals(row, received)
    }

    @Test fun productionDaoPagesOnlyIdsAndOmitsBodyFromMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WatchRssDatabase::class.java).build()
        try {
            val channel = db.rssChannelDao().insertChannel(RssChannelEntity(url = "https://fixture.invalid/rss",
                title = "Fixture", description = null, imageUrl = null, lastFetchedAt = null,
                createdAt = 1, sortOrder = 1, isPinned = false))
            val body = "synthetic body".repeat(8192)
            val prototype = RssItemEntity(channelId = channel, title = "Chapter", description = null,
                content = body, originalContent = body, link = "https://fixture.invalid/chapter",
                guid = null, pubDate = null, imageUrl = null, audioUrl = null, videoUrl = null,
                summary = "summary", previewImageUrl = null, isRead = true, isLiked = false,
                readingProgress = .25f, dedupKey = "a", fetchedAt = 1,
                contentSizeBytes = body.length.toLong())
            val ids = db.rssItemDao().insertItems(listOf(prototype, prototype.copy(dedupKey = "b"),
                prototype.copy(dedupKey = "c")))
            assertEquals(ids.take(2), db.rssItemDao().getCloudExportItemIds(0, 2))
            assertEquals(ids.drop(2), db.rssItemDao().getCloudExportItemIds(ids[1], 2))
            val metadata = requireNotNull(db.rssItemDao().getCloudExportMetadata(ids.first()))
            assertNull(metadata.content)
            assertNull(metadata.originalContent)
            assertEquals(.25f, metadata.readingProgress)
            assertEquals(body, db.rssItemDao().getItem(ids.first())?.content)
            db.savedEntryDao().insert(SavedEntryEntity(itemId = ids[0], saveType = "FAVORITE", createdAt = 10, sortOrder = 10))
            db.savedEntryDao().insert(SavedEntryEntity(itemId = ids[1], saveType = "FAVORITE", createdAt = 20, sortOrder = 20))
            assertEquals(ids[1], db.savedEntryDao().getCloudExportSavedEntry(prototype.link!!, "FAVORITE")?.itemId)
        } finally { db.close() }
    }

    @Test fun countAndByteLimitsBoundCloudTransactions() = runBlocking {
        val rows = List(35) { article("small-$it") } +
            List(5) { article("medium-$it", excerpt = "m".repeat(100 * 1024)) } +
            listOf(article("large", excerpt = "L".repeat(300 * 1024)))
        val payload = LibrarySyncPayload.buildResponse("fixture", rows, 0).toString().byteInputStream()
        val sizes = mutableListOf<Int>()
        var received = 0
        val repository = object : RssRepository by FakeRssRepository() {
            override suspend fun mergeCloudArticleBatch(articles: List<SyncedSavedArticle>,
                remoteDeviceId: String, localDeviceId: String): SyncedSavedArticleMergeStats {
                assertTrue(articles.size <= CLOUD_BATCH_ROWS)
                assertTrue(articles.size == 1 || articles.sumOf(::cloudArticleBatchBytes) <= CLOUD_BATCH_BYTES)
                sizes.add(articles.size)
                received += articles.size
                return SyncedSavedArticleMergeStats(articles.size, articles.size)
            }
        }
        WatchCloudLibraryAdapter(repository, "fixture").restore(payload, "fixture")
        assertEquals(rows.size, received)
        assertTrue(sizes.size < rows.size / 2)
        assertTrue(sizes.any { it == 16 })
        assertEquals(1, sizes.last())
    }

    private fun article(id: String = "fixture", excerpt: String = "summary", content: String = "") = SyncedSavedArticle(
        articleId = id, sourceDeviceId = "fixture", url = "https://fixture.invalid/$id",
        title = "Chapter", siteName = "Book", excerpt = excerpt, contentHtml = null,
        contentText = content, imageUrl = null, contentHash = "body-hash", importedAt = 1,
        updatedAt = 2, independentSaved = false, rssSourceUrl = "https://fixture.invalid/book",
        rssSourceTitle = "Book", favoriteSaved = false, favoriteChangedAt = 0,
        favoriteSortOrder = 0, watchLaterSaved = false, watchLaterChangedAt = 0,
        watchLaterSortOrder = 0, deleted = false, deletedAt = 0
    )

    private fun repeatedRows(row: ByteArray, count: Int): InputStream = object : InputStream() {
        private val prefix = "{\"articles\":[".toByteArray()
        private val suffix = "]}".toByteArray()
        private var section = -1
        private var offset = 0
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 255
        }
        override fun read(buffer: ByteArray, start: Int, length: Int): Int {
            if (length == 0) return 0
            while (true) {
                val chunk = when {
                    section == -1 -> prefix
                    section >= count * 2 - 1 -> suffix
                    section % 2 == 0 -> row
                    else -> byteArrayOf(','.code.toByte())
                }
                if (offset == chunk.size) {
                    if (section >= count * 2 - 1) return -1
                    section++; offset = 0; continue
                }
                val amount = minOf(length, chunk.size - offset)
                chunk.copyInto(buffer, start, offset, offset + amount)
                offset += amount
                return amount
            }
        }
    }
}
