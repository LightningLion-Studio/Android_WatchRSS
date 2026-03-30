package com.lightningstudio.watchrss.debug

import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DouyinPlaybackDebugController {
    const val ACTION_FORCE_FAIL_NEXT_VIDEO =
        "com.lightningstudio.watchrss.debug.action.FORCE_FAIL_NEXT_DOUYIN_VIDEO"
    const val ACTION_FORCE_FAIL_CURRENT_VIDEO =
        "com.lightningstudio.watchrss.debug.action.FORCE_FAIL_CURRENT_DOUYIN_VIDEO"

    private const val TAG = "DouyinPlaybackDebug"

    private val _poisonedAwemeIds = MutableStateFlow<Set<String>>(emptySet())

    @Volatile
    private var activeAwemeId: String? = null
    @Volatile
    private var nextAwemeId: String? = null

    val poisonedAwemeIds: StateFlow<Set<String>> = _poisonedAwemeIds.asStateFlow()

    fun updatePlaybackContext(activeAwemeId: String?, nextAwemeId: String?) {
        this.activeAwemeId = activeAwemeId?.trim()?.takeIf { it.isNotEmpty() }
        this.nextAwemeId = nextAwemeId?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun supportsForceFailAction(action: String?): Boolean {
        return action == ACTION_FORCE_FAIL_NEXT_VIDEO || action == ACTION_FORCE_FAIL_CURRENT_VIDEO
    }

    fun requestForceFailNextVideo(source: String = "unknown"): String? {
        val awemeId = nextAwemeId
        if (awemeId.isNullOrBlank()) {
            AppLogger.w(
                TAG,
                "request ignored: no next douyin video source=$source activeAwemeId=$activeAwemeId"
            )
            return null
        }
        _poisonedAwemeIds.value = _poisonedAwemeIds.value + awemeId
        AppLogger.d(
            TAG,
            "request poison next video awemeId=$awemeId source=$source activeAwemeId=$activeAwemeId"
        )
        return awemeId
    }
}
