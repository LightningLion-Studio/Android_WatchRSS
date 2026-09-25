package com.lightningstudio.watchrss.data.novel

import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.testutil.sampleRssItem
import org.junit.Assert.*
import org.junit.Test

class NovelCatalogTest {
    private val book = "${ImportedContentIds.EPUB_SOURCE_ROOT_URL}/book-a"
    private fun chapter(index: Int) = sampleRssItem(id = index.toLong()).copy(link = "$book/chapter/${index.toString().padStart(4, '0')}-hash")

    @Test fun chapterOrderIsNumericNotFetchOrderOrLexical() {
        val ordered = orderNovelChapters(listOf(chapter(10001), chapter(2), chapter(350), chapter(1)))
        assertEquals(listOf(1L, 2L, 350L, 10001L), ordered.map { it.id })
    }

    @Test fun anchorFindsChapterBeyondTheRssTwoHundredCap() {
        val chapters = (1..350).map(::chapter)
        assertEquals(309, novelResumeIndex(book, chapters, chapter(310).link))
    }

    @Test fun removedChapterFallsForwardOrToLastSiblingButNeverAnotherBook() {
        assertEquals(1, novelResumeIndex(book, listOf(chapter(1), chapter(3)), chapter(2).link))
        assertEquals(1, novelResumeIndex(book, listOf(chapter(1), chapter(3)), chapter(5).link))
        assertNull(novelResumeIndex("$book-other", listOf(chapter(1)), chapter(1).link))
        assertNull(novelResumeIndex(book, emptyList(), chapter(1).link))
    }

    @Test fun chapteredTxtRecognizedWithoutTreatingItAsLegacyWholeTextFile() {
        val source = "${ImportedContentIds.TXT_NOVEL_SOURCE_ROOT_URL}/book-b"
        val url = "$source/chapter/0230-hash"
        assertEquals(NovelChapterLocation(source, 230), novelChapterLocation(url))
        assertTrue(ImportedContentIds.isImportedContentUrl(source))
        assertTrue(ImportedContentIds.isNovelSourceUrl(source))
        assertTrue(ImportedContentIds.isNovelContentItemUrl(url))
        assertFalse(ImportedContentIds.isImportedTextItemUrl(url))
        assertFalse(ImportedContentIds.isNovelChapterSourceUrl(ImportedContentIds.ROOT_SOURCE_URL))
    }

    @Test fun externalOrMalformedChapterCannotCreateHistoryRecord() {
        assertNull(novelChapterLocation("https://example.org/book/chapter/0001-x"))
        assertNull(novelChapterLocation("$book/chapter/0000-x"))
        assertNull(novelChapterLocation("$book/chapter/999999999999-x"))
        assertNull(novelChapterLocation("${ImportedContentIds.ROOT_SOURCE_URL}/txt/whole-book"))
    }
}
