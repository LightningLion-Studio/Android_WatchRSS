package com.lightningstudio.watchrss.port.bili

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lightningstudio.watchrss.data.bili.BiliRecommendationHistory
import com.lightningstudio.watchrss.data.bili.biliRecommendationId
import com.lightningstudio.watchrss.data.bili.biliRecommendationScope
import com.lightningstudio.watchrss.sdk.bili.BiliAccount
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BiliRecommendationHistoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun accountScope_survivesCookieRotation_andNormalizesUid() {
        val first = BiliAccount(cookies = mapOf("SESSDATA" to "fixture-a", "DedeUserID" to " 00042 "))
        val rotated = first.copy(cookies = first.cookies + ("SESSDATA" to "fixture-b"))
        assertEquals("account:42", biliRecommendationScope(first))
        assertEquals(biliRecommendationScope(first), biliRecommendationScope(rotated))
        assertNull(biliRecommendationScope(first.copy(cookies = mapOf("DedeUserID" to "42"))))
    }

    @Test
    fun incompleteAccounts_useSeparateHashedSessions() {
        val first = biliRecommendationScope(BiliAccount(cookies = mapOf("SESSDATA" to "fixture-a")))!!
        val second = biliRecommendationScope(BiliAccount(cookies = mapOf("SESSDATA" to "fixture-b", "DedeUserID" to "0")))
        assertNotEquals(first, second)
        assertFalse(first.contains("fixture-a"))
        assertEquals(72, first.length)
        assertNull(biliRecommendationScope(null))
    }

    @Test
    fun invalidItems_neverUseTitleAsPersistentIdentity() {
        assertEquals("aid:1", biliRecommendationId(BiliItem(aid = 1, bvid = "BV1")))
        assertEquals("bvid:BV1", biliRecommendationId(BiliItem(aid = 0, bvid = " BV1 ")))
        assertNull(biliRecommendationId(BiliItem(title = "title only")))
        assertNull(biliRecommendationId(BiliItem(bvid = "BV\ninvalid")))
        assertNull(biliRecommendationId(BiliItem(bvid = "x".repeat(300))))
    }

    @Test
    fun persistence_keeps512MostRecentlyShown_perAccount_andSurvivesReopen() = runTest {
        val file = File(temporaryFolder.root, "history.preferences_pb")
        val job = SupervisorJob()
        val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        val history = BiliRecommendationHistory(dataStore)
        try {
            history.remember("account:1", (1L..513L).map { BiliItem(aid = it) })
            history.remember("account:2", listOf(BiliItem(aid = 1)))
            assertEquals((2L..513L).map { "aid:$it" }, history.read("account:1"))
            // Reading/filtering does not promote an old hit. An actual display does.
            history.read("account:1")
            history.remember("account:1", listOf(BiliItem(aid = 2), BiliItem(aid = 514)))
            assertEquals((4L..513L).map { "aid:$it" } + listOf("aid:2", "aid:514"), history.read("account:1"))
            assertEquals(listOf("aid:1"), history.read("account:2"))
        } finally {
            job.cancelAndJoin()
        }
        val reopenedJob = SupervisorJob()
        val reopened = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + reopenedJob)) { file }
        try {
            assertEquals(512, BiliRecommendationHistory(reopened).read("account:1").size)
            assertEquals("aid:514", BiliRecommendationHistory(reopened).read("account:1").last())
            assertEquals(listOf("aid:1"), BiliRecommendationHistory(reopened).read("account:2"))
        } finally {
            reopenedJob.cancelAndJoin()
        }
    }

    @Test
    fun concurrentDisplays_areAtomic_andMalformedRecordsAreDiscarded() = runTest {
        val job = SupervisorJob()
        val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
            File(temporaryFolder.root, "atomic.preferences_pb")
        }
        val history = BiliRecommendationHistory(dataStore)
        try {
            dataStore.edit {
                it[stringPreferencesKey("bili_feed_shown_v1:account:1")] = "aid:0\naid:2\naid:2\ninvalid\nbvid:BV1"
            }
            assertEquals(listOf("aid:2", "bvid:BV1"), history.read("account:1"))
            (3L..30L).map { id -> launch { history.remember("account:1", listOf(BiliItem(aid = id))) } }
                .forEach { it.join() }
            assertEquals(30, history.read("account:1").size)
            history.remember(null, listOf(BiliItem(aid = 1)))
            assertEquals(emptyList<String>(), history.read(null))
        } finally {
            job.cancelAndJoin()
        }
    }
}
