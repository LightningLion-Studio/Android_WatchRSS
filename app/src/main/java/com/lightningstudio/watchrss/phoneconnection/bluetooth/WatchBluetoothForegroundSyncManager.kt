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
    private val application: Application,
    private val onLibrarySyncCompleted: (() -> Unit)? = null
) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val powerManager = application.getSystemService(PowerManager::class.java)
    private val screenOnController = BluetoothTransferScreenOnController()
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
    private var stopListeningJob: Job? = null

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
        val isExclusiveBluetoothListener = activity.isExclusiveBluetoothListener()
        if (isExclusiveBluetoothListener) {
            excludedResumedCount += 1
        } else {
            screenOnController.setCurrentActivity(activity)
        }
        screenInteractive = powerManager?.isInteractive ?: true
        updateListeningState()
    }

    override fun onActivityPaused(activity: Activity) {
        resumedCount = (resumedCount - 1).coerceAtLeast(0)
        val isExclusiveBluetoothListener = activity.isExclusiveBluetoothListener()
        if (isExclusiveBluetoothListener) {
            excludedResumedCount = (excludedResumedCount - 1).coerceAtLeast(0)
        } else {
            screenOnController.clearActivity(activity)
        }
        updateListeningState()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        screenOnController.clearActivity(activity)
    }

    private fun updateListeningState() {
        val shouldListen = shouldListen()
        if (shouldListen) {
            desiredListening = true
            cancelScheduledStop()
            ensureListeningJob()
        } else {
            scheduleStopListening()
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

    private fun cancelScheduledStop() {
        synchronized(lock) {
            stopListeningJob?.cancel()
            stopListeningJob = null
        }
    }

    private fun scheduleStopListening() {
        synchronized(lock) {
            if (transferInProgress || stopListeningJob?.isActive == true) return
            stopListeningJob = scope.launch {
                delay(LISTENER_STOP_GRACE_MS)
                synchronized(lock) {
                    stopListeningJob = null
                    if (shouldListen() || transferInProgress) return@synchronized
                    desiredListening = false
                    listeningJob?.cancel()
                    listeningJob = null
                }
            }
        }
    }

    private suspend fun listenLoop() {
        while (desiredListening) {
            updateTransferInProgress(false)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    WatchBluetoothSyncServer(
                        context = application,
                        allowedActions = setOf(
                            BluetoothSyncProtocol.ACTION_SYNC_LIBRARY,
                            BluetoothSyncProtocol.ACTION_SYNC_READER,
                            BluetoothSyncProtocol.ACTION_PREVIEW_READER,
                            BluetoothSyncProtocol.ACTION_SYNC_ACCOUNT
                        ),
                        onClientAccepted = {
                            updateTransferInProgress(true)
                        }
                    ).acceptOnce(timeoutMs = FOREGROUND_ACCEPT_TIMEOUT_MS)
                }
            }
            updateTransferInProgress(false)
            result.onSuccess { syncResult ->
                Log.i(
                    TAG,
                    "foreground sync complete remote=${syncResult.remoteName} action=${syncResult.request.optString("action")}"
                )
                if (
                    syncResult.request.optString("action") ==
                    BluetoothSyncProtocol.ACTION_SYNC_LIBRARY
                ) {
                    onLibrarySyncCompleted?.invoke()
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) return
                if (desiredListening) {
                    Log.w(TAG, "foreground sync listen failed: ${throwable.message}")
                    delay(RETRY_DELAY_MS)
                }
            }
            if (!shouldListen()) scheduleStopListening()
        }
        synchronized(lock) {
            if (listeningJob?.isActive != true) {
                listeningJob = null
            }
        }
    }

    private fun updateTransferInProgress(inProgress: Boolean) {
        transferInProgress = inProgress
        screenOnController.setTransferInProgress(inProgress)
        if (!inProgress && !shouldListen()) scheduleStopListening()
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
        private const val RETRY_DELAY_MS = 500L
        private const val LISTENER_STOP_GRACE_MS = 2_500L
    }
}
