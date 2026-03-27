package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.Serializable

@Serializable
data class BiliBrowserProfile(
    val version: Int = CURRENT_VERSION,
    val userAgent: String,
    val acceptLanguage: String,
    val secChUa: String,
    val secChUaMobile: String = "?0",
    val secChUaPlatform: String = "\"Windows\"",
    val referer: String = DEFAULT_REFERER,
    val origin: String = DEFAULT_ORIGIN
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
        const val DEFAULT_REFERER: String = "https://www.bilibili.com/"
        const val DEFAULT_ORIGIN: String = "https://www.bilibili.com"

        fun desktopChrome(
            userAgent: String,
            acceptLanguage: String
        ): BiliBrowserProfile {
            val majorVersion = Regex("""Chrome/(\d+)""")
                .find(userAgent)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
                .ifBlank { "124" }
            return BiliBrowserProfile(
                userAgent = userAgent,
                acceptLanguage = acceptLanguage,
                secChUa = buildSecChUa(majorVersion)
            )
        }

        private fun buildSecChUa(majorVersion: String): String =
            "\"Not_A Brand\";v=\"99\", \"Chromium\";v=\"$majorVersion\", " +
                "\"Google Chrome\";v=\"$majorVersion\""
    }
}
