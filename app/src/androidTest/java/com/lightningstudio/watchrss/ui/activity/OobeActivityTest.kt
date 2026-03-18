package com.lightningstudio.watchrss.ui.activity

import android.app.Activity
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.lightningstudio.watchrss.MainActivity
import com.lightningstudio.watchrss.OobeActivity
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import com.lightningstudio.watchrss.testutil.TestAppContainer
import com.lightningstudio.watchrss.testutil.TestAppContainerRule
import com.lightningstudio.watchrss.testutil.createTestSettingsRepository
import com.lightningstudio.watchrss.ui.testing.OobeTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OobeActivityTest {
    private lateinit var settingsRepository: com.lightningstudio.watchrss.data.settings.SettingsRepository

    private val containerRule = TestAppContainerRule { context ->
        settingsRepository = createTestSettingsRepository(context, "oobe-activity")
        runBlocking {
            settingsRepository.setOobeSeenVersion(0)
            settingsRepository.setPhoneConnectionEnabled(true)
        }
        TestAppContainer(
            context = context,
            rssRepository = FakeRssRepository(),
            settingsRepository = settingsRepository
        )
    }

    private val composeRule = createAndroidComposeRule<OobeActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(containerRule).around(composeRule)

    @Test
    fun completingOobe_thenBackFromHome_doesNotReturnToOobe() {
        composeRule.onNodeWithTag(OobeTestTags.NEXT_BUTTON).assertExists().performClick()
        composeRule.onNodeWithTag(OobeTestTags.AGREEMENT_CHECKBOX).assertExists().performClick()
        composeRule.onNodeWithTag(OobeTestTags.CONTINUE_BUTTON).assertExists().performClick()

        waitUntil(timeoutMillis = 5_000) {
            currentResumedActivity()?.javaClass == MainActivity::class.java
        }

        assertEquals(
            com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION,
            runBlocking { settingsRepository.oobeSeenVersion.first() }
        )

        pressBackUnconditionally()

        waitUntil(timeoutMillis = 5_000) {
            currentResumedActivity() == null
        }
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Condition not met within ${timeoutMillis}ms")
    }

    private fun currentResumedActivity(): Activity? {
        var resumedActivity: Activity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resumedActivity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .firstOrNull()
        }
        return resumedActivity
    }
}
