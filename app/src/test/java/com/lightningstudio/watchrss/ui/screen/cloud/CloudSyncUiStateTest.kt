package com.lightningstudio.watchrss.ui.screen.cloud

import com.lightningstudio.watchrss.data.account.WatchAccountState
import com.lightningstudio.watchrss.data.account.WatchEntitlementSnapshot
import com.lightningstudio.watchrss.data.cloud.WatchCloudPhase
import com.lightningstudio.watchrss.data.cloud.WatchCloudStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class CloudSyncUiStateTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_760_000_000_000L

    private fun account(
        userId: String = "user-a",
        plan: String = "member",
        active: Boolean = true,
        refreshExpiresAt: Long = now + 86_400_000L
    ) = WatchAccountState(
        userId = userId,
        phoneMasked = "138****0000",
        installId = "install",
        watchDeviceToken = "token",
        tokenExpiresAtMillis = now + 60_000L,
        watchRefreshToken = "refresh",
        refreshTokenExpiresAtMillis = refreshExpiresAt,
        backendBaseUrl = "https://example.invalid",
        posthogHost = "",
        posthogProjectApiKey = "",
        entitlement = WatchEntitlementSnapshot(plan = plan, active = active)
    )

    private fun state(account: WatchAccountState?, status: WatchCloudStatus?, pending: Boolean = false) =
        cloudSyncUiState(account, status, pending, now, utc)

    @Test
    fun `build without cloud service offers no action`() {
        val ui = state(account(), status = null)
        assertEquals("暂未开放", ui.title)
        assertNull(ui.button)
    }

    @Test
    fun `unbound and expired accounts lead to phone connection, never sync`() {
        assertEquals(CloudSyncAction.OPEN_PHONE_CONNECTION, state(null, WatchCloudStatus()).button?.action)
        val expired = state(account(refreshExpiresAt = now - 1), WatchCloudStatus())
        assertEquals("登录已过期", expired.title)
        assertEquals(CloudSyncAction.OPEN_PHONE_CONNECTION, expired.button?.action)
    }

    @Test
    fun `non member sees no sync or purchase action`() {
        assertNull(state(account(plan = "free"), WatchCloudStatus()).button)
        assertNull(state(account(active = false), WatchCloudStatus()).button)
    }

    @Test
    fun `status from another account is not shown`() {
        val foreign = WatchCloudStatus(
            phase = WatchCloudPhase.COMPLETE,
            usedBytes = 1L shl 20,
            quotaBytes = 1L shl 30,
            lastCompletedAt = now - 1000,
            accountId = "user-a"
        )
        val ui = state(account(userId = "user-b"), foreign)
        assertEquals("尚未同步", ui.title)
        assertNull(ui.space)
        assertNull(ui.lastSynced)
    }

    @Test
    fun `pending manual sync disables the action immediately`() {
        val ui = state(account(), WatchCloudStatus(accountId = "user-a"), pending = true)
        assertEquals("正在同步", ui.title)
        assertFalse(ui.button!!.enabled)
    }

    @Test
    fun `running background phase also disables the action`() {
        val ui = state(account(), WatchCloudStatus(WatchCloudPhase.UPLOADING, "正在上传手表状态", accountId = "user-a"))
        assertEquals("正在上传手表状态", ui.detail)
        assertFalse(ui.button!!.enabled)
    }

    @Test
    fun `error offers retry with a plain message instead of raw http text`() {
        val ui = state(account(), WatchCloudStatus(WatchCloudPhase.ERROR, "HTTP 503: upstream", accountId = "user-a"))
        assertEquals("重试", ui.button?.label)
        assertTrue(ui.button!!.enabled)
        assertFalse(ui.detail.contains("HTTP"))
    }

    @Test
    fun `waiting approval keeps a recheck action`() {
        val ui = state(account(), WatchCloudStatus(WatchCloudPhase.WAITING_APPROVAL, accountId = "user-a"))
        assertEquals("等待手机批准", ui.title)
        assertEquals(CloudSyncAction.REQUEST_APPROVAL, ui.button?.action)
        assertEquals(CloudSyncAction.SYNC, ui.secondaryButton?.action)
    }

    @Test
    fun `complete shows real space and last success time`() {
        val ui = state(
            account(),
            WatchCloudStatus(
                phase = WatchCloudPhase.COMPLETE,
                message = "云同步完成",
                usedBytes = 256L shl 20,
                quotaBytes = 1L shl 30,
                lastCompletedAt = now,
                accountId = "user-a"
            )
        )
        assertEquals("已同步", ui.title)
        assertEquals("已用 256 MB / 1 GB", ui.space?.label)
        assertEquals(0.25f, ui.space!!.fraction, 0.001f)
        assertTrue(ui.lastSynced!!.startsWith("上次同步 今天"))
    }

    @Test
    fun `missing quota hides the space line`() {
        val ui = state(account(), WatchCloudStatus(WatchCloudPhase.COMPLETE, accountId = "user-a"))
        assertNull(ui.space)
        assertNull(ui.lastSynced)
    }
    @Test fun `hardware key capability errors do not ask for repeated approval`() {
        assertFalse(friendlyCloudError("云中继需要 Android 12 及以上硬件密钥支持").contains("批准"))
        assertTrue(friendlyCloudError("缺少第2版账号密钥，请在手机端重新批准手表").contains("批准"))
    }

    @Test fun `authorized device can request new code without removing existing key`() {
        val ui = state(account(), WatchCloudStatus(WatchCloudPhase.COMPLETE, accountId = "user-a"))
        assertEquals("重新授权此设备", ui.secondaryButton?.label)
        assertEquals(CloudSyncAction.REQUEST_APPROVAL, ui.secondaryButton?.action)
    }

}
