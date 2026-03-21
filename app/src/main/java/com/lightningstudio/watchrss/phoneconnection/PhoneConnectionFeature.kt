package com.lightningstudio.watchrss.phoneconnection

import com.lightningstudio.watchrss.BuildConfig

object PhoneConnectionFeature {
    val isDebugBuild: Boolean
        get() = BuildConfig.DEBUG

    fun isEnabled(userEnabled: Boolean): Boolean = BuildConfig.DEBUG && userEnabled
}
