package com.lightningstudio.watchrss.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssContentParserTest {
    @Test
    fun parseReturnsEmptyForBlank() {
        val blocks = RssContentParser.parse("   ")
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun parseHandlesTextImageVideoOrder() {
        val html = "<p>Hello</p><img src=\"img.png\" alt=\"A\"/>" +
            "<video src=\"vid.mp4\" poster=\"poster.jpg\"></video>"
        val blocks = RssContentParser.parse(html)

        assertEquals(3, blocks.size)
        val text = blocks[0] as ContentBlock.Text
        assertEquals("Hello", text.text)

        val image = blocks[1] as ContentBlock.Image
        assertEquals("img.png", image.url)
        assertEquals("A", image.alt)

        val video = blocks[2] as ContentBlock.Video
        assertEquals("vid.mp4", video.url)
        assertEquals("poster.jpg", video.poster)
    }
}
