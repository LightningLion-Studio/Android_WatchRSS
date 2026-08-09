package com.lightningstudio.watchrss.ui.screen.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePreviewScreenTest {
    @Test
    fun localImageFailureExplainsThatTheAssetNeedsSync() {
        assertEquals(
            "图片未同步\n请在手机端重新同步",
            imagePreviewFailureMessage("/data/user/0/example/files/notes/assets/missing.jpg")
        )
    }

    @Test
    fun remoteImageFailureUsesGenericRetryMessage() {
        assertEquals(
            "图片加载失败\n请稍后重试",
            imagePreviewFailureMessage("https://example.com/image.jpg")
        )
    }
}
