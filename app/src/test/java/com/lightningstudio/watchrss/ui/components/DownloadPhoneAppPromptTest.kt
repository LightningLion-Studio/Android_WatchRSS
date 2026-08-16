package com.lightningstudio.watchrss.ui.components

import org.junit.Assert.assertEquals
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
}
