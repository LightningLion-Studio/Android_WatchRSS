package com.lightningstudio.watchrss.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.data.reader.ReaderBackground
import com.lightningstudio.watchrss.data.reader.ReaderBackgroundFit
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderVideoBackgroundTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun keepPreviewVisible() {
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            it.setTurnScreenOn(true)
            it.setShowWhenLocked(true)
        }
    }

    @Test fun imageFocusReachesFullFrameCornersAfterZoom() {
        val file = File(compose.activity.cacheDir, "reader-image-corners.png")
        val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).forEachIndexed { i, color ->
            paint.color = color
            val x = (i % 2) * 400f; val y = (i / 2) * 200f
            canvas.drawRect(x, y, x + 400f, y + 200f, paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val background = mutableStateOf(ReaderBackground(zoom = 2f, focusX = 0f, focusY = 0f))
        compose.setContent {
            Box(Modifier.fillMaxSize().clipToBounds().testTag("background")) {
                ReaderBackgroundMedia(file, null, background.value)
            }
        }
        compose.waitUntil(10_000) {
            val pixels = compose.onNodeWithTag("background").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            center.red > .9f && center.green < .1f
        }
        compose.runOnIdle { background.value = background.value.copy(zoom = 8f, focusX = 1f, focusY = 1f) }
        compose.waitUntil(5_000) {
            val pixels = compose.onNodeWithTag("background").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            center.red > .9f && center.green > .9f && center.blue < .1f
        }
        compose.runOnIdle { background.value = background.value.copy(zoom = 1f, focusX = .5f, focusY = .5f, blurDp = 24f) }
        compose.waitUntil(5_000) {
            val pixels = compose.onNodeWithTag("background").captureToImage().toPixelMap()
            val color = pixels[pixels.width / 2 - 6, pixels.height / 4]
            color.red > .1f && color.green > .1f
        }
        file.delete()
    }

    @Test fun videoUsesFullFrameFocusAndCanBlurDuringPlaybackOnAndroid11() {
        val file = File(compose.activity.cacheDir, "reader-video-corners.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("reader-backgrounds/corners.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val background = mutableStateOf(ReaderBackground(zoom = 2f, focusX = 0f, focusY = 0f))
        compose.setContent {
            Box(Modifier.fillMaxSize().clipToBounds().testTag("video-background")) {
                ReaderBackgroundMedia(null, file, background.value)
            }
        }
        compose.waitUntil(20_000) {
            val pixels = compose.onNodeWithTag("video-background").captureToImage().toPixelMap()
            val color = pixels[pixels.width / 2, pixels.height / 4]
            color.red > .8f && color.green < .1f
        }
        compose.runOnIdle { background.value = background.value.copy(focusX = 1f, focusY = 1f) }
        compose.waitUntil(5_000) {
            val pixels = compose.onNodeWithTag("video-background").captureToImage().toPixelMap()
            val color = pixels[pixels.width / 2, pixels.height * 3 / 4]
            color.red > .8f && color.green > .8f
        }
        compose.runOnIdle { background.value = background.value.copy(zoom = 1f, focusX = .5f, focusY = .5f, blurDp = 24f) }
        compose.waitUntil(5_000) {
            val pixels = compose.onNodeWithTag("video-background").captureToImage().toPixelMap()
            val color = pixels[pixels.width / 2 - 6, pixels.height / 4]
            color.red > .1f && color.green > .1f
        }
        file.delete()
    }

    @Test fun videoKeepsPlayingWhilePanningAndZoomingAndPausesInBackground() {
        val file = File(compose.activity.cacheDir, "reader-video-test.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("reader-backgrounds/sdr.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val background = mutableStateOf(ReaderBackground(fit = ReaderBackgroundFit.CROP))
        val visible = mutableStateOf(true)
        compose.setContent {
            if (visible.value) Box(Modifier.fillMaxSize().clipToBounds()) {
                ReaderBackgroundMedia(null, file, background.value)
            }
        }
        var texture: TextureView? = null
        compose.waitUntil(20_000) {
            compose.runOnUiThread {
                texture = findTexture(compose.activity.window.decorView)
            }
            texture?.isAvailable == true && texture?.alpha == 1f
        }
        val original = texture!!
        val width = original.width
        val beforeTransform = transform(original)
        val firstTime = timestamp(original)
        compose.runOnIdle { background.value = background.value.copy(zoom = 2f, focusX = 1f, focusY = 1f) }
        compose.waitUntil(5_000) { transform(original)[Matrix.MSCALE_X] > beforeTransform[Matrix.MSCALE_X] * 1.9f }
        compose.runOnIdle {
            assertSame(original, findTexture(compose.activity.window.decorView))
            assertEquals("Zoom must not enlarge the playback surface", width, original.width)
            assertTrue("Zoomed full frame must move to show its right edge", transform(original)[Matrix.MTRANS_X] < 0)
        }
        compose.waitUntil(5_000) { timestamp(original) != firstTime }
        // Real rendered frame data must change, not only the layout or a static poster.
        val pixels = frameHash(original)
        compose.waitUntil(5_000) { frameHash(original) != pixels }
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        Thread.sleep(500)
        val pausedTime = timestamp(original)
        Thread.sleep(400)
        assertEquals("Paused activity must stop producing video frames", pausedTime, timestamp(original))
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(5_000) { timestamp(original) != pausedTime }
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertFalse(original.isAttachedToWindow)
        file.delete()
    }

    @Test fun switchingResourcesRebindsTheTextureAndRendersANewFirstFrame() {
        val first = File(compose.activity.cacheDir, "reader-video-first.mp4")
        val second = File(compose.activity.cacheDir, "reader-video-second.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("reader-backgrounds/sdr.mp4").use { input ->
            first.outputStream().use { input.copyTo(it) }
        }
        first.copyTo(second, overwrite = true)
        val current = mutableStateOf(first)
        compose.setContent { ReaderBackgroundMedia(null, current.value, ReaderBackground()) }
        var original: TextureView? = null
        compose.waitUntil(20_000) {
            compose.runOnUiThread { original = findTexture(compose.activity.window.decorView) }
            original?.alpha == 1f
        }
        compose.runOnIdle { current.value = second }
        compose.waitUntil(20_000) {
            var next: TextureView? = null
            compose.runOnUiThread { next = findTexture(compose.activity.window.decorView) }
            next != null && next !== original && next?.alpha == 1f
        }
        first.delete(); second.delete()
    }

    private fun transform(view: TextureView): FloatArray {
        val result = FloatArray(9)
        compose.runOnUiThread { view.getTransform(Matrix()).getValues(result) }
        return result
    }

    private fun timestamp(view: TextureView): Long {
        var result = -1L
        compose.runOnUiThread { result = view.surfaceTexture?.timestamp ?: -1L }
        return result
    }
    private fun frameHash(view: TextureView): Int {
        var result = 0
        compose.runOnUiThread {
            view.getBitmap(32, 32)?.let { bitmap ->
                val pixels = IntArray(1024)
                bitmap.getPixels(pixels, 0, 32, 0, 0, 32, 32)
                result = pixels.contentHashCode(); bitmap.recycle()
            }
        }
        return result
    }
    private fun findTexture(view: View): TextureView? {
        if (view is TextureView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findTexture(view.getChildAt(i))?.let { return it }
        return null
    }
}
