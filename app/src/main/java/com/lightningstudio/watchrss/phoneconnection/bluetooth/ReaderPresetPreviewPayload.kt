package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.data.reader.ReaderPresetCodec
import com.lightningstudio.watchrss.data.reader.WatchReaderPresetPreviewSession
import org.json.JSONObject

object ReaderPresetPreviewPayload {
    const val VERSION = 2
    private const val LEGACY_VERSION = 1
    const val PHASE_UPDATE = "update"
    const val PHASE_RESOURCE_HANDOFF = "resourceHandoff"
    const val PHASE_HEARTBEAT = "heartbeat"
    const val PHASE_STOP = "stop"

    fun handle(
        request: JSONObject,
        session: WatchReaderPresetPreviewSession
    ): JSONObject {
        val version = request.optInt("version")
        require(version in LEGACY_VERSION..VERSION) { "不支持的阅读器实时预览版本" }
        val sessionId = request.optString("sessionId").trim()
        require(sessionId.isNotBlank()) { "缺少预览会话 ID" }
        return when (val phase = request.optString("phase")) {
            PHASE_UPDATE,
            PHASE_RESOURCE_HANDOFF -> {
                val sequence = request.getLong("sequence")
                val resourceTransfer =
                    phase == PHASE_RESOURCE_HANDOFF || request.optBoolean("resourceTransfer")
                val applied = when {
                    request.has("preset") -> session.update(
                        sessionId = sessionId,
                        sequence = sequence,
                        preset = ReaderPresetCodec.decode(request.getJSONObject("preset").toString()),
                        resourceTransferInProgress = resourceTransfer
                    )
                    request.has("changes") -> session.updateDelta(
                        sessionId = sessionId,
                        sequence = sequence,
                        changes = request.getJSONObject("changes"),
                        resourceTransferInProgress = resourceTransfer
                    )
                    else -> session.update(
                        sessionId = sessionId,
                        sequence = sequence,
                        preset = ReaderPresetCodec.decode(request.getString("presetJson")),
                        resourceTransferInProgress = resourceTransfer
                    )
                }
                response(sessionId, phase).apply {
                    put("sequence", sequence)
                    put("applied", applied)
                }
            }

            PHASE_HEARTBEAT -> response(sessionId, phase).apply {
                put("sequence", request.optLong("sequence"))
                session.heartbeat(sessionId, request.optLong("sequence"))?.let {
                    put("appliedSequence", it)
                }
            }

            PHASE_STOP -> response(sessionId, phase).apply {
                put("applied", session.stop(sessionId))
            }

            else -> error("未知的阅读器实时预览阶段")
        }
    }

    private fun response(sessionId: String, phase: String): JSONObject = JSONObject().apply {
        put("success", true)
        put("action", BluetoothSyncProtocol.ACTION_PREVIEW_READER)
        put("phase", phase)
        put("sessionId", sessionId)
    }
}
