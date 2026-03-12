package com.lightningstudio.watchrss.sdk.bili

data class BiliResult<T>(
    val code: Int,
    val message: String? = null,
    val data: T? = null
) {
    val isSuccess: Boolean
        get() = code == 0
}
