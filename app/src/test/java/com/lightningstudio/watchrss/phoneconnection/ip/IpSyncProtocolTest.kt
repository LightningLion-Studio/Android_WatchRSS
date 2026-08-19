package com.lightningstudio.watchrss.phoneconnection.ip

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class IpSyncProtocolTest {
    @Test
    fun websocketClientDoesNotPingDuringBackpressuredTransfers() {
        val client = buildWatchIpSyncWebSocketClient(probeTimeoutMs = 2_500L)

        try {
            assertEquals(2_500, client.connectTimeoutMillis)
            assertEquals(0, client.readTimeoutMillis)
            assertEquals(0, client.pingIntervalMillis)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun parsesAndVerifiesPhoneCompactDescriptor() {
        val unsigned = descriptor(hmac = "")
        val signed = unsigned.copy(
            hmac = IpSyncProtocol.hmac(unsigned.challengeSecret, unsigned.canonicalPayload())
        )
        val compact = JSONObject().apply {
            put("v", signed.version)
            put("id", signed.serverDeviceId)
            put("e", signed.epoch)
            put("x", signed.expiresAt)
            put("p", signed.port)
            put("a", org.json.JSONArray().apply {
                signed.endpoints.forEach { endpoint ->
                    put(
                        org.json.JSONArray()
                            .put(endpoint.endpointId)
                            .put(endpoint.address)
                            .put(endpoint.transportKind.wireName)
                            .put(endpoint.priority)
                    )
                }
            })
            put("c", signed.challengeId)
            put("k", signed.challengeSecret)
            put("h", signed.hmac)
        }

        val decoded = IpEndpointDescriptor.fromJson(compact)
        assertEquals(signed, decoded)
        assertTrue(decoded.verify(1_700_000_000_000L))
        assertFalse(decoded.copy(epoch = 8).verify(1_700_000_000_000L))
        assertFalse(decoded.verify(decoded.expiresAt + 1))
    }

    @Test
    fun helloAndResumeAckUseSameAuthenticatedCanonicalFields() {
        val unsigned = descriptor(hmac = "")
        val descriptor = unsigned.copy(
            hmac = IpSyncProtocol.hmac(unsigned.challengeSecret, unsigned.canonicalPayload())
        )
        val hello = IpSyncProtocol.buildHello(
            descriptor = descriptor,
            watchDeviceId = "watch-device",
            clientNonce = "client-nonce",
            resumeSessionId = "session-1",
            lastAckSeq = 42
        )
        assertEquals(
            IpSyncProtocol.hmac(
                descriptor.challengeSecret,
                "2|watch-device|challenge-1|7|client-nonce|session-1|42"
            ),
            hello.getString("hmac")
        )

        val unsignedAck = IpHelloAck(
            serverDeviceId = descriptor.serverDeviceId,
            sessionId = "session-1",
            routeKind = IpTransportKind.WIFI_LAN,
            acceptedResumeSeq = 42,
            serverNonce = "server-nonce",
            hmac = ""
        )
        val ack = unsignedAck.copy(
            hmac = IpSyncProtocol.hmac(descriptor.challengeSecret, unsignedAck.canonicalPayload())
        )
        assertTrue(IpSyncProtocol.verifyAck(descriptor, ack))
        assertFalse(IpSyncProtocol.verifyAck(descriptor, ack.copy(acceptedResumeSeq = 41)))
    }

    private fun descriptor(hmac: String): IpEndpointDescriptor = IpEndpointDescriptor(
        version = IpSyncProtocol.VERSION,
        serverDeviceId = "phone-device",
        epoch = 7,
        expiresAt = 1_800_000_000_000L,
        port = 31_337,
        endpoints = listOf(
            IpEndpointCandidate(
                endpointId = "wifi",
                address = "192.168.1.2",
                family = "ipv4",
                transportKind = IpTransportKind.WIFI_LAN,
                priority = IpTransportKind.WIFI_LAN.priority
            ),
            IpEndpointCandidate(
                endpointId = "bridge",
                address = "192.168.7.1",
                family = "ipv4",
                transportKind = IpTransportKind.BLUETOOTH_BRIDGE,
                priority = IpTransportKind.BLUETOOTH_BRIDGE.priority
            )
        ),
        challengeId = "challenge-1",
        challengeSecret = Base64.getUrlEncoder().encodeToString(ByteArray(32) { it.toByte() }),
        hmac = hmac
    )
}
