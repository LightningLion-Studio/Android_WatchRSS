package com.lightningstudio.watchrss.phoneconnection.bluetooth

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicInteger

class BluetoothSyncProtocolTest {
    @Test
    fun frameReadWrite_keepFastPathWithoutCallbacks() {
        val payload = JSONObject().apply {
            put("action", BluetoothSyncProtocol.ACTION_SYNC_LIBRARY)
            put("data", "x".repeat(80_000))
        }
        val output = ByteArrayOutputStream()

        BluetoothSyncProtocol.writeFrame(output, payload)
        val decoded = BluetoothSyncProtocol.readFrame(ByteArrayInputStream(output.toByteArray()))

        assertEquals(payload.toString(), decoded.toString())
        assertEquals(BluetoothSyncProtocol.wireSize(payload), output.size().toLong())
    }

    @Test
    fun frameReadWrite_reportTransferredBytesInChunksWhenRequested() {
        val payload = JSONObject().apply {
            put("action", BluetoothSyncProtocol.ACTION_SYNC_LIBRARY)
            put("data", "x".repeat(80_000))
        }
        val output = ByteArrayOutputStream()
        val writeDeltas = mutableListOf<Long>()

        BluetoothSyncProtocol.writeFrame(output, payload) { bytes ->
            writeDeltas += bytes
        }

        val readDeltas = mutableListOf<Long>()
        val decoded = BluetoothSyncProtocol.readFrame(ByteArrayInputStream(output.toByteArray())) { bytes ->
            readDeltas += bytes
        }

        assertEquals(payload.toString(), decoded.toString())
        assertEquals(BluetoothSyncProtocol.wireSize(payload), writeDeltas.sum())
        assertEquals(BluetoothSyncProtocol.wireSize(payload), readDeltas.sum())
        assertEquals(BluetoothSyncProtocol.LENGTH_PREFIX_BYTES.toLong(), writeDeltas.first())
        assertEquals(BluetoothSyncProtocol.LENGTH_PREFIX_BYTES.toLong(), readDeltas.first())
        assertTrue(writeDeltas.size > 2)
        assertTrue(readDeltas.size > 2)
    }

    @Test
    fun persistentSession_requiresBothCapabilityFlags() {
        val v14Request = BluetoothSyncProtocol.withPersistentSessionRequest(
            JSONObject().put("version", 14).put("action", BluetoothSyncProtocol.ACTION_SYNC_ACCOUNT)
        )
        val missingSupport = JSONObject(v14Request.toString()).apply {
            remove(BluetoothSyncProtocol.FIELD_SUPPORTS_PERSISTENT_SESSION)
        }
        val legacyV13 = JSONObject()
            .put("version", 13)
            .put("action", BluetoothSyncProtocol.ACTION_SYNC_LIBRARY)

        assertTrue(BluetoothSyncProtocol.requestsPersistentSession(v14Request))
        assertFalse(BluetoothSyncProtocol.requestsPersistentSession(missingSupport))
        assertFalse(BluetoothSyncProtocol.requestsPersistentSession(legacyV13))
        assertEquals(15 * 60 * 1_000L, BluetoothSyncProtocol.PERSISTENT_SESSION_IDLE_TIMEOUT_MS)
    }

    @Test
    fun sessionControl_roundTripsCompleteAndAbortAndRejectsUnknownValues() {
        listOf(
            BluetoothSyncProtocol.SESSION_PHASE_COMPLETE,
            BluetoothSyncProtocol.SESSION_PHASE_ABORT
        ).forEach { phase ->
            val response = BluetoothSyncProtocol.buildSessionControlResponse(14, phase)
            assertEquals(BluetoothSyncProtocol.ACTION_SYNC_SESSION, response.getString("action"))
            assertEquals(phase, BluetoothSyncProtocol.sessionControlPhase(response))
            assertTrue(BluetoothSyncProtocol.acceptsPersistentSession(response))
        }
        assertEquals(
            null,
            BluetoothSyncProtocol.sessionControlPhase(JSONObject().put("action", "futureAction"))
        )
        try {
            BluetoothSyncProtocol.sessionControlPhase(
                JSONObject()
                    .put("action", BluetoothSyncProtocol.ACTION_SYNC_SESSION)
                    .put("phase", "pause")
            )
            fail("unknown session phase should fail")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun frameRead_reportsEofWhenPeerClosesBetweenActions() {
        try {
            BluetoothSyncProtocol.readFrame(ByteArrayInputStream(ByteArray(0)))
            fail("closed peer should report EOF")
        } catch (_: EOFException) {
        }
    }

    @Test
    fun transportReadTimeout_closesBlockedTransportOnce() = runBlocking {
        val input = PipedInputStream()
        val output = PipedOutputStream(input)
        val closeCount = AtomicInteger()

        try {
            withTimeout(1_000L) {
                withTransportReadTimeout(
                    timeoutMs = 25L,
                    closeTransport = {
                        closeCount.incrementAndGet()
                        input.close()
                        output.close()
                    },
                    read = { BluetoothSyncProtocol.readFrame(input) }
                )
            }
            fail("blocked read should be interrupted by transport close")
        } catch (_: IOException) {
        }
        assertEquals(1, closeCount.get())
    }
}
