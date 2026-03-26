package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.data.settings.DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.testing.SettingsTestTags
import com.lightningstudio.watchrss.ui.util.isSystemShareSettingSupported
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val showSystemShareSetting = isSystemShareSettingSupported(
        ApplicationProvider.getApplicationContext()
    )

    @Test
    fun settingsScreen_rendersCoreControls() {
        composeRule.setWatchContent {
            SettingsScreen(
                cacheLimitMb = MutableStateFlow(1024L),
                cacheUsageMb = MutableStateFlow(256L),
                readingThemeDark = MutableStateFlow(true),
                shareUseSystem = MutableStateFlow(false),
                readingFontSizeSp = MutableStateFlow(14),
                phoneConnectionEnabled = MutableStateFlow(true),
                rssInlineImagePrefetchMode = MutableStateFlow(DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE),
                llmFeatureEnabled = MutableStateFlow(false),
                llmAutoSummarize = MutableStateFlow(false),
                llmShowTokenUsage = MutableStateFlow(false),
                llmPromptPreset = MutableStateFlow(0),
                showPerformanceTools = false,
                onSelectCacheLimit = {},
                onToggleReadingTheme = {},
                onToggleShareMode = {},
                onSelectFontSize = {},
                onTogglePhoneConnection = {},
                onSelectRssInlineImagePrefetchMode = {},
                onToggleLlmFeatureEnabled = {},
                onToggleLlmAutoSummarize = {},
                onToggleLlmShowTokenUsage = {},
                onOpenOobe = {},
                onOpenPerfLargeList = {},
                onOpenPerfLargeArticle = {},
                onOpenDouyinCookieInput = {},
                onOpenLlmConnectivity = {},
                onOpenLlmPhoneConfig = {},
                onOpenLlmPromptPreset = {},
                onBeianClick = {}
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.ROOT).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.THEME_SWITCH, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.FONT_VALUE, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.ADVANCED_ENTRY, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.OPEN_OOBE_ENTRY, useUnmergedTree = true).performScrollTo().assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.PHONE_CONNECTION_SWITCH, useUnmergedTree = true).performScrollTo().assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.DOUYIN_COOKIE_ENTRY, useUnmergedTree = true).performScrollTo().assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.BEIAN_ENTRY, useUnmergedTree = true).performScrollTo().assertExists()

        composeRule.onNodeWithTag(SettingsTestTags.ADVANCED_ENTRY, useUnmergedTree = true).performScrollTo().performClick()

        composeRule.onNodeWithTag(SettingsTestTags.CACHE_VALUE, useUnmergedTree = true).assertExists()
        if (showSystemShareSetting) {
            composeRule.onNodeWithTag(SettingsTestTags.SHARE_SWITCH, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun settingsScreen_interactionsInvokeCallbacks() {
        val cacheSelections = mutableListOf<Long>()
        val fontSelections = mutableListOf<Int>()
        var themeToggleCount = 0

        composeRule.setWatchContent {
            SettingsScreen(
                cacheLimitMb = MutableStateFlow(1024L),
                cacheUsageMb = MutableStateFlow(256L),
                readingThemeDark = MutableStateFlow(true),
                shareUseSystem = MutableStateFlow(false),
                readingFontSizeSp = MutableStateFlow(14),
                phoneConnectionEnabled = MutableStateFlow(true),
                rssInlineImagePrefetchMode = MutableStateFlow(DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE),
                llmFeatureEnabled = MutableStateFlow(false),
                llmAutoSummarize = MutableStateFlow(false),
                llmShowTokenUsage = MutableStateFlow(false),
                llmPromptPreset = MutableStateFlow(0),
                showPerformanceTools = false,
                onSelectCacheLimit = { cacheSelections += it },
                onToggleReadingTheme = { themeToggleCount += 1 },
                onToggleShareMode = {},
                onSelectFontSize = { fontSelections += it },
                onTogglePhoneConnection = {},
                onSelectRssInlineImagePrefetchMode = {},
                onToggleLlmFeatureEnabled = {},
                onToggleLlmAutoSummarize = {},
                onToggleLlmShowTokenUsage = {},
                onOpenOobe = {},
                onOpenPerfLargeList = {},
                onOpenPerfLargeArticle = {},
                onOpenDouyinCookieInput = {},
                onOpenLlmConnectivity = {},
                onOpenLlmPhoneConfig = {},
                onOpenLlmPromptPreset = {},
                onBeianClick = {}
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.THEME_SWITCH, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.FONT_DECREASE_BUTTON, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag(SettingsTestTags.FONT_INCREASE_BUTTON, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag(SettingsTestTags.ADVANCED_ENTRY, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag(SettingsTestTags.CACHE_DECREASE_BUTTON, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.CACHE_INCREASE_BUTTON, useUnmergedTree = true).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(768L, 1536L), cacheSelections)
            assertEquals(listOf(12, 16), fontSelections)
            assertEquals(1, themeToggleCount)
        }
    }
}
