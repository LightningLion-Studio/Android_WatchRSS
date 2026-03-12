package com.lightningstudio.watchrss.sdk.bili

object BiliDebugLog {
    @Volatile
    private var logger: ((String, String) -> Unit)? = null

    fun setLogger(value: ((String, String) -> Unit)?) {
        logger = value
    }

    fun log(tag: String, message: String) {
        logger?.invoke(tag, message)
    }
}
