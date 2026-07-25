package com.lightningstudio.watchrss.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType

internal object BuiltinChannelSeed {
    private const val COLUMN_LIST =
        "url, title, description, imageUrl, lastFetchedAt, createdAt, sortOrder, isPinned, useOriginalContent, continuePlaybackInBackground"
    private const val VALUE_PLACEHOLDERS = "(?,?,?,?,?,?,?,?,?,?)"

    val callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            insertInto(db)
        }
    }

    fun rows(now: Long = System.currentTimeMillis()): List<RssChannelEntity> {
        return BuiltinChannelType.values().mapIndexed { index, type ->
            val timestamp = now - index
            RssChannelEntity(
                url = type.url,
                title = type.title,
                description = type.description,
                imageUrl = null,
                lastFetchedAt = null,
                createdAt = timestamp,
                sortOrder = timestamp,
                isPinned = false,
                useOriginalContent = type.useOriginalContentByDefault
            )
        }
    }

    fun insertInto(database: SupportSQLiteDatabase, now: Long = System.currentTimeMillis()) {
        val command = buildInsertCommand(now) ?: return
        database.execSQL(command.sql, command.bindArgs)
    }

    fun buildInsertCommand(now: Long = System.currentTimeMillis()): BuiltinChannelInsertCommand? {
        val rows = rows(now)
        if (rows.isEmpty()) return null
        val sql = buildString {
            append("INSERT OR IGNORE INTO rss_channels(")
            append(COLUMN_LIST)
            append(") VALUES ")
            append(List(rows.size) { VALUE_PLACEHOLDERS }.joinToString(","))
        }
        val bindArgs = ArrayList<Any?>(rows.size * 10)
        rows.forEach { row ->
            bindArgs += row.url
            bindArgs += row.title
            bindArgs += row.description
            bindArgs += row.imageUrl
            bindArgs += row.lastFetchedAt
            bindArgs += row.createdAt
            bindArgs += row.sortOrder
            bindArgs += if (row.isPinned) 1 else 0
            bindArgs += if (row.useOriginalContent) 1 else 0
            bindArgs += if (row.continuePlaybackInBackground) 1 else 0
        }
        return BuiltinChannelInsertCommand(sql = sql, bindArgs = bindArgs.toTypedArray())
    }
}

internal data class BuiltinChannelInsertCommand(
    val sql: String,
    val bindArgs: Array<Any?>
)
