package com.lightningstudio.watchrss.phoneconnection

import com.lightningstudio.watchrss.BuildConfig

object PhoneConnectionFeature {
    val isAvailable: Boolean
        get() = true

    val isDebugBuild: Boolean
        get() = BuildConfig.DEBUG

    fun isEnabled(userEnabled: Boolean): Boolean = isAvailable && userEnabled
}
