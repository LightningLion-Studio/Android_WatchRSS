package com.lightningstudio.watchrss.ui.reader

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import com.lightningstudio.watchrss.data.db.WatchRssDatabase
import com.lightningstudio.watchrss.data.reader.*
import com.lightningstudio.watchrss.phoneconnection.bluetooth.ReaderPresetSyncPayload
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** Isolated Room, files and preferences; exercises the actual watch sync protocol handler. */
class ReaderPresetLiveSyncTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val name = "reader-live-${UUID.randomUUID()}"
    private lateinit var database: WatchRssDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var context: Context
    private lateinit var repository: ReaderPresetRepository
    private lateinit var directory: File
    private val prefNames = mutableSetOf<String>()

    @Before fun createIsolatedReader() {
        val base = compose.activity.applicationContext
        directory = File(base.cacheDir, name).apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(pref: String, mode: Int): SharedPreferences {
                val isolated = "$name-$pref"
                prefNames += isolated
                return base.getSharedPreferences(isolated, mode)
            }
        }
        openRepository()
    }

    private fun openRepository() {
        database = Room.databaseBuilder(compose.activity, WatchRssDatabase::class.java, name).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        repository = ReaderPresetRepository(context, database, database.readerPresetDao(), "test-watch", scope)
    }

    @After fun cleanOnlyTestData() {
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        database.close()
        compose.activity.deleteDatabase(name)
        prefNames.forEach { compose.activity.deleteSharedPreferences(it) }
        directory.deleteRecursively()
    }

    @Test fun selectedPresetUpdate_changesLiveStyle_andSurvivesRepositoryReopen() {
        val original = ReaderPreset(id = "selected", name = "选中预设", updatedAt = 1, modifiedBy = "phone")
        installAndSelect(original)
        showReader()
        waitForText("size=14.0")
        val updated = original.copy(body = original.body.copy(fontSizeSp = 23f), updatedAt = 2,
            background = ReaderBackground(colorArgb = 0xFF00FF00))
        manifest(listOf(updated))
        waitForText("size=23.0")
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("size=23.0").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(23f, layouts.single().layoutInput.style.fontSize.value)
        assertTrue(readerPixel().green > .9f)
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        database.close()
        openRepository()
        val restored = runBlocking { withTimeout(5_000) { repository.activePreset.first { it.id == original.id } } }
        assertEquals(23f, restored.body.fontSizeSp)
        assertEquals(original.id, repository.selection.value.lightPresetId)
    }

    @Test fun unselectedPresetUpdate_doesNotStealWatchSelection() {
        val selected = ReaderPreset(id = "selected", name = "Z selected", updatedAt = 1, modifiedBy = "phone")
        installAndSelect(selected)
        showReader()
        waitForText("size=14.0")
        val other = selected.copy(id = "other", name = "A other", body = selected.body.copy(fontSizeSp = 30f), updatedAt = 2)
        manifest(listOf(other))
        runBlocking { withTimeout(5_000) { repository.presets.first { it.any { preset -> preset.id == "other" } } } }
        compose.waitForIdle()
        assertEquals("selected", repository.activePreset.value.id)
        compose.onNodeWithText("size=14.0").assertIsDisplayed()
        compose.onNodeWithText("size=30.0").assertDoesNotExist()
    }

    @Test fun lateBackgroundFile_refreshesAlreadyOpenReader_withoutPresetChange() {
        val image = imageBytes()
        val asset = backgroundAsset(image)
        val preset = ReaderPreset(id = "selected", name = "同步背景", updatedAt = 1, modifiedBy = "phone",
            background = ReaderBackground(type = ReaderBackgroundType.IMAGE, assetId = asset.id))
        installAndSelect(preset, listOf(asset))
        showReader()
        waitForText("size=14.0")
        assertTrue(readerPixel().red < .1f)
        capture("01-before-resource.png")
        val ack = pushImage(image, asset.masterFileName)
        assertTrue(ack.getBoolean("applied"))
        compose.waitUntil(5_000) { readerPixel().red > .9f }
        capture("02-after-resource.png")
        assertEquals(1L, repository.activePreset.value.updatedAt) // Same preset; only resource arrived.
    }

    @Test fun lateBackgroundMetadata_refreshesFileAlreadyOnDisk() {
        val bytes = imageBytes()
        val asset = backgroundAsset(bytes)
        val preset = ReaderPreset(id = "selected", name = "先文件后清单", updatedAt = 1, modifiedBy = "phone",
            background = ReaderBackground(type = ReaderBackgroundType.IMAGE, assetId = asset.id))
        installAndSelect(preset)
        showReader()
        waitForText("size=14.0")
        pushImage(bytes, asset.masterFileName)
        compose.waitForIdle()
        assertTrue(readerPixel().red < .1f)
        manifest(emptyList(), listOf(asset))
        compose.waitUntil(5_000) { readerPixel().red > .9f }
        assertEquals(preset.id, repository.activePreset.value.id)
    }

    @Test fun partialResource_isNotAppliedUntilLastChunkPassesValidation() {
        // PNG permits trailing bytes; padding exercises the production 1 MiB chunk boundary.
        val bytes = imageBytes() + ByteArray(ReaderPresetSyncPayload.CHUNK_BYTES)
        val asset = backgroundAsset(bytes)
        val preset = ReaderPreset(id = "selected", name = "分块测试", updatedAt = 1, modifiedBy = "phone",
            background = ReaderBackground(type = ReaderBackgroundType.IMAGE, assetId = asset.id))
        installAndSelect(preset, listOf(asset))
        showReader()
        waitForText("size=14.0")
        assertFalse(pushChunk(bytes, asset.masterFileName, 0, 2).getBoolean("applied"))
        assertNull(repository.resourceStore.backgroundFile(asset.masterFileName))
        assertTrue(readerPixel().red < .1f)
        assertTrue(pushChunk(bytes, asset.masterFileName, 1, 2).getBoolean("applied"))
        compose.waitUntil(5_000) { readerPixel().red > .9f }
    }

    @Test fun invalidChecksum_keepsPreviousResourceAndDoesNotPublishRefresh() {
        val bytes = imageBytes()
        val asset = backgroundAsset(bytes)
        val preset = ReaderPreset(id = "selected", name = "校验失败", updatedAt = 1, modifiedBy = "phone",
            background = ReaderBackground(type = ReaderBackgroundType.IMAGE, assetId = asset.id))
        installAndSelect(preset, listOf(asset))
        showReader()
        pushImage(bytes, asset.masterFileName)
        compose.waitUntil(5_000) { readerPixel().red > .9f }
        val before = repository.resourceRevision.value
        val failure = runCatching { pushChunk(bytes, asset.masterFileName, 0, 1, "0".repeat(64)) }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertEquals(before, repository.resourceRevision.value)
        assertTrue(repository.resourceStore.verify(repository.resourceStore.backgroundFile(asset.masterFileName)!!,
            bytes.size.toLong(), hash(bytes)))
        assertTrue(readerPixel().red > .9f)
    }

    private fun capture(name: String) {
        val output = File(compose.activity.getExternalFilesDir(null), "bug004").apply { mkdirs() }
        File(output, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun showReader() {
        val current = repository
        compose.setContent {
            WatchRSSTheme {
                ProvideReaderPreset(current) {
                    ReaderBackgroundSurface(Modifier.fillMaxSize().testTag("reader")) {
                        Text("size=${LocalReaderPresetRuntime.current.preset.body.fontSizeSp}",
                            style = readerTextStyle(ReaderTextRole.BODY))
                    }
                }
            }
        }
    }

    private fun readerPixel() = compose.onNodeWithTag("reader").captureToImage().toPixelMap().let {
        it[it.width / 2, it.height * 3 / 4]
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun installAndSelect(preset: ReaderPreset, backgrounds: List<ReaderBackgroundAssetEntity> = emptyList()) {
        manifest(listOf(preset), backgrounds)
        runBlocking { withTimeout(5_000) {
            repository.presets.first { it.any { p -> p.id == preset.id } }
            if (backgrounds.isNotEmpty()) repository.backgrounds.first { it.isNotEmpty() }
        } }
        repository.setActivePreset(preset.id)
        repository.setThemeMode(ReaderThemeMode.LIGHT)
        runBlocking { withTimeout(5_000) { repository.activePreset.first { it.id == preset.id } } }
    }

    private fun manifest(presets: List<ReaderPreset>, backgrounds: List<ReaderBackgroundAssetEntity> = emptyList()) {
        val request = ReaderPresetSyncPayload.snapshotJson(ReaderPresetSnapshot(
            presets.map { it.toEntity() }, emptyList(), backgrounds, emptyList()
        )).put("phase", ReaderPresetSyncPayload.PHASE_MANIFEST)
        runBlocking { ReaderPresetSyncPayload.handle(request, repository) }
    }

    private fun imageBytes() = ByteArrayOutputStream().also {
        Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
            .compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private fun backgroundAsset(bytes: ByteArray) = ReaderBackgroundAssetEntity(
        "bg", hash(bytes), "测试背景", "IMAGE", "image/png", "test.png", bytes.size.toLong(),
        0L, 40, 40, null, "{}", 1L, "phone", false
    )

    private fun pushImage(bytes: ByteArray, fileName: String): JSONObject = pushChunk(bytes, fileName, 0, 1)

    private fun pushChunk(bytes: ByteArray, fileName: String, index: Int, count: Int,
                          expectedHash: String = hash(bytes)): JSONObject = runBlocking {
        val start = index * ReaderPresetSyncPayload.CHUNK_BYTES
        val chunk = bytes.copyOfRange(start, minOf(start + ReaderPresetSyncPayload.CHUNK_BYTES, bytes.size))
        ReaderPresetSyncPayload.handle(JSONObject().apply {
            put("phase", ReaderPresetSyncPayload.PHASE_PUSH_RESOURCE)
            put("kind", "background"); put("fileName", fileName)
            put("sha256", expectedHash); put("totalBytes", bytes.size)
            put("chunkIndex", index); put("chunkCount", count)
            put("chunkSha256", hash(chunk)); put("data", Base64.getEncoder().encodeToString(chunk))
        }, repository)
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
