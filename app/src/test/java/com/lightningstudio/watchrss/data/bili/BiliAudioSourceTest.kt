package com.lightningstudio.watchrss.data.bili

import com.lightningstudio.watchrss.sdk.bili.*
import org.junit.Assert.*
import org.junit.Test

class BiliAudioSourceTest {
    @Test fun videoOrMuxedOnlySourcesNeverBecomeAudioMode() {
        val video = BiliDashStream(baseUrl = "https://cdn.example/video.mp4")
        assertNull(selectBiliAudioTrack(BiliPlayUrl(dash = BiliDash(video = listOf(video)), durl = listOf(BiliDurl(url = video.baseUrl)))))
    }
    @Test fun selectsTheLowestKnownBandwidthAudioAndUsesValidBackups() {
        val tracks = listOf(
            BiliDashStream(baseUrl = "https://cdn.example/high.m4s", bandwidth = 192000),
            BiliDashStream(baseUrl = "file:///invalid", backupUrl = listOf("//cdn.example/low.m4s"), bandwidth = 64000),
            BiliDashStream(baseUrl = "https://cdn.example/unknown.m4s")
        )
        assertEquals("https://cdn.example/low.m4s" to 64000, selectBiliAudioTrack(BiliPlayUrl(dash = BiliDash(audio = tracks))))
    }
    @Test fun rejectsNonNetworkAudioAddressesAndEmbeddedCredentials() {
        val tracks = listOf("content://local/audio", "https://user:secret@cdn.example/audio", "http://cdn.example/audio", "broken").map { BiliDashStream(baseUrl = it) }
        assertNull(selectBiliAudioTrack(BiliPlayUrl(dash = BiliDash(audio = tracks))))
    }
}
