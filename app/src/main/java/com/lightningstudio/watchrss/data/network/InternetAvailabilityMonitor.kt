package com.lightningstudio.watchrss.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
                trySend(resolveStatus(networkCapabilities))
            }

            override fun onLost(network: Network) {
                emitCurrentStatus()
            }

            override fun onUnavailable() {
                trySend(InternetAvailabilityStatus.Unavailable)
            }
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

        emitCurrentStatus()

        awaitClose {
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
}
