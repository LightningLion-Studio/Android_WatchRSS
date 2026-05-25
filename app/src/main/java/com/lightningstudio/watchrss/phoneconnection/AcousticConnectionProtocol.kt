package com.lightningstudio.watchrss.phoneconnection

import com.lightningstudio.watchrss.data.rss.SavedItem
import org.json.JSONArray
import org.json.JSONObject

const val ACOUSTIC_KIND_PURE_SOUND = "pure_sound"
const val ACOUSTIC_KIND_GUIDED_WIFI = "guided_wifi"
private const val ACOUSTIC_KIND_GUIDED_WIFI_COMPACT = "g"

data class PureSoundEnvelope(
    val ability: PhoneConnectionAbility,
    val url: String? = null,
    val items: JSONArray? = null
)

data class GuidedWifiEnvelope(
    val ability: PhoneConnectionAbility,
    val ssid: String,
    val passphrase: String,
    val host: String,
    val port: Int,
    val token: String
)

object AcousticConnectionProtocol {
    fun buildPureSoundRemoteInput(url: String): ByteArray {
        return JSONObject().apply {
            put("kind", ACOUSTIC_KIND_PURE_SOUND)
            put("ability", PhoneConnectionAbility.REMOTE_INPUT.name)
            put("url", url)
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun buildPureSoundSavedItems(
        ability: PhoneConnectionAbility,
        items: List<SavedItem>
    ): ByteArray {
        val payloadItems = SavedItemsSyncPayload.buildLinksOnly(items)

        return JSONObject().apply {
            put("kind", ACOUSTIC_KIND_PURE_SOUND)
            put("ability", ability.name)
            put("items", payloadItems)
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun parsePureSound(bytes: ByteArray): PureSoundEnvelope {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.optString("kind") == ACOUSTIC_KIND_PURE_SOUND) { "不是纯声波数据" }
        val ability = PhoneConnectionAbility.valueOf(json.getString("ability"))
        return PureSoundEnvelope(
            ability = ability,
            url = json.optString("url").takeIf { it.isNotBlank() },
            items = json.optJSONArray("items")
        )
    }

    fun buildGuidedWifi(
        ability: PhoneConnectionAbility,
        ssid: String,
        passphrase: String,
        host: String,
        port: Int,
        token: String
    ): ByteArray {
        return JSONObject().apply {
            put("k", ACOUSTIC_KIND_GUIDED_WIFI_COMPACT)
            put("a", ability.acousticCode)
            if (ssid.isNotBlank()) {
                put("s", ssid)
            }
            if (passphrase.isNotBlank()) {
                put("p", passphrase)
            }
            put("h", host)
            put("o", port)
            put("t", token)
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun parseGuidedWifi(bytes: ByteArray): GuidedWifiEnvelope {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        val kind = json.optString("k").ifBlank { json.optString("kind") }
        require(kind == ACOUSTIC_KIND_GUIDED_WIFI_COMPACT || kind == ACOUSTIC_KIND_GUIDED_WIFI) {
            "不是声波引导 WiFi 数据"
        }
        return GuidedWifiEnvelope(
            ability = PhoneConnectionAbility.fromPayloadValue(
                json.optString("a").ifBlank { json.getString("ability") }
            ),
            ssid = json.optString("s").ifBlank { json.optString("ssid") },
            passphrase = json.optString("p").ifBlank { json.optString("passphrase") },
            host = json.optString("h").ifBlank { json.getString("host") },
            port = if (json.has("o")) json.getInt("o") else json.getInt("port"),
            token = json.optString("t").ifBlank { json.getString("token") }
        )
    }
}
