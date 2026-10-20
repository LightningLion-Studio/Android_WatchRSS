package com.lightningstudio.watchrss.data.cloud

import com.lightningstudio.watchrss.data.account.WatchAccountState
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/** Synthetic OkHttp interceptor; exercises real client routing/status logic without network. */
class WatchCloudRecordClientTest {
    @Test fun missingEndpointIsTheOnlyHttpDowngradeAndProbeNeverConsents() = runBlocking {
        server(404, "{}") { client, account, requests ->
            assertNull(client.recordPage(account, "0"))
            assertEquals(listOf("GET /functions/v1/cloud/records?after=0&limit=16"), requests.map { it.first })
        }
        server(403, "{\"error\":\"cloud_forbidden\"}") { client, account, _ ->
            try { client.recordPage(account, "0"); fail() }
            catch (error: IOException) { assertTrue(error.message.orEmpty().contains("HTTP 403")) }
        }
    }

    @Test fun exactConflictAndCursorErrorsReachTheCoordinator() = runBlocking {
        server(409, "{\"error\":\"cloud_record_conflict\"}") { client, account, _ ->
            try { client.writeRecords(account, JSONObject().put("formatVersion", 1).put("items", JSONArray())); fail() }
            catch (_: WatchCloudRecordConflict) { }
        }
        server(400, "{\"error\":\"cloud_record_cursor_invalid\"}") { client, account, _ ->
            try { client.recordPage(account, "99"); fail() }
            catch (_: WatchCloudRecordCursorInvalid) { }
        }
    }

    @Test fun registrationRetainsLegacyAndAdvertisesRecordCapability() = runBlocking {
        server(200, "{}") { client, account, requests ->
            client.register(account, "fixture-device", "fixture-spki", "Fixture", 1)
            val capabilities = JSONObject(requests.single().second).getJSONObject("capabilities")
            assertEquals(1, capabilities.getInt("rssRecordsVersion"))
            assertEquals(2, capabilities.getInt("snapshotSchema"))
        }
    }

    private suspend fun server(status: Int, response: String,
        run: suspend (WatchCloudClient, WatchAccountState, MutableList<Pair<String, String>>) -> Unit) {
        val requests = mutableListOf<Pair<String, String>>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
            requests.add(request.method + " " + request.url.encodedPath +
                (request.url.encodedQuery?.let { "?$it" } ?: "") to body)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status)
                .message("fixture").body(response.toResponseBody()).build()
        }.build()
        val account = WatchAccountState(userId = "fixture-user", phoneMasked = "fixture", installId = "fixture-install",
            watchDeviceToken = "fixture-token", tokenExpiresAtMillis = Long.MAX_VALUE,
            backendBaseUrl = "https://fixture.invalid", posthogHost = "", posthogProjectApiKey = "")
        run(WatchCloudClient(http), account, requests)
    }
}
