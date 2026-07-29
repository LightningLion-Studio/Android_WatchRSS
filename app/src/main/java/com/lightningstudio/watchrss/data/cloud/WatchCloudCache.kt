package com.lightningstudio.watchrss.data.cloud

import android.content.Context
import java.io.File

class WatchCloudCache(context: Context) {
    private val root = File(context.applicationContext.filesDir, "watch-cloud-cache")

    fun manifest(userId: String, snapshotId: String): ByteArray? =
        manifestFile(userId, snapshotId).takeIf(File::isFile)?.readBytes()

    fun storeManifest(
        userId: String,
        snapshotId: String,
        bytes: ByteArray,
        markAsLocalHead: Boolean
    ) {
        write(manifestFile(userId, snapshotId), bytes)
        if (markAsLocalHead) {
            write(File(userDir(userId), "latest"), snapshotId.toByteArray())
        }
    }

    fun latest(userId: String): Pair<String, ByteArray>? {
        val id = File(userDir(userId), "latest").takeIf(File::isFile)
            ?.readText()?.trim()?.takeIf(String::isNotBlank) ?: return null
        return manifest(userId, id)?.let { id to it }
    }

    fun chunk(userId: String, hash: String): ByteArray? =
        chunkFile(userId, hash).takeIf(File::isFile)?.readBytes()?.takeIf {
            WatchCloudCodec.sha256(it) == hash
        }

    fun storeChunk(userId: String, hash: String, bytes: ByteArray) {
        require(WatchCloudCodec.sha256(bytes) == hash)
        write(chunkFile(userId, hash), bytes)
    }

    private fun userDir(userId: String) =
        File(root, WatchCloudCodec.sha256(userId.toByteArray()).take(32))

    private fun manifestFile(userId: String, id: String): File {
        require(id.matches(Regex("""[a-zA-Z0-9-]{1,128}""")))
        return File(userDir(userId), "manifests/$id.bin")
    }

    private fun chunkFile(userId: String, hash: String): File {
        require(hash.matches(Regex("""[0-9a-f]{64}""")))
        return File(userDir(userId), "chunks/${hash.take(2)}/$hash.bin")
    }

    private fun write(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeBytes(bytes)
        check(temporary.renameTo(file) || run {
            file.delete()
            temporary.renameTo(file)
        })
    }
}
