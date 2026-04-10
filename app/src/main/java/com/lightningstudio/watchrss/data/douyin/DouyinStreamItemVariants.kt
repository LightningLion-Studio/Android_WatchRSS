package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoCodec
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoVariant
import org.json.JSONArray
import org.json.JSONObject

internal fun encodeDouyinVariants(variants: List<DouyinVideoVariant>): JSONArray {
    val array = JSONArray()
    variants.forEach { variant ->
        array.put(
            JSONObject().apply {
                put("playUrl", variant.playUrl)
                put("codec", variant.codec.name)
                put("bitrate", variant.bitrate)
                put("width", variant.width)
                put("height", variant.height)
                put("definition", variant.definition.orEmpty())
                put("quality", variant.quality.orEmpty())
                put("gearName", variant.gearName.orEmpty())
            }
        )
    }
    return array
}

internal fun decodeDouyinVariants(raw: String?): List<DouyinVideoVariant> {
    if (raw.isNullOrBlank()) return emptyList()
    return decodeDouyinVariants(runCatching { JSONArray(raw) }.getOrNull())
}

internal fun decodeDouyinVariants(array: JSONArray?): List<DouyinVideoVariant> {
    if (array == null) return emptyList()
    return buildList(array.length()) {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val playUrl = item.optString("playUrl").trim()
            if (playUrl.isEmpty()) continue
            add(
                DouyinVideoVariant(
                    playUrl = playUrl,
                    codec = DouyinVideoCodec.entries.firstOrNull {
                        it.name == item.optString("codec").trim()
                    } ?: DouyinVideoCodec.UNKNOWN,
                    bitrate = item.optLong("bitrate", 0L),
                    width = item.optInt("width", 0),
                    height = item.optInt("height", 0),
                    definition = item.optString("definition").takeIf { it.isNotBlank() },
                    quality = item.optString("quality").takeIf { it.isNotBlank() },
                    gearName = item.optString("gearName").takeIf { it.isNotBlank() }
                )
            )
        }
    }
}
