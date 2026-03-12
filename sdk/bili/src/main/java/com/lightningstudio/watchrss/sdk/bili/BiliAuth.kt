package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BiliAuth(private val client: BiliClient) {
    suspend fun applyCookies(cookies: Map<String, String>) {
        if (cookies.isEmpty()) return
        updateAccountStore(null, null, null, cookies)
    }

    suspend fun requestTvQrCode(): TvQrCode? {
        val url = "${client.config.passportBaseUrl}/x/passport-tv-login/qrcode/auth_code"
        val ts = (System.currentTimeMillis() / 1000).toString()
        val params = mapOf(
            "appkey" to client.config.tvAppKey,
            "local_id" to client.config.tvLocalId,
            "ts" to ts,
            "mobi_app" to client.config.tvMobiApp
        )
        val signed = BiliSigners.signApp(params, client.config.tvAppKey, client.config.tvAppSec)
        val response = client.httpClient.postForm(url, signed, includeCookies = false)
        if (response.code != 200) return null
        val json = biliJson.parseToJsonElement(response.body).jsonObject
        if (json.intOrNull("code") != 0) return null
        val data = json.objOrNull("data") ?: return null
        val authCode = data.stringOrNull("auth_code") ?: return null
        val qrUrl = data.stringOrNull("url") ?: return null
        return TvQrCode(authCode, qrUrl)
    }

    suspend fun pollTvQrCode(authCode: String): QrPollResult {
        val url = "${client.config.passportBaseUrl}/x/passport-tv-login/qrcode/poll"
        val ts = (System.currentTimeMillis() / 1000).toString()
        val params = mapOf(
            "appkey" to client.config.tvAppKey,
            "auth_code" to authCode,
            "local_id" to client.config.tvLocalId,
            "ts" to ts
        )
        val signed = BiliSigners.signApp(params, client.config.tvAppKey, client.config.tvAppSec)
        val response = client.httpClient.postForm(url, signed, includeCookies = false)
        if (response.code != 200) {
            return QrPollResult(QrPollStatus.ERROR, response.code, "http_${response.code}")
        }
        val json = biliJson.parseToJsonElement(response.body).jsonObject
        val code = json.intOrNull("code") ?: -1
        val message = json.stringOrNull("message")
        val status = when (code) {
            0 -> QrPollStatus.SUCCESS
            86038 -> QrPollStatus.EXPIRED
            86039, 86090 -> QrPollStatus.SCANNED
            else -> QrPollStatus.ERROR
        }
        if (status != QrPollStatus.SUCCESS) {
            return QrPollResult(status, code, message)
        }
        val data = json.objOrNull("data")
        val accessToken = data?.stringOrNull("access_token")
        val refreshToken = data?.stringOrNull("refresh_token")
        val cookies = extractTvCookies(data)
        updateAccountStore(accessToken, null, refreshToken, cookies)
        return QrPollResult(status, code, message, accessToken, refreshToken, cookies)
    }

    suspend fun requestWebQrCode(): WebQrCode? {
        val url = "${client.config.passportBaseUrl}/x/passport-login/web/qrcode/generate"
        val response = client.httpClient.get(url, includeCookies = false)
        if (response.code != 200) return null
        val json = biliJson.parseToJsonElement(response.body).jsonObject
        if (json.intOrNull("code") != 0) return null
        val data = json.objOrNull("data") ?: return null
        val qrUrl = data.stringOrNull("url") ?: return null
        val qrKey = data.stringOrNull("qrcode_key") ?: return null
        return WebQrCode(qrKey, qrUrl)
    }

    suspend fun pollWebQrCode(qrKey: String): QrPollResult {
        val url = "${client.config.passportBaseUrl}/x/passport-login/web/qrcode/poll"
        val response = client.httpClient.get(
            url,
            params = mapOf("qrcode_key" to qrKey),
            includeCookies = false
        )
        if (response.code != 200) {
            return QrPollResult(QrPollStatus.ERROR, response.code, "http_${response.code}")
        }
        val json = biliJson.parseToJsonElement(response.body).jsonObject
        if (json.intOrNull("code") != 0) {
            return QrPollResult(QrPollStatus.ERROR, json.intOrNull("code") ?: -1, json.stringOrNull("message"))
        }
        val data = json.objOrNull("data") ?: return QrPollResult(QrPollStatus.ERROR, -1, "empty_data")
        val statusCode = data.intOrNull("code") ?: -1
        val status = when (statusCode) {
            0 -> QrPollStatus.SUCCESS
            86038 -> QrPollStatus.EXPIRED
            86090 -> QrPollStatus.SCANNED
            86101 -> QrPollStatus.PENDING
            else -> QrPollStatus.ERROR
        }
        if (status != QrPollStatus.SUCCESS) {
            val statusMessage = data.stringOrNull("message")
            return QrPollResult(status, statusCode, statusMessage)
        }
        val refreshToken = data.stringOrNull("refresh_token")
        val cookies = BiliCookies.parseSetCookieHeaders(response.headers)
        updateAccountStore(null, refreshToken, null, cookies)
        return QrPollResult(status, statusCode, data.stringOrNull("message"), null, refreshToken, cookies)
    }

    private suspend fun updateAccountStore(
        accessToken: String?,
        refreshToken: String?,
        appRefreshToken: String?,
        cookies: Map<String, String>
    ) {
        client.accountStore?.update { current ->
            current.copy(
                cookies = BiliCookies.merge(current.cookies, cookies),
                accessToken = accessToken ?: current.accessToken,
                refreshToken = refreshToken ?: current.refreshToken,
                appRefreshToken = appRefreshToken ?: current.appRefreshToken,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
        if (cookies.isNotEmpty()) {
            client.identity.fetchBuvid()
            client.identity.fetchWbiKeys()
            val csrf = cookies["bili_jct"] ?: client.accountStore?.read()?.csrfToken()
            if (!csrf.isNullOrBlank()) {
                client.identity.fetchWebTicket(csrf)
            }
        }
    }

    private fun extractTvCookies(data: kotlinx.serialization.json.JsonObject?): Map<String, String> {
        val cookiesArray = data
            ?.objOrNull("cookie_info")
            ?.arrayOrNull("cookies")
            ?: return emptyMap()
        return cookiesArray.toCookieMap()
    }

    private fun JsonArray.toCookieMap(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (element in this) {
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content
            val value = obj["value"]?.jsonPrimitive?.content
            if (!name.isNullOrBlank() && value != null) {
                result[name] = value
            }
        }
        return result
    }

}

data class TvQrCode(
    val authCode: String,
    val url: String
)

data class WebQrCode(
    val qrKey: String,
    val url: String
)

enum class QrPollStatus {
    PENDING,
    SCANNED,
    EXPIRED,
    SUCCESS,
    ERROR
}

data class QrPollResult(
    val status: QrPollStatus,
    val rawCode: Int,
    val message: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val cookies: Map<String, String> = emptyMap()
)
