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
import com.lightningstudio.watchrss.phoneconnection.bluetooth.WatchBluetoothSyncServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugBluetoothSyncActivity : BaseWatchActivity() {
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
                    WatchBluetoothSyncServer(applicationContext).acceptOnce(timeoutMs)
                }
            }.onSuccess { result ->
                val message = buildString {
                    appendLine("Bluetooth sync request complete")
                    appendLine("remote=${result.remoteName} ${result.remoteAddress}")
                    appendLine("request=${result.request}")
                    appendLine("response=${result.response}")
                }
                Log.i(TAG, message)
                statusView.text = message
            }.onFailure { throwable ->
                val message = "Bluetooth sync request failed: ${throwable.message}"
                Log.e(TAG, message, throwable)
                statusView.text = message
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
