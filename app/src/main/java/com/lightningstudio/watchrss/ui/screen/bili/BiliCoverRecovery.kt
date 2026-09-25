package com.lightningstudio.watchrss.ui.screen.bili

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Network identity matters: Wi-Fi -> Wi-Fi must retry even if both report online. */
internal data class BiliCoverNetwork(val identity: String? = null, val online: Boolean? = null)

internal object BiliCoverNetworks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var shared: StateFlow<BiliCoverNetwork>? = null

    @Synchronized
    fun observe(context: Context): StateFlow<BiliCoverNetwork> = shared ?: changes(context.applicationContext)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), snapshot(context.getSystemService(ConnectivityManager::class.java)))
        .also { shared = it }

    private fun snapshot(manager: ConnectivityManager?): BiliCoverNetwork {
        if (manager == null) return BiliCoverNetwork()
        return try {
            val network = manager.activeNetwork
            val capabilities = network?.let(manager::getNetworkCapabilities)
            BiliCoverNetwork(
                identity = network?.toString(),
                online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        } catch (_: SecurityException) {
            BiliCoverNetwork()
        }
    }

    internal fun changes(context: Context): Flow<BiliCoverNetwork> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            trySend(BiliCoverNetwork())
            close()
            return@callbackFlow
        }
        val initial = snapshot(manager)
        var currentIdentity = initial.identity
        trySend(initial)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (currentIdentity != network.toString()) {
                    currentIdentity = network.toString()
                    trySend(BiliCoverNetwork(currentIdentity, false))
                }
            }

            override fun onLost(network: Network) {
                if (currentIdentity == network.toString()) {
                    currentIdentity = null
                    trySend(BiliCoverNetwork(null, false))
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (currentIdentity == network.toString()) {
                    // Use callback data, not synchronous ConnectivityManager reads: those can
                    // still describe the previous network while a handover is being delivered.
                    trySend(BiliCoverNetwork(currentIdentity,
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)))
                }
            }
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
        } catch (_: SecurityException) {
            trySend(BiliCoverNetwork())
            close()
            return@callbackFlow
        }
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

internal fun interface BiliCoverLoader {
    suspend fun load(context: Context, url: String, width: Int): Bitmap?
}

/** Serialize same-URL loads within Bili so cancelled handovers cannot race shared .tmp files. */
private object BiliCoverRequests {
    private class Gate(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val gates = mutableMapOf<String, Gate>()

    suspend fun load(context: Context, url: String, width: Int): Bitmap? {
        val gate = synchronized(gates) { gates.getOrPut(url) { Gate() }.also { it.users++ } }
        return try {
            gate.mutex.withLock { RssImageLoader.loadBitmap(context, url, width) }
        } finally {
            synchronized(gates) {
                gate.users--
                if (gate.users == 0) gates.remove(url)
            }
        }
    }
}

// Test seams stay in the Bili module; production keeps the existing shared image cache.
internal val LocalBiliCoverLoader = staticCompositionLocalOf<BiliCoverLoader> {
    BiliCoverLoader { context, url, width -> BiliCoverRequests.load(context, url, width) }
}
internal val LocalBiliCoverNetwork = staticCompositionLocalOf<Flow<BiliCoverNetwork>?> { null }

internal enum class BiliCoverStatus { Missing, Deferred, Loading, Failed, Offline, Ready }

internal data class BiliCoverState(
    val bitmap: Bitmap?,
    val status: BiliCoverStatus,
    val retry: () -> Unit
)

@Composable
internal fun rememberBiliCover(url: String?, maxWidthPx: Int, enabled: Boolean = true): BiliCoverState {
    val context = LocalContext.current.applicationContext
    val loader = LocalBiliCoverLoader.current
    val networkFlow = LocalBiliCoverNetwork.current ?: remember(context) { BiliCoverNetworks.observe(context) }
    val network by networkFlow.collectAsStateWithLifecycle(
        initialValue = (networkFlow as? StateFlow<BiliCoverNetwork>)?.value ?: BiliCoverNetwork()
    )
    var bitmap by remember(url, maxWidthPx) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(url, maxWidthPx) { mutableStateOf(false) }
    var attempted by remember(url, maxWidthPx) { mutableStateOf(false) }
    var retry by remember(url, maxWidthPx) { mutableIntStateOf(0) }

    LaunchedEffect(url, maxWidthPx, network, retry, enabled) {
        if (url.isNullOrBlank() || bitmap != null || !enabled) return@LaunchedEffect
        // Try the shared disk/memory cache at least once even offline. Later automatic
        // retries only occur on recovery or a new default network, never in a polling loop.
        if (attempted && network.online == false && retry == 0) return@LaunchedEffect
        loading = true
        attempted = true
        try {
            bitmap = loader.load(context, url, maxWidthPx.coerceAtLeast(1))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            bitmap = null
        } finally {
            loading = false
        }
    }
    val status = when {
        url.isNullOrBlank() -> BiliCoverStatus.Missing
        bitmap != null -> BiliCoverStatus.Ready
        !attempted && !enabled -> BiliCoverStatus.Deferred
        loading -> BiliCoverStatus.Loading
        network.online == false -> BiliCoverStatus.Offline
        else -> BiliCoverStatus.Failed
    }
    return BiliCoverState(bitmap, status) { if (!loading && enabled) retry++ }
}

@Composable
internal fun BiliCoverFeedback(state: BiliCoverState, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val label = when (state.status) {
        BiliCoverStatus.Ready -> return
        BiliCoverStatus.Missing -> "暂无封面"
        BiliCoverStatus.Deferred -> "停止滚动后加载封面"
        BiliCoverStatus.Loading -> "封面加载中"
        BiliCoverStatus.Offline -> "网络不可用，点此重试"
        BiliCoverStatus.Failed -> "封面加载失败，点此重试"
    }
    val canRetry = enabled && (state.status == BiliCoverStatus.Failed || state.status == BiliCoverStatus.Offline)
    Text(
        text = label,
        color = Color.White,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(Color(0xFF303030), RoundedCornerShape(4.dp))
            .then(if (canRetry) Modifier.clickable(role = Role.Button, onClick = state.retry) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}
