package com.lightningstudio.watchrss

object AppLaunchSignal {
    @Volatile
    private var launcherOpenToken: Long = 0L

    fun markLauncherOpen() {
        launcherOpenToken += 1L
    }

    fun currentToken(): Long = launcherOpenToken
}
