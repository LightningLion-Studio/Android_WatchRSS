package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoCodec
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoVariant
import kotlin.math.abs
import kotlin.math.max

internal const val DOUYIN_TARGET_VERTICAL_RESOLUTION = 540

internal fun applyPreferredPlayback(video: DouyinVideo) {
    video.playUrl = selectPreferredPlayUrl(
        variants = video.variants,
        fallbackPlayUrl = video.playUrl
    )
}

internal fun applyPreferredPlayback(content: DouyinContent.Video): DouyinContent.Video {
    val preferredPlayUrl = selectPreferredPlayUrl(
        variants = content.variants,
        fallbackPlayUrl = content.playUrl
    ).orEmpty()
    return if (preferredPlayUrl == content.playUrl) {
        content
    } else {
        content.copy(playUrl = preferredPlayUrl)
    }
}

internal fun selectPreferredPlayUrl(
    variants: List<DouyinVideoVariant>,
    fallbackPlayUrl: String?
): String? {
    val preferredVariant = selectPreferredVariant(variants = variants)
    val fallback = fallbackPlayUrl?.trim()?.takeIf { it.isNotEmpty() }
    return preferredVariant?.playUrl ?: fallback?.takeUnless {
        variants.any { variant ->
            variant.codec == DouyinVideoCodec.H265 && variant.playUrl.trim() == fallback
        }
    }
}

internal fun selectPreferredVariant(variants: List<DouyinVideoVariant>): DouyinVideoVariant? {
    val playableVariants = variants
        .asSequence()
        .filter { it.playUrl.isNotBlank() }
        .filter { it.codec != DouyinVideoCodec.H265 }
        .toList()
    if (playableVariants.isEmpty()) return null
    val preferredVariants = playableVariants
        .filter { it.codec == DouyinVideoCodec.H264 }
        .ifEmpty { playableVariants }
    return preferredVariants.minWithOrNull(
        compareBy<DouyinVideoVariant> { resolutionDistance(it) }
            .thenBy { codecPriority(it.codec) }
            .thenByDescending { it.bitrate }
            .thenByDescending { max(it.width, it.height) }
    )
}

private fun resolutionDistance(variant: DouyinVideoVariant): Int {
    val normalized = normalizedVerticalResolution(variant) ?: return Int.MAX_VALUE / 4
    return abs(normalized - DOUYIN_TARGET_VERTICAL_RESOLUTION)
}

private fun normalizedVerticalResolution(variant: DouyinVideoVariant): Int? {
    extractLabeledResolution(variant)?.let { return it }
    val shortSide = listOf(variant.width, variant.height)
        .filter { it > 0 }
        .minOrNull()
        ?: return null
    return when {
        shortSide in 540..576 -> 540
        shortSide in 720..768 -> 720
        shortSide in 1080..1152 -> 1080
        shortSide in 1440..1536 -> 1440
        shortSide in 2160..2304 -> 2160
        else -> shortSide
    }
}

private fun extractLabeledResolution(variant: DouyinVideoVariant): Int? {
    val label = listOfNotNull(variant.definition, variant.quality, variant.gearName)
        .joinToString(separator = " ")
        .lowercase()
    val matches = RESOLUTION_PATTERN.findAll(label).toList()
    if (matches.isEmpty()) return null
    return matches
        .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
        .firstOrNull()
}

private fun codecPriority(codec: DouyinVideoCodec): Int {
    return when (codec) {
        DouyinVideoCodec.H264 -> 0
        DouyinVideoCodec.UNKNOWN -> 1
        DouyinVideoCodec.H265 -> 2
    }
}

private val RESOLUTION_PATTERN = Regex("""(?<!\d)(2160|1440|1080|720|540|480)(?:p)?(?!\d)""")
