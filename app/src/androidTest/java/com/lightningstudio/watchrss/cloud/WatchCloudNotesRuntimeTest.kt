package com.lightningstudio.watchrss.cloud

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.data.account.*
import com.lightningstudio.watchrss.data.account.workspace.AccountDataContext
import com.lightningstudio.watchrss.data.cloud.*
import com.lightningstudio.watchrss.data.db.WatchRssDatabase
import com.lightningstudio.watchrss.data.note.*
import java.io.File
import java.util.UUID
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchCloudNotesRuntimeTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(name: String) = InstrumentationRegistry.getInstrumentation().context.assets.open("cloud/$name").use { it.readBytes() }

    @Test fun phoneBundleRestoresFoldersImagesAndExportsACompatibleWatchBundle() = runBlocking {
        val context = AccountDataContext(target, "cloud_notes_${UUID.randomUUID()}", false)
        val other = AccountDataContext(target, "cloud_notes_other_${UUID.randomUUID()}", false)
        val database = Room.inMemoryDatabaseBuilder(context, WatchRssDatabase::class.java).build()
        try {
            val repository = WatchNoteRepository(database.watchNoteDao())
            val adapter = WatchCloudNotesAdapter(context, repository, "fixture-watch")
            val state = fixture("phone-notes-state.json"); val archive = fixture("phone-notes-assets.zip")
            assertEquals(3, adapter.restore(state, archive))
            assertEquals("cloud-folder", repository.all().single().folderId)
            assertEquals(2, repository.cloudState().folders.size)
            assertTrue(repository.cloudState().folders.single { it.folderId == "deleted-folder" }.deleted)
            val image = File(context.filesDir, "notes/assets/cloud.jpg")
            assertEquals("cloud fixture image", image.readText())
            assertFalse(File(other.filesDir, "notes/assets/cloud.jpg").exists())
            image.writeText("damaged cache")
            assertEquals(0, adapter.restore(state, archive))
            assertEquals("cloud fixture image", image.readText())
            val payload = adapter.export()
            assertEquals(2, NoteCloudStateCodec.decodeState(payload.state).folders.size)
            assertEquals(1, MarkdownNoteArchive.read(requireNotNull(payload.archive)).count { it.path == "notes/assets/cloud.jpg" })
            val output = File(target.cacheDir, "cloud-note-interop").apply { mkdirs() }
            File(output, "watch-notes-state.json").writeBytes(payload.state)
            File(output, "watch-notes-assets.zip").writeBytes(requireNotNull(payload.archive))
            val bad = repository.all().single().copy(markdown = "![bad](assets/missing.jpg)")
            var rejected = false
            try { adapter.restore(NoteCloudStateCodec.encode(listOf(bad)), null) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(NoteCloudStateCodec.decode(state).single().markdown, repository.all().single().markdown)
        } finally {
            database.close(); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
            other.filesDir.deleteRecursively(); other.cacheDir.deleteRecursively()
        }
    }

    @Test fun actualReadOnlyCloudServiceDownloadsAuthenticatedNotesAndAcknowledgesOnlyAfterApply() = runBlocking {
        val context = AccountDataContext(target, "cloud_notes_service_${UUID.randomUUID()}", false)
        val database = Room.inMemoryDatabaseBuilder(context, WatchRssDatabase::class.java).build()
        val user = "fixture-${UUID.randomUUID()}"; val device = "fixture-watch"
        val keys = WatchCloudKeyManager(context)
        val alias = "watchrss_cloud_ecdh_" + WatchCloudCodec.sha256("$user:$device".toByteArray()).take(24)
        try {
            val key = ByteArray(32) { (it + 1).toByte() }
            approve(keys, user, device, key)
            val snapshot = WatchCloudCodec().create(key, 1, "fixture-phone", 1,
                listOf(WatchCloudLogicalObject("notes-state.json", fixture("phone-notes-state.json")), WatchCloudLogicalObject("notes-assets.zip", fixture("phone-notes-assets.zip"), false)), emptyMap(), emptyMap())
            val id = snapshot.manifest.snapshotId
            val head = JSONObject().put("id", id).put("sourceDeviceId", "fixture-phone").put("deviceSequence", 1).put("keyVersion", 1)
                .put("manifestSha256", WatchCloudCodec.sha256(snapshot.encryptedManifest)).put("manifestSizeBytes", snapshot.encryptedManifest.size).put("observedHeads", JSONObject())
            val bootstrap = JSONObject().put("member", JSONObject().put("readable", true).put("writable", false).put("quotaBytes", 1048576))
                .put("devices", JSONArray()).put("keyEnvelopes", JSONArray())
            val paths = mutableListOf<String>()
            var failAck = true
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                val path = chain.request().url.encodedPath
                paths += path
                var code = 200
                val bytes = when (path) {
                    "/functions/v1/cloud/bootstrap" -> bootstrap.toString().toByteArray()
                    "/functions/v1/cloud/snapshots/heads" -> JSONObject().put("heads", JSONArray().put(head)).toString().toByteArray()
                    "/functions/v1/cloud/snapshots/$id" -> JSONObject().put("snapshot", JSONObject().put("manifestSignedUrl", "https://invalid.example/manifest"))
                        .put("chunks", JSONArray().apply { snapshot.newChunks.forEach { (hash, bytes) -> put(JSONObject().put("sha256", hash).put("sizeBytes", bytes.size).put("signedUrl", "https://invalid.example/chunks/$hash")) } }).toString().toByteArray()
                    "/functions/v1/cloud/snapshots/$id/ack" -> {
                        assertEquals(1, runBlocking { database.watchNoteDao().all().size })
                        if (failAck) code = 500
                        "{}".toByteArray()
                    }
                    "/manifest" -> snapshot.encryptedManifest
                    else -> snapshot.newChunks[path.removePrefix("/chunks/")] ?: error("Unexpected request or mutation: $path")
                }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                    .body(bytes.toResponseBody("application/octet-stream".toMediaType())).build()
            }.build()
            val account = WatchAccountState(user, "fixture", "fixture", "fixture-token", System.currentTimeMillis() + 3600000,
                backendBaseUrl = "https://invalid.example", posthogHost = "", posthogProjectApiKey = "")
            val store = object : AccountStore {
                override val state = MutableStateFlow<WatchAccountState?>(account)
                override fun read() = account
                override fun save(state: WatchAccountState) = error("No credentials may change")
                override fun clear() = Unit
            }
            val rss = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(com.lightningstudio.watchrss.data.rss.RssRepository::class.java)) { _, method, _ ->
                if (method.name == "exportSyncedRssSources") emptyList<Any>() else error("Unexpected RSS operation: ${method.name}")
            } as com.lightningstudio.watchrss.data.rss.RssRepository
            context.getSharedPreferences("watchrss_cloud_state", 0).edit().putString("applied_full_reader_v2", "{\"fixture-phone\":100}").commit()
            val notes = WatchNoteRepository(database.watchNoteDao())
            val service = WatchCloudSyncService(context, store, rss, client = WatchCloudClient(http), deviceId = device, noteRepository = notes)
            assertFalse(service.syncNow(manual = true))
            assertEquals(WatchCloudPhase.ERROR, service.status.value.phase)
            assertEquals(0L, WatchCloudStateStore(context).applied("fixture-phone", true))
            assertEquals(0L, service.status.value.lastCompletedAt)
            failAck = false
            assertTrue(service.status.value.message, service.syncNow(manual = true))
            assertEquals(WatchCloudPhase.COMPLETE, service.status.value.phase)
            assertEquals(1, notes.all().size)
            assertEquals(2, notes.cloudState().folders.size)
            assertEquals("cloud fixture image", File(context.filesDir, "notes/assets/cloud.jpg").readText())
            assertEquals(1L, WatchCloudStateStore(context).applied("fixture-phone", true))
            assertTrue(paths.contains("/functions/v1/cloud/snapshots/$id/ack"))
            val original = notes.saveRawMarkdown(null, "base", device, now = 100)
            notes.saveRawMarkdown(original.noteId, "local", device, now = 200)
            notes.merge(listOf(original.copy(markdown = "remote", plainText = "remote", contentHash = watchNoteHash("remote"), updatedAt = 300, modifiedBy = "phone")), device)
            assertEquals(1, notes.retainedConflictCopyCount())
            repeat(2) {
                assertTrue(service.syncNow(manual = true))
                assertTrue(service.status.value.message.contains("已保留 1 份冲突副本"))
            }
            assertEquals(2, paths.count { it == "/functions/v1/cloud/snapshots/$id" })
            val conflict = notes.all().single { it.noteId.startsWith("conflict-") }
            notes.delete(conflict.noteId, device)
            assertTrue(service.syncNow(manual = true))
            assertFalse(service.status.value.message.contains("冲突副本"))
        } finally {
            database.close()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias); deleteEntry(alias.replace("_ecdh_", "_rsa_")) }
            listOf("watchrss_cloud_keys", "watchrss_cloud_state", "watchrss_cloud_controls").forEach(context::deleteSharedPreferences)
            context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
        }
    }

    private fun approve(keys: WatchCloudKeyManager, user: String, device: String, key: ByteArray) {
        if (keys.deviceKeyAlgorithm(user, device) == "RSA-2048") {
            val recipient = java.security.KeyFactory.getInstance("RSA").generatePublic(java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(keys.publicKeySpki(user, device))))
            val wrapping = ByteArray(32) { (it + 5).toByte() }
            val nonce = ByteArray(12) { (it + 7).toByte() }
            val capsule = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding").run {
                init(javax.crypto.Cipher.ENCRYPT_MODE, recipient, javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA1, javax.crypto.spec.PSource.PSpecified.DEFAULT))
                doFinal(wrapping)
            }
            val ciphertext = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").run {
                init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(wrapping, "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce))
                updateAAD("watchrss-account-key:$user:1:$device".toByteArray()); doFinal(key)
            }
            keys.acceptEnvelope(user, device, WatchDeviceKeyEnvelope(RsaDeviceEnvelope.ALGORITHM, 1, Base64.getEncoder().encodeToString(capsule + ciphertext), Base64.getEncoder().encodeToString(nonce), ""))
            return
        }
        val recipient = java.security.KeyFactory.getInstance("EC").generatePublic(java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(keys.publicKeySpki(user, device))))
        val pair = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val secret = javax.crypto.KeyAgreement.getInstance("ECDH").run { init(pair.private); doPhase(recipient, true); generateSecret() }
        val salt = java.security.MessageDigest.getInstance("SHA-256").digest(user.toByteArray())
        val extracted = javax.crypto.Mac.getInstance("HmacSHA256").apply { init(javax.crypto.spec.SecretKeySpec(salt, "HmacSHA256")) }.doFinal(secret)
        val wrapping = javax.crypto.Mac.getInstance("HmacSHA256").apply { init(javax.crypto.spec.SecretKeySpec(extracted, "HmacSHA256")) }.doFinal("watchrss/device-envelope/v1/$device".toByteArray() + byteArrayOf(1))
        val nonce = ByteArray(12) { (it + 2).toByte() }
        val encrypted = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").run {
            init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(wrapping, "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce))
            updateAAD("watchrss-account-key:$user:1:$device".toByteArray()); doFinal(key)
        }
        keys.acceptEnvelope(user, device, WatchDeviceKeyEnvelope("P-256+HKDF-SHA256+A256GCM", 1, Base64.getEncoder().encodeToString(encrypted), Base64.getEncoder().encodeToString(nonce), Base64.getEncoder().encodeToString(pair.public.encoded)))
        secret.fill(0); extracted.fill(0); wrapping.fill(0)
    }
}
