package com.lightningstudio.watchrss.data.network

import android.content.Context
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class InternetAvailabilityStatus {
    Checking,
    Unavailable,
    Bluetooth,
    Available
}

interface InternetAvailabilityMonitor {
    val internetAvailability: Flow<InternetAvailabilityStatus>
}

class DefaultInternetAvailabilityMonitor(
    context: Context
) : InternetAvailabilityMonitor {
    private val appContext = context.applicationContext

    override val internetAvailability: Flow<InternetAvailabilityStatus> = callbackFlow {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            trySend(InternetAvailabilityStatus.Unavailable)
            close()
            return@callbackFlow
        }

        fun emitCurrentStatus() {
            trySend(resolveStatus(connectivityManager))
        }

        var isRegistered = false
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                emitCurrentStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                emitCurrentStatus()
            }

            override fun onLost(network: Network) {
                emitCurrentStatus()
            }

            override fun onUnavailable() {
                emitCurrentStatus()
            }
        }
        var debugOverrideObserverRegistered = false
        val debugOverrideObserver = createDebugOverrideObserverOrNull {
            emitCurrentStatus()
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            isRegistered = true
        } catch (error: Exception) {
            AppLogger.e(
                "InternetAvailabilityMonitor",
                "Failed to register default network callback",
                error
            )
        }
        debugOverrideObserver?.let { observer ->
            runCatching {
                appContext.contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(DEBUG_FORCE_STATUS_SETTING),
                    false,
                    observer
                )
                debugOverrideObserverRegistered = true
            }.onFailure { error ->
                AppLogger.e(
                    "InternetAvailabilityMonitor",
                    "Failed to register debug internet override observer",
                    error
                )
            }
        }

        emitCurrentStatus()

        awaitClose {
            if (debugOverrideObserverRegistered && debugOverrideObserver != null) {
                runCatching {
                    appContext.contentResolver.unregisterContentObserver(debugOverrideObserver)
                }.onFailure { error ->
                    AppLogger.e(
                        "InternetAvailabilityMonitor",
                        "Failed to unregister debug internet override observer",
                        error
                    )
                }
            }
            if (isRegistered) {
                runCatching {
                    connectivityManager.unregisterNetworkCallback(callback)
                }.onFailure { error ->
                    AppLogger.e(
                        "InternetAvailabilityMonitor",
                        "Failed to unregister default network callback",
                        error
                    )
                }
            }
        }
    }.distinctUntilChanged()

    private fun resolveStatus(connectivityManager: ConnectivityManager): InternetAvailabilityStatus {
        resolveDebugForcedStatus()?.let { status ->
            AppLogger.d("InternetAvailabilityMonitor", "Debug forced internet availability: $status")
            return status
        }
        val activeNetwork = connectivityManager.activeNetwork ?: return InternetAvailabilityStatus.Unavailable
        return resolveStatus(connectivityManager.getNetworkCapabilities(activeNetwork))
    }

    private fun resolveStatus(capabilities: NetworkCapabilities?): InternetAvailabilityStatus {
        if (capabilities == null) {
            return InternetAvailabilityStatus.Unavailable
        }

        val hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!hasInternetCapability || !isValidated) {
            return InternetAvailabilityStatus.Unavailable
        }

        val hasPreferredTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        if (hasPreferredTransport) {
            return InternetAvailabilityStatus.Available
        }

        return if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            InternetAvailabilityStatus.Bluetooth
        } else {
            InternetAvailabilityStatus.Unavailable
        }
    }

    private fun createDebugOverrideObserverOrNull(onChanged: () -> Unit): ContentObserver? {
        if (!isDebugOverrideEnabled()) return null
        return object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                onChanged()
            }
        }
    }

    private fun resolveDebugForcedStatus(): InternetAvailabilityStatus? {
        if (!isDebugOverrideEnabled()) return null
        val rawValue = runCatching {
            Settings.Global.getString(appContext.contentResolver, DEBUG_FORCE_STATUS_SETTING)
        }.getOrNull()
        return parseDebugForcedStatus(rawValue)
    }

    private fun isDebugOverrideEnabled(): Boolean = BuildConfig.BUILD_TYPE == "debug"

    companion object {
        const val DEBUG_FORCE_STATUS_SETTING = "watchrss.debug.internet_availability"

        internal fun parseDebugForcedStatus(value: String?): InternetAvailabilityStatus? {
            return when (value?.trim()?.lowercase()) {
                "available", "online", "wifi", "cellular", "mobile" -> InternetAvailabilityStatus.Available
                "bluetooth", "bt" -> InternetAvailabilityStatus.Bluetooth
                "unavailable", "offline", "none" -> InternetAvailabilityStatus.Unavailable
                else -> null
            }
        }
    }
}
