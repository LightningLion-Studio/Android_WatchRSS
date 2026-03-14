package com.lightningstudio.watchrss.ui.activity

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.AboutActivity
import com.lightningstudio.watchrss.BeianActivity
import com.lightningstudio.watchrss.CollaboratorsActivity
import com.lightningstudio.watchrss.ContactDeveloperActivity
import com.lightningstudio.watchrss.ImagePreviewActivity
import com.lightningstudio.watchrss.InfoActivity
import com.lightningstudio.watchrss.JoinGroupActivity
import com.lightningstudio.watchrss.LogUploadPrivacyActivity
import com.lightningstudio.watchrss.OobeActivity
import com.lightningstudio.watchrss.ProjectInfoActivity
import com.lightningstudio.watchrss.ServerActivity
import com.lightningstudio.watchrss.ShareQrActivity
import com.lightningstudio.watchrss.WebViewActivity
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import com.lightningstudio.watchrss.testutil.TestAppContainer
import com.lightningstudio.watchrss.testutil.TestAppContainerRule
import com.lightningstudio.watchrss.testutil.createTestSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommonActivitySmokeTest {
    private val containerRule = TestAppContainerRule { context ->
        val settingsRepository = createTestSettingsRepository(context, "common-smoke")
        runBlocking {
            settingsRepository.setOobeSeenVersion(CURRENT_OOBE_VERSION)
            settingsRepository.setShareUseSystem(false)
            settingsRepository.setReadingThemeDark(true)
            settingsRepository.setReadingFontSizeSp(14)
            settingsRepository.setPhoneConnectionEnabled(true)
        }
        TestAppContainer(
            context = context,
            rssRepository = FakeRssRepository(),
            settingsRepository = settingsRepository
        )
    }

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(containerRule)

    @Test
    fun aboutActivity_launches() {
        launchAndAssertResumed(AboutActivity::class.java)
    }

    @Test
    fun collaboratorsActivity_launches() {
        launchAndAssertResumed(CollaboratorsActivity::class.java)
    }

    @Test
    fun contactDeveloperActivity_launches() {
        launchAndAssertResumed(ContactDeveloperActivity::class.java)
    }

    @Test
    fun joinGroupActivity_launches() {
        launchAndAssertResumed(JoinGroupActivity::class.java)
    }

    @Test
    fun infoActivity_launches() {
        launchAndAssertResumed(InfoActivity.createIntent(context(), "测试标题", "测试内容"))
    }

    @Test
    fun projectInfoActivity_launches() {
        launchAndAssertResumed(ProjectInfoActivity::class.java)
    }

    @Test
    fun beianActivity_launches() {
        launchAndAssertResumed(BeianActivity.createIntent(context()))
    }

    @Test
    fun imagePreviewActivity_launches() {
        launchAndAssertResumed(
            ImagePreviewActivity.createIntent(
                context = context(),
                url = "https://example.com/image.jpg",
                alt = "测试图片"
            )
        )
    }

    @Test
    fun shareQrActivity_launches() {
        launchAndAssertResumed(
            ShareQrActivity.createIntent(
                context = context(),
                title = "测试分享",
                link = "https://example.com"
            )
        )
    }

    @Test
    fun webViewActivity_launches() {
        launchAndAssertResumed(WebViewActivity.createIntent(context(), "https://example.com"))
    }

    @Test
    fun oobeActivity_launches() {
        launchAndAssertResumed(OobeActivity.createIntent(context(), returnHomeOnFinish = false))
    }

    @Test
    fun logUploadPrivacyActivity_launches() {
        launchAndAssertResumed(LogUploadPrivacyActivity::class.java)
    }

    @Test
    fun serverActivity_launches() {
        launchAndAssertResumed(ServerActivity::class.java)
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun launchAndAssertResumed(activityClass: Class<*>) {
        ActivityScenario.launch<android.app.Activity>(Intent(context(), activityClass)).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        }
    }

    private fun launchAndAssertResumed(intent: Intent) {
        ActivityScenario.launch<android.app.Activity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        }
    }
}
