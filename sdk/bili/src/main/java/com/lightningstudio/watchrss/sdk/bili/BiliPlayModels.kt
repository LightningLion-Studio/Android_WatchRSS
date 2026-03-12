package com.lightningstudio.watchrss.sdk.bili

data class BiliPlayUrl(
    val quality: Int? = null,
    val format: String? = null,
    val timelength: Long? = null,
    val acceptQuality: List<Int> = emptyList(),
    val acceptDescription: List<String> = emptyList(),
    val dash: BiliDash? = null,
    val durl: List<BiliDurl> = emptyList()
)

data class BiliDash(
    val video: List<BiliDashStream> = emptyList(),
    val audio: List<BiliDashStream> = emptyList()
)

data class BiliDashStream(
    val id: Int? = null,
    val baseUrl: String? = null,
    val backupUrl: List<String> = emptyList(),
    val bandwidth: Int? = null,
    val codecid: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: String? = null
)

data class BiliDurl(
    val order: Int? = null,
    val length: Long? = null,
    val size: Long? = null,
    val url: String? = null,
    val backupUrl: List<String> = emptyList()
)
