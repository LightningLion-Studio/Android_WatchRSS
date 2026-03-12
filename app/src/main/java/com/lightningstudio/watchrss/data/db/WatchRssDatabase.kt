package com.lightningstudio.watchrss.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.SkipQueryVerification
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RssChannelEntity::class,
        RssItemEntity::class,
        SavedEntryEntity::class,
        OfflineMediaEntity::class
    ],
    version = 6,
    exportSchema = false
)
@SkipQueryVerification
abstract class WatchRssDatabase : RoomDatabase() {
    abstract fun rssChannelDao(): RssChannelDao
    abstract fun rssItemDao(): RssItemDao
    abstract fun savedEntryDao(): SavedEntryDao
    abstract fun offlineMediaDao(): OfflineMediaDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE rss_channels ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE rss_channels ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "UPDATE rss_channels SET sortOrder = createdAt"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE rss_items ADD COLUMN isLiked INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS saved_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemId INTEGER NOT NULL,
                        saveType TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(itemId) REFERENCES rss_items(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_saved_entries_itemId ON saved_entries(itemId)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_saved_entries_itemId_saveType ON saved_entries(itemId, saveType)"
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS offline_media (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemId INTEGER NOT NULL,
                        mediaType TEXT NOT NULL,
                        originUrl TEXT NOT NULL,
                        localPath TEXT,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(itemId) REFERENCES rss_items(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_offline_media_itemId ON offline_media(itemId)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_offline_media_itemId_originUrl ON offline_media(itemId, originUrl)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE rss_channels ADD COLUMN useOriginalContent INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE rss_items ADD COLUMN readingProgress REAL NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE rss_items ADD COLUMN summary TEXT"
                )
                database.execSQL(
                    "ALTER TABLE rss_items ADD COLUMN previewImageUrl TEXT"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_rss_items_channelId_fetchedAt ON rss_items(channelId, fetchedAt)"
                )
            }
        }
    }
}
