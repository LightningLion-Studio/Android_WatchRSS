package com.lightningstudio.watchrss.data.rss

/** Lightweight chapter metadata; reading the catalogue never loads article bodies. */
data class NovelChapter(
    val id: Long,
    val title: String,
    val link: String?,
    val readingPositionChangedAt: Long
)

data class NovelNavigation(val previous: NovelChapter?, val next: NovelChapter?)

private val chapterAddress = Regex("^(https://watchrss\\.local/(?:import-epub|import-txt-novel)/[^/]+)/chapter/(\\d+)-[^/]+$", RegexOption.IGNORE_CASE)

fun novelBookSource(link: String?): String? =
    chapterAddress.matchEntire(link?.trim().orEmpty())?.groupValues?.get(1)

fun orderedNovelChapters(chapters: List<NovelChapter>, bookSource: String): List<NovelChapter> =
    chapters.filter { novelBookSource(it.link) == bookSource }
        .sortedWith(compareBy<NovelChapter> {
            chapterAddress.matchEntire(it.link!!.trim())!!.groupValues[2].toLongOrNull() ?: Long.MAX_VALUE
        }.thenBy { it.link })

fun novelResumeChapter(chapters: List<NovelChapter>): NovelChapter? =
    chapters.filter { it.readingPositionChangedAt > 0L }.maxByOrNull { it.readingPositionChangedAt }
        ?: chapters.firstOrNull()

fun novelNavigation(chapters: List<NovelChapter>, currentId: Long): NovelNavigation? {
    val index = chapters.indexOfFirst { it.id == currentId }
    if (index < 0) return null
    return NovelNavigation(chapters.getOrNull(index - 1), chapters.getOrNull(index + 1))
}
