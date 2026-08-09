package com.lightningstudio.watchrss.phoneconnection.bluetooth

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Base64

object WatchNoteAssetSyncPayload {
    const val ACTION = "syncNoteAsset"
    const val VERSION = 1
    private const val CHUNK_BYTES = 512 * 1024
    private val SafeStorageKey = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}")

    fun apply(context: Context, request: JSONObject): JSONObject = apply(context.filesDir, request)

    internal fun apply(filesDir: File, request: JSONObject): JSONObject {
        require(request.optInt("version") == VERSION) { "不支持的备忘录图片同步版本" }
        val storageKey = request.getString("storageKey")
        require(SafeStorageKey.matches(storageKey)) { "备忘录图片文件名无效" }
        val expectedSha256 = request.getString("sha256").lowercase()
        require(expectedSha256.matches(Regex("[0-9a-f]{64}"))) { "备忘录图片校验值无效" }
        val chunkIndex = request.getInt("chunkIndex")
        val chunkCount = request.getInt("chunkCount")
        require(chunkCount > 0 && chunkIndex in 0 until chunkCount) { "备忘录图片分块序号无效" }

        val directory = File(filesDir, "notes/assets").also { it.mkdirs() }
        val target = File(directory, storageKey)
        if (target.isFile && target.sha256() == expectedSha256) {
            return response(complete = true, alreadyPresent = true)
        }

        val temporary = File(directory, ".$storageKey.part")
        if (chunkIndex == 0) {
            if (temporary.exists()) temporary.delete()
        } else {
            require(temporary.isFile && temporary.length() == chunkIndex.toLong() * CHUNK_BYTES) {
                "备忘录图片分块顺序错误"
            }
        }
        val bytes = Base64.getDecoder().decode(request.getString("data"))
        require(bytes.size <= CHUNK_BYTES) { "备忘录图片分块过大" }
        FileOutputStream(temporary, chunkIndex > 0).buffered().use { output ->
            output.write(bytes)
        }

        val complete = chunkIndex == chunkCount - 1
        if (complete) {
            require(temporary.sha256() == expectedSha256) { "备忘录图片校验失败" }
            if (target.exists()) target.delete()
            require(temporary.renameTo(target)) { "备忘录图片保存失败" }
        }
        return response(complete = complete, alreadyPresent = false)
    }

    private fun response(complete: Boolean, alreadyPresent: Boolean) = JSONObject().apply {
        put("success", true)
        put("action", ACTION)
        put("complete", complete)
        put("alreadyPresent", alreadyPresent)
    }

    private fun File.sha256(): String = inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
