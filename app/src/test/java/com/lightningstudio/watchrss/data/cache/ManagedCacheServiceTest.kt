package com.lightningstudio.watchrss.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.lightningstudio.watchrss.data.settings.MB_BYTES
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class ManagedCacheServiceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun trimToLimit_evictsInPriorityOrder_andKeepsProtectedBuckets() = runBlocking {
        val env = createEnvironment("eviction.preferences_pb")
        try {
            env.settingsRepository.setCacheLimitBytes(512L * MB_BYTES)

            val offlineA = createSizedFile(
                File(env.roots.rssOfflineDir, "101/image_0.jpg"),
                sizeBytes = mb(120),
                lastModified = 10L
            )
            val offlineB = createSizedFile(
                File(env.roots.rssOfflineDir, "102/video_0.mp4"),
                sizeBytes = mb(120),
                lastModified = 20L
            )

            val douyinOld = createSizedFile(
                File(env.roots.douyinPreloadDir, "old.mp4"),
                sizeBytes = mb(110),
                lastModified = 30L
            )
            val douyinMid = createSizedFile(
                File(env.roots.douyinPreloadDir, "mid.mp4"),
                sizeBytes = mb(110),
                lastModified = 40L
            )
            val douyinNew = createSizedFile(
                File(env.roots.douyinPreloadDir, "new.mp4"),
                sizeBytes = mb(110),
                lastModified = 50L
            )

            val imageA = createSizedFile(
                File(env.roots.rssImagesDir, "a.jpg"),
                sizeBytes = mb(80),
                lastModified = 60L
            )
            val imageB = createSizedFile(
                File(env.roots.rssImagesDir, "b.jpg"),
                sizeBytes = mb(80),
                lastModified = 70L
            )

            val biliA = createSizedFile(
                File(env.roots.biliPreviewDir, "BV1abc_100_q32.mp4"),
                sizeBytes = mb(70),
                lastModified = 80L
            )
            val biliB = createSizedFile(
                File(env.roots.biliPreviewDir, "BV1abc_200_q32.mp4"),
                sizeBytes = mb(70),
                lastModified = 90L
            )

            File(env.roots.rssImagesDir, "dangling.tmp").writeText("tmp")
            File(env.roots.rssImagesDir, "empty.jpg").createNewFile()

            env.service.trimToLimit(CacheTrimReason.MANUAL)

            assertTrue(offlineA.exists())
            assertTrue(offlineB.exists())
            assertFalse(douyinOld.exists())
            assertTrue(douyinMid.exists())
            assertTrue(douyinNew.exists())
            assertFalse(imageA.exists())
            assertFalse(imageB.exists())
            assertFalse(biliA.exists())
            assertFalse(biliB.exists())
            assertFalse(File(env.roots.rssImagesDir, "dangling.tmp").exists())
            assertFalse(File(env.roots.rssImagesDir, "empty.jpg").exists())
            assertEquals(mb(460), env.service.observeUsageBytes().value)
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun clearBiliPreviewsForVideo_removesAllPreviewVariantsForSameVideo() = runBlocking {
        val env = createEnvironment("bili-clear.preferences_pb")
        try {
            val bvFirst = createSizedFile(
                File(env.roots.biliPreviewDir, "BV1test_100_q32.mp4"),
                sizeBytes = mb(10),
                lastModified = 10L
            )
            val bvSecond = createSizedFile(
                File(env.roots.biliPreviewDir, "BV1test_200_q32.mp4"),
                sizeBytes = mb(10),
                lastModified = 20L
            )
            val bvOther = createSizedFile(
                File(env.roots.biliPreviewDir, "BV9keep_300_q32.mp4"),
                sizeBytes = mb(10),
                lastModified = 30L
            )
            val avPreview = createSizedFile(
                File(env.roots.biliPreviewDir, "av77_400_q32.mp4"),
                sizeBytes = mb(10),
                lastModified = 40L
            )

            env.service.clearBiliPreviewsForVideo(aid = null, bvid = "BV1test")

            assertFalse(bvFirst.exists())
            assertFalse(bvSecond.exists())
            assertTrue(bvOther.exists())
            assertTrue(avPreview.exists())

            env.service.clearBiliPreviewsForVideo(aid = 77L, bvid = null)

            assertFalse(avPreview.exists())
            assertTrue(bvOther.exists())
            assertEquals(mb(10), env.service.observeUsageBytes().value)
        } finally {
            env.scope.cancel()
        }
    }

    private fun createEnvironment(fileName: String): TestEnvironment {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile(fileName) }
        )
        val settingsRepository = SettingsRepository(dataStore)
        val roots = ManagedCacheRoots(
            douyinPreloadDir = tempFolder.newFolder("douyin-preload"),
            rssImagesDir = tempFolder.newFolder("rss-images"),
            biliPreviewDir = tempFolder.newFolder("bili-preview"),
            rssOfflineDir = tempFolder.newFolder("rss-offline")
        )
        val service = ManagedCacheService(
            roots = roots,
            settingsRepository = settingsRepository,
            appScope = scope
        )
        return TestEnvironment(
            service = service,
            roots = roots,
            settingsRepository = settingsRepository,
            dataStore = dataStore,
            scope = scope
        )
    }

    private fun createSizedFile(file: File, sizeBytes: Long, lastModified: Long): File {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(sizeBytes)
        }
        file.setLastModified(lastModified)
        return file
    }

    private fun mb(value: Long): Long = value * MB_BYTES

    private data class TestEnvironment(
        val service: ManagedCacheService,
        val roots: ManagedCacheRoots,
        val settingsRepository: SettingsRepository,
        val dataStore: DataStore<Preferences>,
        val scope: CoroutineScope
    )
}
