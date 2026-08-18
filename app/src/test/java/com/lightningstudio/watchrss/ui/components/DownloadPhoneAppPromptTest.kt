package com.lightningstudio.watchrss.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPhoneAppPromptTest {

    @Test
    fun `prompt combines operation label`() {
        assertEquals(
            "下载手机端App以完成同步收藏",
            phoneAppDownloadPrompt("同步收藏")
        )
    }

    @Test
    fun `memo prompt explains phone editing and sync`() {
        assertEquals(
            "下载手机端app以在手机上编辑和同步内容",
            phoneAppDownloadPrompt("同步备忘录")
        )
    }

    @Test
    fun `prompt survives blank operation`() {
        assertEquals(
            "下载手机端App以完成",
            phoneAppDownloadPrompt("")
        )
    }

    @Test
    fun `download url points at the oppo store page`() {
        assertEquals(
            "https://app.cdo.oppomobile.com/home/detail?app_id=37262051",
            PHONE_APP_DOWNLOAD_URL
        )
    }

    @Test
    fun `qr size is nonzero and square-ready across round watch sizes`() {
        val roundScreenSizes = listOf(248.dp, 320.dp, 360.dp, 466.dp)

        roundScreenSizes.forEach { screenSize ->
            val qrSize = downloadPhoneAppQrSize(screenSize)

            assertTrue("QR must be visible on $screenSize", qrSize > 0.dp)
            assertTrue("QR must fit inside $screenSize", qrSize <= screenSize)
        }
    }

    @Test
    fun `qr size scales predictably without exceeding baseline`() {
        assertEquals(88f, downloadPhoneAppQrSize(233.dp).value, 0.001f)
        assertEquals(176f, downloadPhoneAppQrSize(466.dp).value, 0.001f)
        assertEquals(176f, downloadPhoneAppQrSize(512.dp).value, 0.001f)
    }

    @Test
    fun `dialog properties create a modal full-window surface`() {
        assertTrue(DOWNLOAD_PHONE_APP_DIALOG_PROPERTIES.dismissOnBackPress)
        assertTrue(!DOWNLOAD_PHONE_APP_DIALOG_PROPERTIES.dismissOnClickOutside)
        assertTrue(!DOWNLOAD_PHONE_APP_DIALOG_PROPERTIES.usePlatformDefaultWidth)
        assertTrue(!DOWNLOAD_PHONE_APP_DIALOG_PROPERTIES.decorFitsSystemWindows)
    }
}
