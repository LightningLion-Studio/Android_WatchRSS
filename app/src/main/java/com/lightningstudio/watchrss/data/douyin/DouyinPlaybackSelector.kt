package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.data.settings.DouyinVideoCodecPreference
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoCodec
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoVariant
import kotlin.math.abs
import kotlin.math.max

internal const val DOUYIN_TARGET_VERTICAL_RESOLUTION = 540

internal fun applyPreferredPlayback(
    video: DouyinVideo,
    preference: DouyinVideoCodecPreference,
    h265Supported: Boolean
) {
    selectPreferredPlayUrl(
        variants = video.variants,
        fallbackPlayUrl = video.playUrl,
        preference = preference,
        h265Supported = h265Supported
    )?.let { video.playUrl = it }
}

internal fun applyPreferredPlayback(
    content: DouyinContent.Video,
    preference: DouyinVideoCodecPreference,
    h265Supported: Boolean
): DouyinContent.Video {
    val preferredPlayUrl = selectPreferredPlayUrl(
        variants = content.variants,
        fallbackPlayUrl = content.playUrl,
        preference = preference,
        h265Supported = h265Supported
    ) ?: content.playUrl
    return if (preferredPlayUrl == content.playUrl) {
        content
    } else {
        content.copy(playUrl = preferredPlayUrl)
    }
}

internal fun selectPreferredPlayUrl(
    variants: List<DouyinVideoVariant>,
    fallbackPlayUrl: String?,
    preference: DouyinVideoCodecPreference,
    h265Supported: Boolean
): String? {
    val preferredVariant = selectPreferredVariant(
        variants = variants,
        preference = preference,
        h265Supported = h265Supported
    )
    return preferredVariant?.playUrl ?: fallbackPlayUrl?.trim()?.takeIf { it.isNotEmpty() }
}

internal fun selectPreferredVariant(
    variants: List<DouyinVideoVariant>,
    preference: DouyinVideoCodecPreference,
    h265Supported: Boolean
): DouyinVideoVariant? {
    val effectivePreference = effectiveDouyinVideoCodecPreference(preference)
    val playableVariants = variants
        .asSequence()
        .filter { it.playUrl.isNotBlank() }
        .filter { it.codec != DouyinVideoCodec.H265 || h265Supported }
        .toList()
    if (playableVariants.isEmpty()) return null
    val preferredVariants = preferredCodecPlayableVariants(
        variants = playableVariants,
        preference = effectivePreference,
        h265Supported = h265Supported
    )
    return preferredVariants.minWithOrNull(
        compareBy<DouyinVideoVariant> { resolutionDistance(it) }
            .thenBy { codecPriority(it.codec, effectivePreference, h265Supported) }
            .thenByDescending { it.bitrate }
            .thenByDescending { max(it.width, it.height) }
    )
}

private fun preferredCodecPlayableVariants(
    variants: List<DouyinVideoVariant>,
    preference: DouyinVideoCodecPreference,
    h265Supported: Boolean
): List<DouyinVideoVariant> {
    val preferredCodec = when (preference) {
        DouyinVideoCodecPreference.AUTO -> null
        DouyinVideoCodecPreference.H264 -> DouyinVideoCodec.H264
        DouyinVideoCodecPreference.H265 -> DouyinVideoCodec.H265.takeIf { h265Supported }
    } ?: return variants
    val matchingVariants = variants.filter { it.codec == preferredCodec }
    return matchingVariants.ifEmpty { variants }
}

internal fun effectiveDouyinVideoCodecPreference(
    preference: DouyinVideoCodecPreference
): DouyinVideoCodecPreference {
    return if (
        preference == DouyinVideoCodecPreference.AUTO &&
        DouyinCodecRuntimePolicy.shouldPreferH264InAutoMode()
    ) {
        DouyinVideoCodecPreference.H264
    } else {
        preference
    }
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

private fun codecPriority(
    codec: DouyinVideoCodec,
    preference: DouyinVideoCodecPreference,
    h265Supported: Boolean
): Int {
    val preferredOrder = when (preference) {
        DouyinVideoCodecPreference.AUTO ->
            if (h265Supported) listOf(DouyinVideoCodec.H264, DouyinVideoCodec.H265, DouyinVideoCodec.UNKNOWN)
            else listOf(DouyinVideoCodec.H264, DouyinVideoCodec.UNKNOWN, DouyinVideoCodec.H265)
        DouyinVideoCodecPreference.H264 ->
            if (h265Supported) listOf(DouyinVideoCodec.H264, DouyinVideoCodec.H265, DouyinVideoCodec.UNKNOWN)
            else listOf(DouyinVideoCodec.H264, DouyinVideoCodec.UNKNOWN, DouyinVideoCodec.H265)
        DouyinVideoCodecPreference.H265 ->
            if (h265Supported) listOf(DouyinVideoCodec.H265, DouyinVideoCodec.H264, DouyinVideoCodec.UNKNOWN)
            else listOf(DouyinVideoCodec.H264, DouyinVideoCodec.UNKNOWN, DouyinVideoCodec.H265)
    }
    return preferredOrder.indexOf(codec).takeIf { it >= 0 } ?: preferredOrder.size
}

private val RESOLUTION_PATTERN = Regex("""(?<!\d)(2160|1440|1080|720|540|480)(?:p)?(?!\d)""")
