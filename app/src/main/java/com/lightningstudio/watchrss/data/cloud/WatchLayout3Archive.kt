package com.lightningstudio.watchrss.data.cloud

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** WRSS content-only articles. Unknown row tokens and unknown ZIP members stay in the source archive. */
internal object WatchLayout3Archive {
    data class Article(val metadata: WatchLayout3RawDocument, val text: String, val html: String?)
    fun decodeMicro(bytes: ByteArray, id: String): Article {
        val entries = WatchLayout3Codec.zipEntries(bytes, WatchLayout3Format.MAX_OBJECT_BYTES.toLong())
        val manifest = WatchLayout3RawDocument.parse(entries.getValue("manifest.json"))
        val root = manifest.json()
        if (!WatchLayout3Format.access(manifest, emptySet()).readable) throw WatchCloudUpgradeRequired()
        require(root.getString("format") == "watchrss-library-backup" && WatchLayout3Format.integer(manifest, "version") == 3L && root.get("cloudContentOnly") == true)
        // The raw parser accepts an object root; wrap the array to retain exact row slices.
        val arrayRaw = WatchLayout3RawDocument.parse("{\"articles\":" + WatchLayout3Format.text(entries.getValue("articles.json")) + "}")
        val nodes = arrayRaw.node(listOf("articles"))!!.elements!!
        require(nodes.size == 1)
        val row = nodes.single().let { WatchLayout3RawDocument.parse(arrayRaw.source.substring(it.start, it.end)) }
        val json = row.json()
        if (!WatchLayout3Format.access(row, emptySet()).readable) throw WatchCloudUpgradeRequired()
        require(json.getString("articleId") == id && !json.optBoolean("deleted") &&
            listOf("url", "title", "contentHash").all { json.get(it) is String && json.getString(it).isNotBlank() })
        val allowed = mutableSetOf("manifest.json", "articles.json", "sources.json", "saved_items.json", "app_meta.json")
        val stem = "bodies/" + WatchCloudCodec.sha256(id.toByteArray())
        fun body(key: String, suffix: String, required: Boolean): String? {
            if (!json.has(key) || json.isNull(key)) { require(!required); return null }
            val name = json.getString(key)
            require(name == stem + suffix)
            allowed += name
            return WatchLayout3Format.text(entries.getValue(name))
        }
        val text = requireNotNull(body("contentTextEntry", ".txt", true))
        val html = body("contentHtmlEntry", ".html", false)
        require(entries.keys.all { it in allowed })
        for (name in listOf("sources.json", "saved_items.json", "app_meta.json"))
            require(WatchLayout3Format.text(entries.getValue(name)) == "[]")
        return Article(row, text, html)
    }
    fun encodeMicro(article: Article, exportedAt: Long = 1): ByteArray {
        if (!WatchLayout3Format.access(article.metadata, emptySet()).writable) throw WatchCloudUpgradeRequired()
        val id = article.metadata.json().getString("articleId")
        val stem = "bodies/" + WatchCloudCodec.sha256(id.toByteArray())
        val row = article.metadata.merge(emptyList(), JSONObject().put("contentTextEntry", "$stem.txt")
            .put("contentHtmlEntry", if (article.html == null) JSONObject.NULL else "$stem.html"))
        val manifest = JSONObject().put("format", "watchrss-library-backup").put("version", 3).put("cloudContentOnly", true)
            .put("exportedAt", exportedAt).put("sourceCount", 0).put("articleCount", 1).put("savedItemCount", 0)
            .put("readerPresetCount", 0).put("readerFontCount", 0).put("readerBackgroundCount", 0).put("appMetaCount", 0)
        val entries = linkedMapOf("manifest.json" to manifest.toString().toByteArray(), "articles.json" to "[${row.source}]".toByteArray(),
            "sources.json" to "[]".toByteArray(), "saved_items.json" to "[]".toByteArray(), "app_meta.json" to "[]".toByteArray(), "$stem.txt" to article.text.toByteArray())
        article.html?.let { entries["$stem.html"] = it.toByteArray() }
        return zip(entries).also { decodeMicro(it, id) }
    }
    fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name).apply { time = 0L }); zip.write(bytes); zip.closeEntry()
        } }
    }.toByteArray()
}
