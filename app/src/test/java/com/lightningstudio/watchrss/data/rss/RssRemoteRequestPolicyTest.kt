package com.lightningstudio.watchrss.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssRemoteRequestPolicyTest {
    @Test
    fun headersFor_sspaiImage_includesReferer() {
        val headers = RssRemoteRequestPolicy.headersFor(
            "https://cdnfile.sspai.com/2026/03/10/demo.jpg?imageView2/2/w/1120/q/90"
        )

        assertEquals("https://sspai.com/", headers["Referer"])
        assertEquals(RssRemoteRequestPolicy.DEFAULT_USER_AGENT, headers["User-Agent"])
    }

    @Test
    fun headersFor_regularImage_skipsReferer() {
        val headers = RssRemoteRequestPolicy.headersFor("https://example.com/image.png")

        assertFalse(headers.names().contains("Referer"))
        assertEquals(RssRemoteRequestPolicy.DEFAULT_USER_AGENT, headers["User-Agent"])
    }
}
