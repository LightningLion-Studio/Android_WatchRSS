package com.lightningstudio.watchrss.ui.util

/** Tracks the current validated default network and signals each newly usable network once. */
internal class ValidatedNetworkTracker<T : Any> {
    private var current: T? = null

    fun onCapabilitiesChanged(network: T, validated: Boolean): Boolean {
        if (!validated) {
            if (network == current) current = null
            return false
        }
        if (network == current) return false
        current = network
        return true
    }

    fun onLost(network: T): Boolean {
        if (network != current) return false
        current = null
        return true
    }
}

/** Limits network-triggered retries per image while leaving manual retries unbounded. */
internal class ValidatedNetworkRetryBudget(
    initialRevision: Int,
    private val maxAutomaticRetries: Int = 3
) {
    private var lastRevision = initialRevision
    private var automaticRetries = 0

    fun shouldAttempt(revision: Int): Boolean {
        if (revision == lastRevision) return true
        lastRevision = revision
        if (automaticRetries >= maxAutomaticRetries) return false
        automaticRetries++
        return true
    }
}
