package com.lightningstudio.watchrss.phoneconnection.bluetooth

import android.Manifest
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.lightningstudio.watchrss.BluetoothConnectionActivity
import com.lightningstudio.watchrss.DebugBluetoothSyncActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WatchBluetoothForegroundSyncManager(
    private val application: Application
) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val powerManager = application.getSystemService(PowerManager::class.java)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenInteractive = true
                    updateListeningState()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    updateListeningState()
                }
            }
        }
    }

    @Volatile
    private var resumedCount = 0

    @Volatile
    private var excludedResumedCount = 0

    @Volatile
    private var screenInteractive = powerManager?.isInteractive ?: true

    @Volatile
    private var transferInProgress = false

    @Volatile
    private var desiredListening = false

    private val lock = Any()
    private var listeningJob: Job? = null

    fun install() {
        application.registerActivityLifecycleCallbacks(this)
        application.registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )
        updateListeningState()
    }

    override fun onActivityResumed(activity: Activity) {
        resumedCount += 1
        if (activity.isExclusiveBluetoothListener()) {
            excludedResumedCount += 1
        }
        screenInteractive = powerManager?.isInteractive ?: true
        updateListeningState()
    }

    override fun onActivityPaused(activity: Activity) {
        resumedCount = (resumedCount - 1).coerceAtLeast(0)
        if (activity.isExclusiveBluetoothListener()) {
            excludedResumedCount = (excludedResumedCount - 1).coerceAtLeast(0)
        }
        updateListeningState()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun updateListeningState() {
        val shouldListen = shouldListen()
        desiredListening = shouldListen
        if (shouldListen) {
            ensureListeningJob()
        } else {
            stopListeningIfIdle()
        }
    }

    private fun shouldListen(): Boolean {
        return resumedCount > 0 &&
            excludedResumedCount == 0 &&
            screenInteractive &&
            hasBluetoothPermission() &&
            isBluetoothEnabled()
    }

    private fun ensureListeningJob() {
        synchronized(lock) {
            val current = listeningJob
            if (current?.isActive == true) return
            listeningJob = scope.launch {
                listenLoop()
            }
        }
    }

    private fun stopListeningIfIdle() {
        synchronized(lock) {
            if (transferInProgress) return
            listeningJob?.cancel()
            listeningJob = null
        }
    }

    private suspend fun listenLoop() {
        while (desiredListening) {
            transferInProgress = false
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    WatchBluetoothSyncServer(
                        context = application,
                        allowedActions = setOf(BluetoothSyncProtocol.ACTION_SYNC_LIBRARY),
                        onClientAccepted = {
                            transferInProgress = true
                        }
                    ).acceptOnce(timeoutMs = FOREGROUND_ACCEPT_TIMEOUT_MS)
                }
            }
            transferInProgress = false
            result.onSuccess { syncResult ->
                Log.i(
                    TAG,
                    "foreground sync complete remote=${syncResult.remoteName} action=${syncResult.request.optString("action")}"
                )
            }.onFailure { throwable ->
                if (throwable is CancellationException) return
                if (desiredListening) {
                    Log.w(TAG, "foreground sync listen failed: ${throwable.message}")
                    delay(RETRY_DELAY_MS)
                }
            }
            if (!shouldListen()) {
                desiredListening = false
            }
        }
        synchronized(lock) {
            if (listeningJob?.isActive != true) {
                listeningJob = null
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isBluetoothEnabled(): Boolean {
        return runCatching {
            application.getSystemService(BluetoothManager::class.java)
                ?.adapter
                ?.isEnabled == true
        }.getOrDefault(false)
    }

    private fun Activity.isExclusiveBluetoothListener(): Boolean {
        return this is BluetoothConnectionActivity || this is DebugBluetoothSyncActivity
    }

    companion object {
        private const val TAG = "WatchRSS_BtFgSync"
        private const val FOREGROUND_ACCEPT_TIMEOUT_MS = 120_000L
        private const val RETRY_DELAY_MS = 5_000L
    }
}
