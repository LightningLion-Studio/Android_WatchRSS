package com.lightningstudio.watchrss.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val SENSITIVE_HEADER_PATTERN =
    Regex("""(?i)\b((?:set-)?cookie|authorization|proxy-authorization|x-watchrss-pairing-auth)\s*([:=])\s*[^\r\n]*""")
private val BEARER_TOKEN_PATTERN =
    Regex("""(?i)\b(Bearer\s+)[A-Za-z0-9._~+/\-=]+""")
private val SENSITIVE_VALUE_PATTERN = Regex(
    """(?i)\b(""" + listOf(
        "access_token",
        "api_key",
        "apiKey",
        "appRefreshToken",
        "bili_jct",
        "csrf_session_id",
        "DedeUserID",
        "DedeUserID__ckMd5",
        "msToken",
        "n_mh",
        "odin_tt",
        "passport_csrf_token",
        "passport_csrf_token_default",
        "refresh_token",
        "refreshToken",
        "SESSDATA",
        "sessionid",
        "sessionid_ss",
        "sid",
        "sid_guard",
        "sid_tt",
        "sid_ucp_v1",
        "s_v_web_id",
        "ssid_ucp_v1",
        "ttwid",
        "uid_tt",
        "uid_tt_ss",
        "watchrss_pair"
    ).joinToString("|") { Regex.escape(it) } + """)\s*=\s*([^;\s,\]\)}]+)"""
)

internal fun redactSensitiveLogContent(raw: String): String {
    if (raw.isEmpty()) return raw
    return SENSITIVE_VALUE_PATTERN.replace(
        BEARER_TOKEN_PATTERN.replace(
            SENSITIVE_HEADER_PATTERN.replace(raw) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}<redacted>"
            }
        ) { match ->
            "${match.groupValues[1]}<redacted>"
        }
    ) { match ->
        "${match.groupValues[1]}=<redacted>"
    }
}

/**
 * 应用日志工具类
 * 同时将日志输出到控制台和持久化文件
 * 文件大小限制为5MB，超过后自动删除最早的日志
 */
object AppLogger {
    private const val TAG = "AppLogger"
    private const val LOG_FILE_NAME = "app_log.txt"
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5MB
    private const val FLUSH_TIMEOUT_MS = 1500L

    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileIoExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AppLogger-IO").apply {
            isDaemon = true
        }
    }

    /**
     * 初始化日志系统
     * 应在Application.onCreate()中调用
     */
    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        redactExistingLogFile()
        log("AppLogger", "日志系统初始化完成")
    }

    /**
     * 记录日志
     * 同时输出到控制台和文件
     */
    fun log(tag: String, message: String) {
        d(tag, message)
    }

    fun v(tag: String, message: String) {
        write(Log.VERBOSE, tag, message)
    }

    fun d(tag: String, message: String) {
        write(Log.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        write(Log.INFO, tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        write(Log.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        write(Log.ERROR, tag, message, throwable)
    }

    private fun write(level: Int, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = synchronized(dateFormat) {
            dateFormat.format(Date())
        }
        val levelChar = when (level) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "D"
        }
        val safeMessage = redactSensitiveLogContent(message)
        val throwableText = throwable?.stackTraceToString()?.let(::redactSensitiveLogContent)
        val fullMessage = if (throwableText.isNullOrBlank()) {
            safeMessage
        } else {
            "$safeMessage\n$throwableText"
        }
        val logMessage = "[$timestamp] [$levelChar] [$tag] $fullMessage"

        // 输出到控制台，使用已脱敏的 fullMessage，避免 Throwable message 泄露敏感字段。
        runCatching {
            Log.println(level, tag, fullMessage)
        }

        // 写入文件
        writeToFile(logMessage)
    }

    /**
     * 写入日志到文件
     * 如果文件超过5MB，删除最早的行
     */
    private fun writeToFile(message: String) {
        val targetFile = logFile ?: return
        fileIoExecutor.execute {
            try {
                if (!targetFile.exists()) {
                    targetFile.parentFile?.mkdirs()
                    targetFile.createNewFile()
                }

                // 检查文件大小
                if (targetFile.length() > MAX_FILE_SIZE) {
                    trimLogFile(targetFile)
                }

                // 追加日志
                targetFile.appendText("$message\n")
            } catch (e: Exception) {
                Log.e(TAG, "写入日志失败", e)
            }
        }
    }

    /**
     * 删除最早的日志行，直到文件小于5MB
     */
    private fun trimLogFile(file: File) {
        try {
            val lines = file.readLines().toMutableList()

            // 删除前20%的行
            val linesToRemove = (lines.size * 0.2).toInt()
            if (linesToRemove > 0) {
                repeat(linesToRemove) {
                    if (lines.isNotEmpty()) {
                        lines.removeAt(0)
                    }
                }
            }

            // 重写文件
            file.writeText(lines.joinToString("\n") + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "清理日志文件失败", e)
        }
    }

    private fun redactExistingLogFile() {
        val targetFile = logFile ?: return
        fileIoExecutor.execute {
            try {
                if (!targetFile.exists() || targetFile.length() == 0L) return@execute
                val raw = targetFile.readText()
                val redacted = redactSensitiveLogContent(raw)
                if (redacted != raw) {
                    targetFile.writeText(redacted)
                }
            } catch (e: Exception) {
                Log.e(TAG, "清理敏感日志失败", e)
            }
        }
    }

    /**
     * 读取所有日志内容
     * @return 日志文本，如果文件不存在或为空返回null
     */
    fun readLogs(): String? {
        return try {
            val file = logFile ?: return null
            flushPendingWrites()
            if (!file.exists() || file.length() == 0L) {
                return null
            }
            redactSensitiveLogContent(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "读取日志失败", e)
            null
        }
    }

    /**
     * 清空日志文件
     */
    fun clearLogs() {
        try {
            logFile?.writeText("")
            log(TAG, "日志已清空")
        } catch (e: Exception) {
            Log.e(TAG, "清空日志失败", e)
        }
    }

    private fun flushPendingWrites() {
        if (Thread.currentThread().name == "AppLogger-IO") return
        try {
            fileIoExecutor.submit {}.get(FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "等待日志写入完成超时或失败", e)
        }
    }
}
