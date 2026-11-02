package com.lightningstudio.watchrss.sdk.douyin

/** A caller supplies a trusted local snapshot. This class never downloads configuration. */
class DouyinRequestConfig(
    val versionCode: String = "170400",
    val versionName: String = "17.4.0",
    val browserVersion: String = "155.0.0.0",
    val userAgent: String = DouyinWebCrawler.USER_AGENT,
    private val sessionParameters: Map<String, String> = emptyMap()
) {
    init {
        require(versionCode.matches(Regex("[0-9]{1,9}")))
        require(listOf(versionName, browserVersion).all {
            it.matches(Regex("[0-9]{1,4}(\\.[0-9]{1,4}){1,3}"))
        })
        require(userAgent.length in 1..512 && userAgent.all { it.code in 32..126 })
        require(sessionParameters.all { (key, value) ->
            key in SESSION_KEYS && value.length <= 2048 && value.all { it.code in 33..126 }
        })
    }

    fun parameters(base: Map<String, String>): MutableMap<String, String> = base.toMutableMap().apply {
        put("version_code", versionCode)
        put("version_name", versionName)
        put("browser_version", browserVersion)
        put("engine_version", browserVersion)
        put("msToken", "")
        putAll(sessionParameters)
        if (containsKey("verifyFp") && !containsKey("fp")) put("fp", getValue("verifyFp"))
        if (containsKey("fp") && !containsKey("verifyFp")) put("verifyFp", getValue("fp"))
    }

    companion object {
        private val SESSION_KEYS = setOf("verifyFp", "fp", "msToken", "webid", "uifid")
    }
}
