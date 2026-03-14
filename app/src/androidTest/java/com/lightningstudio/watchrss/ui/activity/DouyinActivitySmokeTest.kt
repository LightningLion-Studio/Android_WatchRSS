package com.lightningstudio.watchrss.ui.activity

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.DouyinChannelInfoActivity
import com.lightningstudio.watchrss.DouyinDetailActivity
import com.lightningstudio.watchrss.DouyinEntryActivity
import com.lightningstudio.watchrss.DouyinLoginActivity
import com.lightningstudio.watchrss.DouyinPlayerActivity
import com.lightningstudio.watchrss.DouyinSettingsActivity
import com.lightningstudio.watchrss.data.douyin.DouyinResult
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.testutil.FakeDouyinRepository
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import com.lightningstudio.watchrss.testutil.TestAppContainer
import com.lightningstudio.watchrss.testutil.TestAppContainerRule
import com.lightningstudio.watchrss.testutil.createTestSettingsRepository
import com.lightningstudio.watchrss.testutil.sampleDouyinBuiltinChannel
import com.lightningstudio.watchrss.testutil.sampleDouyinFeedPage
import com.lightningstudio.watchrss.testutil.sampleDouyinVideo
import com.lightningstudio.watchrss.testutil.sampleDouyinVideoContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DouyinActivitySmokeTest {
    private val sampleVideo = sampleDouyinVideo()
    private val fakeDouyinRepository = FakeDouyinRepository(
        initialLoggedIn = true,
        initialFeedPage = sampleDouyinFeedPage(listOf(sampleVideo)),
        initialHeaders = mapOf("Referer" to "https://www.douyin.com")
    ).apply {
        setFeedPage(null, DouyinResult(0, data = sampleDouyinFeedPage(listOf(sampleVideo))))
        setVideo(sampleVideo.awemeId.orEmpty(), DouyinResult(0, data = sampleDouyinVideoContent(sampleVideo.awemeId.orEmpty())))
    }
    private val fakeRssRepository = FakeRssRepository(
        initialChannels = listOf(sampleDouyinBuiltinChannel())
    )

    private val containerRule = TestAppContainerRule { context ->
        val settingsRepository = createTestSettingsRepository(context, "douyin-smoke")
        runBlocking {
            settingsRepository.setOobeSeenVersion(CURRENT_OOBE_VERSION)
            settingsRepository.setShareUseSystem(false)
            settingsRepository.setReadingThemeDark(true)
            settingsRepository.setReadingFontSizeSp(14)
        }
        TestAppContainer(
            context = context,
            rssRepository = fakeRssRepository,
            settingsRepository = settingsRepository,
            douyinRepositoryOverride = fakeDouyinRepository
        )
    }

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(containerRule)

    @Test
    fun douyinEntryActivity_launches() {
        launchAndAssertResumed(DouyinEntryActivity::class.java)
    }

    @Test
    fun douyinLoginActivity_launches() {
        launchAndAssertResumed(DouyinLoginActivity.createIntent(context()))
    }

    @Test
    fun douyinChannelInfoActivity_launches() {
        launchAndAssertResumed(DouyinChannelInfoActivity.createIntent(context()))
    }

    @Test
    fun douyinSettingsActivity_launches() {
        launchAndAssertResumed(DouyinSettingsActivity.createIntent(context()))
    }

    @Test
    fun douyinDetailActivity_launches() {
        launchAndAssertResumed(
            DouyinDetailActivity.createIntent(
                context = context(),
                awemeId = sampleVideo.awemeId,
                title = sampleVideo.desc,
                author = sampleVideo.authorName,
                summary = "点赞 ${sampleVideo.likeCount}",
                playUrl = sampleVideo.playUrl,
                coverUrl = sampleVideo.coverUrl
            )
        )
    }

    @Test
    fun douyinPlayerActivity_launches() {
        launchAndAssertResumed(
            DouyinPlayerActivity.createIntent(
                context = context(),
                items = listOf(sampleVideo),
                startIndex = 0
            )
        )
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun launchAndAssertResumed(activityClass: Class<*>) {
        ActivityScenario.launch<android.app.Activity>(android.content.Intent(context(), activityClass)).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        }
    }

    private fun launchAndAssertResumed(intent: android.content.Intent) {
        ActivityScenario.launch<android.app.Activity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        }
    }
}
