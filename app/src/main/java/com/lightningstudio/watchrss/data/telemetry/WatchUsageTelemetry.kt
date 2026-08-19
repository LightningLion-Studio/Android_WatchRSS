package com.lightningstudio.watchrss.data.telemetry

import android.content.Context
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.data.account.AccountStore
import com.lightningstudio.watchrss.data.account.WatchTokenManager
import com.lightningstudio.watchrss.data.network.withWatchRssAppVersionHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Sends only cumulative daily counters to the authenticated WatchRSS backend. */
class WatchUsageTelemetry(
    context: Context,
    private val installationIdentity: WatchInstallationIdentity,
    private val deviceId: String,
    accountStore: AccountStore,
    private val tokenManager: WatchTokenManager = WatchTokenManager(accountStore),
    private val appScope: CoroutineScope,
    private val httpClient: OkHttpClient = defaultHttpClient()
) : UsageTelemetry {
    private val store = DailyTelemetryStore(context)
    private val downloadPreferences = context.applicationContext.getSharedPreferences(
        DOWNLOAD_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val uploadScheduled = AtomicBoolean(false)
    private val generation = AtomicLong(0L)

    override fun recordAppLaunch() = capture("app_opened")

    /** Records one release OOBE open per installation. */
    fun recordReleaseOobeOpened() {
        if (!shouldCountReleaseOobeOpen(BuildConfig.DEBUG)) return
        synchronized(downloadPreferences) {
            if (downloadPreferences.getBoolean(KEY_RELEASE_OOBE_RECORDED, false)) return
            store.record(EVENT_RELEASE_OOBE_OPENED)
            downloadPreferences.edit().putBoolean(KEY_RELEASE_OOBE_RECORDED, true).apply()
        }
        generation.incrementAndGet()
        scheduleUpload()
    }

    override fun recordScreenOpen(screen: String) =
        capture("screen_opened", mapOf("screen" to screen))

    override fun recordScreenDuration(screen: String, durationMs: Long) {
        if (durationMs > 0L) {
            capture("screen_duration", mapOf("screen" to screen, "durationMs" to durationMs))
        }
    }

    override fun recordSyncReceived(kind: String, itemCount: Int) = capture("sync_received")

    override fun recordFeedRefreshed(channelId: String?, channelTitle: String?, success: Boolean) =
        capture("feed_refreshed")

    override fun recordArticleReadStarted(
        itemId: Long,
        title: String?,
        channelId: String?,
        channelTitle: String?
    ) = capture("article_read_started")

    override fun recordArticleReadFinished(
        itemId: Long,
        title: String?,
        channelId: String?,
        channelTitle: String?,
        reachedBottom: Boolean,
        durationMs: Long
    ) = capture("article_read_finished")

    override fun recordVideoPlayed(source: String, id: String, title: String?) =
        capture("video_played")

    override fun recordSyncAccount() = capture("sync_account")

    override fun backlogCount(): Int = store.snapshots().size

    private fun capture(event: String, properties: Map<String, Any?> = emptyMap()) {
        store.record(event, properties)
        generation.incrementAndGet()
        scheduleUpload()
    }

    private fun scheduleUpload() {
        if (!uploadScheduled.compareAndSet(false, true)) return
        appScope.launch(Dispatchers.IO) {
            delay(UPLOAD_DEBOUNCE_MS)
            val uploadingGeneration = generation.get()
            runCatching { uploadPending() }
            uploadScheduled.set(false)
            if (generation.get() != uploadingGeneration) scheduleUpload()
        }
    }

    private suspend fun uploadPending() {
        val account = tokenManager.freshAccount()
        for (snapshot in store.snapshots()) {
            val payload = JSONObject().apply {
                put("day", snapshot.day)
                put("installId", installationIdentity.installId)
                put("deviceId", deviceId)
                put("platform", "watch")
                put("appVersionName", BuildConfig.VERSION_NAME)
                put("appVersionCode", BuildConfig.VERSION_CODE)
                put("screenOpenCounts", JSONObject(snapshot.screenOpenCounts))
                put("screenDurationMs", JSONObject(snapshot.screenDurationMs))
                put("eventCounts", JSONObject(snapshot.eventCounts))
                put("appForegroundMs", snapshot.appForegroundMs)
                put("syncSuccessCount", snapshot.syncSuccessCount)
                put("syncFailureCount", snapshot.syncFailureCount)
                put("diagnosticsOptedIn", false)
            }
            val request = Request.Builder()
                .url(account.backendBaseUrl.trimEnd('/') + "/functions/v1/telemetry-rollup")
                .header("Authorization", "Bearer ${account.watchDeviceToken}")
                .withWatchRssAppVersionHeader()
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
            }
        }
    }

    companion object {
        const val EVENT_RELEASE_OOBE_OPENED = "release_oobe_opened"

        private const val DOWNLOAD_PREFERENCES = "watchrss_download_telemetry"
        private const val KEY_RELEASE_OOBE_RECORDED = "release_oobe_recorded"
        private const val UPLOAD_DEBOUNCE_MS = 750L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}

internal fun shouldCountReleaseOobeOpen(debugBuild: Boolean): Boolean = !debugBuild
