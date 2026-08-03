package com.lightningstudio.watchrss

import android.app.Application
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.lightningstudio.watchrss.data.AppContainer
import com.lightningstudio.watchrss.data.DefaultAppContainer
import com.lightningstudio.watchrss.data.account.WatchAccountStore
import com.lightningstudio.watchrss.data.account.WatchTokenManager
import com.lightningstudio.watchrss.data.cloud.WatchCloudSyncService
import com.lightningstudio.watchrss.data.cloud.WatchCloudSyncWorker
import com.lightningstudio.watchrss.data.reader.WatchReaderPresetPreviewSession
import com.lightningstudio.watchrss.data.telemetry.WatchUsageTelemetry
import com.lightningstudio.watchrss.debug.DebugLogBuffer
import com.lightningstudio.watchrss.debug.StartupDurationTracker
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.phoneconnection.bluetooth.WatchBluetoothForegroundSyncManager
import com.lightningstudio.watchrss.sdk.bili.BiliDebugLog
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WatchRssApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val defaultContainer: AppContainer by lazy {
        DefaultAppContainer(this)
    }

    @Volatile
    private var testContainerOverride: AppContainer? = null
    private val bluetoothForegroundSyncManager: WatchBluetoothForegroundSyncManager by lazy {
        WatchBluetoothForegroundSyncManager(this) {
            appScope.launch { cloudSyncService.syncNow() }
        }
    }
    private var lastCloudForegroundAt = 0L
    private var pendingCloudChangeSync: Job? = null

    val readerPresetPreviewSession by lazy {
        WatchReaderPresetPreviewSession(appScope)
    }

    val container: AppContainer
        get() = testContainerOverride ?: defaultContainer

    val accountStore: WatchAccountStore by lazy {
        (container as DefaultAppContainer).watchAccountStore
    }
    val watchTokenManager: WatchTokenManager by lazy {
        WatchTokenManager(accountStore)
    }

    val usageTelemetry: WatchUsageTelemetry
        get() = container.watchUsageTelemetry

    val cloudSyncService: WatchCloudSyncService by lazy {
        WatchCloudSyncService(
            context = this,
            accountStore = accountStore,
            repository = container.rssRepository
        )
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化日志系统
        AppLogger.init(this)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        AppLogger.log("Application", "应用启动 - ${dateFormat.format(Date())}")
        StartupDurationTracker.markApplicationCreated()
        val enableDebugLogBuffer = BuildConfig.ENABLE_RUNTIME_PERF_MONITOR
        DebugLogBuffer.setEnabled(enableDebugLogBuffer)
        if (enableDebugLogBuffer) {
            BiliDebugLog.setLogger { tag, message -> DebugLogBuffer.log(tag, message) }
        }

        bluetoothForegroundSyncManager.install()
        WatchCloudSyncWorker.schedule(this)
        usageTelemetry.recordAppLaunch()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastCloudForegroundAt < 5 * 60 * 1000L) return
                lastCloudForegroundAt = now
                appScope.launch { cloudSyncService.syncNow() }
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        appScope.launch {
            if (accountStore.read() != null) {
                cloudSyncService.syncNow()
            }
        }
        appScope.launch {
            accountStore.state.drop(1).collect { account ->
                if (account != null) cloudSyncService.syncNow()
            }
        }
        appScope.launch {
            container.rssRepository.observeCloudSyncRevision().drop(1).collect {
                pendingCloudChangeSync?.cancel()
                pendingCloudChangeSync = launch {
                    delay(CLOUD_CHANGE_DEBOUNCE_MS)
                    cloudSyncService.syncNow()
                }
            }
        }
    }

    fun setContainerForTesting(container: AppContainer?) {
        testContainerOverride = container
    }

    fun openReaderPresetPreview() {
        if (ReaderPresetPreviewActivity.isVisible) return
        startActivity(
            Intent(this, ReaderPresetPreviewActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
        )
    }

    private companion object {
        private const val CLOUD_CHANGE_DEBOUNCE_MS = 10 * 60 * 1000L
    }
}
