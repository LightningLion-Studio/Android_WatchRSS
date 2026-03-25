package com.lightningstudio.watchrss.phoneconnection

import com.lightningstudio.watchrss.data.AppContainer
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.util.AppLogger
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 手表端本地 HTTP 服务器，基于 NanoHTTPD 实现，用于与手机端 App 进行局域网通信。
 *
 * ## 设计思想
 *
 * 手表屏幕小、输入受限，很多操作（如输入 RSS URL、配置 LLM API Key）在手表上操作体验差。
 * 解决方案是在手表上开启一个临时 HTTP 服务器，手机 App 通过扫描手表屏幕上的二维码（二维码
 * 包含手表的局域网 IP + 端口）连接到该服务器，从而实现"手机辅助操作手表"的交互模式。
 *
 * 同理，手表上的收藏夹、稍后阅读等数据也可通过此通道同步到手机端。
 *
 * ## 能力（Ability）模型
 *
 * 每个服务器实例只开放其被创建时声明的"能力"集合对应的端点，以最小化暴露面。
 * 能力由 [PhoneConnectionAbility] 枚举定义，每个能力有一个固定的 UUID 作为线上标识（wireCode），
 * 手机端 App 通过 wireCode 来判断当前连接的服务器支持哪些功能，而非依赖端点路径的字符串匹配。
 *
 * ## 服务器类型与工厂方法
 *
 * 为避免调用方直接操作构造函数和枚举，所有创建入口均通过 companion object 中的工厂方法暴露：
 * - [createRemoteInputServer]    — 仅开放远程输入 RSS URL 端点（适用于添加订阅场景）
 * - [createSyncFavoritesServer]  — 仅开放获取收藏夹数据端点
 * - [createSyncWatchLaterServer] — 仅开放获取稍后阅读列表端点
 * - [createLlmConfigServer]      — 仅开放 LLM 配置的读写端点
 *
 * ## 典型调用流程
 *
 * ```
 * // 1. 创建并启动服务器
 * val server = LocalHttpServer.createRemoteInputServer(container) { url ->
 *     viewModel.addRssFeed(url)  // 手机端发来的 URL
 * }
 * server.start()
 *
 * // 2. 读取实际监听端口（端口 0 表示系统自动分配）
 * val port = server.listeningPort
 *
 * // 3. 将 "http://<手表局域网IP>:<port>" 生成二维码展示给用户，等待手机扫码
 *
 * // 4. 用完后停止服务器，释放端口
 * server.stop()
 * ```
 *
 * ## HTTP 端点一览
 *
 * | 路径                          | 能力限制               | 说明                                       |
 * |-------------------------------|------------------------|--------------------------------------------|
 * | GET  /                        | 无                     | 展示手机端 App 下载引导页（HTML）          |
 * | GET  /health                  | 无                     | 健康检查，返回 `{"status":"ok"}`           |
 * | GET  /getCurrentActivationAbility | 无               | 返回本次二维码对应的主能力及版本号         |
 * | GET  /getAbilities            | 无                     | 返回该服务器实例支持的所有能力列表         |
 * | POST /remoteEnterRSSURL       | REMOTE_INPUT           | 手机端推送 RSS URL 到手表                  |
 * | GET  /getFavorites            | SYNC_FAVORITES         | 手机端拉取手表收藏夹数据                   |
 * | GET  /getWatchlaterList       | SYNC_WATCH_LATER       | 手机端拉取手表稍后阅读数据                 |
 * | GET  /getLLMSummaryConfig     | LLM_SUMMARY_CONFIG     | 手机端读取手表当前 LLM 配置（API Key 脱敏）|
 * | POST /setLLMSummaryConfig     | LLM_SUMMARY_CONFIG     | 手机端写入 LLM 配置到手表                  |
 *
 * ## 版本协商
 *
 * `/getCurrentActivationAbility` 与 `/getAbilities` 均返回 `version` 字段（当前固定为 "0.0.1"）。
 * 手机端使用该版本号与自身版本比较：若手表端更新则提示手机端升级；若手机端更新则提示手表端升级。
 *
 * ## 线程模型
 *
 * NanoHTTPD 在独立的 I/O 线程上调用 [serve]。需要访问数据库或 DataStore 的处理函数
 * 通过 `runBlocking` 在调用线程上等待协程完成，因此不应在主线程调用 `start()`，且每次
 * 请求的处理时间可能较长（受数据库查询耗时影响），这在局域网短连接场景下是可接受的。
 */
class LocalHttpServer private constructor(
    port: Int,
    private val container: AppContainer,
    private val serverType: ServerType,
    private val preferredAbility: PhoneConnectionAbility? = null,
    private val onRemoteInput: ((String) -> Unit)? = null,
    private val onSyncComplete: (() -> Unit)? = null,
    private val onLlmConfigSaved: (() -> Unit)? = null
) : NanoHTTPD(port) {

    /**
     * 服务器类型，决定该实例对外暴露的能力集合。
     * 每个类型对应一个或多个 [PhoneConnectionAbility]，仅注册对应能力的端点会被路由处理，
     * 其余请求均返回 404，避免误操作或信息泄露。
     */
    enum class ServerType {
        /** 仅支持手机端远程输入 RSS URL */
        REMOTE_INPUT,
        /** 仅支持手机端同步手表收藏夹 */
        SYNC_FAVORITES,
        /** 仅支持手机端同步稍后阅读列表 */
        SYNC_WATCH_LATER,
        /** 仅支持手机端读写 LLM 摘要配置 */
        LLM_CONFIG
    }

    /** 本实例实际开放的能力集合，由 [serverType] 在构造时确定，运行期不可变。 */
    private val abilities: Set<PhoneConnectionAbility> = when (serverType) {
        ServerType.REMOTE_INPUT -> setOf(PhoneConnectionAbility.REMOTE_INPUT)
        ServerType.SYNC_FAVORITES -> setOf(PhoneConnectionAbility.SYNC_FAVORITES)
        ServerType.SYNC_WATCH_LATER -> setOf(PhoneConnectionAbility.SYNC_WATCH_LATER)
        ServerType.LLM_CONFIG -> setOf(PhoneConnectionAbility.LLM_SUMMARY_CONFIG)
    }

    /**
     * HTTP 请求路由入口，由 NanoHTTPD 在 I/O 线程上调用。
     *
     * 路由逻辑为"路径匹配 + 能力检查"双重守卫：路径不匹配或服务器不具备该能力时均返回 404，
     * 防止某个能力未被声明时仍被误访问。
     *
     * 版本协商说明：/getCurrentActivationAbility 和 /getAbilities 返回的 version 字段
     * 供手机端 App 做兼容性检查——手表端版本较新时提示手机端升级，反之提示手表端升级。
     */
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
            uri == "/getLLMSummaryConfig" && supports(PhoneConnectionAbility.LLM_SUMMARY_CONFIG) -> {
                handleGetLlmSummaryConfig()
            }
            uri == "/setLLMSummaryConfig" && supports(PhoneConnectionAbility.LLM_SUMMARY_CONFIG) -> {
                handleSetLlmSummaryConfig(session)
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

    private fun handleGetLlmSummaryConfig(): Response {
        return try {
            val settingsRepository = container.settingsRepository
            val apiKeyStore = container.llmApiKeyStore

            var provider = ""
            var model = ""
            var baseUrl = ""
            var enabled = false
            kotlinx.coroutines.runBlocking {
                provider = settingsRepository.llmProvider.first()
                model = settingsRepository.llmModel.first()
                baseUrl = settingsRepository.llmBaseUrl.first()
                enabled = settingsRepository.llmEnabled.first()
            }

            val hasConfig = provider.isNotEmpty() || apiKeyStore.hasApiKey()
            val dataObj = if (hasConfig) {
                val rawKey = apiKeyStore.getApiKey()
                val maskedKey = if (rawKey.length <= 4) "****" else "****${rawKey.takeLast(4)}"
                JSONObject().apply {
                    put("provider", provider)
                    put("apiKey", maskedKey)
                    put("model", model)
                    put("baseUrl", baseUrl)
                    put("enabled", enabled)
                }
            } else {
                JSONObject.NULL
            }

            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                JSONObject().apply {
                    put("success", true)
                    put("data", dataObj)
                }.toString()
            )
        } catch (e: Exception) {
            AppLogger.e("LocalHttpServer", "handleGetLlmSummaryConfig failed", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                JSONObject().apply {
                    put("success", false)
                    put("data", JSONObject.NULL)
                }.toString()
            )
        }
    }

    private fun handleSetLlmSummaryConfig(session: IHTTPSession): Response {
        return try {
            val params = mutableMapOf<String, String>()
            session.parseBody(params)
            val postData = params["postData"] ?: ""
            val json = JSONObject(postData)

            val provider = json.optString("provider", "")
            val apiKey = json.optString("apiKey", "")
            val model = json.optString("model", "")
            val baseUrl = json.optString("baseUrl", "")
            val enabled = json.optBoolean("enabled", false)

            if (provider.isEmpty()) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    JSONObject().apply {
                        put("success", false)
                        put("message", "provider 不能为空")
                    }.toString()
                )
            }
            if (apiKey.isEmpty()) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    JSONObject().apply {
                        put("success", false)
                        put("message", "apiKey 不能为空")
                    }.toString()
                )
            }

            container.llmApiKeyStore.setApiKey(apiKey)
            kotlinx.coroutines.runBlocking {
                container.settingsRepository.setLlmConfig(
                    provider = provider,
                    model = model,
                    baseUrl = baseUrl,
                    enabled = enabled
                )
            }
            onLlmConfigSaved?.invoke()

            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                JSONObject().apply {
                    put("success", true)
                    put("message", "配置已保存")
                }.toString()
            )
        } catch (e: Exception) {
            AppLogger.e("LocalHttpServer", "handleSetLlmSummaryConfig failed", e)
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
        /**
         * 端口传入 0 让系统自动选择可用端口，避免端口冲突。
         * 启动后通过 [NanoHTTPD.listeningPort] 读取实际分配的端口，用于生成二维码。
         */
        private const val DEFAULT_PORT = 0

        /**
         * 创建"远程输入 RSS URL"专用服务器。
         *
         * 适用场景：在手表"添加订阅"界面，用户扫码后在手机端输入 URL，手机 App 向
         * `/remoteEnterRSSURL` POST 包含 `{"url":"..."}` 的 JSON 正文，服务器收到后
         * 调用 [onRemoteInput] 回调将 URL 传递给业务层。
         *
         * @param onRemoteInput 手机端成功推送 URL 后的回调，参数为接收到的 URL 字符串，
         *                      在 NanoHTTPD I/O 线程上被调用，如需更新 UI 请切换到主线程。
         */
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

        /**
         * 创建"同步收藏夹"专用服务器。
         *
         * 手机 App 通过 GET `/getFavorites` 拉取手表上所有标记为 [SaveType.FAVORITE] 的条目，
         * 返回格式由 [SavedItemsSyncPayload.buildLinksOnly] 定义（仅含链接信息，不含正文）。
         *
         * @param onSyncComplete 手机端成功拉取数据后的回调，可用于关闭等待界面或更新状态。
         */
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

        /**
         * 创建"同步稍后阅读"专用服务器。
         *
         * 与 [createSyncFavoritesServer] 逻辑相同，区别仅在于拉取的是 [SaveType.WATCH_LATER] 列表。
         *
         * @param onSyncComplete 手机端成功拉取数据后的回调。
         */
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

        /**
         * 创建"LLM 配置"专用服务器。
         *
         * 手表屏幕无法便捷输入 API Key 等长字符串，因此通过此服务器让手机端代劳：
         * - GET  `/getLLMSummaryConfig`：手机端读取当前配置，API Key 以 `****xxxx` 形式脱敏返回。
         * - POST `/setLLMSummaryConfig`：手机端将完整配置（含明文 API Key）写入手表，
         *   Key 存储在 [com.lightningstudio.watchrss.data.settings.LlmApiKeyStore]，
         *   其余字段持久化到 DataStore。
         *
         * 注意：该服务器不提供 [onRemoteInput] / [onSyncComplete] 回调，配置写入是同步完成的。
         */
        fun createLlmConfigServer(
            container: AppContainer,
            onLlmConfigSaved: (() -> Unit)? = null
        ): LocalHttpServer {
            return LocalHttpServer(
                DEFAULT_PORT,
                container,
                ServerType.LLM_CONFIG,
                preferredAbility = PhoneConnectionAbility.LLM_SUMMARY_CONFIG,
                onLlmConfigSaved = onLlmConfigSaved
            )
        }
    }
}
