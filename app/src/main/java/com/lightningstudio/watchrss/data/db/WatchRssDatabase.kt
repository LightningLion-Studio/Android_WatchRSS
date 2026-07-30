package com.lightningstudio.watchrss.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.SkipQueryVerification
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lightningstudio.watchrss.data.reader.ReaderBackgroundAssetEntity
import com.lightningstudio.watchrss.data.reader.ReaderDeletionEntity
import com.lightningstudio.watchrss.data.reader.ReaderFontAssetEntity
import com.lightningstudio.watchrss.data.reader.ReaderPresetDao
import com.lightningstudio.watchrss.data.reader.ReaderPresetEntity

@Database(
    entities = [
        RssChannelEntity::class,
        RssItemEntity::class,
        SavedEntryEntity::class,
        SavedSyncStateEntity::class,
        OfflineMediaEntity::class,
        SyncChangeLogEntity::class,
        SyncPeerStateEntity::class,
        RssSourceSyncStateEntity::class,
        ReaderPresetEntity::class,
        ReaderFontAssetEntity::class,
        ReaderBackgroundAssetEntity::class,
        ReaderDeletionEntity::class,
        LlmTokenUsageEntity::class
    ],
    version = 17,
    exportSchema = false
)
@SkipQueryVerification
abstract class WatchRssDatabase : RoomDatabase() {
    abstract fun rssChannelDao(): RssChannelDao
    abstract fun rssItemDao(): RssItemDao
    abstract fun savedEntryDao(): SavedEntryDao
    abstract fun savedSyncStateDao(): SavedSyncStateDao
    abstract fun offlineMediaDao(): OfflineMediaDao
    abstract fun syncChangeLogDao(): SyncChangeLogDao
    abstract fun syncPeerStateDao(): SyncPeerStateDao
    abstract fun rssSourceSyncStateDao(): RssSourceSyncStateDao
    abstract fun readerPresetDao(): ReaderPresetDao
    abstract fun llmTokenUsageDao(): LlmTokenUsageDao

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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE rss_items ADD COLUMN originalContent TEXT"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_rss_items_isRead_channelId ON rss_items(isRead, channelId)"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE saved_entries ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "UPDATE saved_entries SET sortOrder = createdAt"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS saved_sync_states (
                        articleId TEXT NOT NULL,
                        saveType TEXT NOT NULL,
                        itemId INTEGER,
                        url TEXT NOT NULL,
                        saved INTEGER NOT NULL,
                        changedAt INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        sourceDeviceId TEXT NOT NULL,
                        PRIMARY KEY(articleId, saveType)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_saved_sync_states_itemId ON saved_sync_states(itemId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_saved_sync_states_saveType_saved ON saved_sync_states(saveType, saved)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE rss_channels ADD COLUMN continuePlaybackInBackground INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    UPDATE rss_items
                    SET
                        content = CASE
                            WHEN content IS NOT NULL AND length(content) > 100000 THEN NULL
                            ELSE content
                        END,
                        originalContent = CASE
                            WHEN originalContent IS NOT NULL AND length(originalContent) > 100000 THEN NULL
                            ELSE originalContent
                        END
                    WHERE channelId IN (
                        SELECT id FROM rss_channels
                        WHERE url = 'watchrss://phone-imports'
                           OR url LIKE 'https://watchrss.local/import-content%'
                    )
                    AND (
                        (content IS NOT NULL AND length(content) > 100000) OR
                        (originalContent IS NOT NULL AND length(originalContent) > 100000)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE rss_items ADD COLUMN syncBodyHash TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE rss_items ADD COLUMN syncBodyByteCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE rss_items ADD COLUMN syncChunkSize INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE rss_items ADD COLUMN syncChunkHashesJson TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE rss_items ADD COLUMN syncMetadataHash TEXT NOT NULL DEFAULT ''")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_rss_items_syncBodyHash ON rss_items(syncBodyHash)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_change_log (
                        seq INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        kind TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        changedAt INTEGER NOT NULL,
                        originDeviceId TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_change_log_kind_entityId ON sync_change_log(kind, entityId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_change_log_seq ON sync_change_log(seq)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_peer_state (
                        peerDeviceId TEXT NOT NULL PRIMARY KEY,
                        lastLocalSeqAckedByPeer INTEGER NOT NULL DEFAULT 0,
                        lastRemoteSeqApplied INTEGER NOT NULL DEFAULT 0,
                        lastFullSyncAt INTEGER NOT NULL DEFAULT 0,
                        lastProtocolVersion INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rss_source_sync_states (
                        url TEXT NOT NULL PRIMARY KEY,
                        sourceDeviceId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        siteUrl TEXT,
                        imageUrl TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        isPinned INTEGER NOT NULL,
                        deleted INTEGER NOT NULL,
                        deletedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.createReaderPresetTables()
                database.createLlmTokenUsageTable()
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.createReaderPresetTables()
                database.execSQL(
                    "ALTER TABLE rss_items ADD COLUMN readingPositionBytes INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE rss_items ADD COLUMN readingPositionContentHash TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE rss_items ADD COLUMN readingPositionChangedAt INTEGER NOT NULL DEFAULT 0"
                )
                database.createLlmTokenUsageTable()
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.createLlmTokenUsageTable()
            }
        }
    }
}

private fun SupportSQLiteDatabase.createReaderPresetTables() {
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS reader_presets (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            payloadJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL,
            modifiedBy TEXT NOT NULL,
            deleted INTEGER NOT NULL
        )
        """.trimIndent()
    )
    execSQL("CREATE INDEX IF NOT EXISTS index_reader_presets_deleted_name ON reader_presets(deleted, name)")
    execSQL("CREATE INDEX IF NOT EXISTS index_reader_presets_updatedAt ON reader_presets(updatedAt)")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS reader_font_assets (
            id TEXT NOT NULL PRIMARY KEY,
            sha256 TEXT NOT NULL,
            displayName TEXT NOT NULL,
            familyName TEXT NOT NULL,
            fileName TEXT NOT NULL,
            mimeType TEXT NOT NULL,
            byteCount INTEGER NOT NULL,
            faceCount INTEGER NOT NULL,
            metadataJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL,
            modifiedBy TEXT NOT NULL,
            deleted INTEGER NOT NULL
        )
        """.trimIndent()
    )
    execSQL("CREATE INDEX IF NOT EXISTS index_reader_font_assets_deleted_displayName ON reader_font_assets(deleted, displayName)")
    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reader_font_assets_sha256 ON reader_font_assets(sha256)")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS reader_background_assets (
            id TEXT NOT NULL PRIMARY KEY,
            sha256 TEXT NOT NULL,
            displayName TEXT NOT NULL,
            kind TEXT NOT NULL,
            mimeType TEXT NOT NULL,
            masterFileName TEXT NOT NULL,
            byteCount INTEGER NOT NULL,
            durationMs INTEGER NOT NULL,
            width INTEGER NOT NULL,
            height INTEGER NOT NULL,
            posterAssetId TEXT,
            variantsJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL,
            modifiedBy TEXT NOT NULL,
            deleted INTEGER NOT NULL
        )
        """.trimIndent()
    )
    execSQL("CREATE INDEX IF NOT EXISTS index_reader_background_assets_deleted_displayName ON reader_background_assets(deleted, displayName)")
    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reader_background_assets_sha256 ON reader_background_assets(sha256)")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS reader_deletions (
            kind TEXT NOT NULL,
            entityId TEXT NOT NULL,
            deletedAt INTEGER NOT NULL,
            deletedBy TEXT NOT NULL,
            PRIMARY KEY(kind, entityId)
        )
        """.trimIndent()
    )
    execSQL("CREATE INDEX IF NOT EXISTS index_reader_deletions_deletedAt ON reader_deletions(deletedAt)")
}

private fun SupportSQLiteDatabase.createLlmTokenUsageTable() {
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS llm_token_usage (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            provider TEXT NOT NULL,
            model TEXT NOT NULL,
            requestId TEXT NOT NULL,
            promptTokens INTEGER,
            completionTokens INTEGER,
            totalTokens INTEGER,
            reasoningTokens INTEGER,
            cachedPromptTokens INTEGER,
            inputTokens INTEGER,
            outputTokens INTEGER,
            promptTokenCount INTEGER,
            candidatesTokenCount INTEGER,
            totalTokenCount INTEGER,
            createdAt INTEGER NOT NULL
        )
        """.trimIndent()
    )
    execSQL(
        "CREATE INDEX IF NOT EXISTS index_llm_token_usage_provider_createdAt " +
            "ON llm_token_usage(provider, createdAt)"
    )
    execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_llm_token_usage_requestId " +
            "ON llm_token_usage(requestId)"
    )
}
