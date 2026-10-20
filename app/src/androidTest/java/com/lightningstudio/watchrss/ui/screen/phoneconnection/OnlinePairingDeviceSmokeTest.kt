package com.lightningstudio.watchrss.ui.screen.phoneconnection

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.lightningstudio.watchrss.PhoneConnectionActivity
import com.lightningstudio.watchrss.MiniProgramPairingActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit isolated-device smoke: no registration or account/key writes. */
@RunWith(AndroidJUnit4::class)
class OnlinePairingDeviceSmokeTest {
    @Test fun landingAndOnlineQrRenderOnRoundDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".bletest")) { "Isolated test package required" }
        val out = File(context.getExternalFilesDir(null), "online-pairing-smoke").apply { mkdirs() }
        fun capture(name: String): Bitmap {
            instrumentation.waitForIdleSync()
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return bitmap
        }
        ActivityScenario.launch<PhoneConnectionActivity>(Intent(context, PhoneConnectionActivity::class.java)).use {
            Thread.sleep(1200)
            capture("native-landing.png").recycle()
        }
        ActivityScenario.launch<MiniProgramPairingActivity>(Intent(context, MiniProgramPairingActivity::class.java)).use {
            var decoded: String? = null
            val deadline = android.os.SystemClock.elapsedRealtime() + 25000
            while (decoded == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(700)
                val image = capture("native-online-qr.png")
                val pixels = IntArray(image.width * image.height)
                image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
                decoded = runCatching { QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))).text }.getOrNull()
                image.recycle()
            }
            assertTrue("Internet QR must render and decode on the actual round screen", decoded?.startsWith("wrss-pair:") == true)
            val json = org.json.JSONObject(String(java.util.Base64.getUrlDecoder().decode(decoded!!.substring(10)), Charsets.UTF_8))
            assertEquals("internet", json.getString("transport"))
            assertEquals("watchrss-online-setup-v1", json.getString("protocol"))
        }
    }
}
