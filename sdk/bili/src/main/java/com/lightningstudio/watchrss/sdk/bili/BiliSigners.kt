package com.lightningstudio.watchrss.sdk.bili

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

object BiliSigners {
    fun signApp(params: Map<String, String>, appKey: String, appSec: String): Map<String, String> {
        val mutable = params.toMutableMap()
        if (!mutable.containsKey("appkey")) {
            mutable["appkey"] = appKey
        }
        val sorted = mutable.toSortedMap()
        val query = sorted.entries.joinToString("&") { (k, v) -> "$k=$v" }
        val sign = md5(query + appSec)
        mutable["sign"] = sign
        return mutable
    }

    fun signWbi(
        params: Map<String, String>,
        imgKey: String,
        subKey: String,
        timestampSeconds: Long = System.currentTimeMillis() / 1000
    ): Map<String, String> {
        val mixinKey = mixinKey(imgKey, subKey)
        val mutable = params.toMutableMap()
        mutable["wts"] = timestampSeconds.toString()
        val sorted = mutable.toSortedMap()
        val encoded = sorted.entries.joinToString("&") { (k, v) ->
            "${encodeWbi(k)}=${encodeWbi(v)}"
        }
        val wRid = md5(encoded + mixinKey)
        mutable["w_rid"] = wRid
        return mutable
    }

    fun mixinKey(imgKey: String, subKey: String): String {
        val origin = imgKey + subKey
        val key = StringBuilder()
        MIXIN_KEY_ENCODE_TABLE.forEach { index ->
            if (index < origin.length) {
                key.append(origin[index])
            }
        }
        return key.toString().take(32)
    }

    fun extractWbiKey(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val name = url.substringAfterLast('/')
        return name.substringBefore('.')
    }

    private fun encodeWbi(raw: String): String {
        val encoded = URLEncoder.encode(raw, "UTF-8")
        return encoded
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
            .uppercase(Locale.US)
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val MIXIN_KEY_ENCODE_TABLE = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32,
        15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19,
        29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61,
        26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63,
        57, 62, 11, 36, 20, 34, 44, 52
    )
}
