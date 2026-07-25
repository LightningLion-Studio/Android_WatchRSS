package com.lightningstudio.watchrss.data.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudTextSegmenterTest {
    @Test
    fun segmentDoesNotApplyLegacyArticleLimit() {
        val text = "开头。" + "甲".repeat(20_000)

        val segments = ReadAloudTextSegmenter.segment(text)

        assertTrue(segments.sumOf { it.length } > 18_000)
        assertTrue(segments.all { it.length <= ReadAloudTextSegmenter.MAX_SEGMENT_CHARS })
    }

    @Test
    fun segmentSplitsLongTextWithoutPunctuation() {
        val text = "甲".repeat(251)

        val segments = ReadAloudTextSegmenter.segment(text, maxSegmentChars = 100)

        assertEquals(listOf(100, 100, 51), segments.map { it.length })
    }

    @Test
    fun segmentKeepsReadableSpaceBetweenEnglishSentences() {
        val text = "Hello. World."

        val segments = ReadAloudTextSegmenter.segment(text, maxSegmentChars = 100)

        assertEquals(listOf("Hello. World."), segments)
    }
}
