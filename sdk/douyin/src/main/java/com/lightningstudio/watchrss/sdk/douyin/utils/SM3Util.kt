package com.lightningstudio.watchrss.sdk.douyin.utils

import org.bouncycastle.crypto.digests.SM3Digest
import org.bouncycastle.util.encoders.Hex

/**
 * SM3加密工具类
 * 使用BouncyCastle库实现SM3算法
 */
object SM3Util {

    /**
     * 计算字符串的SM3哈希值
     *
     * @param data 输入字符串
     * @return 32字节的哈希值
     */
    fun hash(data: String): ByteArray {
        return hash(data.toByteArray(Charsets.UTF_8))
    }

    /**
     * 计算字节数组的SM3哈希值
     *
     * @param data 输入字节数组
     * @return 32字节的哈希值
     */
    fun hash(data: ByteArray): ByteArray {
        val digest = SM3Digest()
        digest.update(data, 0, data.size)
        val hash = ByteArray(digest.digestSize)
        digest.doFinal(hash, 0)
        return hash
    }

    /**
     * 计算字符串的SM3哈希值并返回十六进制字符串
     *
     * @param data 输入字符串
     * @return 64字符的十六进制字符串
     */
    fun hashToHex(data: String): String {
        return Hex.toHexString(hash(data))
    }

    /**
     * 计算字节数组的SM3哈希值并返回十六进制字符串
     *
     * @param data 输入字节数组
     * @return 64字符的十六进制字符串
     */
    fun hashToHex(data: ByteArray): String {
        return Hex.toHexString(hash(data))
    }

    /**
     * 双重SM3哈希（哈希的哈希）
     *
     * @param data 输入字符串
     * @return 32字节的哈希值
     */
    fun doubleHash(data: String): ByteArray {
        val firstHash = hash(data)
        return hash(firstHash)
    }

    /**
     * 双重SM3哈希并返回十六进制字符串
     *
     * @param data 输入字符串
     * @return 64字符的十六进制字符串
     */
    fun doubleHashToHex(data: String): String {
        return Hex.toHexString(doubleHash(data))
    }
}
