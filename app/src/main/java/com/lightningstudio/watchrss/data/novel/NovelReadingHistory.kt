package com.lightningstudio.watchrss.data.novel

import android.content.Context
import android.content.SharedPreferences
import com.lightningstudio.watchrss.data.rss.RssItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private val chapterPattern = Regex(
    "^(https://watchrss\\.local/(?:import-epub|import-txt-novel)/[^/?#]+)/chapter/(\\d+)(?:-[^/?#]+)?$",
    RegexOption.IGNORE_CASE
)

data class NovelChapterLocation(val bookUrl: String, val number: Int)

fun novelChapterLocation(url: String?): NovelChapterLocation? {
    val match = chapterPattern.matchEntire(url?.trim().orEmpty()) ?: return null
    val number = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
    return NovelChapterLocation(match.groupValues[1], number)
}

fun orderNovelChapters(items: List<RssItem>): List<RssItem> = items.sortedWith(
    compareBy<RssItem> { novelChapterLocation(it.link)?.number ?: Int.MAX_VALUE }.thenBy { it.id }
)

/** Exact stable URL first; if a chapter was removed, use the next ordinal (or the last). */
fun novelResumeIndex(bookUrl: String, items: List<RssItem>, lastChapterUrl: String?): Int? {
    if (lastChapterUrl == null || items.isEmpty()) return null
    val previous = novelChapterLocation(lastChapterUrl) ?: return null
    if (previous.bookUrl != bookUrl.trimEnd('/')) return null
    items.indexOfFirst { it.link == lastChapterUrl }.takeIf { it >= 0 }?.let { return it }
    val following = items.indexOfFirst {
        val location = novelChapterLocation(it.link)
        location?.bookUrl == previous.bookUrl && location.number >= previous.number
    }
    return following.takeIf { it >= 0 } ?: items.indexOfLast {
        novelChapterLocation(it.link)?.bookUrl == previous.bookUrl
    }.takeIf { it >= 0 }
}

/** Device-local, book-scoped navigation metadata; never stores or changes article bodies. */
class NovelReadingHistory(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("novel_reading_history", Context.MODE_PRIVATE)

    fun lastChapter(bookUrl: String): String? = preferences.getString(key(bookUrl), null)

    fun observe(bookUrl: String): Flow<String?> = callbackFlow {
        val preferenceKey = key(bookUrl)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == preferenceKey || changed == null) trySend(lastChapter(bookUrl))
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(lastChapter(bookUrl))
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    suspend fun recordChapter(chapterUrl: String): Boolean {
        val location = novelChapterLocation(chapterUrl) ?: return false
        // Finish this small local commit even if the reader is closed immediately afterwards.
        return withContext(NonCancellable + Dispatchers.IO) {
            preferences.edit().putString(key(location.bookUrl), chapterUrl).commit()
        }
    }

    internal fun removeBook(bookUrl: String) { preferences.edit().remove(key(bookUrl)).commit() }

    private fun key(bookUrl: String): String = "chapter_" + MessageDigest.getInstance("SHA-256")
        .digest(bookUrl.trimEnd('/').toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
