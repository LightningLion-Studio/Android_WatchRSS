package com.lightningstudio.watchrss.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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
        assertEquals(null, image.width)
        assertEquals(null, image.height)

        val video = blocks[2] as ContentBlock.Video
        assertEquals("vid.mp4", video.url)
        assertEquals("poster.jpg", video.poster)
    }

    @Test
    fun parsePrefersArticleMainContentAndSkipsHiddenPopoverNoise() {
        val html = """
            <article class="normal-article">
                <div class="article-header normal">
                    <div class="title">产品团购暨直播 | 少数派十四周年：一场特别的周年回馈活动</div>
                    <div role="tooltip" aria-hidden="true" class="el-popover author-popover">
                        <div class="ss__user__card__intro">Now is the only reality.</div>
                    </div>
                </div>
                <div class="article-body">
                    <div class="article__main__content wangEditor-txt">
                        <p>真实正文第一段。</p>
                        <figure class="image ss-img-wrapper"><img src="hero.png" alt="头图" width="750" height="412" /></figure>
                        <p>真实正文第二段。</p>
                    </div>
                    <div class="comments__feed">
                        <p>这不是正文</p>
                    </div>
                </div>
            </article>
        """.trimIndent()

        val blocks = RssContentParser.parse(html)

        assertEquals(3, blocks.size)
        assertEquals("真实正文第一段。", (blocks[0] as ContentBlock.Text).text)
        assertEquals("hero.png", (blocks[1] as ContentBlock.Image).url)
        val image = blocks[1] as ContentBlock.Image
        assertEquals("hero.png", image.url)
        assertEquals(750, image.width)
        assertEquals(412, image.height)
        assertEquals("真实正文第二段。", (blocks[2] as ContentBlock.Text).text)
        assertFalse(blocks.filterIsInstance<ContentBlock.Text>().any { it.text.contains("Now is the only reality.") })
        assertFalse(blocks.filterIsInstance<ContentBlock.Text>().any { it.text.contains("这不是正文") })
    }
}
