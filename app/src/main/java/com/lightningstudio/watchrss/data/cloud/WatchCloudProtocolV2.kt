package com.lightningstudio.watchrss.data.cloud

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A compatibility failure must stop publication, without resetting local data or keys. */
internal class WatchCloudUpgradeRequired : java.io.IOException("云端格式需要更新手表应用；本机资料和待同步内容已保留")

internal object WatchCloudProtocolV2 {
    fun lp(vararg fields: Any): ByteArray = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { data -> fields.forEach {
            val bytes = if (it is ByteArray) it else it.toString().toByteArray(Charsets.UTF_8)
            data.writeInt(bytes.size); data.write(bytes)
        } }
    }.toByteArray()
    fun mac(key: ByteArray, vararg fields: Any): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(lp(*fields))
    }.joinToString("") { "%02x".format(it) }
    fun commitment(key: ByteArray, user: String, epoch: String, version: Int) =
        mac(key, "watchrss/key-commit/v2", user, epoch, version)
    fun attestation(key: ByteArray, user: String, epoch: String, device: String, spki: String) =
        mac(key, "watchrss/device-attest/v2", user, epoch, device,
            MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(spki)))
    fun equal(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
    fun uuid(value: String): String = value.also { require(UUID.fromString(it).toString() == it) { "云端身份版本无效" } }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    fun approvalCommitment(spki: ByteArray, nonce: ByteArray): String {
        require(nonce.size == 32)
        return hex(sha(lp("watchrss/approval-commit/v2", spki, nonce)))
    }
    fun transcript(user: String, epoch: String, request: String, requester: String, requesterSpki: ByteArray,
                   approver: String, approverSpki: ByteArray, requesterNonce: ByteArray, approverNonce: ByteArray): ByteArray {
        require(requesterNonce.size == 32 && approverNonce.size == 32)
        return lp("watchrss/approval/v2", user, epoch, request, requester, requesterSpki, approver, approverSpki, requesterNonce, approverNonce)
    }
    fun code(transcript: ByteArray): String = BigInteger(1, sha(transcript).copyOfRange(0, 8))
        .mod(BigInteger.valueOf(100000000)).toString().padStart(8, '0')
    private fun delivery(transcript: ByteArray, payload: String) =
        lp("watchrss/approval-deliver/v2", sha(transcript), payload.toByteArray(Charsets.UTF_8))
    fun verify(spki: ByteArray, transcript: ByteArray, payload: String, signature: ByteArray): Boolean = runCatching {
        require(signature.size == 64)
        fun integer(bytes: ByteArray): ByteArray {
            val positive = BigInteger(1, bytes).toByteArray()
            return byteArrayOf(2, positive.size.toByte()) + positive
        }
        val content = integer(signature.copyOfRange(0, 32)) + integer(signature.copyOfRange(32, 64))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki)))
            update(delivery(transcript, payload)); verify(byteArrayOf(0x30, content.size.toByte()) + content)
        }
    }.getOrDefault(false)
    fun features(json: JSONObject) {
        if (json.has("features")) {
            val features = json.get("features")
            if (features !is JSONObject || features.keys().asSequence().any { name ->
                    name !in setOf("incompat", "roCompat", "compat") ||
                        features.opt(name) !is JSONArray || features.getJSONArray(name).length() != 0
                }) throw WatchCloudUpgradeRequired()
        }
        if (json.has("requiredFeatures")) {
            val raw = json.get("requiredFeatures")
            // No optional protocol declaration is silently treated as supported.
            if (raw !is JSONArray || raw.length() != 0) throw WatchCloudUpgradeRequired()
        }
    }

    fun validateKeys(user: String, epoch: String, floor: Int, rows: JSONArray, keys: Map<Int, ByteArray>) {
        require(epoch.isNotBlank() && rows.length() > 0 && keys.isNotEmpty()) { "请用手机 App 或小程序恢复云密钥" }
        val ordered = (0 until rows.length()).map(rows::getJSONObject).sortedBy { it.getInt("keyVersion") }
        val versions = ordered.map { it.getInt("keyVersion") }
        require(versions.distinct().size == versions.size && versions.first() > floor &&
            ordered.first().isNull("rotationProof") && versions.zipWithNext().all { (a, b) -> b == a + 1 }) { "云密钥版本链无效" }
        require(versions.first() in keys && keys.keys.all { it in versions }) { "云密钥版本缺失" }
        for ((version, key) in keys) {
            require(key.size == 32)
            val row = ordered.first { it.getInt("keyVersion") == version }
            require(equal(row.getString("commitment"), commitment(key, user, epoch, version))) { "云密钥承诺不匹配" }
            if (version != versions.first()) {
                val previous = keys[version - 1] ?: error("缺少历史云密钥")
                require(equal(row.getString("rotationProof"), mac(previous, "watchrss/key-rotate/v2", user, epoch,
                    version, row.getString("commitment")))) { "云密钥版本链无效" }
            }
        }
    }
}
