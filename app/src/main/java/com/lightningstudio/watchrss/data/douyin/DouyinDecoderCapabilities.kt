package com.lightningstudio.watchrss.data.douyin

import android.media.MediaCodecList
import android.media.MediaFormat
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoCodec
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoVariant

internal object DouyinDecoderCapabilities {
    fun supports(variant: DouyinVideoVariant): Boolean {
        if (variant.codec != DouyinVideoCodec.H265) return true
        if (DouyinCodecRuntimePolicy.shouldPreferH264InAutoMode()) return false
        if (variant.width <= 0 || variant.height <= 0) return false
        return runCatching {
            val format = MediaFormat.createVideoFormat("video/hevc", variant.width, variant.height)
            MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) != null
        }.getOrDefault(false)
    }
}
