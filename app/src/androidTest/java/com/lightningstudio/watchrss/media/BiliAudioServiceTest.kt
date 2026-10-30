@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.lightningstudio.watchrss.media

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.lightningstudio.watchrss.BiliAudioActivity
import com.lightningstudio.watchrss.data.media.BiliAudioService
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BiliAudioServiceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun connect(): MediaController {
        val future = main {
            MediaController.Builder(context, SessionToken(context, ComponentName(context, BiliAudioService::class.java))).buildAsync()
        }
        return future.get(15, TimeUnit.SECONDS)
    }

    private fun command(controller: MediaController, args: Bundle): SessionResult =
        main { controller.sendCustomCommand(BiliAudioService.LOAD, args) }.get(10, TimeUnit.SECONDS)

    private fun await(description: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (main(predicate)) return
            SystemClock.sleep(100)
        }
        fail(description)
    }

    @Test fun emptySessionNeverAutoPlaysAndRejectsNonNetworkLoad() {
        val scenario = ActivityScenario.launch<BiliAudioActivity>(Intent(context, BiliAudioActivity::class.java))
        val controller = connect()
        try {
            assertEquals(0, main { controller.mediaItemCount })
            assertFalse(main { controller.playWhenReady })
            assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE, command(controller, Bundle().apply {
                putString("url", "file:///data/local/tmp/audio.wav")
            }).resultCode)
            assertEquals(0, main { controller.mediaItemCount })
            assertEquals(SessionResult.RESULT_SUCCESS, main {
                controller.sendCustomCommand(BiliAudioService.STOP, Bundle.EMPTY)
            }.get(10, TimeUnit.SECONDS).resultCode)
        } finally {
            main { controller.release() }
            scenario.close()
            context.stopService(Intent(context, BiliAudioService::class.java))
        }
    }

    /** Explicit network smoke test; no B站 login or cookie is used. */
    @Test fun audioContinuesWithoutAnActivityWhileScreenIsOffAndCanBeStopped() {
        val url = InstrumentationRegistry.getArguments().getString("audioSmokeUrl")
        assumeTrue("Set audioSmokeUrl to an authorized HTTPS audio test asset", !url.isNullOrBlank())
        val scenario = ActivityScenario.launch<BiliAudioActivity>(Intent(context, BiliAudioActivity::class.java))
        var controller: MediaController? = connect()
        try {
            val initial = controller!!
            assertEquals(SessionResult.RESULT_SUCCESS, command(initial, Bundle().apply {
                putString("url", url)
                putString("media_id", "instrumented-independent-audio")
                putString("title", "后台音频验证")
            }).resultCode)
            await("Audio never reached ready/playing: ${main { initial.playerError }}") {
                initial.playbackState == Player.STATE_READY && initial.isPlaying
            }
            val before = main { initial.currentPosition }
            scenario.close()
            main { initial.release() }
            controller = null
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            SystemClock.sleep(2_000)
            controller = connect()
            val resumed = controller!!
            assertEquals("instrumented-independent-audio", main { resumed.currentMediaItem?.mediaId })
            assertTrue("Audio progress did not advance in background", main { resumed.currentPosition } > before + 500)
            main { resumed.pause() }
            await("Pause did not apply") { !resumed.playWhenReady }
            main { resumed.play() }
            await("Playback did not resume") { resumed.isPlaying }
            assertEquals(SessionResult.RESULT_SUCCESS, main {
                resumed.sendCustomCommand(BiliAudioService.STOP, Bundle.EMPTY)
            }.get(10, TimeUnit.SECONDS).resultCode)
            await("Stop did not clear the audio") { resumed.mediaItemCount == 0 && !resumed.playWhenReady }
        } finally {
            controller?.let { active -> main { active.release() } }
            scenario.close()
            context.stopService(Intent(context, BiliAudioService::class.java))
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        }
    }
}
