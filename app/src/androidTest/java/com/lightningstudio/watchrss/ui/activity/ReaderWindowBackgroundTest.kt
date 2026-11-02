package com.lightningstudio.watchrss.ui.activity

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.DetailActivity
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.reader.ReaderPreset
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import com.lightningstudio.watchrss.testutil.TestAppContainer
import com.lightningstudio.watchrss.testutil.TestAppContainerRule
import com.lightningstudio.watchrss.testutil.createTestSettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReaderWindowBackgroundTest {
    @get:Rule
    val containerRule = TestAppContainerRule { context ->
        TestAppContainer(
            context = context,
            rssRepository = FakeRssRepository(),
            settingsRepository = createTestSettingsRepository(context, "reader-window-background")
        )
    }

    @Test
    fun activePresetColorUpdatesReaderWindowAndBottomNavigationBar() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<WatchRssApplication>()
        val repository = application.container.readerPresetRepository
        val previousPreset = repository.activePreset.value
        val fixtureId = "reader-window-${UUID.randomUUID()}"
        val firstColor = 0xFF123456L
        val secondColor = 0xFF654321L
        var scenario: ActivityScenario<DetailActivity>? = null

        try {
            val fixture = repository.savePreset(
                ReaderPreset.lightDefault(name = "窗口背景测试").copy(
                    id = fixtureId,
                    background = previousPreset.background.copy(colorArgb = firstColor)
                ),
                applyAfterSave = true
            )
            scenario = ActivityScenario.launch(
                Intent(application, DetailActivity::class.java)
            )
            assertWindowColors(scenario, firstColor)

            repository.savePreset(
                fixture.copy(background = fixture.background.copy(colorArgb = secondColor)),
                applyAfterSave = true
            )
            assertWindowColors(scenario, secondColor)
        } finally {
            scenario?.close()
            repository.setActivePreset(previousPreset.id)
            withTimeout(10_000) { repository.activePreset.first { it.id == previousPreset.id } }
            repository.deletePreset(fixtureId)
        }
    }

    private fun assertWindowColors(scenario: ActivityScenario<DetailActivity>, expected: Long) {
        var windowColor: Int? = null
        var navigationColor: Int? = null
        repeat(40) {
            scenario.onActivity { activity ->
                windowColor = (activity.window.decorView.background as? ColorDrawable)?.color
                navigationColor = activity.window.navigationBarColor
            }
            if (windowColor == expected.toInt() && navigationColor == expected.toInt()) return
            Thread.sleep(100)
        }
        assertEquals("Reader window background", expected.toInt(), windowColor)
        assertEquals("Bottom navigation bar", expected.toInt(), navigationColor)
    }
}
