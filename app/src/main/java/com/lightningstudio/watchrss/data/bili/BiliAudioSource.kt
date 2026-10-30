package com.lightningstudio.watchrss.data.bili

import com.lightningstudio.watchrss.sdk.bili.BiliPlayUrl
import java.net.URI

/** An independently addressable DASH audio track; never substitutes a video/muxed URL. */
data class BiliAudioSource(val url: String, val headers: Map<String, String>, val bandwidth: Int?)

internal fun selectBiliAudioTrack(playUrl: BiliPlayUrl): Pair<String, Int?>? =
    playUrl.dash?.audio.orEmpty()
        .sortedBy { it.bandwidth?.takeIf { bandwidth -> bandwidth > 0 } ?: Int.MAX_VALUE }
        .firstNotNullOfOrNull { track ->
            (listOfNotNull(track.baseUrl) + track.backupUrl).firstNotNullOfOrNull { raw ->
                val url = if (raw.startsWith("//")) "https:$raw" else raw
                runCatching { URI(url) }.getOrNull()?.takeIf {
                    it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null
                }?.let { url to track.bandwidth }
            }
        }
