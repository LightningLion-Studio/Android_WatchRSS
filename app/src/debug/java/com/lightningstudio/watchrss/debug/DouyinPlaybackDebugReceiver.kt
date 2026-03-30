package com.lightningstudio.watchrss.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lightningstudio.watchrss.util.AppLogger

class DouyinPlaybackDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (!DouyinPlaybackDebugController.supportsForceFailAction(action)) {
            return
        }
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

    private companion object {
        private const val TAG = "DouyinPlaybackDebug"
    }
}
