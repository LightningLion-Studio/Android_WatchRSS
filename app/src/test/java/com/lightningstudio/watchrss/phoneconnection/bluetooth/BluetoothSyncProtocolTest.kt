package com.lightningstudio.watchrss.phoneconnection.bluetooth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
}
