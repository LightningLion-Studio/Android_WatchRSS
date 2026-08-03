package com.lightningstudio.watchrss

import android.app.Application
import com.lightningstudio.watchrss.data.AppContainer
import com.lightningstudio.watchrss.data.DefaultAppContainer
import com.lightningstudio.watchrss.data.account.WatchAccountStore
import com.lightningstudio.watchrss.data.account.WatchTokenManager
import com.lightningstudio.watchrss.data.telemetry.WatchInstallationIdentity
import com.lightningstudio.watchrss.data.telemetry.WatchUsageTelemetry
import com.lightningstudio.watchrss.debug.DebugLogBuffer
import com.lightningstudio.watchrss.debug.StartupDurationTracker
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.phoneconnection.bluetooth.WatchBluetoothForegroundSyncManager
import com.lightningstudio.watchrss.sdk.bili.BiliDebugLog
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        WatchBluetoothForegroundSyncManager(this)
    }

    val container: AppContainer
        get() = testContainerOverride ?: defaultContainer

    val accountStore: WatchAccountStore by lazy {
        WatchAccountStore(this)
    }
    val watchTokenManager: WatchTokenManager by lazy {
        WatchTokenManager(accountStore)
    }

    val usageTelemetry: WatchUsageTelemetry by lazy {
        WatchUsageTelemetry(
            context = this,
            installationIdentity = WatchInstallationIdentity(this),
            appScope = appScope,
            openPanelAnalytics = (container as DefaultAppContainer).openPanelAnalytics
        )
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化日志系统
        AppLogger.init(this)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        AppLogger.log("Application", "应用启动 - ${dateFormat.format(Date())}")
        StartupDurationTracker.markApplicationCreated()
        container.watchUsageTelemetry.recordAppLaunch()

        val enableDebugLogBuffer = BuildConfig.ENABLE_RUNTIME_PERF_MONITOR
        DebugLogBuffer.setEnabled(enableDebugLogBuffer)
        if (enableDebugLogBuffer) {
            BiliDebugLog.setLogger { tag, message -> DebugLogBuffer.log(tag, message) }
        }

        bluetoothForegroundSyncManager.install()
        usageTelemetry.recordAppLaunch()
    }

    fun setContainerForTesting(container: AppContainer?) {
        testContainerOverride = container
    }
}
