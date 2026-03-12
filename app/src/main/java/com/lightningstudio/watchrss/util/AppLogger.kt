package com.lightningstudio.watchrss.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用日志工具类
 * 同时将日志输出到控制台和持久化文件
 * 文件大小限制为5MB，超过后自动删除最早的日志
 */
object AppLogger {
    private const val TAG = "AppLogger"
    private const val LOG_FILE_NAME = "app_log.txt"
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5MB

    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * 初始化日志系统
     * 应在Application.onCreate()中调用
     */
    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        if (!logFile!!.exists()) {
            logFile!!.createNewFile()
        }
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
        val timestamp = dateFormat.format(Date())
        val levelChar = when (level) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "D"
        }
        val throwableText = throwable?.stackTraceToString()
        val fullMessage = if (throwableText.isNullOrBlank()) {
            message
        } else {
            "$message\n$throwableText"
        }
        val logMessage = "[$timestamp] [$levelChar] [$tag] $fullMessage"

        // 输出到控制台
        when (level) {
            Log.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
            Log.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            else -> Log.println(level, tag, message)
        }

        // 写入文件
        writeToFile(logMessage)
    }

    /**
     * 写入日志到文件
     * 如果文件超过5MB，删除最早的行
     */
    private fun writeToFile(message: String) {
        try {
            val file = logFile ?: return

            // 检查文件大小
            if (file.length() > MAX_FILE_SIZE) {
                trimLogFile(file)
            }

            // 追加日志
            file.appendText("$message\n")
        } catch (e: Exception) {
            Log.e(TAG, "写入日志失败", e)
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

    /**
     * 读取所有日志内容
     * @return 日志文本，如果文件不存在或为空返回null
     */
    fun readLogs(): String? {
        return try {
            val file = logFile ?: return null
            if (!file.exists() || file.length() == 0L) {
                return null
            }
            file.readText()
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
}
