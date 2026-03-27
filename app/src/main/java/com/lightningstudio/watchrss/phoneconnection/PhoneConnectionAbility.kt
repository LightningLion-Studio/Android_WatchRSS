package com.lightningstudio.watchrss.phoneconnection

enum class PhoneConnectionAbility(
    val wireCode: String,
    val displayName: String
) {
    REMOTE_INPUT(
        wireCode = "dc40517c-a09c-419c-8c4d-d3883258992e",
        displayName = "RSS订阅输入"
    ),
    SYNC_FAVORITES(
        wireCode = "c4bf141f-b0de-46f7-a661-0a3ad0716bce",
        displayName = "收藏夹"
    ),
    SYNC_WATCH_LATER(
        wireCode = "f1aa43bd-0fe3-4771-ae6b-d4799ecf84b5",
        displayName = "稍后阅读"
    ),
    LLM_SUMMARY_CONFIG(
        wireCode = "a3e72c1d-5f84-4b90-9d16-e8c047f2b3a1",
        displayName = "LLM总结配置"
    ),
    READ_ALOUD_CONFIG(
        wireCode = "196d6681-dc2d-4121-8ff7-9ffafdf7b5d8",
        displayName = "朗读配置"
    );

    companion object {
        val orderedValues: List<PhoneConnectionAbility> = listOf(
            REMOTE_INPUT,
            SYNC_FAVORITES,
            SYNC_WATCH_LATER,
            LLM_SUMMARY_CONFIG,
            READ_ALOUD_CONFIG
        )

        fun fromNameOrNull(value: String?): PhoneConnectionAbility? {
            if (value.isNullOrBlank()) return null
            return orderedValues.firstOrNull { it.name == value }
        }
    }
}
