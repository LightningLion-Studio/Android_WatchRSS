package com.lightningstudio.watchrss.data.douyin

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Network
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class DouyinOfflineReadiness { NONE, PREFIX_READY, FULL_READY }

internal fun douyinStockContentKey(item: DouyinStreamItem): String {
    val variant = item.variants.firstOrNull { item.playUrl == it.playUrl || item.playUrl in it.playUrls }
    val identity = listOf("douyin-stock-v1", item.awemeId, variant?.codec?.name, variant?.width,
        variant?.height, variant?.bitrate, item.durationMs,
        if (variant == null) item.playUrl.substringBefore('?') else "").joinToString(":")
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

/** Disk inventory is separate from exit snapshots. Only local decode success admits an entry. */
internal class DouyinStockStore(
    private val directory: File,
    private val decodeFirstFrame: (File) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private val consumed = linkedSetOf<String>()
    private val playback = linkedMapOf<String, DouyinStreamItem>()
    private val verifiedFiles = mutableMapOf<String, Pair<Long, Long>>()
    private var entries: List<DouyinStreamItem> = readIndex()

    fun restore(watched: Set<String>): List<DouyinStreamItem> = synchronized(lock) {
        entries = entries.filter { it.awemeId !in watched && it.awemeId !in consumed && fresh(it) && ready(it) }.take(3)
        persist()
        entries.toList()
    }

    fun add(item: DouyinStreamItem, bytes: ByteArray, watched: Set<String>): Boolean = synchronized(lock) {
        if (entries.size >= 3 || item.awemeId in watched || item.awemeId in consumed || !fresh(item) ||
            entries.any { it.awemeId == item.awemeId } || bytes.isEmpty() || bytes.size > 4 * 1024 * 1024) return false
        directory.mkdirs()
        val file = mediaFile(item)
        atomicWrite(file, bytes)
        if (!decodeFirstFrame(file)) { file.delete(); return false }
        verifiedFiles[file.name] = file.length() to file.lastModified()
        entries = entries + item
        persist()
        trimFiles()
        true
    }

    fun contains(id: String): Boolean = synchronized(lock) { entries.any { it.awemeId == id } }
    fun count(): Int = synchronized(lock) { entries.size }

    fun prefix(item: DouyinStreamItem): ByteArray? = synchronized(lock) {
        val stored = (entries + playback.values).firstOrNull {
            it.awemeId == item.awemeId && douyinStockContentKey(it) == douyinStockContentKey(item)
        } ?: return null
        if (!fresh(stored) || !ready(stored)) return null
        mediaFile(stored).readBytes()
    }

    fun admitPlaybackPrefix(item: DouyinStreamItem, bytes: ByteArray): Boolean = synchronized(lock) {
        if (!fresh(item) || bytes.isEmpty() || bytes.size > 4 * 1024 * 1024) return false
        directory.mkdirs()
        val file = mediaFile(item)
        atomicWrite(file, bytes)
        if (!decodeFirstFrame(file)) { file.delete(); return false }
        verifiedFiles[file.name] = file.length() to file.lastModified()
        playback[item.awemeId] = item
        while (playback.size > 3) playback.remove(playback.keys.first())
        trimFiles()
        true
    }

    fun consume(id: String) = synchronized(lock) {
        entries.firstOrNull { it.awemeId == id }?.let { playback[id] = it }
        while (playback.size > 3) playback.remove(playback.keys.first())
        consumed.add(id)
        entries = entries.filterNot { it.awemeId == id }
        persist() // Keep playing bytes until the bounded playback window evicts them.
        trimFiles()
    }

    fun clear() = synchronized(lock) {
        entries = emptyList()
        playback.clear()
        consumed.clear()
        verifiedFiles.clear()
        directory.deleteRecursively()
    }

    private fun fresh(item: DouyinStreamItem): Boolean {
        if (isDouyinPlayUrlExpired(item, now())) return false
        val expiry = runCatching {
            URI(item.playUrl).rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
                .firstOrNull { it.first() in setOf("expire", "expires", "x-expires") }
                ?.getOrNull(1)?.toLongOrNull()
        }.getOrNull()
        return expiry == null || expiry > now() / 1000
    }

    private fun ready(item: DouyinStreamItem): Boolean {
        val file = mediaFile(item)
        if (!file.isFile || file.length() !in 1..(4L * 1024 * 1024)) return false
        val stamp = file.length() to file.lastModified()
        if (verifiedFiles[file.name] == stamp) return true
        return decodeFirstFrame(file).also { if (it) verifiedFiles[file.name] = stamp }
    }

    private fun mediaFile(item: DouyinStreamItem) = File(directory, "${douyinStockContentKey(item)}.mp4")
    private fun readIndex(): List<DouyinStreamItem> = runCatching {
        val file = File(directory, "inventory.json")
        if (!file.isFile || file.length() > 256 * 1024) emptyList()
        else parseDouyinFeedCache(file.readText(), 3).items
    }.getOrDefault(emptyList())

    private fun persist() {
        directory.mkdirs()
        atomicWrite(File(directory, "inventory.json"), serializeDouyinFeedCache(entries).toByteArray())
    }

    private fun trimFiles() {
        val keep = (entries + playback.values).map { mediaFile(it).name }.toSet() + "inventory.json"
        directory.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    private fun atomicWrite(file: File, bytes: ByteArray) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
        check(temp.renameTo(file)) { "stock write failed" }
    }
}

/** All native/network operations are kept outside the inventory's portable state machine. */
internal object DouyinStockRuntime {
    private var context: Context? = null
    private var store: DouyinStockStore? = null
    @Volatile private var epoch = 0L
    private var lastFeedAtMs = 0L
    private val refillMutex = Mutex()
    private val http = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()

    @Synchronized
    fun configure(context: Context) {
        if (this.context != null) return
        this.context = context.applicationContext
        store = DouyinStockStore(File(context.filesDir, "douyin_unwatched_stock"), ::decode)
    }

    fun observeNetworkChanges(onChanged: () -> Unit): () -> Unit {
        val manager = context?.getSystemService(ConnectivityManager::class.java) ?: return {}
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChanged()
            override fun onLost(network: Network) = onChanged()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = onChanged()
        }
        return runCatching {
            manager.registerDefaultNetworkCallback(callback)
            val unregister: () -> Unit = { runCatching { manager.unregisterNetworkCallback(callback) }; Unit }
            unregister
        }.getOrElse { {} }
    }

    @Synchronized fun clear() { epoch++; lastFeedAtMs = 0; store?.clear() }
    fun consume(id: String) { runCatching { store?.consume(id) } }
    fun restore(watched: Set<String>): List<DouyinStreamItem> = runCatching {
        store?.restore(watched).orEmpty().onEach { hydrate(it) }
    }.getOrDefault(emptyList())

    fun hydrate(item: DouyinStreamItem, cacheUri: String? = null): Boolean {
        return try {
            val inventory = store ?: return false
            val bytes = inventory.prefix(item) ?: run {
                val captured = DouyinPlaybackPreviewCache.cachedPrefixBytes(cacheUri ?: item.playUrl) ?: return false
                if (!inventory.admitPlaybackPrefix(item, captured)) return false
                captured
            }
            DouyinPlaybackPreviewCache.restoreVerifiedPrefix(item, bytes)
            true
        } catch (_: Exception) { false }
    }

    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    suspend fun refill(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        watched: () -> Set<String>,
        feed: suspend () -> List<DouyinStreamItem> = { emptyList() }
    ) = withContext(Dispatchers.IO) {
        refillMutex.withLock {
            val inventory = store ?: return@withLock
            val generation = epoch
            if (!allowed()) return@withLock
            inventory.restore(watched())
            val watchedIds = watched()
            var candidates = items.filter { it.awemeId !in watchedIds && !isDouyinPlayUrlExpired(it) }
            val nowMs = System.currentTimeMillis()
            if (inventory.count() < 3 && candidates.size < 3 - inventory.count() &&
                nowMs - lastFeedAtMs >= 30_000 && generation == epoch && allowed()) {
                lastFeedAtMs = nowMs
                val fresh = try { feed() } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { emptyList() }
                currentCoroutineContext().ensureActive()
                if (generation != epoch || !allowed()) return@withLock
                candidates = (candidates + fresh).distinctBy { it.awemeId }
            }
            for (item in candidates.take(12)) {
                currentCoroutineContext().ensureActive()
                if (generation != epoch || !allowed() || inventory.count() >= 3) break
                if (inventory.contains(item.awemeId) || item.awemeId in watched() || isDouyinPlayUrlExpired(item)) continue
                try {
                    val bitrate = item.variants.firstOrNull { item.playUrl == it.playUrl || item.playUrl in it.playUrls }?.bitrate ?: 0
                    val budget = estimatePreviewBytes(null, item.durationMs, bitrate)
                    val request = Request.Builder().url(item.playUrl)
                        .header("Range", "bytes=0-${budget - 1}")
                        .apply { headers.forEach { (name, value) -> header(name, value) } }.build()
                    val call = http.newCall(request)
                    val handle = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { call.cancel() }
                    val bytes = try {
                        call.execute().use { response ->
                            if (response.code != 200 && response.code != 206) return@use null
                            if (response.code == 206 && !response.header("Content-Range").orEmpty().startsWith("bytes 0-")) return@use null
                            response.body?.byteStream()?.use { input ->
                                val buffer = ByteArray(budget)
                                var offset = 0
                                while (offset < budget && generation == epoch && allowed()) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer, offset, minOf(64 * 1024, budget - offset))
                                    if (count <= 0) break
                                    offset += count
                                }
                                buffer.copyOf(offset)
                            }
                        }
                    } finally { handle.dispose() }
                    currentCoroutineContext().ensureActive()
                    synchronized(this@DouyinStockRuntime) {
                        if (generation == epoch && bytes != null && allowed() && inventory.add(item, bytes, watched())) hydrate(item)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Fail closed: a partial/undecodable download never enters durable inventory.
                }
            }
        }
    }

    private fun allowed(): Boolean = runCatching {
        val manager = context?.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            manager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
    }.getOrDefault(false)

    private fun decode(file: File): Boolean = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val ready = frame != null
            frame?.recycle()
            ready
        } finally { retriever.release() }
    }.getOrDefault(false)
}
