package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.util.AppLogger
import java.util.concurrent.atomic.AtomicInteger

internal object WatchBluetoothDebugLog {
    private const val TAG = "BtSyncDebug"
    private const val MAX_VALUE_CHARS = 600
    private val sessionCounter = AtomicInteger(0)

    fun newSessionId(prefix: String): String {
        val safePrefix = prefix.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "bt" }
        return "$safePrefix-${System.currentTimeMillis().toString(36)}-${sessionCounter.incrementAndGet()}"
    }

    fun event(
        sessionId: String,
        event: String,
        fields: Map<String, Any?> = emptyMap()
    ) {
        AppLogger.i(TAG, buildMessage(sessionId, event, fields))
    }

    fun warn(
        sessionId: String,
        event: String,
        fields: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null
    ) {
        AppLogger.w(TAG, buildMessage(sessionId, event, fields), throwable)
    }

    fun error(
        sessionId: String,
        event: String,
        fields: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null
    ) {
        AppLogger.e(TAG, buildMessage(sessionId, event, fields), throwable)
    }

    private fun buildMessage(
        sessionId: String,
        event: String,
        fields: Map<String, Any?>
    ): String = buildString {
        append("event=")
        append(event.escapeValue())
        append(" session=")
        append(sessionId.escapeValue())
        fields.forEach { (key, value) ->
            append(' ')
            append(key.filter { it.isLetterOrDigit() || it == '_' || it == '-' })
            append('=')
            append(value.formatValue())
        }
    }

    private fun Any?.formatValue(): String {
        return when (this) {
            null -> "null"
            is Number,
            is Boolean -> toString()
            else -> toString().escapeValue()
        }
    }

    private fun String.escapeValue(): String {
        val clipped = if (length > MAX_VALUE_CHARS) {
            take(MAX_VALUE_CHARS) + "...<truncated>"
        } else {
            this
        }
        return buildString {
            append('"')
            clipped.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
    }
}
