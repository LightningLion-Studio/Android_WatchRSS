package com.lightningstudio.watchrss.data.cloud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private data class CloudNetworkState(val network: Long?, val internet: Boolean, val unmetered: Boolean, val charging: Boolean)

/** Observed only while enabled; bandwidth-only changes cannot schedule another transfer. */
internal fun watchCloudNetworkEvents(context: Context): Flow<Any> = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val battery = context.getSystemService(BatteryManager::class.java)
    fun sendState(network: Network?, capabilities: NetworkCapabilities?) {
        trySend(CloudNetworkState(network?.networkHandle,
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
            battery.isCharging))
    }
    fun refresh() {
        val network = manager.activeNetwork
        sendState(network, network?.let(manager::getNetworkCapabilities))
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) { refresh() }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { sendState(network, capabilities) }
    }
    val power = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { refresh() }
    }
    val filter = IntentFilter(Intent.ACTION_POWER_CONNECTED).apply { addAction(Intent.ACTION_POWER_DISCONNECTED) }
    manager.registerDefaultNetworkCallback(callback)
    try {
        ContextCompat.registerReceiver(context, power, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    } catch (error: Exception) {
        manager.unregisterNetworkCallback(callback)
        throw error
    }
    refresh()
    awaitClose {
        manager.unregisterNetworkCallback(callback)
        context.unregisterReceiver(power)
    }
}.distinctUntilChanged()
