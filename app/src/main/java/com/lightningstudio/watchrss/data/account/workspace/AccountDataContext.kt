package com.lightningstudio.watchrss.data.account.workspace

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.lightningstudio.watchrss.WatchRssApplication
import java.io.File
import java.security.MessageDigest

/** Immutable for the lifetime of a container, including its unfinished IO. */
internal class AccountDataContext(
    base: Context,
    val spaceKey: String,
    private val legacy: Boolean
) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = if (legacy) baseContext.filesDir else
        File(baseContext.filesDir, "accounts/$spaceKey").apply { mkdirs() }
    override fun getCacheDir(): File = if (legacy) baseContext.cacheDir else
        File(baseContext.cacheDir, "accounts/$spaceKey").apply { mkdirs() }
    override fun getDatabasePath(name: String): File = if (legacy) baseContext.getDatabasePath(name) else
        File(baseContext.getDatabasePath(name).parentFile, "accounts/$spaceKey/$name").apply {
            parentFile?.mkdirs()
        }
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        baseContext.openOrCreateDatabase(getDatabasePath(name).absolutePath, mode, factory)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
        baseContext.openOrCreateDatabase(getDatabasePath(name).absolutePath, mode, factory, errorHandler)
    override fun deleteDatabase(name: String): Boolean =
        SQLiteDatabase.deleteDatabase(getDatabasePath(name))
    private fun preferenceName(name: String): String = if (legacy) name else "account_${spaceKey}_$name"
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        baseContext.getSharedPreferences(preferenceName(name), mode)
    override fun deleteSharedPreferences(name: String): Boolean =
        baseContext.deleteSharedPreferences(preferenceName(name))
}

/** Old files stay in place and receive a durable owner once; other accounts start empty. */
internal fun accountDataContext(context: Context, environment: String, userId: String?): Context {
    val key = accountSpaceKey(environment, userId)
    val prefs = context.getSharedPreferences("watchrss_library_ownership", Context.MODE_PRIVATE)
    val owner = synchronized(AccountDataContext::class.java) {
        prefs.getString("legacy_owner", null) ?: key.also {
            check(prefs.edit().putString("legacy_owner", it).commit()) { "无法保存旧资料归属" }
        }
    }
    return AccountDataContext(context, key, legacy = owner == key)
}

internal fun accountSpaceKey(environment: String, userId: String?): String {
    require(environment.isNotBlank())
    require(userId == null || userId.isNotBlank())
    // Length framing separates guest from an account literally named 'guest' and avoids collisions.
    val value = "$environment:${userId?.let { "user:${it.length}:$it" } ?: "guest"}"
    return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/** Resolve once when a store/editor is created; never retarget an existing store on account change. */
internal fun libraryContext(context: Context): Context {
    if (context is AccountDataContext) return context
    val app = context.applicationContext as? WatchRssApplication ?: return context.applicationContext
    var owner: Context = context
    while (owner is ContextWrapper && owner !is android.app.Activity) {
        val next = owner.baseContext
        if (next === owner) break
        owner = next
    }
    return if (owner is android.app.Activity) app.dataContextFor(owner) else app.libraryContext
}
