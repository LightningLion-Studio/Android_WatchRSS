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
            uri == "/" -> {
                handleRootPage()
            }
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

    private fun handleRootPage(): Response {
        val html = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>腕上RSS - 手机版下载</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 20px;
                    }
                    .container {
                        background: white;
                        border-radius: 16px;
                        padding: 40px 30px;
                        max-width: 500px;
                        width: 100%;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                    }
                    h1 {
                        color: #333;
                        font-size: 24px;
                        margin-bottom: 30px;
                        text-align: center;
                    }
                    .download-list {
                        list-style: none;
                    }
                    .download-item {
                        background: #f8f9fa;
                        border-radius: 12px;
                        padding: 20px;
                        margin-bottom: 15px;
                        cursor: pointer;
                        transition: all 0.3s ease;
                        border: 2px solid transparent;
                    }
                    .download-item:hover {
                        background: #e9ecef;
                        border-color: #667eea;
                        transform: translateY(-2px);
                    }
                    .download-item.disabled {
                        cursor: not-allowed;
                        opacity: 0.6;
                    }
                    .download-item.disabled:hover {
                        transform: none;
                        border-color: transparent;
                    }
                    .platform {
                        font-weight: 600;
                        color: #667eea;
                        font-size: 18px;
                        margin-bottom: 8px;
                    }
                    .url {
                        color: #666;
                        font-size: 14px;
                        word-break: break-all;
                    }
                    .note {
                        color: #999;
                        font-size: 13px;
                        margin-top: 5px;
                    }
                    .toast {
                        position: fixed;
                        top: 20px;
                        left: 50%;
                        transform: translateX(-50%);
                        background: #28a745;
                        color: white;
                        padding: 12px 24px;
                        border-radius: 8px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                        opacity: 0;
                        transition: opacity 0.3s ease;
                        z-index: 1000;
                    }
                    .toast.show {
                        opacity: 1;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>请使用腕上RSS手机版App扫码</h1>
                    <ul class="download-list">
                        <li class="download-item" onclick="copyToClipboard('https://github.com/LightningLion-Studio/Android_WatchRSS_Phone/releases')">
                            <div class="platform">安卓手机版本</div>
                            <div class="url">https://github.com/LightningLion-Studio/Android_WatchRSS_Phone/releases</div>
                            <div class="note">点击复制下载地址</div>
                        </li>
                        <li class="download-item disabled">
                            <div class="platform">鸿蒙手机版本</div>
                            <div class="url">暂未提供（请使用卓易通使用）</div>
                        </li>
                        <li class="download-item disabled">
                            <div class="platform">苹果手机版本</div>
                            <div class="url">暂未提供（请使用安卓手机来使用）</div>
                        </li>
                    </ul>
                </div>
                <div class="toast" id="toast">已复制到剪贴板</div>
                <script>
                    function copyToClipboard(text) {
                        if (navigator.clipboard && navigator.clipboard.writeText) {
                            navigator.clipboard.writeText(text).then(function() {
                                showToast();
                            }).catch(function(err) {
                                fallbackCopy(text);
                            });
                        } else {
                            fallbackCopy(text);
                        }
                    }

                    function fallbackCopy(text) {
                        var textArea = document.createElement("textarea");
                        textArea.value = text;
                        textArea.style.position = "fixed";
                        textArea.style.left = "-999999px";
                        document.body.appendChild(textArea);
                        textArea.focus();
                        textArea.select();
                        try {
                            document.execCommand('copy');
                            showToast();
                        } catch (err) {
                            alert('复制失败，请手动复制');
                        }
                        document.body.removeChild(textArea);
                    }

                    function showToast() {
                        var toast = document.getElementById('toast');
                        toast.classList.add('show');
                        setTimeout(function() {
                            toast.classList.remove('show');
                        }, 2000);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(
            Response.Status.OK,
            "text/html; charset=utf-8",
            html
        )
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
