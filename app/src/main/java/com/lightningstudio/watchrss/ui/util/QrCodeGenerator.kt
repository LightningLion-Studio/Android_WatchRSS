package com.lightningstudio.watchrss.ui.util

import androidx.core.graphics.createBitmap
import android.graphics.Bitmap
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {
    fun create(text: String, size: Int): Bitmap? {
        if (text.isBlank() || size <= 0) return null
        return try {
            // 使用字节模式（Byte Mode）确保 UTF-8 中文正确编码
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 0
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                val offset = y * size
                for (x in 0 until size) {
                    pixels[offset + x] = if (matrix[x, y]) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                }
            }
            createBitmap(size, size).also { bitmap ->
                bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 生成腕上RSS标准格式的二维码
     * @param data 要编码的数据（如 IP:port）
     * @param size 二维码尺寸
     * @return 二维码 Bitmap
     */
    fun createWatchRssQrCode(data: String, size: Int): Bitmap? {
        // 使用 URL fragment (#) 而不是查询参数 (?) 来添加用户提示
        // 原因：fragment 不会被发送到服务器，只在客户端（扫码应用）显示
        // 这样可以给用户提示，同时保持服务器端简洁，不污染服务器日志
        val qrContent = "http://$data/#请将手机和手表连上同一个WiFi再扫码"
        return create(qrContent, size)
    }
}
