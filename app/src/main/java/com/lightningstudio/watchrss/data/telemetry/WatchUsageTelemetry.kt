package com.lightningstudio.watchrss.data.telemetry

import android.content.Context
import android.os.Build
import com.lightningstudio.watchrss.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WatchUsageTelemetry(
    context: Context,
    private val installationIdentity: WatchInstallationIdentity,
    private val appScope: CoroutineScope,
    private val openPanelAnalytics: OpenPanelAnalytics
) {
    private val appContext = context.applicationContext

    init {
        openPanelAnalytics.setGlobalProperties(
            mapOf(
                "platform" to "watch",
                "packageName" to appContext.packageName,
                "appVersionName" to BuildConfig.VERSION_NAME,
                "appVersionCode" to BuildConfig.VERSION_CODE,
                "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "sdk" to Build.VERSION.SDK_INT,
                "firstInstalledAt" to installationIdentity.firstInstalledAtMillis
            )
        )
        openPanelAnalytics.identify(
            installationIdentity.installId,
            mapOf("installId" to installationIdentity.installId)
        )
    }

    fun recordAppLaunch() {
        capture("app_opened")
    }

    fun recordScreenOpen(screen: String) {
        capture("screen_opened", mapOf("screen" to screen))
    }

    fun recordScreenDuration(screen: String, durationMs: Long) {
        if (durationMs <= 0L) return
        capture("screen_duration", mapOf("screen" to screen, "durationMs" to durationMs))
    }

    fun recordSyncReceived(kind: String, itemCount: Int = 0) {
        capture(
            event = "sync_received",
            properties = mapOf(
                "kind" to kind,
                "itemCount" to itemCount
            )
        )
    }

    fun recordFeedRefreshed(channelId: String?, channelTitle: String?, success: Boolean) {
        capture(
            event = "feed_refreshed",
            properties = mapOf(
                "channelId" to channelId.orEmpty(),
                "channelTitle" to channelTitle.orEmpty(),
                "success" to success
            )
        )
    }

    fun recordArticleReadStarted(itemId: Long, title: String?, channelId: String?, channelTitle: String?) {
        capture(
            event = "article_read_started",
            properties = mapOf(
                "itemId" to itemId,
                "title" to title.orEmpty(),
                "channelId" to channelId.orEmpty(),
                "channelTitle" to channelTitle.orEmpty()
            )
        )
    }

    fun recordArticleReadFinished(
        itemId: Long,
        title: String?,
        channelId: String?,
        channelTitle: String?,
        reachedBottom: Boolean,
        durationMs: Long
    ) {
        capture(
            event = "article_read_finished",
            properties = mapOf(
                "itemId" to itemId,
                "title" to title.orEmpty(),
                "channelId" to channelId.orEmpty(),
                "channelTitle" to channelTitle.orEmpty(),
                "reachedBottom" to reachedBottom,
                "durationMs" to durationMs
            )
        )
    }

    fun recordVideoPlayed(source: String, id: String, title: String?) {
        capture(
            event = "video_played",
            properties = mapOf(
                "source" to source,
                "id" to id,
                "title" to title.orEmpty()
            )
        )
    }

    private fun capture(event: String, properties: Map<String, Any> = emptyMap()) {
        appScope.launch(Dispatchers.IO) {
            openPanelAnalytics.track(event, properties)
        }
    }
}