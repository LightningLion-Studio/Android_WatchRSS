package com.lightningstudio.watchrss.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatedNetworkTrackerTest {
    @Test
    fun retriesOnceForEachNewValidatedNetworkAndIgnoresOldNetworkLoss() {
        val tracker = ValidatedNetworkTracker<String>()

        assertFalse(tracker.onCapabilitiesChanged("cellular", validated = false))
        assertTrue(tracker.onCapabilitiesChanged("wifi", validated = true))
        assertFalse(tracker.onCapabilitiesChanged("wifi", validated = true))

        assertFalse(tracker.onLost("stale-network"))
        assertTrue(tracker.onLost("wifi"))
        assertTrue(tracker.onCapabilitiesChanged("cellular", validated = true))
        assertFalse(tracker.onCapabilitiesChanged("cellular", validated = true))
        assertFalse(tracker.onLost("wifi"))

        assertTrue(tracker.onLost("cellular"))
        assertTrue(tracker.onCapabilitiesChanged("cellular", validated = true))
    }

    @Test
    fun losingAnOlderNetworkCannotClearTheCurrentValidatedNetwork() {
        val tracker = ValidatedNetworkTracker<String>()
        assertTrue(tracker.onCapabilitiesChanged("wifi", validated = true))
        assertTrue(tracker.onCapabilitiesChanged("cellular", validated = true))

        assertFalse(tracker.onLost("wifi"))
        assertFalse(tracker.onCapabilitiesChanged("cellular", validated = true))
    }
}
