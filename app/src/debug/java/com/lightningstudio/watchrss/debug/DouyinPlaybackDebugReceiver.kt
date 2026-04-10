package com.lightningstudio.watchrss.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.runBlocking

class DouyinPlaybackDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == null) {
            return
        }
        when (action) {
            DouyinPlaybackDebugController.ACTION_ADVANCE_TO_NEXT_VIDEO -> {
                val advanced = DouyinPlaybackDebugController.requestAdvanceToNextVideo(source = "adb")
                if (advanced) {
                    AppLogger.d(TAG, "advance broadcast accepted action=$action")
                } else {
                    AppLogger.w(TAG, "advance broadcast ignored action=$action")
                }
            }
            DouyinPlaybackDebugController.ACTION_FORCE_FAIL_NEXT_VIDEO,
            DouyinPlaybackDebugController.ACTION_FORCE_FAIL_CURRENT_VIDEO -> {
                val source = if (action == DouyinPlaybackDebugController.ACTION_FORCE_FAIL_CURRENT_VIDEO) {
                    "adb-legacy-current-alias"
                } else {
                    "adb"
                }
                val awemeId = DouyinPlaybackDebugController.requestForceFailNextVideo(source = source)
                if (awemeId == null) {
                    AppLogger.w(TAG, "force fail broadcast ignored: no next douyin video action=$action")
                } else {
                    AppLogger.d(TAG, "force fail broadcast accepted awemeId=$awemeId action=$action")
                }
            }
            DouyinPlaybackDebugController.ACTION_REPORT_PLAYBACK_CONTEXT -> {
                val (activeAwemeId, nextAwemeId, inVideoFlow) = DouyinPlaybackDebugController.snapshotPlaybackContext()
                val result = "activeAwemeId=${activeAwemeId.orEmpty()};nextAwemeId=${nextAwemeId.orEmpty()};inVideoFlow=$inVideoFlow"
                setResultCode(0)
                setResultData(result)
                AppLogger.d(TAG, "report context $result")
            }
            DouyinPlaybackDebugController.ACTION_IMPORT_DOUYIN_COOKIE -> {
                val rawCookie = intent.getStringExtra(DouyinPlaybackDebugController.EXTRA_RAW_COOKIE)
                    ?.trim()
                    .orEmpty()
                if (rawCookie.isBlank()) {
                    setResultCode(1)
                    setResultData("missing_cookie")
                    AppLogger.w(TAG, "import cookie broadcast ignored: missing cookie extra")
                    return
                }
                val repository = (context.applicationContext as WatchRssApplication).container.douyinRepository
                val result = runBlocking {
                    repository.applyCookieHeader(rawCookie)
                }
                if (result.isSuccess) {
                    setResultCode(0)
                    setResultData("ok")
                    AppLogger.d(TAG, "import cookie broadcast applied length=${rawCookie.length}")
                } else {
                    val message = result.exceptionOrNull()?.message?.ifBlank { null } ?: "apply_failed"
                    setResultCode(2)
                    setResultData(message)
                    AppLogger.w(TAG, "import cookie broadcast failed message=$message")
                }
            }
            else -> return
        }
    }

    private companion object {
        private const val TAG = "DouyinPlaybackDebug"
    }
}
