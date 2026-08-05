package com.lightningstudio.watchrss.phoneconnection

enum class PhoneConnectionAbility(
    val wireCode: String,
    val displayName: String,
    val acousticCode: String
) {
    REMOTE_INPUT(
        wireCode = "dc40517c-a09c-419c-8c4d-d3883258992e",
        displayName = "RSS订阅输入",
        acousticCode = "r"
    ),
    SYNC_FAVORITES(
        wireCode = "c4bf141f-b0de-46f7-a661-0a3ad0716bce",
        displayName = "收藏夹",
        acousticCode = "f"
    ),
    SYNC_WATCH_LATER(
        wireCode = "f1aa43bd-0fe3-4771-ae6b-d4799ecf84b5",
        displayName = "稍后阅读",
        acousticCode = "w"
    ),
    SYNC_BILI_WATCH_RECORDS(
        wireCode = "9a88f4ec-a071-4cf4-8b25-e735836ebb0d",
        displayName = "B站观看历史/进度",
        acousticCode = "b"
    ),
    LLM_SUMMARY_CONFIG(
        wireCode = "a3e72c1d-5f84-4b90-9d16-e8c047f2b3a1",
        displayName = "LLM总结配置",
        acousticCode = "l"
    ),
    TTS_CONFIG(
        wireCode = "b8f4d9e2-1c7a-4e5b-9a3f-6d2e8c1b5f74",
        displayName = "朗读语音配置",
        acousticCode = "t"
    );

    companion object {
        val orderedValues: List<PhoneConnectionAbility> = listOf(
            REMOTE_INPUT,
            SYNC_FAVORITES,
            SYNC_WATCH_LATER,
            SYNC_BILI_WATCH_RECORDS,
            LLM_SUMMARY_CONFIG,
            TTS_CONFIG
        )

        fun fromNameOrNull(value: String?): PhoneConnectionAbility? {
            if (value.isNullOrBlank()) return null
            return orderedValues.firstOrNull { it.name == value }
        }

        fun fromPayloadValue(value: String): PhoneConnectionAbility {
            val normalized = value.trim()
            return orderedValues.firstOrNull { ability ->
                ability.name == normalized ||
                    ability.wireCode == normalized ||
                    ability.displayName == normalized ||
                    ability.acousticCode == normalized
            } ?: throw IllegalArgumentException("未知能力标识：$value")
        }
    }
}
