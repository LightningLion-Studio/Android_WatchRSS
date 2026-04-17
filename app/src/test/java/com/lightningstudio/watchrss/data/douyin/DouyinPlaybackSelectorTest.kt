package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoCodec
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoVariant
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DouyinPlaybackSelectorTest {
    @Test
    fun selectPreferredVariant_prefers540pH264() {
        val selected = selectPreferredVariant(
            variants = listOf(
                variant(codec = DouyinVideoCodec.H264, bitrate = 1_497_113L, definition = "540p", quality = "normal_540_0"),
                variant(codec = DouyinVideoCodec.H265, bitrate = 1_269_141L, definition = "540p", quality = "adapt_540_1"),
                variant(codec = DouyinVideoCodec.H264, bitrate = 2_000_340L, definition = "1080p", width = 1920, height = 1080)
            )
        )

        assertEquals(DouyinVideoCodec.H264, selected?.codec)
        assertEquals("https://example.com/h264-540.mp4", selected?.playUrl)
    }

    @Test
    fun selectPreferredVariant_ignoresH265WhenH264Exists() {
        val selected = selectPreferredVariant(
            variants = listOf(
                variant(codec = DouyinVideoCodec.H265, bitrate = 1_269_141L, definition = "540p", quality = "adapt_540_1"),
                variant(codec = DouyinVideoCodec.H264, bitrate = 1_497_113L, definition = "540p", quality = "normal_540_0")
            )
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
            )
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
            )
        )

        assertEquals("https://example.com/h264-720.mp4", selected?.playUrl)
    }

    @Test
    fun selectPreferredVariant_skipsH265AndFallsBackToUnknownCodec() {
        val selected = selectPreferredVariant(
            variants = listOf(
                variant(codec = DouyinVideoCodec.H265, bitrate = 1_269_141L, definition = "540p", suffix = "h265-540"),
                variant(
                    codec = DouyinVideoCodec.UNKNOWN,
                    bitrate = 1_600_000L,
                    definition = "720p",
                    width = 1280,
                    height = 720,
                    suffix = "unknown-720"
                )
            )
        )

        assertEquals(DouyinVideoCodec.UNKNOWN, selected?.codec)
        assertEquals("https://example.com/unknown-720.mp4", selected?.playUrl)
    }

    @Test
    fun selectPreferredPlayUrl_fallsBackToOriginalUrlWhenNoVariants() {
        val selected = selectPreferredPlayUrl(
            variants = emptyList(),
            fallbackPlayUrl = "https://example.com/original.mp4"
        )

        assertEquals("https://example.com/original.mp4", selected)
    }

    @Test
    fun selectPreferredPlayUrl_doesNotFallbackToKnownH265Url() {
        val selected = selectPreferredPlayUrl(
            variants = listOf(
                variant(codec = DouyinVideoCodec.H265, bitrate = 1_269_141L, definition = "540p", suffix = "h265-540")
            ),
            fallbackPlayUrl = "https://example.com/h265-540.mp4"
        )

        assertNull(selected)
    }

    @Test
    fun applyPreferredPlayback_clearsKnownH265OnlyVideoUrl() {
        val video = DouyinVideo().apply {
            playUrl = "https://example.com/h265-540.mp4"
            variants = listOf(
                variant(codec = DouyinVideoCodec.H265, bitrate = 1_269_141L, definition = "540p", suffix = "h265-540")
            )
        }

        applyPreferredPlayback(video)

        assertNull(video.playUrl)
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
            )
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
