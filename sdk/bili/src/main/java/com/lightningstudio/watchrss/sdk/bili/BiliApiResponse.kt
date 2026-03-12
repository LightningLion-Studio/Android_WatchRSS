package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.Serializable

@Serializable
data class BiliApiResponse<T>(
    val code: Int,
    val message: String? = null,
    val data: T? = null
)
