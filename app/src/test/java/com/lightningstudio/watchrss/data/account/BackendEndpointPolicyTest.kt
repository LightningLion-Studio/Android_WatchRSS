package com.lightningstudio.watchrss.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackendEndpointPolicyTest {
    @Test
    fun releaseRequiresHttps() {
        assertEquals(
            "https://api.example.com",
            BackendEndpointPolicy.requireSecure(" https://api.example.com/ ", debugBuild = false)
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackendEndpointPolicy.requireSecure("http://api.example.com", debugBuild = false)
        }
    }

    @Test
    fun debugOnlyAllowsLoopbackHttp() {
        assertEquals(
            "http://10.0.2.2:8787",
            BackendEndpointPolicy.requireSecure("http://10.0.2.2:8787/", debugBuild = true)
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackendEndpointPolicy.requireSecure("http://192.168.1.8:8787", debugBuild = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackendEndpointPolicy.requireSecure("http://127.0.0.1:8787", debugBuild = false)
        }
    }

    @Test
    fun credentialsInEndpointAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackendEndpointPolicy.requireSecure("https://user:pass@example.com", debugBuild = false)
        }
    }
}
