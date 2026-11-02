package com.lightningstudio.watchrss.data.cloud

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class WatchCloudNetworkPolicy(val label: String) {
    WIFI_BODIES_ANY_STATE("Wi-Fi 传正文，其他网络仅同步小状态"),
    WIFI_AND_CHARGING("仅 Wi-Fi 且充电时同步"),
    ANY_NETWORK("任何网络，可能产生流量费用")
}

internal data class WatchCloudConfiguration(
    val automatic: Boolean = false,
    val network: WatchCloudNetworkPolicy = WatchCloudNetworkPolicy.WIFI_BODIES_ANY_STATE
)

/** Bound to an immutable account context. Nothing uploads before explicit opt-in. */
internal class WatchCloudControls(context: Context) {
    private val prefs = context.getSharedPreferences("watchrss_cloud_controls", Context.MODE_PRIVATE)
    private fun read() = WatchCloudConfiguration(
        prefs.getBoolean("automatic", false),
        runCatching { WatchCloudNetworkPolicy.valueOf(prefs.getString("network", "").orEmpty()) }
            .getOrDefault(WatchCloudNetworkPolicy.WIFI_BODIES_ANY_STATE)
    )
    private val mutableState = MutableStateFlow(read())
    val state = mutableState.asStateFlow()

    @Synchronized fun setAutomatic(enabled: Boolean) {
        check(prefs.edit().putBoolean("automatic", enabled).commit())
        mutableState.value = read()
    }

    @Synchronized fun setNetwork(policy: WatchCloudNetworkPolicy) {
        check(prefs.edit().putString("network", policy.name).commit())
        mutableState.value = read()
    }
}

internal fun cloudTransferAllowed(policy: WatchCloudNetworkPolicy, manual: Boolean, connected: Boolean, unmetered: Boolean, charging: Boolean): Pair<Boolean, Boolean> {
    if (!connected) return false to false
    if (manual) return true to true
    return when (policy) {
        WatchCloudNetworkPolicy.WIFI_BODIES_ANY_STATE -> true to unmetered
        WatchCloudNetworkPolicy.WIFI_AND_CHARGING -> (unmetered && charging).let { it to it }
        WatchCloudNetworkPolicy.ANY_NETWORK -> true to true
    }
}
