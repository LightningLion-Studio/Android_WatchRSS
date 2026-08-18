package com.lightningstudio.watchrss

import android.annotation.SuppressLint
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.lightningstudio.watchrss.ui.screen.WebViewScreen
import com.lightningstudio.watchrss.ui.reader.readerChromeStyle
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

enum class WebViewScaleMode {
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
    private val readerPresetRepository by lazy {
        (application as WatchRssApplication).container.readerPresetRepository
    }
    private lateinit var webView: WebView
    private lateinit var loadingRing: ProgressRingView
    private var progressAnimator: ValueAnimator? = null
    private var currentProgress = 0f
    private val progressInterpolator = DecelerateInterpolator()
    private var webViewInitialized = false
    private var ringInitialized = false
    private var currentScaleMode = WebViewScaleMode.Standard

    override fun onCreate(savedInstanceState: Bundle?) {
        currentScaleMode = savedInstanceState?.getString(KEY_SCALE_MODE)
            ?.let { value -> runCatching { WebViewScaleMode.valueOf(value) }.getOrNull() }
            ?: WebViewScaleMode.Standard
        val initialReaderChrome = readerPresetRepository.activePreset.value.readerChromeStyle()
        setTheme(
            if (initialReaderChrome.isDark) {
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
            val activePreset by readerPresetRepository.activePreset.collectAsState()
            val readerChrome = activePreset.readerChromeStyle()

            WatchRSSTheme(darkTheme = readerChrome.isDark) {
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
                }

                LaunchedEffect(readerChrome) {
                    if (webViewInitialized) {
                        applyWebThemePreference(
                            isDark = readerChrome.isDark,
                            backgroundColor = readerChrome.backgroundColor.toArgb()
                        )
                    }
                }

                WebViewScreen(
                    backgroundColor = readerChrome.backgroundColor,
                    errorMessage = errorMessage,
                    scaleMode = scaleMode,
                    onToggleScaleMode = { scaleMode = scaleMode.next() },
                    onWebViewReady = { view ->
                        if (!webViewInitialized) {
                            webViewInitialized = true
                            webView = view
                            try {
                                setupWebView()
                                applyWebThemePreference(
                                    isDark = readerChrome.isDark,
                                    backgroundColor = readerChrome.backgroundColor.toArgb()
                                )
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
    private fun applyWebThemePreference(isDark: Boolean, backgroundColor: Int) {
        webView.setBackgroundColor(backgroundColor)
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
                if (isDark) {
                    WebSettingsCompat.FORCE_DARK_ON
                } else {
                    WebSettingsCompat.FORCE_DARK_OFF
                }
            )
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDark)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
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
            return when (trimmed.toUri().scheme?.lowercase()) {
                "http", "https" -> true
                else -> false
            }
        }
    }
}
