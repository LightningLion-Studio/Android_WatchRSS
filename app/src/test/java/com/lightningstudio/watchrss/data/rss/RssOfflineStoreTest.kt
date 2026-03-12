package com.lightningstudio.watchrss.data.rss

import com.lightningstudio.watchrss.data.db.OfflineMediaDao
import com.lightningstudio.watchrss.data.db.OfflineMediaEntity
import com.lightningstudio.watchrss.data.db.RssItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RssOfflineStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun downloadMediaForItem_writesFiles_and_insertsEntries() = runBlocking {
        val dao = FakeOfflineMediaDao()
        val downloader = FakeDownloadClient()
        val root = tempFolder.newFolder("rss-offline")
        val store = RssOfflineStore(root, dao, downloader)
        val item = buildItem(
            id = 7L,
            content = "<img src=\"https://example.com/a.jpg\" />" +
                "<video src=\"https://example.com/b.mp4\"></video>"
        )

        store.downloadMediaForItem(item)

        val entries = dao.getByItemId(item.id)
        assertEquals(2, entries.size)
        entries.forEach { entry ->
            assertNotNull(entry.localPath)
            assertTrue(File(entry.localPath!!).exists())
        }
        assertEquals(2, downloader.requests.size)
    }

    @Test
    fun downloadMediaForItem_skipsWhenAlreadyStored() = runBlocking {
        val dao = FakeOfflineMediaDao()
        val downloader = FakeDownloadClient()
        val root = tempFolder.newFolder("rss-offline-2")
        val store = RssOfflineStore(root, dao, downloader)
        val item = buildItem(
            id = 8L,
            content = "<img src=\"https://example.com/a.jpg\" />"
        )

        store.downloadMediaForItem(item)
        store.downloadMediaForItem(item)

        val entries = dao.getByItemId(item.id)
        assertEquals(1, entries.size)
        assertEquals(1, downloader.requests.size)
    }

    @Test
    fun deleteMediaForItem_removesFiles_and_entries() = runBlocking {
        val dao = FakeOfflineMediaDao()
        val downloader = FakeDownloadClient()
        val root = tempFolder.newFolder("rss-offline-3")
        val store = RssOfflineStore(root, dao, downloader)
        val item = buildItem(
            id = 9L,
            content = "<img src=\"https://example.com/a.jpg\" />"
        )

        store.downloadMediaForItem(item)
        val entries = dao.getByItemId(item.id)
        val path = entries.firstOrNull()?.localPath
        assertNotNull(path)
        assertTrue(File(path!!).exists())

        store.deleteMediaForItem(item.id)

        assertTrue(dao.getByItemId(item.id).isEmpty())
        assertFalse(File(path).exists())
    }

    @Test
    fun downloadMediaForItem_retriesWhenLocalPathMissing() = runBlocking {
        val dao = FakeOfflineMediaDao()
        val url = "https://example.com/a.jpg"
        val downloader = FakeDownloadClient(
            outcomes = mapOf(url to listOf(false, true))
        )
        val root = tempFolder.newFolder("rss-offline-4")
        val store = RssOfflineStore(root, dao, downloader)
        val item = buildItem(
            id = 10L,
            content = "<img src=\"$url\" />"
        )

        store.downloadMediaForItem(item)

        val first = dao.getByItemId(item.id)
        assertEquals(1, first.size)
        assertEquals(null, first.first().localPath)

        store.downloadMediaForItem(item)

        val second = dao.getByItemId(item.id)
        assertEquals(1, second.size)
        assertNotNull(second.first().localPath)
        assertEquals(2, downloader.requests.size)
    }

    private fun buildItem(id: Long, content: String?): RssItemEntity = RssItemEntity(
        id = id,
        channelId = 1L,
        title = "title",
        description = null,
        content = content,
        link = null,
        guid = null,
        pubDate = null,
        imageUrl = null,
        audioUrl = null,
        videoUrl = null,
        summary = null,
        previewImageUrl = null,
        isRead = false,
        isLiked = false,
        readingProgress = 0f,
        dedupKey = "dedup:$id",
        fetchedAt = 0L,
        contentSizeBytes = 0L
    )

    private class FakeDownloadClient(
        private val outcomes: Map<String, List<Boolean>> = emptyMap()
    ) : RssDownloadClient {
        val requests = mutableListOf<String>()
        private val counters = mutableMapOf<String, Int>()

        override fun downloadToFile(url: String, file: File): String? {
            requests.add(url)
            val index = counters.getOrDefault(url, 0)
            counters[url] = index + 1
            val allowWrite = outcomes[url]?.getOrNull(index) ?: true
            if (!allowWrite) return null
            file.parentFile?.mkdirs()
            file.writeText("data:$url")
            return file.absolutePath
        }
    }

    private class FakeOfflineMediaDao : OfflineMediaDao {
        private val entries = mutableListOf<OfflineMediaEntity>()
        private val flow = MutableStateFlow<List<OfflineMediaEntity>>(emptyList())

        override fun observeByItemId(itemId: Long): Flow<List<OfflineMediaEntity>> =
            flow.map { list -> list.filter { it.itemId == itemId } }

        override suspend fun getByItemId(itemId: Long): List<OfflineMediaEntity> =
            entries.filter { it.itemId == itemId }

        override suspend fun getByChannelId(channelId: Long): List<OfflineMediaEntity> =
            entries.toList()

        override suspend fun findByOrigin(itemId: Long, originUrl: String): OfflineMediaEntity? =
            entries.firstOrNull { it.itemId == itemId && it.originUrl == originUrl }

        override suspend fun insertAll(entries: List<OfflineMediaEntity>) {
            val nextId = (this.entries.maxOfOrNull { it.id } ?: 0L) + 1L
            var currentId = nextId
            entries.forEach { entry ->
                if (this.entries.none { it.itemId == entry.itemId && it.originUrl == entry.originUrl }) {
                    this.entries.add(entry.copy(id = currentId))
                    currentId += 1
                }
            }
            flow.value = this.entries.toList()
        }

        override suspend fun updateLocalPath(itemId: Long, originUrl: String, localPath: String?) {
            val index = entries.indexOfFirst { it.itemId == itemId && it.originUrl == originUrl }
            if (index >= 0) {
                entries[index] = entries[index].copy(localPath = localPath)
                flow.value = entries.toList()
            }
        }

        override suspend fun deleteByItemId(itemId: Long) {
            entries.removeAll { it.itemId == itemId }
            flow.value = entries.toList()
        }
    }
}
