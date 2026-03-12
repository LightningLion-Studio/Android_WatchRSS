package com.lightningstudio.watchrss.ui.util

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
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                val offset = y * size
                for (x in 0 until size) {
                    pixels[offset + x] = if (matrix[x, y]) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                }
            }
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
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
        val base64Data = Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val qrContent = "请使用手机版的腕上RSS扫码\n$base64Data"
        return create(qrContent, size)
    }
}
