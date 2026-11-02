package com.lightningstudio.watchrss.data.cloud

import com.lightningstudio.watchrss.data.account.WatchAccountState
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

/** One requester transcript. No decrypt/install occurs before local code confirmation. */
internal class WatchCloudApprovalV2(
    val account: WatchAccountState,
    private val deviceId: String,
    private val spki: String,
    val epoch: String,
    private val get: suspend (String) -> JSONObject,
    private val post: suspend (String, JSONObject) -> JSONObject,
    private val checkOwner: () -> Unit,
    private val install: suspend (String) -> Unit
) {
    private val nonce = ByteArray(32).also(SecureRandom()::nextBytes)
    private var id = ""
    private var pinned: JSONObject? = null
    private var transcript: ByteArray? = null
    private var delivery: Pair<String, String>? = null
    private var confirmed = false
    private var finished = false
    private fun path() = "/functions/v1/cloud/approvals/v2/$id"
    suspend fun begin(): String {
        checkOwner()
        val result = post("/functions/v1/cloud/approvals/v2", JSONObject().put("deviceId", deviceId)
            .put("commitment", WatchCloudProtocolV2.approvalCommitment(Base64.getDecoder().decode(spki), nonce)))
        checkOwner()
        id = result.getString("requestId").also { WatchCloudProtocolV2.uuid(it) }
        return id
    }
    suspend fun poll(): String? {
        checkOwner(); check(!finished)
        val remote = get(path()); checkOwner()
        require(remote.optString("status") !in setOf("expired", "cancelled")) { "approval_expired" }
        if (!remote.has("approverSpki") || remote.isNull("approverSpki")) return null
        val fields = listOf("approverSpki", "approverDeviceId", "approverNonce")
        val previous = pinned
        if (previous == null) pinned = JSONObject().apply { fields.forEach { put(it, remote.getString(it)) } }
        else require(fields.all { previous.getString(it) == remote.getString(it) }) { "授权会话发生变化" }
        if (remote.has("requesterNonce") && !remote.isNull("requesterNonce")) {
            require(java.security.MessageDigest.isEqual(nonce, Base64.getDecoder().decode(remote.getString("requesterNonce"))))
        } else post(path() + "/reveal", JSONObject().put("requesterNonce", Base64.getEncoder().encodeToString(nonce)))
        checkOwner()
        val row = requireNotNull(pinned)
        val next = WatchCloudProtocolV2.transcript(account.userId, epoch, id, deviceId, Base64.getDecoder().decode(spki),
            row.getString("approverDeviceId"), Base64.getDecoder().decode(row.getString("approverSpki")),
            nonce, Base64.getDecoder().decode(row.getString("approverNonce")))
        transcript?.let { require(it.contentEquals(next)) { "授权会话发生变化" } }
        transcript = next
        if (remote.optString("status") == "delivered") delivery = remote.getString("payload") to remote.getString("signature")
        acceptIfConfirmed()
        return WatchCloudProtocolV2.code(next)
    }
    suspend fun confirm(code: String): Boolean {
        val local = poll() ?: error("请等待手机 App 或小程序开始授权")
        require(WatchCloudProtocolV2.equal(code, local)) { "配对码不一致，请取消后重新授权" }
        confirmed = true
        acceptIfConfirmed()
        return finished
    }
    val delivered get() = finished
    private suspend fun acceptIfConfirmed() {
        if (!confirmed || finished) return
        val (payload, signature) = delivery ?: return
        require(WatchCloudProtocolV2.verify(Base64.getDecoder().decode(requireNotNull(pinned).getString("approverSpki")),
            requireNotNull(transcript), payload, Base64.getDecoder().decode(signature))) { "授权签名验证失败" }
        checkOwner(); install(payload); checkOwner()
        finished = true; nonce.fill(0)
    }
    suspend fun cancel() {
        try { if (!finished && id.isNotBlank()) { checkOwner(); post(path() + "/cancel", JSONObject()) } }
        finally { nonce.fill(0); delivery = null; confirmed = false; finished = true }
    }
}
