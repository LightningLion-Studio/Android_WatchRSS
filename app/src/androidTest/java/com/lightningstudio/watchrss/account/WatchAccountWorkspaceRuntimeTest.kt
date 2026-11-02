package com.lightningstudio.watchrss.account

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.DefaultAppContainer
import com.lightningstudio.watchrss.data.account.WatchAccountState
import com.lightningstudio.watchrss.data.account.WatchAccountStore
import com.lightningstudio.watchrss.data.cloud.WatchCloudStateStore
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackPreviewCache
import com.lightningstudio.watchrss.data.rss.FileArticleContentStore
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchAccountWorkspaceRuntimeTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun account(id: String) = WatchAccountState(
        userId = id, phoneMasked = "138****0000", installId = "test-install",
        watchDeviceToken = "test-only-token", tokenExpiresAtMillis = 0,
        backendBaseUrl = "https://invalid.example", posthogHost = "", posthogProjectApiKey = ""
    )

    @Test fun switchingActualContainersRestoresPrivateLibrarySettingsAndRejectsOldRefresh() = runBlocking {
        val suffix = UUID.randomUUID().toString()
        val name = "verification_watch_account_$suffix"
        val alpha = account("alpha-$suffix")
        val beta = account("beta-$suffix")
        val bootstrap = WatchAccountStore(target, name, enforceIdentity = true)
        val opened = mutableListOf<DefaultAppContainer>()
        fun fresh() = DefaultAppContainer(target, WatchAccountStore(target, name, enforceIdentity = true)).also { opened += it }
        try {
            bootstrap.persistNewIdentity(alpha)
            val a = fresh()
            assertNotEquals(target.filesDir, a.libraryContext.filesDir)
            a.settingsRepository.setReadingFontSizeSp(22)
            a.watchNoteRepository.saveRawMarkdown(null, "alpha private note", "watch", rawTitle = "Alpha")
            val textA = FileArticleContentStore(a.libraryContext)
            val marker = textA.storeText("same-id", "alpha body")
            val asset = File(a.libraryContext.filesDir, "notes/assets/same.jpg").apply {
                parentFile!!.mkdirs(); writeText("alpha image")
            }
            WatchCloudStateStore(a.libraryContext).markApplied("phone", 40, true)
            a.watchAccountStore.persistNewIdentity(beta)
            assertNull(a.watchAccountStore.read())
            assertTrue(runCatching { a.watchAccountStore.save(alpha.copy(watchDeviceToken = "late-refresh")) }.isFailure)
            assertEquals(beta.userId, WatchAccountStore(target, name).read()?.userId)
            a.stopAccountTasks(); a.closeAccountDatabase()

            val b = fresh()
            assertTrue(b.watchNoteRepository.all().isEmpty())
            assertNull(FileArticleContentStore(b.libraryContext).loadText(marker))
            assertFalse(File(b.libraryContext.filesDir, "notes/assets/same.jpg").exists())
            assertEquals(0L, WatchCloudStateStore(b.libraryContext).applied("phone", true))
            b.watchNoteRepository.saveRawMarkdown(null, "beta private note", "watch", rawTitle = "Beta")
            b.settingsRepository.setReadingFontSizeSp(16)
            b.watchAccountStore.persistNewIdentity(alpha)
            b.stopAccountTasks(); b.closeAccountDatabase()

            val restored = fresh()
            assertEquals("Alpha", restored.watchNoteRepository.all().single().title)
            assertEquals(22, restored.settingsRepository.readingFontSizeSp.first())
            assertEquals("alpha body", FileArticleContentStore(restored.libraryContext).loadText(marker))
            assertEquals("alpha image", asset.readText())
            assertEquals(40L, WatchCloudStateStore(restored.libraryContext).applied("phone", true))
        } finally {
            opened.forEach { it.stopAccountTasks(); it.closeAccountDatabase() }
            opened.map { it.libraryContext }.distinctBy { it.filesDir }.forEach { context ->
                // These UUID-bound verification accounts can never own the real legacy root.
                if (context.filesDir.absolutePath.contains("/accounts/")) {
                    context.deleteSharedPreferences("watchrss_cloud_state")
                    context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
                    context.getDatabasePath("watchrss.db").parentFile!!.deleteRecursively()
                }
            }
            target.deleteSharedPreferences(name)
            val app = target.applicationContext as WatchRssApplication
            DouyinPlaybackPreviewCache.configure(app.libraryContext)
            RssImageLoader.configure(app.container.managedCacheService)
        }
    }
}
