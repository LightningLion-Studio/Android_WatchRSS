package com.lightningstudio.watchrss.sdk.douyin

// 参考自Python实现的crawlers/douyin/web/models.py
open class BaseRequestModel(
    val device_platform: String = "webapp",
    val aid: String = "6383",
    val channel: String = "channel_pc_web",
    val pc_client_type: Int = 1,
    val version_code: String = "290100",
    val version_name: String = "29.1.0",
    val cookie_enabled: String = "true",
    val screen_width: Int = 907,
    val screen_height: Int = 680,
    val browser_language: String = "zh-CN",
    val browser_platform: String = "Win32",
    val browser_name: String = "Chrome",
    val browser_version: String = "130.0.0.0",
    val browser_online: String = "true",
    val engine_name: String = "Blink",
    val engine_version: String = "130.0.0.0",
    val os_name: String = "Windows",
    val os_version: String = "10",
    val cpu_core_num: Int = 12,
    val device_memory: Int = 8,
    val platform: String = "PC",
    val downlink: String = "10",
    val effective_type: String = "4g",
    val from_user_page: String = "1",
    val locate_query: String = "false",
    val need_time_list: String = "1",
    val pc_libra_divert: String = "Windows",
    val publish_video_strategy_type: String = "2",
    val round_trip_time: String = "0",
    val show_live_replay_strategy: String = "1",
    val time_list_query: String = "0",
    val whale_cut_token: String = "",
    val update_version_code: String = "170400"
) {
    open fun toMap(): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        map["device_platform"] = device_platform
        map["aid"] = aid
        map["channel"] = channel
        map["pc_client_type"] = pc_client_type.toString()
        map["version_code"] = version_code
        map["version_name"] = version_name
        map["cookie_enabled"] = cookie_enabled
        map["screen_width"] = screen_width.toString()
        map["screen_height"] = screen_height.toString()
        map["browser_language"] = browser_language
        map["browser_platform"] = browser_platform
        map["browser_name"] = browser_name
        map["browser_version"] = browser_version
        map["browser_online"] = browser_online
        map["engine_name"] = engine_name
        map["engine_version"] = engine_version
        map["os_name"] = os_name
        map["os_version"] = os_version
        map["cpu_core_num"] = cpu_core_num.toString()
        map["device_memory"] = device_memory.toString()
        map["platform"] = platform
        map["downlink"] = downlink
        map["effective_type"] = effective_type
        map["from_user_page"] = from_user_page
        map["locate_query"] = locate_query
        map["need_time_list"] = need_time_list
        map["pc_libra_divert"] = pc_libra_divert
        map["publish_video_strategy_type"] = publish_video_strategy_type
        map["round_trip_time"] = round_trip_time
        map["show_live_replay_strategy"] = show_live_replay_strategy
        map["time_list_query"] = time_list_query
        map["whale_cut_token"] = whale_cut_token
        map["update_version_code"] = update_version_code
        return map
    }
}

class PostDetail(val awemeId: String) : BaseRequestModel() {
    override fun toMap(): MutableMap<String, String> {
        val map = super.toMap()
        map["aweme_id"] = awemeId
        return map
    }
}

class JingxuanFeed(
    private val cursor: String? = null,
    private val count: Int = 10
) : BaseRequestModel() {
    override fun toMap(): MutableMap<String, String> {
        val map = super.toMap()
        map["count"] = count.toString()
        val safeCursor = cursor?.trim().orEmpty()
        if (safeCursor.isNotEmpty()) {
            map["cursor"] = safeCursor
            map["max_cursor"] = safeCursor
        }
        return map
    }
}
