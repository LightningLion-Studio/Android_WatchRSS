package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.Json

internal val biliJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
