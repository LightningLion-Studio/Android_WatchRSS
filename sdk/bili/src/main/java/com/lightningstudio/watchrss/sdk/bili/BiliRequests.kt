package com.lightningstudio.watchrss.sdk.bili

internal suspend fun BiliClient.csrfToken(): String? = accountStore?.read()?.csrfToken()

internal suspend fun BiliClient.accessKey(): String? = accountStore?.read()?.accessToken

internal suspend fun BiliClient.signedAppParams(
    params: Map<String, String>,
    includeAccessKey: Boolean = true,
    includeTs: Boolean = true
): Map<String, String> {
    val base = BiliParams.defaultAppParams(config).toMutableMap()
    base.putAll(params)
    if (includeTs && !base.containsKey("ts")) {
        base["ts"] = (System.currentTimeMillis() / 1000).toString()
    }
    if (includeAccessKey && !base.containsKey("access_key")) {
        val accessKey = accessKey()
        if (!accessKey.isNullOrBlank()) {
            base["access_key"] = accessKey
        }
    }
    return BiliSigners.signApp(base, config.appKey, config.appSec)
}

internal suspend fun BiliClient.signedWbiParams(params: Map<String, String>): Map<String, String> {
    var account = accountStore?.read()
    var imgKey = account?.wbiImgKey
    var subKey = account?.wbiSubKey
    if (imgKey.isNullOrBlank() || subKey.isNullOrBlank()) {
        identity.fetchWbiKeys()
        account = accountStore?.read()
        imgKey = account?.wbiImgKey
        subKey = account?.wbiSubKey
    }
    return if (!imgKey.isNullOrBlank() && !subKey.isNullOrBlank()) {
        BiliSigners.signWbi(params, imgKey, subKey)
    } else {
        params
    }
}
