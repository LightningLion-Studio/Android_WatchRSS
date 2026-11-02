package com.lightningstudio.watchrss.data.cloud

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** The book identity excludes filename, URLs, HTML and display metadata. */
internal object WatchLayout3Books {
    const val MAX_CHAPTERS = 100000
    data class Chapter(val index: Int, val title: String, val text: String, val html: String?, val url: String)
    data class Package(val header: JSONObject, val chapters: List<Chapter>, val hashes: List<String>, val fingerprint: String, val bookId: String)
    private val url = Regex("https://watchrss\\.local/(?:import-txt-novel(?:/.*)?|import-epub(?:/.*)?|import-content/txt/.*)", RegexOption.IGNORE_CASE)
    private val cli = Regex("watchrss://import/online-v1/[a-f0-9]{64}")
    fun bookUrl(value: String): Boolean = url.matches(value) || cli.matches(value)
    fun normalize(value: String): String = value.replace("\r\n", "\n").replace('\r', '\n')
    private fun hash(vararg fields: Any): String = hashList(fields.toList())
    private fun hashList(fields: List<Any>): String {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data -> fields.forEach { field ->
            val bytes = field.toString().toByteArray(Charsets.UTF_8)
            data.writeInt(bytes.size); data.write(bytes)
        } }
        return WatchCloudCodec.sha256(output.toByteArray())
    }
    fun chapterHash(chapter: Chapter): String {
        require(chapter.index in 1..MAX_CHAPTERS)
        return hash("watchrss/book/chapter/v1", chapter.index, normalize(chapter.title), normalize(chapter.text))
    }
    fun fingerprint(hashes: List<String>): String {
        require(hashes.size in 1..MAX_CHAPTERS && hashes.all { WatchLayout3Format.hash.matches(it) })
        return hashList(listOf("watchrss/book/fingerprint/v1", hashes.size) + hashes)
    }
    fun bookId(fingerprint: String): String {
        require(WatchLayout3Format.hash.matches(fingerprint))
        return hash("watchrss/book/id/v1", fingerprint)
    }
    fun articleId(bookId: String, index: Int): String {
        require(WatchLayout3Format.hash.matches(bookId) && index in 1..MAX_CHAPTERS)
        return hash("watchrss/book/article/v1", bookId, index)
    }
    // JSONObject.quote escapes the slash in </p>. JSON.stringify does not.
    // Package JSONL uses the producer's exact canonical string spelling.
    private fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { canonical(it) + ":" + canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> buildString {
            append('"')
            value.forEach { c -> when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
            } }
            append('"')
        }
        else -> WatchCloudRecordCodec.canonical(value)
    }
    fun encode(header: JSONObject, chapters: List<Chapter>): ByteArray {
        require(header.getInt("chapterCount") == chapters.size)
        val result = buildString {
            append(canonical(header)); append('\n')
            chapters.forEach { c ->
                append(canonical(JSONObject().put("chapterIndex", c.index).put("title", normalize(c.title))
                    .put("contentText", normalize(c.text)).put("contentHtml", c.html?.let(::normalize) ?: JSONObject.NULL).put("url", c.url)))
                append('\n')
            }
        }.toByteArray()
        decode(result)
        return result
    }
    fun decode(bytes: ByteArray, expected: JSONObject? = null): Package {
        require(bytes.size <= WatchLayout3Format.MAX_OBJECT_BYTES)
        val text = WatchLayout3Format.text(bytes)
        require(text.isNotEmpty() && text[0] != '\ufeff' && text.endsWith('\n'))
        val lines = text.split('\n').dropLast(1)
        require(lines.size in 2..MAX_CHAPTERS + 1 && lines.none { it.endsWith('\r') })
        val raw = WatchLayout3RawDocument.parse(lines.first())
        val header = raw.json()
        require(header.get("format") == "watchrss-book" && WatchLayout3Format.integer(raw, "version") == 1L)
        require(header.get("title") is String && header.get("sourceUrl") is String && bookUrl(header.getString("sourceUrl")))
        require(header.getString("kind") in setOf("txt", "epub"))
        val count = WatchLayout3Format.integer(raw, "chapterCount", MAX_CHAPTERS.toLong()).toInt()
        require(count > 0 && lines.size == count + 1)
        val chapters = lines.drop(1).mapIndexed { i, line ->
            val cRaw = WatchLayout3RawDocument.parse(line)
            val c = cRaw.json()
            require(WatchLayout3Format.integer(cRaw, "chapterIndex") == i + 1L &&
                c.get("title") is String && c.get("contentText") is String && c.get("url") is String && bookUrl(c.getString("url")))
            require(c.has("contentHtml") && (c.isNull("contentHtml") || c.get("contentHtml") is String))
            Chapter(i + 1, c.getString("title"), c.getString("contentText"), if (c.isNull("contentHtml")) null else c.getString("contentHtml"), c.getString("url"))
        }
        val hashes = chapters.map(::chapterHash)
        val fingerprint = fingerprint(hashes)
        val id = bookId(fingerprint)
        expected?.let {
            require(it.getString("bookId") == id && it.getString("fingerprint") == fingerprint)
            if (it.has("chapterCount")) require(it.getInt("chapterCount") == count)
            if (it.has("packagePath")) require(it.getString("packagePath") == "books/$id.jsonl")
        }
        return Package(header, chapters, hashes, fingerprint, id)
    }

    fun validateState(raw: WatchLayout3RawDocument) {
        val value = raw.json()
        require(value.getString("bookId") == bookId(value.getString("fingerprint")) && value.get("title") is String &&
            value.get("sourceDeviceId") is String && value.get("deleted") is Boolean)
        val count = WatchLayout3Format.integer(raw, "chapterCount", MAX_CHAPTERS.toLong())
        require(count > 0)
        listOf("deletionClock", "deletedAt", "updatedAt").forEach { WatchLayout3Format.integer(raw, it) }
        listOf("sourceUrl", "kind").forEach { if (value.has(it)) require(value.get(it) is String) }
        var previous = 0L
        val nodes = raw.node(listOf("deletedChapters"))!!.elements!!
        for (node in nodes) {
            val token = raw.source.substring(node.start, node.end)
            val integer = WatchLayout3Format.integer(WatchLayout3RawDocument.parse("{\"n\":$token}"), "n", count)
            require(integer > previous); previous = integer
        }
    }
    fun validateReading(raw: WatchLayout3RawDocument) {
        val value = raw.json()
        require(WatchLayout3Format.hash.matches(value.getString("bookId")))
        require(WatchLayout3Format.integer(raw, "chapterIndex", MAX_CHAPTERS.toLong()) > 0)
        require(value.get("isRead") is Boolean && value.get("sourceDeviceId") is String && value.get("readingPositionContentHash") is String)
        listOf("readingPositionBytes", "readingPositionChangedAt").forEach { WatchLayout3Format.integer(raw, it) }
        require(value.get("readingProgress") is Number && value.getDouble("readingProgress").let { it.isFinite() && it in 0.0..1.0 })
        for (prefix in listOf("favorite", "watchLater")) {
            if (value.has(prefix + "Saved")) {
                require(value.get(prefix + "Saved") is Boolean)
                listOf("ChangedAt", "SortOrder").forEach { WatchLayout3Format.integer(raw, prefix + it) }
                if (value.has(prefix + "SourceDeviceId")) require(value.get(prefix + "SourceDeviceId") is String)
            } else require(listOf("ChangedAt", "SortOrder", "SourceDeviceId").none { value.has(prefix + it) })
        }
    }
    private fun compare(a: List<Comparable<*>>, b: List<Comparable<*>>): Int {
        for (i in a.indices) {
            @Suppress("UNCHECKED_CAST") val order = (a[i] as Comparable<Any>).compareTo(b[i])
            if (order != 0) return order
        }
        return 0
    }
    /** Preserve unknown tokens of the winning authenticated row. Deletion is permanent. */
    fun mergeState(local: WatchLayout3RawDocument?, remote: WatchLayout3RawDocument): WatchLayout3RawDocument {
        validateState(remote)
        if (local == null) return remote
        validateState(local)
        val l = local.json(); val r = remote.json()
        require(listOf("bookId", "fingerprint", "chapterCount").all { l.get(it) == r.get(it) })
        fun clock(v: JSONObject): List<Comparable<*>> = listOf(v.getLong("updatedAt"), v.getString("sourceDeviceId"), v.getString("title"), v.optString("sourceUrl"), v.optString("kind"))
        var winner = if (compare(clock(r), clock(l)) > 0) remote else local
        val deleted = listOf(l, r).flatMap { v -> v.getJSONArray("deletedChapters").let { a -> (0 until a.length()).map { a.getInt(it) } } }.distinct().sorted()
        winner = winner.merge(emptyList(), JSONObject().put("deletedChapters", JSONArray(deleted)).put("deleted", l.getBoolean("deleted") || r.getBoolean("deleted"))
            .put("deletionClock", maxOf(l.getLong("deletionClock"), r.getLong("deletionClock"))).put("deletedAt", maxOf(l.getLong("deletedAt"), r.getLong("deletedAt"))))
        return winner
    }
    fun mergeReading(local: WatchLayout3RawDocument?, remote: WatchLayout3RawDocument): WatchLayout3RawDocument {
        validateReading(remote)
        if (local == null) return remote
        validateReading(local)
        val l = local.json(); val r = remote.json()
        require(l.getString("bookId") == r.getString("bookId") && l.getInt("chapterIndex") == r.getInt("chapterIndex"))
        fun clock(v: JSONObject): List<Comparable<*>> = listOf(v.getLong("readingPositionChangedAt"), v.getString("sourceDeviceId"),
            if (v.getBoolean("isRead")) 0 else 1, v.getLong("readingPositionBytes"), v.getDouble("readingProgress"), v.getString("readingPositionContentHash"))
        var winner = if (compare(clock(r), clock(l)) > 0) remote else local
        val updates = JSONObject()
        for (prefix in listOf("favorite", "watchLater")) {
            fun saveClock(v: JSONObject): List<Comparable<*>> = listOf(v.getLong(prefix + "ChangedAt"), v.optString(prefix + "SourceDeviceId", v.getString("sourceDeviceId")),
                if (v.getBoolean(prefix + "Saved")) 0 else 1, v.getLong(prefix + "SortOrder"))
            val saveWinner = when {
                !r.has(prefix + "Saved") -> l
                !l.has(prefix + "Saved") -> r
                compare(saveClock(r), saveClock(l)) > 0 -> r
                else -> l
            }
            if (saveWinner.has(prefix + "Saved")) {
                listOf("Saved", "ChangedAt", "SortOrder").forEach { updates.put(prefix + it, saveWinner.get(prefix + it)) }
                updates.put(prefix + "SourceDeviceId", saveWinner.optString(prefix + "SourceDeviceId", saveWinner.getString("sourceDeviceId")))
            }
        }
        val progress = winner.json().getDouble("readingProgress")
        updates.put("readingProgress", kotlin.math.floor(progress * 1000000 + 0.5) / 1000000)
        winner = winner.merge(emptyList(), updates)
        return winner
    }
}
