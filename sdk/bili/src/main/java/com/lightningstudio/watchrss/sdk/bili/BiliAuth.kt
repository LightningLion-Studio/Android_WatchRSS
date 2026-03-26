package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.jsonObject

class BiliAuth(private val client: BiliClient) {
    suspend fun applyCookies(cookies: Map<String, String>) {
        if (cookies.isEmpty()) return
        updateAccountStore(cookies = cookies)
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
        updateAccountStore(cookies = cookies, refreshToken = refreshToken)
        return QrPollResult(status, statusCode, data.stringOrNull("message"), null, refreshToken, cookies)
    }

    private suspend fun updateAccountStore(
        cookies: Map<String, String>,
        refreshToken: String? = null
    ) {
        client.accountStore?.update { current ->
            current.copy(
                cookies = BiliCookies.merge(current.cookies, cookies),
                accessToken = null,
                refreshToken = refreshToken ?: current.refreshToken,
                appRefreshToken = null,
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
}

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
