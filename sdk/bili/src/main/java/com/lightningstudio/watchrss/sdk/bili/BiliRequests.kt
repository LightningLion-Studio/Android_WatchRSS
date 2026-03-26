package com.lightningstudio.watchrss.sdk.bili

internal suspend fun BiliClient.csrfToken(): String? = accountStore?.read()?.csrfToken()

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
