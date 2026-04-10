package com.lightningstudio.watchrss.data.settings

enum class DouyinVideoCodecPreference(
    val persistedValue: Int,
    val label: String,
    val summary: String
) {
    AUTO(
        persistedValue = 0,
        label = "自动",
        summary = "固定使用 540p；优先 AVC/H264 以减少起播和切换等待，缺失时再回退到 HEVC/H265"
    ),
    H264(
        persistedValue = 1,
        label = "H264",
        summary = "固定使用 540p；优先 AVC/H264，缺失时自动回退到可播放编码"
    ),
    H265(
        persistedValue = 2,
        label = "H265",
        summary = "固定使用 540p；设备支持 HEVC/H265 时优先使用，否则回退 H264"
    );

    companion object {
        fun fromPersistedValue(value: Int): DouyinVideoCodecPreference {
            return entries.firstOrNull { it.persistedValue == value } ?: DEFAULT_DOUYIN_VIDEO_CODEC_PREFERENCE
        }
    }
}

val DEFAULT_DOUYIN_VIDEO_CODEC_PREFERENCE: DouyinVideoCodecPreference =
    DouyinVideoCodecPreference.AUTO
