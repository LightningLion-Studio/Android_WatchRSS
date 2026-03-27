package com.lightningstudio.watchrss.data.douyin

const val DOUYIN_PLAY_URL_TTL_MS: Long = 5 * 60 * 1000L

enum class DouyinSourceOrigin {
    BOOTSTRAP_CACHE,
    NETWORK_FEED,
    VIDEO_REFRESH;

    companion object {
        fun fromPersistedValue(value: String?): DouyinSourceOrigin {
            return entries.firstOrNull { it.name == value } ?: BOOTSTRAP_CACHE
        }
    }
}

enum class DouyinPlaybackSourceKind {
    LOCAL,
    REMOTE
}
