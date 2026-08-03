package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.data.account.WatchAccountState
import com.lightningstudio.watchrss.data.account.WatchEntitlementSnapshot
import com.lightningstudio.watchrss.data.account.WatchTelemetryConfig
import org.json.JSONArray
import org.json.JSONObject

object AccountSyncPayload {
    const val PROTOCOL_VERSION = 1

    fun parseRequest(payload: JSONObject): WatchAccountState {
        val account = payload.optJSONObject("account") ?: error("缺少账号载荷")
        val entitlement = payload.optJSONObject("entitlement")
        val telemetry = payload.optJSONObject("telemetry")
        return WatchAccountState(
            userId = account.optString("userId").trim(),
            phoneMasked = account.optString("phoneMasked").trim(),
            installId = account.optString("installId").trim(),
            watchDeviceToken = account.optString("watchAccessToken")
                .ifBlank { account.optString("watchDeviceToken") }.trim(),
            tokenExpiresAtMillis = account.optLong(
                "accessTokenExpiresAt", account.optLong("tokenExpiresAt")
            ),
            watchRefreshToken = account.optString("watchRefreshToken").trim(),
            refreshTokenExpiresAtMillis = account.optLong("refreshTokenExpiresAt"),
            backendBaseUrl = account.optString("backendBaseUrl").trim(),
            posthogHost = account.optString("posthogHost").trim(),
            posthogProjectApiKey = account.optString("posthogProjectApiKey").trim(),
            entitlement = WatchEntitlementSnapshot(
                plan = entitlement?.optString("plan")?.ifBlank { "free" } ?: "free",
                active = entitlement?.optBoolean("active", true) ?: true,
                expiresAtMillis = entitlement?.optLong("expiresAt") ?: 0L,
                features = entitlement?.optJSONArray("features").toStringList()
            ),
            telemetryConfig = WatchTelemetryConfig(
                anonymousEnabled = telemetry?.optBoolean("anonymousEnabled", true) ?: true,
                diagnosticsEnabled = telemetry?.optBoolean("diagnosticsEnabled", false) ?: false,
                sampleRate = telemetry?.optDouble("sampleRate", 1.0) ?: 1.0
            )
        ).also { state ->
            require(state.userId.isNotBlank()) { "缺少用户 ID" }
            require(state.watchDeviceToken.isNotBlank()) { "缺少手表设备 token" }
        }
    }

    fun buildResponse(
        deviceId: String,
        state: WatchAccountState,
        telemetryBacklog: Int
    ): JSONObject =
        JSONObject().apply {
            put("success", true)
            put("version", PROTOCOL_VERSION)
            put("action", BluetoothSyncProtocol.ACTION_SYNC_ACCOUNT)
            put("deviceId", deviceId)
            put("boundUserId", state.userId)
            put("telemetryBacklog", telemetryBacklog)
        }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
