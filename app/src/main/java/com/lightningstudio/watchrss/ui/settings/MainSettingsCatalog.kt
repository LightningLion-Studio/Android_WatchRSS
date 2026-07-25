package com.lightningstudio.watchrss.ui.settings

data class MainSettingInfo(
    val title: String,
    val description: String
)

object MainSettingsCatalog {
    val readingTheme = MainSettingInfo(
        title = "阅读主题",
        description = "切换浅色或深色阅读界面"
    )

    val fontSize = MainSettingInfo(
        title = "字体大小",
        description = "调整正文阅读字号"
    )

    val mediaVolumeControl = MainSettingInfo(
        title = "使用滚轮调节音量",
        description = "播放器内可用滚轮调节媒体音量"
    )

    val mediaVolumeGuard = MainSettingInfo(
        title = "音量调节防干扰",
        description = "滚轮连续上调时先停一下，减少误触后突然过响"
    )

    val mediaPlaybackStartVolumeLimit = MainSettingInfo(
        title = "静音开播",
        description = "开始播放时仅在当前音量高于上限时压低音量"
    )

    val standardEntries: List<MainSettingInfo> = listOf(
        readingTheme,
        fontSize,
        mediaVolumeControl,
        mediaVolumeGuard,
        mediaPlaybackStartVolumeLimit
    )
}
