package com.lightningstudio.watchrss.port.readerrelease

import com.lightningstudio.watchrss.ui.reader.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class ReaderPaginationTest {
    private fun pages(texts: List<String>, size: Float = 24f) =
        paginateReaderText(texts, 466f, 466f, size * 1.4f) { text, start, width ->
            (start + (width / size).toInt().coerceAtLeast(1)).coerceAtMost(text.length)
        }

    @Test fun everyCharacterAppearsOnceAndPagesHaveStableTextAnchors() {
        val texts = listOf("第一段".repeat(100), "第二段，含有空白。\n".repeat(60))
        val result = pages(texts)
        assertTrue(result.size > 3)
        texts.indices.forEach { segment ->
            val rendered = result.flatMap { it.lines }.filter { it.anchor.segment == segment }
                .joinToString("") { texts[segment].substring(it.anchor.offset, it.end) }
            assertEquals(texts[segment], rendered)
        }
        result.forEachIndexed { index, page -> assertEquals(index, readerPageForAnchor(result, page.anchor)) }
    }

    @Test fun entireLineBoxFitsInside466Circle() {
        pages(listOf("测".repeat(2000))).flatMap { it.lines }.forEach { line ->
            val halfWidth = line.width / 2f
            listOf(line.top, line.top + 24f * 1.4f).forEach { y ->
                val dy = y - 233f
                assertTrue(halfWidth < sqrt(233f * 233f - dy * dy))
            }
            assertTrue(line.left >= 16f)
            assertTrue(line.top >= 466f * .17f)
            assertTrue(line.top + 24f * 1.4f <= 466f * .68f)
        }
    }

    @Test fun fontAndViewportChangesRestoreContainingTextPage() {
        val texts = listOf("正文".repeat(1400))
        val original = pages(texts)
        val anchor = original[4].anchor
        val large = pages(texts, 36f)
        val index = readerPageForAnchor(large, anchor)
        assertTrue(large[index].anchor.offset <= anchor.offset)
        assertTrue(index == large.lastIndex || large[index + 1].anchor.offset > anchor.offset)
        assertEquals(4, readerPageForAnchor(original, anchor))
        val smallerViewport = paginateReaderText(texts, 400f, 400f, 40f) { text, start, width ->
            minOf(text.length, start + width / 26)
        }
        val restored = readerPageForAnchor(smallerViewport, anchor)
        assertTrue(smallerViewport[restored].anchor.offset <= anchor.offset)
    }

    @Test fun utf8AnchorRoundTripPreservesCjkAndSurrogatePairsAcrossParagraphs() {
        val texts = listOf("中文😀abc", "第二段😎正文")
        listOf(ReaderTextAnchor(0, 2), ReaderTextAnchor(0, 4), ReaderTextAnchor(1, 3)).forEach { anchor ->
            val progress = readerAnchorProgress(texts, anchor)
            assertEquals(anchor, readerAnchorFromProgress(texts, progress))
        }
        assertEquals(ReaderTextAnchor(0, 0), readerAnchorFromProgress(texts, -1f))
        assertEquals(ReaderTextAnchor(1, texts[1].length), readerAnchorFromProgress(texts, 1f))
    }

    @Test fun illustrationsKeepTheirPositionBetweenTextPages() {
        val texts = listOf("前文", "", "后文")
        val result = paginateReaderText(texts, 466f, 466f, 28f, setOf(1)) { text, _, _ -> text.length }
        assertEquals(3, result.size)
        assertEquals(1, result[1].mediaSegment)
        assertEquals(ReaderTextAnchor(1, 0), result[1].anchor)
        assertEquals("后文", texts[result[2].anchor.segment])
    }

    @Test fun emptyAndUnusableViewportsReturnNoPages() {
        assertTrue(pages(emptyList()).isEmpty())
        assertTrue(paginateReaderText(listOf("x"), 0f, 466f, 20f) { _, _, _ -> 1 }.isEmpty())
        assertTrue(paginateReaderText(listOf("x"), 466f, 20f, 40f) { _, _, _ -> 1 }.isEmpty())
    }
}
