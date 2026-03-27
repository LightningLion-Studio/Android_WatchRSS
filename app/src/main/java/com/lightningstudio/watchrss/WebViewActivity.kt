package com.lightningstudio.watchrss

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.lightningstudio.watchrss.ui.screen.WebViewScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.util.getWebViewUnavailableMessage
import com.lightningstudio.watchrss.ui.util.hasValidatedInternetConnection
import com.lightningstudio.watchrss.ui.util.offlineToastMessageOrNull
import com.lightningstudio.watchrss.ui.util.OFFLINE_USER_MESSAGE
import com.lightningstudio.watchrss.ui.util.warnWebViewUnavailable
import com.lightningstudio.watchrss.ui.util.showAppToast
import com.lightningstudio.watchrss.ui.widget.ProgressRingView
import com.lightningstudio.watchrss.util.AppLogger
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt

internal enum class WebViewScaleMode {
    Standard,
    Expanded,
    Shrunk;

    fun next(): WebViewScaleMode {
        return when (this) {
            Standard -> Expanded
            Expanded -> Shrunk
            Shrunk -> Standard
        }
    }
}

class WebViewActivity : BaseWatchActivity() {
    private val settingsRepository by lazy { (application as WatchRssApplication).container.settingsRepository }
    private lateinit var webView: WebView
    private lateinit var loadingRing: ProgressRingView
    private var progressAnimator: ValueAnimator? = null
    private var currentProgress = 0f
    private val progressInterpolator = DecelerateInterpolator()
    private var webViewInitialized = false
    private var ringInitialized = false
    private var activityReadingThemeDark = true
    private var currentScaleMode = WebViewScaleMode.Standard

    override fun onCreate(savedInstanceState: Bundle?) {
        currentScaleMode = savedInstanceState?.getString(KEY_SCALE_MODE)
            ?.let { value -> runCatching { WebViewScaleMode.valueOf(value) }.getOrNull() }
            ?: WebViewScaleMode.Standard
        activityReadingThemeDark = runBlocking { settingsRepository.readingThemeDark.first() }
        setTheme(
            if (activityReadingThemeDark) {
                R.style.Theme_WatchRSS_Translucent_Dark
            } else {
                R.style.Theme_WatchRSS_Translucent_Light
            }
        )
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        val initialWebViewError = getWebViewUnavailableMessage(this)
        setContent {
            val readingThemeDark by settingsRepository.readingThemeDark.collectAsState(initial = activityReadingThemeDark)

            WatchRSSTheme(darkTheme = readingThemeDark) {
                var errorMessage by remember { mutableStateOf(initialWebViewError) }
                var warningMessage by remember { mutableStateOf(initialWebViewError) }
                var scaleMode by rememberSaveable { mutableStateOf(currentScaleMode) }

                LaunchedEffect(warningMessage) {
                    val message = warningMessage ?: return@LaunchedEffect
                    warnWebViewUnavailable(this@WebViewActivity, message)
                    warningMessage = null
                }

                LaunchedEffect(scaleMode) {
                    currentScaleMode = scaleMode
                    applyCurrentScaleMode()
                }

                LaunchedEffect(readingThemeDark) {
                    if (readingThemeDark != activityReadingThemeDark) {
                        if (webViewInitialized) {
                            val currentUrl = webView.url
                            if (!currentUrl.isNullOrBlank()) {
                                intent.putExtra(EXTRA_URL, currentUrl)
                            }
                        }
                        recreate()
                    }
                }

                WebViewScreen(
                    backgroundColor = if (readingThemeDark) ComposeColor.Black else ComposeColor.White,
                    errorMessage = errorMessage,
                    scaleMode = scaleMode,
                    onToggleScaleMode = { scaleMode = scaleMode.next() },
                    onWebViewReady = { view ->
                        if (!webViewInitialized) {
                            webViewInitialized = true
                            webView = view
                            try {
                                setupWebView()
                                applyWebThemePreference(readingThemeDark)
                                webView.loadUrl(url)
                            } catch (throwable: Throwable) {
                                AppLogger.e("WebViewActivity", "Failed to load WebView url: $url", throwable)
                                val message = getWebViewUnavailableMessage(this@WebViewActivity)
                                    ?: "当前设备无法初始化 WebView，无法打开此页面"
                                errorMessage = message
                                warningMessage = message
                            }
                        }
                    },
                    onWebViewInitFailed = { message ->
                        errorMessage = message
                        warningMessage = message
                    },
                    onProgressRingReady = { ring ->
                        if (!ringInitialized) {
                            ringInitialized = true
                            loadingRing = ring
                            loadingRing.setShowBase(false)
                            loadingRing.visibility = View.GONE
                        }
                    }
                )
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webViewInitialized && webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyWebThemePreference(readingThemeDark: Boolean) {
        webView.setBackgroundColor(if (readingThemeDark) Color.BLACK else Color.WHITE)
        val settings = webView.settings
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
            WebSettingsCompat.setForceDarkStrategy(
                settings,
                WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
            )
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(
                settings,
                if (readingThemeDark) {
                    WebSettingsCompat.FORCE_DARK_ON
                } else {
                    WebSettingsCompat.FORCE_DARK_OFF
                }
            )
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, readingThemeDark)
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = true
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                showLoadingRing()
                resetProgress()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                animateProgressTo(1f, hideWhenDone = true)
                view?.post { applyCurrentScaleMode() }
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    offlineToastMessageOrNull(
                        this@WebViewActivity,
                        error?.description?.toString()
                    )?.let { message ->
                        showAppToast(this@WebViewActivity, message)
                    }
                }
                hideLoadingRing()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val clamped = (newProgress / 100f).coerceIn(0f, 1f)
                if (newProgress >= 100) {
                    showLoadingRing()
                    animateProgressTo(1f, hideWhenDone = true)
                } else {
                    showLoadingRing()
                    animateProgressTo(clamped, hideWhenDone = false)
                }
            }
        }
    }

    private fun applyCurrentScaleMode() {
        if (!webViewInitialized) return
        val scaleFactor = calculateScaleFactor(currentScaleMode)
        val script = buildScaleScript(scaleFactor)
        webView.post {
            runCatching {
                webView.evaluateJavascript(script, null)
            }.onFailure { throwable ->
                AppLogger.e("WebViewActivity", "Failed to apply web scale mode: $currentScaleMode", throwable)
            }
        }
    }

    private fun calculateScaleFactor(scaleMode: WebViewScaleMode): Float {
        if (!webViewInitialized) return 1f
        val contentWidth = webView.width.toFloat().coerceAtLeast(1f)
        val contentHeight = webView.height.toFloat().coerceAtLeast(1f)
        val screenWidth = (
            window.decorView.width.takeIf { it > 0 }
                ?: webView.rootView.width.takeIf { it > 0 }
                ?: resources.displayMetrics.widthPixels
            ).toFloat().coerceAtLeast(contentWidth)
        return when (scaleMode) {
            WebViewScaleMode.Standard -> 1f
            WebViewScaleMode.Expanded -> (screenWidth / contentWidth).coerceAtLeast(1f)
            WebViewScaleMode.Shrunk -> {
                val diagonal = sqrt(contentWidth * contentWidth + contentHeight * contentHeight)
                if (diagonal > 0f) {
                    (screenWidth / diagonal).coerceAtMost(1f)
                } else {
                    1f
                }
            }
        }
    }

    private fun buildScaleScript(scaleFactor: Float): String {
        val normalized = scaleFactor.coerceAtLeast(0.1f)
        return """
            (function() {
              var zoom = ${normalized};
              var root = document.documentElement;
              if (!root) return;
              if (Math.abs(zoom - 1) < 0.001) {
                root.style.removeProperty('zoom');
              } else {
                root.style.setProperty('zoom', String(zoom));
              }
            })();
        """.trimIndent()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SCALE_MODE, currentScaleMode.name)
    }

    override fun onDestroy() {
        if (webViewInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        progressAnimator?.cancel()
        super.onDestroy()
    }

    private fun showLoadingRing() {
        if (ringInitialized && loadingRing.visibility != View.VISIBLE) {
            loadingRing.visibility = View.VISIBLE
        }
    }

    private fun hideLoadingRing() {
        if (ringInitialized) {
            loadingRing.visibility = View.GONE
        }
    }

    private fun resetProgress() {
        progressAnimator?.cancel()
        currentProgress = 0f
        if (ringInitialized) {
            loadingRing.setProgress(0f)
        }
    }

    private fun animateProgressTo(target: Float, hideWhenDone: Boolean) {
        if (!ringInitialized) return
        val clamped = target.coerceIn(0f, 1f)
        if (clamped <= currentProgress) {
            currentProgress = clamped
            loadingRing.setProgress(clamped)
            if (hideWhenDone && clamped >= 1f) {
                hideLoadingRing()
            }
            return
        }
        progressAnimator?.cancel()
        val duration = ((clamped - currentProgress) * 700f).coerceIn(150f, 500f).toLong()
        val animator = ValueAnimator.ofFloat(currentProgress, clamped).apply {
            interpolator = progressInterpolator
            this.duration = duration
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                currentProgress = value
                loadingRing.setProgress(value)
            }
            if (hideWhenDone) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (currentProgress >= 1f) {
                            hideLoadingRing()
                        }
                    }
                })
            }
        }
        animator.start()
        progressAnimator = animator
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val KEY_SCALE_MODE = "web_view_scale_mode"

        fun open(context: Context, link: String): Boolean {
            if (requiresInternetConnection(link) && !hasValidatedInternetConnection(context)) {
                showAppToast(context, OFFLINE_USER_MESSAGE)
                return false
            }
            val unavailableMessage = getWebViewUnavailableMessage(context)
            if (unavailableMessage != null) {
                warnWebViewUnavailable(context, unavailableMessage)
                return false
            }
            context.startActivity(createIntent(context, link))
            return true
        }

        fun createIntent(context: Context, link: String): Intent {
            val trimmed = link.trim()
            val resolved = if (trimmed.startsWith("/")) {
                Uri.fromFile(File(trimmed)).toString()
            } else {
                trimmed
            }
            return Intent(context, WebViewActivity::class.java).putExtra(EXTRA_URL, resolved)
        }

        private fun requiresInternetConnection(link: String): Boolean {
            val trimmed = link.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("/")) return false
            return when (Uri.parse(trimmed).scheme?.lowercase()) {
                "http", "https" -> true
                else -> false
            }
        }
    }
}
