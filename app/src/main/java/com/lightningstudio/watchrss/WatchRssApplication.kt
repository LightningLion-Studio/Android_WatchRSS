package com.lightningstudio.watchrss

import android.app.Application
import com.lightningstudio.watchrss.data.AppContainer
import com.lightningstudio.watchrss.data.DefaultAppContainer
import com.lightningstudio.watchrss.debug.DebugLogBuffer
import com.lightningstudio.watchrss.debug.StartupDurationTracker
import com.lightningstudio.watchrss.sdk.bili.BiliDebugLog
import com.lightningstudio.watchrss.util.AppLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WatchRssApplication : Application() {
    private val defaultContainer: AppContainer by lazy {
        DefaultAppContainer(this)
    }

    @Volatile
    private var testContainerOverride: AppContainer? = null

    val container: AppContainer
        get() = testContainerOverride ?: defaultContainer

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

    }

    fun setContainerForTesting(container: AppContainer?) {
        testContainerOverride = container
    }
}
