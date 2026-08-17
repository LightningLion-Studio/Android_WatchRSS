package com.lightningstudio.watchrss.ui.util

import androidx.core.graphics.createBitmap
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.lightningstudio.watchrss.phoneconnection.ConfigPairingProtocol

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
    fun createWatchRssQrCode(
        data: String,
        size: Int,
        pairingToken: String? = null
    ): Bitmap? {
        return create(buildWatchRssQrContent(data, pairingToken), size)
    }
}

/**
 * Uses a URL fragment so the pairing secret stays in the scanner and is never sent in an HTTP URL.
 */
internal fun buildWatchRssQrContent(data: String, pairingToken: String?): String {
    val fragment = if (pairingToken != null) {
        require(ConfigPairingProtocol.isValidPairingToken(pairingToken)) { "Invalid pairing token" }
        "watchrss_pair=$pairingToken&protocol=${ConfigPairingProtocol.PROTOCOL_ID}"
    } else {
        "请将手机和手表连上同一个WiFi再扫码"
    }
    return "http://$data/#$fragment"
}
