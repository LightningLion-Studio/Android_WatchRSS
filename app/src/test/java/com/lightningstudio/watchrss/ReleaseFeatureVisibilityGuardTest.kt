package com.lightningstudio.watchrss

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseFeatureVisibilityGuardTest {
    @Test
    fun `debug gated features remain unavailable in release builds`() {
        val profileActivity = source("ProfileActivity.kt")
        val settingsScreen = source("ui/screen/rss/SettingsScreen.kt")

        assertTrue(profileActivity.contains("showAccountEntry = true"))

        val debugGate = settingsScreen.indexOf("if (BuildConfig.DEBUG)")
        val oobe = settingsScreen.indexOf("label = \"新手引导\"")
        val douyinCookie = settingsScreen.indexOf("label = \"抖音登录 Cookie\"")
        val paidAiGate = settingsScreen.indexOf("if (hasPaidAiAccess)")
        assertTrue(debugGate in 0 until oobe)
        assertTrue(oobe in 0 until paidAiGate)
        assertTrue(douyinCookie in 0 until paidAiGate)

        val ttsSettings = source("ui/screen/rss/TtsSettingsScreen.kt")
        assertTrue(ttsSettings.contains("isDetailedTtsConfigurationVisible(buildType: String)"))
        assertTrue(ttsSettings.contains("buildType == \"debug\""))

        val detailScreen = source("ui/screen/rss/DetailScreen.kt")
        assertTrue(detailScreen.contains("): Boolean = llmEnabled"))
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
