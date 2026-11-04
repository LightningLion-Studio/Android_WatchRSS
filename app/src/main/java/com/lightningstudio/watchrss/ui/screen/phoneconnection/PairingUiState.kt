package com.lightningstudio.watchrss.ui.screen.phoneconnection

import android.graphics.Bitmap
import com.lightningstudio.watchrss.phoneconnection.provisioning.ProvisioningSession

enum class PairingTransport(val wireName: String) {
    Internet(ProvisioningSession.INTERNET),
    Bluetooth(ProvisioningSession.BLUETOOTH);

    val other: PairingTransport get() = if (this == Internet) Bluetooth else Internet

    companion object {
        fun from(value: String?): PairingTransport = Internet
    }
}

/** The live steps the user can see; cloud sync is optional and may be skipped by the phone. */
enum class PairingStep(val label: String) {
    Scanned("手机已扫码"),
    Confirmed("已确认账号"),
    Saving("保存账号"),
    Cloud("正在完成配对")
}

sealed interface PairingUiState {
    /** Session work is running; leaving the page cancels it. */
    val live: Boolean get() = false

    data class Starting(val transport: PairingTransport) : PairingUiState { override val live get() = true }
    data class NeedsPermission(val denied: Boolean) : PairingUiState
    data class WaitingForScan(
        val transport: PairingTransport,
        val qr: Bitmap,
        val expiresAtElapsed: Long
    ) : PairingUiState { override val live get() = true }
    data class Confirming(val account: String, val sas: String = "", val phoneConfirmed: Boolean = false, val watchConfirmed: Boolean = false) : PairingUiState { override val live get() = true }
    data class Working(val current: PairingStep) : PairingUiState { override val live get() = true }
    /** Only entered after the account (and optionally the cloud key) is durably saved. */
    data class Done(val cloudReady: Boolean) : PairingUiState
    data class Failed(val title: String, val detail: String, val suggestOther: Boolean) : PairingUiState
    data object Cancelled : PairingUiState
    data object Paused : PairingUiState
}

fun pairingDoneTitle(cloudReady: Boolean): String = "手表已绑定"

fun pairingDoneDetail(cloudReady: Boolean): String =
    "账号已登录，可以开始使用"

fun pairingStartingLabel(transport: PairingTransport): String = "正在生成配对码…"

fun pairingScanHint(transport: PairingTransport): String =
    "用腕上RSS手机 App 或微信小程序，点“添加手表”扫码"

/** Remaining whole seconds formatted as m:ss for the QR lifetime hint. */
fun pairingCountdown(remainingMillis: Long): String {
    val seconds = ((remainingMillis + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d 后失效".format(seconds / 60, seconds % 60)
}

/** Maps a transport failure to plain words; never exposes protocol, server or limit details. */
fun pairingFailure(error: Throwable, transport: PairingTransport, qrShown: Boolean): PairingUiState.Failed {
    val reason = (error as? ProvisioningSession.Failure)?.reason
    return when {
        reason == ProvisioningSession.Failure.OFFLINE ->
            PairingUiState.Failed("手表没连上网络", "请检查手表网络后重试", suggestOther = false)
        reason == ProvisioningSession.Failure.EXPIRED ->
            PairingUiState.Failed("配对码已过期", "重新生成一个即可", suggestOther = false)
        reason == ProvisioningSession.Failure.CLOSED || (qrShown && reason == null) ->
            PairingUiState.Failed("连接已断开", "请重新开始，再用手机 App 或微信小程序扫码", suggestOther = false)
        else -> PairingUiState.Failed("配对没有完成", "请重新开始配对", suggestOther = false)
    }
}
