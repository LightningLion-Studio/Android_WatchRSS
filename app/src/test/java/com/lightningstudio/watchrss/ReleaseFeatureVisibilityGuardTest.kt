package com.lightningstudio.watchrss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseFeatureVisibilityGuardTest {
    @Test
    fun `product features remain available in release builds`() {
        val profileActivity = source("ProfileActivity.kt")
        val settingsScreen = source("ui/screen/rss/SettingsScreen.kt")

        assertTrue(profileActivity.contains("showAccountEntry = true"))
        assertFalse(profileActivity.contains("showAccountEntry = BuildConfig.DEBUG"))

        val phoneInput = settingsScreen.indexOf("operation = \"从手机输入 RSS\"")
        val oobe = settingsScreen.indexOf("label = \"新手引导\"")
        val douyinCookie = settingsScreen.indexOf("label = \"抖音登录 Cookie\"")
        val performanceGate = settingsScreen.indexOf("if (BuildConfig.DEBUG && showPerformanceTools)")
        assertTrue(phoneInput >= 0)
        assertTrue(oobe in 0 until performanceGate)
        assertTrue(douyinCookie in 0 until performanceGate)

        val ttsSettings = source("ui/screen/rss/TtsSettingsScreen.kt")
        assertTrue(ttsSettings.contains("isDetailedTtsConfigurationVisible(): Boolean = true"))

        val detailScreen = source("ui/screen/rss/DetailScreen.kt")
        assertTrue(detailScreen.contains("): Boolean = llmEnabled && !isNovelContent"))
        assertFalse(detailScreen.contains("): Boolean = isDebugBuild && llmEnabled"))
    }

    @Test
    fun `debug tooling remains unavailable in release builds`() {
        val settingsScreen = source("ui/screen/rss/SettingsScreen.kt")
        assertTrue(settingsScreen.contains("if (BuildConfig.DEBUG && showPerformanceTools)"))

        val debugMask = source("BaseWatchActivity.kt")
        assertTrue(debugMask.contains("if (!BuildConfig.ENABLE_WATCH_DEBUG_MASK)"))

        val bluetoothDiagnostics = source("DebugBluetoothSyncActivity.kt")
        assertTrue(bluetoothDiagnostics.contains("ApplicationInfo.FLAG_DEBUGGABLE"))
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/lightningstudio/watchrss/$relativePath").readText()
}
