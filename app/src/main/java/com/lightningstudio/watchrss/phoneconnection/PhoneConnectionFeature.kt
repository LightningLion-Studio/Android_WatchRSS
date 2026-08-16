package com.lightningstudio.watchrss.phoneconnection

import com.lightningstudio.watchrss.BuildConfig

object PhoneConnectionFeature {
    /** "连接手机"入口对全量构建开放（含 release）。 */
    val isAvailable: Boolean
        get() = true

    val isDebugBuild: Boolean
        get() = BuildConfig.DEBUG

    fun isEnabled(userEnabled: Boolean): Boolean = isAvailable && userEnabled
}
