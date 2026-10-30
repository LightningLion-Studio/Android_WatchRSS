package com.lightningstudio.watchrss

import android.app.Application
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.lightningstudio.watchrss.data.AppContainer
import com.lightningstudio.watchrss.data.DefaultAppContainer
import com.lightningstudio.watchrss.data.account.WatchAccountStore
import com.lightningstudio.watchrss.data.cloud.WatchCloudSyncService
import com.lightningstudio.watchrss.data.cloud.WatchCloudSyncWorker
import com.lightningstudio.watchrss.data.reader.WatchReaderPresetPreviewSession
import com.lightningstudio.watchrss.data.telemetry.WatchUsageTelemetry
import com.lightningstudio.watchrss.debug.DebugLogBuffer
import com.lightningstudio.watchrss.debug.StartupDurationTracker
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.phoneconnection.bluetooth.WatchBluetoothForegroundSyncManager
import com.lightningstudio.watchrss.phoneconnection.ip.SyncMediaKeepAlive
import com.lightningstudio.watchrss.phoneconnection.ip.WatchIpSyncManager
import com.lightningstudio.watchrss.sdk.bili.BiliDebugLog
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.lightningstudio.watchrss.data.account.WatchAccountState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WatchRssApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var defaultContainerRef: DefaultAppContainer? = null
    private val defaultContainer: DefaultAppContainer get() = defaultContainerRef ?: synchronized(this) {
        defaultContainerRef ?: DefaultAppContainer(this).also { defaultContainerRef = it }
    }
    internal val libraryContext get() = (container as? DefaultAppContainer)?.libraryContext ?: this
    private val accountSwitchMutex = Mutex()
    private val accountUiHandoff = com.lightningstudio.watchrss.data.account.workspace.AccountUiHandoff<Activity>()
    private var accountTriggerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activityContexts = java.util.concurrent.ConcurrentHashMap<Activity, android.content.Context>()
    private val retiredContainers = mutableListOf<Pair<DefaultAppContainer, MutableSet<Activity>>>()
    private val retiredActivityContexts = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Activity, android.content.Context>())
    private val activityContextLock = Any()
    internal fun dataContextFor(activity: Activity): android.content.Context = synchronized(activityContextLock) {
        activityContexts[activity] ?: retiredActivityContexts[activity] ?: libraryContext
    }

    @Volatile
    private var testContainerOverride: AppContainer? = null
    private val bluetoothForegroundSyncManager: WatchBluetoothForegroundSyncManager by lazy {
        WatchBluetoothForegroundSyncManager(this) {
            cloudChangeScheduler?.requestCatchUp()
        }
    }
    internal val syncMediaKeepAlive: SyncMediaKeepAlive by lazy { SyncMediaKeepAlive() }
    val ipSyncManager: WatchIpSyncManager by lazy { WatchIpSyncManager(this) }
    private var lastCloudForegroundAt = 0L
    private var cloudChangeScheduler: com.lightningstudio.watchrss.data.cloud.WatchCloudChangeScheduler? = null

    val readerPresetPreviewSession by lazy {
        WatchReaderPresetPreviewSession(appScope)
    }

    val container: AppContainer
        get() = testContainerOverride ?: defaultContainer

    val accountStore: WatchAccountStore get() = container.watchAccountStore
    val watchTokenManager
        get() = container.watchTokenManager

    val usageTelemetry: WatchUsageTelemetry
        get() = container.watchUsageTelemetry

    @Volatile private var cloudServiceBinding: Pair<AppContainer, WatchCloudSyncService>? = null
    val cloudSyncService: WatchCloudSyncService get() = synchronized(this) {
        val current = container
        cloudServiceBinding?.takeIf { it.first === current }?.second ?: WatchCloudSyncService(
            context = (current as? DefaultAppContainer)?.libraryContext ?: this,
            accountStore = current.watchAccountStore,
            repository = current.rssRepository,
            readerRepository = current.readerPresetRepository,
            noteRepository = current.watchNoteRepository,
            tokenManager = current.watchTokenManager,
            deviceId = WatchDeviceIdentity(this).deviceId,
            canSync = { current.settingsRepository.privacyPolicyAgreedVersion.first() >= com.lightningstudio.watchrss.data.settings.PRIVACY_POLICY_VERSION }
        ).also { cloudServiceBinding = current to it }
    }

    internal suspend fun acceptAccountSync(state: WatchAccountState, sessionId: String): AppContainer = withContext(kotlinx.coroutines.NonCancellable) {
        accountSwitchMutex.withLock {
            val previous = defaultContainer
            val identity = state.backendBaseUrl.trimEnd('/') to state.userId
            if (previous.accountIdentity == identity && previous.watchAccountStore.read() != null) {
                previous.watchAccountStore.save(state)
                return@withLock previous
            }
            // Commit the new identity without publishing it into the old data container.
            previous.watchAccountStore.persistNewIdentity(state)
            accountTriggerScope.cancel()
            cloudChangeScheduler?.stop()
            WatchCloudSyncWorker.cancel(this@WatchRssApplication)
            previous.stopAccountTasks()
            val replacement = DefaultAppContainer(this@WatchRssApplication)
            withContext(Dispatchers.Main.immediate) {
                stopService(Intent(this@WatchRssApplication, com.lightningstudio.watchrss.data.media.BiliAudioService::class.java))
                readerPresetPreviewSession.state.value?.sessionId?.let(readerPresetPreviewSession::stop)
                defaultContainerRef = replacement
                cloudServiceBinding = null
                lastCloudForegroundAt = 0L
                accountTriggerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                bindContainerTriggers()
                val oldActivities = activityContexts.keys.toMutableSet()
                if (oldActivities.isEmpty()) appScope.launch { previous.closeAccountDatabase() }
                else retiredContainers += previous to oldActivities
                // The connection may belong to one of these Activities. Rebuilding the
                // UI here would cancel it before the phone receives its response/ACK.
                accountUiHandoff.defer(sessionId, oldActivities)
            }
            replacement
        }
    }

    internal suspend fun finishAccountSyncUi(sessionId: String) = withContext(kotlinx.coroutines.NonCancellable) {
        accountSwitchMutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                val staleActivities = accountUiHandoff.finish(sessionId)
                    .filter { !it.isDestroyed && !it.isFinishing }
                if (staleActivities.any { it.hasWindowFocus() }) {
                    startActivity(Intent(this@WatchRssApplication, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }
                // A background sync must not bring the app to the foreground. Its next
                // normal entry uses the replacement container; old UI is still retired.
                staleActivities.forEach(Activity::finish)
            }
        }
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
        ipSyncManager.install()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) { activityContexts[activity] = libraryContext }
            override fun onActivityDestroyed(activity: Activity) {
                synchronized(activityContextLock) {
                    activityContexts.remove(activity)?.let { retiredActivityContexts[activity] = it }
                }
                val iterator = retiredContainers.iterator()
                while (iterator.hasNext()) {
                    val (old, pending) = iterator.next()
                    pending -= activity
                    if (pending.isEmpty()) { iterator.remove(); appScope.launch { old.closeAccountDatabase() } }
                }
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })
        bindContainerTriggers()
        installCloudSyncTriggers()
        usageTelemetry.recordAppLaunch()
    }

    private fun installCloudSyncTriggers() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastCloudForegroundAt < 5 * 60 * 1000L) return
                lastCloudForegroundAt = now
                cloudChangeScheduler?.requestCatchUp()
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun bindContainerTriggers() {
        val selected = container
        accountTriggerScope.launch {
            selected.settingsRepository.syncMediaKeepAliveEnabled.collect(syncMediaKeepAlive::setEnabled)
        }
        val cloud = cloudSyncService
        val enabled = combine(cloud.controls.state, selected.watchAccountStore.state,
            selected.settingsRepository.privacyPolicyAgreedVersion) { config, account, consent ->
            config.automatic && account != null && consent >= com.lightningstudio.watchrss.data.settings.PRIVACY_POLICY_VERSION
        }
        cloudChangeScheduler = com.lightningstudio.watchrss.data.cloud.WatchCloudChangeScheduler(
            enabled = enabled,
            changes = kotlinx.coroutines.flow.merge(
                combine(selected.rssRepository.observeCloudSyncRevision(), cloud.controls.state) { revision, config -> listOf(revision, config) as Any },
                combine(selected.readerPresetRepository.presetRecords, selected.readerPresetRepository.fonts,
                    selected.readerPresetRepository.backgrounds, selected.readerPresetRepository.resourceRevision) { presets, fonts, backgrounds, revision -> listOf(presets, fonts, backgrounds, revision) as Any },
                combine(selected.watchNoteRepository.observe(), selected.watchNoteRepository.observeFolders()) { notes, folders -> listOf(notes, folders) as Any },
                com.lightningstudio.watchrss.data.cloud.watchCloudNetworkEvents(this)
            ),
            scope = accountTriggerScope,
            transfer = {
                val success = cloud.syncNow()
                if (!success && cloud.status.value.phase in setOf(com.lightningstudio.watchrss.data.cloud.WatchCloudPhase.ERROR,
                    com.lightningstudio.watchrss.data.cloud.WatchCloudPhase.WAITING_NETWORK)) {
                    throw java.io.IOException(cloud.status.value.message)
                }
            },
            onEnabled = { active ->
                if (active) WatchCloudSyncWorker.schedule(this@WatchRssApplication)
                else WatchCloudSyncWorker.cancel(this@WatchRssApplication)
            }
        ).also { it.start() }
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

}
