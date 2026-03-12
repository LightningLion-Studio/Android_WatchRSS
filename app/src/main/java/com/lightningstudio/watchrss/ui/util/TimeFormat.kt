package com.lightningstudio.watchrss.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

fun formatTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "未更新"
    return timeFormatter.format(Date(timestamp))
}
