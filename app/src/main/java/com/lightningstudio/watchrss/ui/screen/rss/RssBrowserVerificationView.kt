package com.lightningstudio.watchrss.ui.screen.rss

import android.graphics.Color
import android.util.Log
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import org.json.JSONArray
import com.lightningstudio.watchrss.BuildConfig

private const val RSS_XML_CHUNK_LENGTH = 12_000
private const val MAX_RSS_XML_LENGTH = 4 * 1024 * 1024
private const val WAF_CHALLENGE_ELEMENT_ID = "__waf_verification_challenge"

private class RssWebViewSession {
    var pageLoadId: Int = 0
    var extractionId: Int = 0
    var delivered: Boolean = false
}

/**
 * Loads the original subscription URL in Android WebView so its own JavaScript and
 * CookieManager can finish a site-issued browser challenge. No JavaScript bridge is exposed.
 */
@Composable
internal fun RssBrowserVerificationView(
    url: String,
    challengeHtml: String,
    onStatusChanged: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onFeedXmlReady: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val session = remember(url, challengeHtml) { RssWebViewSession() }
    val latestOnStatusChanged = rememberUpdatedState(onStatusChanged)
    val latestOnProgressChanged = rememberUpdatedState(onProgressChanged)
    val latestOnFeedXmlReady = rememberUpdatedState(onFeedXmlReady)
    val webViewHolder = remember(url, challengeHtml) { mutableListOf<WebView?>(null) }
    val pageRequestKey = remember(url, challengeHtml) { "$url:${challengeHtml.hashCode()}" }

    AndroidView(
        modifier = modifier.testTag("rss/browser_verification_webview"),
        factory = { viewContext ->
            CookieManager.getInstance().setAcceptCookie(true)
            WebView(viewContext).apply {
                setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                tag = pageRequestKey
                webViewHolder[0] = this
                Log.i(
                    "RssBrowserVerification",
                    if (challengeHtml.isNotBlank()) "Loading captured challenge HTML (${challengeHtml.length} characters)"
                    else "Loading subscription URL directly in WebView"
                )
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        latestOnProgressChanged.value(newProgress.coerceIn(0, 100))
                    }

                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            val sourceHost = Uri.parse(message.sourceId()).host ?: "unknown"
                            Log.w(
                                "RssBrowserVerification",
                                "WebView JavaScript error at line ${message.lineNumber()} from $sourceHost"
                            )
                        }
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, pageUrl, favicon)
                        Log.i("RssBrowserVerification", "WebView navigation started")
                        session.pageLoadId += 1
                        session.delivered = false
                        latestOnProgressChanged.value(0)
                        latestOnStatusChanged.value("正在加载验证网页…")
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val scheme = request.url.scheme?.lowercase()
                        return scheme != "http" && scheme != "https"
                    }

                    override fun onPageFinished(view: WebView, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        Log.i("RssBrowserVerification", "WebView page finished; inspecting document")
                        if (session.delivered) return
                        val expectedLoad = session.pageLoadId
                        view.evaluateJavascript(INSPECT_DOCUMENT_SCRIPT) { result ->
                            if (expectedLoad != session.pageLoadId || session.delivered) return@evaluateJavascript
                            val inspection = decodeJavascriptString(result).orEmpty()
                            when {
                                inspection.startsWith("feed:") || inspection.startsWith("plain:") -> {
                                    val isPlainText = inspection.startsWith("plain:")
                                    val length = inspection.substringAfter(':').toIntOrNull()
                                    if (length == null || length <= 0 || length > MAX_RSS_XML_LENGTH) {
                                        latestOnStatusChanged.value("订阅内容过大或无法读取，请稍后重试")
                                    } else {
                                        val expectedExtraction = ++session.extractionId
                                        continueXmlChunkReads(
                                            view = view,
                                            loadId = expectedLoad,
                                            extractionId = expectedExtraction,
                                            totalLength = length,
                                            session = session,
                                            output = StringBuilder(length),
                                            onStatus = { latestOnStatusChanged.value(it) },
                                            onComplete = { content ->
                                                if (isPlainText && !isPlainTextRssPayload(content)) {
                                                    latestOnStatusChanged.value("网站返回的纯文本不是 RSS/XML 内容")
                                                } else {
                                                    CookieManager.getInstance().flush()
                                                    Log.i(
                                                        "RssBrowserVerification",
                                                        "Verified feed XML extracted (${content.length} characters)"
                                                    )
                                                    session.delivered = true
                                                    latestOnFeedXmlReady.value(content)
                                                }
                                            }
                                        )
                                    }
                                }

                                inspection == "challenge" -> {
                                    val cookieLength = runCatching {
                                        CookieManager.getInstance().getCookie(url)?.length ?: 0
                                    }.getOrDefault(0)
                                    Log.i("RssBrowserVerification", "Challenge page detected; shared cookie length=$cookieLength")
                                    view.evaluateJavascript(CHALLENGE_CAPABILITY_DIAGNOSTIC_SCRIPT) { diagnostic ->
                                        val summary = decodeJavascriptString(diagnostic).orEmpty()
                                        Log.i("RssBrowserVerification", "WebView challenge capabilities: $summary")
                                    }
                                    latestOnStatusChanged.value("请完成滑动验证\n完成后自动继续")
                                }

                                inspection.startsWith("html:") -> {
                                    val inspectionParts = inspection.split(':', limit = 4)
                                    val documentSummary = inspectionParts.take(3).joinToString(":")
                                    Log.w("RssBrowserVerification", "WebView returned non-feed document: $documentSummary")
                                    if (BuildConfig.DEBUG) {
                                        Log.w(
                                            "RssBrowserVerification",
                                            "Debug-only non-feed body text: ${inspectionParts.getOrNull(3).orEmpty().take(500)}"
                                        )
                                    }
                                    latestOnStatusChanged.value("网站没有返回 RSS 内容，手表端安全验证未通过")
                                }

                                else ->
                                    latestOnStatusChanged.value("正在等待网页验证结果…")
                            }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            Log.w(
                                "RssBrowserVerification",
                                "WebView main-frame load error ${error.errorCode}"
                            )
                            latestOnStatusChanged.value("网页验证加载失败，请检查网络后重试")
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                            Log.w(
                                "RssBrowserVerification",
                                "WebView main-frame HTTP error ${errorResponse.statusCode}"
                            )
                            latestOnStatusChanged.value("网站暂时无法完成验证，请稍后重试")
                        }
                    }
                }
                if (challengeHtml.isNotBlank()) {
                    Log.i("RssBrowserVerification", "Using the subscription URL as the challenge page history URL")
                    loadDataWithBaseURL(url, challengeHtml, "text/html", "UTF-8", url)
                } else {
                    loadUrl(url)
                }
            }
        },
        update = { view ->
            if (view.tag != pageRequestKey) {
                view.tag = pageRequestKey
                if (challengeHtml.isNotBlank()) {
                    view.loadDataWithBaseURL(url, challengeHtml, "text/html", "UTF-8", url)
                } else {
                    view.loadUrl(url)
                }
            }
        }
    )

    DisposableEffect(webViewHolder) {
        onDispose {
            webViewHolder[0]?.apply {
                stopLoading()
                webViewClient = WebViewClient()
                destroy()
            }
            webViewHolder[0] = null
        }
    }
}

private fun continueXmlChunkReads(
    view: WebView,
    loadId: Int,
    extractionId: Int,
    totalLength: Int,
    session: RssWebViewSession,
    output: StringBuilder,
    onStatus: (String) -> Unit,
    onComplete: (String) -> Unit
) {
    if (loadId != session.pageLoadId || extractionId != session.extractionId || session.delivered) return
    if (output.length >= totalLength) {
        onComplete(output.toString())
        return
    }
    val start = output.length
    val end = (start + RSS_XML_CHUNK_LENGTH).coerceAtMost(totalLength)
    view.evaluateJavascript(
        "window.__watchRssVerificationXml ? " +
            "window.__watchRssVerificationXml.substring($start, $end) : ''"
    ) { encoded ->
        if (loadId != session.pageLoadId || extractionId != session.extractionId || session.delivered) return@evaluateJavascript
        val chunk = decodeJavascriptString(encoded).orEmpty()
        if (chunk.isEmpty()) {
            onStatus("无法读取订阅内容，请重试网页验证")
        } else {
            output.append(chunk)
            continueXmlChunkReads(
                view = view,
                loadId = loadId,
                extractionId = extractionId,
                totalLength = totalLength,
                session = session,
                output = output,
                onStatus = onStatus,
                onComplete = onComplete
            )
        }
    }
}

private fun decodeJavascriptString(value: String?): String? {
    if (value.isNullOrBlank() || value == "null") return null
    return runCatching { JSONArray("[$value]").getString(0) }.getOrNull()
}

internal fun isPlainTextRssPayload(content: String): Boolean {
    val trimmed = content.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
    return listOf("<?xml", "<rss", "<feed", "<rdf:RDF").any { trimmed.startsWith(it, ignoreCase = true) }
}

private const val INSPECT_DOCUMENT_SCRIPT = """(function(){var d=document,r=d.documentElement;if(!r)return'empty';var n=(r.localName||r.tagName||'').toLowerCase(),t=(d.contentType||'').toLowerCase();if(n==='rss'||n==='feed'||n.indexOf('rdf')>=0||(t.indexOf('xml')>=0&&n!=='html')){try{window.__watchRssVerificationXml=new XMLSerializer().serializeToString(d);return'feed:'+window.__watchRssVerificationXml.length}catch(e){try{window.__watchRssVerificationXml=r.outerHTML;return'feed:'+window.__watchRssVerificationXml.length}catch(e2){return'empty'}}}var c=!!d.getElementById('$WAF_CHALLENGE_ELEMENT_ID');if(c)return'challenge';if(t.indexOf('text/plain')>=0){window.__watchRssVerificationXml=((d.body&&d.body.textContent)||'').replace(/^\uFEFF/,'');return'plain:'+window.__watchRssVerificationXml.length}var b=((d.body&&d.body.innerText)||r.textContent||'').replace(/\s+/g,' ').trim().slice(0,500);return'html:'+n+':'+t+':false:'+b})()"""

private const val CHALLENGE_CAPABILITY_DIAGNOSTIC_SCRIPT = """(function(){var c2=document.createElement('canvas'),c3=document.createElement('canvas'),g=null,f=document.createElement('iframe'),i=false;try{g=c3.getContext('webgl')||c3.getContext('experimental-webgl')}catch(e){}try{document.body.appendChild(f);i=!!f.contentWindow;f.remove()}catch(e){}return'worker='+!!window.Worker+',promise='+!!window.Promise+',canvas2d='+!!c2.getContext('2d')+',webgl='+!!g+',cookieChars='+document.cookie.length+',iframe='+i})()"""
