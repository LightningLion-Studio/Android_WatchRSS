package com.lightningstudio.watchrss.data.bili

import com.lightningstudio.watchrss.sdk.bili.BiliAccount
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.sdk.bili.BiliSdkConfig

internal fun shouldPreferAppAction(config: BiliSdkConfig, account: BiliAccount?): Boolean {
    return config.appKey.isNotBlank() &&
        config.appSec.isNotBlank() &&
        !account?.accessToken.isNullOrBlank()
}

internal fun hasWebActionAuth(account: BiliAccount?): Boolean {
    val cookies = account?.cookies.orEmpty()
    return !cookies["SESSDATA"].isNullOrBlank() && !cookies["bili_jct"].isNullOrBlank()
}

internal fun shouldRetryActionViaWeb(result: BiliResult<*>): Boolean {
    if (result.requestMode != "app") return false
    if (result.httpCode == 401) return true
    return result.code in setOf(-2, -3, -101, -401, -658)
}
