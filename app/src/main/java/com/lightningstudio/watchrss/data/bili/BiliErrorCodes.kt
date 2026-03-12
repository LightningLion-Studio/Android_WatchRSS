package com.lightningstudio.watchrss.data.bili

import kotlin.math.abs

object BiliErrorCodes {
    const val REQUEST_FAILED = 9001
    const val MISSING_MID = 9002
    const val MISSING_FAVORITE_FOLDER = 9003
    const val QR_REQUEST_FAILED = 9004
    const val COOKIE_INVALID = 9005
    const val PLAY_PARAM_MISSING = 9006
    const val PLAY_URL_EMPTY = 9007
}

fun formatBiliError(code: Int): String {
    return "RSS解析失败(-${abs(code)})"
}
