package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

internal data class BiliStatus(
    val code: Int,
    val message: String? = null,
    val data: JsonElement? = null
)

internal fun parseBiliStatus(body: String): BiliStatus {
    return runCatching {
        val json = biliJson.parseToJsonElement(body).jsonObject
        BiliStatus(
            code = json.intOrNull("code") ?: -1,
            message = json.stringOrNull("message"),
            data = json["data"]
        )
    }.getOrElse {
        BiliStatus(-1, "invalid_json")
    }
}
