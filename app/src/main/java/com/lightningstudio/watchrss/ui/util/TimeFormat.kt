package com.lightningstudio.watchrss.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "未更新"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
