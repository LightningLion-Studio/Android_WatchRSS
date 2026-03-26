package com.lightningstudio.watchrss.sdk.bili

data class BiliResult<T>(
    val code: Int,
    val message: String? = null,
    val data: T? = null,
    val httpCode: Int? = null,
    val requestMode: String? = null
) {
    val isSuccess: Boolean
        get() = code == 0
}
