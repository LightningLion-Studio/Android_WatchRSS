package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BiliIdentity(
    private val httpClient: BiliHttpClient,
    private val accountStore: BiliAccountStore?
) {
    private companion object {
        private const val WEB_TICKET_KEY_ID = "ec02"
        private const val WEB_TICKET_HMAC_KEY = "XgwSnGZ1p"
    }

    suspend fun fetchBuvid(): BuvidResult? {
        val url = "https://api.bilibili.com/x/frontend/finger/spi"
        val response = httpClient.get(url, includeCookies = false)
        if (response.code != 200) return null
        val json = biliJson.parseToJsonElement(response.body).jsonObject
        val data = json["data"]?.jsonObject ?: return null
        val buvid3 = data["b_3"]?.jsonPrimitive?.content
        val buvid4 = data["b_4"]?.jsonPrimitive?.content
        if (buvid3.isNullOrBlank() && buvid4.isNullOrBlank()) return null
        accountStore?.update { current ->
            val updatedCookies = buildMap {
                if (!buvid3.isNullOrBlank()) put("buvid3", buvid3)
                if (!buvid4.isNullOrBlank()) put("buvid4", buvid4)
            }
            current.copy(
                cookies = if (updatedCookies.isEmpty()) current.cookies
                else BiliCookies.merge(current.cookies, updatedCookies),
                buvid3 = buvid3 ?: current.buvid3,
                buvid4 = buvid4 ?: current.buvid4,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
        return BuvidResult(buvid3, buvid4)
    }

    suspend fun fetchWbiKeys(): WbiKeys? {
        val url = "https://api.bilibili.com/x/web-interface/nav"
        val response = httpClient.get(url)
        if (response.code != 200) return null
        val json = biliJson.parseToJsonElement(response.body).jsonObject
        val data = json["data"]?.jsonObject ?: return null
        val wbi = data["wbi_img"]?.jsonObject ?: return null
        val imgUrl = wbi["img_url"]?.jsonPrimitive?.content
        val subUrl = wbi["sub_url"]?.jsonPrimitive?.content
        val imgKey = BiliSigners.extractWbiKey(imgUrl)
        val subKey = BiliSigners.extractWbiKey(subUrl)
        if (imgKey.isNullOrBlank() || subKey.isNullOrBlank()) return null
        accountStore?.update { current ->
            current.copy(
                wbiImgKey = imgKey,
                wbiSubKey = subKey,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
        return WbiKeys(imgKey, subKey)
    }

    suspend fun fetchWebTicket(csrf: String): String? {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val hexsign = hmacSha256Hex(WEB_TICKET_HMAC_KEY, "ts$timestamp")
        val baseUrl = "https://api.bilibili.com/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket"
        val urlParams = mutableMapOf(
            "key_id" to WEB_TICKET_KEY_ID,
            "hexsign" to hexsign,
            "context[ts]" to timestamp
        )
        if (csrf.isNotBlank()) {
            urlParams["csrf"] = csrf
        }
        val url = buildUrlWithParams(baseUrl, urlParams)
        val response = httpClient.postForm(
            url,
            form = emptyMap(),
            headers = mapOf("Referer" to "https://www.bilibili.com/")
        )
        if (response.code != 200) {
            BiliDebugLog.log("bili_ticket", "http=${response.code} csrf=${csrf.isNotBlank()}")
            return null
        }
        val json = biliJson.parseToJsonElement(response.body).jsonObject
        val code = json.intOrNull("code") ?: -1
        if (code != 0) {
            val message = json.stringOrNull("message").orEmpty()
            BiliDebugLog.log("bili_ticket", "code=$code msg=$message csrf=${csrf.isNotBlank()}")
            return null
        }
        val data = json["data"]?.jsonObject ?: return null
        val ticket = data["ticket"]?.jsonPrimitive?.content
        if (ticket.isNullOrBlank()) {
            BiliDebugLog.log("bili_ticket", "empty_ticket csrf=${csrf.isNotBlank()}")
            return null
        }
        accountStore?.update { current ->
            val updatedCookies = BiliCookies.merge(
                current.cookies,
                mapOf("bili_ticket" to ticket)
            )
            current.copy(
                cookies = updatedCookies,
                biliTicket = ticket,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
        BiliDebugLog.log("bili_ticket", "ok csrf=${csrf.isNotBlank()}")
        return ticket
    }

    private fun buildUrlWithParams(url: String, params: Map<String, String>): String {
        val httpUrl = url.toHttpUrlOrNull() ?: return url
        val builder = httpUrl.newBuilder()
        params.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun hmacSha256Hex(key: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

}

data class BuvidResult(
    val buvid3: String?,
    val buvid4: String?
)

data class WbiKeys(
    val imgKey: String,
    val subKey: String
)
