package com.lightningstudio.watchrss.phoneconnection.ip

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

internal class WatchIpRouteResolver(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    @Suppress("DEPRECATION")
    fun networkFor(address: String): Network? {
        val target = runCatching { InetAddress.getByName(address) as? Inet4Address }.getOrNull()
            ?: return null
        return connectivityManager.allNetworks.firstOrNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@firstOrNull false
            if (
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) return@firstOrNull false
            connectivityManager.getLinkProperties(network)?.linkAddresses.orEmpty().any { link ->
                contains(link, target)
            }
        }
    }

    internal fun contains(linkAddress: LinkAddress, target: Inet4Address): Boolean {
        val local = linkAddress.address as? Inet4Address ?: return false
        val prefix = linkAddress.prefixLength.coerceIn(0, 32)
        val localBytes = local.address
        val targetBytes = target.address
        val fullBytes = prefix / 8
        val remainingBits = prefix % 8
        for (index in 0 until fullBytes) {
            if (localBytes[index] != targetBytes[index]) return false
        }
        if (remainingBits == 0) return true
        val mask = (0xff shl (8 - remainingBits)) and 0xff
        return (localBytes[fullBytes].toInt() and mask) ==
            (targetBytes[fullBytes].toInt() and mask)
    }
}
