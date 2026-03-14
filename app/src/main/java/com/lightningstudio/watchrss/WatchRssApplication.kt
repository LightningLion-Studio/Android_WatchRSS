package com.lightningstudio.watchrss

import android.app.Application
import android.content.pm.ApplicationInfo
import com.lightningstudio.watchrss.data.cache.CacheTrimReason
import com.lightningstudio.watchrss.data.AppContainer
import com.lightningstudio.watchrss.data.DefaultAppContainer
import com.lightningstudio.watchrss.debug.DebugLogBuffer
import com.lightningstudio.watchrss.sdk.bili.BiliDebugLog
import com.lightningstudio.watchrss.util.AppLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WatchRssApplication : Application() {
    val container: AppContainer by lazy {
        DefaultAppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化日志系统
        AppLogger.init(this)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        AppLogger.log("Application", "应用启动 - ${dateFormat.format(Date())}")

        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        DebugLogBuffer.setEnabled(debuggable)
        if (debuggable) {
            BiliDebugLog.setLogger { tag, message -> DebugLogBuffer.log(tag, message) }
        }

        container.managedCacheService.scheduleMaintenance(CacheTrimReason.APP_START)
    }
}
