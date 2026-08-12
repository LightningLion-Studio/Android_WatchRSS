package com.lightningstudio.watchrss.data.network

import com.lightningstudio.watchrss.BuildConfig
import okhttp3.Request

internal const val WATCHRSS_APP_VERSION_HEADER = "X-WatchRSS-App-Version"

internal fun watchRssAppVersionHeaderValue(
    versionName: String = BuildConfig.VERSION_NAME,
    versionCode: Int = BuildConfig.VERSION_CODE
): String = "watch-${versionName.trim()}+$versionCode"

internal fun Request.Builder.withWatchRssAppVersionHeader(): Request.Builder =
    header(WATCHRSS_APP_VERSION_HEADER, watchRssAppVersionHeaderValue())
