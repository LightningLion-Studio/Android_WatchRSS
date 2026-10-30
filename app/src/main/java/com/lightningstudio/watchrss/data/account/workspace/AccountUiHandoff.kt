package com.lightningstudio.watchrss.data.account.workspace

/** Accessed under the application's account-switch mutex, on the main thread. */
internal class AccountUiHandoff<T> {
    private var owner: String? = null
    private val stale = linkedSetOf<T>()

    fun defer(sessionId: String, activities: Collection<T>) {
        owner = sessionId
        stale += activities
    }

    fun finish(sessionId: String): Set<T> {
        if (owner != sessionId) return emptySet()
        owner = null
        return stale.toSet().also { stale.clear() }
    }
}
