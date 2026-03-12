package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.Serializable

@Serializable
data class BiliAccount(
    val cookies: Map<String, String> = emptyMap(),
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val appRefreshToken: String? = null,
    val buvid3: String? = null,
    val buvid4: String? = null,
    val bNut: String? = null,
    val biliTicket: String? = null,
    val wbiImgKey: String? = null,
    val wbiSubKey: String? = null,
    val updatedAtMillis: Long? = null
) {
    fun csrfToken(): String? = cookies["bili_jct"]

    fun cookieHeader(): String = cookies.entries.joinToString("; ") { (k, v) -> "$k=$v" }
}
