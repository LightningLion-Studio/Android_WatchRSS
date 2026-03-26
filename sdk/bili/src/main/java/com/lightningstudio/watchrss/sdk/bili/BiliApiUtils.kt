package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

internal data class BiliStatus(
    val code: Int,
    val message: String? = null,
    val data: JsonElement? = null,
    val httpCode: Int? = null,
    val requestMode: String? = null
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

internal fun parseBiliStatus(
    response: BiliHttpResult,
    requestMode: String? = null
): BiliStatus {
    val parsed = parseBiliStatus(response.body)
    if (parsed.code == -1 && parsed.message == "invalid_json" && response.code !in 200..299) {
        return parsed.copy(
            code = -response.code,
            message = "HTTP ${response.code}",
            httpCode = response.code,
            requestMode = requestMode
        )
    }
    return parsed.copy(
        httpCode = response.code,
        requestMode = requestMode
    )
}
