package com.lightningstudio.watchrss.data.bili

import com.lightningstudio.watchrss.sdk.bili.BiliAccount
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.sdk.bili.BiliSdkConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliActionPolicyTest {
    @Test
    fun shouldPreferAppAction_requiresConfiguredKeys_andAccessToken() {
        val account = BiliAccount(accessToken = "token")

        assertTrue(
            shouldPreferAppAction(
                config = BiliSdkConfig(appKey = "appKey", appSec = "appSec"),
                account = account
            )
        )
        assertFalse(
            shouldPreferAppAction(
                config = BiliSdkConfig(appKey = "", appSec = ""),
                account = account
            )
        )
        assertFalse(
            shouldPreferAppAction(
                config = BiliSdkConfig(appKey = "appKey", appSec = "appSec"),
                account = BiliAccount()
            )
        )
    }

    @Test
    fun hasWebActionAuth_requiresSessdata_andCsrf() {
        assertTrue(
            hasWebActionAuth(
                BiliAccount(
                    cookies = mapOf(
                        "SESSDATA" to "sess",
                        "bili_jct" to "csrf"
                    )
                )
            )
        )
        assertFalse(hasWebActionAuth(BiliAccount(cookies = mapOf("SESSDATA" to "sess"))))
        assertFalse(hasWebActionAuth(BiliAccount(cookies = mapOf("bili_jct" to "csrf"))))
    }

    @Test
    fun shouldRetryActionViaWeb_matchesAppAuthFailures_only() {
        assertTrue(
            shouldRetryActionViaWeb(
                BiliResult<Unit>(
                    code = -401,
                    requestMode = "app"
                )
            )
        )
        assertTrue(
            shouldRetryActionViaWeb(
                BiliResult<Unit>(
                    code = -1,
                    httpCode = 401,
                    requestMode = "app"
                )
            )
        )
        assertFalse(
            shouldRetryActionViaWeb(
                BiliResult<Unit>(
                    code = -401,
                    requestMode = "web"
                )
            )
        )
        assertFalse(
            shouldRetryActionViaWeb(
                BiliResult<Unit>(
                    code = 9001,
                    requestMode = "app"
                )
            )
        )
    }
}
