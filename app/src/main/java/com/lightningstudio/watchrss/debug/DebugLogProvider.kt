package com.lightningstudio.watchrss.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import java.io.PrintWriter

class DebugLogProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun dump(fd: java.io.FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
        if (!DebugLogBuffer.isEnabled()) {
            writer.println("DebugLogProvider disabled.")
            return
        }
        val argList = args?.toList().orEmpty()
        if ("--clear" in argList) {
            DebugLogBuffer.clear()
            writer.println("DebugLogBuffer cleared.")
            return
        }
        DebugLogBuffer.dump(writer)
    }
}
