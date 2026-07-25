package com.lightningstudio.watchrss.sdk.bili

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliPlayTest {
    @Test
    fun fetchMp4Url_fallsBackToLegacyEndpointWhenWbiReturnsVoucherOnly() = runTest {
        val store = TestBiliAccountStore(
            BiliAccount(
                wbiImgKey = "7cd084941338484aae1ad9425b84077c",
                wbiSubKey = "4932caff0ff746eab6f01bf08b70ac45"
            )
        )
        val http = TestBiliHttpService().apply {
            enqueue(
                method = "GET",
                urlContains = "/x/player/wbi/playurl",
                body = """{"code":0,"message":"OK","data":{"v_voucher":"voucher_test"}}"""
            )
            enqueue(
                method = "GET",
                urlContains = "/x/player/playurl",
                body = """
                    {
                      "code": 0,
                      "message": "OK",
                      "data": {
                        "quality": 16,
                        "format": "mp4",
                        "durl": [
                          {
                            "order": 1,
                            "length": 126833,
                            "size": 9300661,
                            "url": "https://example.com/video.mp4"
                          }
                        ]
                      }
                    }
                """.trimIndent()
            )
        }
        val client = BiliClient(BiliSdkConfig(), store, http)

        val result = client.play.fetchMp4Url(
            cid = 37347986442L,
            bvid = "BV1bDDEBZEp8",
            qn = 32
        )

        assertTrue(result.isSuccess)
        assertEquals("https://example.com/video.mp4", result.data?.durl?.single()?.url)
        assertEquals(2, http.recordedRequests.size)
        assertTrue(http.recordedRequests[0].url.contains("/x/player/wbi/playurl"))
        assertTrue(http.recordedRequests[0].url.contains("w_rid="))
        assertTrue(http.recordedRequests[1].url.contains("/x/player/playurl"))
        assertTrue(!http.recordedRequests[1].url.contains("w_rid="))
        assertTrue(http.recordedRequests[1].url.contains("platform=html5"))
    }
}
