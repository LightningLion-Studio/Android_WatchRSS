package com.lightningstudio.watchrss.phoneconnection.ip

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap

internal class WatchIpNsdDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)

    suspend fun discover(expectedPort: Int): List<IpEndpointCandidate> {
        val found = ConcurrentHashMap<String, IpEndpointCandidate>()
        val started = CompletableDeferred<Unit>()
        val multicastLock = runCatching {
            wifiManager.createMulticastLock("watchrss-ip-discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                started.complete(Unit)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceName.startsWith("WatchRSS-")) return
                runCatching {
                    nsdManager.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                if (resolved.port != expectedPort) return
                                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    resolved.hostAddresses
                                } else {
                                    @Suppress("DEPRECATION")
                                    listOfNotNull(resolved.host)
                                }
                                addresses.filterIsInstance<Inet4Address>().forEach { address ->
                                    val host = address.hostAddress ?: return@forEach
                                    found[host] = IpEndpointCandidate(
                                        endpointId = "mdns-$host",
                                        address = host,
                                        family = "ipv4",
                                        transportKind = IpTransportKind.WIFI_LAN,
                                        priority = IpTransportKind.WIFI_LAN.priority
                                    )
                                }
                            }
                        }
                    )
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                started.complete(Unit)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        return try {
            runCatching {
                nsdManager.discoverServices(
                    IpSyncProtocol.SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener
                )
            }.onFailure { started.complete(Unit) }
            started.await()
            delay(DISCOVERY_WINDOW_MS)
            found.values.toList()
        } finally {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
            runCatching { multicastLock?.release() }
        }
    }

    companion object {
        private const val DISCOVERY_WINDOW_MS = 1_500L
    }
}
