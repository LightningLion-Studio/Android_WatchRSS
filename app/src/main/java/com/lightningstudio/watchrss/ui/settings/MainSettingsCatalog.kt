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

    val mediaVolumeGuard = MainSettingInfo(
        title = "音量调节防干扰",
        description = "播放前会先压低音量，减少外放突然过响"
    )

    val standardEntries: List<MainSettingInfo> = listOf(
        readingTheme,
        fontSize,
        mediaVolumeGuard
    )
}
