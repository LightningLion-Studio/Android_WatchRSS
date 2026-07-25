package com.lightningstudio.watchrss

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.phoneconnection.bluetooth.BluetoothTransferScreenOnController
import com.lightningstudio.watchrss.phoneconnection.bluetooth.WatchBluetoothSyncServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DebugBluetoothSyncActivity : BaseWatchActivity() {
    private val screenOnController = BluetoothTransferScreenOnController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            finish()
            return
        }

        val statusView = TextView(this).apply {
            textSize = 14f
            setPadding(24, 24, 24, 24)
            text = "等待手机蓝牙连接..."
        }
        setContentView(statusView)

        val missingPermission = missingBluetoothPermission()
        if (missingPermission != null) {
            val message = "Missing permission: $missingPermission"
            Log.e(TAG, message)
            statusView.text = message
            return
        }

        val timeoutMs = intent.getLongExtra(EXTRA_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    WatchBluetoothSyncServer(
                        context = applicationContext,
                        onClientAccepted = {
                            screenOnController.setTransferInProgress(true)
                        }
                    ).acceptOnce(timeoutMs)
                }
            }.onSuccess { result ->
                screenOnController.setTransferInProgress(false)
                val message = buildString {
                    appendLine("Bluetooth sync request complete")
                    appendLine("remote=${result.remoteName} ${result.remoteAddress}")
                    appendLine(summarizePayload("request", result.request))
                    appendLine(summarizePayload("response", result.response))
                }
                Log.i(TAG, message)
                statusView.text = message
            }.onFailure { throwable ->
                screenOnController.setTransferInProgress(false)
                val message = "Bluetooth sync request failed: ${throwable.message}"
                Log.e(TAG, message, throwable)
                statusView.text = message
            }
        }
    }

    override fun onResume() {
        super.onResume()
        screenOnController.setCurrentActivity(this)
    }

    override fun onPause() {
        screenOnController.clearActivity(this)
        super.onPause()
    }

    override fun onDestroy() {
        screenOnController.setTransferInProgress(false)
        screenOnController.clearActivity(this)
        super.onDestroy()
    }

    private fun summarizePayload(label: String, payload: JSONObject): String {
        val batchIndex = payload.optInt("batchIndex", -1)
        val batchCount = payload.optInt("batchCount", -1)
        val batch = if (batchIndex >= 0 && batchCount > 0) {
            "${batchIndex + 1}/$batchCount"
        } else {
            "-"
        }
        return buildString {
            append(label)
            append(": action=")
            append(payload.optString("action").ifBlank { "-" })
            append(" phase=")
            append(payload.optString("phase").ifBlank { "-" })
            append(" version=")
            append(payload.optInt("version", 0))
            append(" success=")
            append(payload.optBoolean("success", true))
            append(" articleManifest=")
            append(payload.optJSONArray("articleManifest")?.length() ?: 0)
            append(" articles=")
            append(payload.optJSONArray("articles")?.length() ?: 0)
            append(" bodyRequests=")
            append(payload.optJSONArray("bodyRequests")?.length() ?: 0)
            append(" rssSources=")
            append(payload.optJSONArray("rssSources")?.length() ?: 0)
            append(" batch=")
            append(batch)
            payload.optJSONObject("changeSeqRange")?.let { range ->
                append(" seq=")
                append(range.optLong("fromExclusive"))
                append("..")
                append(range.optLong("toInclusive"))
            }
        }
    }

    private fun missingBluetoothPermission(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Manifest.permission.BLUETOOTH_CONNECT.takeIf {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val TAG = "WatchRSS_DebugBtSync"
        private const val DEFAULT_TIMEOUT_MS = 120_000L
        private const val EXTRA_TIMEOUT_MS = "timeout_ms"
    }
}
