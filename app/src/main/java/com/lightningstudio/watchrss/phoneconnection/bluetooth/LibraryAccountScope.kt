package com.lightningstudio.watchrss.phoneconnection.bluetooth

import org.json.JSONObject

/** An account tag selects a library; pairing/authentication remains the transport's responsibility. */
internal object LibraryAccountScope {
    private val dataActions = setOf(
        BluetoothSyncProtocol.ACTION_SYNC_LIBRARY,
        BluetoothSyncProtocol.ACTION_CLEAR_LIBRARY,
        BluetoothSyncProtocol.ACTION_REMOTE_INPUT,
        BluetoothSyncProtocol.ACTION_PULL_SAVED_ITEMS,
        BluetoothSyncProtocol.ACTION_SYNC_NOTES,
        BluetoothSyncProtocol.ACTION_SYNC_NOTE_ASSET,
        BluetoothSyncProtocol.ACTION_SYNC_READER,
        BluetoothSyncProtocol.ACTION_PREVIEW_READER,
        BluetoothSyncProtocol.ACTION_SYNC_LLM_TOKEN_USAGE
    )

    fun matchesAccount(request: JSONObject, identity: Pair<String, String>?): Boolean =
        identity != null && request.optString("libraryAccountUserId") == identity.second &&
            request.optString("libraryAccountBackend").trimEnd('/') == identity.first

    fun requireAutomaticAccount(current: Pair<String, String>?, next: Pair<String, String>) {
        require(current == next) { "手表账号已变化，请手动同步确认账号" }
    }

    fun requireMatching(
        request: JSONObject,
        identity: Pair<String, String>?,
        confirmedSessionIdentity: Pair<String, String>? = null
    ) {
        val action = request.optString("action")
        if (action !in dataActions) return
        if (action == BluetoothSyncProtocol.ACTION_SYNC_LIBRARY && request.optString("phase") == LibrarySyncPayload.PHASE_PROBE) return
        if (!request.has("libraryAccountUserId")) {
            require(identity == null || confirmedSessionIdentity == identity) { "请重新连接并同步账号后再同步资料" }
            return
        }
        val userId = request.optString("libraryAccountUserId")
        val backend = request.optString("libraryAccountBackend").trimEnd('/')
        require(userId == identity?.second.orEmpty() && (identity == null || backend == identity.first)) {
            "手机与当前同步会话的资料账号不一致，请先同步账号或重新连接"
        }
    }
}

/** Legacy phones identify their library once through a successful account sync on this transport. */
internal class LibraryAccountSession {
    private var confirmedIdentity: Pair<String, String>? = null

    fun reset() { confirmedIdentity = null }

    fun confirm(identity: Pair<String, String>) { confirmedIdentity = identity }

    fun requireMatching(request: JSONObject, identity: Pair<String, String>?) {
        LibraryAccountScope.requireMatching(request, identity, confirmedIdentity)
    }
}
