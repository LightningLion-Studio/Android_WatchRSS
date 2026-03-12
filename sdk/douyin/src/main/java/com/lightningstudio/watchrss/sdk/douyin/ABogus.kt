package com.lightningstudio.watchrss.sdk.douyin

import java.net.URLEncoder
import kotlin.random.Random
import com.lightningstudio.watchrss.sdk.douyin.utils.SM3HashUtils

// 从Python实现移植得来：https://github.com/Evil0ctal/Douyin_TikTok_Download_API/?tab=readme-ov-file
class ABogus {
    private val browser = "1536|742|1536|864|0|0|0|0|1536|864|1536|864|1536|742|24|24|MacIntel"
    private val browserCode: List<Int> = charCodeAt(browser)

    private fun endCheckNum(a: List<Long>): Int {
        var r = 0
        for (i in a) {
            r = r xor i.toInt()
        }
        return r
    }

    private fun fromCharCode(vararg codes: Long): String {
        return codes.joinToString(separator = "") { it.toInt().toChar().toString() }
    }

    private fun generateString2Inner(a: MutableList<Long>): String {
        val e = endCheckNum(a)
        a.addAll(browserCode.map { it.toLong() })
        a.add(e.toLong())
        val rc4Parameter0 = fromCharCode(*a.toLongArray())
        return RC4Utils.rc4Encrypt(rc4Parameter0, "y")
    }

    private fun generateString2(urlParams: String): String {
        val a = SM3HashUtils().generateString2List(urlParams).toMutableList()
        return generateString2Inner(a)
    }

    fun getValue(urlParams: MutableMap<String, String>): String {
        val string1 = ABogusString1Generator().generateString1()
        val urlParamsStr = URLParametersToURLQueryString().urlEncode(urlParams)
        val string2 = generateString2(urlParamsStr)
        val string = string1 + string2
        return ABogusGetValueResultGenerator().generateResult(string)
    }

    companion object {
        fun charCodeAt(s: String): List<Int> = s.map { it.code }
    }
}

class ABogusString1Generator {
    fun generateString1(): String {
        val str1 = fromCharCode(*list1().toIntArray())
        val str2 = fromCharCode(*list2().toIntArray())
        val str3 = fromCharCode(*list3().toIntArray())
        return str1 + str2 + str3
    }

    fun fromCharCode(vararg codes: Int): String {
        return codes.joinToString("") { code ->
            val validCode = code.coerceIn(0, 65535)
            validCode.toChar().toString()
        }
    }

    fun list1(a: Int = 170, b: Int = 85, c: Int = 45): List<Int> {
        return randomList(
            b = a,
            c = b,
            d = 1,
            e = 2,
            f = 5,
            g = c and a
        )
    }

    fun list2(a: Int = 170, b: Int = 85): List<Int> {
        return randomList(
            b = a,
            c = b,
            d = 1,
            e = 0,
            f = 0,
            g = 0
        )
    }

    fun list3(a: Int = 170, b: Int = 85): List<Int> {
        return randomList(
            b = a,
            c = b,
            d = 1,
            e = 0,
            f = 5,
            g = 0
        )
    }

    fun randomList(
        b: Int = 170,
        c: Int = 85,
        d: Int = 0,
        e: Int = 0,
        f: Int = 0,
        g: Int = 0
    ): List<Int> {
        val r = Random.nextDouble() * 10000.0
        val v = mutableListOf<Int>().apply {
            add(r.toInt() and 255)
            add(r.toInt() shr 8)
        }
        val s1 = v[0] and b or d
        v.add(s1)
        val s2 = v[0] and c or e
        v.add(s2)
        val s3 = v[1] and b or f
        v.add(s3)
        val s4 = v[1] and c or g
        v.add(s4)
        return v.takeLast(4)
    }
}

class InvalidUrlParameterException(message: String) : IllegalArgumentException(message)

class URLParametersToURLQueryString {
    fun urlEncode(params: Map<String, String>): String {
        val encodedParams = mutableListOf<String>()
        val charset = "UTF-8"
        for ((key, value) in params) {
            val encodedKey = URLEncoder.encode(key, charset)
            val encodedValue = URLEncoder.encode(value, charset)
            encodedParams.add("$encodedKey=$encodedValue")
        }
        return encodedParams.joinToString("&")
    }
}

class ABogusGetValueResultGenerator {
    private val charTable = charArrayOf(
        'D', 'k', 'd', 'p', 'g', 'h', '2', 'Z', 'm', 's', 'Q', 'B', '8', '0', '/', 'M',
        'f', 'v', 'V', '3', '6', 'X', 'I', '1', 'R', '4', '5', '-', 'W', 'U', 'A', 'l',
        'E', 'i', 'x', 'N', 'L', 'w', 'o', 'q', 'Y', 'T', 'O', 'P', 'u', 'z', 'K', 'F',
        'j', 'J', 'n', 'r', 'y', '7', '9', 'H', 'b', 'G', 'c', 'a', 'S', 't', 'C', 'e'
    )

    fun generateResult(s: String): String {
        val r = mutableListOf<Char>()
        val strLength = s.length

        var i = 0
        while (i < strLength) {
            val n = when {
                i + 2 < strLength -> (s[i].code shl 16) or (s[i + 1].code shl 8) or s[i + 2].code
                i + 1 < strLength -> (s[i].code shl 16) or (s[i + 1].code shl 8)
                else -> s[i].code shl 16
            }

            val jRange = listOf(18, 12, 6, 0)
            val kValues = listOf(0xFC0000, 0x03F000, 0x0FC0, 0x3F)
            for ((idx, j) in jRange.withIndex()) {
                val k = kValues[idx]
                if (j == 6 && i + 1 >= strLength) break
                if (j == 0 && i + 2 >= strLength) break

                val index = (n and k) shr j
                r.add(charTable[index])
            }

            i += 3
        }

        val paddingCount = (4 - r.size % 4) % 4
        repeat(paddingCount) { r.add('=') }
        return r.joinToString("")
    }
}

object RC4Utils {
    @JvmStatic
    fun rc4Encrypt(plaintext: String, key: String): String {
        val s = IntArray(256) { it }
        var j = 0

        for (i in 0..255) {
            val keyChar = key[i % key.length]
            j = (j + s[i] + keyChar.code) % 256
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
        }

        var i = 0
        j = 0
        val cipher = StringBuilder()

        for (k in plaintext.indices) {
            i = (i + 1) % 256
            j = (j + s[i]) % 256
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
            val t = (s[i] + s[j]) % 256
            val plainCharCode = plaintext[k].code
            val cipherCharCode = s[t] xor plainCharCode
            cipher.append(Char(cipherCharCode))
        }

        return cipher.toString()
    }
}
