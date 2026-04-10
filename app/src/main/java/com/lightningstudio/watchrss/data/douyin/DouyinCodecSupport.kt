package com.lightningstudio.watchrss.data.douyin

import android.media.MediaCodecList

internal object DouyinCodecSupport {
    @Volatile
    private var cachedHevcSupport: Boolean? = null

    fun isH265Supported(): Boolean {
        cachedHevcSupport?.let { return it }
        return synchronized(this) {
            cachedHevcSupport ?: detectHevcDecoder().also { cachedHevcSupport = it }
        }
    }

    private fun detectHevcDecoder(): Boolean {
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codecInfo ->
                !codecInfo.isEncoder && codecInfo.supportedTypes.any { type ->
                    type.equals("video/hevc", ignoreCase = true)
                }
            }
        }.getOrDefault(false)
    }
}
