package com.lightningstudio.watchrss.phoneconnection.provisioning

import org.json.JSONObject

/** Decisions are monotonic and bound to this encrypted session's identity. Release stays disabled. */
internal class PairingConsent(
    private val userId: String,
    private val deviceId: String,
    private val installId: String,
    val sas: String,
    val accountConflict: Boolean,
    private val clock: () -> Long,
    private val expires: Long = clock() + 90_000L
) {
    var phone: String = "pending"; private set
    var watch: String = "pending"; private set
    val bothConfirmed get() = phone == "confirmed" && watch == "confirmed" && !accountConflict
    val rejected get() = phone == "rejected" || watch == "rejected"
    private fun current() { check(clock() < expires) { "pairing_confirmation_expired" } }
    fun watchDecision(confirmed: Boolean) {
        current()
        val decision = if (confirmed) "confirmed" else "rejected"
        require(watch == "pending" || watch == decision) { "pairing_identity_changed" }
        watch = decision
    }
    fun update(request: JSONObject) {
        current()
        require(request.getString("userId") == userId) { "pairing_identity_changed" }
        val decision = request.getString("decision")
        require(decision in setOf("pending", "confirmed", "rejected"))
        require(phone == "pending" || phone == decision) { "pairing_identity_changed" }
        phone = decision
    }
    fun receipt(publicKeySpki: String): JSONObject {
        current()
        return JSONObject().put("success", true).put("userId", userId).put("deviceId", deviceId)
            .put("installId", installId).put("publicKeySpki", publicKeySpki).put("sas", sas)
            // Current backend main accepts only 0..15; do not claim the phone's 255-message contract.
            .put("pairingUxVersion", 1).put("messageSequenceMax", 15)
            .put("capabilities", JSONObject().put("pairingConfirmV1", true).put("pairingReplaceAccountV1", false))
            .put("accountConflict", accountConflict)
            .put("confirmation", JSONObject().put("phone", phone).put("watch", watch))
    }
}
