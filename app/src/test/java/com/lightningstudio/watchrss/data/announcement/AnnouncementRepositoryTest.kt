package com.lightningstudio.watchrss.data.announcement

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementRepositoryTest {
    @Test
    fun parsesCompleteAnnouncement() {
        val result = AnnouncementRepository.parseAnnouncement(
            JSONObject(
                """{"version":"1.3.0-2","changelog_md":"- Fixed sync","force_update":true,"download_url":"https://example.com/release"}"""
            )
        )

        requireNotNull(result)
        assertEquals("1.3.0-2", result.version)
        assertEquals("- Fixed sync", result.changelogMarkdown)
        assertTrue(result.forceUpdate)
        assertEquals("https://example.com/release", result.downloadUrl)
    }

    @Test
    fun rejectsIncompleteAnnouncement() {
        assertNull(
            AnnouncementRepository.parseAnnouncement(
                JSONObject("""{"version":"1.3.0-2","changelog_md":"notes"}""")
            )
        )
    }

    @Test
    fun comparesReleaseRevisionNumerically() {
        assertTrue(AnnouncementRepository.compareVersions("1.3.0-2", "1.3.0-1") > 0)
        assertTrue(AnnouncementRepository.compareVersions("1.10.0", "1.9.9") > 0)
        assertEquals(0, AnnouncementRepository.compareVersions("1.3", "1.3.0"))
        assertFalse(AnnouncementRepository.compareVersions("1.2.9", "1.3.0") > 0)
    }
}
