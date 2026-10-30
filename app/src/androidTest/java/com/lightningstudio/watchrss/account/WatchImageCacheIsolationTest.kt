package com.lightningstudio.watchrss.account

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.account.workspace.AccountDataContext
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchImageCacheIsolationTest {
    @Test fun sameUrlAndLateOldLoadsStayInTheirOwnMemoryAndDiskNamespace() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val app = target.applicationContext as WatchRssApplication
        val suffix = UUID.randomUUID().toString()
        val a = AccountDataContext(target, "image_test_a_$suffix", false)
        val b = AccountDataContext(target, "image_test_b_$suffix", false)
        val url = "https://invalid.example/private/image-$suffix"
        val late = "https://invalid.example/private/late-$suffix"
        fun imageFile(context: android.content.Context, key: String): File {
            val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
            return File(context.cacheDir, "rss_images/$hash.img")
        }
        fun seed(context: android.content.Context, key: String, color: Int) {
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            imageFile(context, key).also { it.parentFile!!.mkdirs() }.outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
        try {
            seed(a, url, Color.RED)
            seed(b, url, Color.BLUE)
            seed(a, late, Color.GREEN)
            RssImageLoader.configure(null, a.cacheDir.absolutePath)
            assertEquals(Color.RED, RssImageLoader.loadBitmap(a, url, 20)!!.getPixel(0, 0))
            assertEquals(Color.RED, RssImageLoader.getCachedBitmap(url)!!.getPixel(0, 0))
            RssImageLoader.configure(null, b.cacheDir.absolutePath)
            assertNull(RssImageLoader.getCachedBitmap(url))
            assertEquals(Color.BLUE, RssImageLoader.loadBitmap(b, url, 20)!!.getPixel(0, 0))
            assertEquals(Color.GREEN, RssImageLoader.loadBitmap(a, late, 20)!!.getPixel(0, 0))
            assertNull(RssImageLoader.getCachedBitmap(late))
            assertFalse(imageFile(b, late).exists())
            RssImageLoader.configure(null, a.cacheDir.absolutePath)
            assertEquals(Color.RED, RssImageLoader.getCachedBitmap(url)!!.getPixel(0, 0))
            assertEquals(Color.GREEN, RssImageLoader.getCachedBitmap(late)!!.getPixel(0, 0))
        } finally {
            a.cacheDir.deleteRecursively(); b.cacheDir.deleteRecursively()
            a.filesDir.deleteRecursively(); b.filesDir.deleteRecursively()
            RssImageLoader.configure(app.container.managedCacheService, app.libraryContext.cacheDir.absolutePath)
        }
    }
}
