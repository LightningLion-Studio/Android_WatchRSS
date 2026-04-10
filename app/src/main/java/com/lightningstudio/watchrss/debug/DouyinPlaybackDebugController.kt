package com.lightningstudio.watchrss.debug

import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DouyinPlaybackDebugController {
    const val ACTION_IMPORT_DOUYIN_COOKIE =
        "com.lightningstudio.watchrss.debug.action.IMPORT_DOUYIN_COOKIE"
    const val EXTRA_RAW_COOKIE = "raw_cookie"
    const val ACTION_FORCE_FAIL_NEXT_VIDEO =
        "com.lightningstudio.watchrss.debug.action.FORCE_FAIL_NEXT_DOUYIN_VIDEO"
    const val ACTION_FORCE_FAIL_CURRENT_VIDEO =
        "com.lightningstudio.watchrss.debug.action.FORCE_FAIL_CURRENT_DOUYIN_VIDEO"
    const val ACTION_ADVANCE_TO_NEXT_VIDEO =
        "com.lightningstudio.watchrss.debug.action.ADVANCE_TO_NEXT_DOUYIN_VIDEO"
    const val ACTION_REPORT_PLAYBACK_CONTEXT =
        "com.lightningstudio.watchrss.debug.action.REPORT_DOUYIN_PLAYBACK_CONTEXT"

    private const val TAG = "DouyinPlaybackDebug"

    private val _poisonedAwemeIds = MutableStateFlow<Set<String>>(emptySet())
    private val _advanceRequests = MutableSharedFlow<Long>(extraBufferCapacity = 8)

    @Volatile
    private var activeAwemeId: String? = null
    @Volatile
    private var nextAwemeId: String? = null
    @Volatile
    private var inVideoFlow: Boolean = false

    val poisonedAwemeIds: StateFlow<Set<String>> = _poisonedAwemeIds.asStateFlow()
    val advanceRequests: SharedFlow<Long> = _advanceRequests

    fun updatePlaybackContext(activeAwemeId: String?, nextAwemeId: String?, inVideoFlow: Boolean) {
        this.activeAwemeId = activeAwemeId?.trim()?.takeIf { it.isNotEmpty() }
        this.nextAwemeId = nextAwemeId?.trim()?.takeIf { it.isNotEmpty() }
        this.inVideoFlow = inVideoFlow
    }

    fun supportsForceFailAction(action: String?): Boolean {
        return action == ACTION_FORCE_FAIL_NEXT_VIDEO ||
            action == ACTION_FORCE_FAIL_CURRENT_VIDEO ||
            action == ACTION_ADVANCE_TO_NEXT_VIDEO ||
            action == ACTION_REPORT_PLAYBACK_CONTEXT ||
            action == ACTION_IMPORT_DOUYIN_COOKIE
    }

    fun snapshotPlaybackContext(): Triple<String?, String?, Boolean> {
        return Triple(activeAwemeId, nextAwemeId, inVideoFlow)
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

    fun requestAdvanceToNextVideo(source: String = "unknown"): Boolean {
        val emitted = _advanceRequests.tryEmit(System.nanoTime())
        AppLogger.d(
            TAG,
            "request advance next video emitted=$emitted source=$source activeAwemeId=$activeAwemeId nextAwemeId=$nextAwemeId"
        )
        return emitted
    }
}
