package com.lightningstudio.watchrss.ui.screen.phoneconnection

import com.lightningstudio.watchrss.phoneconnection.provisioning.ProvisioningSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingUiStateTest {
    @Test
    fun offlineInternetFailure_offersNetworkRetry() {
        val failed = pairingFailure(ProvisioningSession.Failure(ProvisioningSession.Failure.OFFLINE, "x"), PairingTransport.Internet, qrShown = false)
        assertFalse(failed.suggestOther)
        assertEquals("请检查手表网络后重试", failed.detail)
    }

    @Test
    fun droppedBluetoothAfterQr_readsAsDisconnectNotRadioProblem() {
        val failed = pairingFailure(IllegalStateException("配对已结束"), PairingTransport.Bluetooth, qrShown = true)
        assertEquals("连接已断开", failed.title)
        assertFalse(failed.suggestOther)
    }

    @Test
    fun outcomes_showAccountBindingWithoutCloudPromotion() {
        for (cloudReady in listOf(true, false)) {
            assertEquals("手表已绑定", pairingDoneTitle(cloudReady))
            assertEquals("账号已登录，可以开始使用", pairingDoneDetail(cloudReady))
        }
    }

    @Test
    fun countdown_roundsUpToWholeSeconds() {
        assertEquals("5:00 后失效", pairingCountdown(300_000))
        assertEquals("0:01 后失效", pairingCountdown(1))
        assertEquals("0:00 后失效", pairingCountdown(-5))
    }

    @Test
    fun transportExtra_defaultsToInternet() {
        assertEquals(PairingTransport.Internet, PairingTransport.from(null))
        assertEquals(PairingTransport.Internet, PairingTransport.from("bluetooth"))
    }
}
