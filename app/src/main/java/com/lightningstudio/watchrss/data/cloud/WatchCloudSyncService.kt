package com.lightningstudio.watchrss.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import com.lightningstudio.watchrss.BuildConfig
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lightningstudio.watchrss.data.account.AccountStore
import com.lightningstudio.watchrss.data.account.WatchTokenManager
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class WatchCloudPhase {
    IDLE,
    REGISTERING,
    WAITING_APPROVAL,
    WAITING_NETWORK,
    DOWNLOADING,
    MERGING,
    UPLOADING,
    COMPLETE,
    ERROR
}

data class WatchCloudStatus(
    val phase: WatchCloudPhase = WatchCloudPhase.IDLE,
    val message: String = "尚未同步",
    val usedBytes: Long = 0,
    val quotaBytes: Long = 0,
    val lastCompletedAt: Long = 0,
    /** 状态所属账号；界面只展示与当前账号一致的状态。 */
    val accountId: String = ""
)

data class WatchCloudApprovalRequest(val id: String, val userId: String, val expiresAt: Long)

class WatchCloudSyncService(
    context: Context,
    private val accountStore: AccountStore,
    repository: com.lightningstudio.watchrss.data.rss.RssRepository,
    private val tokenManager: WatchTokenManager = WatchTokenManager(accountStore),
    private val client: WatchCloudClient = WatchCloudClient(accountProvider = { original ->
        val current = accountStore.read() ?: error("手表尚未绑定账号")
        check(current.userId == original.userId && current.installId == original.installId &&
            current.backendBaseUrl.trimEnd('/') == original.backendBaseUrl.trimEnd('/')) { "账号已切换" }
        if (current.watchRefreshToken.isNotBlank()) tokenManager.freshAccount() else current
    }),
    private val codec: WatchCloudCodec = WatchCloudCodec(),
    private val deviceId: String = WatchDeviceIdentity(context).deviceId,
    private val canSync: suspend () -> Boolean = { true },
    private val readerRepository: com.lightningstudio.watchrss.data.reader.ReaderPresetRepository? = null,
    private val noteRepository: com.lightningstudio.watchrss.data.note.WatchNoteRepository? = null
) {
    private val appContext = context.applicationContext
    internal val controls = WatchCloudControls(context)
    private val keyManager = WatchCloudKeyManager(context)
    private val cache = WatchCloudCache(context)
    private val stateStore = WatchCloudStateStore(context)
    private val recordStore = WatchCloudRecordStore(context)
    private val library = WatchCloudLibraryAdapter(repository, deviceId)
    private val notes = noteRepository?.let { WatchCloudNotesAdapter(context, it, deviceId) }
    private val mutex = Mutex()
    private val _status = MutableStateFlow(WatchCloudStatus())
    private var statusAccountId = ""
    val status: StateFlow<WatchCloudStatus> = _status

    /** Requests authorize only this installation. No account/key material leaves the watch. */
    suspend fun requestCodeApproval(): WatchCloudApprovalRequest = withContext(Dispatchers.IO) {
        val owner = accountStore.read()?.userId ?: error("手表尚未绑定账号")
        val account = tokenManager.freshAccount()
        check(account.userId == owner && accountStore.read()?.userId == owner) { "账号已切换" }
        client.register(account, deviceId, keyManager.publicKeySpki(owner, deviceId),
            "手表 · ${Build.MODEL}", keyManager.currentKeyVersion(owner), keyManager.deviceKeyAlgorithm(owner, deviceId))
        val (id, expires) = client.createApproval(account, deviceId)
        check(accountStore.read()?.userId == owner) { "账号已切换" }
        WatchCloudApprovalRequest(id, owner, expires)
    }

    suspend fun redeemCodeApproval(request: WatchCloudApprovalRequest, code: String) = withContext(Dispatchers.IO) {
        require(code.length == 8 && code.all { it in '0'..'9' }) { "请输入8位配对码" }
        check(System.currentTimeMillis() < request.expiresAt) { "approval_expired" }
        check(accountStore.read()?.userId == request.userId) { "账号已切换" }
        val account = tokenManager.freshAccount()
        check(account.userId == request.userId) { "账号已切换" }
        client.redeemApproval(account, deviceId, request.id, code)
        val bootstrap = client.bootstrap(account)
        check(accountStore.read()?.userId == request.userId) { "账号已切换" }
        bootstrap.envelopes.sortedBy(WatchDeviceKeyEnvelope::keyVersion).forEach { envelope ->
            // Re-authorization still verifies/decrypts the offered envelope. The key manager
            // compares an existing same-version key before persisting, so conflicts cannot replace it.
            keyManager.acceptEnvelope(request.userId, deviceId, envelope).fill(0)
        }
        check(keyManager.keyVersions(request.userId).isNotEmpty()) { "授权尚未完成，请稍后重新检查" }
        statusAccountId = request.userId
        if (!mutex.isLocked) set(WatchCloudPhase.IDLE, "已获得云同步授权", bootstrap.member)
    }

    suspend fun syncNow(manual: Boolean = false): Boolean = mutex.withLock {
        try {
            if (!canSync()) {
                set(WatchCloudPhase.IDLE, "请先在应用入口阅读并同意隐私政策")
                return@withLock false
            }
            if (!manual && !controls.state.value.automatic) {
                set(WatchCloudPhase.IDLE, "自动云同步已关闭")
                return@withLock false
            }
            val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
            val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
            val connected = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            val (allowed, fullTransfer) = cloudTransferAllowed(controls.state.value.network, manual, connected,
                !connectivity.isActiveNetworkMetered, appContext.getSystemService(android.os.BatteryManager::class.java).isCharging)
            if (!allowed) {
                set(if (connected) WatchCloudPhase.IDLE else WatchCloudPhase.WAITING_NETWORK,
                    if (connected) "等待 Wi-Fi 并充电" else "等待网络连接")
                return@withLock false
            }
            val storedAccount = accountStore.read()
            if (storedAccount == null) {
                set(WatchCloudPhase.IDLE, "请先登录后再同步")
                return@withLock false
            }
            statusAccountId = storedAccount.userId
            val account = if (storedAccount.watchRefreshToken.isNotBlank()) {
                tokenManager.freshAccount()
            } else {
                storedAccount
            }
            var bootstrap = client.bootstrap(account)
            require(bootstrap.member.readable) { "当前账号没有可读取的会员云空间" }
            val publicKey = keyManager.publicKeySpki(account.userId, deviceId)
            if (bootstrap.member.writable) {
                set(WatchCloudPhase.REGISTERING, "正在登记手表")
                client.register(account, deviceId, publicKey, "手表 · ${Build.MODEL}", keyManager.currentKeyVersion(account.userId), keyManager.deviceKeyAlgorithm(account.userId, deviceId))
                bootstrap = client.bootstrap(account)
            }
            bootstrap.envelopes.sortedBy(WatchDeviceKeyEnvelope::keyVersion).forEach { envelope ->
                if (keyManager.accountKey(account.userId, envelope.keyVersion) == null) {
                    keyManager.acceptEnvelope(account.userId, deviceId, envelope)
                }
            }
            if (keyManager.keyVersions(account.userId).isEmpty()) {
                if (bootstrap.envelopes.isEmpty()) {
                    set(
                        WatchCloudPhase.WAITING_APPROVAL,
                        "请在手机会员云空间中批准此手表",
                        bootstrap.member
                    )
                    return@withLock false
                }
                error("设备密钥信封无法解密")
            }
            if (bootstrap.member.writable) client.register(
                account,
                deviceId,
                publicKey,
                "手表 · ${Build.MODEL}",
                keyManager.currentKeyVersion(account.userId),
                keyManager.deviceKeyAlgorithm(account.userId, deviceId)
            )
            bootstrap = client.bootstrap(account)
            var changed = false
            val heads = client.heads(account)
            for (head in heads.sortedBy(WatchCloudHead::sequence)) {
                if (head.sequence <= stateStore.applied(head.sourceDeviceId, fullTransfer)) continue
                set(WatchCloudPhase.DOWNLOADING, "正在接收云快照", bootstrap.member)
                val download = client.snapshot(account, head.id)
                val encryptedManifest = cache.manifest(account.userId, head.id)
                    ?: client.download(
                        download.manifestUrl,
                        head.manifestBytes,
                        head.manifestHash
                    ).also {
                        cache.storeManifest(
                            account.userId,
                            head.id,
                            it,
                            markAsLocalHead = false
                        )
                    }
                val accountKey = keyManager.accountKey(account.userId, head.keyVersion)
                    ?: error("缺少第${head.keyVersion}版账号密钥，请在手机端重新批准手表")
                val manifest = codec.decryptManifest(accountKey, head.id, encryptedManifest)
                val selected = manifest.objects.filter {
                    it.name == RSS_STATE_OBJECT || (fullTransfer && (it.name == RELAY_OBJECT || (readerRepository != null && it.name == PRIVATE_LIBRARY_OBJECT) ||
                        (notes != null && it.name in setOf(NOTES_STATE_OBJECT, NOTES_ARCHIVE_OBJECT))))
                }
                if (selected.isNotEmpty()) {
                    val remoteChunks = download.chunks.associateBy(WatchCloudDownloadObject::sha256)
                    selected.flatMap(WatchCloudObject::chunks)
                        .distinctBy(WatchCloudChunk::ciphertextSha256)
                        .forEach { descriptor ->
                            if (cache.chunk(account.userId, descriptor.ciphertextSha256) == null) {
                                val remote = remoteChunks[descriptor.ciphertextSha256]
                                    ?: error("云快照缺少中继数据块")
                                client.download(
                                    remote.signedUrl,
                                    remote.sizeBytes,
                                    remote.sha256
                                ).also {
                                    cache.storeChunk(account.userId, remote.sha256, it)
                                }
                            }
                        }
                    set(WatchCloudPhase.MERGING, "正在合并手机数据", bootstrap.member)
                    for (objectDescriptor in selected) {
                        if (objectDescriptor.name != RELAY_OBJECT && objectDescriptor.name != RSS_STATE_OBJECT) continue
                        debugProgress("restore_start object=${objectDescriptor.name} bytes=${objectDescriptor.originalBytes} chunks=${objectDescriptor.chunks.size}")
                        val applied = codec.withRestoredObject(accountKey, objectDescriptor,
                            { hash -> cache.chunk(account.userId, hash) ?: error("中继缓存缺失") }) { input ->
                            when (objectDescriptor.name) {
                                RELAY_OBJECT -> library.restore(input, head.sourceDeviceId)
                                RSS_STATE_OBJECT -> library.restoreState(input, head.sourceDeviceId)
                                else -> 0
                            }
                        }
                        changed = applied > 0 || changed
                        debugProgress("restore_done object=${objectDescriptor.name} applied=$applied")
                    }
                    // Keep large article objects on the v2 streaming path. Restore the
                    // upstream reader/note formats together so note assets precede publication.
                    val attachments = selected.filter { it.name != RELAY_OBJECT && it.name != RSS_STATE_OBJECT }
                    if (attachments.isNotEmpty()) {
                        val payload = codec.restore(accountKey, manifest.copy(objects = attachments)) { hash ->
                            cache.chunk(account.userId, hash) ?: error("中继缓存缺失")
                        }
                        notes?.let { adapter ->
                            changed = adapter.restore(payload[NOTES_STATE_OBJECT], payload[NOTES_ARCHIVE_OBJECT]) > 0 || changed
                        }
                        payload[PRIVATE_LIBRARY_OBJECT]?.let { bytes ->
                            readerRepository?.let { reader ->
                                changed = WatchCloudReaderArchive.restore(bytes, reader, appContext.cacheDir) || changed
                            }
                        }
                    }
                }
                cache.storeManifest(
                    account.userId,
                    head.id,
                    encryptedManifest,
                    markAsLocalHead = false
                )
                if (fullTransfer) {
                    client.acknowledge(account, head.id, deviceId)
                    debugProgress("head_ack sequence=${head.sequence}")
                    stateStore.markApplied(head.sourceDeviceId, head.sequence, full = true)
                    stateStore.markApplied(head.sourceDeviceId, head.sequence, full = false)
                } else if (selected.any { it.name == RSS_STATE_OBJECT }) {
                    stateStore.markApplied(head.sourceDeviceId, head.sequence, full = false)
                }
            }

            if (fullTransfer) {
                set(WatchCloudPhase.DOWNLOADING, "正在更新公共 RSS 库", bootstrap.member)
                library.publicSources().forEach { source ->
                    runCatching { client.rssInventory(account, source.url) }
                        .onSuccess { inventory ->
                            changed = library.mergeInventory(inventory) > 0 || changed
                        }
                }
            }

            // Keep legacy compatibility above. The records channel is opt-in on
            // the server; Android only probes GET and never grants consent.
            WatchCloudRecordSync(
                userId = account.userId,
                scope = account.backendBaseUrl.trimEnd('/') + "|" + account.userId,
                store = recordStore,
                keyVersion = { keyManager.currentKeyVersion(account.userId) },
                keyForVersion = { version -> keyManager.accountKey(account.userId, version) },
                checkOwner = {
                    val current = accountStore.read()
                    check(current?.userId == account.userId && current.installId == account.installId &&
                        current.backendBaseUrl.trimEnd('/') == account.backendBaseUrl.trimEnd('/')) { "账号已切换" }
                },
                readPage = { cursor -> client.recordPage(account, cursor) },
                writeBatch = { body -> client.writeRecords(account, body) },
                streamLocal = { emit -> library.streamRecordValues(emit) },
                apply = { values -> library.applyRecordValues(values) },
                localProjection = { value -> library.recordProjection(value) }
            ).sync(writable = bootstrap.member.writable)

            if (!bootstrap.member.writable) {
                set(
                    if (fullTransfer) WatchCloudPhase.COMPLETE else WatchCloudPhase.WAITING_NETWORK,
                    withConflictSummary(if (fullTransfer) "只读云同步完成，未上传本机资料" else "小状态已同步；正文等待Wi‑Fi"),
                    bootstrap.member,
                    if (fullTransfer) System.currentTimeMillis() else _status.value.lastCompletedAt
                )
                return@withLock true
            }
            val currentKeyVersion = keyManager.currentKeyVersion(account.userId)
            val accountKey = keyManager.accountKey(account.userId, currentKeyVersion)
                ?: error("当前账号主密钥不存在")
            val previous = cache.latest(account.userId)?.let { (id, bytes) ->
                runCatching { codec.decryptManifest(accountKey, id, bytes) }.getOrNull()
                    ?.takeIf { it.keyVersion == currentKeyVersion }
            }
            val carried = if (fullTransfer) emptyList()
                else previous?.objects?.filterNot { it.name == RSS_STATE_OBJECT }.orEmpty()
            val readerPayload = if (fullTransfer) readerRepository?.let { reader ->
                WatchCloudReaderArchive.encode(reader.exportSnapshot(), { WatchCloudReaderArchive.existing(reader.resourceStore, it) })
            } else null
            val notesPayload = if (fullTransfer) notes?.export() else null
            val stateDigest = MessageDigest.getInstance("SHA-256").apply {
                update("keyVersion:$currentKeyVersion;state;".toByteArray())
            }
            val fullDigest = MessageDigest.getInstance("SHA-256").apply {
                update("keyVersion:$currentKeyVersion;full:$fullTransfer;".toByteArray())
                // Archive transport timestamps must not trigger another upload.
                update(watchCloudContentHash("{}".toByteArray(), null, currentKeyVersion,
                    readerPayload, notesPayload?.state, notesPayload?.archive).toByteArray())
            }
            fun digesting(output: OutputStream, vararg digests: MessageDigest) = object : OutputStream() {
                override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    digests.forEach { it.update(bytes, offset, length) }
                    output.write(bytes, offset, length)
                }
                override fun flush() = output.flush()
                override fun close() = output.flush()
            }
            val logicalObjects = buildList {
                add(WatchCloudStreamingObject(RSS_STATE_OBJECT) { output ->
                    library.exportStateTo(digesting(output, stateDigest, fullDigest))
                })
                if (fullTransfer) add(WatchCloudStreamingObject(RELAY_OBJECT) { output ->
                    library.exportTo(digesting(output, fullDigest))
                })
                readerPayload?.let { bytes -> add(WatchCloudStreamingObject(PRIVATE_LIBRARY_OBJECT) { it.write(bytes) }) }
                notesPayload?.let { bundle ->
                    add(WatchCloudStreamingObject(NOTES_STATE_OBJECT) { it.write(bundle.state) })
                    bundle.archive?.let { bytes -> add(WatchCloudStreamingObject(NOTES_ARCHIVE_OBJECT) { it.write(bytes) }) }
                }
            }
            set(WatchCloudPhase.UPLOADING, "正在准备手表状态", bootstrap.member)
            val prepared = codec.prepareStreamingObjects(accountKey, logicalObjects, previous) { hash, bytes ->
                cache.storeChunk(account.userId, hash, bytes)
            }
            debugProgress("export_done bytes=${prepared.sumOf { it.originalBytes }} chunks=${prepared.sumOf { it.chunks.size }}")
            val stateContentHash = stateDigest.digest().joinToString("") { "%02x".format(it) }
            val contentHash = fullDigest.digest().joinToString("") { "%02x".format(it) }
            val freshHeads = client.heads(account)
            val mergeNeeded = freshHeads.isNotEmpty() && freshHeads.none { candidate ->
                freshHeads.all { current ->
                    current.sourceDeviceId == candidate.sourceDeviceId ||
                        current.sequence <= (candidate.observedHeads[current.sourceDeviceId] ?: 0)
                }
            }
            if (bootstrap.member.writable && (stateStore.lastContentHash(fullTransfer) !=
                (if (fullTransfer) contentHash else stateContentHash) ||
                mergeNeeded || changed)
            ) {
                // Observe only heads actually restored above; a newly arriving head must be
                // merged on the next pass rather than being falsely advertised as consumed.
                val parentHeads = heads.associate { it.sourceDeviceId to it.id }
                val observedHeads = heads.associate { it.sourceDeviceId to it.sequence }
                val serverSequence = bootstrap.devices
                    .firstOrNull { it.deviceId == deviceId }?.lastSequence ?: 0
                val snapshot = codec.createFromPrepared(
                    accountKey = accountKey,
                    keyVersion = currentKeyVersion,
                    sourceDeviceId = deviceId,
                    sequence = stateStore.nextSequence(serverSequence),
                    objects = carried + prepared,
                    parentHeads = parentHeads,
                    observedHeads = observedHeads
                )
                set(WatchCloudPhase.UPLOADING, "正在上传手表状态", bootstrap.member)
                client.reserve(account, snapshot, 30).forEach { target ->
                    val bytes = if (target.kind == "manifest") {
                        snapshot.encryptedManifest
                    } else {
                        cache.chunk(account.userId, target.sha256)
                            ?: error("待上传中继块缺失")
                    }
                    client.upload(target, bytes)
                }
                client.complete(account, snapshot)
                debugProgress("snapshot_complete sequence=${snapshot.manifest.deviceSequence}")
                cache.storeManifest(
                    account.userId,
                    snapshot.manifest.snapshotId,
                    snapshot.encryptedManifest,
                    markAsLocalHead = true
                )
                stateStore.markApplied(deviceId, snapshot.manifest.deviceSequence, full = false)
                stateStore.markUploaded(stateContentHash, full = false)
                if (fullTransfer) {
                    stateStore.markApplied(deviceId, snapshot.manifest.deviceSequence, full = true)
                    stateStore.markUploaded(contentHash, full = true)
                }
            }
            set(
                if (fullTransfer) WatchCloudPhase.COMPLETE else WatchCloudPhase.WAITING_NETWORK,
                withConflictSummary(if (fullTransfer) "云同步完成" else "小状态已同步；正文等待Wi‑Fi"),
                bootstrap.member,
                if (fullTransfer) System.currentTimeMillis() else _status.value.lastCompletedAt
            )
            debugProgress("sync_complete full=$fullTransfer")
            true
        } catch (error: Exception) {
            if (error is CancellationException) {
                debugProgress("sync_canceled phase=${_status.value.phase}")
                set(WatchCloudPhase.IDLE, if (manual) "本次云同步已取消，未完成项目可稍后继续" else "自动云同步已停止，未完成项目可稍后继续")
                throw error
            }
            val http = Regex("HTTP ([0-9]{3})").find(error.message.orEmpty())?.groupValues?.get(1) ?: "none"
            val frame = error.stackTrace.firstOrNull { it.className.startsWith("com.lightningstudio.watchrss.") }
            debugProgress("sync_failed phase=${_status.value.phase} class=${error.javaClass.simpleName} http=$http at=${frame?.className}:${frame?.methodName}:${frame?.lineNumber}")
            set(WatchCloudPhase.ERROR, error.message ?: "云同步失败")
            false
        }
    }

    /** Diagnostic counts/methods only: never article URLs, plaintext, account IDs or token/error payloads. */
    private fun debugProgress(message: String) {
        if (BuildConfig.DEBUG) Log.i("WatchCloudSync", message)
    }

    private suspend fun withConflictSummary(message: String): String {
        val copies = noteRepository?.retainedConflictCopyCount() ?: 0
        return if (copies > 0) "$message；已保留 $copies 份冲突副本，请到备忘录检查" else message
    }

    private fun set(
        phase: WatchCloudPhase,
        message: String,
        member: WatchCloudMember? = null,
        completedAt: Long? = null
    ) {
        // 换账号后不沿用上一账号的空间与完成时间。
        val previous = _status.value.takeIf { it.accountId == statusAccountId }
            ?: WatchCloudStatus(accountId = statusAccountId)
        _status.value = WatchCloudStatus(
            phase,
            message,
            member?.usedBytes ?: previous.usedBytes,
            member?.quotaBytes ?: previous.quotaBytes,
            completedAt ?: previous.lastCompletedAt,
            statusAccountId
        )
    }

    private companion object {
        private const val RELAY_OBJECT = "library-sync.json"
        private const val RSS_STATE_OBJECT = "rss-state.json"
        private const val PRIVATE_LIBRARY_OBJECT = "private-library.wrss"
        private const val NOTES_STATE_OBJECT = "notes-state.json"
        private const val NOTES_ARCHIVE_OBJECT = "notes-assets.zip"
    }
}
