package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.data.reader.ReaderPresetCodec
import com.lightningstudio.watchrss.data.reader.WatchReaderPresetPreviewSession
import org.json.JSONObject

object ReaderPresetPreviewPayload {
    const val VERSION = 1
    const val PHASE_UPDATE = "update"
    const val PHASE_STOP = "stop"

    fun handle(
        request: JSONObject,
        session: WatchReaderPresetPreviewSession
    ): JSONObject {
        require(request.optInt("version") == VERSION) { "不支持的阅读器实时预览版本" }
        val sessionId = request.optString("sessionId").trim()
        require(sessionId.isNotBlank()) { "缺少预览会话 ID" }
        return when (val phase = request.optString("phase")) {
            PHASE_UPDATE -> {
                val sequence = request.getLong("sequence")
                val preset = ReaderPresetCodec.decode(request.getString("presetJson"))
                val applied = session.update(sessionId, sequence, preset)
                JSONObject().apply {
                    put("success", true)
                    put("action", BluetoothSyncProtocol.ACTION_PREVIEW_READER)
                    put("phase", phase)
                    put("sessionId", sessionId)
                    put("sequence", sequence)
                    put("applied", applied)
                }
            }

            PHASE_STOP -> JSONObject().apply {
                put("success", true)
                put("action", BluetoothSyncProtocol.ACTION_PREVIEW_READER)
                put("phase", phase)
                put("sessionId", sessionId)
                put("applied", session.stop(sessionId))
            }

            else -> error("未知的阅读器实时预览阶段")
        }
    }
}
