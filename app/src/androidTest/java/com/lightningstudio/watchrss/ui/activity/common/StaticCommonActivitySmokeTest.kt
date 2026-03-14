package com.lightningstudio.watchrss.ui.activity.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.AboutActivity
import com.lightningstudio.watchrss.BeianActivity
import com.lightningstudio.watchrss.CollaboratorsActivity
import com.lightningstudio.watchrss.ContactDeveloperActivity
import com.lightningstudio.watchrss.ImagePreviewActivity
import com.lightningstudio.watchrss.JoinGroupActivity
import com.lightningstudio.watchrss.LogUploadActivity
import com.lightningstudio.watchrss.LogUploadPrivacyActivity
import com.lightningstudio.watchrss.ProjectInfoActivity
import com.lightningstudio.watchrss.RssRecommendActivity
import com.lightningstudio.watchrss.RssRecommendGroupActivity
import com.lightningstudio.watchrss.ShareQrActivity
import com.lightningstudio.watchrss.WebViewActivity
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StaticCommonActivitySmokeTest {
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aboutActivity_launches() {
        launchAndAssertNotFinishing<AboutActivity>()
    }

    @Test
    fun beianActivity_launches() {
        launchIntentAndAssertNotFinishing(BeianActivity.createIntent(appContext))
    }

    @Test
    fun collaboratorsActivity_launches() {
        launchAndAssertNotFinishing<CollaboratorsActivity>()
    }

    @Test
    fun contactDeveloperActivity_launches() {
        launchAndAssertNotFinishing<ContactDeveloperActivity>()
    }

    @Test
    fun joinGroupActivity_launches() {
        launchAndAssertNotFinishing<JoinGroupActivity>()
    }

    @Test
    fun logUploadPrivacyActivity_launches() {
        launchAndAssertNotFinishing<LogUploadPrivacyActivity>()
    }

    @Test
    fun projectInfoActivity_launches() {
        launchAndAssertNotFinishing<ProjectInfoActivity>()
    }

    @Test
    fun rssRecommendActivity_launches() {
        launchAndAssertNotFinishing<RssRecommendActivity>()
    }

    @Test
    fun rssRecommendGroupActivity_launchesWithValidGroup() {
        launchIntentAndAssertNotFinishing(
            Intent(appContext, RssRecommendGroupActivity::class.java).apply {
                putExtra(RssRecommendGroupActivity.EXTRA_GROUP_ID, "36kr")
            }
        )
    }

    @Test
    fun shareQrActivity_launchesWithValidLink() {
        launchIntentAndAssertNotFinishing(
            ShareQrActivity.createIntent(
                context = appContext,
                title = "WatchRSS",
                link = "https://github.com/LightningLion-Studio/WatchRSS"
            )
        )
    }

    @Test
    fun imagePreviewActivity_launchesWithLocalImage() {
        val imageFile = createTempImageFile()

        launchIntentAndAssertNotFinishing(
            ImagePreviewActivity.createIntent(
                context = appContext,
                url = imageFile.absolutePath,
                alt = "test preview"
            )
        )
    }

    @Test
    fun logUploadActivity_launches() {
        launchAndAssertNotFinishing<LogUploadActivity>()
    }

    @Test
    fun webViewActivity_launchesWithLocalHtml() {
        val htmlFile = createTempHtmlFile()

        launchIntentAndAssertNotFinishing(WebViewActivity.createIntent(appContext, htmlFile.absolutePath))
    }

    private inline fun <reified A : Activity> launchAndAssertNotFinishing(intent: Intent? = null) {
        val scenario = if (intent == null) {
            ActivityScenario.launch(A::class.java)
        } else {
            ActivityScenario.launch<Activity>(intent)
        }

        try {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        } finally {
            scenario.close()
        }
    }

    private fun launchIntentAndAssertNotFinishing(intent: Intent) {
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        }
    }

    private fun createTempImageFile(): File {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        val file = File(appContext.cacheDir, "image-preview-smoke.png")
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        return file
    }

    private fun createTempHtmlFile(): File {
        val file = File(appContext.cacheDir, "webview-smoke.html")
        file.writeText(
            """
            <html>
            <head><meta charset="utf-8" /></head>
            <body style="background:#000;color:#fff;">WatchRSS smoke</body>
            </html>
            """.trimIndent()
        )
        return file
    }
}
