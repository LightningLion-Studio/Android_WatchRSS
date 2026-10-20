package com.lightningstudio.watchrss.ui.screen.cloud

import com.lightningstudio.watchrss.data.account.WatchAccountState
import com.lightningstudio.watchrss.data.cloud.WatchCloudPhase
import com.lightningstudio.watchrss.data.cloud.WatchCloudStatus
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class CloudSyncTone { MUTED, BUSY, OK, ATTENTION, ERROR }

enum class CloudSyncAction { SYNC, OPEN_PHONE_CONNECTION, REQUEST_APPROVAL }

data class CloudSyncButton(val label: String, val action: CloudSyncAction, val enabled: Boolean = true)

data class CloudSyncSpace(val label: String, val fraction: Float)

data class CloudSyncUiState(
    val title: String,
    val detail: String,
    val tone: CloudSyncTone,
    val button: CloudSyncButton? = null,
    val lastSynced: String? = null,
    val space: CloudSyncSpace? = null,
    val secondaryButton: CloudSyncButton? = null
)

private val busyPhases = setOf(
    WatchCloudPhase.REGISTERING,
    WatchCloudPhase.DOWNLOADING,
    WatchCloudPhase.MERGING,
    WatchCloudPhase.UPLOADING
)

/**
 * 只读派生：把账号与云同步服务状态翻译成一屏能读懂的结论。
 * [status] 为 null 表示当前构建未接入云同步服务；不属于当前账号的状态一律视为“尚未同步”。
 */
fun cloudSyncUiState(
    account: WatchAccountState?,
    status: WatchCloudStatus?,
    manualPending: Boolean,
    now: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault()
): CloudSyncUiState {
    val connectPhone = CloudSyncButton("连接手机", CloudSyncAction.OPEN_PHONE_CONNECTION)
    if (status == null) {
        return CloudSyncUiState(
            title = "暂未开放",
            detail = "当前版本还不能使用云同步。本机内容和手机同步照常可用。",
            tone = CloudSyncTone.MUTED
        )
    }
    if (account == null) {
        return CloudSyncUiState(
            title = "未登录账号",
            detail = "在手机端登录腕上RSS，再把账号同步到手表。",
            tone = CloudSyncTone.MUTED,
            button = connectPhone
        )
    }
    fun expired(at: Long) = at in 1..now
    val loginExpired = if (account.watchRefreshToken.isBlank()) {
        expired(account.tokenExpiresAtMillis)
    } else {
        expired(account.refreshTokenExpiresAtMillis)
    }
    if (loginExpired) {
        return CloudSyncUiState(
            title = "登录已过期",
            detail = "请在手机端重新把账号同步到手表。",
            tone = CloudSyncTone.ATTENTION,
            button = connectPhone
        )
    }
    if (account.entitlement.plan != "member" || !account.entitlement.active) {
        return CloudSyncUiState(
            title = "会员可用",
            detail = "云同步需要有效会员。本机内容和手机同步不受影响。",
            tone = CloudSyncTone.MUTED
        )
    }

    val own = status.takeIf { it.accountId == account.userId } ?: WatchCloudStatus()
    val lastSynced = own.lastCompletedAt.takeIf { it > 0 }?.let { "上次同步 ${formatSyncTime(it, now, zone)}" }
    val space = own.takeIf { it.quotaBytes > 0 }?.let {
        CloudSyncSpace(
            label = "已用 ${formatBytes(it.usedBytes)} / ${formatBytes(it.quotaBytes)}",
            fraction = (it.usedBytes.toFloat() / it.quotaBytes).coerceIn(0f, 1f)
        )
    }
    val syncNow = CloudSyncButton("立即同步", CloudSyncAction.SYNC)
    val base = CloudSyncUiState("", "", CloudSyncTone.OK, syncNow, lastSynced, space,
        secondaryButton = CloudSyncButton("重新授权此设备", CloudSyncAction.REQUEST_APPROVAL))

    if (manualPending || own.phase in busyPhases) {
        return base.copy(
            title = "正在同步",
            detail = own.message.takeIf { own.phase in busyPhases } ?: "正在连接云空间",
            tone = CloudSyncTone.BUSY,
            button = CloudSyncButton("同步中…", CloudSyncAction.SYNC, enabled = false)
        )
    }
    return when (own.phase) {
        WatchCloudPhase.COMPLETE -> base.copy(
            title = "已同步",
            detail = when {
                own.message.contains("正文") -> "阅读状态已同步，文章正文会在连上 Wi‑Fi 后同步。"
                else -> "手表与云端已一致。"
            }
        )
        WatchCloudPhase.WAITING_APPROVAL -> base.copy(
            title = "等待手机批准",
            detail = "向已授权的手机或小程序请求批准，再输入上面显示的8位配对码。",
            tone = CloudSyncTone.ATTENTION,
            button = CloudSyncButton("请求旧设备批准", CloudSyncAction.REQUEST_APPROVAL),
            secondaryButton = CloudSyncButton("重新检查", CloudSyncAction.SYNC)
        )
        WatchCloudPhase.ERROR -> base.copy(
            title = "同步失败",
            detail = friendlyCloudError(own.message),
            tone = CloudSyncTone.ERROR,
            button = CloudSyncButton("重试", CloudSyncAction.SYNC)
        )
        else -> base.copy(
            title = "尚未同步",
            detail = "同步后，订阅与阅读记录会保存在会员云空间。",
            tone = CloudSyncTone.MUTED
        )
    }
}

/** 服务端或网络原文可能是 HTTP 码、异常信息；只给出用户能处理的结论。 */
internal fun friendlyCloudError(message: String): String {
    val text = message.lowercase(Locale.ROOT)
    return when {
        "批准" in message || "缺少第" in message || "设备密钥信封无法解密" in message ->
            "需要在手机端重新批准这块手表。"
        "设备加密凭据" in message || "硬件密钥支持" in message ->
            "设备加密服务暂不可用，请更新手表应用后重试。"
        "http 401" in text || "http 403" in text || "令牌" in message || "未绑定" in message ->
            "登录状态失效，请在手机端重新同步账号。"
        "http 413" in text || "http 507" in text || "quota" in text ->
            "会员云空间已满，请在手机端清理后再试。"
        "http 5" in text -> "云服务暂时不可用，请稍后重试。"
        listOf("unable to resolve", "timeout", "timed out", "failed to connect", "network", "connection")
            .any { it in text } -> "网络连接不稳定，请联网后重试。"
        else -> "这次没有同步完成，请稍后重试。"
    }
}

internal fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.coerceAtLeast(0).toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val number = if (unit == 0 || value >= 10 || value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", value)
    }
    return "$number ${units[unit]}"
}

internal fun formatSyncTime(at: Long, now: Long, zone: TimeZone): String {
    fun calendar(millis: Long) = Calendar.getInstance(zone, Locale.ROOT).apply { timeInMillis = millis }
    val then = calendar(at)
    val today = calendar(now)
    val clock = String.format(Locale.ROOT, "%02d:%02d", then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE))
    val sameYear = then.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    val dayDiff = today.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "今天 $clock"
        sameYear && dayDiff == 1 -> "昨天 $clock"
        else -> "${then.get(Calendar.MONTH) + 1}月${then.get(Calendar.DAY_OF_MONTH)}日 $clock"
    }
}
