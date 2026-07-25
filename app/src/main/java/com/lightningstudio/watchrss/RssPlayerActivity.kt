package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import com.lightningstudio.watchrss.data.network.InternetAvailabilityStatus
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
import com.lightningstudio.watchrss.ui.screen.bili.BiliPlayerScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.RssPlayerViewModel
import java.io.File
import kotlinx.coroutines.launch

class RssPlayerActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val viewModel: RssPlayerViewModel by viewModels {
        AppViewModelFactory(container)
    }
    private var panOffsetX = 0f
    private var panRangeX = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val awemeId = intent.getStringExtra(RssPlayerViewModel.KEY_AWEME_ID)
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state.initialSource != null) {
                    container.watchUsageTelemetry.recordVideoPlayed(
                        source = if (awemeId.isNullOrBlank()) "rss" else "douyin",
                        id = awemeId ?: viewModel.currentPlayUrl()?.takeLast(40) ?: "",
                        title = state.title
                    )
                }
            }
        }
        setContent {
            WatchRSSTheme {
                val uiState by viewModel.uiState.collectAsState()
                val volumeGuardEnabled by container.settingsRepository.mediaVolumeGuardEnabled.collectAsState(
                    initial = DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
                )
                val volumeControlEnabled by container.settingsRepository.mediaVolumeControlEnabled.collectAsState(
                    initial = DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED
                )
                val playbackStartVolumeLimitPercent by container.settingsRepository.mediaPlaybackStartVolumeLimitPercent.collectAsState(
                    initial = DEFAULT_MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT
                )
                val internetAvailabilityStatus by container.internetAvailabilityMonitor.internetAvailability.collectAsState(
                    initial = InternetAvailabilityStatus.Checking
                )
                val continuePlaybackInBackground by viewModel.continuePlaybackInBackground.collectAsState(
                    initial = false
                )
                BiliPlayerScreen(
                    uiState = uiState,
                    onRetry = viewModel::loadPlayUrl,
                    onPlaybackError = viewModel::recoverFromPlaybackError,
                    onOpenWeb = {
                        val link = viewModel.webUrl() ?: return@BiliPlayerScreen
                        if (link.startsWith("http", ignoreCase = true)) {
                            WebViewActivity.open(this, link)
                        } else {
                            openExternalLink(this, link)
                        }
                    },
                    internetAvailabilityStatus = internetAvailabilityStatus,
                    onPanStateChange = { offsetX, rangeX ->
                        panOffsetX = offsetX
                        panRangeX = rangeX
                    },
                    digitalCrownVolumeEnabled = volumeControlEnabled,
                    volumeGuardEnabled = volumeGuardEnabled,
                    playbackStartVolumeLimitPercent = playbackStartVolumeLimitPercent,
                    continuePlaybackInBackground = continuePlaybackInBackground
                )
            }
        }
    }

    override fun shouldDeferSwipeBack(dx: Float, dy: Float): Boolean {
        if (dx <= 0f) return false
        if (panRangeX <= 0f) return false
        return panOffsetX < panRangeX - 1f
    }

    override fun buildResumeIntent(): Intent? {
        val playUrl = viewModel.currentPlayUrl()?.trim().orEmpty()
        if (playUrl.isBlank()) return null
        val webUrl = viewModel.webUrl()
        val awemeId = viewModel.awemeId()
        return createIntent(
            context = this,
            playUrl = playUrl,
            webUrl = webUrl,
            awemeId = awemeId,
            channelId = intent.getLongExtra(RssPlayerViewModel.KEY_CHANNEL_ID, 0L)
        )
    }

    companion object {
        fun createIntent(
            context: Context,
            playUrl: String,
            webUrl: String? = null,
            awemeId: String? = null,
            channelId: Long = 0L
        ): Intent {
            return Intent(context, RssPlayerActivity::class.java).apply {
                putExtra(RssPlayerViewModel.KEY_PLAY_URL, playUrl)
                putExtra(RssPlayerViewModel.KEY_WEB_URL, webUrl.orEmpty())
                putExtra(RssPlayerViewModel.KEY_AWEME_ID, awemeId.orEmpty())
                putExtra(RssPlayerViewModel.KEY_CHANNEL_ID, channelId)
            }
        }
    }
}

private fun openExternalLink(context: Context, link: String) {
    val trimmed = link.trim()
    if (trimmed.isEmpty()) return
    val uri = if (trimmed.startsWith("/")) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(trimmed))
    } else {
        Uri.parse(trimmed)
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(intent)
}
