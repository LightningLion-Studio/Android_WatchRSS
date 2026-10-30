package com.lightningstudio.watchrss.data.rss

import org.junit.Assert.*
import org.junit.Test

class NovelNavigationTest {
    private val book = "https://watchrss.local/import-epub/book-a"
    private fun chapter(id: Long, index: String, changedAt: Long = 0, source: String = book) =
        NovelChapter(id, "章节 $index", "$source/chapter/$index-key", changedAt)

    @Test
    fun chapterOrder_isNumericAndDoesNotMixBooks() {
        val ordered = orderedNovelChapters(listOf(
            chapter(3, "10000"), chapter(2, "9999"), chapter(1, "0001"),
            chapter(4, "0002", source = "$book-other")
        ), book)
        assertEquals(listOf(1L, 2L, 3L), ordered.map { it.id })
        assertEquals(2L, novelNavigation(ordered, 1L)?.next?.id)
        assertNull(novelNavigation(ordered, 1L)?.previous)
        assertNull(novelNavigation(ordered, 3L)?.next)
        assertNull(novelNavigation(ordered, 999L))
    }

    @Test
    fun continueReading_usesLatestVisitedChapterEvenWhenReturningToEarlierChapter() {
        val chapters = listOf(chapter(1, "0001", 200), chapter(2, "0002", 100))
        assertEquals(1L, novelResumeChapter(chapters)?.id)
        assertEquals(1L, novelResumeChapter(listOf(chapter(1, "0001"), chapter(2, "0002")))?.id)
        assertNull(novelResumeChapter(emptyList()))
    }

    @Test
    fun txtAndEpubAddressesAreRecognizedButOrdinaryArticlesAreNot() {
        val txt = "https://watchrss.local/import-txt-novel/book"
        val link = "$txt/chapter/0001-abc"
        assertEquals(txt, novelBookSource(link))
        assertTrue(ImportedContentIds.isNovelContentItemUrl(link))
        assertEquals(ReaderContentCapabilities(false, false), ImportedContentIds.readerCapabilities(link))
        assertNull(novelBookSource("https://example.com/chapter/0001-key"))
        assertNull(novelBookSource("$txt/chapter/missing-index"))
    }
}
