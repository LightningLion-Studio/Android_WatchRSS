package com.lightningstudio.watchrss.ui.screen.rss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSettingsVisibilityTest {
    @Test
    fun detailedConfigurationIsOnlyVisibleInDebugBuild() {
        assertTrue(isDetailedTtsConfigurationVisible("debug"))
        assertFalse(isDetailedTtsConfigurationVisible("release"))
        assertFalse(isDetailedTtsConfigurationVisible("profileableRelease"))
    }
}
