package com.lightningstudio.watchrss.data.reader

import android.content.Context
import java.io.File
import java.security.MessageDigest

class ReaderResourceStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, ROOT_DIRECTORY)
    private val fonts = File(root, FONT_DIRECTORY)
    private val backgrounds = File(root, BACKGROUND_DIRECTORY)
    private val variants = File(root, VARIANT_DIRECTORY)

    init {
        fonts.mkdirs()
        backgrounds.mkdirs()
        variants.mkdirs()
    }

    fun fontFile(fileName: String): File? =
        safeChild(fonts, fileName)?.takeIf(File::isFile)

    fun backgroundFile(fileName: String): File? =
        safeChild(backgrounds, fileName)?.takeIf(File::isFile)

    fun variantFile(fileName: String): File? =
        safeChild(variants, fileName)?.takeIf(File::isFile)

    fun targetFontFile(fileName: String): File =
        requireNotNull(safeChild(fonts, fileName)) { "字体文件名无效" }

    fun targetBackgroundFile(fileName: String): File =
        requireNotNull(safeChild(backgrounds, fileName)) { "背景文件名无效" }

    fun targetVariantFile(fileName: String): File =
        requireNotNull(safeChild(variants, fileName)) { "背景派生文件名无效" }

    fun verify(file: File, expectedBytes: Long, expectedSha256: String): Boolean {
        if (!file.isFile || file.length() != expectedBytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    private fun safeChild(directory: File, fileName: String): File? {
        if (fileName.isBlank() || '/' in fileName || '\\' in fileName || fileName.contains("..")) {
            return null
        }
        val child = File(directory, fileName)
        return child.takeIf { it.parentFile == directory }
    }

    companion object {
        const val ROOT_DIRECTORY = "reader_assets"
        const val FONT_DIRECTORY = "fonts"
        const val BACKGROUND_DIRECTORY = "backgrounds"
        const val VARIANT_DIRECTORY = "variants"
    }
}
