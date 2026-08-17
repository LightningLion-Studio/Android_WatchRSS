package com.lightningstudio.watchrss.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneSyncActionsLabelTest {

    @Test
    fun `sync label describes the action that starts immediately`() {
        assertEquals("开始同步收藏", phoneSyncStartLabel("同步收藏"))
        assertEquals("开始同步稍后再看", phoneSyncStartLabel("同步稍后再看"))
        assertEquals("开始同步B站观看记录", phoneSyncStartLabel("同步B站观看记录"))
    }
}
