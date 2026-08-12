package com.lightningstudio.watchrss.data.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WatchTokenManagerTest {
    @Test
    fun refreshEntitlementPayloadReplacesStaleSnapshot() {
        val manager = WatchTokenManager(FakeAccountStore())
        val entitlement = manager.decodeEntitlement(
            JSONObject()
                .put("plan", "member")
                .put("active", true)
                .put("expiresAt", 1234L)
                .put("features", JSONArray(listOf("cloud", "rss")))
        )

        assertEquals("member", entitlement.plan)
        assertEquals(1234L, entitlement.expiresAtMillis)
        assertEquals(listOf("cloud", "rss"), entitlement.features)
    }

    @Test
    fun missingActiveFailsClosed() {
        val manager = WatchTokenManager(FakeAccountStore())
        assertFalse(manager.decodeEntitlement(JSONObject().put("plan", "member")).active)
    }

    private class FakeAccountStore : AccountStore {
        private val mutableState = MutableStateFlow<WatchAccountState?>(null)
        override fun read(): WatchAccountState? = mutableState.value
        override val state: StateFlow<WatchAccountState?> = mutableState
        override fun save(state: WatchAccountState) { mutableState.value = state }
        override fun clear() { mutableState.value = null }
    }
}
