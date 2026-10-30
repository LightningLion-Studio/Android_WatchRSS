package com.lightningstudio.watchrss.data.cloud

import com.lightningstudio.watchrss.testutil.TestRssRepository
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudContentFingerprintTest {
    @Test fun repeatedExportsIgnoreOnlyTransportClocks() = runTest {
        val library = WatchCloudLibraryAdapter(TestRssRepository(), "watch-fixture")
        val state = library.exportState()
        val relay = library.export()
        val laterState = JSONObject(state.toString(Charsets.UTF_8)).put("exportedAt", Long.MAX_VALUE).toString().toByteArray()
        val laterRelay = JSONObject(relay.toString(Charsets.UTF_8)).put("sentAt", Long.MAX_VALUE).toString().toByteArray()
        assertEquals(watchCloudContentHash(state, relay, 1), watchCloudContentHash(laterState, laterRelay, 1))
        assertNotEquals(watchCloudContentHash(state, relay, 1), watchCloudContentHash(state, relay, 2))
    }

    @Test fun readingProgressAndPrivateBodyEditsRemainSignificant() {
        val state = """{"exportedAt":1,"articles":[{"readingProgress":0.1,"updatedAt":3}]}""".toByteArray()
        val relay = """{"sentAt":1,"articles":[{"contentText":"first"}]}""".toByteArray()
        val initial = watchCloudContentHash(state, relay, 1)
        assertNotEquals(initial, watchCloudContentHash(state.toString(Charsets.UTF_8).replace("0.1", "0.2").toByteArray(), relay, 1))
        assertNotEquals(initial, watchCloudContentHash(state, relay.toString(Charsets.UTF_8).replace("first", "second").toByteArray(), 1))
        assertNotEquals(initial, watchCloudContentHash(state.toString(Charsets.UTF_8).replace("updatedAt\":3", "updatedAt\":4").toByteArray(), relay, 1))
    }
}
