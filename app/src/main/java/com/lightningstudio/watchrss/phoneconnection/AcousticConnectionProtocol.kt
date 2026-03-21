package com.lightningstudio.watchrss.phoneconnection

import com.lightningstudio.watchrss.data.rss.SavedItem
import org.json.JSONArray
import org.json.JSONObject

const val ACOUSTIC_KIND_PURE_SOUND = "pure_sound"
const val ACOUSTIC_KIND_GUIDED_WIFI = "guided_wifi"

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
            put("kind", ACOUSTIC_KIND_GUIDED_WIFI)
            put("ability", ability.name)
            put("ssid", ssid)
            put("passphrase", passphrase)
            put("host", host)
            put("port", port)
            put("token", token)
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun parseGuidedWifi(bytes: ByteArray): GuidedWifiEnvelope {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.optString("kind") == ACOUSTIC_KIND_GUIDED_WIFI) { "不是声波引导 WiFi 数据" }
        return GuidedWifiEnvelope(
            ability = PhoneConnectionAbility.valueOf(json.getString("ability")),
            ssid = json.getString("ssid"),
            passphrase = json.getString("passphrase"),
            host = json.getString("host"),
            port = json.getInt("port"),
            token = json.getString("token")
        )
    }
}
