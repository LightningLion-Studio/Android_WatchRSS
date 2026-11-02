package com.lightningstudio.watchrss.ui.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow

/** Increments once whenever a newly validated default network becomes available. */
@Composable
internal fun rememberValidatedNetworkRevision(context: Context): Int {
    DisposableEffect(context.applicationContext) {
        ValidatedNetworkRevisionSource.start(context.applicationContext)
        onDispose { }
    }
    return ValidatedNetworkRevisionSource.revision.collectAsState().value
}

/** One process-scoped callback serves every visible feed and detail thumbnail. */
private object ValidatedNetworkRevisionSource {
    val revision = MutableStateFlow(0)

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            val handler = Handler(Looper.getMainLooper())
            val networkTracker = ValidatedNetworkTracker<Network>()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    handler.post {
                        if (networkTracker.onCapabilitiesChanged(network, validated)) {
                            revision.value += 1
                        }
                    }
                }

                override fun onLost(network: Network) {
                    handler.post { networkTracker.onLost(network) }
                }
            }
            started = runCatching {
                manager.registerDefaultNetworkCallback(callback)
            }.isSuccess
        }
    }
}
