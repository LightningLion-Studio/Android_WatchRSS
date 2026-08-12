package com.lightningstudio.watchrss.phoneconnection.bluetooth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountSyncPayloadTest {
    @Test
    fun secureBackendEndpointIsAcceptedAndNormalized() {
        val state = AccountSyncPayload.parseRequest(payload("https://api.example.com/"))

        assertEquals("https://api.example.com", state.backendBaseUrl)
    }

    @Test
    fun remoteCleartextBackendEndpointIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountSyncPayload.parseRequest(payload("http://192.168.1.8:8787"))
        }
    }

    private fun payload(backendBaseUrl: String) = JSONObject().apply {
        put("account", JSONObject().apply {
            put("userId", "user-1")
            put("watchAccessToken", "wra_test")
            put("backendBaseUrl", backendBaseUrl)
        })
    }
}
