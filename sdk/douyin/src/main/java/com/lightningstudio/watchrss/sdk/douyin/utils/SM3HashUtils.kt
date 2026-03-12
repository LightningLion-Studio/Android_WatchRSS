package com.lightningstudio.watchrss.sdk.douyin.utils

import android.util.Log
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.random.Random
import org.bouncycastle.crypto.digests.SM3Digest
import org.bouncycastle.util.encoders.Hex

/**
 * 完全对齐Python GMSSL SM3行为的工具类
 */
class SM3HashUtils {
    // 对应Python中的__end_string
    private val endString = "cus"

    /**
     * 生成方法编码（完全对齐Python的generate_method_code）
     */
    fun generateMethodCode(method: String): List<Long> {
        val l0Input = method + endString
        Log.d("WatchRSS", "调用generate_method_code得到的l0Input：$l0Input")
        val l0Output = sm3ToArrayL0(l0Input)
        Log.d("WatchRSS", "调用generate_method_code得到的l0Output：$l0Output")
        return sm3ToArrayL1(l0Output)
    }

    fun generateParamsCode(params: String): List<Long> {
        val l0Input = params + endString
        Log.d("WatchRSS", "调用generate_params_code得到的l0Input：$l0Input")
        val l0Output = sm3ToArrayL0(l0Input)
        Log.d("WatchRSS", "调用generate_params_code得到的l0Output：$l0Output")
        return sm3ToArrayL1(l0Output)
    }

    // 对应 Python 中的 generate_string_2_list 方法
    fun generateString2List(urlParams: String): List<Long> {
        // 1. 计算毫秒级时间戳，对应 Python 的 time() * 1000
        val startTime = Instant.now().toEpochMilli()
        Log.d("WatchRSS", "调用generateString2List得到的startTime：$startTime")
        // 2. 生成 4-8 之间的随机整数，对应 Python 的 randint(4, 8)
        val randomDelta = Random.nextInt(4, 9)
        Log.d("WatchRSS", "调用generateString2List的randomOffset：$randomDelta")
        val endTime = startTime + randomDelta

        // 3. 获取参数数组
        val paramsArray: List<Long> = generateParamsCode(urlParams)

        // 4. 计算各个参数（位运算、除法逻辑与Python完全一致）
        val list4Parameter0 = ((endTime shr 24) and 255L).toInt()
        val list4Parameter1 = paramsArray[21].toInt()
        val list4Parameter2 = 40
        val list4Parameter3 = ((endTime shr 16) and 255L).toInt()
        val list4Parameter4 = paramsArray[22].toInt()
        val list4Parameter5 = 49
        val list4Parameter6 = ((endTime shr 8) and 255L).toInt()
        val list4Parameter7 = ((endTime shr 0) and 255L).toInt()
        val list4Parameter8 = ((startTime shr 24) and 255L).toInt()
        val list4Parameter9 = ((startTime shr 16) and 255L).toInt()
        val list4Parameter10 = ((startTime shr 8) and 255L).toInt()
        val list4Parameter11 = ((startTime shr 0) and 255L).toInt()
        val list4Parameter12 = 251
        val list4Parameter13 = 167
        val list4Parameter14 = ((endTime / 256 / 256 / 256 / 256) shr 0).toInt()
        val list4Parameter15 = ((startTime / 256 / 256 / 256 / 256) shr 0).toInt()
        val list4Parameter16 = 67

        // 5. 调用list4方法并返回结果
        return list4(
            list4Parameter0,
            list4Parameter1,
            list4Parameter2,
            list4Parameter3,
            list4Parameter4,
            list4Parameter5,
            list4Parameter6,
            list4Parameter7,
            list4Parameter8,
            list4Parameter9,
            list4Parameter10,
            list4Parameter11,
            list4Parameter12,
            list4Parameter13,
            list4Parameter14,
            list4Parameter15,
            list4Parameter16
        ).map { it.toLong() }
    }

    // 对应 Python 中的 list_4 静态方法
    companion object {
        fun list4(
            a: Int,
            b: Int,
            c: Int,
            d: Int,
            e: Int,
            f: Int,
            g: Int,
            h: Int,
            i: Int,
            j: Int,
            k: Int,
            m: Int,
            n: Int,
            o: Int,
            p: Int,
            q: Int,
            r: Int
        ): List<Int> {
            return listOf(
                44,
                a,
                0,
                0,
                0,
                0,
                24,
                b,
                n,
                0,
                c,
                d,
                0,
                0,
                0,
                1,
                0,
                239,
                e,
                o,
                f,
                g,
                0,
                0,
                0,
                0,
                h,
                0,
                0,
                14,
                i,
                j,
                0,
                k,
                m,
                3,
                p,
                1,
                q,
                1,
                r,
                0,
                0,
                0
            )
        }
    }

    /**
     * 严格对齐Python的sm3_to_arrayL0
     * 步骤：字符串→UTF8字节→字节列表→SM3哈希→十六进制字符串→按两位拆分转整数列表
     */
    fun sm3ToArrayL0(data: String): List<Long> {
        val utf8Bytes = data.toByteArray(StandardCharsets.UTF_8)

        val sm3Digest = SM3Digest()
        sm3Digest.update(utf8Bytes, 0, utf8Bytes.size)
        val hashBytes = ByteArray(sm3Digest.digestSize)
        sm3Digest.doFinal(hashBytes, 0)
        val hexStr = Hex.toHexString(hashBytes).lowercase()

        val result = mutableListOf<Long>()
        for (i in 0 until hexStr.length step 2) {
            val hexPair = hexStr.substring(i, i + 2)
            result.add(hexPair.toInt(16).toLong())
        }
        return result
    }

    /**
     * 严格对齐Python的sm3_to_arrayL1
     * 步骤：整数列表→字节数组→字节列表→SM3哈希→十六进制字符串→按两位拆分转整数列表
     */
    fun sm3ToArrayL1(data: List<Long>): List<Long> {
        val byteArray = ByteArray(data.size)
        data.forEachIndexed { index, value ->
            byteArray[index] = value.toByte()
        }

        val sm3Digest = SM3Digest()
        sm3Digest.update(byteArray, 0, byteArray.size)
        val hashBytes = ByteArray(sm3Digest.digestSize)
        sm3Digest.doFinal(hashBytes, 0)
        val hexStr = Hex.toHexString(hashBytes).lowercase()

        val result = mutableListOf<Long>()
        for (i in 0 until hexStr.length step 2) {
            val hexPair = hexStr.substring(i, i + 2)
            result.add(hexPair.toInt(16).toLong())
        }
        return result
    }
}
