package com.lightningstudio.watchrss.ui.screen.rss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssBrowserVerificationPayloadTest {
    @Test
    fun acceptsPlainTextRssXmlWithLeadingBomAndWhitespace() {
        assertTrue(isPlainTextRssPayload("\uFEFF \n<?xml version=\"1.0\"?><rss><channel/></rss>"))
        assertTrue(isPlainTextRssPayload("<feed xmlns=\"http://www.w3.org/2005/Atom\"/>"))
        assertTrue(isPlainTextRssPayload("<rdf:RDF/>"))
    }

    @Test
    fun rejectsNonFeedTextAndHtml() {
        assertFalse(isPlainTextRssPayload("正在进行安全检测，请稍候"))
        assertFalse(isPlainTextRssPayload("<html><body>not a feed</body></html>"))
    }
}
