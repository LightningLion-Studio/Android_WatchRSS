package com.lightningstudio.watchrss.data.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class RssDedupKeyTest {
    @Test
    fun computePrefersGuid() {
        val key = RssDedupKey.compute("g1", "l1", "t1")
        assertEquals("guid:g1", key)
    }

    @Test
    fun computeUsesLinkWhenGuidBlank() {
        val key = RssDedupKey.compute("   ", "l1", "t1")
        assertEquals("link:l1", key)
    }

    @Test
    fun computeFallsBackToTitle() {
        val key = RssDedupKey.compute(null, " ", "title")
        assertEquals("title:title", key)
    }
}
