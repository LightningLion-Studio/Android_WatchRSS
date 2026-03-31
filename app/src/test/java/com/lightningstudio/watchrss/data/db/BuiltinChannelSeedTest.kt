package com.lightningstudio.watchrss.data.db

import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinChannelSeedTest {
    @Test
    fun rows_matchBuiltinDefinitionsAndStableOrdering() {
        val now = 1_700_000_000_000L

        val rows = BuiltinChannelSeed.rows(now)

        assertEquals(BuiltinChannelType.values().size, rows.size)
        assertEquals(BuiltinChannelType.BILI.url, rows[0].url)
        assertEquals(BuiltinChannelType.DOUYIN.url, rows[1].url)
        assertEquals(now, rows[0].createdAt)
        assertEquals(now - 1, rows[1].createdAt)
        assertEquals(now, rows[0].sortOrder)
        assertEquals(now - 1, rows[1].sortOrder)
        assertEquals(true, rows.all { !it.isPinned })
    }

    @Test
    fun buildInsertCommand_createsSingleInsertStatementForAllBuiltins() {
        val command = BuiltinChannelSeed.buildInsertCommand(now = 1234L)
        val sql = command?.sql.orEmpty()
        val args = command?.bindArgs ?: emptyArray()
        assertTrue(sql.startsWith("INSERT OR IGNORE INTO rss_channels("))
        assertTrue(sql.contains("VALUES (?,?,?,?,?,?,?,?,?),(?,?,?,?,?,?,?,?,?)"))
        assertEquals(18, args.size)
        assertEquals(BuiltinChannelType.BILI.url, args[0])
        assertEquals("哔哩哔哩", args[1])
        assertEquals(1234L, args[5])
        assertEquals(1234L, args[6])
        assertEquals(0, args[7])
        assertEquals(1, args[8])
        assertEquals(BuiltinChannelType.DOUYIN.url, args[9])
        assertEquals("抖音", args[10])
        assertEquals(1233L, args[14])
        assertEquals(1233L, args[15])
        assertEquals(0, args[16])
        assertEquals(1, args[17])
    }
}
