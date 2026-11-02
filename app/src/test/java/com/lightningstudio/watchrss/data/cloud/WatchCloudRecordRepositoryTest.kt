package com.lightningstudio.watchrss.data.cloud

import android.net.Uri
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.data.db.*
import com.lightningstudio.watchrss.data.rss.*
import com.lightningstudio.watchrss.phoneconnection.bluetooth.LibrarySyncPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

class WatchCloudRecordRepositoryTest {
    @Test fun nodeEncryptedTrueSettingsApplyAndExportWithoutLoss() = runBlocking {
        val fixture = JSONObject(requireNotNull(javaClass.getResourceAsStream("/cloud-record-source-settings-node-v1.json"))
            .bufferedReader().use { it.readText() })
        val key = ByteArray(32) { it.toByte() }
        val decoded = WatchCloudRecordCodec.decrypt(fixture.getString("user"), fixture.getString("recordId"), fixture.getJSONObject("envelope")) { key }
        val source = LibrarySyncPayload.parseRssSources(JSONArray().put(decoded.value)).single()
        assertTrue(source.syncedSettingsIncluded)
        val setup = Setup()
        withValidUri {
            setup.repo.mergeSyncedRssSources(listOf(source), "phone-fixture", "watch-fixture")
        }
        assertTrue(setup.channel!!.useOriginalContent)
        assertTrue(setup.channel!!.continuePlaybackInBackground)
        val rows = mutableListOf<SyncedRssSource>()
        setup.repo.streamCloudSources("watch-fixture") { rows.add(it) }
        val wire = LibrarySyncPayload.buildResponse("watch-fixture", emptyList(), 0, rows)
            .getJSONArray("rssSources").getJSONObject(0)
        assertTrue(wire.getBoolean("useOriginalContent"))
        assertTrue(wire.getBoolean("continuePlaybackInBackground"))
    }

    @Test fun legacySourceWithoutSettingsDoesNotClearLocalSettings() = runBlocking {
        val setup = Setup()
        setup.channel = setup.channel!!.copy(useOriginalContent = true, continuePlaybackInBackground = true)
        val old = JSONObject().put("url", "https://example.com/rss").put("title", "Legacy")
            .put("updatedAt", 200).put("sortOrder", 200)
        val normalized = WatchCloudRecordCodec.metadata("source", old)
        assertFalse(normalized.has("useOriginalContent"))
        val source = LibrarySyncPayload.parseRssSources(JSONArray().put(normalized)).single()
        assertFalse(source.syncedSettingsIncluded)
        withValidUri { setup.repo.mergeSyncedRssSources(listOf(source), "phone-fixture", "watch-fixture") }
        assertTrue(setup.channel!!.useOriginalContent)
        assertTrue(setup.channel!!.continuePlaybackInBackground)
    }

    @Test fun unknownDeletePersistsAndOlderLegacyLiveCannotResurrectIt() = runBlocking {
        val setup = Setup()
        setup.channel = null
        val deleted = article().copy(deleted = true, deletedAt = 200, updatedAt = 200)
        setup.repo.mergeSyncedSavedArticles(listOf(deleted), "phone-fixture", "watch-fixture")
        assertEquals(200L, setup.states["article-fixture:ARTICLE_DELETE"]?.changedAt)
        val live = article().copy(updatedAt = 100, importedAt = 100)
        setup.repo.mergeSyncedSavedArticles(listOf(live), "phone-fixture", "watch-fixture")
        setup.repo.mergeSyncedSavedArticles(listOf(live.copy(updatedAt = 9999)), "cloud-rss-inventory", "watch-fixture")
        verify(setup.items, never()).insertItems(anyList())
        assertFalse(incomingArticleAfterDeletion(live, "phone-fixture", 200, "watch-fixture"))
        assertTrue(incomingArticleAfterDeletion(live.copy(updatedAt = 201), "phone-fixture", 200, "watch-fixture"))
    }

    @Test fun metadataOnlyRemoteContentHashIsNotFaithfullyRepresentedByCurrentWatchBodyHash() = runBlocking {
        val setup = Setup()
        val id = java.security.MessageDigest.getInstance("SHA-256").digest("https://example.com/article".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val remote = article().copy(articleId = id, contentHash = "remote-body-content-hash", rssSourceUrl = "https://example.com/rss",
            rssSourceTitle = "Fixture", isRead = true, readingProgress = .2f)
        setup.repo.mergeSyncedSavedArticles(listOf(remote), "phone-fixture", "watch-fixture")
        val exported = mutableListOf<SyncedSavedArticle>()
        setup.repo.streamCloudArticles("watch-fixture", true) { exported.add(it) }
        val local = exported.single()
        assertEquals(remote.articleId, local.articleId)
        // Diagnostic regression: a metadata-only stub currently stores its empty
        // encoded-body hash, not the remote cloud metadata content identity.
        assertNotEquals(remote.contentHash, local.contentHash)
        assertEquals(ArticleSyncBody.metadataFor(remote).bodyHash, local.contentHash)
    }

    @Test fun realRepositoryProjectionDoesNotEchoAndProgressKeepsRemoteBodyIdentityAndOrigin() = runBlocking {
        val setup = Setup()
        val user = "record-fixture-user"
        val key = ByteArray(32) { it.toByte() }
        val id = java.security.MessageDigest.getInstance("SHA-256").digest("https://example.com/article".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val remote = article().copy(articleId = id, importedAt = 50, updatedAt = 100,
            contentHash = "original-cloud-body-hash", rssSourceUrl = "https://example.com/rss", rssSourceTitle = "Fixture",
            isRead = true, readingProgress = .2f)
        fun stateRow(row: SyncedSavedArticle): JSONObject = LibrarySyncPayload.buildResponse("fixture", listOf(row), 0)
            .getJSONArray("articles").getJSONObject(0).also { it.remove("contentHtmlGzip"); it.remove("contentTextGzip") }
        val (recordId, encrypted) = WatchCloudRecordCodec.encrypt(key, user, 3, "article", stateRow(remote))
        val stamps = linkedMapOf<String, WatchRecordStamp>()
        var cursor = "0"
        val store = object : WatchRecordStore {
            override fun reset(scope: String) { cursor = "0"; stamps.clear() }
            override fun cursor(scope: String) = cursor
            override fun record(scope: String, id: String) = stamps[id]
            override fun commitPage(scope: String, next: String, rows: List<WatchRecordStoredRow>) {
                commitWrites(scope, rows); cursor = next
            }
            override fun commitWrites(scope: String, rows: List<WatchRecordStoredRow>) {
                rows.forEach { stamps[it.recordId] = it.stamp }
            }
        }
        val uploaded = mutableListOf<JSONObject>()
        val sync = WatchCloudRecordSync(user, "fixture", store, { 3 }, { key }, {},
            readPage = { after -> JSONObject().put("formatVersion", 1).put("cursor", "1").put("headRevision", "1")
                .put("hasMore", false).put("items", JSONArray().apply { if (after == "0") put(JSONObject()
                    .put("recordId", recordId).put("revision", "1").put("envelope", encrypted)) }) },
            writeBatch = { batch ->
                val rows = batch.getJSONArray("items")
                assertEquals(1, rows.length())
                val row = rows.getJSONObject(0)
                uploaded.add(WatchCloudRecordCodec.decrypt(user, recordId, row.getJSONObject("envelope")) { key }.value)
                JSONObject().put("items", JSONArray().put(JSONObject().put("recordId", recordId)
                    .put("revision", (uploaded.size + 1).toString())))
            }, streamLocal = { emit -> setup.repo.streamCloudArticles("watch-fixture", true) { row -> emit("article", stateRow(row)) } },
            apply = { values ->
                for (row in values) setup.repo.mergeCloudArticleBatch(
                    LibrarySyncPayload.parseArticles(JSONArray().put(row.value)), "phone-fixture", "watch-fixture")
            }, localProjection = { row -> setup.repo.cloudRecordProjection(row.getString("articleId"), row.getString("rssSourceUrl"))
                ?.let { WatchRecordProjection(it.contentHash, it.importedAt, it.metadataOnly) } })
        assertTrue(sync.sync(true))
        assertTrue(uploaded.isEmpty()) // ImportedAt 50->100 and encoded-empty body hash are corrected.
        val originalRow = setup.itemRows.values.single()
        assertNotEquals(remote.contentHash, originalRow.syncBodyHash)
        assertEquals(100L, originalRow.fetchedAt)
        setup.itemRows[originalRow.id] = originalRow.copy(readingProgress = .4f, readingPositionBytes = 444,
            readingPositionChangedAt = 201, readingPositionContentHash = remote.contentHash)
        assertTrue(sync.sync(true))
        assertEquals(1, uploaded.size)
        assertEquals(remote.contentHash, uploaded.single().getString("contentHash"))
        assertEquals(50L, uploaded.single().getLong("importedAt"))
        assertEquals(444L, uploaded.single().getLong("readingPositionBytes"))
        setup.itemRows[originalRow.id] = setup.itemRows.getValue(originalRow.id).copy(syncBodyHash = "genuine-new-body-hash")
        assertTrue(sync.sync(true))
        assertEquals("genuine-new-body-hash", uploaded.last().getString("contentHash"))
        assertEquals(50L, uploaded.last().getLong("importedAt"))
    }

    @Test fun preexistingRealBodyCannotBeMappedBackToRemoteOldBodyHash() = runBlocking {
        val setup = Setup()
        val id = java.security.MessageDigest.getInstance("SHA-256").digest("https://example.com/article".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val remote = article().copy(articleId = id, importedAt = 50, updatedAt = 100, contentHash = "old-cloud-hash",
            rssSourceUrl = "https://example.com/rss", rssSourceTitle = "Fixture", isRead = true)
        setup.repo.mergeSyncedSavedArticles(listOf(remote), "phone-fixture", "watch-fixture")
        val item = setup.itemRows.values.single()
        setup.itemRows[item.id] = item.copy(content = "genuine body", originalContent = "genuine body",
            syncBodyHash = "genuine-new-local-body-hash", fetchedAt = 300)
        val projection = requireNotNull(setup.repo.cloudRecordProjection(id, remote.rssSourceUrl))
        assertFalse(projection.metadataOnly)
        val user = "fixture-user"
        val key = ByteArray(32) { it.toByte() }
        fun wire(row: SyncedSavedArticle) = LibrarySyncPayload.buildResponse("fixture", listOf(row), 0)
            .getJSONArray("articles").getJSONObject(0).also { it.remove("contentHtmlGzip"); it.remove("contentTextGzip") }
        val (recordId, envelope) = WatchCloudRecordCodec.encrypt(key, user, 3, "article", wire(remote))
        val rows = linkedMapOf<String, WatchRecordStamp>()
        val store = object : WatchRecordStore {
            override fun reset(scope: String) = Unit
            override fun cursor(scope: String) = "0"
            override fun record(scope: String, id: String) = rows[id]
            override fun commitPage(scope: String, cursor: String, values: List<WatchRecordStoredRow>) { commitWrites(scope, values) }
            override fun commitWrites(scope: String, values: List<WatchRecordStoredRow>) { values.forEach { rows[it.recordId] = it.stamp } }
        }
        var sent: JSONObject? = null
        val sync = WatchCloudRecordSync(user, "fixture", store, { 3 }, { key }, {}, readPage = {
            JSONObject().put("formatVersion", 1).put("cursor", "1").put("headRevision", "1").put("hasMore", false)
                .put("items", JSONArray().put(JSONObject().put("recordId", recordId).put("revision", "1").put("envelope", envelope)))
        }, writeBatch = { body ->
            sent = WatchCloudRecordCodec.decrypt(user, recordId, body.getJSONArray("items").getJSONObject(0).getJSONObject("envelope")) { key }.value
            JSONObject().put("items", JSONArray().put(JSONObject().put("recordId", recordId).put("revision", "2")))
        }, streamLocal = { emit -> setup.repo.streamCloudArticles("watch-fixture", true) { emit("article", wire(it)) } },
            apply = {}, localProjection = { WatchRecordProjection(projection.contentHash, projection.importedAt, projection.metadataOnly) })
        assertTrue(sync.sync(true))
        assertEquals("genuine-new-local-body-hash", sent!!.getString("contentHash"))
        assertEquals(300L, sent!!.getLong("importedAt"))
    }

    private suspend fun withValidUri(block: suspend () -> Unit) = withContext(Dispatchers.IO) {
        val parsed = mock(Uri::class.java)
        `when`(parsed.scheme).thenReturn("https")
        `when`(parsed.host).thenReturn("example.com")
        mockStatic(Uri::class.java).use { statics ->
            statics.`when`<Uri> { Uri.parse(anyString()) }.thenReturn(parsed)
            block()
        }
    }

    private class Setup {
        var channel: RssChannelEntity? = RssChannelEntity(id = 7, url = "https://example.com/rss", title = "Fixture",
            description = "", imageUrl = null, lastFetchedAt = null, createdAt = 50, sortOrder = 100, isPinned = false)
        val states = linkedMapOf<String, SavedSyncStateEntity>()
        val channels = mock(RssChannelDao::class.java)
        val items = mock(RssItemDao::class.java)
        val itemRows = linkedMapOf<Long, RssItemEntity>()
        private val saved = mock(SavedEntryDao::class.java)
        private val tombstones = mock(SavedSyncStateDao::class.java)
        private val sourceStates = mock(RssSourceSyncStateDao::class.java)
        val repo: DefaultRssRepository
        init {
            runBlocking {
                `when`(items.getItemByDedupKey(anyLong(), anyString())).thenAnswer { call ->
                    itemRows.values.firstOrNull { it.channelId == call.getArgument<Long>(0) && it.dedupKey == call.getArgument<String>(1) }
                }
                `when`(items.insertItems(anyList())).thenAnswer { call ->
                    call.getArgument<List<RssItemEntity>>(0).map { row ->
                        val id = itemRows.size + 1L; itemRows[id] = row.copy(id = id); id
                    }
                }
                `when`(items.getCloudRecordItemId(anyLong(), anyString())).thenAnswer { call ->
                    itemRows.values.firstOrNull { it.channelId == call.getArgument<Long>(0) && it.dedupKey == call.getArgument<String>(1) }?.id
                }
                `when`(saved.getByItemId(anyLong())).thenReturn(emptyList())
                `when`(items.getCloudExportItemIds(anyLong(), anyInt())).thenAnswer { call ->
                    itemRows.keys.filter { it > call.getArgument<Long>(0) }.take(call.getArgument<Int>(1))
                }
                `when`(items.getCloudExportMetadata(anyLong())).thenAnswer { call ->
                    itemRows[call.getArgument<Long>(0)]?.copy(content = null, originalContent = null)
                }
                `when`(items.getItem(anyLong())).thenAnswer { call -> itemRows[call.getArgument<Long>(0)] }
                `when`(channels.getChannelByUrl(anyString())).thenAnswer { channel }
                `when`(channels.getChannel(anyLong())).thenAnswer { channel }
                `when`(channels.getCloudExportPage(anyLong(), anyInt())).thenAnswer { call ->
                    channel?.takeIf { it.id > call.getArgument<Long>(0) }?.let(::listOf) ?: emptyList<RssChannelEntity>()
                }
                doAnswer { call -> channel = call.getArgument(0); null }.`when`(channels).updateChannel(any(RssChannelEntity::class.java) ?: requireNotNull(channel))
                `when`(tombstones.getCloudExportArticleIds(anyString(), anyInt())).thenReturn(emptyList())
                `when`(tombstones.get(anyString(), anyString())).thenAnswer { call -> states[call.getArgument<String>(0) + ":" + call.getArgument<String>(1)] }
                doAnswer { call -> val row = call.getArgument<SavedSyncStateEntity>(0); states[row.articleId + ":" + row.saveType] = row; null }
                    .`when`(tombstones).upsert(any(SavedSyncStateEntity::class.java) ?: SavedSyncStateEntity("fixture", "FAVORITE", null, "fixture", false, 1, 0, "fixture"))
                `when`(sourceStates.getCloudExportPage(anyString(), anyInt())).thenReturn(emptyList())
            }
            repo = DefaultRssRepository(mock(WatchRssDatabase::class.java), channels, items, saved, tombstones, mock(OfflineMediaDao::class.java),
                mock(SyncChangeLogDao::class.java), mock(SyncPeerStateDao::class.java), sourceStates,
                mock(ManagedCacheService::class.java), CoroutineScope(Dispatchers.Unconfined),
                mock(RssFetchService::class.java), mock(RssReadableService::class.java), mock(RssParseService::class.java),
                mock(RssOfflineStore::class.java), "watch-fixture")
        }
    }

    private fun article() = SyncedSavedArticle(articleId = "article-fixture", sourceDeviceId = "phone-fixture",
        url = "https://example.com/article", title = "Fixture", siteName = "Fixture", excerpt = "", contentHtml = null,
        contentText = "", imageUrl = null, contentHash = "fixture", importedAt = 100, updatedAt = 100,
        favoriteSaved = false, favoriteChangedAt = 0, favoriteSortOrder = 0, watchLaterSaved = false,
        watchLaterChangedAt = 0, watchLaterSortOrder = 0, deleted = false, deletedAt = 0)
}
