package com.lightningstudio.watchrss.port.readerrelease

import com.lightningstudio.watchrss.data.rss.RssFetchService
import com.prof18.rssparser.RssParserBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mockito.Mockito.*
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.Reader
import java.lang.reflect.Proxy
import javax.xml.parsers.DocumentBuilderFactory

/** Keep thread-local Android factory substitution on the caller thread. */
internal fun fixtureRssFetchService(): RssFetchService {
    val parser = RssParserBuilder().build()
    parser.javaClass.getDeclaredField("coroutineContext").apply { isAccessible = true }
        .set(parser, Dispatchers.Unconfined)
    val xml = parser.javaClass.getDeclaredField("xmlParser").apply { isAccessible = true }.get(parser)
    xml.javaClass.getDeclaredField("dispatcher").apply { isAccessible = true }
        .set(xml, Dispatchers.Unconfined)
    return RssFetchService(parser = parser)
}

/** Supply the Android-only pull API from JDK DOM; RSS extraction stays in the real library. */
internal suspend fun <T> withFixtureXmlParser(block: suspend () -> T): T = withContext(Dispatchers.IO) {
    val factory = mock(XmlPullParserFactory::class.java)
    `when`(factory.newPullParser()).thenAnswer { domPullParser() }
    mockStatic(XmlPullParserFactory::class.java).use { platform ->
        platform.`when`<XmlPullParserFactory> { XmlPullParserFactory.newInstance() }.thenReturn(factory)
        block()
    }
}

private data class XmlToken(val event: Int, val node: Node? = null)

private fun domPullParser(): XmlPullParser {
    val tokens = mutableListOf<XmlToken>()
    var index = 0
    fun visit(node: Node) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            tokens += XmlToken(XmlPullParser.START_TAG, node)
            for (child in 0 until node.childNodes.length) visit(node.childNodes.item(child))
            tokens += XmlToken(XmlPullParser.END_TAG, node)
        } else if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
            tokens += XmlToken(XmlPullParser.TEXT, node)
        }
    }
    return Proxy.newProxyInstance(XmlPullParser::class.java.classLoader, arrayOf(XmlPullParser::class.java)) { _, method, args ->
        when (method.name) {
            "setInput" -> {
                val input = when (val source = args[0]) {
                    is InputStream -> InputSource(source).apply { encoding = args[1] as? String ?: "UTF-8" }
                    is Reader -> InputSource(source)
                    else -> error("Unsupported fixture input")
                }
                val document = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = false
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                }.newDocumentBuilder().parse(input)
                tokens.clear()
                tokens += XmlToken(XmlPullParser.START_DOCUMENT)
                visit(document.documentElement)
                tokens += XmlToken(XmlPullParser.END_DOCUMENT)
                index = 0
                null
            }
            "getEventType" -> tokens[index].event
            "next", "nextToken" -> { index = (index + 1).coerceAtMost(tokens.lastIndex); tokens[index].event }
            "nextText" -> {
                val node = requireNotNull(tokens[index].node)
                var depth = 1
                while (depth > 0) {
                    index++
                    if (tokens[index].event == XmlPullParser.START_TAG) depth++
                    if (tokens[index].event == XmlPullParser.END_TAG) depth--
                }
                node.textContent
            }
            "getName" -> tokens[index].node?.nodeName
            "getText" -> tokens[index].node?.nodeValue
            "getAttributeValue" -> tokens[index].node?.attributes?.let { attributes ->
                if (args.size == 1) attributes.item(args[0] as Int)?.nodeValue
                else attributes.getNamedItem(args[1] as String)?.nodeValue
            }
            "getAttributeCount" -> tokens[index].node?.attributes?.length ?: 0
            "getAttributeName" -> tokens[index].node?.attributes?.item(args[0] as Int)?.nodeName
            "getPrefix", "getNamespace" -> null
            "getLineNumber", "getColumnNumber" -> -1
            "require" -> { check(tokens[index].event == args[0]); null }
            "toString" -> "JDK DOM fixture pull parser"
            else -> error("Unsupported fixture pull method: ${method.name}")
        }
    } as XmlPullParser
}
