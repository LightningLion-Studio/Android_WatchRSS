package com.lightningstudio.watchrss.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.AppContainer
import com.lightningstudio.watchrss.data.account.WatchAccountState
import com.lightningstudio.watchrss.data.account.WatchAccountStore
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.phoneconnection.bluetooth.BluetoothSyncProtocol
import com.lightningstudio.watchrss.phoneconnection.bluetooth.WatchBluetoothSyncServer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchLibraryScopeWireTest {
    @Test fun wrongAccountIsRejectedOnActualFramePathBeforeAnyLibraryRead() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as WatchRssApplication
        val original = app.container
        val name = "verification_wire_account_${UUID.randomUUID()}"
        val fixtureStore = WatchAccountStore(context, name)
        fixtureStore.save(WatchAccountState("alpha", "138****0000", "test", "test-token", 0,
            backendBaseUrl = "https://invalid.example", posthogHost = "", posthogProjectApiKey = ""))
        app.setContainerForTesting(object : AppContainer by original {
            override val watchAccountStore = fixtureStore
            override val rssRepository: RssRepository get() = error("Mismatched request must not read any library")
        })
        try {
            for (action in listOf("syncLibrary", "pullSavedItems", "syncNotes", "syncNoteAsset", "syncReader", "previewReader", "syncLlmTokenUsage", "remoteInput")) {
                val request = JSONObject().put("action", action).put("phase", "cursor")
                    .put("libraryAccountUserId", "beta").put("libraryAccountBackend", "https://invalid.example")
                val incoming = ByteArrayOutputStream().also { BluetoothSyncProtocol.writeFrame(it, request) }
                val outgoing = ByteArrayOutputStream()
                val result = WatchBluetoothSyncServer(context).serveStreams(
                    ByteArrayInputStream(incoming.toByteArray()), outgoing,
                    remoteName = "verification-phone", remoteAddress = "verification-only"
                )
                assertFalse(result.response.optBoolean("success"))
                val received = BluetoothSyncProtocol.readFrame(ByteArrayInputStream(outgoing.toByteArray()))
                assertFalse(received.optBoolean("success"))
                assertTrue(received.optString("message").contains("账号不一致"))
                assertEquals("alpha", fixtureStore.read()?.userId)
            }
        } finally {
            app.setContainerForTesting(null)
            context.deleteSharedPreferences(name)
        }
    }
    @Test fun automaticProbeConfirmsOnlyOwnAccountAndAccountFrameCannotSwitchIdentity() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as WatchRssApplication
        val original = app.container
        val name = "verification_auto_account_${UUID.randomUUID()}"
        val fixtureStore = WatchAccountStore(context, name)
        fixtureStore.save(WatchAccountState("alpha", "138****0000", "test", "test-token", 0,
            backendBaseUrl = "https://invalid.example", posthogHost = "", posthogProjectApiKey = ""))
        app.setContainerForTesting(object : AppContainer by original {
            override val watchAccountStore = fixtureStore
            override val rssRepository: RssRepository get() = error("Probe/rejected account must not access library")
        })
        suspend fun exchange(request: JSONObject): JSONObject {
            val incoming = ByteArrayOutputStream().also {
                BluetoothSyncProtocol.writeFrame(it, request)
                BluetoothSyncProtocol.writeFrame(it, JSONObject().put("action", "ack").put("success", true))
            }
            val outgoing = ByteArrayOutputStream()
            WatchBluetoothSyncServer(context).serveStreams(ByteArrayInputStream(incoming.toByteArray()), outgoing,
                "verification-phone", "verification-only")
            return BluetoothSyncProtocol.readFrame(ByteArrayInputStream(outgoing.toByteArray()))
        }
        try {
            for (id in listOf("alpha", "beta")) {
                val probe = JSONObject().put("action", "syncLibrary").put("phase", "probe").put("version", 14)
                    .put("libraryAccountUserId", id).put("libraryAccountBackend", "https://invalid.example")
                val response = exchange(probe)
                assertTrue(response.optBoolean("supportsAutomaticAccountSync"))
                assertEquals(id == "alpha", response.optBoolean("accountMatchesRequest"))
                assertFalse(response.has("boundUserId"))
            }
            val request = JSONObject().put("action", "syncAccount").put("automatic", true)
                .put("account", JSONObject().put("userId", "beta").put("watchAccessToken", "fixture-only")
                    .put("backendBaseUrl", "https://invalid.example"))
            val rejected = exchange(request)
            assertFalse(rejected.optBoolean("success"))
            assertTrue(rejected.optString("message").contains("手动同步"))
            assertEquals("alpha", fixtureStore.read()?.userId)
        } finally {
            app.setContainerForTesting(null)
            context.deleteSharedPreferences(name)
        }
    }
}
