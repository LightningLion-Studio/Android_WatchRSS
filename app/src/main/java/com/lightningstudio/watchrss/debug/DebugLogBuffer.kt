package com.lightningstudio.watchrss.debug

import java.io.PrintWriter
import java.util.ArrayDeque

object DebugLogBuffer {
    private const val MAX_ENTRIES = 400
    private val lock = Any()
    private val entries = ArrayDeque<String>(MAX_ENTRIES)
    @Volatile private var enabled = false

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun isEnabled(): Boolean = enabled

    fun log(tag: String, message: String) {
        if (!enabled) return
        val entry = "${System.currentTimeMillis()} [$tag] $message"
        synchronized(lock) {
            if (entries.size >= MAX_ENTRIES) {
                entries.removeFirst()
            }
            entries.addLast(entry)
        }
    }

    fun dump(writer: PrintWriter) {
        val snapshot = synchronized(lock) { entries.toList() }
        writer.println("DebugLogBuffer size=${snapshot.size} max=$MAX_ENTRIES")
        snapshot.forEach { writer.println(it) }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }
}
