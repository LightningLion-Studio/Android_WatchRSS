package com.lightningstudio.watchrss.util

import com.lightningstudio.watchrss.data.AppContainer
import com.lightningstudio.watchrss.data.rss.SaveType
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
    private val onRemoteInput: ((String) -> Unit)? = null,
    private val onSyncComplete: (() -> Unit)? = null
) : NanoHTTPD(port) {

    enum class ServerType {
        REMOTE_INPUT,
        SYNC_FAVORITES,
        SYNC_WATCH_LATER
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
            uri == "/remoteEnterRSSURL" && serverType == ServerType.REMOTE_INPUT -> {
                handleRemoteInput(session)
            }
            uri == "/getFavorites" && serverType == ServerType.SYNC_FAVORITES -> {
                handleGetFavorites()
            }
            uri == "/getWatchlaterList" && serverType == ServerType.SYNC_WATCH_LATER -> {
                handleGetWatchLater()
            }
            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }
        }
    }

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
        val (code, name) = when (serverType) {
            ServerType.REMOTE_INPUT -> "dc40517c-a09c-419c-8c4d-d3883258992e" to "RSS订阅输入"
            ServerType.SYNC_FAVORITES -> "c4bf141f-b0de-46f7-a661-0a3ad0716bce" to "收藏夹"
            ServerType.SYNC_WATCH_LATER -> "f1aa43bd-0fe3-4771-ae6b-d4799ecf84b5" to "稍后阅读"
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
            put(JSONObject().apply {
                put("code", "dc40517c-a09c-419c-8c4d-d3883258992e")
                put("name", "RSS订阅输入")
                put("version", "0.0.1")
            })
            put(JSONObject().apply {
                put("code", "c4bf141f-b0de-46f7-a661-0a3ad0716bce")
                put("name", "收藏夹")
                put("version", "0.0.1")
            })
            put(JSONObject().apply {
                put("code", "f1aa43bd-0fe3-4771-ae6b-d4799ecf84b5")
                put("name", "稍后阅读")
                put("version", "0.0.1")
            })
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
                items.forEach { savedItem ->
                    jsonArray.put(JSONObject().apply {
                        put("id", savedItem.item.id)
                        put("title", savedItem.item.title)
                        put("link", savedItem.item.link ?: "")
                        put("summary", savedItem.item.summary ?: "")
                        put("channelTitle", savedItem.channelTitle)
                        put("pubDate", savedItem.item.pubDate ?: "")
                    })
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
                items.forEach { savedItem ->
                    jsonArray.put(JSONObject().apply {
                        put("id", savedItem.item.id)
                        put("title", savedItem.item.title)
                        put("link", savedItem.item.link ?: "")
                        put("summary", savedItem.item.summary ?: "")
                        put("channelTitle", savedItem.channelTitle)
                        put("pubDate", savedItem.item.pubDate ?: "")
                    })
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
                onSyncComplete = onSyncComplete
            )
        }
    }
}
