package com.lightningstudio.watchrss.phoneconnection.bluetooth

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.StatFs
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject

object WatchMediaCapabilities {
    fun inspect(context: Context): JSONObject {
        val display = context.getSystemService(WindowManager::class.java).defaultDisplay
        val metrics = android.util.DisplayMetrics()
        display.getRealMetrics(metrics)
        val refreshRate = display.refreshRate
        val stat = StatFs(context.filesDir.absolutePath)
        return JSONObject().apply {
            put("widthPx", metrics.widthPixels)
            put("heightPx", metrics.heightPixels)
            put("refreshRateHz", refreshRate.toDouble())
            put("availableBytes", stat.availableBytes)
            put("videoDecoders", decoderArray(metrics.widthPixels, metrics.heightPixels, refreshRate))
        }
    }

    private fun decoderArray(width: Int, height: Int, refreshRate: Float): JSONArray {
        val result = JSONArray()
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .asSequence()
            .filterNot(MediaCodecInfo::isEncoder)
            .forEach { info ->
                info.supportedTypes
                    .filter { it.startsWith("video/") }
                    .forEach { mime ->
                        val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull()
                            ?: return@forEach
                        val video = caps.videoCapabilities ?: return@forEach
                        val profiles = JSONArray().apply {
                            caps.profileLevels.forEach {
                                put(JSONObject().apply {
                                    put("profile", it.profile)
                                    put("level", it.level)
                                })
                            }
                        }
                        result.put(JSONObject().apply {
                            put("name", info.name)
                            put("mime", mime)
                            put("hardwareAccelerated", info.isHardwareAccelerated)
                            put("profiles", profiles)
                            put("maxWidth", video.supportedWidths.upper)
                            put("maxHeight", video.supportedHeights.upper)
                            put(
                                "maxFrameRate",
                                runCatching {
                                    video.getSupportedFrameRatesFor(width, height).upper
                                }.getOrDefault(refreshRate.toDouble())
                            )
                        })
                    }
            }
        return result
    }
}
