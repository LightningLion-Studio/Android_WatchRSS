package com.lightningstudio.watchrss.data.telemetry

import android.content.Context
import android.os.Build
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.data.account.WatchAccountStore
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class WatchUsageTelemetry(
    private val context: Context,
    private val accountStore: WatchAccountStore,
    private val deviceIdentity: WatchDeviceIdentity,
    private val appScope: CoroutineScope
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "watchrss_watch_telemetry",
        Context.MODE_PRIVATE
    )

    fun recordAppLaunch() {
        capture("app_opened")
    }

    fun recordScreenOpen(screen: String) {
        capture("screen_opened", mapOf("screen" to screen))
    }

    fun recordSyncAccount() {
        capture("account_synced")
    }

    fun backlogCount(): Int = preferences.getInt(KEY_BACKLOG_COUNT, 0)

    private fun capture(event: String, properties: Map<String, Any?> = emptyMap()) {
        val state = accountStore.state.value ?: return
        if (!state.telemetryConfig.anonymousEnabled) return
        if (state.posthogHost.isBlank() || state.posthogProjectApiKey.isBlank()) return
        appScope.launch(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("api_key", state.posthogProjectApiKey)
                    put("event", event)
                    put("distinct_id", state.userId.ifBlank { deviceIdentity.deviceId })
                    put("properties", JSONObject().apply {
                        put("userId", state.userId)
                        put("installId", state.installId)
                        put("deviceId", deviceIdentity.deviceId)
                        put("platform", "watch")
                        put("packageName", context.packageName)
                        put("appVersionName", BuildConfig.VERSION_NAME)
                        put("appVersionCode", BuildConfig.VERSION_CODE)
                        put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
                        put("sdk", Build.VERSION.SDK_INT)
                        properties.forEach { (key, value) -> put(key, value) }
                    })
                }
                postJson("${state.posthogHost}/capture/", payload)
                preferences.edit().putInt(KEY_BACKLOG_COUNT, 0).apply()
            }.onFailure {
                preferences.edit().putInt(KEY_BACKLOG_COUNT, backlogCount() + 1).apply()
            }
        }
    }

    private fun postJson(url: String, payload: JSONObject) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("content-type", "application/json; charset=utf-8")
        }
        connection.outputStream.use { output ->
            output.write(payload.toString().toByteArray(Charsets.UTF_8))
        }
        val code = connection.responseCode
        connection.disconnect()
        require(code in 200..299) { "telemetry_http_$code" }
    }

    companion object {
        private const val KEY_BACKLOG_COUNT = "backlog_count"
    }
}

