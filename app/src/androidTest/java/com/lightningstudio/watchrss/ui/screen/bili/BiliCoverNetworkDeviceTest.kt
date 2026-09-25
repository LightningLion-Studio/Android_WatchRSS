package com.lightningstudio.watchrss.ui.screen.bili

import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Explicitly opt-in emulator test. Restores mobile-data state even after a failed assertion. */
class BiliCoverNetworkDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun actualDefaultNetworkLossAndRecovery_refreshesCoverWithoutTap() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowNetworkToggle") == "true")
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val originalData = shell("settings get global mobile_data").trim()
        val originalWifi = shell("settings get global wifi_on").trim()
        // This scenario deliberately targets the observed cellular-only emulator baseline.
        assertEquals("1", originalData)
        assertEquals("0", originalWifi)
        val image = ByteArrayOutputStream().also {
            Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF347E69.toInt()) }
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val healthy = AtomicBoolean(false)
        val server = object : NanoHTTPD("127.0.0.1", 0) {
            override fun serve(session: IHTTPSession): Response = if (healthy.get()) {
                newFixedLengthResponse(Response.Status.OK, "image/png", image.inputStream(), image.size.toLong())
            } else newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "injected failure")
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.listeningPort}/${UUID.randomUUID()}.png"
            compose.setContent {
                WatchRSSTheme {
                    // Production loader AND production connectivity observer: no injection.
                    BiliFeedCard("网络恢复验证", "合成封面", url, {}, coverRecoveryEnabled = true)
                }
            }
            waitForText("封面加载失败，点此重试")
            capture("01-server-error.png")
            shell("svc data disable")
            waitForText("网络不可用，点此重试")
            capture("02-offline.png")
            healthy.set(true)
            shell("svc data enable")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("网络恢复验证").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("网络恢复验证").assertIsDisplayed()
            compose.onNodeWithText("网络不可用，点此重试").assertDoesNotExist()
            capture("03-recovered.png")
        } finally {
            shell("svc data ${if (originalData == "1") "enable" else "disable"}")
            server.stop()
            assertEquals(originalWifi, shell("settings get global wifi_on").trim())
            assertEquals(originalData, shell("settings get global mobile_data").trim())
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun capture(name: String) {
        val directory = File(compose.activity.getExternalFilesDir(null), "bug003").apply { mkdirs() }
        File(directory, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
}
