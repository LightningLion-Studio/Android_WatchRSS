package com.lightningstudio.watchrss.sdk.bili

import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliAuthAndActionRepairTest {
    @Test
    fun refreshWebCookies_skipsRefreshWhenCookieInfoSaysNo() = runTest {
        val store = TestBiliAccountStore(
            BiliAccount(
                cookies = mapOf(
                    "SESSDATA" to "sess",
                    "bili_jct" to "csrf"
                ),
                refreshToken = "refresh-token"
            )
        )
        val http = TestBiliHttpService().apply {
            enqueue(
                method = "GET",
                urlContains = "/x/passport-login/web/cookie/info",
                body = """{"code":0,"data":{"refresh":false,"timestamp":1710000000000}}"""
            )
        }
        val client = BiliClient(BiliSdkConfig(), store, http)

        val result = client.auth.refreshWebCookies(forceRefresh = false)
        val account = store.read()

        assertTrue(result.isSuccess)
        assertFalse(result.refreshed)
        assertTrue(result.checked)
        assertEquals(1710000000000L, account?.cookieRefreshCheckedAtMillis)
        assertEquals(1, http.recordedRequests.size)
    }

    @Test
    fun refreshWebCookies_forceRefreshUpdatesCookiesAndIdentityFields() = runTest {
        val store = TestBiliAccountStore(
            BiliAccount(
                cookies = mapOf(
                    "SESSDATA" to "sess-old",
                    "bili_jct" to "csrf-old"
                ),
                refreshToken = "refresh-old"
            )
        )
        val http = TestBiliHttpService().apply {
            enqueue(
                method = "GET",
                urlContains = "/correspond/1/",
                body = """<html><body><div id="1-name">refresh-csrf-token</div></body></html>"""
            )
            enqueue(
                method = "POST",
                urlContains = "/x/passport-login/web/cookie/refresh",
                headers = headersOfSetCookies(
                    "SESSDATA=sess-new",
                    "bili_jct=csrf-new",
                    "sid=sid-new",
                    "DedeUserID=123",
                    "DedeUserID__ckMd5=ckmd5"
                ),
                body = """{"code":0,"data":{"refresh_token":"refresh-new"}}"""
            )
            enqueue(
                method = "POST",
                urlContains = "/x/passport-login/web/confirm/refresh",
                body = """{"code":0}"""
            )
            enqueue(
                method = "GET",
                urlContains = "/x/frontend/finger/spi",
                body = """{"code":0,"data":{"b_3":"buvid3-new","b_4":"buvid4-new"}}"""
            )
            enqueue(
                method = "GET",
                urlContains = "https://www.bilibili.com/",
                headers = headersOfSetCookies(
                    "buvid3=buvid3-cookie",
                    "b_nut=bnut-new"
                ),
                body = "<html></html>"
            )
            enqueue(
                method = "GET",
                urlContains = "/x/web-interface/nav",
                body = """
                    {"code":0,"data":{"wbi_img":{
                        "img_url":"https://i0.hdslb.com/bfs/wbi/img_key.png",
                        "sub_url":"https://i0.hdslb.com/bfs/wbi/sub_key.png"
                    }}}
                """.trimIndent()
            )
            enqueue(
                method = "POST",
                urlContains = "GenWebTicket",
                body = """{"code":0,"data":{"ticket":"ticket-new"}}"""
            )
        }
        val client = BiliClient(BiliSdkConfig(), store, http)

        val result = client.auth.refreshWebCookies(forceRefresh = true)
        val account = store.read()

        assertTrue(result.isSuccess)
        assertTrue(result.refreshed)
        assertEquals("refresh-new", account?.refreshToken)
        assertEquals("sess-new", account?.cookies?.get("SESSDATA"))
        assertEquals("csrf-new", account?.cookies?.get("bili_jct"))
        assertEquals("sid-new", account?.cookies?.get("sid"))
        assertEquals("123", account?.cookies?.get("DedeUserID"))
        assertEquals("ckmd5", account?.cookies?.get("DedeUserID__ckMd5"))
        assertEquals("buvid3-new", account?.buvid3)
        assertEquals("buvid4-new", account?.buvid4)
        assertEquals("bnut-new", account?.bNut)
        assertEquals("ticket-new", account?.biliTicket)
        assertNotNull(account?.cookieRefreshCheckedAtMillis)
        assertNotNull(account?.buvidFetchedAtMillis)
        assertNotNull(account?.biliTicketFetchedAtMillis)
    }

    @Test
    fun actionRepair_prefetchesMissingBuvidAndTicketBeforeAction() = runTest {
        val store = TestBiliAccountStore(
            BiliAccount(
                cookies = mapOf(
                    "SESSDATA" to "sess",
                    "bili_jct" to "csrf"
                )
            )
        )
        val http = TestBiliHttpService().apply {
            enqueue(
                method = "GET",
                urlContains = "/x/frontend/finger/spi",
                body = """{"code":0,"data":{"b_3":"buvid3-ready","b_4":"buvid4-ready"}}"""
            )
            enqueue(
                method = "GET",
                urlContains = "https://www.bilibili.com/",
                headers = headersOfSetCookies(
                    "buvid3=buvid3-cookie",
                    "b_nut=bnut-ready"
                ),
                body = "<html></html>"
            )
            enqueue(
                method = "POST",
                urlContains = "GenWebTicket",
                body = """{"code":0,"data":{"ticket":"ticket-ready"}}"""
            )
        }
        val client = BiliClient(BiliSdkConfig(), store, http)
        val repair = BiliWebActionRepair(client, nowMillis = { 1720000000000L })
        var attempts = 0

        val result = repair.execute("like") {
            attempts += 1
            BiliResult(code = 0, data = Unit)
        }
        val account = store.read()

        assertTrue(result.isSuccess)
        assertEquals(1, attempts)
        assertEquals("buvid3-ready", account?.buvid3)
        assertEquals("buvid4-ready", account?.buvid4)
        assertEquals("bnut-ready", account?.bNut)
        assertEquals("ticket-ready", account?.biliTicket)
    }

    @Test
    fun actionRepair_retriesOnceAfterCsrfFailureAndRefresh() = runTest {
        val now = 1730000000000L
        val store = TestBiliAccountStore(
            BiliAccount(
                cookies = mapOf(
                    "SESSDATA" to "sess-old",
                    "bili_jct" to "csrf-old",
                    "sid" to "sid-old",
                    "DedeUserID" to "123",
                    "DedeUserID__ckMd5" to "ckmd5",
                    "buvid3" to "buvid3-old",
                    "buvid4" to "buvid4-old",
                    "b_nut" to "bnut-old"
                ),
                refreshToken = "refresh-old",
                biliTicket = "ticket-old",
                biliTicketFetchedAtMillis = now,
                cookieRefreshCheckedAtMillis = now
            )
        )
        val http = TestBiliHttpService().apply {
            enqueue(
                method = "GET",
                urlContains = "/correspond/1/",
                body = """<html><body><div id="1-name">retry-refresh-csrf</div></body></html>"""
            )
            enqueue(
                method = "POST",
                urlContains = "/x/passport-login/web/cookie/refresh",
                headers = headersOfSetCookies(
                    "SESSDATA=sess-new",
                    "bili_jct=csrf-new",
                    "sid=sid-new",
                    "DedeUserID=123",
                    "DedeUserID__ckMd5=ckmd5"
                ),
                body = """{"code":0,"data":{"refresh_token":"refresh-new"}}"""
            )
            enqueue(
                method = "POST",
                urlContains = "/x/passport-login/web/confirm/refresh",
                body = """{"code":0}"""
            )
            enqueue(
                method = "GET",
                urlContains = "/x/frontend/finger/spi",
                body = """{"code":0,"data":{"b_3":"buvid3-new","b_4":"buvid4-new"}}"""
            )
            enqueue(
                method = "GET",
                urlContains = "https://www.bilibili.com/",
                headers = headersOfSetCookies(
                    "buvid3=buvid3-cookie",
                    "b_nut=bnut-new"
                ),
                body = "<html></html>"
            )
            enqueue(
                method = "GET",
                urlContains = "/x/web-interface/nav",
                body = """
                    {"code":0,"data":{"wbi_img":{
                        "img_url":"https://i0.hdslb.com/bfs/wbi/img_retry.png",
                        "sub_url":"https://i0.hdslb.com/bfs/wbi/sub_retry.png"
                    }}}
                """.trimIndent()
            )
            enqueue(
                method = "POST",
                urlContains = "GenWebTicket",
                body = """{"code":0,"data":{"ticket":"ticket-new"}}"""
            )
        }
        val client = BiliClient(BiliSdkConfig(), store, http)
        val repair = BiliWebActionRepair(client, nowMillis = { now })
        var attempts = 0

        val result = repair.execute("like") {
            attempts += 1
            when (attempts) {
                1 -> BiliResult(code = -111, message = "csrf failed")
                else -> BiliResult(code = 0, data = Unit)
            }
        }
        val account = store.read()

        assertTrue(result.isSuccess)
        assertEquals(2, attempts)
        assertEquals("refresh-new", account?.refreshToken)
        assertEquals("sess-new", account?.cookies?.get("SESSDATA"))
        assertEquals("csrf-new", account?.cookies?.get("bili_jct"))
        assertEquals("ticket-new", account?.biliTicket)
    }

    private fun headersOfSetCookies(vararg pairs: String): Headers {
        val builder = Headers.Builder()
        pairs.forEach { builder.add("Set-Cookie", "$it; Path=/; Domain=bilibili.com") }
        return builder.build()
    }
}
