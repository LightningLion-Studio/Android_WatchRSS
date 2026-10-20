package com.lightningstudio.watchrss.data.cloud

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class WatchRecordProjection(val contentHash: String, val importedAt: Long, val metadataOnly: Boolean = false)
internal data class WatchRecordStamp(val revision: String, val fingerprint: String,
    val remoteContentHash: String? = null, val localContentHash: String? = null,
    val remoteImportedAt: Long? = null, val localImportedAt: Long? = null, val metadataOnly: Boolean = false)
internal data class WatchRecordStoredRow(val recordId: String, val stamp: WatchRecordStamp)
internal interface WatchRecordStore {
    fun reset(scope: String)
    fun cursor(scope: String): String
    fun record(scope: String, id: String): WatchRecordStamp?
    fun commitPage(scope: String, cursor: String, rows: List<WatchRecordStoredRow>)
    fun commitWrites(scope: String, rows: List<WatchRecordStoredRow>)
}

/** Only opaque IDs, revisions, hashes and origin timestamps; no article text or key. */
internal class WatchCloudRecordStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "watchrss_cloud_record_cursors.db", null, 2
), WatchRecordStore {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cursors(scope TEXT PRIMARY KEY NOT NULL, revision TEXT NOT NULL)")
        db.execSQL("CREATE TABLE records(scope TEXT NOT NULL, record_id TEXT NOT NULL, revision TEXT NOT NULL, fingerprint TEXT NOT NULL, remote_hash TEXT, local_hash TEXT, remote_imported_at INTEGER, local_imported_at INTEGER, metadata_only INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(scope,record_id))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE records ADD COLUMN remote_hash TEXT")
            db.execSQL("ALTER TABLE records ADD COLUMN local_hash TEXT")
            db.execSQL("ALTER TABLE records ADD COLUMN remote_imported_at INTEGER")
            db.execSQL("ALTER TABLE records ADD COLUMN local_imported_at INTEGER")
            db.execSQL("ALTER TABLE records ADD COLUMN metadata_only INTEGER NOT NULL DEFAULT 0")
            // Version-one debug caches lack projection baselines. Re-read
            // records to establish them; retain revisions and all local content.
            db.execSQL("UPDATE cursors SET revision='0'")
        }
    }
    override fun reset(scope: String) = transaction { db ->
        db.delete("records", "scope=?", arrayOf(scope))
        db.delete("cursors", "scope=?", arrayOf(scope))
    }
    override fun cursor(scope: String): String = readableDatabase.rawQuery(
        "SELECT revision FROM cursors WHERE scope=?", arrayOf(scope)).use { if (it.moveToFirst()) it.getString(0) else "0" }
    override fun record(scope: String, id: String): WatchRecordStamp? = readableDatabase.rawQuery(
        "SELECT revision,fingerprint,remote_hash,local_hash,remote_imported_at,local_imported_at,metadata_only FROM records WHERE scope=? AND record_id=?", arrayOf(scope, id)
    ).use { if (it.moveToFirst()) WatchRecordStamp(it.getString(0), it.getString(1), it.getString(2), it.getString(3),
            if (it.isNull(4)) null else it.getLong(4), if (it.isNull(5)) null else it.getLong(5), it.getInt(6) != 0) else null }
    override fun commitPage(scope: String, cursor: String, rows: List<WatchRecordStoredRow>) = transaction { db ->
        rows.forEach { write(db, scope, it) }
        check(db.insertWithOnConflict("cursors", null, ContentValues().apply {
            put("scope", scope); put("revision", cursor)
        }, SQLiteDatabase.CONFLICT_REPLACE) >= 0L)
        Unit
    }
    override fun commitWrites(scope: String, rows: List<WatchRecordStoredRow>) = transaction { db ->
        rows.forEach { write(db, scope, it) }
    }
    private fun write(db: SQLiteDatabase, scope: String, row: WatchRecordStoredRow) {
        check(db.insertWithOnConflict("records", null, ContentValues().apply {
            put("scope", scope); put("record_id", row.recordId); put("revision", row.stamp.revision)
            put("fingerprint", row.stamp.fingerprint)
            put("metadata_only", if (row.stamp.metadataOnly) 1 else 0)
            put("remote_hash", row.stamp.remoteContentHash); put("local_hash", row.stamp.localContentHash)
            row.stamp.remoteImportedAt?.let { put("remote_imported_at", it) } ?: putNull("remote_imported_at")
            row.stamp.localImportedAt?.let { put("local_imported_at", it) } ?: putNull("local_imported_at")
        }, SQLiteDatabase.CONFLICT_REPLACE) >= 0L)
    }
    private inline fun transaction(block: (SQLiteDatabase) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try { block(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
}
