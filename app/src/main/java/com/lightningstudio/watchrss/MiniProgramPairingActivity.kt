package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.data.cloud.WatchCloudKeyManager
import com.lightningstudio.watchrss.data.telemetry.WatchInstallationIdentity
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.phoneconnection.bluetooth.AccountSyncPayload
import com.lightningstudio.watchrss.phoneconnection.provisioning.OnlineProvisioningSession
import com.lightningstudio.watchrss.phoneconnection.provisioning.PairingConsent
import com.lightningstudio.watchrss.phoneconnection.provisioning.ProvisioningSession
import com.lightningstudio.watchrss.ui.screen.phoneconnection.MiniProgramPairingScreen
import com.lightningstudio.watchrss.ui.screen.phoneconnection.PairingActions
import com.lightningstudio.watchrss.ui.screen.phoneconnection.PairingStep
import com.lightningstudio.watchrss.ui.screen.phoneconnection.PairingTransport
import com.lightningstudio.watchrss.ui.screen.phoneconnection.PairingUiState
import com.lightningstudio.watchrss.ui.screen.phoneconnection.pairingFailure
import com.lightningstudio.watchrss.ui.screen.phoneconnection.pairingQrBitmap
import com.lightningstudio.watchrss.ui.screen.phoneconnection.pairingQrPixels
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Arrays

/**
 * Explicit foreground pairing only. Leaving this screen closes its session and authorization.
 * Pairing uses the encrypted internet channel.
 */
class MiniProgramPairingActivity : BaseWatchActivity() {
    private var transport by mutableStateOf(PairingTransport.Internet)
    private var state by mutableStateOf<PairingUiState>(PairingUiState.Paused)
    private var confirmation: CompletableDeferred<Boolean>? = null
    private var session: ProvisioningSession? = null
    private var job: Job? = null
    /** Bumped on every start/stop; a run only touches UI and ownership while it is current. */
    @Volatile private var generation = 0
    @Volatile private var bindingSaved = false
    @Volatile private var cloudSaved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        transport = PairingTransport.Internet
        val actions = PairingActions(
            start = ::requestStart,
            confirm = { confirmation?.complete(true) },
            decline = { confirmation?.complete(false) },
            cancel = ::cancel,
            close = ::finish
        )
        setContent { WatchRSSTheme { MiniProgramPairingScreen(state, transport, actions) } }
        // Entering this page is the explicit request; recreation waits for the user instead.
        if (savedInstanceState == null) requestStart()
    }

    private fun requestStart() = begin()

    private fun cancel() {
        when {
            bindingSaved -> stop(PairingUiState.Done(cloudSaved))
            state is PairingUiState.Starting || state is PairingUiState.WaitingForScan -> { stop(null); finish() }
            else -> stop(PairingUiState.Cancelled)
        }
    }

    private fun begin() {
        stop(null)
        val epoch = ++generation
        val mode = transport
        bindingSaved = false
        cloudSaved = false
        state = PairingUiState.Starting(mode)
        val metrics = resources.displayMetrics
        val qrPixels = pairingQrPixels(metrics.density, minOf(metrics.widthPixels, metrics.heightPixels) / metrics.density)
        job = lifecycleScope.launch {
            var owned: ProvisioningSession? = null
            var qrShown = false
            fun current() = generation == epoch
            suspend fun show(next: PairingUiState) = withContext(Dispatchers.Main) { if (current()) state = next }
            try {
                withContext(Dispatchers.IO) {
                    val context = this@MiniProgramPairingActivity
                    val deviceId = WatchDeviceIdentity(context).deviceId
                    val installId = WatchInstallationIdentity(context).installId
                    val createdAt = SystemClock.elapsedRealtime()
                    val channel: ProvisioningSession =
                        OnlineProvisioningSession(deviceId, installId)
                    owned = channel
                    withContext(Dispatchers.Main) { if (current()) session = channel else channel.close() }
                    channel.start()
                    val bitmap = pairingQrBitmap(channel.qr(), qrPixels) ?: error("qr")
                    qrShown = true
                    show(PairingUiState.WaitingForScan(mode, bitmap, createdAt + SESSION_LIFETIME_MS))
                    runSetup(channel, deviceId, installId, ::current) { show(it) }
                }
            } catch (error: Exception) {
                if (error !is CancellationException && current()) {
                    state = if (bindingSaved) PairingUiState.Done(cloudSaved) else pairingFailure(error, mode, qrShown)
                }
            } finally {
                owned?.close()
                if (bindingSaved && owned != null) (application as WatchRssApplication).finishAccountSyncUi(owned!!.sessionId())
                if (session === owned) session = null
                if (current()) confirmation = null
            }
        }
    }

    /** Business setup over the encrypted channel; every receipt is sent only after its durable save. */
    private suspend fun kotlinx.coroutines.CoroutineScope.runSetup(
        channel: ProvisioningSession,
        deviceId: String,
        installId: String,
        current: () -> Boolean,
        show: suspend (PairingUiState) -> Unit
    ) {
        val app = application as WatchRssApplication
        val keys = WatchCloudKeyManager(this@MiniProgramPairingActivity)
        var userId: String? = null
        var saved = false
        var keySaved = false
        var consent: PairingConsent? = null
        var pendingApproval: CompletableDeferred<Boolean>? = null
        var pairingLabel = "当前账号"
        while (isActive) {
            val request = channel.receive()
            val sequence = request.getInt("_sequence")
            try {
                val response = when (request.getString("action")) {
                    "prepare" -> {
                        val requested = request.getString("userId")
                        check(java.util.UUID.fromString(requested).toString() == requested)
                        check(userId == null || userId == requested)
                        val extended = request.optInt("pairingUxVersion") == 1
                        val existing = app.accountStore.read()
                        val conflict = existing != null && existing.userId != requested
                        if (conflict && !extended) throw PairingStop(
                            PairingUiState.Failed("手表已登录其他账号", "请先在手表上退出原账号，再重新配对", false),
                            "手表已绑定其他账号，请先在手表退出原账号")
                        val publicKey = keys.publicKeySpki(requested, deviceId)
                        if (pendingApproval == null) {
                            val approval = CompletableDeferred<Boolean>()
                            pendingApproval = approval
                            pairingLabel = request.optString("phoneMasked", "当前账号").take(24)
                            val sas = channel.sas(requested)
                            if (extended) consent = PairingConsent(requested, deviceId, installId, sas, conflict, SystemClock::elapsedRealtime)
                            withContext(Dispatchers.Main) { if (current()) confirmation = approval else approval.complete(false) }
                            show(PairingUiState.Confirming(pairingLabel, sas))
                        }
                        userId = requested
                        if (extended) requireNotNull(consent).receipt(publicKey)
                        else {
                            val approved = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { requireNotNull(pendingApproval).await() }
                            if (approved != true) throw PairingStop(PairingUiState.Cancelled, "手表未确认绑定")
                            show(PairingUiState.Working(PairingStep.Saving))
                            JSONObject().put("success", true).put("deviceId", deviceId).put("installId", installId)
                                .put("publicKeySpki", publicKey).put("userId", requested)
                        }
                    }
                    "pairingStatus" -> {
                        val status = requireNotNull(consent)
                        if (pendingApproval?.isCompleted == true && status.watch == "pending") status.watchDecision(pendingApproval!!.await())
                        status.update(request)
                        show(PairingUiState.Confirming(pairingLabel, status.sas, status.phone == "confirmed", status.watch == "confirmed"))
                        status.receipt(keys.publicKeySpki(requireNotNull(userId), deviceId)).put("action", "pairingStatus")
                    }
                    "configure" -> {
                        check(userId != null && !saved)
                        check(consent == null || consent!!.bothConfirmed) { "pairing_confirmation_required" }
                        val accountState = AccountSyncPayload.parseRequest(request)
                        check(accountState.userId == userId && accountState.installId == installId && accountState.backendBaseUrl == "https://sly-data-plane.watchrss.cn")
                        check(accountState.tokenExpiresAtMillis > System.currentTimeMillis() && accountState.watchRefreshToken.isNotBlank())
                        val existing = app.accountStore.read()
                        check(existing == null || existing.userId == userId)
                        app.acceptAccountSync(accountState, channel.sessionId())
                        saved = true
                        // Re-pairing the same account retains its durable cloud authorization.
                        keySaved = runCatching {
                            keys.accountKey(accountState.userId)?.let { key ->
                                try { key.size == 32 } finally { Arrays.fill(key, 0.toByte()) }
                            } ?: false
                        }.getOrDefault(false)
                        withContext(Dispatchers.Main) {
                            if (current()) { bindingSaved = true; cloudSaved = keySaved }
                        }
                        show(PairingUiState.Working(PairingStep.Cloud))
                        // The phone requires the watch's own durable account registration before granting keys.
                        try { app.cloudSyncService.registerPairedAccount() }
                        catch (error: Exception) { if (error is CancellationException) throw error }
                        JSONObject().put("success", true).put("bound", true).put("deviceId", deviceId).put("userId", userId).put("cloudReady", keySaved)
                    }
                    "key" -> {
                        check(saved && request.getString("userId") == userId)
                        if (request.has("payload")) {
                            val service = app.cloudSyncService
                            service.acceptPairingPayload(userId!!, request.getString("libraryEpoch"), request.getString("payload"))
                        } else {
                            // Current v2 servers require a commitment-bound multi-version payload.
                            throw IllegalStateException("请更新手机 App 或小程序后重新授权云同步")
                        }
                        keySaved = true
                        withContext(Dispatchers.Main) { if (current()) cloudSaved = true }
                        JSONObject().put("success", true).put("cloudReady", true).put("deviceId", deviceId)
                            .put("keyVersion", keys.currentKeyVersion(userId!!))
                    }
                    "finish" -> {
                        check(saved && (!request.optBoolean("cloudReady") || keySaved))
                        JSONObject().put("success", true).put("deviceId", deviceId).put("cloudReady", keySaved)
                    }
                    else -> error("unsupported")
                }
                channel.respond(sequence, response)
                if (consent?.rejected == true) { show(PairingUiState.Cancelled); return }
                if (request.getString("action") == "finish") {
                    show(PairingUiState.Done(keySaved))
                    // Let the phone collect the final receipt, then end the session ourselves.
                    launch { delay(FINISH_GRACE_MS); channel.close() }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val stop = error as? PairingStop
                channel.respond(
                    sequence,
                    JSONObject().put("success", false).put("message", stop?.phoneMessage ?: "手表配置未完成，请检查账号状态后重新配对")
                )
                when {
                    stop != null -> { show(if (bindingSaved) PairingUiState.Done(cloudSaved) else stop.ui); return }
                    bindingSaved -> show(PairingUiState.Done(keySaved))
                    else -> show(PairingUiState.Failed("配对没有完成", "请重新开始配对", suggestOther = false))
                }
            }
        }
    }

    /** Ends the owned session, rejecting any pending confirmation. [next] replaces the page when given. */
    private fun stop(next: PairingUiState?) {
        generation++
        confirmation?.complete(false)
        confirmation = null
        job?.cancel()
        job = null
        session?.close()
        session = null
        if (next != null) state = next
    }

    override fun onStop() {
        when {
            bindingSaved -> stop(PairingUiState.Done(cloudSaved))
            state.live -> stop(PairingUiState.Paused)
            else -> stop(null)
        }
        super.onStop()
    }

    private class PairingStop(val ui: PairingUiState, val phoneMessage: String) : Exception(phoneMessage)

    companion object {
        private const val EXTRA_TRANSPORT = "transport"
        private const val SESSION_LIFETIME_MS = 300_000L
        private const val CONFIRM_TIMEOUT_MS = 90_000L
        private const val FINISH_GRACE_MS = 20_000L

        fun createIntent(context: Context, transport: PairingTransport = PairingTransport.Internet): Intent =
            Intent(context, MiniProgramPairingActivity::class.java).putExtra(EXTRA_TRANSPORT, transport.wireName)
    }
}
