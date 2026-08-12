package com.lightningstudio.watchrss.data.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchRssAppVersionHeaderTest {
    @Test
    fun `value identifies watch version and code`() {
        assertEquals("watch-1.3.1-3+24", watchRssAppVersionHeaderValue("1.3.1-3", 24))
    }

    @Test
    fun `builder replaces any stale version header`() {
        val request = Request.Builder()
            .url("https://backend.example/healthz")
            .header(WATCHRSS_APP_VERSION_HEADER, "stale")
            .withWatchRssAppVersionHeader()
            .build()

        assertEquals(1, request.headers(WATCHRSS_APP_VERSION_HEADER).size)
        assertEquals(watchRssAppVersionHeaderValue(), request.header(WATCHRSS_APP_VERSION_HEADER))
    }
}
