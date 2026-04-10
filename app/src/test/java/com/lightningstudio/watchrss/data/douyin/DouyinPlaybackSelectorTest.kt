package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.data.settings.DouyinVideoCodecPreference
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoCodec
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DouyinPlaybackSelectorTest {
    @Before
    fun setUp() {
        DouyinCodecRuntimePolicy.resetForTests()
    }

    @Test
    fun selectPreferredVariant_autoPrefers540pH264WhenSupported() {
        val selected = selectPreferredVariant(
            variants = listOf(
                variant(codec = DouyinVideoCodec.H264, bitrate = 1_497_113L, definition = "540p", quality = "normal_540_0"),
                variant(codec = DouyinVideoCodec.H265, bitrate = 1_269_141L, definition = "540p", quality = "adapt_540_1"),
                variant(codec = DouyinVideoCodec.H264, bitrate = 2_000_340L, definition = "1080p", width = 1920, height = 1080)
            ),
            preference = DouyinVideoCodecPreference.AUTO,
            h265Supported = true
        )

        assertEquals(DouyinVideoCodec.H264, selected?.codec)
        assertEquals("https://example.com/h264-540.mp4", selected?.playUrl)
    }

    @Test
    fun selectPreferredVariant_autoFallsBackTo540pH264WhenH265Unsupported() {
        val selected = selectPreferredVariant(
            variants = listOf(
                variant(codec = DouyinVideoCodec.H265, bitrate = 1_269_141L, definition = "540p", quality = "adapt_540_1"),
                variant(codec = DouyinVideoCodec.H264, bitrate = 1_497_113L, definition = "540p", quality = "normal_540_0")
            ),
            preference = DouyinVideoCodecPreference.AUTO,
            h265Supported = false
        )

        assertEquals(DouyinVideoCodec.H264, selected?.codec)
        assertEquals("https://example.com/h264-540.mp4", selected?.playUrl)
    }

    @Test
    fun selectPreferredVariant_h264PrefersH264Within540p() {
        val selected = selectPreferredVariant(
            variants = listOf(
                variant(codec = DouyinVideoCodec.H265, bitrate = 1_269_141L, definition = "540p"),
                variant(codec = DouyinVideoCodec.H264, bitrate = 1_013_083L, definition = "540p")
            ),
            preference = DouyinVideoCodecPreference.H264,
            h265Supported = true
        )

        assertEquals(DouyinVideoCodec.H264, selected?.codec)
    }

    @Test
    fun selectPreferredVariant_prefersClosestResolutionWhen540pMissing() {
        val selected = selectPreferredVariant(
            variants = listOf(
                variant(
                    codec = DouyinVideoCodec.H264,
                    bitrate = 2_000_340L,
                    definition = "1080p",
                    width = 1920,
                    height = 1080,
                    suffix = "h264-1080"
                ),
                variant(
                    codec = DouyinVideoCodec.H264,
                    bitrate = 1_600_000L,
                    definition = "720p",
                    width = 1280,
                    height = 720,
                    suffix = "h264-720"
                )
            ),
            preference = DouyinVideoCodecPreference.AUTO,
            h265Supported = true
        )

        assertEquals("https://example.com/h264-720.mp4", selected?.playUrl)
    }

    @Test
    fun selectPreferredVariant_autoForcedToH264PrefersH264AfterFrequentHevcFailures() {
        DouyinCodecRuntimePolicy.recordAutoHevcAttempt()
        DouyinCodecRuntimePolicy.recordAutoHevcFailure()
        DouyinCodecRuntimePolicy.recordAutoHevcAttempt()
        DouyinCodecRuntimePolicy.recordAutoHevcFailure()

        val selected = selectPreferredVariant(
            variants = listOf(
                variant(codec = DouyinVideoCodec.H265, bitrate = 1_269_141L, definition = "540p", suffix = "h265-540"),
                variant(
                    codec = DouyinVideoCodec.H264,
                    bitrate = 1_600_000L,
                    definition = "720p",
                    width = 1280,
                    height = 720,
                    suffix = "h264-720"
                )
            ),
            preference = DouyinVideoCodecPreference.AUTO,
            h265Supported = true
        )

        assertEquals(DouyinVideoCodec.H264, selected?.codec)
        assertEquals("https://example.com/h264-720.mp4", selected?.playUrl)
    }

    @Test
    fun selectPreferredPlayUrl_fallsBackToOriginalUrlWhenNoVariants() {
        val selected = selectPreferredPlayUrl(
            variants = emptyList(),
            fallbackPlayUrl = "https://example.com/original.mp4",
            preference = DouyinVideoCodecPreference.AUTO,
            h265Supported = true
        )

        assertEquals("https://example.com/original.mp4", selected)
    }

    @Test
    fun selectPreferredVariant_returnsNullForEmptyPlayableSet() {
        val selected = selectPreferredVariant(
            variants = listOf(
                DouyinVideoVariant(
                    playUrl = "",
                    codec = DouyinVideoCodec.H264,
                    bitrate = 0L,
                    width = 0,
                    height = 0
                )
            ),
            preference = DouyinVideoCodecPreference.AUTO,
            h265Supported = true
        )

        assertNull(selected)
    }

    private fun variant(
        codec: DouyinVideoCodec,
        bitrate: Long,
        definition: String,
        quality: String = definition,
        width: Int = 1024,
        height: Int = 576,
        suffix: String = when (codec) {
            DouyinVideoCodec.H264 -> "h264-540"
            DouyinVideoCodec.H265 -> "h265-540"
            DouyinVideoCodec.UNKNOWN -> "unknown-540"
        }
    ): DouyinVideoVariant {
        return DouyinVideoVariant(
            playUrl = "https://example.com/$suffix.mp4",
            codec = codec,
            bitrate = bitrate,
            width = width,
            height = height,
            definition = definition,
            quality = quality,
            gearName = quality
        )
    }
}
