package com.lightningstudio.watchrss.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LatestToastSlotTest {
    @Test
    fun replacementDismissesPreviousToastBeforeCreatingTheNextOne() {
        data class ToastHandle(val label: String)
        val events = mutableListOf<String>()
        val slot = LatestToastSlot<ToastHandle> { events += "dismiss:${it.label}" }

        slot.replace {
            events += "show:first"
            ToastHandle("first")
        }
        slot.replace {
            events += "show:latest"
            ToastHandle("latest")
        }

        assertEquals(
            listOf("show:first", "dismiss:first", "show:latest"),
            events
        )
    }
}
