package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class BiliAuth(private val client: BiliClient) {
    private companion object {
        private const val WEB_COOKIE_SOURCE = "main_web"
        private const val WEB_COOKIE_REFRESH_HTML_ID = "1-name"
        private val WEB_COOKIE_REFRESH_CSRF_REGEX =
            Regex("""id=["']$WEB_COOKIE_REFRESH_HTML_ID["'][^>]*>\s*([^<\s]+)\s*<""")
        private val WEB_COOKIE_REFRESH_PUBLIC_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDLgd2OAkcGVtoE3ThUREbio0Eg
            Uc/prcajMKXvkCKFCWhJYJcLkcM2DKKcSeFpD/j6Boy538YXnR6VhcuUJOhH2x71
            nzPjfdTcqMz7djHum0qSZA0AyCBDABUqCrfNgCiJ00Ra7GmRj+YCK1NJEuewlb40
            JNrRuoEUXpabUzGB8QIDAQAB
            -----END PUBLIC KEY-----
        """.trimIndent()
    }

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

    suspend fun refreshWebCookies(forceRefresh: Boolean = false): WebCookieRefreshResult {
        val account = client.accountStore?.read()
            ?: return WebCookieRefreshResult(code = -101, message = "missing_account", forced = forceRefresh)
        val refreshToken = account.refreshToken
            ?: return WebCookieRefreshResult(code = -101, message = "missing_refresh_token", forced = forceRefresh)
        val csrf = account.csrfToken()
            ?: return WebCookieRefreshResult(code = -111, message = "missing_csrf", forced = forceRefresh)

        val checkResult = if (forceRefresh) {
            null
        } else {
            val checked = checkWebCookieRefresh(csrf)
            if (!checked.isSuccess) return WebCookieRefreshResult(
                code = checked.code,
                message = checked.message,
                checked = true,
                forced = false
            )
            val checkedAt = checked.timestamp ?: System.currentTimeMillis()
            updateCookieRefreshCheckTimestamp(checkedAt)
            if (!checked.shouldRefresh) {
                BiliDebugLog.log("bili_cookie_refresh", "skip_refresh force=false reason=not_needed")
                return WebCookieRefreshResult(
                    code = 0,
                    refreshed = false,
                    checked = true,
                    forced = false
                )
            }
            checked
        }

        val refreshTimestamp = checkResult?.timestamp ?: System.currentTimeMillis()
        val correspondPath = generateCorrespondPath(refreshTimestamp)
        val refreshCsrf = fetchRefreshCsrf(correspondPath)
            ?: return WebCookieRefreshResult(
                code = -1,
                message = "missing_refresh_csrf",
                checked = checkResult != null,
                forced = forceRefresh
            )

        val refreshResponse = requestWebCookieRefresh(
            csrf = csrf,
            refreshCsrf = refreshCsrf,
            refreshToken = refreshToken
        )
        if (!refreshResponse.isSuccess || refreshResponse.cookies.isEmpty()) {
            return WebCookieRefreshResult(
                code = refreshResponse.code,
                message = refreshResponse.message,
                checked = checkResult != null,
                forced = forceRefresh
            )
        }

        val checkedAt = System.currentTimeMillis()
        updateAccountStore(
            cookies = refreshResponse.cookies,
            refreshToken = refreshResponse.refreshToken,
            cookieRefreshCheckedAtMillis = checkedAt,
            syncIdentity = false
        )

        val newCsrf = client.accountStore?.read()?.csrfToken().orEmpty()
        if (newCsrf.isNotBlank()) {
            val confirmResult = confirmWebCookieRefresh(newCsrf, refreshToken)
            if (!confirmResult.isSuccess) {
                BiliDebugLog.log(
                    "bili_cookie_refresh",
                    "confirm_failed code=${confirmResult.code} msg=${confirmResult.message}"
                )
            }
        }

        client.identity.fetchBuvid()
        client.identity.fetchWbiKeys()
        if (newCsrf.isNotBlank()) {
            client.identity.fetchWebTicket(newCsrf)
        }

        BiliDebugLog.log(
            "bili_cookie_refresh",
            "refresh_ok force=$forceRefresh checked=${checkResult != null}"
        )
        return WebCookieRefreshResult(
            code = 0,
            refreshed = true,
            checked = checkResult != null,
            forced = forceRefresh
        )
    }

    private suspend fun updateAccountStore(
        cookies: Map<String, String>,
        refreshToken: String? = null,
        cookieRefreshCheckedAtMillis: Long? = null,
        syncIdentity: Boolean = true
    ) {
        val now = System.currentTimeMillis()
        client.accountStore?.update { current ->
            current.copy(
                cookies = BiliCookies.merge(current.cookies, cookies),
                accessToken = null,
                refreshToken = refreshToken ?: current.refreshToken,
                appRefreshToken = null,
                cookieRefreshCheckedAtMillis = cookieRefreshCheckedAtMillis ?: current.cookieRefreshCheckedAtMillis,
                updatedAtMillis = now
            )
        }
        if (syncIdentity && cookies.isNotEmpty()) {
            client.identity.fetchBuvid()
            client.identity.fetchWbiKeys()
            val csrf = cookies["bili_jct"] ?: client.accountStore?.read()?.csrfToken()
            if (!csrf.isNullOrBlank()) {
                client.identity.fetchWebTicket(csrf)
            }
        }
    }

    private suspend fun updateCookieRefreshCheckTimestamp(timestamp: Long) {
        client.accountStore?.update { current ->
            current.copy(
                cookieRefreshCheckedAtMillis = timestamp,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
    }

    private suspend fun checkWebCookieRefresh(csrf: String): WebCookieCheckResult {
        val url = "${client.config.passportBaseUrl}/x/passport-login/web/cookie/info"
        val response = client.httpClient.get(
            url = url,
            params = mapOf("csrf" to csrf)
        )
        val status = parseBiliStatus(response, requestMode = "web")
        if (status.code != 0) {
            BiliDebugLog.log(
                "bili_cookie_refresh",
                "check_failed code=${status.code} msg=${status.message}"
            )
            return WebCookieCheckResult(code = status.code, message = status.message)
        }
        val data = status.data?.asObjectOrNull()
        return WebCookieCheckResult(
            code = 0,
            shouldRefresh = data?.booleanOrNull("refresh") == true,
            timestamp = data?.longOrNull("timestamp")
        )
    }

    private suspend fun fetchRefreshCsrf(correspondPath: String): String? {
        val url = "${client.config.webReferer.trimEnd('/')}/correspond/1/$correspondPath"
        val response = client.httpClient.get(url)
        if (response.code !in 200..299) {
            BiliDebugLog.log("bili_cookie_refresh", "refresh_csrf_http=${response.code}")
            return null
        }
        val match = WEB_COOKIE_REFRESH_CSRF_REGEX.find(response.body)
        val refreshCsrf = match?.groupValues?.getOrNull(1)?.trim()
        if (refreshCsrf.isNullOrBlank()) {
            BiliDebugLog.log("bili_cookie_refresh", "refresh_csrf_missing")
            return null
        }
        return refreshCsrf
    }

    private suspend fun requestWebCookieRefresh(
        csrf: String,
        refreshCsrf: String,
        refreshToken: String
    ): WebCookieRefreshResponse {
        val url = "${client.config.passportBaseUrl}/x/passport-login/web/cookie/refresh"
        val response = client.httpClient.postForm(
            url = url,
            form = mapOf(
                "csrf" to csrf,
                "refresh_csrf" to refreshCsrf,
                "source" to WEB_COOKIE_SOURCE,
                "refresh_token" to refreshToken
            )
        )
        val status = parseBiliStatus(response, requestMode = "web")
        val refreshedCookies = BiliCookies.parseSetCookieHeaders(response.headers)
        if (status.code != 0) {
            BiliDebugLog.log(
                "bili_cookie_refresh",
                "refresh_failed code=${status.code} msg=${status.message}"
            )
            return WebCookieRefreshResponse(
                code = status.code,
                message = status.message,
                cookies = refreshedCookies
            )
        }
        val data = status.data?.asObjectOrNull()
        val nextRefreshToken = data?.stringOrNull("refresh_token")
        return WebCookieRefreshResponse(
            code = 0,
            refreshToken = nextRefreshToken,
            cookies = refreshedCookies
        )
    }

    private suspend fun confirmWebCookieRefresh(csrf: String, oldRefreshToken: String): WebCookieRefreshConfirmResult {
        val url = "${client.config.passportBaseUrl}/x/passport-login/web/confirm/refresh"
        val response = client.httpClient.postForm(
            url = url,
            form = mapOf(
                "csrf" to csrf,
                "refresh_token" to oldRefreshToken
            )
        )
        val status = parseBiliStatus(response, requestMode = "web")
        return WebCookieRefreshConfirmResult(code = status.code, message = status.message)
    }

    private fun generateCorrespondPath(timestamp: Long): String {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(
                Base64.getDecoder().decode(
                    WEB_COOKIE_REFRESH_PUBLIC_KEY
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replace("\n", "")
                        .trim()
                )
            )
        )
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding").apply {
            init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT
                )
            )
        }
        return cipher
            .doFinal("refresh_$timestamp".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

data class WebCookieCheckResult(
    val code: Int,
    val message: String? = null,
    val shouldRefresh: Boolean = false,
    val timestamp: Long? = null
) {
    val isSuccess: Boolean
        get() = code == 0
}

data class WebCookieRefreshResult(
    val code: Int,
    val message: String? = null,
    val refreshed: Boolean = false,
    val checked: Boolean = false,
    val forced: Boolean = false
) {
    val isSuccess: Boolean
        get() = code == 0
}

private data class WebCookieRefreshResponse(
    val code: Int,
    val message: String? = null,
    val refreshToken: String? = null,
    val cookies: Map<String, String> = emptyMap()
) {
    val isSuccess: Boolean
        get() = code == 0
}

private data class WebCookieRefreshConfirmResult(
    val code: Int,
    val message: String? = null
) {
    val isSuccess: Boolean
        get() = code == 0
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
