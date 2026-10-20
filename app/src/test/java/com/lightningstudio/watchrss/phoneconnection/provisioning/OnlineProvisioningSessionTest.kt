package com.lightningstudio.watchrss.phoneconnection.provisioning

import java.io.IOException
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OnlineProvisioningSessionTest {
    private val watchToken = "a".repeat(64)
    private val claimToken = "b".repeat(64)

    /** Minimal relay: holds one phone request until the watch answers it. */
    private inner class Relay : OnlineProvisioningSession.Http {
        val calls = mutableListOf<Triple<String, String, String?>>()
        val bodies = mutableListOf<String>()
        var request: JSONObject? = null
        var responseStatus = ArrayDeque<Any>()
        var sessionId = ""
        val closed = CountDownLatch(1)
        val aborts = AtomicLong()

        override fun exchange(method: String, path: String, token: String?, body: ByteArray?): OnlineProvisioningSession.Reply {
            calls += Triple(method, path, token)
            body?.let { bodies += String(it) }
            fun ok(value: JSONObject) = OnlineProvisioningSession.Reply(200, value.toString().toByteArray())
            return when {
                method == "POST" && path == OnlineProvisioningSession.SESSIONS -> {
                    sessionId = JSONObject(String(body!!)).getString("sessionId")
                    ok(JSONObject().put("sessionId", sessionId).put("watchToken", watchToken).put("claimToken", claimToken).put("expiresAt", 1_900_000_000_000))
                }
                path.endsWith("/request") -> ok(JSONObject().put("wire", request ?: JSONObject.NULL))
                path.endsWith("/response") -> when (val next = responseStatus.removeFirstOrNull()) {
                    is IOException -> throw next
                    is Int -> OnlineProvisioningSession.Reply(next, ByteArray(0))
                    else -> ok(JSONObject().put("accepted", true))
                }
                path.endsWith("/close") -> { closed.countDown(); ok(JSONObject().put("closed", true)) }
                else -> OnlineProvisioningSession.Reply(404, ByteArray(0))
            }
        }

        override fun abort() { aborts.incrementAndGet() }
    }

    private fun session(relay: Relay, clock: AtomicLong = AtomicLong(0)) =
        OnlineProvisioningSession("watch-device", "install-1", relay, { clock.get() }, { it.run() })

    private fun descriptor(qr: String): JSONObject {
        assertTrue(qr.startsWith("wrss-pair:"))
        return JSONObject(String(Base64.getUrlDecoder().decode(qr.removePrefix("wrss-pair:"))))
    }

    private fun phoneWire(secret: ByteArray, sessionId: String, seq: Int, message: JSONObject): JSONObject {
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = BleSetupCrypto.crypt(secret, sessionId, Cipher.ENCRYPT_MODE, "phone", seq, nonce, message.toString().toByteArray())
        return JSONObject().put("seq", seq).put("n", Base64.getEncoder().encodeToString(nonce)).put("c", Base64.getEncoder().encodeToString(cipher))
    }

    @Test
    fun abortedHttp_rejectsNewWorkBeforeConnecting() {
        val http = OnlineProvisioningSession.UrlHttp()
        http.abort()
        try {
            http.exchange("GET", OnlineProvisioningSession.SESSIONS + "/closed/request", "x", null)
            fail("closed transport must not reconnect")
        } catch (expected: IOException) {
            assertEquals("Pairing closed", expected.message)
        }
    }

    @Test
    fun start_keepsSecretOffTheRelayAndPutsOnlyClaimTokenInQr() {
        val relay = Relay()
        val session = session(relay).apply { start() }
        val qr = descriptor(session.qr())
        assertEquals(OnlineProvisioningSession.PROTOCOL, qr.getString("protocol"))
        assertEquals("internet", qr.getString("transport"))
        assertEquals(session.sessionId(), qr.getString("sessionId"))
        assertEquals(22, session.sessionId().length)
        assertEquals(claimToken, qr.getString("claimToken"))
        assertFalse(qr.toString().contains(watchToken))
        val secret = qr.getString("secret")
        assertEquals(32, Base64.getDecoder().decode(secret).size)
        assertTrue(relay.bodies.none { it.contains(secret) })
        assertEquals(setOf("sessionId", "deviceId", "installId"), JSONObject(relay.bodies.single()).keys().asSequence().toSet())
        session.close()
    }

    @Test
    fun roundTrip_usesExistingBleEnvelopeInBothDirections() {
        val relay = Relay()
        val session = session(relay).apply { start() }
        val secret = Base64.getDecoder().decode(descriptor(session.qr()).getString("secret"))
        relay.request = phoneWire(secret, session.sessionId(), 0, JSONObject().put("action", "prepare"))

        val received = session.receive()
        assertEquals("prepare", received.getString("action"))
        assertEquals(0, received.getInt("_sequence"))
        assertTrue(relay.calls.filter { it.second.endsWith("/request") }.all { it.third == watchToken })

        session.respond(0, JSONObject().put("success", true))
        val posted = JSONObject(relay.bodies.last())
        val wire = posted.getJSONObject("wire")
        assertEquals(0, posted.getInt("seq"))
        val plain = BleSetupCrypto.crypt(
            secret, session.sessionId(), Cipher.DECRYPT_MODE, "watch", 0,
            Base64.getDecoder().decode(wire.getString("n")), Base64.getDecoder().decode(wire.getString("c"))
        )
        assertTrue(JSONObject(String(plain)).getBoolean("success"))
        session.close()
    }

    @Test
    fun uncertainAcknowledgement_retriesIdenticalCiphertext() {
        val relay = Relay()
        val session = session(relay).apply { start() }
        val secret = Base64.getDecoder().decode(descriptor(session.qr()).getString("secret"))
        relay.request = phoneWire(secret, session.sessionId(), 0, JSONObject().put("action", "prepare"))
        session.receive()
        relay.responseStatus.addLast(IOException("timeout"))
        relay.responseStatus.addLast(503)

        session.respond(0, JSONObject().put("success", true))
        val responses = relay.calls.indices.filter { relay.calls[it].second.endsWith("/response") }
        assertEquals(3, responses.size)
        val sent = relay.bodies.takeLast(3)
        assertTrue(sent.all { it == sent.first() })
        session.close()
    }

    @Test
    fun lingeringAnsweredRequest_isIgnoredUntilNextSequence() {
        val relay = Relay()
        val session = session(relay).apply { start() }
        val secret = Base64.getDecoder().decode(descriptor(session.qr()).getString("secret"))
        val first = phoneWire(secret, session.sessionId(), 0, JSONObject().put("action", "prepare"))
        relay.request = first
        session.receive()
        session.respond(0, JSONObject().put("success", true))
        val next = phoneWire(secret, session.sessionId(), 1, JSONObject().put("action", "configure"))
        val swap = Thread { Thread.sleep(200); relay.request = next }.apply { start() }
        assertEquals("configure", session.receive().getString("action"))
        swap.join()
        session.close()
    }

    @Test
    fun expiredRelay_closesWithExpiredReasonAndBestEffortClose() {
        val relay = Relay()
        val session = session(relay).apply { start() }
        val secret = Base64.getDecoder().decode(descriptor(session.qr()).getString("secret"))
        relay.request = phoneWire(secret, session.sessionId(), 0, JSONObject().put("action", "prepare"))
        session.receive()
        relay.responseStatus.addLast(410)
        try {
            session.respond(0, JSONObject().put("success", true)); fail()
        } catch (failure: ProvisioningSession.Failure) {
            assertEquals(ProvisioningSession.Failure.EXPIRED, failure.reason)
        }
        assertTrue(relay.closed.await(1, TimeUnit.SECONDS))
        assertEquals(watchToken, relay.calls.last().third)
        try { session.qr(); fail() } catch (_: ProvisioningSession.Failure) {}
    }

    @Test
    fun tamperedCiphertext_isRejected() {
        val relay = Relay()
        val session = session(relay).apply { start() }
        val secret = Base64.getDecoder().decode(descriptor(session.qr()).getString("secret"))
        val wire = phoneWire(secret, session.sessionId(), 0, JSONObject().put("action", "prepare"))
        val bytes = Base64.getDecoder().decode(wire.getString("c")).also { it[0] = (it[0].toInt() xor 1).toByte() }
        relay.request = wire.put("c", Base64.getEncoder().encodeToString(bytes))
        try { session.receive(); fail() } catch (failure: ProvisioningSession.Failure) {
            assertEquals(ProvisioningSession.Failure.REJECTED, failure.reason)
        }
    }

    @Test
    fun close_unblocksPollingImmediately() {
        val relay = Relay()
        val session = session(relay).apply { start() }
        val done = CountDownLatch(1)
        var reason: String? = null
        Thread {
            try { session.receive() } catch (failure: ProvisioningSession.Failure) { reason = failure.reason }
            done.countDown()
        }.start()
        Thread.sleep(100)
        session.close()
        assertTrue(done.await(500, TimeUnit.MILLISECONDS))
        assertEquals(ProvisioningSession.Failure.CLOSED, reason)
        assertTrue(relay.aborts.get() > 0)
    }

    @Test
    fun deadline_expiresWithoutNetwork() {
        val relay = Relay()
        val clock = AtomicLong(0)
        val session = session(relay, clock).apply { start() }
        clock.set(OnlineProvisioningSession.LIFETIME_MS)
        try { session.receive(); fail() } catch (failure: ProvisioningSession.Failure) {
            assertEquals(ProvisioningSession.Failure.EXPIRED, failure.reason)
        }
    }

    @Test
    fun startOffline_reportsOffline() {
        val session = OnlineProvisioningSession("watch-device", "install-1", object : OnlineProvisioningSession.Http {
            override fun exchange(method: String, path: String, token: String?, body: ByteArray?): OnlineProvisioningSession.Reply = throw IOException("down")
            override fun abort() = Unit
        }, { 0L }, { it.run() })
        try { session.start(); fail() } catch (failure: ProvisioningSession.Failure) {
            assertEquals(ProvisioningSession.Failure.OFFLINE, failure.reason)
        }
    }
}
