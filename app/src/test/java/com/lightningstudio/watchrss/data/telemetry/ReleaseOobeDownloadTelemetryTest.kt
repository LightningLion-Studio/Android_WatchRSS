package com.lightningstudio.watchrss.data.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseOobeDownloadTelemetryTest {
    @Test
    fun `all release watch OOBE opens qualify regardless of screen size`() {
        assertFalse(shouldCountReleaseOobeOpen(debugBuild = true))
        assertTrue(shouldCountReleaseOobeOpen(debugBuild = false))
    }
}
