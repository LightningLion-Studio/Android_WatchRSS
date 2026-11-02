@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.lightningstudio.watchrss

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.lightningstudio.watchrss.data.bili.formatBiliError
import com.lightningstudio.watchrss.data.media.AudioManagerMediaPlaybackStartVolumeLimiter
import com.lightningstudio.watchrss.data.media.BiliAudioService
import com.lightningstudio.watchrss.ui.components.PlaybackSeekControl
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.components.WatchTextButton
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BiliAudioActivity : BaseWatchActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var loadJob: Job? = null
    private val currentController = mutableStateOf<MediaController?>(null)
    private val loading = mutableStateOf(true)
    private val error = mutableStateOf<String?>(null)
    private val playWhenReady = mutableStateOf(false)
    private val playing = mutableStateOf(false)
    private val hasAudio = mutableStateOf(false)
    private val title = mutableStateOf("B站后台音频")
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            playWhenReady.value = player.playWhenReady
            playing.value = player.isPlaying
            hasAudio.value = player.mediaItemCount > 0
            title.value = player.mediaMetadata.title?.toString() ?: "B站后台音频"
            player.playerError?.let { error.value = "音频播放失败，请重试；过期地址会重新获取" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        setContent {
            WatchRSSTheme {
                WatchSurface {
                    val scroll = rememberScrollState()
                    InstallDigitalCrownScrollHandler(scroll)
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 32.dp)) {
                    Text(title.value, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            !hasAudio.value -> "尚未播放或已停止"
                            currentController.value?.playbackState == Player.STATE_ENDED -> "播放已结束"
                            playing.value -> "正在播放 · 返回后继续播放"
                            playWhenReady.value -> "正在缓冲"
                            else -> "已暂停"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (loading.value) Text("正在准备音频…")
                    error.value?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    PlaybackSeekControl(currentController.value)
                    WatchTextButton(enabled = !loading.value && (currentController.value?.mediaItemCount ?: 0) > 0, onClick = {
                        currentController.value?.let {
                            if (it.playWhenReady) it.pause() else {
                                if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition()
                                it.play()
                            }
                        }
                    }) { Text(if (playWhenReady.value) "暂停音频" else "继续音频") }
                    if (error.value != null) WatchTextButton(onClick = ::loadAudio) { Text("重试音频") }
                    WatchTextButton(onClick = {
                        loadJob?.cancel()
                        val controller = currentController.value
                        if (controller == null) finish() else {
                            val stopped = controller.sendCustomCommand(BiliAudioService.STOP, Bundle.EMPTY)
                            stopped.addListener({
                                runCatching { stopped.get() }.onFailure {
                                    stopService(Intent(this@BiliAudioActivity, BiliAudioService::class.java))
                                }
                                finish()
                            }, ContextCompat.getMainExecutor(this@BiliAudioActivity))
                        }
                    }) { Text("停止后台音频") }
                    WatchTextButton(onClick = ::finish) { Text("返回，继续后台播放") }
                    }
                }
            }
        }
        val future = MediaController.Builder(this, SessionToken(this, ComponentName(this, BiliAudioService::class.java))).buildAsync()
        controllerFuture = future
        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            runCatching { future.get() }.onSuccess {
                currentController.value = it
                it.addListener(listener)
                playWhenReady.value = it.playWhenReady
                playing.value = it.isPlaying
                hasAudio.value = it.mediaItemCount > 0
                title.value = it.mediaMetadata.title?.toString() ?: "B站后台音频"
                loadAudio()
            }.onFailure { loading.value = false; error.value = "无法连接后台音频服务" }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadAudio() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val controller = currentController.value ?: return@launch
            val cid = intent.getLongExtra("audio_cid", 0)
            val bvid = intent.getStringExtra("audio_bvid")?.takeIf(String::isNotBlank)
            val aid = intent.getLongExtra("audio_aid", 0).takeIf { it > 0 }
            val id = "$aid:$bvid:$cid"
            if ((cid <= 0 || controller.currentMediaItem?.mediaId == id) && controller.mediaItemCount > 0 && controller.playerError == null) {
                loading.value = false
                return@launch
            }
            if (cid <= 0) { loading.value = false; error.value = "暂无后台音频，请从视频详情选择只听音频"; return@launch }
            loading.value = true
            error.value = null
            try {
                val container = (application as WatchRssApplication).container
                val originalAccount = container.biliRepository.readAccount()
                val result = container.biliRepository.resolveAudioSource(cid, aid, bvid)
                val source = result.data
                if (!result.isSuccess || source == null) {
                    error.value = formatBiliError(result.code, result.message)
                    return@launch
                }
                if (container.biliRepository.readAccount() != originalAccount) {
                    error.value = "账号已变更，请重新打开音频"
                    return@launch
                }
                val resumePosition = container.biliRepository.readPlaybackProgress(aid, bvid, cid)?.positionMs ?: 0L
                val limit = container.settingsRepository.mediaPlaybackStartVolumeLimitPercent.first()
                AudioManagerMediaPlaybackStartVolumeLimiter(this@BiliAudioActivity).enforcePlaybackStartVolumeLimit(limit)
                val args = Bundle().apply {
                    putString("url", source.url)
                    putString("media_id", id)
                    putLong("position", resumePosition)
                    putString("title", intent.getStringExtra("audio_title") ?: "B站后台音频")
                    putBundle("headers", Bundle().apply { source.headers.forEach { (key, value) -> putString(key, value) } })
                }
                val submitted = controller.sendCustomCommand(BiliAudioService.LOAD, args)
                submitted.addListener({
                    runCatching { submitted.get() }.onSuccess {
                        if (it.resultCode != SessionResult.RESULT_SUCCESS) error.value = "后台音频未启动，请重试"
                    }.onFailure { error.value = "后台音频请求失败" }
                }, ContextCompat.getMainExecutor(this@BiliAudioActivity))
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "无法获取独立音频，请检查网络或重新登录后重试" }
            finally { loading.value = false }
        }
    }

    override fun onDestroy() {
        currentController.value?.removeListener(listener)
        controllerFuture?.let(MediaController::releaseFuture)
        currentController.value = null
        super.onDestroy()
    }

    companion object {
        fun createIntent(context: Context, cid: Long, aid: Long?, bvid: String?, title: String?) =
            Intent(context, BiliAudioActivity::class.java).putExtra("audio_cid", cid)
                .putExtra("audio_aid", aid ?: 0).putExtra("audio_bvid", bvid).putExtra("audio_title", title)
    }
}
