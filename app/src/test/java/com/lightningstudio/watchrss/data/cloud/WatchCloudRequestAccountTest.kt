package com.lightningstudio.watchrss.data.cloud

import com.lightningstudio.watchrss.data.account.WatchAccountState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WatchCloudRequestAccountTest {
    private fun account() = WatchAccountState("owner", "", "installation", "old", 1L,
        backendBaseUrl="https://example.invalid", posthogHost="", posthogProjectApiKey="")

    @Test fun expiredCapturedSessionResolvesCurrentTokenForEveryRequest() = runBlocking {
        val original = account()
        var current = original.copy(watchDeviceToken="refreshed", tokenExpiresAtMillis=Long.MAX_VALUE)
        var calls = 0
        val client = WatchCloudClient(accountProvider = { calls++; current })
        assertEquals("refreshed", client.requestAccount(original).watchDeviceToken)
        // Simulate an access-token rotation while a long-running merge still holds original.
        current = current.copy(watchDeviceToken="rotated-again")
        assertEquals("rotated-again", client.requestAccount(original).watchDeviceToken)
        assertEquals(2, calls)
    }

    @Test fun changedOwnerInstallationOrBackendCannotReplaceCapturedSession() = runBlocking {
        val original = account()
        for (changed in listOf(original.copy(userId="other"), original.copy(installId="other"),
            original.copy(backendBaseUrl="https://other.invalid"))) {
            val client = WatchCloudClient(accountProvider = { changed.copy(tokenExpiresAtMillis=Long.MAX_VALUE) })
            try { client.requestAccount(original); fail("session switch accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun failedRefreshPropagatesAndExpiredProviderIsRejected() = runBlocking {
        val original = account()
        val failed = WatchCloudClient(accountProvider = { throw java.io.IOException("revoked") })
        try { failed.requestAccount(original); fail("refresh failure swallowed") }
        catch (_: java.io.IOException) { }
        try { WatchCloudClient(accountProvider={original}).requestAccount(original); fail("expired accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
