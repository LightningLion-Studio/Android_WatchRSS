package com.lightningstudio.watchrss.ui.screen.rss

import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSettingsVisibilityTest {
    @Test
    fun detailedConfigurationIsVisibleInProduction() {
        assertTrue(isDetailedTtsConfigurationVisible())
    }
}
