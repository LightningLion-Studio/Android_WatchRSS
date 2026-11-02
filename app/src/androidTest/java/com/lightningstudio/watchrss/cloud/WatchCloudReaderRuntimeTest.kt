package com.lightningstudio.watchrss.cloud

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.data.account.workspace.AccountDataContext
import com.lightningstudio.watchrss.data.cloud.WatchCloudCodec
import com.lightningstudio.watchrss.data.cloud.WatchCloudReaderArchive
import com.lightningstudio.watchrss.data.db.WatchRssDatabase
import com.lightningstudio.watchrss.data.reader.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchCloudReaderRuntimeTest {
    @Test fun readerCapabilityUpgradeRevisitsOldHeadsWithoutResettingSmallStateCursor() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = AccountDataContext(target, "cloud_cursor_${UUID.randomUUID()}", false)
        try {
            context.getSharedPreferences("watchrss_cloud_state", 0).edit()
                .putString("applied_full", "{\"phone\":100}")
                .putString("applied_full_reader_v2", "{\"phone\":100}")
                .putString("applied", "{\"phone\":99}")
                .putString("applied_state", "{\"phone\":100}").commit()
            val store = com.lightningstudio.watchrss.data.cloud.WatchCloudStateStore(context)
            assertEquals(0L, store.applied("phone", full = true))
            assertEquals(100L, store.applied("phone", full = false))
            store.markApplied("phone", 100, full = true)
            assertEquals(100L, com.lightningstudio.watchrss.data.cloud.WatchCloudStateStore(context).applied("phone", full = true))
        } finally {
            context.deleteSharedPreferences("watchrss_cloud_state")
            context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
        }
    }

    @Test fun cloudReaderMergeRefreshesActivePresetHydratesResourcesAndKeepsAccountsSeparate() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = AccountDataContext(target, "cloud_reader_${UUID.randomUUID()}", false)
        val other = AccountDataContext(target, "cloud_reader_other_${UUID.randomUUID()}", false)
        val database = Room.inMemoryDatabaseBuilder(context, WatchRssDatabase::class.java).build()
        val job = SupervisorJob()
        val repository = ReaderPresetRepository(context, database, database.readerPresetDao(), "watch-fixture", CoroutineScope(job + Dispatchers.IO))
        try {
            val old = repository.savePreset(ReaderPreset.lightDefault(name = "Cloud fixture"), applyAfterSave = true)
            withTimeout(10_000) { repository.activePreset.first { it.id == old.id } }
            val fontBytes = "cloud-reader-font-fixture".toByteArray()
            val font = ReaderFontAssetEntity("cloud-font", WatchCloudCodec.sha256(fontBytes), "Fixture", "Fixture", "cloud-font.bin", "font/ttf", fontBytes.size.toLong(), 1, "{}", old.updatedAt + 1, "phone-fixture", false)
            val next = old.copy(
                name = "Cloud updated",
                body = old.body.copy(fontSizeSp = old.body.fontSizeSp + 3f),
                background = old.background.copy(colorArgb = 0xFF123456),
                updatedAt = old.updatedAt + 1,
                modifiedBy = "phone-fixture"
            )
            val snapshot = ReaderPresetSnapshot(listOf(next.toEntity()), listOf(font), emptyList(), emptyList())
            val source = File(context.cacheDir, "source-font.bin").apply { parentFile!!.mkdirs(); writeBytes(fontBytes) }
            val archive = WatchCloudReaderArchive.encode(snapshot, { source })
            assertTrue(WatchCloudReaderArchive.restore(archive, repository, context.cacheDir))
            val refreshed = withTimeout(10_000) {
                repository.activePreset.first {
                    it.name == "Cloud updated" &&
                        it.body.fontSizeSp == next.body.fontSizeSp &&
                        it.background.colorArgb == next.background.colorArgb
                }
            }
            assertEquals(next.body.fontSizeSp, refreshed.body.fontSizeSp)
            assertEquals(next.background.colorArgb, refreshed.background.colorArgb)
            assertArrayEquals(fontBytes, repository.resourceStore.fontFile(font.fileName)!!.readBytes())
            assertFalse(File(other.filesDir, "reader_assets/fonts/${font.fileName}").exists())
            assertTrue(repository.resourceRevision.value > 0)
            assertFalse(WatchCloudReaderArchive.restore(archive, repository, context.cacheDir))
        } finally {
            job.cancelAndJoin(); database.close()
            context.deleteSharedPreferences("reader_preset_state")
            context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
            other.filesDir.deleteRecursively(); other.cacheDir.deleteRecursively()
        }
    }
}
