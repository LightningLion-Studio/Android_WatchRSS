package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.data.settings.PRIVACY_POLICY_VERSION
import com.lightningstudio.watchrss.debug.PerformanceMonitor
import com.lightningstudio.watchrss.debug.StartupDurationTracker
import com.lightningstudio.watchrss.ui.screen.launch.LaunchScreenOverlay
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseWatchActivity() {
    private var keepSplashOnScreen = true
    private val launchShellReady = CompletableDeferred<Unit>()

    override fun isSwipeBackEnabled(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val launcherEntry = isLauncherEntry(intent)
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.view.animate()
                .alpha(0f)
                .scaleX(1.12f)
                .scaleY(1.12f)
                .setDuration(SYSTEM_SPLASH_FADE_DURATION_MS)
                .withEndAction {
                    splashScreenViewProvider.remove()
                }
                .start()
        }
        super.onCreate(savedInstanceState)
        StartupDurationTracker.markMainActivityCreated(
            isLauncherEntry = launcherEntry,
            savedInstanceState = savedInstanceState
        )
        PerformanceMonitor.setScenario(this, "home_cold_start")
        setupSystemBars()
        renderLaunchContent()

        lifecycleScope.launch {
            val settingsRepository = (application as WatchRssApplication).container.settingsRepository
            val shouldShowOobeDeferred = async(Dispatchers.IO) {
                settingsRepository.oobeSeenVersion.first() < CURRENT_OOBE_VERSION
            }
            val shouldShowPrivacyConsentDeferred = async(Dispatchers.IO) {
                settingsRepository.privacyPolicyAgreedVersion.first() < PRIVACY_POLICY_VERSION
            }
            launchShellReady.await()
            keepSplashOnScreen = false
            if (launcherEntry) {
                AppLaunchSignal.markLauncherOpen()
            }
            val shouldShowOobe = shouldShowOobeDeferred.await()
            if (shouldShowOobe) {
                StartupDurationTracker.markStartupReady(destination = "oobe")
                startActivity(OobeActivity.createIntent(this@MainActivity))
                finish()
                return@launch
            }
            val shouldShowPrivacyConsent = shouldShowPrivacyConsentDeferred.await()
            if (shouldShowPrivacyConsent) {
                StartupDurationTracker.markStartupReady(destination = "privacy_consent")
                startActivity(PrivacyPolicyConsentActivity.createIntent(this@MainActivity))
                finish()
                return@launch
            }
            if (launcherEntry) {
                if (maybeResumeLastContent(intent)) {
                    finish()
                    return@launch
                }
            }
            startActivity(
                HomeFeedListActivity.createIntent(
                    context = this@MainActivity,
                    launcherEntry = launcherEntry
                ).apply {
                    if (intent.getBooleanExtra(EXTRA_DEBUG_HOME_AUTOSCROLL_PERF, false)) {
                        putExtra(
                            HomeFeedListActivity.EXTRA_DEBUG_AUTOSCROLL_PERF,
                            true
                        )
                    }
                }
            )
            finish()
        }
    }

    private fun renderLaunchContent() {
        setContent {
            WatchRSSTheme {
                SideEffect {
                    if (!launchShellReady.isCompleted) {
                        launchShellReady.complete(Unit)
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LaunchScreenOverlay(visible = true)
                }
            }
        }
    }

    private suspend fun maybeResumeLastContent(sourceIntent: Intent?): Boolean {
        if (!isLauncherEntry(sourceIntent)) return false
        val resumeIntent = withContext(Dispatchers.IO) {
            AppResumeStateStore.load(this@MainActivity)
        } ?: return false
        val component = resumeIntent.component ?: return false
        val homeIntent = HomeFeedListActivity.createIntent(
            context = this@MainActivity,
            launcherEntry = true
        )
        if (component.className == MainActivity::class.java.name ||
            component.className == HomeFeedListActivity::class.java.name
        ) {
            startActivity(homeIntent)
            return true
        }
        startActivities(
            arrayOf(
                homeIntent,
                resumeIntent
            )
        )
        return true
    }

    private fun isLauncherEntry(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_MAIN) return false
        return intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
    }

    companion object {
        private const val EXTRA_DEBUG_HOME_AUTOSCROLL_PERF = "debug_home_autoscroll_perf"
        private const val SYSTEM_SPLASH_FADE_DURATION_MS = 80L
    }
}
