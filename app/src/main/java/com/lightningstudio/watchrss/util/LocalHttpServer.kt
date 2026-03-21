package com.lightningstudio.watchrss.util

import com.lightningstudio.watchrss.data.AppContainer
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.phoneconnection.SavedItemsSyncPayload
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class LocalHttpServer private constructor(
    port: Int,
    private val container: AppContainer,
    private val serverType: ServerType,
    private val preferredAbility: PhoneConnectionAbility? = null,
    private val onRemoteInput: ((String) -> Unit)? = null,
    private val onSyncComplete: (() -> Unit)? = null
) : NanoHTTPD(port) {

    enum class ServerType {
        REMOTE_INPUT,
        SYNC_FAVORITES,
        SYNC_WATCH_LATER,
        CONNECTION_HUB
    }

    private val abilities: Set<PhoneConnectionAbility> = when (serverType) {
        ServerType.REMOTE_INPUT -> setOf(PhoneConnectionAbility.REMOTE_INPUT)
        ServerType.SYNC_FAVORITES -> setOf(PhoneConnectionAbility.SYNC_FAVORITES)
        ServerType.SYNC_WATCH_LATER -> setOf(PhoneConnectionAbility.SYNC_WATCH_LATER)
        ServerType.CONNECTION_HUB -> PhoneConnectionAbility.orderedValues.toSet()
    }

    // 手机端会使用版本号来检查是否匹配手表端App，如果手表端更加新，手机端会提示需要升级，如果手机端更加新，手机端会提示需要升级手表端。
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri == "/health" -> {
                handleHealth()
            }
            uri == "/getCurrentActivationAbility" -> {
                handleGetCurrentActivationAbility()
            }
            uri == "/getAbilities" -> {
                handleGetAbilities()
            }
            uri == "/remoteEnterRSSURL" && supports(PhoneConnectionAbility.REMOTE_INPUT) -> {
                handleRemoteInput(session)
            }
            uri == "/getFavorites" && supports(PhoneConnectionAbility.SYNC_FAVORITES) -> {
                handleGetFavorites()
            }
            uri == "/getWatchlaterList" && supports(PhoneConnectionAbility.SYNC_WATCH_LATER) -> {
                handleGetWatchLater()
            }
            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }
        }
    }

    private fun supports(ability: PhoneConnectionAbility): Boolean = ability in abilities

    private fun handleHealth(): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            JSONObject().apply {
                put("status", "ok")
            }.toString()
        )
    }

    private fun handleGetCurrentActivationAbility(): Response {
        val current = preferredAbility ?: abilities.singleOrNull()
        val (code, name) = if (current != null) {
            current.wireCode to current.displayName
        } else {
            "watchrss-connection-hub" to "连接手机"
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            JSONObject().apply {
                put("code", code)
                put("name", name)
                put("version", "0.0.1")
            }.toString()
        )
    }

    private fun handleGetAbilities(): Response {
        val abilities = JSONArray().apply {
            PhoneConnectionAbility.orderedValues
                .filter { it in this@LocalHttpServer.abilities }
                .forEach { ability ->
                    put(JSONObject().apply {
                        put("code", ability.wireCode)
                        put("name", ability.displayName)
                        put("version", "0.0.1")
                    })
                }
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            JSONObject().apply {
                put("status", "ok")
                put("abilities", abilities)
            }.toString()
        )
    }

    private fun handleRemoteInput(session: IHTTPSession): Response {
        return try {
            val params = mutableMapOf<String, String>()
            session.parseBody(params)
            val postData = params["postData"] ?: ""

            // Parse JSON request body
            val url = if (postData.isNotBlank()) {
                try {
                    val jsonObject = JSONObject(postData)
                    jsonObject.optString("url", "")
                } catch (e: Exception) {
                    // Fallback: treat postData as plain URL
                    postData
                }
            } else {
                // Fallback: check URL parameter
                session.parameters["url"]?.firstOrNull() ?: ""
            }

            if (url.isNotBlank()) {
                onRemoteInput?.invoke(url)
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    JSONObject().apply {
                        put("success", true)
                        put("message", "操作成功")
                    }.toString()
                )
            } else {
                newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    JSONObject().apply {
                        put("success", false)
                        put("message", "URL is required")
                    }.toString()
                )
            }
        } catch (e: Exception) {
            AppLogger.e("LocalHttpServer", "handleRemoteInput failed", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                JSONObject().apply {
                    put("success", false)
                    put("message", e.message ?: "Unknown error")
                }.toString()
            )
        }
    }

    private fun handleGetFavorites(): Response {
        return try {
            val jsonArray = JSONArray()
            val scope = CoroutineScope(Dispatchers.IO)
            val job = scope.launch {
                val items = container.rssRepository.observeSavedItems(SaveType.FAVORITE).first()
                val payload = SavedItemsSyncPayload.buildLinksOnly(items)
                for (index in 0 until payload.length()) {
                    jsonArray.put(payload.getJSONObject(index))
                }
            }

            // Wait for completion
            kotlinx.coroutines.runBlocking { job.join() }

            // Notify sync complete
            onSyncComplete?.invoke()

            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                JSONObject().apply {
                    put("success", true)
                    put("data", jsonArray)
                }.toString()
            )
        } catch (e: Exception) {
            AppLogger.e("LocalHttpServer", "handleGetFavorites failed", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                JSONObject().apply {
                    put("success", false)
                    put("message", e.message ?: "Unknown error")
                }.toString()
            )
        }
    }

    private fun handleGetWatchLater(): Response {
        return try {
            val jsonArray = JSONArray()
            val scope = CoroutineScope(Dispatchers.IO)
            val job = scope.launch {
                val items = container.rssRepository.observeSavedItems(SaveType.WATCH_LATER).first()
                val payload = SavedItemsSyncPayload.buildLinksOnly(items)
                for (index in 0 until payload.length()) {
                    jsonArray.put(payload.getJSONObject(index))
                }
            }

            // Wait for completion
            kotlinx.coroutines.runBlocking { job.join() }

            // Notify sync complete
            onSyncComplete?.invoke()

            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                JSONObject().apply {
                    put("success", true)
                    put("data", jsonArray)
                }.toString()
            )
        } catch (e: Exception) {
            AppLogger.e("LocalHttpServer", "handleGetWatchLater failed", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                JSONObject().apply {
                    put("success", false)
                    put("message", e.message ?: "Unknown error")
                }.toString()
            )
        }
    }

    companion object {
        private const val DEFAULT_PORT = 0 // 0 means auto-select available port

        fun createRemoteInputServer(
            container: AppContainer,
            onRemoteInput: (String) -> Unit
        ): LocalHttpServer {
            return LocalHttpServer(
                DEFAULT_PORT,
                container,
                ServerType.REMOTE_INPUT,
                preferredAbility = PhoneConnectionAbility.REMOTE_INPUT,
                onRemoteInput = onRemoteInput
            )
        }

        fun createSyncFavoritesServer(
            container: AppContainer,
            onSyncComplete: () -> Unit
        ): LocalHttpServer {
            return LocalHttpServer(
                DEFAULT_PORT,
                container,
                ServerType.SYNC_FAVORITES,
                preferredAbility = PhoneConnectionAbility.SYNC_FAVORITES,
                onSyncComplete = onSyncComplete
            )
        }

        fun createSyncWatchLaterServer(
            container: AppContainer,
            onSyncComplete: () -> Unit
        ): LocalHttpServer {
            return LocalHttpServer(
                DEFAULT_PORT,
                container,
                ServerType.SYNC_WATCH_LATER,
                preferredAbility = PhoneConnectionAbility.SYNC_WATCH_LATER,
                onSyncComplete = onSyncComplete
            )
        }

        fun createConnectionHubServer(
            container: AppContainer,
            preferredAbility: PhoneConnectionAbility? = null,
            onRemoteInput: ((String) -> Unit)? = null,
            onSyncComplete: (() -> Unit)? = null
        ): LocalHttpServer {
            return LocalHttpServer(
                DEFAULT_PORT,
                container,
                ServerType.CONNECTION_HUB,
                preferredAbility = preferredAbility,
                onRemoteInput = onRemoteInput,
                onSyncComplete = onSyncComplete
            )
        }
    }
}
