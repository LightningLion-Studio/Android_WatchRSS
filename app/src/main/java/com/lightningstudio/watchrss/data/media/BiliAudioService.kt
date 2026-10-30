package com.lightningstudio.watchrss.data.media

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lightningstudio.watchrss.BiliAudioActivity
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.WatchRssApplication
import java.net.URI

/** A player owned by the service. No Activity, video surface, or video track is retained. */
@UnstableApi
class BiliAudioService : MediaSessionService() {
    private var session: MediaSession? = null
    private val httpFactory = DefaultHttpDataSource.Factory()

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        val activity = PendingIntent.getActivity(this, 0, Intent(this, BiliAudioActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player)
            .setSessionActivity(activity)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    if (controller.packageName != packageName && !controller.isTrusted) return MediaSession.ConnectionResult.reject()
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(STOP)
                    if (controller.packageName == packageName) commands.add(LOAD)
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands.build())
                        .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                            .remove(Player.COMMAND_SET_MEDIA_ITEM).remove(Player.COMMAND_CHANGE_MEDIA_ITEMS).build())
                        .build()
                }

                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    if (command.customAction == STOP_ACTION) {
                        session.player.pause()
                        session.player.stop()
                        session.player.clearMediaItems()
                        stopSelf()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    if (command.customAction != LOAD_ACTION || controller.packageName != packageName) {
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED))
                    }
                    val url = args.getString("url").orEmpty()
                    val uri = runCatching { URI(url) }.getOrNull()
                    if (uri?.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) {
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                    }
                    val headers = args.getBundle("headers") ?: Bundle.EMPTY
                    httpFactory.setDefaultRequestProperties(headers.keySet().associateWith { headers.getString(it).orEmpty() })
                    (application as WatchRssApplication).container.readAloudController.stop()
                    session.player.setMediaItem(MediaItem.Builder()
                        .setMediaId(args.getString("media_id").orEmpty()).setUri(url)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(args.getString("title") ?: "B站后台音频").build())
                        .build(), args.getLong("position", 0L).coerceAtLeast(0))
                    session.player.prepare()
                    session.player.play()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }).build().also {
                it.setCustomLayout(listOf(CommandButton.Builder().setDisplayName("停止后台音频")
                    .setIconResId(R.drawable.ic_audio_stop).setSessionCommand(STOP).build()))
            }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session?.takeIf { controllerInfo.packageName == packageName || controllerInfo.isTrusted }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    companion object {
        const val LOAD_ACTION = "com.lightningstudio.watchrss.audio.LOAD"
        const val STOP_ACTION = "com.lightningstudio.watchrss.audio.STOP"
        val LOAD = SessionCommand(LOAD_ACTION, Bundle.EMPTY)
        val STOP = SessionCommand(STOP_ACTION, Bundle.EMPTY)
    }
}
