package com.lightningstudio.watchrss.ui.screen

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.CloudSyncActivity
import com.lightningstudio.watchrss.ProfileActivity
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/** Device layout check only: never presses sync, logs in or changes account data. */
@RunWith(AndroidJUnit4::class)
class CloudSyncDeviceSmokeTest {
    @Test fun profileAndCloudStatusRender() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".bletest"))
        val out = File(context.getExternalFilesDir(null), "cloud-ui-smoke").apply { mkdirs() }
        fun capture(name: String) {
            Thread.sleep(1000)
            instrumentation.waitForIdleSync()
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        ActivityScenario.launch<ProfileActivity>(Intent(context, ProfileActivity::class.java)).use { capture("native-profile-cloud.png") }
        ActivityScenario.launch<CloudSyncActivity>(Intent(context, CloudSyncActivity::class.java)).use { capture("native-cloud-status.png") }
    }
}
