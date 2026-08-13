package com.lightningstudio.watchrss.phoneconnection.ip

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class IpTransportKind(val wireName: String, val priority: Int) {
    WIFI_LAN("wifiLan", 300),
    PHONE_HOTSPOT("phoneHotspot", 250),
    BLUETOOTH_BRIDGE("bluetoothBridge", 100),
    UNKNOWN_LOCAL("unknownLocal", 50);

    companion object {
        fun fromWireName(value: String): IpTransportKind =
            entries.firstOrNull { it.wireName == value } ?: UNKNOWN_LOCAL
    }
}

data class IpEndpointCandidate(
    val endpointId: String,
    val address: String,
    val family: String,
    val transportKind: IpTransportKind,
    val priority: Int
) {
    companion object {
        fun fromJson(json: JSONObject): IpEndpointCandidate {
            val kind = IpTransportKind.fromWireName(json.optString("transportKind"))
            return IpEndpointCandidate(
                endpointId = json.getString("endpointId"),
                address = json.getString("address"),
                family = json.optString("family", "ipv4"),
                transportKind = kind,
                priority = json.optInt("priority", kind.priority)
            )
        }
    }
}

data class IpEndpointDescriptor(
    val version: Int,
    val serverDeviceId: String,
    val epoch: Long,
    val expiresAt: Long,
    val port: Int,
    val endpoints: List<IpEndpointCandidate>,
    val challengeId: String,
    val challengeSecret: String,
    val hmac: String
) {
    fun canonicalPayload(): String = buildString {
        append(version).append('|')
        append(serverDeviceId).append('|')
        append(epoch).append('|')
        append(expiresAt).append('|')
        append(port).append('|')
        endpoints.sortedWith(
            compareByDescending<IpEndpointCandidate> { it.priority }.thenBy { it.endpointId }
        ).forEach { endpoint ->
            append(endpoint.endpointId).append(',')
            append(endpoint.address).append(',')
            append(endpoint.family).append(',')
            append(endpoint.transportKind.wireName).append(',')
            append(endpoint.priority).append(';')
        }
        append('|').append(challengeId).append('|').append(challengeSecret)
    }

    fun verify(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (
            version != IpSyncProtocol.VERSION || port !in 1..65535 ||
            expiresAt < nowMillis || endpoints.isEmpty()
        ) return false
        return IpSyncProtocol.constantTimeEquals(
            IpSyncProtocol.hmac(challengeSecret, canonicalPayload()),
            hmac
        )
    }

    fun withAdditionalEndpoints(additional: List<IpEndpointCandidate>): IpEndpointDescriptor =
        copy(
            endpoints = (endpoints + additional)
                .distinctBy { it.address }
                .sortedByDescending { it.priority }
        )

    companion object {
        fun fromJson(json: JSONObject): IpEndpointDescriptor {
            val array = json.optJSONArray("endpoints") ?: json.optJSONArray("a") ?: JSONArray()
            val endpoints = buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let { candidate ->
                        runCatching { add(IpEndpointCandidate.fromJson(candidate)) }
                    } ?: array.optJSONArray(index)?.let { compact ->
                        val kind = IpTransportKind.fromWireName(compact.optString(2))
                        if (compact.length() >= 4) {
                            add(
                                IpEndpointCandidate(
                                    endpointId = compact.optString(0),
                                    address = compact.optString(1),
                                    family = "ipv4",
                                    transportKind = kind,
                                    priority = compact.optInt(3, kind.priority)
                                )
                            )
                        }
                    }
                }
            }
            return IpEndpointDescriptor(
                version = json.optInt("version", json.optInt("v")),
                serverDeviceId = json.optString("serverDeviceId", json.optString("id")),
                epoch = json.optLong("epoch", json.optLong("e")),
                expiresAt = json.optLong("expiresAt", json.optLong("x")),
                port = json.optInt("port", json.optInt("p")),
                endpoints = endpoints,
                challengeId = json.optString("challengeId", json.optString("c")),
                challengeSecret = json.optString("challengeSecret", json.optString("k")),
                hmac = json.optString("hmac", json.optString("h"))
            )
        }
    }
}

data class IpHelloAck(
    val serverDeviceId: String,
    val sessionId: String,
    val routeKind: IpTransportKind,
    val acceptedResumeSeq: Long,
    val serverNonce: String,
    val hmac: String
) {
    fun canonicalPayload(): String = listOf(
        IpSyncProtocol.VERSION.toString(),
        serverDeviceId,
        sessionId,
        routeKind.wireName,
        acceptedResumeSeq.toString(),
        serverNonce
    ).joinToString("|")

    companion object {
        fun fromJson(json: JSONObject): IpHelloAck = IpHelloAck(
            serverDeviceId = json.optString("serverDeviceId"),
            sessionId = json.optString("sessionId"),
            routeKind = IpTransportKind.fromWireName(json.optString("routeKind")),
            acceptedResumeSeq = json.optLong("acceptedResumeSeq"),
            serverNonce = json.optString("serverNonce"),
            hmac = json.optString("hmac")
        )
    }
}

object IpSyncProtocol {
    const val VERSION = 2
    const val TYPE_HELLO = "hello"
    const val TYPE_HELLO_ACK = "helloAck"
    const val TYPE_ERROR = "error"
    const val SERVICE_TYPE = "_watchrss-sync._tcp."
    const val WIFI_LAN_PROBE_TIMEOUT_MS = 1_000L
    const val FALLBACK_IP_PROBE_TIMEOUT_MS = 4_000L
    const val FAILED_ENDPOINT_COOLDOWN_MS = 10_000L
    const val MAX_PROBE_CANDIDATES = 3
    val ADVERTISED_BLE_SERVICE_UUID: UUID =
        UUID.fromString("7e57c201-1f7d-4f0b-9f3d-2d7d3a65b201")
    val BLE_DISCOVERY_SERVICE_UUID: UUID =
        UUID.fromString("7e57d001-1f7d-4f0b-9f3d-2d7d3a65d001")
    val BLE_ENDPOINT_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("7e57d001-1f7d-4f0b-9f3d-2d7d3a65d002")

    fun buildHello(
        descriptor: IpEndpointDescriptor,
        watchDeviceId: String,
        clientNonce: String,
        resumeSessionId: String?,
        lastAckSeq: Long
    ): JSONObject {
        val canonical = listOf(
            VERSION.toString(),
            watchDeviceId,
            descriptor.challengeId,
            descriptor.epoch.toString(),
            clientNonce,
            resumeSessionId.orEmpty(),
            lastAckSeq.toString()
        ).joinToString("|")
        return JSONObject().apply {
            put("type", TYPE_HELLO)
            put("version", VERSION)
            put("watchDeviceId", watchDeviceId)
            put("challengeId", descriptor.challengeId)
            put("endpointEpoch", descriptor.epoch)
            put("clientNonce", clientNonce)
            resumeSessionId?.let { put("resumeSessionId", it) }
            put("lastAckSeq", lastAckSeq)
            put("hmac", hmac(descriptor.challengeSecret, canonical))
        }
    }

    fun verifyAck(descriptor: IpEndpointDescriptor, ack: IpHelloAck): Boolean =
        ack.serverDeviceId == descriptor.serverDeviceId &&
            ack.sessionId.isNotBlank() &&
            constantTimeEquals(hmac(descriptor.challengeSecret, ack.canonicalPayload()), ack.hmac)

    fun hmac(base64Key: String, payload: String): String {
        val key = Base64.getUrlDecoder().decode(base64Key)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return Base64.getUrlEncoder().encodeToString(
            mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        )
    }

    fun constantTimeEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(
            left.toByteArray(Charsets.US_ASCII),
            right.toByteArray(Charsets.US_ASCII)
        )
}
