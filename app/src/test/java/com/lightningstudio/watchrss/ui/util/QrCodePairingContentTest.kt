package com.lightningstudio.watchrss.ui.util

import com.lightningstudio.watchrss.phoneconnection.ConfigPairingProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodePairingContentTest {
    @Test
    fun pairingSecretIsPlacedOnlyInUrlFragment() {
        val token = ConfigPairingProtocol.generatePairingToken()
        val content = buildWatchRssQrContent("192.168.1.20:34567", token)

        assertTrue(content.startsWith("http://192.168.1.20:34567/#watchrss_pair="))
        assertTrue(content.substringAfter('#').contains(token))
        assertFalse(content.substringBefore('#').contains(token))
        assertFalse(content.substringBefore('#').contains('?'))
    }
}
