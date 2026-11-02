package com.lightningstudio.watchrss.data.cloud

import org.json.JSONArray
import org.json.JSONObject

/** Exclude only root transport timestamps; all persisted content/version fields remain significant. */
internal fun cloudContentJson(bytes: ByteArray, ignoredRootFields: Set<String>): ByteArray {
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    ignoredRootFields.forEach(root::remove)
    return canonicalCloudJson(root).toByteArray(Charsets.UTF_8)
}

private fun canonicalCloudJson(value: Any?): String = when (value) {
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
        JSONObject.quote(it) + ":" + canonicalCloudJson(value.get(it))
    }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalCloudJson(value.get(it)) }
    null, JSONObject.NULL -> "null"
    is String -> JSONObject.quote(value)
    else -> value.toString()
}

internal fun watchCloudContentHash(state: ByteArray, relay: ByteArray?, keyVersion: Int, reader: ByteArray? = null, notes: ByteArray? = null, noteArchive: ByteArray? = null): String {
    val stateHash = WatchCloudCodec.sha256(cloudContentJson(state, setOf("exportedAt")))
    val relayHash = relay?.let { WatchCloudCodec.sha256(cloudContentJson(it, setOf("sentAt"))) }.orEmpty()
    val readerHash = reader?.let(::cloudReaderContentHash).orEmpty()
    val notesHash = notes?.let { WatchCloudCodec.sha256(cloudContentJson(it, emptySet())) }.orEmpty()
    val archiveHash = noteArchive?.let(::cloudReaderContentHash).orEmpty()
    return WatchCloudCodec.sha256("content-v4:$keyVersion:$stateHash:$relayHash:$readerHash:$notesHash:$archiveHash".toByteArray())
}

private fun cloudReaderContentHash(bytes: ByteArray): String {
    val entries = sortedMapOf<String, String>()
    java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            require(entry.name !in entries) { "云阅读归档含重复路径" }
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            if (entry.name == "manifest.json") digest.update(cloudContentJson(zip.readBytes(), setOf("exportedAt")))
            else {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) { val count = zip.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            entries[entry.name] = digest.digest().joinToString("") { "%02x".format(it) }
            zip.closeEntry()
        }
    }
    return WatchCloudCodec.sha256(cloudContentJson(JSONObject(entries).toString().toByteArray(), emptySet()))
}
