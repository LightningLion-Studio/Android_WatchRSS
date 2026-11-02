package com.lightningstudio.watchrss.data.cloud

import com.lightningstudio.watchrss.data.reader.*
import com.lightningstudio.watchrss.phoneconnection.bluetooth.ReaderPresetSyncPayload
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The phone's existing WRSS v3 format, with reader metadata/resources and empty article lists. */
internal object WatchCloudReaderArchive {
    private const val MAX_ENTRIES = 100_000
    private const val MAX_ENTRY_BYTES = 512L * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 4L * 1024 * 1024 * 1024
    private const val MAX_METADATA_BYTES = 8L * 1024 * 1024
    private val resourcePath = Regex("reader/(fonts|backgrounds|variants)/[0-9a-f]{64}\\.bin")
    private val bodyPath = Regex("bodies/[0-9a-f]{64}\\.(html|txt)")
    private val metadataNames = setOf("manifest.json", "sources.json", "articles.json", "saved_items.json", "app_meta.json", "reader/manifest.json")

    data class Resource(val kind: String, val fileName: String, val hash: String, val bytes: Long) {
        val entryName: String get() = "reader/${when (kind) { "font" -> "fonts"; "variant" -> "variants"; else -> "backgrounds" }}/$hash.bin"
    }

    class Prepared(val snapshot: ReaderPresetSnapshot?, private val directory: File, private val files: Map<String, File>) : Closeable {
        fun install(effective: ReaderPresetSnapshot, target: (Resource) -> File): Boolean {
            var changed = false
            references(effective).forEach { resource ->
                val source = files[resource.entryName] ?: return@forEach // A local winner need not be in this remote archive.
                val destination = target(resource)
                if (!verify(destination, resource)) {
                    val temporary = File(destination.parentFile, ".${destination.name}.${UUID.randomUUID()}.cloud")
                    try {
                        destination.parentFile?.mkdirs()
                        source.copyTo(temporary)
                        require(verify(temporary, resource)) { "阅读器资源落盘校验失败" }
                        try {
                            Files.move(
                                temporary.toPath(),
                                destination.toPath(),
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        } catch (_: java.io.IOException) {
                            Files.move(
                                temporary.toPath(),
                                destination.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        }
                        changed = true
                    } finally { temporary.delete() }
                }
            }
            return changed
        }
        override fun close() { directory.deleteRecursively() }
    }

    fun encode(snapshot: ReaderPresetSnapshot, resolve: (Resource) -> File, exportedAt: Long = System.currentTimeMillis()): ByteArray {
        val resources = references(snapshot)
        val files = resources.associate { ref ->
            val file = resolve(ref)
            require(verify(file, ref)) { "阅读器资源缺失或损坏：${ref.fileName}" }
            ref.entryName to file
        }
        val manifest = JSONObject().apply {
            put("format", "watchrss-reader-backup"); put("version", 3); put("exportedAt", exportedAt)
            put("scope", "reader-only"); put("appVersion", "watch-cloud"); put("sourceCount", 0); put("articleCount", 0); put("savedItemCount", 0); put("appMetaCount", 0)
            put("readerPresetCount", snapshot.presets.size); put("readerFontCount", snapshot.fonts.size); put("readerBackgroundCount", snapshot.backgrounds.size)
        }
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                fun text(name: String, value: String) {
                    zip.putNextEntry(ZipEntry(name).apply { time = 0L }); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
                text("manifest.json", manifest.toString())
                listOf("sources.json", "articles.json", "saved_items.json", "app_meta.json").forEach { text(it, "[]") }
                text("reader/manifest.json", ReaderPresetSyncPayload.snapshotJson(snapshot).toString())
                files.toSortedMap().forEach { (name, file) ->
                    zip.putNextEntry(ZipEntry(name).apply { time = 0L }); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }

    /** Stage and validate every referenced resource before touching the current account's library. */
    fun prepare(bytes: ByteArray, stagingRoot: File): Prepared {
        val directory = File(stagingRoot, "reader-cloud-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            val names = HashSet<String>()
            val files = mutableMapOf<String, File>()
            val metadata = mutableMapOf<String, ByteArray>()
            var total = 0L
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory && (name in metadataNames || resourcePath.matches(name) || bodyPath.matches(name))) { "云阅读归档路径无效：$name" }
                    require(names.add(name) && names.size <= MAX_ENTRIES) { "云阅读归档含重复或过多条目" }
                    val resource = resourcePath.matches(name)
                    val capture = name == "manifest.json" || name == "reader/manifest.json"
                    val file = if (resource) File(directory, files.size.toString()) else null
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    val text = if (capture) ByteArrayOutputStream() else null
                    val output = file?.outputStream()?.buffered()
                    var count = 0L
                    try {
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            count += read; total += read
                            require(count <= MAX_ENTRY_BYTES && total <= MAX_EXPANDED_BYTES && (!capture || count <= MAX_METADATA_BYTES)) { "云阅读归档解压后过大" }
                            output?.write(buffer, 0, read); text?.write(buffer, 0, read)
                        }
                    } finally { output?.close() }
                    file?.let { files[name] = it }
                    text?.let { metadata[name] = it.toByteArray() }
                    zip.closeEntry()
                }
            }
            val manifest = JSONObject(requireNotNull(metadata["manifest.json"]) { "云阅读归档缺少清单" }.toString(Charsets.UTF_8))
            WatchCloudProtocolV2.features(manifest)
            require(manifest.getString("format") in setOf("watchrss-library-backup", "watchrss-reader-backup") && manifest.getInt("version") in 1..3) { "云阅读归档版本不受支持" }
            require(setOf("manifest.json", "sources.json", "articles.json", "saved_items.json").all(names::contains)) { "云阅读归档缺少必要条目" }
            val snapshot = metadata["reader/manifest.json"]?.let { raw ->
                val json = JSONObject(raw.toString(Charsets.UTF_8))
                WatchCloudProtocolV2.features(json)
                ReaderPresetSnapshot(ReaderPresetSyncPayload.parsePresets(json.optJSONArray("presets")), ReaderPresetSyncPayload.parseFonts(json.optJSONArray("fonts")),
                    ReaderPresetSyncPayload.parseBackgrounds(json.optJSONArray("backgrounds")), ReaderPresetSyncPayload.parseDeletions(json.optJSONArray("deletions")))
            }
            snapshot?.let { reader ->
                require(reader.presets.map { it.id }.distinct().size == reader.presets.size &&
                    reader.fonts.map { it.id }.distinct().size == reader.fonts.size &&
                    reader.backgrounds.map { it.id }.distinct().size == reader.backgrounds.size) { "云阅读归档含重复标识" }
                listOf("readerPresetCount" to reader.presets.size, "readerFontCount" to reader.fonts.size,
                    "readerBackgroundCount" to reader.backgrounds.size).forEach { (key, count) ->
                    require(!manifest.has(key) || manifest.getInt(key) == count) { "云阅读归档数量不匹配" }
                }
            }
            val references = snapshot?.let(::references).orEmpty()
            require(files.keys == references.mapTo(hashSetOf()) { it.entryName }) { "云阅读归档资源缺失或未被引用" }
            references.forEach { resource -> require(verify(files.getValue(resource.entryName), resource)) { "云阅读归档资源校验失败：${resource.fileName}" } }
            return Prepared(snapshot, directory, files)
        } catch (error: Throwable) { directory.deleteRecursively(); throw error }
    }

    suspend fun restore(bytes: ByteArray, repository: ReaderPresetRepository, stagingRoot: File): Boolean = prepare(bytes, stagingRoot).use { prepared ->
        val remote = prepared.snapshot ?: return@use false
        val before = repository.exportSnapshot()
        // Install only resources belonging to winners, so an old snapshot cannot replace a newer local asset.
        val effective = mergedSnapshot(before, remote)
        val resourcesChanged = prepared.install(effective) { target(repository.resourceStore, it) }
        repository.mergeRemote(remote.presets, remote.fonts, remote.backgrounds, remote.deletions)
        if (resourcesChanged) repository.notifyResourceApplied()
        resourcesChanged || before != repository.exportSnapshot()
    }

    internal fun mergedSnapshot(local: ReaderPresetSnapshot, remote: ReaderPresetSnapshot): ReaderPresetSnapshot {
        fun <T> merge(old: List<T>, new: List<T>, id: (T) -> String, stamp: (T) -> Triple<Long, Boolean, String>): List<T> {
            val values = old.associateBy(id).toMutableMap()
            new.forEach { incoming ->
                val existing = values[id(incoming)]
                val next = stamp(incoming); val previous = existing?.let(stamp)
                if (previous == null || compareValuesBy(next, previous, { it.first }, { it.second }, { it.third }) > 0) values[id(incoming)] = incoming
            }
            return values.values.toList()
        }
        return ReaderPresetSnapshot(
            merge(local.presets, remote.presets, { it.id }, { Triple(it.updatedAt, it.deleted, it.modifiedBy) }),
            merge(local.fonts, remote.fonts, { it.id }, { Triple(it.updatedAt, it.deleted, it.modifiedBy) }),
            merge(local.backgrounds, remote.backgrounds, { it.id }, { Triple(it.updatedAt, it.deleted, it.modifiedBy) }), local.deletions + remote.deletions)
    }

    internal fun references(snapshot: ReaderPresetSnapshot): List<Resource> = buildList {
        snapshot.fonts.filterNot { it.deleted }.forEach { add(Resource("font", it.fileName, it.sha256, it.byteCount)) }
        snapshot.backgrounds.filterNot { it.deleted }.forEach { background ->
            add(Resource("background", background.masterFileName, background.sha256, background.byteCount))
            val variants = JSONObject(background.variantsJson)
            listOf("watch", "watchPoster").forEach { key -> variants.optJSONObject(key)?.let { variant ->
                add(Resource("variant", variant.getString("fileName"), variant.getString("sha256"), variant.getLong("byteCount")))
            } }
        }
    }.also { refs ->
        refs.forEach { ref ->
            require(ref.fileName.isNotBlank() && '/' !in ref.fileName && '\\' !in ref.fileName && ".." !in ref.fileName && ref.hash.matches(Regex("[0-9a-f]{64}")) && ref.bytes in 0..MAX_ENTRY_BYTES) { "云阅读资源描述无效" }
        }
        require(refs.groupBy { it.kind to it.fileName }.values.all { values -> values.map { it.hash to it.bytes }.distinct().size == 1 }) { "云阅读资源文件名冲突" }
        require(refs.groupBy { it.entryName }.values.all { values -> values.map { it.bytes }.distinct().size == 1 }) { "云阅读资源大小冲突" }
    }.distinct()

    internal fun existing(store: ReaderResourceStore, ref: Resource): File = when (ref.kind) {
        "font" -> store.fontFile(ref.fileName); "variant" -> store.variantFile(ref.fileName); else -> store.backgroundFile(ref.fileName)
    } ?: error("阅读器资源缺失：${ref.fileName}")

    private fun target(store: ReaderResourceStore, ref: Resource): File = when (ref.kind) {
        "font" -> store.targetFontFile(ref.fileName); "variant" -> store.targetVariantFile(ref.fileName); else -> store.targetBackgroundFile(ref.fileName)
    }

    private fun verify(file: File, ref: Resource): Boolean {
        if (!file.isFile || file.length() != ref.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(DEFAULT_BUFFER_SIZE); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) } == ref.hash
    }
}
