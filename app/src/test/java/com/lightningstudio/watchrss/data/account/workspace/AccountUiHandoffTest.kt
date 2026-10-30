package com.lightningstudio.watchrss.data.account.workspace

import org.junit.Assert.*
import org.junit.Test

class AccountUiHandoffTest {
    @Test fun switchingWaitsForOwningConnectionAndOnlyRefreshesOnce() {
        val handoff = AccountUiHandoff<String>()
        handoff.defer("account-transfer", listOf("reader", "connection"))
        assertTrue(handoff.finish("unrelated-probe").isEmpty())
        assertEquals(setOf("reader", "connection"), handoff.finish("account-transfer"))
        assertTrue(handoff.finish("account-transfer").isEmpty())
    }

    @Test fun olderConnectionCannotRetireUiDuringNewerAccountTransfer() {
        val handoff = AccountUiHandoff<String>()
        handoff.defer("alpha-to-beta", listOf("alpha-reader"))
        handoff.defer("beta-to-gamma", listOf("alpha-reader", "beta-settings"))
        assertTrue(handoff.finish("alpha-to-beta").isEmpty())
        assertEquals(setOf("alpha-reader", "beta-settings"), handoff.finish("beta-to-gamma"))
    }
}
