package com.lightningstudio.watchrss.port.readerrelease

import android.net.Uri
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.data.db.*
import com.lightningstudio.watchrss.data.rss.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

class PublicRssProgressTest {
    @Test fun metadataRefreshKeepsNewerLocalProgressAndReadingClock() = runBlocking {
        withContext(Dispatchers.IO) {
            withUri {
                val setup = Setup()
                setup.repo.mergeSyncedSavedArticles(listOf(article(0f, 10)), "cloud-rss-inventory", "watch-fixture")
                val call = mockingDetails(setup.items).invocations.last { it.method.name == "updateSyncedArticle" }
                assertEquals("Updated title", call.arguments[1])
                assertEquals("Local complete body", call.arguments[3])
                assertEquals(.65f, call.arguments[16])
                assertEquals(650L, call.arguments[18])
                assertEquals("local-content", call.arguments[19])
                assertEquals(200L, call.arguments[20])
            }
        }
    }

    @Test fun newerExplicitReadingPositionCanAdvanceOrRewindLocalProgress() = runBlocking {
        withContext(Dispatchers.IO) {
            withUri {
                listOf(.8f, .2f).forEach { progress ->
                    val setup = Setup()
                    setup.repo.mergeSyncedSavedArticles(listOf(article(progress, 300)), "cloud-rss-inventory", "watch-fixture")
                    val call = mockingDetails(setup.items).invocations.last { it.method.name == "updateSyncedArticle" }
                    assertEquals(progress, call.arguments[16])
                    assertEquals(300L, call.arguments[20])
                    assertEquals("Local complete body", call.arguments[3])
                }
            }
        }
    }

    @Test fun saved36krFixtureCanPreviewAndConfirmThroughRealRepository() = runBlocking {
        withContext(Dispatchers.IO) {
            withUri {
                val setup = Setup()
                `when`(setup.channels.getChannelByUrl(anyString())).thenReturn(null)
                `when`(setup.channels.insertChannel(any(RssChannelEntity::class.java) ?: setup.channel)).thenReturn(7L)
                `when`(setup.items.insertItems(anyList())).thenReturn(listOf(1L))
                val xml = requireNotNull(javaClass.getResourceAsStream("/port/readerrelease/36kr-feed.xml"))
                    .bufferedReader().use { it.readText() }
                val preview = withFixtureXmlParser {
                    setup.repo.previewChannelFromContent("https://36kr.com/feed-article", xml).getOrThrow()
                }
                assertTrue(preview is AddRssPreview.Ready)
                val channel = setup.repo.confirmAddChannel((preview as AddRssPreview.Ready).preview).getOrThrow()
                assertEquals("https://36kr.com/feed-article", channel.url)
                assertEquals("36氪", channel.title)
                val items = mockingDetails(setup.items).invocations.last { it.method.name == "insertItems" }
                    .arguments[0] as List<*>
                assertEquals("测试资讯", (items.single() as RssItemEntity).title)
            }
        }
    }

    private suspend fun withUri(block: suspend () -> Unit) {
        val parsed = mock(Uri::class.java)
        `when`(parsed.scheme).thenReturn("https")
        `when`(parsed.host).thenReturn("36kr.com")
        mockStatic(Uri::class.java).use { statics ->
            statics.`when`<Uri> { Uri.parse(anyString()) }.thenReturn(parsed)
            block()
        }
    }

    private class Setup {
        val channel = RssChannelEntity(7L, "https://36kr.com/feed-article", "36氪", null, null, 100L, 50L, 100L, false)
        val channels = mock(RssChannelDao::class.java)
        val items = mock(RssItemDao::class.java)
        val repo: DefaultRssRepository
        init {
            val existing = RssItemEntity(id = 1L, channelId = 7L, title = "Old title", description = null,
                content = "Local complete body", originalContent = "Local complete body", link = "https://36kr.com/p/fixture-1",
                guid = "fixture-article", pubDate = null, imageUrl = null, audioUrl = null, videoUrl = null,
                summary = null, previewImageUrl = null, isRead = true, isLiked = false,
                readingProgress = .65f, dedupKey = "fixture-article", fetchedAt = 100L, contentSizeBytes = 20L,
                syncBodyHash = "local-content", readingPositionBytes = 650L,
                readingPositionContentHash = "local-content", readingPositionChangedAt = 200L)
            runBlocking {
                `when`(channels.getChannelByUrl(anyString())).thenReturn(channel)
                `when`(items.getItemByDedupKey(anyLong(), anyString())).thenReturn(existing)
                `when`(items.getItem(anyLong())).thenReturn(existing)
            }
            repo = DefaultRssRepository(mock(WatchRssDatabase::class.java), channels, items,
                mock(SavedEntryDao::class.java), mock(SavedSyncStateDao::class.java), mock(OfflineMediaDao::class.java),
                mock(SyncChangeLogDao::class.java), mock(SyncPeerStateDao::class.java), mock(RssSourceSyncStateDao::class.java),
                mock(ManagedCacheService::class.java), CoroutineScope(Dispatchers.Unconfined),
                fixtureRssFetchService(), mock(RssReadableService::class.java), RssParseService(), mock(RssOfflineStore::class.java), "watch-fixture")
        }
    }

    private fun article(progress: Float, clock: Long) = SyncedSavedArticle(
        articleId = "fixture-article", sourceDeviceId = "phone-fixture", url = "https://36kr.com/p/fixture-1",
        title = "Updated title", siteName = "36氪", excerpt = "metadata only", contentHtml = null, contentText = "",
        imageUrl = null, contentHash = "remote-metadata", importedAt = 50L, updatedAt = 400L,
        rssSourceUrl = "https://36kr.com/feed-article", rssSourceTitle = "36氪",
        favoriteSaved = false, favoriteChangedAt = 0L, favoriteSortOrder = 0L,
        watchLaterSaved = false, watchLaterChangedAt = 0L, watchLaterSortOrder = 0L,
        deleted = false, deletedAt = 0L, readingProgress = progress, readingPositionChangedAt = clock)
}
