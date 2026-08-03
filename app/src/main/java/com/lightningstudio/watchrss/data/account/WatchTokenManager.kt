package com.lightningstudio.watchrss.data.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Owns access-token freshness and performs single-flight refresh-token rotation. */
class WatchTokenManager(
    private val store: WatchAccountStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val refreshMutex = Mutex()

    suspend fun freshAccessToken(forceRefresh: Boolean = false): String = refreshMutex.withLock {
        val state = store.read() ?: error("手表尚未绑定腕上RSS账号")
        val refreshEarlyAt = System.currentTimeMillis() + REFRESH_SKEW_MILLIS
        if (!forceRefresh && state.tokenExpiresAtMillis > refreshEarlyAt) {
            return@withLock state.watchDeviceToken
        }
        require(state.watchRefreshToken.isNotBlank()) { "登录已过期，请用手机重新同步账号" }
        require(!state.isRefreshTokenExpired) { "登录已过期，请用手机重新同步账号" }
        refresh(state).watchDeviceToken
    }

    private suspend fun refresh(state: WatchAccountState): WatchAccountState =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("refreshToken", state.watchRefreshToken).toString()
            val request = Request.Builder()
                .url(state.backendBaseUrl.trimEnd('/') + REFRESH_PATH)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("手表令牌刷新失败：HTTP ${response.code}")
                }
                val json = JSONObject(raw)
                val updated = state.copy(
                    watchDeviceToken = json.getString("watchAccessToken"),
                    tokenExpiresAtMillis = json.getLong("accessTokenExpiresAt"),
                    watchRefreshToken = json.getString("watchRefreshToken"),
                    refreshTokenExpiresAtMillis = json.getLong("refreshTokenExpiresAt"),
                    updatedAtMillis = System.currentTimeMillis()
                )
                store.save(updated)
                updated
            }
        }

    companion object {
        private const val REFRESH_PATH = "/functions/v1/account/watch-token/refresh"
        private const val REFRESH_SKEW_MILLIS = 60_000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
