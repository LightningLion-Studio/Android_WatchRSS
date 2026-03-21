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
    );

    companion object {
        val orderedValues: List<PhoneConnectionAbility> = listOf(
            REMOTE_INPUT,
            SYNC_FAVORITES,
            SYNC_WATCH_LATER
        )

        fun fromNameOrNull(value: String?): PhoneConnectionAbility? {
            if (value.isNullOrBlank()) return null
            return orderedValues.firstOrNull { it.name == value }
        }
    }
}
