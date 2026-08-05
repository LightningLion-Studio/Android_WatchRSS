package com.lightningstudio.watchrss.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.lightningstudio.watchrss.data.account.AccountStore
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class WatchCloudPhase {
    IDLE,
    REGISTERING,
    WAITING_APPROVAL,
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
    val lastCompletedAt: Long = 0
)

class WatchCloudSyncService(
    context: Context,
    private val accountStore: AccountStore,
    repository: com.lightningstudio.watchrss.data.rss.RssRepository,
    private val client: WatchCloudClient = WatchCloudClient(),
    private val codec: WatchCloudCodec = WatchCloudCodec()
) {
    private val appContext = context.applicationContext
    private val deviceId = WatchDeviceIdentity(context).deviceId
    private val keyManager = WatchCloudKeyManager(context)
    private val cache = WatchCloudCache(context)
    private val stateStore = WatchCloudStateStore(context)
    private val library = WatchCloudLibraryAdapter(repository, deviceId)
    private val mutex = Mutex()
    private val _status = MutableStateFlow(WatchCloudStatus())
    val status: StateFlow<WatchCloudStatus> = _status

    suspend fun syncNow(manual: Boolean = false): Boolean = mutex.withLock {
        try {
            val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
            val fullTransfer = manual || !connectivity.isActiveNetworkMetered
            val account = accountStore.read() ?: return@withLock false
            require(account.entitlement.plan == "member" && account.entitlement.active) {
                "当前账号没有云中继会员权限"
            }
            set(WatchCloudPhase.REGISTERING, "正在登记手表")
            val publicKey = keyManager.publicKeySpki(account.userId, deviceId)
            client.register(
                account,
                deviceId,
                publicKey,
                "手表 · ${Build.MODEL}",
                keyManager.currentKeyVersion(account.userId)
            )
            var bootstrap = client.bootstrap(account)
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
            client.register(
                account,
                deviceId,
                publicKey,
                "手表 · ${Build.MODEL}",
                keyManager.currentKeyVersion(account.userId)
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
                    it.name == RSS_STATE_OBJECT || (fullTransfer && it.name == RELAY_OBJECT)
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
                    val payload = codec.restore(
                        accountKey,
                        manifest.copy(objects = selected)
                    ) { hash -> cache.chunk(account.userId, hash) ?: error("中继缓存缺失") }
                    if (fullTransfer) {
                        payload[RELAY_OBJECT]?.let {
                            changed = library.restore(it, head.sourceDeviceId) > 0 || changed
                        }
                    }
                    payload[RSS_STATE_OBJECT]?.let {
                        changed = library.restoreState(it, head.sourceDeviceId) > 0 || changed
                    }
                }
                cache.storeManifest(
                    account.userId,
                    head.id,
                    encryptedManifest,
                    markAsLocalHead = false
                )
                if (fullTransfer) {
                    stateStore.markApplied(head.sourceDeviceId, head.sequence, full = true)
                    stateStore.markApplied(head.sourceDeviceId, head.sequence, full = false)
                    client.acknowledge(account, head.id, deviceId)
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

            val currentKeyVersion = keyManager.currentKeyVersion(account.userId)
            val statePayload = library.exportState()
            val stateContentHash = WatchCloudCodec.sha256(
                "keyVersion:$currentKeyVersion;state;".toByteArray() + statePayload
            )
            val relayPayload = if (fullTransfer) library.export() else null
            val contentHash = WatchCloudCodec.sha256(
                "keyVersion:$currentKeyVersion;full:$fullTransfer;".toByteArray() +
                    statePayload +
                    (relayPayload ?: ByteArray(0))
            )
            val freshHeads = client.heads(account)
            val mergeNeeded = freshHeads.isNotEmpty() && freshHeads.none { candidate ->
                freshHeads.all { current ->
                    current.sourceDeviceId == candidate.sourceDeviceId ||
                        current.sequence <= (candidate.observedHeads[current.sourceDeviceId] ?: 0)
                }
            }
            if (stateStore.lastContentHash(fullTransfer) !=
                (if (fullTransfer) contentHash else stateContentHash) ||
                mergeNeeded ||
                changed
            ) {
                val accountKey = keyManager.accountKey(account.userId, currentKeyVersion)
                    ?: error("当前账号主密钥不存在")
                val previous = cache.latest(account.userId)?.let { (id, bytes) ->
                    runCatching { codec.decryptManifest(accountKey, id, bytes) }
                        .getOrNull()
                        ?.takeIf { it.keyVersion == currentKeyVersion }
                }
                val carried = if (fullTransfer) {
                    emptyList()
                } else {
                    listOfNotNull(previous?.objects?.firstOrNull { it.name == RELAY_OBJECT })
                }
                val logicalObjects = buildList {
                    add(WatchCloudLogicalObject(RSS_STATE_OBJECT, statePayload))
                    relayPayload?.let { add(WatchCloudLogicalObject(RELAY_OBJECT, it)) }
                }
                val parentHeads = freshHeads.associate { it.sourceDeviceId to it.id }
                val observedHeads = freshHeads.associate { it.sourceDeviceId to it.sequence }
                val serverSequence = bootstrap.devices
                    .firstOrNull { it.deviceId == deviceId }?.lastSequence ?: 0
                val snapshot = codec.create(
                    accountKey = accountKey,
                    keyVersion = currentKeyVersion,
                    sourceDeviceId = deviceId,
                    sequence = stateStore.nextSequence(serverSequence),
                    objects = logicalObjects,
                    parentHeads = parentHeads,
                    observedHeads = observedHeads,
                    previous = previous,
                    carried = carried
                )
                snapshot.newChunks.forEach { (hash, bytes) ->
                    cache.storeChunk(account.userId, hash, bytes)
                }
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
                WatchCloudPhase.COMPLETE,
                if (fullTransfer) "云同步完成" else "小状态已同步；正文等待Wi‑Fi",
                bootstrap.member,
                System.currentTimeMillis()
            )
            true
        } catch (error: Exception) {
            set(WatchCloudPhase.ERROR, error.message ?: "云同步失败")
            false
        }
    }

    private fun set(
        phase: WatchCloudPhase,
        message: String,
        member: WatchCloudMember? = null,
        completedAt: Long = _status.value.lastCompletedAt
    ) {
        _status.value = WatchCloudStatus(
            phase,
            message,
            member?.usedBytes ?: _status.value.usedBytes,
            member?.quotaBytes ?: _status.value.quotaBytes,
            completedAt
        )
    }

    private companion object {
        private const val RELAY_OBJECT = "library-sync.json"
        private const val RSS_STATE_OBJECT = "rss-state.json"
    }
}
