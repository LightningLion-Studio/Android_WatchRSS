package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.data.reader.ReaderPreset
import com.lightningstudio.watchrss.data.reader.ReaderPresetCodec
import com.lightningstudio.watchrss.data.reader.WatchReaderPresetPreviewSession
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPresetPreviewPayloadTest {
    @Test
    fun updateAndStopApplyOnlyToPreviewSession() = runTest {
        val session = WatchReaderPresetPreviewSession(this)
        val preset = ReaderPreset.lightDefault(id = "draft", name = "手机草稿")
        val update = JSONObject().apply {
            put("version", ReaderPresetPreviewPayload.VERSION)
            put("action", BluetoothSyncProtocol.ACTION_PREVIEW_READER)
            put("phase", ReaderPresetPreviewPayload.PHASE_UPDATE)
            put("sessionId", "session")
            put("sequence", 4L)
            put("presetJson", ReaderPresetCodec.encode(preset))
        }

        val updateResponse = ReaderPresetPreviewPayload.handle(update, session)

        assertTrue(updateResponse.getBoolean("success"))
        assertTrue(updateResponse.getBoolean("applied"))
        assertEquals("手机草稿", session.state.value?.preset?.name)
        assertFalse(session.state.value?.resourceTransferInProgress == true)

        val transferResponse = ReaderPresetPreviewPayload.handle(
            JSONObject(update.toString())
                .put("sequence", 5L)
                .put("resourceTransfer", true),
            session
        )
        assertTrue(transferResponse.getBoolean("applied"))
        assertTrue(session.state.value?.resourceTransferInProgress == true)

        val staleResponse = ReaderPresetPreviewPayload.handle(
            JSONObject(update.toString()).put("sequence", 4L),
            session
        )
        assertFalse(staleResponse.getBoolean("applied"))
        assertEquals(5L, session.state.value?.sequence)

        val stopResponse = ReaderPresetPreviewPayload.handle(
            JSONObject().apply {
                put("version", ReaderPresetPreviewPayload.VERSION)
                put("action", BluetoothSyncProtocol.ACTION_PREVIEW_READER)
                put("phase", ReaderPresetPreviewPayload.PHASE_STOP)
                put("sessionId", "session")
            },
            session
        )
        assertTrue(stopResponse.getBoolean("success"))
        assertNull(session.state.value)
    }
}
