package com.lightningstudio.watchrss.sdk.bili

data class BiliSdkConfig(
    val appKey: String = BuildConfig.BILI_APP_KEY,
    val appSec: String = BuildConfig.BILI_APP_SEC,
    val tvAppKey: String = BuildConfig.BILI_TV_APP_KEY,
    val tvAppSec: String = BuildConfig.BILI_TV_APP_SEC,
    val tvMobiApp: String = "android_tv_yst",
    val tvLocalId: String = "0",
    val mobiApp: String = "android",
    val platform: String = "android",
    val build: Int = 7000000,
    val webUserAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    val appUserAgent: String =
        "Mozilla/5.0 (Linux; Android 12; WatchRSS) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36",
    val webReferer: String = "https://www.bilibili.com/",
    val webBaseUrl: String = "https://api.bilibili.com",
    val appBaseUrl: String = "https://app.bilibili.com",
    val passportBaseUrl: String = "https://passport.bilibili.com"
)
