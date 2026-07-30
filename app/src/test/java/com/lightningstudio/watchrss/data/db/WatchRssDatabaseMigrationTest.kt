package com.lightningstudio.watchrss.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.lang.reflect.Proxy

class WatchRssDatabaseMigrationTest {
    @Test
    fun migration15To16_onlyAddsReadingPositionColumnsWithDefaults() {
        val statements = mutableListOf<String>()
        val database = recordingDatabase(statements)

        WatchRssDatabase.MIGRATION_15_16.migrate(database)

        val readingPositionColumns = statements
            .filter { it.startsWith("ALTER TABLE rss_items ADD COLUMN ") }
        assertEquals(
            setOf(
                "readingPositionBytes INTEGER NOT NULL DEFAULT 0",
                "readingPositionContentHash TEXT NOT NULL DEFAULT ''",
                "readingPositionChangedAt INTEGER NOT NULL DEFAULT 0"
            ),
            readingPositionColumns.map { it.substringAfter("ADD COLUMN ") }.toSet()
        )
        assertFalse(statements.any { it.contains("DROP ", ignoreCase = true) })
        assertFalse(statements.any { it.contains("DELETE ", ignoreCase = true) })
    }

    private fun recordingDatabase(statements: MutableList<String>): SupportSQLiteDatabase =
        Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, arguments ->
            if (method.name == "execSQL") {
                statements += arguments.orEmpty().first() as String
            }
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase
}
