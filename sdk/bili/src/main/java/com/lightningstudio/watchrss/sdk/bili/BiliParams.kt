package com.lightningstudio.watchrss.sdk.bili

object BiliParams {
    fun defaultAppParams(config: BiliSdkConfig): Map<String, String> = mapOf(
        "appkey" to config.appKey,
        "mobi_app" to config.mobiApp,
        "platform" to config.platform,
        "build" to config.build.toString()
    )
}
