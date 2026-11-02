package com.lightningstudio.watchrss.data.cloud

import org.json.JSONArray
import org.json.JSONObject

internal data class WatchLayout3OverlayEntry(val kind: String, val id: String, val objectName: String?, val changedAt: Long, val sourceDeviceId: String) {
    val key: Pair<String, String> get() = kind to id
    fun json(): JSONObject = JSONObject().put("kind", kind).put("id", id).put("object", objectName ?: JSONObject.NULL)
        .put("changedAt", changedAt).put("sourceDeviceId", sourceDeviceId)
}

internal object WatchLayout3Overlay {
    private val order = compareBy<WatchLayout3OverlayEntry>({ it.kind }, { it.id })
    fun articleName(id: String): String = "private/articles/${WatchCloudCodec.sha256(id.toByteArray())}.wrss"
    fun encode(entries: List<WatchLayout3OverlayEntry>): ByteArray = JSONObject().put("format", "watchrss-private-overlay").put("version", 1)
        .put("entries", JSONArray(entries.sortedWith(order).map { it.json() })).toString().toByteArray().also { decode(it) }
    fun decode(bytes: ByteArray): List<WatchLayout3OverlayEntry> {
        require(bytes.size <= 4194304)
        val raw = WatchLayout3RawDocument.parse(bytes)
        val root = raw.json()
        exact(root, setOf("format", "version", "entries"))
        require(root.get("format") == "watchrss-private-overlay" && WatchLayout3Format.integer(raw, "version") == 1L)
        val nodes = raw.node(listOf("entries"))!!.elements!!
        require(nodes.size <= 8192)
        val entries = nodes.map { node ->
            val eRaw = WatchLayout3RawDocument.parse(raw.source.substring(node.start, node.end))
            val e = eRaw.json()
            exact(e, setOf("kind", "id", "object", "changedAt", "sourceDeviceId"))
            require(listOf("kind", "id", "sourceDeviceId").all { e.get(it) is String })
            val kind = e.getString("kind"); val id = e.getString("id"); val source = e.getString("sourceDeviceId")
            require(kind in setOf("article", "book") && id.isNotEmpty() && id.toByteArray().size <= 1024)
            require(source.isNotEmpty() && source.toByteArray().size <= 128 && source.none { it < ' ' || it == '\u007f' })
            require(e.isNull("object") || e.get("object") is String)
            val name = if (e.isNull("object")) null else e.getString("object")
            if (name != null) require(name == if (kind == "article") articleName(id) else {
                require(WatchLayout3Format.hash.matches(id)); "books/$id.jsonl"
            })
            WatchLayout3OverlayEntry(kind, id, name, WatchLayout3Format.integer(eRaw, "changedAt"), source)
        }
        require(entries.map { it.key }.distinct().size == entries.size && entries == entries.sortedWith(order))
        return entries
    }
    fun compareClock(a: WatchLayout3OverlayEntry, b: WatchLayout3OverlayEntry): Int =
        compareValuesBy(a, b, { it.changedAt }, { it.sourceDeviceId }, { if (it.objectName == null) 1 else 0 })
    private fun exact(json: JSONObject, keys: Set<String>) { require(json.keys().asSequence().toSet() == keys) }
}

/** Observe every authenticated head before deciding which private body may project.
 * A missing/invalid winner keeps the backing body available and prevents head ACK. */
internal class WatchLayout3OverlayReadSet {
    private data class Winner(val entry: WatchLayout3OverlayEntry, val remote: String, val binding: String, val valid: Boolean, var conflict: Boolean = false)
    private val winners = mutableMapOf<Pair<String, String>, Winner>()
    fun observe(manifest: WatchLayout3Manifest, payload: Map<String, ByteArray>): Int {
        val bytes = payload[WatchLayout3Format.OVERLAY_NAME] ?: return 0
        val remote = manifest.snapshotId
        var pending = 0
        for (entry in WatchLayout3Overlay.decode(bytes)) {
            val descriptor = entry.objectName?.let { name -> manifest.objects.singleOrNull { it.name == name } }
            val body = payload[entry.objectName]
            val binding = descriptor?.let { WatchCloudCodec.sha256(it.raw.source.toByteArray()) }.orEmpty()
            val valid = entry.objectName == null || descriptor != null && body != null && runCatching {
                descriptor.json.optString("contentSha256").takeIf { it.isNotEmpty() }?.let { require(WatchCloudCodec.sha256(body) == it) }
                if (entry.kind == "article") WatchLayout3Archive.decodeMicro(body, entry.id)
                else {
                    val book = descriptor.json.getJSONObject("privateBook")
                    require(book.getString("bookId") == entry.id && book.getString("packagePath") == entry.objectName)
                    WatchLayout3Books.decode(body, book)
                }
            }.isSuccess
            if (!valid) pending++
            val old = winners[entry.key]
            val order = old?.let { WatchLayout3Overlay.compareClock(entry, it.entry) } ?: 1
            if (order > 0) winners[entry.key] = Winner(entry, remote, binding, valid)
            else if (order == 0 && old != null && entry.objectName != null && old.entry.objectName != null &&
                (entry.objectName != old.entry.objectName || binding != old.binding)) {
                old.conflict = true; pending++
            }
        }
        return pending
    }
    fun allow(kind: String, id: String, snapshotId: String, objectName: String? = null): Boolean {
        val winner = winners[kind to id] ?: return objectName == null
        if (!winner.valid || winner.conflict) return objectName == null
        if (winner.entry.objectName == null) return false
        return winner.remote == snapshotId && winner.entry.objectName == objectName
    }
}
