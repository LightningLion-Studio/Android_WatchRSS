package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

class BiliPlay(private val client: BiliClient) {
    suspend fun fetchPlayUrl(
        cid: Long,
        aid: Long? = null,
        bvid: String? = null,
        qn: Int = 64,
        fnval: Int = 4048,
        fourk: Int = 0,
        platform: String? = null,
        useWbi: Boolean = true
    ): BiliResult<BiliPlayUrl> {
        if (aid == null && bvid.isNullOrBlank()) {
            return BiliResult(-1, "missing_id")
        }
        val params = mutableMapOf(
            "cid" to cid.toString(),
            "qn" to qn.toString(),
            "fnval" to fnval.toString(),
            "fourk" to fourk.toString()
        )
        if (aid != null) params["aid"] = aid.toString()
        if (!bvid.isNullOrBlank()) params["bvid"] = bvid
        if (!platform.isNullOrBlank()) params["platform"] = platform

        val requestParams = if (useWbi) {
            val signed = client.signedWbiParams(params)
            if (!signed.containsKey("w_rid")) {
                return BiliResult(-1, "missing_wbi_keys")
            }
            signed
        } else {
            params
        }
        val path = if (useWbi) "/x/player/wbi/playurl" else "/x/player/playurl"
        val url = "${client.config.webBaseUrl}$path"
        val response = client.httpClient.get(url, params = requestParams)
        val status = parseBiliStatus(response.body)
        if (status.code != 0) {
            return BiliResult(status.code, status.message)
        }
        val data = status.data?.asObjectOrNull() ?: return BiliResult(-1, "empty_data")
        return BiliResult(status.code, status.message, parsePlayUrl(data))
    }

    suspend fun fetchMp4Url(
        cid: Long,
        aid: Long? = null,
        bvid: String? = null,
        qn: Int = 32,
        platform: String = "html5"
    ): BiliResult<BiliPlayUrl> {
        val wbiResult = fetchPlayUrl(
            cid = cid,
            aid = aid,
            bvid = bvid,
            qn = qn,
            fnval = 1,
            fourk = 0,
            platform = platform
        )
        if (wbiResult.shouldUseLegacyMp4Fallback()) {
            val legacyResult = fetchPlayUrl(
                cid = cid,
                aid = aid,
                bvid = bvid,
                qn = qn,
                fnval = 1,
                fourk = 0,
                platform = platform,
                useWbi = false
            )
            if (!legacyResult.shouldUseLegacyMp4Fallback()) {
                return legacyResult
            }
        }
        return wbiResult
    }

    private fun parsePlayUrl(data: JsonObject): BiliPlayUrl {
        val dashObj = data.objOrNull("dash")
        val dash = dashObj?.let {
            BiliDash(
                video = parseDashStreams(it, "video"),
                audio = parseDashStreams(it, "audio")
            )
        }
        val durl = data.arrayOrNull("durl")
            ?.mapNotNull { it.asObjectOrNull() }
            ?.map {
                BiliDurl(
                    order = it.intOrNull("order"),
                    length = it.longOrNull("length"),
                    size = it.longOrNull("size"),
                    url = it.stringOrNull("url"),
                    backupUrl = it.arrayOrNull("backup_url")
                        ?.mapNotNull { el -> (el as? JsonPrimitive)?.contentOrNull }
                        ?: emptyList()
                )
            }
            ?: emptyList()
        val acceptQuality = data.arrayOrNull("accept_quality")
            ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            ?: emptyList()
        val acceptDescription = data.arrayOrNull("accept_description")
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()
        return BiliPlayUrl(
            quality = data.intOrNull("quality"),
            format = data.stringOrNull("format"),
            timelength = data.longOrNull("timelength"),
            acceptQuality = acceptQuality,
            acceptDescription = acceptDescription,
            dash = dash,
            durl = durl
        )
    }

    private fun parseDashStreams(obj: JsonObject, key: String): List<BiliDashStream> {
        return obj.arrayOrNull(key)
            ?.mapNotNull { it.asObjectOrNull() }
            ?.map { stream ->
                BiliDashStream(
                    id = stream.intOrNull("id"),
                    baseUrl = stream.stringOrNull("base_url"),
                    backupUrl = stream.arrayOrNull("backup_url")
                        ?.mapNotNull { el -> (el as? JsonPrimitive)?.contentOrNull }
                        ?: emptyList(),
                    bandwidth = stream.intOrNull("bandwidth"),
                    codecid = stream.intOrNull("codecid"),
                    width = stream.intOrNull("width"),
                    height = stream.intOrNull("height"),
                    frameRate = stream.stringOrNull("frame_rate")
                )
            }
            ?: emptyList()
    }

    private fun BiliResult<BiliPlayUrl>.shouldUseLegacyMp4Fallback(): Boolean {
        if (code == -1 && message == "missing_wbi_keys") return true
        if (!isSuccess) return false
        val playUrl = data ?: return true
        val hasDurl = playUrl.durl.any { !it.url.isNullOrBlank() }
        val hasDashVideo = playUrl.dash?.video?.any { !it.baseUrl.isNullOrBlank() } == true
        return !hasDurl && !hasDashVideo
    }
}
