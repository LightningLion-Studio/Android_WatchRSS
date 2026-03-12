package com.lightningstudio.watchrss

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
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.lightningstudio.watchrss.ui.screen.WebViewScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.widget.ProgressRingView
import java.io.File

class WebViewActivity : BaseWatchActivity() {
    private lateinit var webView: WebView
    private lateinit var loadingRing: ProgressRingView
    private var progressAnimator: ValueAnimator? = null
    private var currentProgress = 0f
    private val progressInterpolator = DecelerateInterpolator()
    private var webViewInitialized = false
    private var ringInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            WatchRSSTheme {
                WebViewScreen(
                    onWebViewReady = { view ->
                        if (!webViewInitialized) {
                            webViewInitialized = true
                            webView = view
                            setupWebView()
                            webView.loadUrl(url)
                        }
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

    private fun setupWebView() {
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.watch_background_deep))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = true
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

        fun createIntent(context: Context, link: String): Intent {
            val trimmed = link.trim()
            val resolved = if (trimmed.startsWith("/")) {
                Uri.fromFile(File(trimmed)).toString()
            } else {
                trimmed
            }
            return Intent(context, WebViewActivity::class.java).putExtra(EXTRA_URL, resolved)
        }
    }
}
